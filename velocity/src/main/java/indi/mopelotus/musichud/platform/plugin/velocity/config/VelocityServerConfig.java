package indi.mopelotus.musichud.platform.plugin.velocity.config;

import indi.mopelotus.musichud.server.ClientResolvedServerConfig;
import java.nio.file.*;
import java.io.*;
import java.util.Properties;

public final class VelocityServerConfig extends ClientResolvedServerConfig {
    private static final VelocityServerConfig INSTANCE = new VelocityServerConfig();
    private Path configFile;
    private final Properties properties = new Properties();
    private VelocityServerConfig() {}
    public static VelocityServerConfig getInstance() { return INSTANCE; }
    public synchronized void initialize(Path directory) {
        configFile = directory.resolve("config.properties");
        properties.clear();
        try {
            Files.createDirectories(directory);
            if (Files.exists(configFile)) try (var input = Files.newInputStream(configFile)) { properties.load(input); }
            try { setPusherVoteAdditionalRate(Double.parseDouble(properties.getProperty("pusherVoteAdditionalRate", ".5"))); }
            catch (NumberFormatException error) { setPusherVoteAdditionalRate(.5); }
            setConfigured(true);
            if (!Files.exists(configFile)) save();
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }
    public synchronized void save() {
        if (configFile == null) throw new IllegalStateException("Velocity config is not initialized");
        properties.setProperty("pusherVoteAdditionalRate", Double.toString(getPusherVoteAdditionalRate()));
        try (var output = Files.newOutputStream(configFile)) { properties.store(output, "MusicHud TuneWeave proxy configuration"); }
        catch (IOException error) { throw new UncheckedIOException(error); }
    }
}