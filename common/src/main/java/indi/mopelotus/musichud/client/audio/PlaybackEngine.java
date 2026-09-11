package indi.mopelotus.musichud.client.audio;

import indi.mopelotus.musichud.MusicHud;
import indi.mopelotus.musichud.client.audio.StreamAudioPlayer.Status;
import indi.mopelotus.musichud.beans.music.Fee;
import indi.mopelotus.musichud.beans.music.FormatType;
import indi.mopelotus.musichud.beans.music.MusicDetail;
import indi.mopelotus.musichud.beans.music.MusicResourceInfo;
import indi.mopelotus.musichud.beans.music.PlaybackSession;
import indi.mopelotus.musichud.beans.music.Quality;
import indi.mopelotus.musichud.client.audio.decoder.*;
import indi.mopelotus.musichud.client.ui.hud.renderer.PlayingStatusRenderer;
import indi.mopelotus.musichud.client.ui.ToastUtil;
import indi.mopelotus.musichud.client.services.tuneweave.TuneWeaveClientService;
import indi.mopelotus.musichud.interfaces.ClientConfig;
import indi.mopelotus.musichud.network.IClientNetworkService;
import indi.mopelotus.musichud.network.payloads.pushMessages.c2s.PlaybackResourceFailureMessage;
import lombok.Getter;
import lombok.SneakyThrows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundSource;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.SOFTDirectChannels;

