package indi.etern.musichud.platform.plugin.paper.admin;

import indi.etern.musichud.interfaces.IServerAdminService;
import indi.etern.musichud.platform.plugin.paper.config.ServerConfigDefinition;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperServerAdminService implements IServerAdminService {
    private static volatile PaperServerAdminService instance;
    private JavaPlugin plugin;

    private PaperServerAdminService() {
    }

    public static PaperServerAdminService getInstance() {
        if (instance == null) {
            synchronized (PaperServerAdminService.class) {
                if (instance == null) {
                    instance = new PaperServerAdminService();
                }
            }
        }
        return instance;
    }

    public void initialize(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean canReloadConfig(ServerPlayer player) {
        return getBukkitPlayer(player).hasPermission(RELOAD_PERMISSION)
                || canManageServerConfig(player);
    }

    @Override
    public boolean canManageServerConfig(ServerPlayer player) {
        return getBukkitPlayer(player).hasPermission(ADMIN_PERMISSION);
    }

    @Override
    public void reloadConfig() {
        if (plugin == null) {
            throw new IllegalStateException("Paper admin service is not initialized");
        }
        plugin.reloadConfig();
        ServerConfigDefinition.getInstance().reloadFromPluginConfig(plugin);
    }

    private CraftPlayer getBukkitPlayer(ServerPlayer player) {
        return player.getBukkitEntity();
    }
}
