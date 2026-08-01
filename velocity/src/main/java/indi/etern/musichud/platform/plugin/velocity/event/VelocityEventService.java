package indi.etern.musichud.platform.plugin.velocity.event;

import com.velocitypowered.api.proxy.Player;
import indi.etern.musichud.interfaces.ICommonEventService;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.network.IPlayerClient;
import indi.etern.musichud.platform.plugin.velocity.network.VelocityPlayerProxy;

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
        IPlayerClient client = VelocityPlayerProxy.of(player);
        disconnectListeners.forEach(listener -> listener.accept(client));
    }

    public void fireProxyStopping() {
        stoppingListeners.forEach(Runnable::run);
    }
}
