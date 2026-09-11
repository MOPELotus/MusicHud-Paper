package indi.mopelotus.musichud.client.network.vanilla;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import indi.mopelotus.musichud.MusicHud;
import indi.mopelotus.musichud.network.IPlayerClient;
import indi.mopelotus.musichud.platform.Environment;
import indi.mopelotus.musichud.utils.IClientDistUtil;
import lombok.Getter;
import lombok.SneakyThrows;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class VanillaPlayerProxy implements IPlayerClient {
    @Getter
    private final Player player;

    private static final Cache<Player, VanillaPlayerProxy> playerProxyCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build();
    private final ClientType clientType;

    private VanillaPlayerProxy(Player player) {
        this.player = player;
        Environment.Side currentSide = MusicHud.getCurrentEnvironment().getSide();
        clientType = currentSide == Environment.Side.CLIENT && IClientDistUtil.getInstance().isLocalPlayer(player) ? ClientType.LOCAL : ClientType.REMOTE;
    }

    @SneakyThrows
    public static VanillaPlayerProxy ofPlayer(Player player) {
        return playerProxyCache.get(player, () -> new VanillaPlayerProxy(player));
    }

    @Override
    public UUID getUUID() {
        return player.getUUID();
    }

    @Override public Object connectionIdentity() { return player; }
    @Override public boolean isConnected() {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) return serverPlayer.connection.isAcceptingMessages();
        return !player.isRemoved() && (clientType != ClientType.LOCAL || IClientDistUtil.getInstance().isLocalPlayer(player));
    }

    @Override
    public String getName() {
        return player.getName().getString();
    }

    @Override
    public ClientType getClientType() {
        return clientType;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof VanillaPlayerProxy vanillaPlayerProxy && player.getUUID().equals(vanillaPlayerProxy.getUUID());
    }

    @Override
    public int hashCode() {
        return getUUID().hashCode();
    }
}
