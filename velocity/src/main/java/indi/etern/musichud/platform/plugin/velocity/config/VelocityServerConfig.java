package indi.etern.musichud.platform.plugin.velocity.config;

import indi.etern.musichud.interfaces.ServerConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Minimal TOML configuration at plugins/MusicHud/config.toml. */
public final class VelocityServerConfig implements ServerConfig {
    private static final VelocityServerConfig INSTANCE = new VelocityServerConfig();
    private static final String KEY_API_BASE_URL = "serverApiBaseUrl";
    private static final String KEY_MANAGE_TUNEWEAVE_INTERNALLY = "manageTuneWeaveInternally";
    private static final String KEY_PUSHER_VOTE_ADDITIONAL_RATE = "pusherVoteAdditionalRate";
    private static final String DEFAULT_API_BASE_URL = "http://127.0.0.1:7832";
    private static final double DEFAULT_PUSHER_VOTE_ADDITIONAL_RATE = 0.5D;

    private Path dataDirectory;
    private Path configFile;
    private String serverApiBaseUrl = DEFAULT_API_BASE_URL;
    private boolean manageTuneWeaveInternally;
    private double pusherVoteAdditionalRate = DEFAULT_PUSHER_VOTE_ADDITIONAL_RATE;
    private boolean configured;

    private VelocityServerConfig() {
    }

    public static VelocityServerConfig getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize(Path dataDirectory) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.configFile = dataDirectory.resolve("config.toml");
        try {
            Files.createDirectories(dataDirectory);
            Map<String, String> values = readToml();
            setServerApiBaseUrl(values.getOrDefault(KEY_API_BASE_URL, DEFAULT_API_BASE_URL));
            setManageTuneWeaveInternally(Boolean.parseBoolean(values.getOrDefault(KEY_MANAGE_TUNEWEAVE_INTERNALLY, "false")));
            try {
                setPusherVoteAdditionalRate(Double.parseDouble(values.getOrDefault(
                        KEY_PUSHER_VOTE_ADDITIONAL_RATE, Double.toString(DEFAULT_PUSHER_VOTE_ADDITIONAL_RATE))));
            } catch (NumberFormatException ignored) {
                setPusherVoteAdditionalRate(DEFAULT_PUSHER_VOTE_ADDITIONAL_RATE);
            }
            configured = true;
            save();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize Velocity MusicHud configuration", exception);
        }
    }

    private Map<String, String> readToml() throws IOException {
        if (!Files.exists(configFile)) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawLine : Files.readAllLines(configFile, StandardCharsets.UTF_8)) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).strip();
            String value = line.substring(separator + 1).strip();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
            }
            values.put(key, value);
        }
        return values;
    }

    @Override
    public String getServerApiBaseUrl() {
        return serverApiBaseUrl;
    }

    @Override
    public void setServerApiBaseUrl(String value) {
        serverApiBaseUrl = value == null || value.isBlank() ? DEFAULT_API_BASE_URL : value.trim();
    }

    @Override
    public boolean getManageTuneWeaveInternally() {
        return manageTuneWeaveInternally;
    }

    @Override
    public void setManageTuneWeaveInternally(boolean manageTuneWeaveInternally) {
        this.manageTuneWeaveInternally = manageTuneWeaveInternally;
    }

    @Override
    public double getPusherVoteAdditionalRate() {
        return pusherVoteAdditionalRate;
    }

    @Override
    public void setPusherVoteAdditionalRate(double value) {
        pusherVoteAdditionalRate = Math.max(0.0D, Math.min(1.0D, value));
    }

    @Override
    public synchronized void save() {
        try {
            Files.createDirectories(Objects.requireNonNull(dataDirectory, "Velocity server config is not initialized"));
            List<String> lines = List.of(
                    "# MusicHud Velocity configuration",
                    KEY_API_BASE_URL + " = \"" + serverApiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"") + "\"",
                    KEY_MANAGE_TUNEWEAVE_INTERNALLY + " = " + manageTuneWeaveInternally,
                    KEY_PUSHER_VOTE_ADDITIONAL_RATE + " = " + pusherVoteAdditionalRate
            );
            Files.write(configFile, lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save Velocity MusicHud configuration", exception);
        }
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public void setConfigured(boolean configured) {
        this.configured = configured;
    }
}
