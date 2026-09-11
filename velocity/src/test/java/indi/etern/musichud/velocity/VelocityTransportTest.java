package indi.mopelotus.musichud.velocity;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.messages.*;
import indi.mopelotus.musichud.network.*;
import indi.mopelotus.musichud.network.payloads.S2CPayload;
import indi.mopelotus.musichud.network.payloads.pushMessages.s2c.ServerPayloadFragment;
import indi.mopelotus.musichud.platform.plugin.velocity.network.*;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class VelocityTransportTest {
    @TempDir Path directory;

    @Test void actualAdapterFragmentsLargePayloadAndRejectsReplacedConnection() {
        var manager = VelocityNetworkManager.getInstance();
        UUID id = UUID.randomUUID(); List<byte[]> sent = new ArrayList<>();
        Player first = player(id, sent), replacement = player(id, sent);
        var active = new AtomicReference<>(first);
        manager.initialize(proxy(active));
        try {
            manager.registerS2CPayload(LargePayload.class, ByteBufCodec.composite(Codecs.STRING_UTF8, LargePayload::value, LargePayload::new), NetworkReceiver.noop());
            manager.registerS2CPayload(ServerPayloadFragment.class, ServerPayloadFragment.CODEC, NetworkReceiver.noop());
            var old = VelocityPlayerProxy.of(first);
            manager.sendToPlayer(old, new LargePayload("x".repeat(30_000)));
            assertEquals(2, sent.size());
            for (byte[] bytes : sent) {
                assertTrue(bytes.length < 32766);
                var buffer = Unpooled.wrappedBuffer(bytes);
                try { assertNotNull(ServerPayloadFragment.CODEC.decode(buffer)); assertFalse(buffer.isReadable()); }
                finally { buffer.release(); }
            }
            active.set(replacement);
            manager.sendToPlayer(old, new LargePayload("old"));
            assertEquals(2, sent.size());
            VelocityPlayerProxy.disconnect(first);
            assertFalse(old.isConnected());
            assertTrue(VelocityPlayerProxy.of(replacement).isConnected());
        } finally { manager.close(); }
    }

    @Test void actualPluginEventDropsBackendAuthorityButLeavesForeignChannelsAlone() throws Exception {
        Player player = player(UUID.randomUUID(), new ArrayList<>());
        ProxyServer proxy = proxy(new AtomicReference<>(player));
        var manager = VelocityNetworkManager.getInstance(); manager.initialize(proxy);
        var initializer = new VelocityInitializer(proxy, org.slf4j.LoggerFactory.getLogger("test"), directory);
        var field = VelocityInitializer.class.getDeclaredField("networkManager"); field.setAccessible(true); field.set(initializer, manager);
        ServerConnection backend = (ServerConnection) Proxy.newProxyInstance(ServerConnection.class.getClassLoader(),
                new Class<?>[]{ServerConnection.class}, (p, m, a) -> { throw new AssertionError(m.getName()); });
        try {
            var owned = new PluginMessageEvent(backend, player,
                    MinecraftChannelIdentifier.from("musichud_tuneweave:switch_music_message"), new byte[0]);
            initializer.onPluginMessage(owned);
            assertEquals(PluginMessageEvent.ForwardResult.handled(), owned.getResult());
            var foreign = new PluginMessageEvent(backend, player, MinecraftChannelIdentifier.from("other:payload"), new byte[0]);
            initializer.onPluginMessage(foreign);
            assertEquals(PluginMessageEvent.ForwardResult.forward(), foreign.getResult());
        } finally { manager.close(); }
    }

    private static Player player(UUID id, List<byte[]> sent) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getUsername" -> "Player";
            case "isActive" -> true;
            case "sendPluginMessage" -> { sent.add(((byte[]) args[1]).clone()); yield true; }
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "Player";
            default -> throw new AssertionError(method.getName());
        });
    }

    private static ProxyServer proxy(AtomicReference<Player> active) {
        ChannelRegistrar registrar = (ChannelRegistrar) Proxy.newProxyInstance(ChannelRegistrar.class.getClassLoader(),
                new Class<?>[]{ChannelRegistrar.class}, (proxy, method, args) -> null);
        return (ProxyServer) Proxy.newProxyInstance(ProxyServer.class.getClassLoader(), new Class<?>[]{ProxyServer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPlayer" -> Optional.ofNullable(active.get());
                    case "getChannelRegistrar" -> registrar;
                    default -> throw new AssertionError(method.getName());
                });
    }

    private record LargePayload(String value) implements S2CPayload {}
}
