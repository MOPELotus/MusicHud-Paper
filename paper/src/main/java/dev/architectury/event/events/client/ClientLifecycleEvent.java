package dev.architectury.event.events.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ClientLifecycleEvent {
    public static final Event<Object> CLIENT_STOPPING = new Event<>();

    private ClientLifecycleEvent() {
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
