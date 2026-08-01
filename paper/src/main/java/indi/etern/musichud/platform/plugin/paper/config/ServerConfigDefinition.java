package indi.etern.musichud.platform.plugin.paper.config;

import indi.etern.musichud.interfaces.ServerConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/** Paper's native configuration stays in plugins/MusicHud/config.yml. */
public final class ServerConfigDefinition implements ServerConfig {
    private static final String KEY_SERVER_API_BASE_URL = "serverApiBaseUrl";
    private static final String KEY_MANAGE_TUNEWEAVE_INTERNALLY = "manageTuneWeaveInternally";
    private static final String KEY_PUSHER_VOTE_ADDITIONAL_RATE = "pusherVoteAdditionalRate";
    private static final String DEFAULT_SERVER_API_BASE_URL = "http://127.0.0.1:7832";
    private static final double DEFAULT_PUSHER_VOTE_ADDITIONAL_RATE = 0.5D;
    private static final ServerConfigDefinition INSTANCE = new ServerConfigDefinition();

    private JavaPlugin plugin;
    private String serverApiBaseUrl = DEFAULT_SERVER_API_BASE_URL;
    private boolean manageTuneWeaveInternally;
    private double pusherVoteAdditionalRate = DEFAULT_PUSHER_VOTE_ADDITIONAL_RATE;
    private boolean configured;

    private ServerConfigDefinition() {
    }

    public static ServerConfigDefinition getInstance() {
        return INSTANCE;
    }

    public void initialize(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        boolean changed = false;
        if (!config.contains(KEY_SERVER_API_BASE_URL)) {
            config.set(KEY_SERVER_API_BASE_URL, DEFAULT_SERVER_API_BASE_URL);
            changed = true;
        }
        if (!config.contains(KEY_PUSHER_VOTE_ADDITIONAL_RATE)) {
            config.set(KEY_PUSHER_VOTE_ADDITIONAL_RATE, DEFAULT_PUSHER_VOTE_ADDITIONAL_RATE);
            changed = true;
        }
        if (!config.contains(KEY_MANAGE_TUNEWEAVE_INTERNALLY)) {
            config.set(KEY_MANAGE_TUNEWEAVE_INTERNALLY, false);
            changed = true;
        }
        serverApiBaseUrl = config.getString(KEY_SERVER_API_BASE_URL, DEFAULT_SERVER_API_BASE_URL);
        manageTuneWeaveInternally = config.getBoolean(KEY_MANAGE_TUNEWEAVE_INTERNALLY, false);
        pusherVoteAdditionalRate = clamp(config.getDouble(KEY_PUSHER_VOTE_ADDITIONAL_RATE, DEFAULT_PUSHER_VOTE_ADDITIONAL_RATE));
        configured = true;
        if (changed) {
            plugin.saveConfig();
        }
    }

    @Override
    public String getServerApiBaseUrl() {
        return serverApiBaseUrl;
    }

    @Override
    public void setServerApiBaseUrl(String serverApiBaseUrl) {
        this.serverApiBaseUrl = serverApiBaseUrl == null || serverApiBaseUrl.isBlank()
                ? DEFAULT_SERVER_API_BASE_URL : serverApiBaseUrl.trim();
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
    public void save() {
        FileConfiguration config = Objects.requireNonNull(plugin, "Paper server config is not initialized").getConfig();
        config.set(KEY_SERVER_API_BASE_URL, serverApiBaseUrl);
        config.set(KEY_MANAGE_TUNEWEAVE_INTERNALLY, manageTuneWeaveInternally);
        config.set(KEY_PUSHER_VOTE_ADDITIONAL_RATE, pusherVoteAdditionalRate);
        plugin.saveConfig();
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
