package indi.etern.musichud.paper;

import dev.architectury.networking.NetworkManager;
import indi.etern.musichud.MusicHud;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class PaperNetworkBridge implements AutoCloseable {
    private static final int MAX_PLUGIN_MESSAGE_RETRY_TICKS = 40;

    private final MusicHudPaper plugin;
    private final Logger logger = MusicHud.getLogger(PaperNetworkBridge.class);
    private final Map<String, RegisteredReceiver<?>> c2sReceivers = new ConcurrentHashMap<>();
    private final Map<CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ? extends CustomPacketPayload>> s2cCodecs =
            new ConcurrentHashMap<>();
    private final Set<String> incomingChannels = ConcurrentHashMap.newKeySet();
    private final Set<String> outgoingChannels = ConcurrentHashMap.newKeySet();

    PaperNetworkBridge(MusicHudPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void close() {
        Messenger messenger = plugin.getServer().getMessenger();
        for (String channel : incomingChannels) {
            messenger.unregisterIncomingPluginChannel(plugin, channel);
        }
        for (String channel : outgoingChannels) {
            messenger.unregisterOutgoingPluginChannel(plugin, channel);
        }
        incomingChannels.clear();
        outgoingChannels.clear();
        c2sReceivers.clear();
        s2cCodecs.clear();
    }

    <T extends CustomPacketPayload> void registerReceiver(
            NetworkManager.Side side,
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkManager.NetworkReceiver<T> receiver
    ) {
        if (side != NetworkManager.Side.C2S) {
            return;
        }
        String channel = type.id().toString();
        c2sReceivers.put(channel, new RegisteredReceiver<>(codec, receiver));
        ensureIncomingChannelRegistered(channel);
    }

    <T extends CustomPacketPayload> void registerS2CPayloadType(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec
    ) {
        s2cCodecs.put(type, codec);
        ensureOutgoingChannelRegistered(type.id().toString());
    }

    @SuppressWarnings("unchecked")
    void registerS2CPayloadTypeUnchecked(
            CustomPacketPayload.Type<? extends CustomPacketPayload> type,
            StreamCodec<? super RegistryFriendlyByteBuf, ? extends CustomPacketPayload> codec
    ) {
        registerS2CPayloadType(
                (CustomPacketPayload.Type<CustomPacketPayload>) type,
                (StreamCodec<? super RegistryFriendlyByteBuf, CustomPacketPayload>) codec
        );
    }

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        StreamCodec<? super RegistryFriendlyByteBuf, ? extends CustomPacketPayload> codec = s2cCodecs.get(payload.type());
        if (codec == null) {
            logger.warn("Skipping unregistered S2C payload {}", payload.type().id());
            return;
        }
        String channel = payload.type().id().toString();
        Player bukkitPlayer = Bukkit.getPlayer(player.getUUID());
        if (bukkitPlayer == null) {
            logger.warn("Skipping {} because player {} is no longer online", channel, player.getScoreboardName());
            return;
        }
        byte[] networkPayload = ArchitecturyPayloadCodec.encode(codec, payload, player);
        ensureOutgoingChannelRegistered(channel);
        runOnPrimaryThread(() -> sendPluginMessageWhenReady(bukkitPlayer, channel, networkPayload, 0));
    }

    void sendToPlayers(Collection<ServerPlayer> players, CustomPacketPayload payload) {
        for (ServerPlayer player : players) {
            sendToPlayer(player, payload);
        }
    }

    private void ensureIncomingChannelRegistered(String channel) {
        if (incomingChannels.add(channel)) {
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel, createListener(channel));
        }
    }

    private void ensureOutgoingChannelRegistered(String channel) {
        if (outgoingChannels.add(channel)) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
        }
    }

    private PluginMessageListener createListener(String expectedChannel) {
        return (channel, player, message) -> {
            if (!expectedChannel.equals(channel)) {
                return;
            }
            RegisteredReceiver<?> registered = c2sReceivers.get(channel);
            if (registered == null) {
                return;
            }
            try {
                ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
                logger.debug("Received {} ({} bytes)", channel, message.length);
                registered.receive(message, serverPlayer, new PaperPacketContext(serverPlayer));
            } catch (Exception e) {
                logger.error("Failed to process payload on channel {}", channel, e);
            }
        };
    }

    private void runOnPrimaryThread(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    private void sendPluginMessageWhenReady(Player player, String channel, byte[] payload, int attempt) {
        if (!player.isOnline()) {
            logger.warn("Skipping {} because player {} went offline before send", channel, player.getName());
            return;
        }
        if (player.getListeningPluginChannels().contains(channel)) {
            if (attempt > 0) {
                logger.info("Sending {} via plugin messaging after {} tick(s) ({} bytes)", channel, attempt, payload.length);
            } else {
                logger.debug("Sending {} via plugin messaging ({} bytes)", channel, payload.length);
            }
            player.sendPluginMessage(plugin, channel, payload);
            return;
        }
        if (attempt >= MAX_PLUGIN_MESSAGE_RETRY_TICKS) {
            logger.warn("Skipping {} because client {} never registered the channel after {} tick(s)", channel, player.getName(), attempt);
            return;
        }
        if (attempt == 0) {
            logger.debug("Waiting for client {} to register {} before sending", player.getName(), channel);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendPluginMessageWhenReady(player, channel, payload, attempt + 1), 1L);
    }

    private final class PaperPacketContext implements NetworkManager.PacketContext {
        private final ServerPlayer player;

        private PaperPacketContext(ServerPlayer player) {
            this.player = player;
        }

        @Override
        public ServerPlayer getPlayer() {
            return player;
        }

        @Override
        public void queue(Runnable runnable) {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    private record RegisteredReceiver<T extends CustomPacketPayload>(
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkManager.NetworkReceiver<T> receiver
    ) {
        private void receive(byte[] bytes, ServerPlayer player, NetworkManager.PacketContext context) {
            T payload = ArchitecturyPayloadCodec.decode(codec, bytes, player);
            receiver.receive(payload, context);
        }
    }
}
