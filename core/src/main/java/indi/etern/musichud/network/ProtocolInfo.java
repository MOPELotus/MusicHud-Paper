package indi.etern.musichud.network;

import indi.etern.musichud.Version;

import java.util.Set;

public final class ProtocolInfo {
    public static final String PROJECT_ID = "musichud-tuneweave";
    public static final Set<ProtocolCapability> CAPABILITIES = Set.of(
            ProtocolCapability.PUBLIC_PLAYBACK_SESSION);

    private ProtocolInfo() {
    }

    public static boolean isCompatible(String projectId, Version version,
                                       Set<ProtocolCapability> capabilities) {
        return PROJECT_ID.equals(projectId)
                && Version.compatibleWith(version)
                && capabilities != null
                && capabilities.containsAll(CAPABILITIES);
    }
}
