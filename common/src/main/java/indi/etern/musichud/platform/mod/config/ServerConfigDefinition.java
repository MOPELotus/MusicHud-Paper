package indi.etern.musichud.platform.mod.config;

import indi.etern.musichud.interfaces.ServerConfig;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ServerConfigDefinition implements ServerConfig {
    private static final String FILE_NAME = "music_hud-server.toml";
    @Getter
    private static final ServerConfigDefinition instance = new ServerConfigDefinition();

    private String serverApiBaseUrl = "http://localhost:3000";
    private boolean startupBinaryApiServerWhenLaunch = true;
    private String serverApiBinaryExecutablePath = "MusicHud/api";
    private double pusherVoteAdditionalRate = 0.5;
    private boolean useRandomCnIp = true;
    @Setter
    @Getter
    private boolean configured;

    private ServerConfigDefinition() {
    }

    public synchronized void load() {
        Path path = SimpleTomlConfig.path(FILE_NAME);
        Map<String, String> values = SimpleTomlConfig.read(path);
        serverApiBaseUrl = SimpleTomlConfig.getString(values, "serverApiBaseUrl", serverApiBaseUrl);
        startupBinaryApiServerWhenLaunch = SimpleTomlConfig.getBoolean(
                values,
                "startupBinaryApiServerWhenLaunch",
                startupBinaryApiServerWhenLaunch
        );
        serverApiBinaryExecutablePath = SimpleTomlConfig.getString(
                values,
                "serverApiBinaryExecutablePath",
                serverApiBinaryExecutablePath
        );
        pusherVoteAdditionalRate = clamp(SimpleTomlConfig.getDouble(
                values,
                "pusherVoteAdditionalRate",
                pusherVoteAdditionalRate
        ));
        useRandomCnIp = SimpleTomlConfig.getBoolean(values, "useRandomCnIp", useRandomCnIp);
        configured = true;
        save();
    }

    @Override
    public void setServerApiBaseUrl(String serverApiBaseUrl) {
        this.serverApiBaseUrl = serverApiBaseUrl;
    }

    @Override
    public void setStartupBinaryApiServerWhenLaunch(boolean startupBinaryApiServerWhenLaunch) {
        this.startupBinaryApiServerWhenLaunch = startupBinaryApiServerWhenLaunch;
    }

    @Override
    public void setServerApiBinaryExecutablePath(String serverApiBinaryExecutablePath) {
        this.serverApiBinaryExecutablePath = serverApiBinaryExecutablePath;
    }

    @Override
    public void setPusherVoteAdditionalRate(double pusherVoteAdditionalRate) {
        this.pusherVoteAdditionalRate = clamp(pusherVoteAdditionalRate);
    }

    @Override
    public void setUseRandomCnIp(boolean useRandomCnIp) {
        this.useRandomCnIp = useRandomCnIp;
    }

    @Override
    public String getServerApiBaseUrl() {
        return serverApiBaseUrl;
    }

    @Override
    public boolean getStartupBinaryApiServerWhenLaunch() {
        return startupBinaryApiServerWhenLaunch;
    }

    @Override
    public String getServerApiBinaryExecutablePath() {
        return serverApiBinaryExecutablePath;
    }

    @Override
    public double getPusherVoteAdditionalRate() {
        return pusherVoteAdditionalRate;
    }

    @Override
    public boolean getUseRandomCnIp() {
        return useRandomCnIp;
    }

    @Override
    public synchronized void save() {
        SimpleTomlConfig.write(SimpleTomlConfig.path(FILE_NAME), List.of(
                new SimpleTomlConfig.Entry("serverApiBaseUrl", "Server API base URL configuration", serverApiBaseUrl),
                new SimpleTomlConfig.Entry("startupBinaryApiServerWhenLaunch", "Start binary API server when game launches", startupBinaryApiServerWhenLaunch),
                new SimpleTomlConfig.Entry("serverApiBinaryExecutablePath", "Server API binary executable path", serverApiBinaryExecutablePath),
                new SimpleTomlConfig.Entry("pusherVoteAdditionalRate", "Skip vote threshold added by the current pusher (0.0 - 1.0)", pusherVoteAdditionalRate),
                new SimpleTomlConfig.Entry("useRandomCnIp", "Use random Chinese IP provided by API server", useRandomCnIp)
        ));
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
