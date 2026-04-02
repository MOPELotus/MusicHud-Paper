package indi.etern.musichud.beans.music;

import indi.etern.musichud.client.music.decoder.AudioDecoder;
import indi.etern.musichud.client.music.decoder.AudioFormatDetector;
import indi.etern.musichud.client.music.decoder.FLACStreamDecoder;
import indi.etern.musichud.client.music.decoder.JavaSoundStreamDecoder;
import indi.etern.musichud.client.music.decoder.MP3StreamDecoder;
import indi.etern.musichud.client.music.decoder.OGGStreamDecoder;
import lombok.SneakyThrows;

import java.io.BufferedInputStream;
import java.util.Locale;

public enum FormatType {
    FLAC {
        @Override
        @SneakyThrows
        public AudioDecoder newDecoder(BufferedInputStream inputStream) {
            return new FLACStreamDecoder(inputStream);
        }
    },
    MP3 {
        @Override
        public AudioDecoder newDecoder(BufferedInputStream inputStream) {
            return new MP3StreamDecoder(inputStream);
        }
    },
    AUTO {
        @Override
        @SneakyThrows
        public AudioDecoder newDecoder(BufferedInputStream inputStream) {
            return AudioFormatDetector.detectFormat(inputStream).newDecoder(inputStream);
        }
    },
    WAV {
        @Override
        @SneakyThrows
        public AudioDecoder newDecoder(BufferedInputStream inputStream) {
            return new JavaSoundStreamDecoder(inputStream);
        }
    },
    OGG {
        @Override
        @SneakyThrows
        public AudioDecoder newDecoder(BufferedInputStream inputStream) {
            return new OGGStreamDecoder(inputStream);
        }
    },
    AIFF {
        @Override
        @SneakyThrows
        public AudioDecoder newDecoder(BufferedInputStream inputStream) {
            return new JavaSoundStreamDecoder(inputStream);
        }
    },
    AU {
        @Override
        @SneakyThrows
        public AudioDecoder newDecoder(BufferedInputStream inputStream) {
            return new JavaSoundStreamDecoder(inputStream);
        }
    },
    AAC {
        @Override
        @SneakyThrows
        public AudioDecoder newDecoder(BufferedInputStream inputStream) {
            return new JavaSoundStreamDecoder(inputStream);
        }
    },
    M4A {
        @Override
        @SneakyThrows
        public AudioDecoder newDecoder(BufferedInputStream inputStream) {
            return new JavaSoundStreamDecoder(inputStream);
        }
    },
    OPUS {
        @Override
        @SneakyThrows
        public AudioDecoder newDecoder(BufferedInputStream inputStream) {
            return new JavaSoundStreamDecoder(inputStream);
        }
    },
    GENERIC {
        @Override
        @SneakyThrows
        public AudioDecoder newDecoder(BufferedInputStream inputStream) {
            return new JavaSoundStreamDecoder(inputStream);
        }
    };

    public abstract AudioDecoder newDecoder(BufferedInputStream inputStream);

    public static FormatType fromSerializedName(String input) {
        if (input == null || input.isBlank()) {
            return AUTO;
        }
        String normalized = input.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "");
        if (normalized.contains("FLAC")) {
            return FLAC;
        }
        if (normalized.contains("MP3") || normalized.contains("MPEG")) {
            return MP3;
        }
        if (normalized.equals("WAV") || normalized.equals("WAVE") || normalized.contains("PCM")) {
            return WAV;
        }
        if (normalized.contains("OPUS")) {
            return OPUS;
        }
        if (normalized.contains("VORBIS") || normalized.contains("OGG") || normalized.contains("OGA")) {
            return OGG;
        }
        if (normalized.contains("AIFF") || normalized.contains("AIFC")) {
            return AIFF;
        }
        if (normalized.equals("AU") || normalized.contains("SUNAU")) {
            return AU;
        }
        if (normalized.contains("M4A") || normalized.contains("ALAC") || normalized.contains("MP4A")) {
            return M4A;
        }
        if (normalized.contains("AAC")) {
            return AAC;
        }
        return GENERIC;
    }
}
