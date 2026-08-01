package indi.etern.musichud.client.audio;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Fee;
import indi.etern.musichud.beans.music.FormatType;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.MusicResourceInfo;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.client.audio.decoder.*;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.hud.renderer.PlayingStatusRenderer;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.payloads.requestResponseCycle.GetMusicResourceRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.GetMusicResourceResponse;
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

import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class StreamAudioPlayer {
    private static final int BUFFER_COUNT = 4;
    private static final int BUFFER_SIZE = 65536;
    private static final long SYNC_TOLERANCE_MILLIS = 250L;
    private static final Logger LOGGER = MusicHud.getLogger(StreamAudioPlayer.class);
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static volatile StreamAudioPlayer instance = null;
    private final int[] buffers = new int[BUFFER_COUNT];
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<Status> status = new AtomicReference<>(Status.IDLE);
    @Getter
    private final Set<Consumer<Status>> statusChangeListener = new HashSet<>();
    private final AtomicLong totalBufferedBytes = new AtomicLong(0);
    private final AtomicLong bufferedAudioStartMillis = new AtomicLong(-1L);
    private volatile BlockingQueue<byte[]> audioBuffer = new LinkedBlockingQueue<>(30); // 最大30个数据块的缓冲区
    private int source = 0;
    private float lastVolume;
    private volatile CompletableFuture<?> playingFuture;
    private volatile CompletableFuture<?> downloadFuture;
    private volatile ZonedDateTime currentStartTime;
    private MusicDetail currentMusicDetail;
    private AudioDecoder currentDecoder;
    //    private boolean isBuffering = false;
    private volatile ZonedDateTime serverStartTime;
    private Future<?> downloadThreadFuture;
    private Future<?> playThreadFuture;
    private volatile boolean directPlayback;
    private volatile MusicResourceInfo directMusicResourceInfo = MusicResourceInfo.NONE;

    public static StreamAudioPlayer getInstance() {
        if (instance == null) {
            synchronized (StreamAudioPlayer.class) {
                if (instance == null) {
                    instance = new StreamAudioPlayer();
                }
            }
        }
        return instance;
    }

    private AudioDecoder loadAudioDecoder(String identifier, FormatType formatType) {
        return AudioDecoderFactory.open(identifier, formatType);
    }

    private AudioDecoder loadAudioDecoder(String identifier, FormatType formatType, Map<String, String> requestHeaders) {
        return AudioDecoderFactory.open(identifier, formatType, requestHeaders);
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

    protected void fullyRetryCurrent(@Nullable CompletableFuture<ZonedDateTime> startPlayingFuture) {
        MusicDetail currentMusicDetail1 = currentMusicDetail;
        ZonedDateTime startTime = currentStartTime != null ? currentStartTime : serverStartTime;
        boolean wasDirectPlayback = directPlayback;
        MusicResourceInfo directResourceInfo = directMusicResourceInfo;
        stopInternal();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        } finally {
            LOGGER.info("Fully retrying");
            CompletableFuture<ZonedDateTime> zonedDateTimeCompletableFuture = wasDirectPlayback && directResourceInfo != null
                    && !directResourceInfo.equals(MusicResourceInfo.NONE)
                    ? playDirectAsync(directResourceInfo.getUrl(), directResourceInfo.getType(), startTime)
                    : playAsyncInternal(currentMusicDetail1, startTime);
            if (startPlayingFuture != null) {
                zonedDateTimeCompletableFuture
                        .thenAccept(startPlayingFuture::complete)
                        .exceptionally(e -> {
                            startPlayingFuture.completeExceptionally(e);
                            return null;
                        });
            }
        }
    }

    public CompletableFuture<ZonedDateTime> playAsync(MusicDetail musicDetail, ZonedDateTime startTime) {
//        synchronized (StreamAudioPlayer.class) {
            currentStartTime = startTime == null ? ZonedDateTime.now() : startTime;
            directPlayback = false;
            directMusicResourceInfo = MusicResourceInfo.NONE;
            stopInternal();
            return playAsyncInternal(musicDetail, startTime);
//        }
    }

    public CompletableFuture<ZonedDateTime> playDirectAsync(String identifier, FormatType formatType, ZonedDateTime startTime) {
        currentStartTime = startTime == null ? ZonedDateTime.now() : startTime;
        directPlayback = true;
        directMusicResourceInfo = new MusicResourceInfo(
                0L,
                AudioFormatDetector.normalizeIdentifier(identifier),
                "{}",
                0,
                0L,
                formatType == null ? FormatType.AUTO : formatType,
                "",
                Fee.UNSET,
                0
        );
        stopInternal();
        return playAsyncInternal(MusicDetail.NONE, startTime);
    }

    private @NotNull CompletableFuture<ZonedDateTime> playAsyncInternal(MusicDetail musicDetail, ZonedDateTime startTime) {
        try {
            currentMusicDetail = musicDetail;
//            currentStartTime = startTime == null ? ZonedDateTime.now() : startTime;
            setStatus(Status.BUFFERING);

            source = AL10.alGenSources();
            checkALError("alGenSources");

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

            initialized.set(true);
        } catch (Exception e) {
            stopInternal();
            try {
                Thread.sleep(500);
            } catch (Exception ignored) {
            }
            return playAsyncInternal(musicDetail, startTime);
        }

        CompletableFuture<Void> downloadInitializedFuture = new CompletableFuture<>();
        CompletableFuture<ZonedDateTime> startPlayingFuture = new CompletableFuture<>();
        serverStartTime = startTime;
        downloadFuture = new CompletableFuture<>();
        playingFuture = new CompletableFuture<>();
        bufferedAudioStartMillis.set(-1L);
        downloadThreadFuture = MusicHud.EXECUTOR.submit(() -> {
            Thread.currentThread().setName("MHWorker-Downloader");
            try {
                downloadAudioWithRetry(startTime != null, downloadInitializedFuture);
            } catch (Exception e) {
                LOGGER.error("Download thread error", e);
                setStatus(Status.ERROR);
                try {
                    fullyRetryCurrent(startPlayingFuture);
                } catch (RuntimeException e1) {
                    LOGGER.error("Retry failed: {}: {}", e1.getClass(), e1.getMessage());
                }
            } finally {
                downloadInitializedFuture.complete(null);
            }
        });
        downloadInitializedFuture.thenAccept(ignore -> {
            playThreadFuture = MusicHud.EXECUTOR.submit(() -> {
                Thread.currentThread().setName("MH-MusicPlayer");
                try {
                    playAudioWithRetry(startPlayingFuture, startTime);
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
    private void playAudioWithRetry(CompletableFuture<ZonedDateTime> startPlayingFuture, ZonedDateTime serverStartTime) {
        CompletableFuture<?> currentPlayingFuture = playingFuture;
        CompletableFuture<?> currentDownloadFuture = downloadFuture;
        BlockingQueue<byte[]> playBuffer = audioBuffer;
        boolean finished = false;
        try {
            // 等待一些数据缓冲
            while (currentPlayingFuture != null && !currentPlayingFuture.isDone() && currentPlayingFuture == playingFuture && totalBufferedBytes.get() < BUFFER_SIZE * BUFFER_COUNT) {
                Thread.sleep(50);
            }

            if (totalBufferedBytes.get() == 0) {
                LOGGER.error("No audio data available");
                if (currentPlayingFuture != null && playingFuture != null) {
                    setStatus(Status.ERROR);
                }
                fullyRetryCurrent(startPlayingFuture);
            } else {
//                synchronized (StreamAudioPlayer.class) {
                    if (!initialized.get() || source == 0) {
                        startPlayingFuture.completeExceptionally(new IllegalStateException("Audio player not initialized"));
                        finished = true;
                    } else {// 从缓冲区填充初始数据
                        for (int i = 0; i < BUFFER_COUNT; i++) {
                            byte[] audioData = playBuffer.poll(0, TimeUnit.SECONDS);
                            if (audioData == null) break;

                            ByteBuffer directBuffer = ByteBuffer.allocateDirect(audioData.length);
                            directBuffer.put(audioData);
                            directBuffer.flip();

                            int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
                            int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;

                            AL10.alBufferData(buffers[i], format, directBuffer, sampleRate);
                            checkALError("alBufferData-Pre");
                            AL10.alSourceQueueBuffers(source, buffers[i]);
                            checkALError("alSourceQueueBuffers-Pre");

                            totalBufferedBytes.addAndGet(-audioData.length);
                        }
                        if (clientConfig.getDisableVanillaMusic())
                            Minecraft.getInstance().getSoundManager().stop(null, SoundSource.MUSIC);
                        setStatus(Status.PLAYING);
                        long sourcePositionMillis = Math.max(0L, bufferedAudioStartMillis.get());
                        ZonedDateTime outputStartedAt = ZonedDateTime.now();
                        AL10.alSourcePlay(source);
                        checkALError("alSourcePlay-Pre");
                        ZonedDateTime audibleTimelineStart = outputStartedAt.minus(Duration.ofMillis(sourcePositionMillis));
                        startPlayingFuture.complete(audibleTimelineStart);
                        long serverTimelineOffsetMillis = serverStartTime == null
                                ? 0L
                                : Duration.between(serverStartTime, audibleTimelineStart).toMillis();
                        LOGGER.info("Audio output started at source position {}ms; lyric timeline offset from server: {}ms",
                                sourcePositionMillis, serverTimelineOffsetMillis);
                    }

//                }
                if (!finished) {// 主播放循环
                    this.serverStartTime = Objects.requireNonNullElseGet(serverStartTime, ZonedDateTime::now);
                    while (currentPlayingFuture != null && !currentPlayingFuture.isDone() && currentPlayingFuture == playingFuture) {
                        try {
//                            synchronized (StreamAudioPlayer.class) {
                                updateVolumeIfNecessary();
                                if (!initialized.get() || source == 0) break;

                                int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
                                //noinspection SpellCheckingInspection
                                checkALError("alGetSourcei-Processed");

                                while (processed-- > 0) {
                                    int[] buffer = new int[1];
                                    AL10.alSourceUnqueueBuffers(source, buffer);
                                    //noinspection SpellCheckingInspection
                                    checkALError("alSourceUnqueueBuffers-Main");

                                    byte[] audioData = playBuffer.poll(0, TimeUnit.MILLISECONDS);

                                    if (audioData == null) {
                                        if (playBuffer.isEmpty() && NowPlayingInfo.getInstance().isCompleted()) {
                                            // 播放已完成且缓冲区为空，结束播放
                                            LOGGER.debug("No more audio data available");
                                            currentPlayingFuture.complete(null);
                                            setStatus(Status.PLAYING);
                                            break;
                                        } else if (!currentDownloadFuture.isDone()) {
                                            audioData = new byte[BUFFER_SIZE];
                                            if (status.get() != Status.ERROR && status.get() != Status.RETRYING) {
                                                setStatus(Status.BUFFERING);
                                            }
                                        } else {
                                            audioData = new byte[BUFFER_SIZE];
                                        }
                                    } else {
                                        setStatus(Status.PLAYING);
                                    }

                                    ByteBuffer directBuffer = ByteBuffer.allocateDirect(audioData.length);
                                    directBuffer.put(audioData);
                                    directBuffer.flip();

                                    int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
                                    int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;

                                    AL10.alBufferData(buffer[0], format, directBuffer, sampleRate);
                                    checkALError("alBufferData-Main");
                                    AL10.alSourceQueueBuffers(source, buffer[0]);
                                    checkALError("alSourceQueueBuffers-Main");

                                    if (audioData.length == BUFFER_SIZE) { // 不是静音数据
                                        totalBufferedBytes.addAndGet(-audioData.length);
                                    }
                                }

                                int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
                                //noinspection SpellCheckingInspection
                                checkALError("alGetSourcei-SourceState");
                                if (state != AL10.AL_PLAYING && !currentPlayingFuture.isDone() && currentPlayingFuture == playingFuture) {
                                    AL10.alSourcePlay(source);
                                    checkALError("alSourcePlay-Main");
                                }
//                            }
                            Thread.sleep(40);
                        } catch (InterruptedException e) {
                            break;
                        } catch (Exception e) {
                            LOGGER.error("Playback error: {}", e.getMessage(), e);
                            try {
                                fullyRetryCurrent(startPlayingFuture);
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
            fullyRetryCurrent(startPlayingFuture);
        } finally {
            if (currentPlayingFuture != null) {
                currentPlayingFuture.complete(null);
            }
            LOGGER.debug("Play task finished");
        }
    }

    @SuppressWarnings("BusyWait")
    private void downloadAudioWithRetry(boolean forceSync, CompletableFuture<Void> downloadInitializedFuture) {
        CompletableFuture<?> currentPlayingFuture = playingFuture;
        CompletableFuture<?> currentDownloadFuture = downloadFuture;
        BlockingQueue<byte[]> localAudioBuffer = audioBuffer;

        int localRetryCount = 0;
        boolean forceSyncInternal = forceSync;
        long decodedBytes = 0L;

        MusicResourceInfo musicResourceInfo = MusicResourceInfo.NONE;
        while (!currentDownloadFuture.isDone() && currentDownloadFuture == downloadFuture) {
            try {
                if (directPlayback) {
                    musicResourceInfo = directMusicResourceInfo;
                } else if (musicResourceInfo == null || musicResourceInfo.equals(MusicResourceInfo.NONE) || localRetryCount % 3 == 0) {
                    musicResourceInfo = getCurrentMusicResourceInfo(clientConfig.getPrimaryChosenQuality(), musicResourceInfo).get();
                }
                if (musicResourceInfo == null || musicResourceInfo.equals(MusicResourceInfo.NONE)) {
                    continue;
                }

                LOGGER.debug("Starting audio download (attempt {})", localRetryCount + 1);

                AudioDecoder decoder = loadAudioDecoder(musicResourceInfo.getUrl(), musicResourceInfo.getType(), musicResourceInfo.getRequestHeaders());
                if (currentDownloadFuture.isDone() || currentDownloadFuture != downloadFuture) {
                    decoder.close();
                    break;
                }
                currentDecoder = decoder;
                downloadInitializedFuture.complete(null);

                if (status.get() != Status.ERROR && status.get() != Status.RETRYING) {
                    setStatus(Status.BUFFERING);
                }

                if (forceSyncInternal) {
                    decodedBytes = syncPlaying(currentDownloadFuture, decoder, decodedBytes);
                }

                // 先填充一些数据到缓冲区
                int initialBuffers = 0;
                while (!currentDownloadFuture.isDone() && currentDownloadFuture == downloadFuture && initialBuffers < BUFFER_COUNT * 2) {
                    if (initialBuffers == 0 && localAudioBuffer.isEmpty()) {
                        bufferedAudioStartMillis.set(positionMillisForBytes(decoder, decodedBytes));
                    }
                    byte[] audioData = decoder.readChunk(BUFFER_SIZE);
                    if (audioData == null) break;
                    if (currentDownloadFuture.isDone() || currentDownloadFuture != downloadFuture) break;

                    localAudioBuffer.put(audioData);
                    decodedBytes += audioData.length;
                    totalBufferedBytes.addAndGet(audioData.length);
                    initialBuffers++;
                }

                // 继续下载剩余数据
                while (!currentDownloadFuture.isDone() && currentDownloadFuture == downloadFuture) {
                    if (localAudioBuffer.size() <= 1) {
                        decodedBytes = syncPlaying(currentDownloadFuture, decoder, decodedBytes);
                    }

                    byte[] audioData = decoder.readChunk(BUFFER_SIZE);
                    if (audioData == null) break;

                    if (currentPlayingFuture.isDone() || currentDownloadFuture != downloadFuture) break;

                    localAudioBuffer.put(audioData);
                    decodedBytes += audioData.length;
                    totalBufferedBytes.addAndGet(audioData.length);
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
                if (e instanceof SocketException e1 && e1.getMessage().equals("Closed by interrupt")) break;
                LOGGER.error("Download error (attempt {})\n{} : {}", localRetryCount + 1, e.getClass().getSimpleName(), e.getMessage());

                localAudioBuffer.clear();
                totalBufferedBytes.set(0);
                decodedBytes = 0L;
                forceSyncInternal = true;
                localRetryCount++;
                setStatus(Status.RETRYING);

                try {
                    // 等待重试延迟
                    int retryDelayAdditionalMs = 1000;
                    int delay = localRetryCount * retryDelayAdditionalMs;
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

    private long syncPlaying(CompletableFuture<?> currentDownloadFuture, AudioDecoder decoder, long decodedBytes) {
        if (serverStartTime == null || decoder == null
                || currentDownloadFuture.isDone() || currentDownloadFuture != downloadFuture) {
            return decodedBytes;
        }
        int bytesPerSample = getBytesPerSample(decoder.getFormat());
        int bytesPerSecond = decoder.getSampleRate() * bytesPerSample;
        if (bytesPerSecond <= 0) {
            return decodedBytes;
        }

        long targetPositionMillis = Math.max(0L, Duration.between(serverStartTime, ZonedDateTime.now()).toMillis());
        long decoderPositionMillis = decoder.getPositionMillis();
        if (decoderPositionMillis >= 0L
                && targetPositionMillis - decoderPositionMillis <= SYNC_TOLERANCE_MILLIS) {
            return decodedBytes;
        }
        if (decoder.seekToMillis(targetPositionMillis)) {
            LOGGER.info("Seeking audio decoder for {} ({}) from {}ms to {}ms for server sync",
                    currentMusicDetail == null ? "<null>" : currentMusicDetail.getName(),
                    currentMusicDetail == null ? 0L : currentMusicDetail.getId(),
                    decoderPositionMillis,
                    targetPositionMillis);
            return targetPositionMillis * bytesPerSecond / 1000L;
        }

        long targetBytes = targetPositionMillis * bytesPerSecond / 1000L;
        while (decodedBytes < targetBytes
                && !currentDownloadFuture.isDone() && currentDownloadFuture == downloadFuture) {
            long remaining = targetBytes - decodedBytes;
            byte[] chunk = decoder.readChunk(Math.min(BUFFER_SIZE, remaining));
            if (chunk == null) {
                break;
            }
            decodedBytes += chunk.length;
        }
        return decodedBytes;
    }

    private long positionMillisForBytes(AudioDecoder decoder, long decodedBytes) {
        int bytesPerSecond = decoder.getSampleRate() * getBytesPerSample(decoder.getFormat());
        return bytesPerSecond <= 0 ? 0L : Math.max(0L, decodedBytes * 1000L / bytesPerSecond);
    }

    private void updateVolumeIfNecessary() {
        float musicVolume = clientConfig.getMuted() ? 0 : (float) clientConfig.getSoundVolume() / 100 *
                (clientConfig.getMixWithVanillaSoundVolume() ? Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MUSIC) : 1);
        if (lastVolume != musicVolume && source != 0 && AL10.alIsSource(source)) {
            PlayingStatusRenderer.getInstance().updateStatus(null);
            AL10.alSourcef(source, AL10.AL_GAIN, musicVolume);
            int error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                LOGGER.warn("Failed to set source gain to {}: {} (source: {})", musicVolume, getALErrorString(error), source);
                // 可选：如果错误表明源无效，可以尝试重新创建源或标记为无效
                if (error == AL10.AL_INVALID_NAME) {
                    // 源可能已被删除，标记为无效，后续播放会重建
                    LOGGER.error("Source {} is invalid, will be reinitialized on nextIdle play", source);
                    source = 0; // 让下次播放时重新生成
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
        return switch (format) {
            case AL10.AL_FORMAT_MONO8 -> 1;
            case AL10.AL_FORMAT_MONO16, AL10.AL_FORMAT_STEREO8 -> 2;
            case AL10.AL_FORMAT_STEREO16 -> 4;
            default -> 4;
        };
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
    public void stop() {
        stopInternal();
        setStatus(Status.IDLE);
    }

    private void stopInternal() {
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
//        synchronized (StreamAudioPlayer.class) {
            try {
                // 停止播放并清除源相关资源
                if (source != 0 && AL10.alIsSource(source)) {
                    // 1. 停止源
                    AL10.alSourceStop(source);
                    int error = AL10.alGetError();
                    if (error != AL10.AL_NO_ERROR) {
                        LOGGER.warn("Error stopping source {}: {}", source, getALErrorString(error));
                    }

                    // 2. 获取已处理的缓冲区数量
                    int processed;
                    try {
                        processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
                        error = AL10.alGetError();
                        if (error != AL10.AL_NO_ERROR) {
                            LOGGER.warn("Error getting processed buffers: {}", getALErrorString(error));
                            // 不清零 processed，后续循环尝试最大次数
                            processed = BUFFER_COUNT; // 保守估计所有缓冲区都已处理
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Exception getting processed buffers", e);
                        processed = BUFFER_COUNT;
                    }

                    // 3. 解绑所有已处理的缓冲区
                    //noinspection SpellCheckingInspection
                    int unqueueCount = 0;
                    for (int i = 0; i < processed; i++) {
                        int[] buffer = new int[1];
                        AL10.alSourceUnqueueBuffers(source, buffer);
                        error = AL10.alGetError();
                        if (error != AL10.AL_NO_ERROR) {
                            LOGGER.warn("Failed to unqueue buffer (attempt {}): {}", i + 1, getALErrorString(error));
                            // 错误后继续尝试，不清除缓冲区，但可能会残留
                        } else {
                            unqueueCount++;
                            // 可选：标记该缓冲区可以被删除
                        }
                    }
                    LOGGER.debug("Unqueued {} buffers", unqueueCount);

                    // 4. 删除源
                    AL10.alDeleteSources(source);
                    error = AL10.alGetError();
                    if (error != AL10.AL_NO_ERROR) {
                        LOGGER.warn("Failed to delete source {}: {}", source, getALErrorString(error));
                    }
                    source = 0;
                }

                // 删除所有缓冲区
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
                audioBuffer = new LinkedBlockingQueue<>(30);
                totalBufferedBytes.set(0);
                bufferedAudioStartMillis.set(-1L);
                serverStartTime = null;
                if (currentDecoder != null) {
                    try {
                        currentDecoder.close();
                    } catch (Exception ignored) {
                    }
                    currentDecoder = null;
                }
                LOGGER.debug("Cleanup completed");
            } catch (Exception e) {
                LOGGER.error("Unexpected error during cleanup", e);
                // 确保 OpenAL 错误状态被清除，防止污染
                AL10.alGetError();
            }
//        }
    }

    public CompletableFuture<MusicResourceInfo> getCurrentMusicResourceInfo(Quality quality, MusicResourceInfo previous) {
        CompletableFuture<MusicResourceInfo> future = new CompletableFuture<>();
        GetMusicResourceResponse.setReceiver(currentMusicDetail.getId(), value -> {
            if (value == MusicResourceInfo.NONE) {
                MusicService.getInstance().switchMusic(MusicDetail.NONE, MusicDetail.NONE, null,
                        I18n.get(MusicHud.MOD_ID + ".text.failedToLoadMusicResource"));
                setStatus(Status.ERROR);
                future.completeExceptionally(new IOException("Failed to load music resource"));
                return;
            }
            future.complete(value);
        });
        String url = previous == null || previous.getUrl() == null ? "" : previous.getUrl();
        IClientNetworkService.getInstance().sendToServer(
                new GetMusicResourceRequest(currentMusicDetail.getId(), quality, url));
        return future;
    }

    public enum Status {
        IDLE, BUFFERING, PLAYING, RETRYING, ERROR
    }
}
