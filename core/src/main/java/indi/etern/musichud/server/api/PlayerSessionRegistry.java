package indi.etern.musichud.server.api;

import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.interfaces.ICommonEventService;
import indi.etern.musichud.interfaces.ServerRegister;
import indi.etern.musichud.network.IPlayerClient;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks Minecraft clients connected to Music HUD only.  This deliberately
 * contains no music-platform identity, cookie, profile, or credential state.
 */
public final class PlayerSessionRegistry implements ServerRegister {
    private static final PlayerSessionRegistry INSTANCE = new PlayerSessionRegistry();
    private final Map<UUID, IPlayerClient> players = new ConcurrentHashMap<>();

    private PlayerSessionRegistry() {
    }

    public static PlayerSessionRegistry getInstance() {
        return INSTANCE;
    }

    public void join(IPlayerClient player) {
        players.put(player.getUUID(), player);
    }

    public void leave(IPlayerClient player) {
        players.remove(player.getUUID());
        MusicPlayerServerService.getInstance().removeAllIdlePlaySource(PusherInfo.ofPlayer(player));
    }

    public boolean contains(UUID playerId) {
        return players.containsKey(playerId);
    }

    public Collection<IPlayerClient> players() {
        return players.values();
    }

    public int size() {
        return players.size();
    }

    @Override
    public void register() {
        ICommonEventService.getInstance().registerCommonPlayerQuit(this::leave);
    }
}
