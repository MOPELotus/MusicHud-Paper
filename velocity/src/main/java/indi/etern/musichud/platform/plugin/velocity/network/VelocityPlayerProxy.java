package indi.etern.musichud.platform.plugin.velocity.network;

import com.velocitypowered.api.proxy.Player;
import indi.etern.musichud.network.IPlayerClient;

import java.util.Objects;
import java.util.UUID;

public final class VelocityPlayerProxy implements IPlayerClient {
    private final Player player;

    private VelocityPlayerProxy(Player player) {
        this.player = player;
    }

    public static VelocityPlayerProxy of(Player player) {
        return new VelocityPlayerProxy(player);
    }

    @Override
    public UUID getUUID() {
        return player.getUniqueId();
    }

    @Override
    public String getName() {
        return player.getUsername();
    }

    @Override
    public ClientType getClientType() {
        return ClientType.REMOTE;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof VelocityPlayerProxy other && getUUID().equals(other.getUUID());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUUID());
    }
}
