package indi.etern.musichud.server;

import indi.etern.musichud.interfaces.ICommonEventService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.interfaces.ServerRegister;
import indi.etern.musichud.network.IPlayerClient;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/** Provider-neutral registry of clients connected to the MusicHud-TuneWeave protocol. */
public final class ServerPlayerRegistry {
    private static final ServerPlayerRegistry INSTANCE = new ServerPlayerRegistry();

    private final ConcurrentHashMap<UUID, IPlayerClient> players = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<Consumer<Collection<IPlayerClient>>> listeners =
            new CopyOnWriteArraySet<>();

    private ServerPlayerRegistry() {
    }

    public static ServerPlayerRegistry getInstance() {
        return INSTANCE;
    }

    public void join(IPlayerClient player) {
        players.put(player.getUUID(), player);
        notifyListeners();
    }

    public void leave(IPlayerClient player) {
        if (players.remove(player.getUUID()) != null) {
            notifyListeners();
        }
    }

    public boolean contains(UUID playerId) {
        return players.containsKey(playerId);
    }

    public List<IPlayerClient> players() {
        return List.copyOf(players.values());
    }

    public void addListener(Consumer<Collection<IPlayerClient>> listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        List<IPlayerClient> snapshot = players();
        listeners.forEach(listener -> listener.accept(snapshot));
    }

    @RegisterMark
    public static class Register implements ServerRegister {
        @Override
        public void register() {
            ICommonEventService.getInstance().registerCommonPlayerQuit(
                    ServerPlayerRegistry.getInstance()::leave);
        }
    }
}
