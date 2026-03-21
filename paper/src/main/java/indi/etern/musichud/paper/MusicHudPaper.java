package indi.etern.musichud.paper;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.server.api.ServerApiMeta;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.Logger;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class MusicHudPaper extends JavaPlugin implements Listener {
    private static MusicHudPaper instance;
    private PaperNetworkBridge networkBridge;

    private final Logger logger = MusicHud.getLogger(MusicHudPaper.class);

    public static MusicHudPaper getInstance() {
        return Objects.requireNonNull(instance, "MusicHudPaper is not enabled");
    }

    @Override
    public void onEnable() {
        instance = this;
        networkBridge = new PaperNetworkBridge(this);
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        if (PaperServerConfigBridge.apply(getConfig())) {
            saveConfig();
        }
        ServerApiMeta.reload();
        getServer().getPluginManager().registerEvents(this, this);
        MusicHud.init();
        logger.info("MusicHud Paper bridge enabled");
    }

    @Override
    public void onDisable() {
        if (networkBridge != null) {
            networkBridge.close();
            networkBridge = null;
        }
        instance = null;
        logger.info("MusicHud Paper bridge disabled");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PlayerEvent.PLAYER_QUIT.invoker(toServerPlayer(event.getPlayer()));
    }

    public <T extends CustomPacketPayload> void registerReceiver(
            NetworkManager.Side side,
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkManager.NetworkReceiver<T> receiver
    ) {
        getNetworkBridge().registerReceiver(side, type, codec, receiver);
    }

    public <T extends CustomPacketPayload> void registerS2CPayloadType(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec
    ) {
        getNetworkBridge().registerS2CPayloadType(type, codec);
    }

    @SuppressWarnings("unchecked")
    public void registerS2CPayloadTypeUnchecked(
            CustomPacketPayload.Type<? extends CustomPacketPayload> type,
            StreamCodec<? super RegistryFriendlyByteBuf, ? extends CustomPacketPayload> codec
    ) {
        getNetworkBridge().registerS2CPayloadTypeUnchecked(type, codec);
    }

    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        getNetworkBridge().sendToPlayer(player, payload);
    }

    public void sendToPlayers(java.util.Collection<ServerPlayer> players, CustomPacketPayload payload) {
        getNetworkBridge().sendToPlayers(players, payload);
    }

    private PaperNetworkBridge getNetworkBridge() {
        return Objects.requireNonNull(networkBridge, "Paper network bridge is not initialized");
    }

    private ServerPlayer toServerPlayer(org.bukkit.entity.Player player) {
        return ((CraftPlayer) player).getHandle();
    }
}
