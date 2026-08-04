package indi.etern.musichud.platform.plugin.velocity.config;

import indi.etern.musichud.interfaces.ServerConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;

public final class VelocityServerConfig implements ServerConfig {
    private static final VelocityServerConfig INSTANCE = new VelocityServerConfig();

    private static final String KEY_API_BASE_URL = "serverApiBaseUrl";
    private static final String KEY_STARTUP_BINARY_API_SERVER = "startupBinaryApiServerWhenLaunch";
    private static final String KEY_API_BINARY_EXECUTABLE_PATH = "serverApiBinaryExecutablePath";
    private static final String KEY_PUSHER_VOTE_ADDITIONAL_RATE = "pusherVoteAdditionalRate";
    private static final String KEY_USE_RANDOM_CN_IP = "useRandomCnIp";
    private static final String KEY_CORS_ALLOW_ORIGIN = "corsAllowOrigin";
    private static final String KEY_ENABLE_PROXY = "enableProxy";
    private static final String KEY_PROXY_URL = "proxyUrl";
    private static final String KEY_ENABLE_GENERAL_UNBLOCK = "enableGeneralUnblock";
    private static final String KEY_ENABLE_FLAC = "enableFlac";
    private static final String KEY_SELECT_MAX_BR = "selectMaxBr";
    private static final String KEY_FOLLOW_SOURCE_ORDER = "followSourceOrder";
    private static final String KEY_PORT = "port";

    private final Properties properties = new Properties();
    private Path dataDirectory;
    private Path configFile;
    private String serverApiBaseUrl = "http://localhost:3000";
    private boolean startupBinaryApiServerWhenLaunch = true;
    private String serverApiBinaryExecutablePath = "api";
    private double pusherVoteAdditionalRate = 0.5D;
    private boolean useRandomCnIp = true;
    private String corsAllowOrigin = "*";
    private boolean enableProxy;
    private String proxyUrl = "https://your-proxy-url.com/?proxy=";
    private boolean enableGeneralUnblock = true;
    private boolean enableFlac = true;
    private boolean selectMaxBr;
    private boolean followSourceOrder = true;
    private int port = 3000;
    private boolean configured;

    private VelocityServerConfig() {
    }

