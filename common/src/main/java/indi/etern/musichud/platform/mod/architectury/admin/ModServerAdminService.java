package indi.etern.musichud.platform.mod.architectury.admin;

import indi.etern.musichud.interfaces.IServerAdminService;
import net.minecraft.server.level.ServerPlayer;

public final class ModServerAdminService implements IServerAdminService {
    private static volatile ModServerAdminService instance;

    private ModServerAdminService() {
    }

    public static ModServerAdminService getInstance() {
        if (instance == null) {
            synchronized (ModServerAdminService.class) {
                if (instance == null) {
                    instance = new ModServerAdminService();
                }
            }
        }
        return instance;
    }

    @Override
    public boolean canReloadConfig(ServerPlayer player) {
        return false;
    }

    @Override
    public boolean canManageServerConfig(ServerPlayer player) {
        return false;
    }

    @Override
    public void reloadConfig() {
        // Mod-side server config reload remains managed by the underlying platform.
    }
}
