package indi.mopelotus.musichud.platform.plugin.paper.network;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import indi.mopelotus.musichud.network.IPlayerClient;
import lombok.SneakyThrows;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PaperPlayerProxy implements IPlayerClient {
    private static final Cache<Player, PaperPlayerProxy> playerProxyCache = CacheBuilder.newBuilder()
            .weakKeys()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build();

    private final Player player;
    private volatile boolean disconnected;

    private PaperPlayerProxy(Player player) {
        this.player = player;
    }

    @SneakyThrows
    public static PaperPlayerProxy ofPlayer(Player player) {
        return playerProxyCache.get(player, () -> new PaperPlayerProxy(player));
    }

    @Override
    public UUID getUUID() {
        return player.getUniqueId();
    }

    @Override
    public String getName() {
        return player.getName();
    }

    @Override
    public ClientType getClientType() {
        return ClientType.REMOTE;
    }

    public void disconnect() { disconnected = true; }
    @Override public boolean isConnected() { return !disconnected && player.isOnline() && PaperNetworkManager.getInstance().isOpen(); }
    @Override public Object connectionIdentity() { return player; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PaperPlayerProxy playerProxy && getUUID().equals(playerProxy.getUUID());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getUUID());
    }
}