    public static VelocityServerConfig getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.configFile = dataDirectory.resolve("config.properties");
        properties.clear();
        try {
            Files.createDirectories(dataDirectory);
            if (Files.exists(configFile)) {
                try (InputStream input = Files.newInputStream(configFile)) {
                    properties.load(input);
                }
            }
            load();
            configured = true;
            save();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize Velocity MusicHud configuration", exception);
        }
    }

    private void load() {
        serverApiBaseUrl = value(KEY_API_BASE_URL, serverApiBaseUrl);
        startupBinaryApiServerWhenLaunch = bool(KEY_STARTUP_BINARY_API_SERVER, startupBinaryApiServerWhenLaunch);
        serverApiBinaryExecutablePath = value(KEY_API_BINARY_EXECUTABLE_PATH, serverApiBinaryExecutablePath);
        pusherVoteAdditionalRate = clamp(doubleValue(KEY_PUSHER_VOTE_ADDITIONAL_RATE, pusherVoteAdditionalRate));
        useRandomCnIp = bool(KEY_USE_RANDOM_CN_IP, useRandomCnIp);
        corsAllowOrigin = value(KEY_CORS_ALLOW_ORIGIN, corsAllowOrigin);
        enableProxy = bool(KEY_ENABLE_PROXY, enableProxy);
        proxyUrl = value(KEY_PROXY_URL, proxyUrl);
        enableGeneralUnblock = bool(KEY_ENABLE_GENERAL_UNBLOCK, enableGeneralUnblock);
        enableFlac = bool(KEY_ENABLE_FLAC, enableFlac);
        selectMaxBr = bool(KEY_SELECT_MAX_BR, selectMaxBr);
        followSourceOrder = bool(KEY_FOLLOW_SOURCE_ORDER, followSourceOrder);
        port = portValue(KEY_PORT, port);
    }

    private String value(String key, String fallback) {
        String value = properties.getProperty(key, fallback);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private boolean bool(String key, boolean fallback) {
        return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(fallback)));
    }

    private double doubleValue(String key, double fallback) {
        try {
            return Double.parseDouble(properties.getProperty(key, Double.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int portValue(String key, int fallback) {
        try {
            int value = Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
            return value > 0 && value <= 65535 ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    @Override public String getServerApiBaseUrl() { return serverApiBaseUrl; }
    @Override public void setServerApiBaseUrl(String value) { serverApiBaseUrl = value; }
    @Override public boolean getStartupBinaryApiServerWhenLaunch() { return startupBinaryApiServerWhenLaunch; }
    @Override public void setStartupBinaryApiServerWhenLaunch(boolean value) { startupBinaryApiServerWhenLaunch = value; }
    @Override public void setServerApiBinaryExecutablePath(String value) { serverApiBinaryExecutablePath = value; }
    @Override public double getPusherVoteAdditionalRate() { return pusherVoteAdditionalRate; }
    @Override public void setPusherVoteAdditionalRate(double value) { pusherVoteAdditionalRate = clamp(value); }
    @Override public boolean getUseRandomCnIp() { return useRandomCnIp; }
    @Override public void setUseRandomCnIp(boolean value) { useRandomCnIp = value; }
    @Override public String getCorsAllowOrigin() { return corsAllowOrigin; }
    @Override public void setCorsAllowOrigin(String value) { corsAllowOrigin = value; }
    @Override public boolean getEnableProxy() { return enableProxy; }
    @Override public void setEnableProxy(boolean value) { enableProxy = value; }
    @Override public String getProxyUrl() { return proxyUrl; }
    @Override public void setProxyUrl(String value) { proxyUrl = value; }
    @Override public boolean getEnableGeneralUnblock() { return enableGeneralUnblock; }
    @Override public void setEnableGeneralUnblock(boolean value) { enableGeneralUnblock = value; }
    @Override public boolean getEnableFlac() { return enableFlac; }
    @Override public void setEnableFlac(boolean value) { enableFlac = value; }
    @Override public boolean getSelectMaxBr() { return selectMaxBr; }
    @Override public void setSelectMaxBr(boolean value) { selectMaxBr = value; }
    @Override public boolean getFollowSourceOrder() { return followSourceOrder; }
    @Override public void setFollowSourceOrder(boolean value) { followSourceOrder = value; }
    @Override public int getPort() { return port; }
    @Override public void setPort(int value) { if (value > 0 && value <= 65535) port = value; }

    @Override
    public String getServerApiBinaryExecutablePath() {
        Path configuredPath = Paths.get(serverApiBinaryExecutablePath);
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize().toString();
        }
        return Objects.requireNonNull(dataDirectory, "Velocity server config is not initialized")
                .resolve(configuredPath).normalize().toString();
    }

    @Override
    public synchronized void save() {
        properties.setProperty(KEY_API_BASE_URL, serverApiBaseUrl);
        properties.setProperty(KEY_STARTUP_BINARY_API_SERVER, Boolean.toString(startupBinaryApiServerWhenLaunch));
        properties.setProperty(KEY_API_BINARY_EXECUTABLE_PATH, serverApiBinaryExecutablePath);
        properties.setProperty(KEY_PUSHER_VOTE_ADDITIONAL_RATE, Double.toString(pusherVoteAdditionalRate));
        properties.setProperty(KEY_USE_RANDOM_CN_IP, Boolean.toString(useRandomCnIp));
        properties.setProperty(KEY_CORS_ALLOW_ORIGIN, corsAllowOrigin);
        properties.setProperty(KEY_ENABLE_PROXY, Boolean.toString(enableProxy));
        properties.setProperty(KEY_PROXY_URL, proxyUrl);
        properties.setProperty(KEY_ENABLE_GENERAL_UNBLOCK, Boolean.toString(enableGeneralUnblock));
        properties.setProperty(KEY_ENABLE_FLAC, Boolean.toString(enableFlac));
        properties.setProperty(KEY_SELECT_MAX_BR, Boolean.toString(selectMaxBr));
        properties.setProperty(KEY_FOLLOW_SOURCE_ORDER, Boolean.toString(followSourceOrder));
        properties.setProperty(KEY_PORT, Integer.toString(port));
        try {
            Files.createDirectories(Objects.requireNonNull(dataDirectory, "Velocity server config is not initialized"));
            try (OutputStream output = Files.newOutputStream(Objects.requireNonNull(configFile))) {
                properties.store(output, "MusicHud Velocity configuration");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save Velocity MusicHud configuration", exception);
        }
    }

    @Override public boolean isConfigured() { return configured; }
    @Override public void setConfigured(boolean value) { configured = value; }
}
