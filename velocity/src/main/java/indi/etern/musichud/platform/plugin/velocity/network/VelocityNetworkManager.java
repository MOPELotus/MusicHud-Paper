package indi.etern.musichud.platform.plugin.velocity.network;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IPlayerClient;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.payloads.IPayload;
import indi.etern.musichud.network.payloads.S2CPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VelocityNetworkManager implements INetworkRegister, IServerNetworkService, AutoCloseable {
    private static final VelocityNetworkManager INSTANCE = new VelocityNetworkManager();

    private final Logger logger = MusicHud.getLogger(VelocityNetworkManager.class);
    private final Map<String, RegisteredReceiver<?>> c2sReceivers = new ConcurrentHashMap<>();
    private final Map<String, ByteBufCodec<?>> s2cCodecs = new ConcurrentHashMap<>();
    private final Map<String, ChannelIdentifier> channels = new ConcurrentHashMap<>();
    private volatile ProxyServer proxy;

    private VelocityNetworkManager() {
    }

    public static VelocityNetworkManager getInstance() {
        return INSTANCE;
    }

    public void initialize(ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Override
    public <T extends IPayload> void registerC2SPayload(
            Class<T> clazz,
            ByteBufCodec<T> codec,
            NetworkReceiver<T> serverReceiver
    ) {
        String channel = channelId(clazz);
        c2sReceivers.put(channel, new RegisteredReceiver<>(codec, serverReceiver));
        registerChannel(channel);
    }

    @Override
    public <T extends IPayload> void registerS2CPayload(
            Class<T> clazz,
            ByteBufCodec<T> codec,
            NetworkReceiver<T> clientReceiver
    ) {
        String channel = channelId(clazz);
        s2cCodecs.put(channel, codec);
        registerChannel(channel);
    }

    @Override
    public <T extends IPayload> void autoRegisterPayload(
            Class<T> clazz,
            ByteBufCodec<T> codec,
            NetworkReceiver<T> receiver
    ) {
        if (S2CPayload.class.isAssignableFrom(clazz)) {
            registerS2CPayload(clazz, codec, receiver);
            return;
        }
        if (C2SPayload.class.isAssignableFrom(clazz)) {
            registerC2SPayload(clazz, codec, receiver);
            return;
        }
        throw new IllegalArgumentException("Payload class must implement S2CPayload or C2SPayload");
    }

    @Override
    public void sendToPlayer(IPlayerClient playerClient, S2CPayload payload) {
        ProxyServer activeProxy = requireProxy();
        Player player = activeProxy.getPlayer(playerClient.getUUID()).orElse(null);
        if (player == null) {
            logger.warn("Skipping payload for offline player {}", playerClient.getName());
            return;
        }

        String channel = channelId(payload.getClass());
        @SuppressWarnings("unchecked")
        ByteBufCodec<S2CPayload> codec = (ByteBufCodec<S2CPayload>) s2cCodecs.get(channel);
        if (codec == null) {
            logger.warn("Skipping unregistered S2C payload {}", channel);
            return;
        }

        ByteBuf buffer = Unpooled.buffer();
        byte[] bytes;
        try {
            codec.encode(buffer, payload);
            bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
        } finally {
            buffer.release();
        }

        ChannelIdentifier identifier = registerChannel(channel);
        if (!player.sendPluginMessage(identifier, bytes)) {
            logger.debug("Velocity did not accept plugin message {} for {}", channel, player.getUsername());
        }
    }

    public boolean handles(String channel) {
        return c2sReceivers.containsKey(channel);
    }

    public void handle(Player player, String channel, byte[] bytes) {
        RegisteredReceiver<?> receiver = c2sReceivers.get(channel);
        if (receiver == null) {
            return;
        }
        try {
            receiver.receive(bytes, VelocityPlayerProxy.of(player));
        } catch (Exception exception) {
            logger.error("Failed to process Velocity payload on channel {}", channel, exception);
        }
    }

    @Override
    public void close() {
        ProxyServer activeProxy = proxy;
        if (activeProxy != null && !channels.isEmpty()) {
            activeProxy.getChannelRegistrar().unregister(channels.values().toArray(ChannelIdentifier[]::new));
        }
        c2sReceivers.clear();
        s2cCodecs.clear();
        channels.clear();
        proxy = null;
    }

    private ChannelIdentifier registerChannel(String channel) {
        return channels.computeIfAbsent(channel, key -> {
            ChannelIdentifier identifier = MinecraftChannelIdentifier.from(key);
            requireProxy().getChannelRegistrar().register(identifier);
            return identifier;
        });
    }

    private ProxyServer requireProxy() {
        ProxyServer activeProxy = proxy;
        if (activeProxy == null) {
            throw new IllegalStateException("Velocity network manager is not initialized");
        }
        return activeProxy;
    }

    private static String channelId(Class<?> clazz) {
        String[] words = clazz.getSimpleName().split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
        return "musichud:" + String.join("_", words).toLowerCase();
    }

    private record RegisteredReceiver<T extends IPayload>(ByteBufCodec<T> codec, NetworkReceiver<T> receiver) {
        private void receive(byte[] bytes, IPlayerClient player) {
            ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
            try {
                receiver.receive(codec.decode(buffer), player);
            } finally {
                buffer.release();
            }
        }
    }
}
