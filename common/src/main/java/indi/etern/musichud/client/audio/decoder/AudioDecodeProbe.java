package indi.etern.musichud.client.audio.decoder;

import indi.etern.musichud.beans.music.FormatType;

public record AudioDecodeProbe(
        String identifier,
        FormatType declaredFormat,
        FormatType detectedFormat,
        String backend,
        int channelCount,
        int sampleRate,
        int openAlFormat,
        int probeBytesRead
) {
}
