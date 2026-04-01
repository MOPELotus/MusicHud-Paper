package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public interface IServerAdminService {
    String ADMIN_PERMISSION = "musichud.admin";
    String RELOAD_PERMISSION = "musichud.reload";

    static IServerAdminService getInstance() {
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        Supplier<IServerAdminService> supplier = platform.getServerAdminServiceSupplier();
        if (supplier != null) {
            IServerAdminService serverAdminService = supplier.get();
            if (serverAdminService != null) {
                return serverAdminService;
            }
        }
        throw new UnsupportedOperationException();
    }

    boolean canReloadConfig(ServerPlayer player);

    boolean canManageServerConfig(ServerPlayer player);

    default boolean canManagePlayback(ServerPlayer player) {
        return canManageServerConfig(player);
    }

    default boolean canQueryPlayerInfo(ServerPlayer player) {
        return canManageServerConfig(player);
    }

    void reloadConfig();
}
