package indi.etern.musichud.client.audio.decoder;

import com.sedmelluq.discord.lavaplayer.format.AudioPlayerInputStream;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.lwjgl.openal.AL10;

import javax.sound.sampled.AudioInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class LavaplayerStreamDecoder implements AudioDecoder {
    private static final AudioPlayerManager PLAYER_MANAGER = createPlayerManager();
    private static final long LOAD_TIMEOUT_SECONDS = 20;
    private static final long STUCK_TIMEOUT_MILLIS = 10_000L;

    private final AudioPlayer player;
    private final AudioInputStream audioInputStream;
    private final int format;
    private final int sampleRate;
    private final int channelCount;
    private volatile boolean closed;

    private LavaplayerStreamDecoder(AudioPlayer player, AudioInputStream audioInputStream, int format, int sampleRate, int channelCount) {
        this.player = player;
        this.audioInputStream = audioInputStream;
        this.format = format;
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
    }

    public static LavaplayerStreamDecoder open(String identifier) throws IOException {
        AudioTrack track = loadTrack(identifier);
        AudioPlayer player = PLAYER_MANAGER.createPlayer();
        player.setVolume(100);
        player.playTrack(track);
        AudioInputStream audioInputStream = AudioPlayerInputStream.createStream(
                player,
                StandardAudioDataFormats.COMMON_PCM_S16_LE,
                STUCK_TIMEOUT_MILLIS,
                false
        );
        int channelCount = StandardAudioDataFormats.COMMON_PCM_S16_LE.channelCount;
        int sampleRate = StandardAudioDataFormats.COMMON_PCM_S16_LE.sampleRate;
        int format = channelCount == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
        return new LavaplayerStreamDecoder(player, audioInputStream, format, sampleRate, channelCount);
    }

    @Override
    public byte[] readChunk(long maxSize) {
        if (closed || maxSize <= 0) {
            return null;
        }
        int boundedSize = (int) Math.min(maxSize, Integer.MAX_VALUE);
        try {
            byte[] bytes = audioInputStream.readNBytes(boundedSize);
            return bytes.length == 0 ? null : bytes;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read decoded audio chunk", e);
        }
    }

    @Override
    public int getFormat() {
        return format;
    }

    @Override
    public int getSampleRate() {
        return sampleRate;
    }

    @Override
    public int getFrameSize() {
        return channelCount * Short.BYTES;
    }

    public int getChannelCount() {
        return channelCount;
    }

    public String getBackendName() {
        return "lavaplayer";
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            audioInputStream.close();
        } catch (IOException ignored) {
        }
        player.stopTrack();
        player.destroy();
    }

    private static AudioPlayerManager createPlayerManager() {
        DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
        manager.getConfiguration().setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_LE);
        manager.registerSourceManager(new HttpAudioSourceManager());
        manager.registerSourceManager(new LocalAudioSourceManager());
        return manager;
    }

    private static AudioTrack loadTrack(String identifier) throws IOException {
        CompletableFuture<AudioTrack> future = new CompletableFuture<>();
        PLAYER_MANAGER.loadItem(identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                future.complete(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack selectedTrack = playlist.getSelectedTrack();
                if (selectedTrack != null) {
                    future.complete(selectedTrack);
                    return;
                }
                if (!playlist.getTracks().isEmpty()) {
                    future.complete(playlist.getTracks().getFirst());
                    return;
                }
                future.completeExceptionally(new IOException("Playlist is empty: " + playlist.getName()));
            }

            @Override
            public void noMatches() {
                future.completeExceptionally(new IOException("No playable audio track found for: " + identifier));
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                future.completeExceptionally(exception);
            }
        });
        try {
            return future.get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while loading audio track", e);
        } catch (ExecutionException e) {
            Throwable cause = Objects.requireNonNullElse(e.getCause(), e);
            throw new IOException("Failed to load audio track: " + cause.getMessage(), cause);
        } catch (TimeoutException e) {
            throw new IOException("Timed out while loading audio track: " + identifier, e);
        }
    }
}
