package indi.etern.musichud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionTest {
    @Test
    void enforcesMinimumCompatibleVersion() {
        assertTrue(Version.compatibleWith(Version.leastCompatible));
        assertTrue(Version.compatibleWith(new Version(1, 4, 0, Version.BuildType.Alpha)));
        assertFalse(Version.compatibleWith(new Version(1, 2, 99, Version.BuildType.Stable)));
    }
}
