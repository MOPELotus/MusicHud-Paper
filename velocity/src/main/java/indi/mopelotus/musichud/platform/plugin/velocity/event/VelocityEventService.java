package indi.mopelotus.musichud.platform.plugin.velocity.event;

import com.velocitypowered.api.proxy.Player;
import indi.mopelotus.musichud.interfaces.ICommonEventService;
import indi.mopelotus.musichud.interfaces.Unregister;
import indi.mopelotus.musichud.network.IPlayerClient;
import indi.mopelotus.musichud.platform.plugin.velocity.network.VelocityPlayerProxy;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class VelocityEventService implements ICommonEventService {
    private static final VelocityEventService INSTANCE = new VelocityEventService();

    private final Set<Consumer<IPlayerClient>> disconnectListeners = ConcurrentHashMap.newKeySet();
    private final Set<Runnable> stoppingListeners = ConcurrentHashMap.newKeySet();

    private VelocityEventService() {
    }

    public static VelocityEventService getInstance() {
        return INSTANCE;
    }

    @Override
    public Unregister registerCommonPlayerQuit(Consumer<IPlayerClient> listener) {
        disconnectListeners.add(listener);
        return () -> disconnectListeners.remove(listener);
    }

    @Override
    public Unregister registerCommonLifecycleStopping(Runnable listener) {
        stoppingListeners.add(listener);
        return () -> stoppingListeners.remove(listener);
    }

    public void firePlayerQuit(Player player) {
        VelocityPlayerProxy.disconnect(player);
        IPlayerClient client = VelocityPlayerProxy.of(player);
        disconnectListeners.forEach(listener -> listener.accept(client));
    }

    public void fireProxyStopping() {
        stoppingListeners.forEach(Runnable::run);
    }
}
