package indi.etern.musichud.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ArchitecturyPayloadCodecTest {
    @Test
    void wrapsAndUnwrapsPayloadWithoutLoss() {
        byte[] payload = new byte[]{0, 1, 2, 127, -1, 42};

        byte[] wrapped = ArchitecturyPayloadCodec.wrapPayload(payload);

        assertArrayEquals(payload, ArchitecturyPayloadCodec.unwrapPayload(wrapped));
    }

    @Test
    void supportsEmptyPayloads() {
        byte[] wrapped = ArchitecturyPayloadCodec.wrapPayload(new byte[0]);

        assertArrayEquals(new byte[0], ArchitecturyPayloadCodec.unwrapPayload(wrapped));
    }
}
