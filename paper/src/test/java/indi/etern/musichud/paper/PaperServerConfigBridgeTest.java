package indi.etern.musichud.paper;

import indi.etern.musichud.server.config.ServerConfigDefinition;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperServerConfigBridgeTest {
    @Test
    void applyWritesMissingDefaultsIntoPaperConfig() {
        MemoryConfiguration config = new MemoryConfiguration();

        boolean changed = PaperServerConfigBridge.apply(config);

        assertTrue(changed);
        assertEquals(ServerConfigDefinition.serverApiBaseUrl.getDefaultValue(), config.getString("serverApiBaseUrl"));
        assertEquals(0.5D, config.getDouble("pusherVoteAdditionalRate"));
        assertTrue(config.getBoolean("useRandomCnIp"));
    }

    @Test
    void applyLoadsExistingValuesIntoSharedConfigValues() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("serverApiBaseUrl", "https://unit.test/api");
        config.set("startupBinaryApiServerWhenLaunch", false);
        config.set("serverApiBinaryExecutablePath", "unit-test/api");
        config.set("pusherVoteAdditionalRate", 0.75D);
        config.set("useRandomCnIp", false);

        boolean changed = PaperServerConfigBridge.apply(config);
        ServerConfigDefinition definition = ServerConfigDefinition.configure.getLeft();

        assertFalse(changed);
        assertEquals("https://unit.test/api", definition.serverApiBaseUrl.get());
        assertEquals(0.75D, definition.pusherVoteAdditionalRate.get());
        assertFalse(definition.useRandomCnIp.get());
    }

    @Test
    void applyFallsBackToDefaultsWhenExistingValuesAreInvalid() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("serverApiBaseUrl", 114514);
        config.set("startupBinaryApiServerWhenLaunch", true);
        config.set("serverApiBinaryExecutablePath", "unit-test/api");
        config.set("pusherVoteAdditionalRate", 10D);
        config.set("useRandomCnIp", "nope");

        boolean changed = PaperServerConfigBridge.apply(config);
        ServerConfigDefinition definition = ServerConfigDefinition.configure.getLeft();

        assertFalse(changed);
        assertEquals(ServerConfigDefinition.serverApiBaseUrl.getDefaultValue(), definition.serverApiBaseUrl.get());
        assertEquals(0.5D, definition.pusherVoteAdditionalRate.get());
        assertTrue(definition.useRandomCnIp.get());
    }
}
