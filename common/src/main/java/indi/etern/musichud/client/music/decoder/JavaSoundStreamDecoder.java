package indi.etern.musichud.client.music.decoder;

import org.lwjgl.openal.AL10;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;

public class JavaSoundStreamDecoder implements AudioDecoder {
    private final AudioInputStream audioInputStream;
    private final int format;
    private final int sampleRate;

    public JavaSoundStreamDecoder(BufferedInputStream inputStream) throws IOException {
        try {
            AudioInputStream sourceStream = AudioSystem.getAudioInputStream(inputStream);
            AudioFormat sourceFormat = sourceStream.getFormat();
            int channels = sourceFormat.getChannels();
            if (channels != 1 && channels != 2) {
                throw new IOException("Only mono/stereo audio is supported");
            }

            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.getSampleRate(),
                    16,
                    channels,
                    channels * 2,
                    sourceFormat.getSampleRate(),
                    false
            );

            if (AudioSystem.isConversionSupported(targetFormat, sourceFormat)) {
                this.audioInputStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream);
            } else if (isPcm16LittleEndian(sourceFormat)) {
                this.audioInputStream = sourceStream;
            } else {
                sourceStream.close();
                throw new IOException("Unsupported audio format conversion: " + sourceFormat);
            }

            AudioFormat resolvedFormat = this.audioInputStream.getFormat();
            this.sampleRate = Math.round(resolvedFormat.getSampleRate());
            this.format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Unsupported audio file", e);
        }
    }

    private static boolean isPcm16LittleEndian(AudioFormat format) {
        return AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                && format.getSampleSizeInBits() == 16
                && !format.isBigEndian()
                && (format.getChannels() == 1 || format.getChannels() == 2);
    }

    @Override
    public byte[] readChunk(long maxSize) {
        int chunkSize = (int) Math.max(4096, Math.min(Integer.MAX_VALUE, maxSize));
        byte[] buffer = new byte[chunkSize];
        try {
            int bytesRead = audioInputStream.read(buffer, 0, chunkSize);
            if (bytesRead <= 0) {
                return null;
            }
            if (bytesRead == buffer.length) {
                return buffer;
            }
            byte[] trimmed = new byte[bytesRead];
            System.arraycopy(buffer, 0, trimmed, 0, bytesRead);
            return trimmed;
        } catch (IOException e) {
            return null;
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
    public void close() {
        try {
            audioInputStream.close();
        } catch (IOException ignored) {
        }
    }
}
