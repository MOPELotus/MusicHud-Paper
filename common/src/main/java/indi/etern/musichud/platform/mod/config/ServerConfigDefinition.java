package indi.etern.musichud.platform.mod.config;

import indi.etern.musichud.interfaces.ServerConfig;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Native TOML configuration shared by Fabric and NeoForge. */
public class ServerConfigDefinition implements ServerConfig {
    private static final String FILE_NAME = "music_hud-server.toml";
    @Getter
    private static final ServerConfigDefinition instance = new ServerConfigDefinition();

    private String serverApiBaseUrl = "http://127.0.0.1:7832";
    private boolean manageTuneWeaveInternally;
    private double pusherVoteAdditionalRate = 0.5;
    @Setter
    @Getter
    private boolean configured;

    private ServerConfigDefinition() {
    }

    public synchronized void load() {
        Path path = SimpleTomlConfig.path(FILE_NAME);
        Map<String, String> values = SimpleTomlConfig.read(path);
        serverApiBaseUrl = SimpleTomlConfig.getString(values, "serverApiBaseUrl", serverApiBaseUrl);
        manageTuneWeaveInternally = SimpleTomlConfig.getBoolean(values, "manageTuneWeaveInternally", manageTuneWeaveInternally);
        pusherVoteAdditionalRate = clamp(SimpleTomlConfig.getDouble(
                values, "pusherVoteAdditionalRate", pusherVoteAdditionalRate));
        configured = true;
        save();
    }

    @Override
    public String getServerApiBaseUrl() {
        return serverApiBaseUrl;
    }

    @Override
    public void setServerApiBaseUrl(String serverApiBaseUrl) {
        this.serverApiBaseUrl = serverApiBaseUrl == null || serverApiBaseUrl.isBlank()
                ? "http://127.0.0.1:7832" : serverApiBaseUrl.trim();
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
    public void setPusherVoteAdditionalRate(double pusherVoteAdditionalRate) {
        this.pusherVoteAdditionalRate = clamp(pusherVoteAdditionalRate);
    }

    @Override
    public synchronized void save() {
        SimpleTomlConfig.write(SimpleTomlConfig.path(FILE_NAME), List.of(
                new SimpleTomlConfig.Entry("serverApiBaseUrl", "TuneWeave service base URL", serverApiBaseUrl),
                new SimpleTomlConfig.Entry("manageTuneWeaveInternally", "Automatically download, verify, launch and update the local TuneWeave service", manageTuneWeaveInternally),
                new SimpleTomlConfig.Entry("pusherVoteAdditionalRate", "Skip vote threshold added by the current pusher (0.0 - 1.0)", pusherVoteAdditionalRate)
        ));
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
