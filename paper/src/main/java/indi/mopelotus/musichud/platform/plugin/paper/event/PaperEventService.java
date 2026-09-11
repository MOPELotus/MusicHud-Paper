package indi.mopelotus.musichud.platform.plugin.paper.event;

import indi.mopelotus.musichud.interfaces.ICommonEventService;
import indi.mopelotus.musichud.interfaces.Unregister;
import indi.mopelotus.musichud.network.IPlayerClient;
import indi.mopelotus.musichud.platform.plugin.paper.network.PaperPlayerProxy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public final class PaperEventService implements ICommonEventService, Listener {
    private static volatile PaperEventService instance;
    private final Set<Consumer<IPlayerClient>> disconnectListeners = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<Runnable> stoppingListeners = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private JavaPlugin plugin;

    private PaperEventService() {
    }

    public static PaperEventService getInstance() {
        if (instance == null) {
            synchronized (PaperEventService.class) {
                if (instance == null) {
                    instance = new PaperEventService();
                }
            }
        }
        return instance;
    }

    public void initialize(JavaPlugin plugin) {
        if (this.plugin == plugin) {
            return;
        }
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public Unregister registerCommonPlayerQuit(Consumer<IPlayerClient> listener) {
        disconnectListeners.add(listener);
        return () -> {
            disconnectListeners.remove(listener);
        };
    }

    @Override
    public Unregister registerCommonLifecycleStopping(Runnable listener) {
        stoppingListeners.add(listener);
        return () -> {
            stoppingListeners.remove(listener);
        };
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = PaperPlayerProxy.ofPlayer(event.getPlayer());
        player.disconnect();
        disconnectListeners.forEach(d -> d.accept(player));
    }

    public void fireServerStopping() {
        stoppingListeners.forEach(Runnable::run);
    }
}
