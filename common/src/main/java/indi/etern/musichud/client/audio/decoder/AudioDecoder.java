package indi.etern.musichud.client.audio.decoder;

public interface AudioDecoder extends AutoCloseable {
    byte[] readChunk(long maxSize);

    default boolean seekToMillis(long positionMillis) {
        return false;
    }

    default long getPositionMillis() {
        return -1L;
    }

    int getFormat();
    int getSampleRate();

    int getFrameSize();

    void close();
}
