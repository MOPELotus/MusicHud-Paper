package indi.etern.musichud.client.music.decoder;

import indi.etern.musichud.beans.music.FormatType;

import java.io.BufferedInputStream;

public final class AudioFormatDetector {
    private AudioFormatDetector() {
    }

    public static FormatType detectFormat(BufferedInputStream ignored) {
        return FormatType.MP3;
    }
}
