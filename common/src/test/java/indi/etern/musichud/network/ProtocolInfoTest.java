package indi.etern.musichud.network;

import indi.etern.musichud.Version;
import indi.etern.musichud.network.payloads.requestResponseCycle.ConnectResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolInfoTest {
    @Test
    void requiresTuneWeaveIdentityAndPlaybackSessionCapability() {
        assertTrue(ProtocolInfo.isCompatible(
                ProtocolInfo.PROJECT_ID, Version.CURRENT, ProtocolInfo.CAPABILITIES));
        assertFalse(ProtocolInfo.isCompatible(
                "music_hud", Version.CURRENT, ProtocolInfo.CAPABILITIES));
        assertFalse(ProtocolInfo.isCompatible(
                ProtocolInfo.PROJECT_ID, Version.CURRENT, Set.of()));
    }

    @Test
    void rejectsPreBreakingProtocolVersions() {
        assertFalse(ProtocolInfo.isCompatible(
                ProtocolInfo.PROJECT_ID,
                new Version(1, 3, 0, Version.BuildType.Alpha),
                ProtocolInfo.CAPABILITIES));
        assertFalse(ProtocolInfo.isCompatible(
                ProtocolInfo.PROJECT_ID,
                new Version(3, 0, 0, Version.BuildType.Alpha),
                ProtocolInfo.CAPABILITIES));
    }

    @Test
    void handshakeResponseContainsOnlyProtocolIdentityAndCapabilities() {
        ConnectResponse source = ConnectResponse.current(true);
        ByteBuf buffer = Unpooled.buffer();
        try {
            ConnectResponse.CODEC.encode(buffer, source);
            ConnectResponse decoded = ConnectResponse.CODEC.decode(buffer);

            assertTrue(decoded.accepted());
            assertEquals(ProtocolInfo.PROJECT_ID, decoded.projectId());
            assertEquals(Version.CURRENT, decoded.serverVersion());
            assertEquals(ProtocolInfo.CAPABILITIES, decoded.capabilities());
        } finally {
            buffer.release();
        }
    }
}