import java.net.SocketException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class PlaybackEngine implements PlaybackHandoff.Lane {
    private volatile float mixGain = 1;
    private volatile boolean disposed;

    @Override public void gain(float gain) { mixGain = Math.clamp(gain, 0, 1); }
    @Override public void discard() { disposed = true; stop(); }
    PlaybackSession session() { return currentPlaybackSession; }
    private static final int BUFFER_COUNT = 8;
    private static final int BUFFER_SIZE = 65536;
    private static final int AUDIO_BUFFER_CAPACITY = 60;
    private static final long STALE_DROP_MARGIN_MS = 500;// 内容落后墙钟超过该值才丢弃
    private static final long PLAYBACK_STALL_LOG_MS = 500;// 播放线程迭代间隔超过该值记录停滞
    private static final long PLAY_LOOP_SLEEP_MS = 40;// 主循环轮询间隔
    private static final long INITIAL_BUFFER_WAIT_SLEEP_MS = 50;// 初始缓冲等待轮询间隔
    private static final long FULLY_RETRY_SLEEP_MS = 1000;// 全量重试前的等待
    private static final long PLAY_INIT_RETRY_SLEEP_MS = 500;// 播放初始化失败后的重试等待
    private static final Logger LOGGER = MusicHud.getLogger(PlaybackEngine.class);
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private final int[] buffers = new int[BUFFER_COUNT];
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<Status> status = new AtomicReference<>(Status.IDLE);
    private final AtomicLong playbackGeneration = new AtomicLong();
    @Getter
    private final Set<Consumer<Status>> statusChangeListener = new HashSet<>();
    private final AtomicLong totalBufferedBytes = new AtomicLong(0);
    private volatile BlockingQueue<byte[]> audioBuffer = new LinkedBlockingQueue<>(AUDIO_BUFFER_CAPACITY);
    private int source = 0;
    private float lastVolume;
    private volatile CompletableFuture<?> playingFuture;
    private volatile CompletableFuture<?> downloadFuture;
    private MusicDetail currentMusicDetail;
    private volatile AudioDecoder currentDecoder;
    private final PlaybackDecoderSlot decoderSlot = new PlaybackDecoderSlot();
    private volatile boolean float32Supported;
    private volatile boolean multichannelSupported;
    private long sourceContext;
    private OwnedAudioSources.Lease ownedSource;
    private long playedBytes = 0;
    private long fedBytes = 0;
    private int lastDecoderFormat = -1;
    private int lastDecoderSampleRate = -1;
    private volatile ZonedDateTime serverStartTime;
    private Future<?> downloadThreadFuture;
    private Future<?> playThreadFuture;
    private volatile boolean directPlayback;
    private volatile MusicResourceInfo directMusicResourceInfo = MusicResourceInfo.NONE;
    private volatile MusicResourceInfo currentResourceInfo = MusicResourceInfo.NONE;
    private final PlaybackSubmissionGate scrobbleGate = new PlaybackSubmissionGate();
    private final PlaybackListeningLedger listeningLedger = new PlaybackListeningLedger();
    private java.util.function.BiConsumer<Long, MusicResourceInfo> preparedScrobble;
    private volatile PlaybackSession currentPlaybackSession = PlaybackSession.NONE;
    private long lastStaleDropLogTime = 0;

    private AudioDecoder loadAudioDecoder(String identifier, FormatType formatType, java.util.Map<String, String> headers) {
        return AudioDecoderFactory.open(identifier, formatType, headers, float32Supported, multichannelSupported);
    }

    private AudioDecoder loadPublicAudioDecoder(String identifier, FormatType formatType,
                                                java.util.Map<String, String> headers) {
        return AudioDecoderFactory.openPublicResource(identifier, formatType, headers, float32Supported, multichannelSupported);
    }

    public Status getStatus() {
        return status.get();
    }

    private void setStatus(Status status) {
        if (this.status.get() != status) {
            this.status.set(status);
            statusChangeListener.forEach(c -> c.accept(status));
        }
    }

    private synchronized void fullyRetryCurrent(long expectedGeneration,
                                   @Nullable CompletableFuture<ZonedDateTime> startPlayingFuture) {
        if (disposed || expectedGeneration == Long.MAX_VALUE
                || !playbackGeneration.compareAndSet(expectedGeneration, expectedGeneration + 1)) return;
        ZonedDateTime previousStart = serverStartTime;
        boolean wasDirect = directPlayback;
        MusicResourceInfo directResource = directMusicResourceInfo;
        PlaybackSession session = currentPlaybackSession;
        long retryGeneration = expectedGeneration + 1;
        scrobbleGate.activate(retryGeneration, session.isActive() ? session.sessionId() : null);
        listeningLedger.begin(retryGeneration, session.isActive() ? session.sessionId() : null, session.resourceInfo().getResolvedTrackReference());
        stopInternal();
        CompletableFuture.delayedExecutor(FULLY_RETRY_SLEEP_MS, TimeUnit.MILLISECONDS, MusicHud.EXECUTOR).execute(() -> {
            CompletableFuture<ZonedDateTime> retry;
            synchronized (PlaybackEngine.this) {
                if (disposed || retryGeneration != playbackGeneration.get()) return;
                retry = wasDirect && directResource != MusicResourceInfo.NONE
                        ? playAsyncInternal(MusicDetail.NONE, previousStart, retryGeneration)
                        : session.isActive()
                        ? playAsyncInternal(session.musicDetail(), session.startTime(), retryGeneration)
                        : CompletableFuture.failedFuture(new IllegalStateException("No playback session to retry"));
            }
            if (startPlayingFuture != null) retry.whenComplete((started, error) -> {
                if (error == null) startPlayingFuture.complete(started);
                else startPlayingFuture.completeExceptionally(error);
            });
        });
    }
    public synchronized CompletableFuture<ZonedDateTime> playSessionAsync(PlaybackSession playbackSession) {
        if (disposed) return CompletableFuture.failedFuture(new CancellationException("Playback engine was retired"));
        if (playbackSession == null || !playbackSession.isActive()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Public playback session is not active"));
        }
        boolean newSession = !playbackSession.sessionId().equals(currentPlaybackSession.sessionId());
        if (newSession || !playbackSession.resourceInfo().getResolvedTrackReference().equals(currentPlaybackSession.resourceInfo().getResolvedTrackReference())) {
            submitScrobble(playbackGeneration.get());
        }
        if (newSession) {
            preparedScrobble = TuneWeaveClientService.getInstance().prepareScrobble(playbackSession.musicDetail());
        }
        long generation = playbackGeneration.incrementAndGet();
        stopInternal();
        directPlayback = false;
        directMusicResourceInfo = MusicResourceInfo.NONE;
        currentPlaybackSession = playbackSession;
        scrobbleGate.activate(generation, playbackSession.sessionId());
        listeningLedger.begin(generation, playbackSession.sessionId(), playbackSession.resourceInfo().getResolvedTrackReference());
        return playAsyncInternal(playbackSession.musicDetail(), playbackSession.startTime(), generation);
    }

    public synchronized CompletableFuture<ZonedDateTime> playDirectAsync(
            String identifier, FormatType formatType, ZonedDateTime startTime) {
        if (disposed) return CompletableFuture.failedFuture(new CancellationException("Playback engine was retired"));
        submitScrobble(playbackGeneration.get());
        long generation = playbackGeneration.incrementAndGet();
        stopInternal();
        directPlayback = true;
        currentPlaybackSession = PlaybackSession.NONE;
        scrobbleGate.invalidate(generation);
        listeningLedger.begin(generation, null);
        directMusicResourceInfo = new MusicResourceInfo(
                0L,
                AudioFormatDetector.normalizeIdentifier(identifier),
                0,
                0L,
                formatType == null ? FormatType.AUTO : formatType,
                "",
                Fee.UNSET,
                0
        );
        return playAsyncInternal(MusicDetail.NONE, startTime, generation);
    }

    private @NotNull CompletableFuture<ZonedDateTime> playAsyncInternal(
            MusicDetail musicDetail, ZonedDateTime startTime, long generation) {
        if (disposed || generation != playbackGeneration.get()) {
            return CompletableFuture.failedFuture(
                    new CancellationException("Playback generation was superseded"));
        }
        try {
            currentMusicDetail = musicDetail;
            setStatus(Status.BUFFERING);

            source = AL10.alGenSources();
            checkALError("alGenSources");
            sourceContext = org.lwjgl.openal.ALC10.alcGetCurrentContext();
            ownedSource = OwnedAudioSources.INSTANCE.register(sourceContext, source);

            for (int i = 0; i < BUFFER_COUNT; i++) {
                buffers[i] = AL10.alGenBuffers();
                checkALError("alGenBuffers");
            }

            // 配置为非空间播放
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
            AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0);
            if (AL.getCapabilities().AL_SOFT_direct_channels) {
                AL10.alSourcei(source, SOFTDirectChannels.AL_DIRECT_CHANNELS_SOFT, AL10.AL_TRUE);
            }
            checkALError("source configuration");
            lastVolume = 1;
            float32Supported = OpenAlFormatSelector.supportsFloat32();
            multichannelSupported = AL.getCapabilities().AL_EXT_MCFORMATS;

            initialized.set(true);
        } catch (Exception e) {
            stopInternal();
            try {
                Thread.sleep(PLAY_INIT_RETRY_SLEEP_MS);
            } catch (Exception ignored) {
            }
            if (generation != playbackGeneration.get()) {
                return CompletableFuture.failedFuture(
                        new CancellationException("Playback generation was superseded"));
            }
            return playAsyncInternal(musicDetail, startTime, generation);
        }

        CompletableFuture<Void> downloadInitializedFuture = new CompletableFuture<>();
        CompletableFuture<ZonedDateTime> startPlayingFuture = new CompletableFuture<>();
        serverStartTime = startTime;
        downloadFuture = new CompletableFuture<>();
        playingFuture = new CompletableFuture<>();
        downloadThreadFuture = MusicHud.EXECUTOR.submit(() -> {
            Thread.currentThread().setName("MHWorker-Downloader");
            try {
                downloadAudioWithRetry(startTime != null, downloadInitializedFuture, generation);
            } catch (Exception e) {
                if (generation != playbackGeneration.get()) {
                    return;
                }
                LOGGER.error("Download thread error", e);
                setStatus(Status.ERROR);
                if (e instanceof TerminalDownloadException) {
                    Throwable cause = Objects.requireNonNullElse(e.getCause(), e);
                    downloadInitializedFuture.completeExceptionally(cause);
                    startPlayingFuture.completeExceptionally(cause);
                    return;
                }
                try {
                    fullyRetryCurrent(generation, startPlayingFuture);
                } catch (RuntimeException e1) {
                    LOGGER.error("Retry failed: {}: {}", e1.getClass(), e1.getMessage());
                }
            } finally {
                downloadInitializedFuture.complete(null);
            }
        });
        downloadInitializedFuture.thenAccept(ignore -> {
            if (generation != playbackGeneration.get()) {
                return;
            }
            playThreadFuture = MusicHud.EXECUTOR.submit(() -> {
                Thread.currentThread().setName("MH-MusicPlayer");
                try {
                    playAudioWithRetry(startPlayingFuture, startTime, generation);
                } catch (Exception e) {
                    LOGGER.error("Play thread error: {}", e.getMessage());
                    if (!startPlayingFuture.isDone()) {
                        startPlayingFuture.completeExceptionally(e);
                    }
                }
            });
        });

        return startPlayingFuture;
    }

    @SuppressWarnings("BusyWait")
    private void playAudioWithRetry(CompletableFuture<ZonedDateTime> startPlayingFuture,
                                    ZonedDateTime serverStartTime, long generation) {
        CompletableFuture<?> currentPlayingFuture = playingFuture;
        CompletableFuture<?> currentDownloadFuture = downloadFuture;
        BlockingQueue<byte[]> playBuffer = audioBuffer;
        boolean finished = false;
        try {
            // 等待一些数据缓冲
            while (currentPlayingFuture != null && !currentPlayingFuture.isDone()
                    && currentPlayingFuture == playingFuture
                    && !currentDownloadFuture.isDone()
                    && generation == playbackGeneration.get()
                    && totalBufferedBytes.get() < BUFFER_SIZE * BUFFER_COUNT) {
                Thread.sleep(INITIAL_BUFFER_WAIT_SLEEP_MS);
            }

            if (totalBufferedBytes.get() == 0) {
                LOGGER.error("No audio data available");
                if (currentPlayingFuture != null && playingFuture != null) {
                    setStatus(Status.ERROR);
                }
                fullyRetryCurrent(generation, startPlayingFuture);
            } else {
                if (!initialized.get() || source == 0) {
                    startPlayingFuture.completeExceptionally(new IllegalStateException("Audio player not initialized"));
                    finished = true;
                } else {// 从缓冲区填充初始数据
                    boolean firstChunk = true;
                    for (int i = 0; i < BUFFER_COUNT; i++) {
                        byte[] audioData = playBuffer.poll(0, TimeUnit.SECONDS);
                        if (audioData == null) break;

                        if (firstChunk) {
                            firstChunk = false;
                            // 锚定内容绝对位置：服务器同步时下载线程已把解码器跳到
                            // 墙钟 - 初始缓冲补偿，fedBytes 从这里开始累计，之后
                            // 与 expectedBytes（墙钟绝对位置）基准一致
                            if (serverStartTime != null && currentDecoder != null) {
                                long bytesPerSecond = (long) currentDecoder.getSampleRate()
                                        * getBytesPerSample(currentDecoder.getFormat());
                                fedBytes = Duration.between(serverStartTime, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000
                                        - (long) BUFFER_SIZE * BUFFER_COUNT / 2;
                                fedBytes = Math.max(0, fedBytes);
                            }
                        }

                        ByteBuffer directBuffer = ByteBuffer.allocateDirect(audioData.length);
                        directBuffer.put(audioData);
                        directBuffer.flip();

                        int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
                        int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;

                        configureChannelVirtualization(format);
                        AL10.alBufferData(buffers[i], format, directBuffer, sampleRate);
                        checkALError("alBufferData-Pre");
                        AL10.alSourceQueueBuffers(source, buffers[i]);
                        checkALError("alSourceQueueBuffers-Pre");
                        listeningLedger.queue(generation, buffers[i], audioData.length,
                                (long) sampleRate * getBytesPerSample(format), true);

                        fedBytes += audioData.length;
                        totalBufferedBytes.addAndGet(-audioData.length);
                    }
                    if (clientConfig.getDisableVanillaMusic())
                        Minecraft.getInstance().getSoundManager().stop(null, SoundSource.MUSIC);
                    setStatus(Status.PLAYING);
                    updateVolumeIfNecessary();
                    AL10.alSourcePlay(source);
                    checkALError("alSourcePlay-Pre");
                }

                if (!finished) {
                    ZonedDateTime wallStart = Objects.requireNonNullElseGet(serverStartTime, ZonedDateTime::now);
                    this.serverStartTime = wallStart;
                    long lastIterationNanos = -1;
                    while (currentPlayingFuture != null && !currentPlayingFuture.isDone()
                            && currentPlayingFuture == playingFuture
                            && generation == playbackGeneration.get()) {
                        try {
                            if (sourceContext != org.lwjgl.openal.ALC10.alcGetCurrentContext()) {
                                throw new IllegalStateException("OpenAL context changed during playback");
                            }
                            long iterationNow = System.nanoTime();
                            if (lastIterationNanos > 0) {
                                long stallMs = (iterationNow - lastIterationNanos) / 1_000_000;
                                if (stallMs > PLAYBACK_STALL_LOG_MS) {
                                    // GC STW / 调度暂停：本地线程被冻结，恢复后第一轮间隔 = 冻结时长
                                    int bytesPerSample = currentDecoder != null ? getBytesPerSample(currentDecoder.getFormat()) : 4;
                                    long bytesPerSecond = (long) (currentDecoder != null ? currentDecoder.getSampleRate() : 44100) * bytesPerSample;
                                    long expected = Duration.between(wallStart, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000;
                                    LOGGER.info("Playback thread stalled for {} ms (GC STW / scheduling pause?)" +
                                                    " [content={} ms expected={} ms bufferedChunks={} processed={} state={}]",
                                            stallMs,
                                            fedBytes * 1000L / bytesPerSecond, expected * 1000L / bytesPerSecond,
                                            audioBuffer.size(),
                                            AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED),
                                            AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE));
                                    // 注意：这里不要清空 audioBuffer 或重置 fedBytes——那会破坏内容连续性
                                    // （下载线程的解码器头不会回退），导致后续排队的内容与墙钟脱节。
                                    // refill 的陈旧判定会自然丢弃 [内容位置, 墙钟-余量) 的过期内容，
                                    // 剩余内容按序播放，与墙钟的偏差保持在余量之内。
                                }
                            }
                            lastIterationNanos = iterationNow;

                            updateVolumeIfNecessary();
                            if (!initialized.get() || source == 0) break;

                            checkDecoderChangeAndFlush(generation);

                            int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
                            //noinspection SpellCheckingInspection
                            checkALError("alGetSourcei-Processed");

                            startPlayingFuture.complete(wallStart);

                            int bytesPerSample = currentDecoder != null ? getBytesPerSample(currentDecoder.getFormat()) : 4;
                            long bytesPerSecond = (long) (currentDecoder != null ? currentDecoder.getSampleRate() : 44100) * bytesPerSample;
                            long expectedBytes = Duration.between(wallStart, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000;
                            long staleDropMarginBytes = STALE_DROP_MARGIN_MS * bytesPerSecond / 1000;

                            while (processed-- > 0) {
                                int[] buffer = new int[1];
                                AL10.alSourceUnqueueBuffers(source, buffer);
                                //noinspection SpellCheckingInspection
                                checkALError("alSourceUnqueueBuffers-Main");
                                listeningLedger.processed(generation, buffer[0]);

                                byte[] audioData = playBuffer.poll(0, TimeUnit.MILLISECONDS);
                                while (audioData != null && fedBytes < expectedBytes - staleDropMarginBytes) {
                                    // 陈旧内容：落后墙钟超过余量，丢弃而不是排入 OpenAL 源。
                                    // 下载线程的 syncPlaying 保证解码器头 ≥ 墙钟，新鲜内容随后到达。
                                    totalBufferedBytes.addAndGet(-audioData.length);
                                    fedBytes += audioData.length;
                                    logStaleDrop(audioData.length, bytesPerSecond);
                                    audioData = playBuffer.poll(0, TimeUnit.MILLISECONDS);
                                }

                                boolean bufferedAudio = audioData != null;
                                if (audioData == null) {
                                    if (!currentDownloadFuture.isDone()) {
                                        audioData = new byte[BUFFER_SIZE];
                                        if (status.get() != Status.ERROR && status.get() != Status.RETRYING) {
                                            setStatus(Status.BUFFERING);
                                        }
                                    } else {
                                        continue;
                                    }
                                } else {
                                    setStatus(Status.PLAYING);
                                }

                                ByteBuffer directBuffer = ByteBuffer.allocateDirect(audioData.length);
                                directBuffer.put(audioData);
                                directBuffer.flip();

                                int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
                                int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;

                                configureChannelVirtualization(format);
                                AL10.alBufferData(buffer[0], format, directBuffer, sampleRate);
                                checkALError("alBufferData-Main");
                                AL10.alSourceQueueBuffers(source, buffer[0]);
                                checkALError("alSourceQueueBuffers-Main");
                                listeningLedger.queue(generation, buffer[0], audioData.length,
                                        (long) sampleRate * getBytesPerSample(format), bufferedAudio);

                                // Silence also advances the wall-clock-aligned feed position.
                                fedBytes += audioData.length;
                                if (bufferedAudio) {
                                    totalBufferedBytes.addAndGet(-audioData.length);
                                }
                            }

                            int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
                            checkALError("alGetSourcei-Queued");
                            listeningLedger.observe(generation, AL10.alGetSourcef(source, org.lwjgl.openal.AL11.AL_SEC_OFFSET), lastVolume > 0);
                            if (currentDownloadFuture.isDone() && playBuffer.isEmpty() && queued == 0) {
                                LOGGER.debug("Audio playback completed");
                                submitScrobble(generation);
                                currentPlayingFuture.complete(null);
                                setStatus(Status.IDLE);
                                break;
                            }

                            int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
                            //noinspection SpellCheckingInspection
                            checkALError("alGetSourcei-SourceState");
                            if (queued > 0 && state != AL10.AL_PLAYING
                                    && !currentPlayingFuture.isDone() && currentPlayingFuture == playingFuture) {
                                AL10.alSourcePlay(source);
                                checkALError("alSourcePlay-Main");
                            }
                            Thread.sleep(PLAY_LOOP_SLEEP_MS);
                        } catch (InterruptedException e) {
                            break;
                        } catch (Exception e) {
                            LOGGER.error("Playback error: {}", e.getMessage(), e);
                            try {
                                fullyRetryCurrent(generation, startPlayingFuture);
                            } catch (RuntimeException e1) {
                                break;
                            }
                            break;
                        }
                    }
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            LOGGER.error("Playback error: {}", e.getMessage(), e);
            fullyRetryCurrent(generation, startPlayingFuture);
        } finally {
            if (currentPlayingFuture != null) {
                currentPlayingFuture.complete(null);
            }
            LOGGER.debug("Play task finished");
        }
    }

    private void logStaleDrop(long droppedBytes, long bytesPerSecond) {
        long now = System.currentTimeMillis();
        if (now - lastStaleDropLogTime > 1000) {
            lastStaleDropLogTime = now;
            long expected = serverStartTime == null ? 0
                    : Duration.between(serverStartTime, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000;
            LOGGER.info("Dropped {} ms of stale audio [content now at {} ms, expected {} ms]",
                    droppedBytes * 1000L / bytesPerSecond,
                    fedBytes * 1000L / bytesPerSecond, expected * 1000L / bytesPerSecond);
        }
    }

    @SuppressWarnings("BusyWait")
    private void downloadAudioWithRetry(boolean forceSync,
                                        CompletableFuture<Void> downloadInitializedFuture,
                                        long generation) {
        CompletableFuture<?> currentPlayingFuture = playingFuture;
        CompletableFuture<?> currentDownloadFuture = downloadFuture;
        BlockingQueue<byte[]> localAudioBuffer = audioBuffer;

        int localRetryCount = 0;
        boolean forceSyncInternal = forceSync;
        boolean localDirectPlayback = directPlayback;
        MusicResourceInfo localDirectResource = directMusicResourceInfo;
        PlaybackSession localPlaybackSession = currentPlaybackSession;

        MusicResourceInfo musicResourceInfo = MusicResourceInfo.NONE;
        List<String> resourceUrls = List.of();
        int resourceUrlIndex = 0;
        boolean refreshResource = true;
        while (!currentDownloadFuture.isDone() && currentDownloadFuture == downloadFuture
                && generation == playbackGeneration.get()) {
            try {
                if (localDirectPlayback && refreshResource) {
                    musicResourceInfo = localDirectResource;
                    resourceUrls = musicResourceInfo.getCandidateUrls();
                    resourceUrlIndex = 0;
                    refreshResource = false;
                } else if (refreshResource) {
                    musicResourceInfo = localPlaybackSession.resourceInfo();
                    resourceUrls = musicResourceInfo.getCandidateUrls();
                    resourceUrlIndex = 0;
                    refreshResource = false;
                }
                currentResourceInfo = musicResourceInfo;
                if (resourceUrls.isEmpty()) throw new IllegalStateException("No audio resource URLs available");

                LOGGER.debug("Starting audio download (attempt {})", localRetryCount + 1);
                if (resourceUrlIndex > 0) {
                    LOGGER.info("Trying backup audio URL {}/{}", resourceUrlIndex, resourceUrls.size() - 1);
                }

                AudioDecoder decoder = localDirectPlayback
                        ? loadAudioDecoder(resourceUrls.get(resourceUrlIndex),
                        musicResourceInfo.getType(), musicResourceInfo.getHeaders())
                        : loadPublicAudioDecoder(resourceUrls.get(resourceUrlIndex),
                        musicResourceInfo.getType(), musicResourceInfo.getHeaders());
                synchronized (this) {
                    if (generation != playbackGeneration.get() || currentDownloadFuture.isDone()
                            || currentDownloadFuture != downloadFuture) {
                        decoder.close();
                        return;
                    }
                    if (!decoderSlot.adopt(generation, decoder)) return;
                    currentDecoder = decoder;
                    downloadInitializedFuture.complete(null);
                    if (status.get() != Status.ERROR && status.get() != Status.RETRYING) setStatus(Status.BUFFERING);
                }

                if (forceSyncInternal) {
                    syncPlaying(currentDownloadFuture, decoder, generation);
                }

                int initialBuffers = 0;
                while (!currentDownloadFuture.isDone() && currentDownloadFuture == downloadFuture && initialBuffers < BUFFER_COUNT * 2) {
                    byte[] audioData = decoder.readChunk(BUFFER_SIZE);
                    if (audioData == null) break;
                    if (currentDownloadFuture.isDone() || currentDownloadFuture != downloadFuture) break;

                    localAudioBuffer.put(audioData);
                    synchronized (this) {
                        if (generation != playbackGeneration.get() || currentDownloadFuture != downloadFuture) return;
                        playedBytes += audioData.length;
                        totalBufferedBytes.addAndGet(audioData.length);
                    }
                    initialBuffers++;
                }

                while (!currentDownloadFuture.isDone() && currentDownloadFuture == downloadFuture) {
                    // 保持解码器头与墙钟对齐（无论下载缓冲是否充足）
                    syncPlaying(currentDownloadFuture, decoder, generation);

                    byte[] audioData = decoder.readChunk(BUFFER_SIZE);
                    if (audioData == null) break;

                    if (currentPlayingFuture.isDone() || currentDownloadFuture != downloadFuture) break;

                    localAudioBuffer.put(audioData);
                    synchronized (this) {
                        if (generation != playbackGeneration.get() || currentDownloadFuture != downloadFuture) return;
                        playedBytes += audioData.length;
                        totalBufferedBytes.addAndGet(audioData.length);
                    }
                }

                // 下载完成
                LOGGER.debug("Audio download completed");
                currentDownloadFuture.complete(null);
                break;
            } catch (InterruptedException e) {
                LOGGER.debug("Download stopped by interruption");
                break;
            } catch (ArrayIndexOutOfBoundsException e) {
                LOGGER.debug("Download stopped by index out of bounds");
                break;
            } catch (Exception e) {
                if (generation != playbackGeneration.get() || currentDownloadFuture != downloadFuture || currentDownloadFuture.isDone()) return;
                if (e instanceof SocketException e1 && "Closed by interrupt".equals(e1.getMessage())) break;
                LOGGER.error("Download error (attempt {})\n{} : {}", localRetryCount + 1, e.getClass().getSimpleName(), e.getMessage());

                String failureMessage = e.getMessage();
                if (e.getCause() instanceof java.util.concurrent.TimeoutException
                        || (failureMessage != null && (failureMessage.contains("Timeout") || failureMessage.contains("timeout")))) {
                    failureMessage = I18n.get(MusicHud.MOD_ID + ".error.cause.timeout");
                }
                if (failureMessage == null || failureMessage.isBlank()) {
                    failureMessage = e.getClass().getSimpleName();
                }
                ToastUtil.show(I18n.get(MusicHud.MOD_ID + ".error.downloadingAudioStream")
                        .replace("{trial}", String.valueOf(localRetryCount + 1))
                        .replace("{message}", failureMessage));

                synchronized (this) {
                    if (generation != playbackGeneration.get() || currentDownloadFuture != downloadFuture) return;
                    localAudioBuffer.clear();
                    totalBufferedBytes.set(0);
                    playedBytes = 0;
                }
                forceSyncInternal = true;
                localRetryCount++;
                boolean hasBackup = resourceUrlIndex + 1 < resourceUrls.size();
                if (hasBackup) {
                    resourceUrlIndex++;
                } else {
                    resourceUrlIndex = 0;
                    if (!localDirectPlayback && localRetryCount >= 3
                            && generation == playbackGeneration.get()) {
                        PlaybackSession failedSession = localPlaybackSession;
                        IClientNetworkService.getInstance().sendToServer(
                                new PlaybackResourceFailureMessage(
                                        failedSession.sessionId(), failedSession.revision()));
                        throw new TerminalDownloadException(e);
                    }
                }
                synchronized (this) {
                    if (generation != playbackGeneration.get() || currentDownloadFuture != downloadFuture) return;
                    setStatus(Status.RETRYING);
                }

                try {
                    int delay = hasBackup ? 100 : Math.min(3000, localRetryCount * 500);
                    LOGGER.debug("Waiting {} ms before retry", delay);
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    LOGGER.debug("Download thread interrupted");
                    break;
                }
            }
        }

        LOGGER.debug("Download task finished");
    }

    private synchronized void submitScrobble(long generation) {
        if (generation != playbackGeneration.get()) return;
        PlaybackSession session = currentPlaybackSession;
        if (!session.isActive()) return;
        MusicDetail detail = session.musicDetail();
        MusicResourceInfo resource = session.resourceInfo();
        if (detail == null || resource == null || resource == MusicResourceInfo.NONE) return;
        long duration = resource.getTime() > 0 ? resource.getTime() : detail.getDurationMillis();
        long played = Math.min(duration, listeningLedger.playedMillis(generation));
        var localPlayer = Minecraft.getInstance().player;
        if (!clientConfig.getScrobbleMode().allows(detail.getPusherInfo().getPlayerUUID(),
                localPlayer == null ? null : localPlayer.getUUID(), played)) return;
        Quality quality = resource.getActualQuality();
        if (quality == null || quality == Quality.NONE) return;
        if (!scrobbleGate.claim(generation, TuneWeaveClientService.scrobbleEligible(detail, played, resource))) return;
        var submitter = preparedScrobble;
        if (submitter == null) return;
        MusicHud.EXECUTOR.execute(() -> {
            try {
                submitter.accept(played, resource);
            } catch (RuntimeException error) {
                LOGGER.debug("Scrobble submission failed", error);
            }
        });
    }

    private void syncPlaying(CompletableFuture<?> currentDownloadFuture, AudioDecoder decoder, long generation) {
        ZonedDateTime startTime = serverStartTime;
        if (startTime == null || decoder == null) return;
        int bytesPerSample = getBytesPerSample(decoder.getFormat());
        int bytesPerSecond = decoder.getSampleRate() * bytesPerSample;

        while (!currentDownloadFuture.isDone() && currentDownloadFuture == downloadFuture) {
            long millis = Duration.between(startTime, ZonedDateTime.now()).toMillis();
            long skipBytes = millis * bytesPerSecond / 1000;
            // Compensate for initial buffer latency: when no audio has been
            // buffered for playback yet, the play thread still needs to fill
            // BUFFER_SIZE * BUFFER_COUNT bytes before alSourcePlay, causing a
            // delay between sync and actual audio output. Subtract half the
            // buffer to avoid audio lagging behind the wall clock.
            if (playedBytes == 0) {
                long bufferLatencyBytes = (long) BUFFER_SIZE * BUFFER_COUNT / 2;
                skipBytes = Math.max(0, skipBytes - bufferLatencyBytes);
            }
            if (playedBytes > skipBytes - bytesPerSample) {
                break;
            }
            byte[] chunk = decoder.readChunk(Math.max(0, skipBytes - playedBytes));
            if (chunk == null) break;
            synchronized (this) {
                if (generation != playbackGeneration.get() || currentDownloadFuture != downloadFuture) return;
                playedBytes += chunk.length;
            }
        }
    }

    /**
     * The downloader may replace the decoder after a retry, and the new decoder
     * may produce a different format/sample rate. Queued buffers created by the
     * old decoder then sit in the source queue next to new-format data; the AL
     * mixer cannot continue past the format boundary, so the voice dies on it
     * and the refill deadlocks (processed stays 0). Detect the change and flush
     * the whole pipeline; the downloader's sync re-aligns the content.
     */
    private void checkDecoderChangeAndFlush(long generation) {
        if (currentDecoder == null) return;
        int format = currentDecoder.getFormat();
        int sampleRate = currentDecoder.getSampleRate();
        if (lastDecoderSampleRate == -1) {
            lastDecoderFormat = format;
            lastDecoderSampleRate = sampleRate;
            return;
        }
        if (format != lastDecoderFormat || sampleRate != lastDecoderSampleRate) {
            lastDecoderFormat = format;
            lastDecoderSampleRate = sampleRate;
            LOGGER.info("Decoder format changed to {}/{}Hz, flushing pipeline", format, sampleRate);
            listeningLedger.discardQueued(generation);
            if (source != 0) {
                AL10.alSourceStop(source);
                checkALError("alSourceStop-Flush");
                int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
                checkALError("alGetSourcei-Queued-Flush");
                for (int i = 0; i < queued; i++) {
                    int[] buffer = new int[1];
                    AL10.alSourceUnqueueBuffers(source, buffer);
                    checkALError("alSourceUnqueueBuffers-Flush");
                }
            }
            fedBytes = 0;
            audioBuffer.clear();
            totalBufferedBytes.set(0);
            // 用新格式的数据重新填充源队列（下载线程的 sync 会重新对齐内容位置）
            for (int i = 0; i < BUFFER_COUNT; i++) {
                byte[] audioData = audioBuffer.poll();
                if (audioData == null) break;

                ByteBuffer directBuffer = ByteBuffer.allocateDirect(audioData.length);
                directBuffer.put(audioData);
                directBuffer.flip();
                configureChannelVirtualization(format);
                AL10.alBufferData(buffers[i], format, directBuffer, sampleRate);
                checkALError("alBufferData-Flush");
                AL10.alSourceQueueBuffers(source, buffers[i]);
                checkALError("alSourceQueueBuffers-Flush");
                listeningLedger.queue(generation, buffers[i], audioData.length,
                        (long) sampleRate * getBytesPerSample(format), true);
                fedBytes += audioData.length;
            }
            AL10.alSourcePlay(source);
            checkALError("alSourcePlay-Flush");
        }
    }

    private void updateVolumeIfNecessary() {
        float musicVolume = clientConfig.getMuted() ? 0 : mixGain * (float) clientConfig.getSoundVolume() / 100 *
                (clientConfig.getMixWithVanillaSoundVolume() ? Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MUSIC) : 1);
        if (lastVolume != musicVolume && source != 0 && AL10.alIsSource(source)) {
            PlayingStatusRenderer.getInstance().updateStatus(null);
            AL10.alSourcef(source, AL10.AL_GAIN, musicVolume);
            int error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                LOGGER.warn("Failed to set source gain to {}: {} (source: {})", musicVolume, getALErrorString(error), source);
                if (error == AL10.AL_INVALID_NAME) {
                    LOGGER.error("Source {} is invalid, will be reinitialized on nextIdle play", source);
                    source = 0;
                    setStatus(Status.ERROR);
                } else {
                    setStatus(Status.ERROR);
                }
            } else {
                lastVolume = musicVolume;
            }
        }
    }

    private int getBytesPerSample(int format) {
        return OpenAlFormatSelector.frameSize(format);
    }

    private void configureChannelVirtualization(int format) {
        if (AL.getCapabilities().AL_SOFT_direct_channels) {
            // Matching-only direct output drops surround speakers on headphones. Let OpenAL map
            // multichannel buffers to the device's speakers/HRTF; keep authored stereo unchanged.
            AL10.alSourcei(source, SOFTDirectChannels.AL_DIRECT_CHANNELS_SOFT,
                    OpenAlFormatSelector.directChannels(format) ? AL10.AL_TRUE : AL10.AL_FALSE);
        }
    }

    private void checkALError(String operation) {
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            String errorMsg = getALErrorString(error);
            LOGGER.warn("OpenAL Error during {}: {} ({})", operation, errorMsg, error);
            throw new RuntimeException("al error occurred while \"" + operation + "\": " + errorMsg);
        }
    }

    private String getALErrorString(int error) {
        return switch (error) {
            case AL10.AL_INVALID_NAME -> "AL_INVALID_NAME";
            case AL10.AL_INVALID_ENUM -> "AL_INVALID_ENUM";
            case AL10.AL_INVALID_VALUE -> "AL_INVALID_VALUE";
            case AL10.AL_INVALID_OPERATION -> "AL_INVALID_OPERATION";
            case AL10.AL_OUT_OF_MEMORY -> "AL_OUT_OF_MEMORY";
            default -> "UNKNOWN_ERROR";
        };
    }

    @SneakyThrows
    public synchronized void stop() {
        long generation = playbackGeneration.get();
        submitScrobble(generation);
        scrobbleGate.invalidate(playbackGeneration.incrementAndGet());
        listeningLedger.begin(playbackGeneration.get(), null);
        stopInternal();
        directPlayback = false;
        directMusicResourceInfo = MusicResourceInfo.NONE;
        currentPlaybackSession = PlaybackSession.NONE;
        currentMusicDetail = MusicDetail.NONE;
        currentResourceInfo = MusicResourceInfo.NONE;
        serverStartTime = null;
        setStatus(Status.IDLE);
    }

    private void stopInternal() {
        OwnedAudioSources.INSTANCE.release(ownedSource);
        ownedSource = null;
        if (sourceContext != 0 && sourceContext != org.lwjgl.openal.ALC10.alcGetCurrentContext()) {
            source = 0;
            java.util.Arrays.fill(buffers, 0);
        }
        decoderSlot.advance(playbackGeneration.get());
        currentDecoder = null;
        if (downloadThreadFuture != null) {
            downloadThreadFuture.cancel(true);
            downloadThreadFuture = null;
        }

        if (playThreadFuture != null) {
            playThreadFuture.cancel(true);
            playThreadFuture = null;
        }

        if (playingFuture != null) {
            playingFuture.cancel(true);
            playingFuture = null;
        }

        if (downloadFuture != null) {
            downloadFuture.cancel(true);
            downloadFuture = null;
        }

        lastVolume = 1;
        serverStartTime = null;
        cleanup();
    }

    private void cleanup() {
        try {
            if (source != 0 && AL10.alIsSource(source)) {
                AL10.alSourceStop(source);
                int error = AL10.alGetError();
                if (error != AL10.AL_NO_ERROR) {
                    LOGGER.warn("Error stopping source {}: {}", source, getALErrorString(error));
                }

                int processed;
                try {
                    processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
                    error = AL10.alGetError();
                    if (error != AL10.AL_NO_ERROR) {
                        LOGGER.warn("Error getting processed buffers: {}", getALErrorString(error));
                        processed = BUFFER_COUNT;
                    }
                } catch (Exception e) {
                    LOGGER.warn("Exception getting processed buffers", e);
                    processed = BUFFER_COUNT;
                }

                //noinspection SpellCheckingInspection
                int unqueueCount = 0;
                for (int i = 0; i < processed; i++) {
                    int[] buffer = new int[1];
                    AL10.alSourceUnqueueBuffers(source, buffer);
                    error = AL10.alGetError();
                    if (error != AL10.AL_NO_ERROR) {
                        LOGGER.warn("Failed to unqueue buffer (attempt {}): {}", i + 1, getALErrorString(error));
                    } else {
                        unqueueCount++;
                    }
                }
                LOGGER.debug("Unqueued {} buffers", unqueueCount);

                AL10.alDeleteSources(source);
                error = AL10.alGetError();
                if (error != AL10.AL_NO_ERROR) {
                    LOGGER.warn("Failed to delete source {}: {}", source, getALErrorString(error));
                }
                source = 0;
            }

            for (int i = 0; i < buffers.length; i++) {
                if (buffers[i] != 0) {
                    if (AL10.alIsBuffer(buffers[i])) {
                        AL10.alDeleteBuffers(buffers[i]);
                        int error = AL10.alGetError();
                        if (error != AL10.AL_NO_ERROR) {
                            LOGGER.warn("Failed to delete buffer {}: {}", buffers[i], getALErrorString(error));
                        }
                    } else {
                        LOGGER.warn("Buffer {} is not a valid OpenAL buffer", buffers[i]);
                    }
                    buffers[i] = 0;
                }
            }

            initialized.set(false);
            lastVolume = 1;
            audioBuffer = new LinkedBlockingQueue<>(AUDIO_BUFFER_CAPACITY);
            totalBufferedBytes.set(0);
            playedBytes = 0;
            fedBytes = 0;
            lastDecoderFormat = -1;
            lastDecoderSampleRate = -1;
            serverStartTime = null;
            LOGGER.debug("Cleanup completed");
        } catch (Exception e) {
            LOGGER.error("Unexpected error during cleanup", e);
            AL10.alGetError();
        }
    }

private static final class TerminalDownloadException extends RuntimeException {
        private TerminalDownloadException(Throwable cause) {
            super(cause);
        }
    }
}
