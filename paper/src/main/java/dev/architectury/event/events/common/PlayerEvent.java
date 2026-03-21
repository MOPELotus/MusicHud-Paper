package dev.architectury.event.events.common;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class PlayerEvent {
    public static final Event<net.minecraft.server.level.ServerPlayer> PLAYER_QUIT = new Event<>();

    private PlayerEvent() {
    }

    public static final class Event<T> {
        private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();

        public void register(Consumer<T> consumer) {
            listeners.add(consumer);
        }

        public void invoker(T value) {
            for (Consumer<T> listener : listeners) {
                listener.accept(value);
            }
        }
    }
}
