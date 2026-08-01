package indi.etern.musichud.client.audio.decoder;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.FormatType;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;

public final class AudioDecoderFactory {
    private static final Logger LOGGER = MusicHud.getLogger(AudioDecoderFactory.class);
    private static final int DEFAULT_PROBE_BYTES = 65536;

    private AudioDecoderFactory() {
    }

    public static AudioDecoder open(String identifier, FormatType declaredFormat) {
        return open(identifier, declaredFormat, Map.of());
    }

    public static AudioDecoder open(String identifier, FormatType declaredFormat, Map<String, String> requestHeaders) {
        String normalizedIdentifier = AudioFormatDetector.normalizeIdentifier(identifier);
        try {
            return LavaplayerStreamDecoder.open(normalizedIdentifier, requestHeaders);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open audio decoder for: " + normalizedIdentifier, e);
        }
    }

    public static AudioDecodeProbe probe(String identifier, FormatType declaredFormat) throws IOException {
        String normalizedIdentifier = AudioFormatDetector.normalizeIdentifier(identifier);
        FormatType detectedFormat = AudioFormatDetector.detectFormat(normalizedIdentifier);
        try (LavaplayerStreamDecoder decoder = LavaplayerStreamDecoder.open(normalizedIdentifier)) {
            byte[] sample = decoder.readChunk(DEFAULT_PROBE_BYTES);
            int probeBytesRead = sample == null ? 0 : sample.length;
            return new AudioDecodeProbe(
                    normalizedIdentifier,
                    declaredFormat,
                    detectedFormat,
                    decoder.getBackendName(),
                    decoder.getChannelCount(),
                    decoder.getSampleRate(),
                    decoder.getFormat(),
                    probeBytesRead
            );
        } catch (IOException e) {
            LOGGER.debug("Failed to probe decoder for {}", normalizedIdentifier, e);
            throw e;
        }
    }
}

