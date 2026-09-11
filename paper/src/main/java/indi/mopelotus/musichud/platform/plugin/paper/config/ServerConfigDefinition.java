package indi.mopelotus.musichud.platform.plugin.paper.config;

import indi.mopelotus.musichud.server.ClientResolvedServerConfig;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServerConfigDefinition extends ClientResolvedServerConfig {
    private static final ServerConfigDefinition INSTANCE = new ServerConfigDefinition();
    private JavaPlugin plugin;
    private ServerConfigDefinition() {}
    public static ServerConfigDefinition getInstance() { return INSTANCE; }
    public void initialize(JavaPlugin plugin) {
        this.plugin = plugin;
        setPusherVoteAdditionalRate(plugin.getConfig().getDouble("pusherVoteAdditionalRate", .5));
        setConfigured(true);
    }
    public void save() {
        if (plugin == null) throw new IllegalStateException("Paper config is not initialized");
        plugin.getConfig().set("pusherVoteAdditionalRate", getPusherVoteAdditionalRate());
        plugin.saveConfig();
    }
}