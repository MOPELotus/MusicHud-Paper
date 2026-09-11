package indi.mopelotus.musichud.client.audio;

import indi.mopelotus.musichud.MusicHud;
import indi.mopelotus.musichud.beans.music.FormatType;
import indi.mopelotus.musichud.beans.music.PlaybackSession;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Public player facade: prepare a muted lane, then atomically hand over and fade for 150 ms. */
public final class StreamAudioPlayer {
    public enum Status { IDLE, BUFFERING, PLAYING, RETRYING, ERROR }
    private static final StreamAudioPlayer INSTANCE = new StreamAudioPlayer();
    private final Set<Consumer<Status>> listeners = ConcurrentHashMap.newKeySet();
    private final AtomicReference<Status> status = new AtomicReference<>(Status.IDLE);
    private final AtomicLong requests = new AtomicLong();
    private final PlaybackHandoff<PlaybackEngine> handoff = new PlaybackHandoff<>(MusicHud.EXECUTOR,
            task -> CompletableFuture.delayedExecutor(20, TimeUnit.MILLISECONDS, MusicHud.EXECUTOR).execute(task), System::nanoTime);

    private StreamAudioPlayer() {}
    public static StreamAudioPlayer getInstance() { return INSTANCE; }
    public Status getStatus() { return status.get(); }
    public Set<Consumer<Status>> getStatusChangeListener() { return listeners; }

    private void publish(Status next) {
        if (status.getAndSet(next) != next) listeners.forEach(listener -> listener.accept(next));
    }

    private PlaybackEngine createEngine() {
        var engine = new PlaybackEngine();
        engine.getStatusChangeListener().add(next -> MusicHud.EXECUTOR.execute(() -> {
            synchronized (this) {
                if (handoff.current() == engine && engine.getStatus() == next) publish(handoff.preparing() ? Status.BUFFERING : next);
            }
        }));
        return engine;
    }

    public synchronized CompletableFuture<ZonedDateTime> playSessionAsync(PlaybackSession session) {
        if (session == null || !session.isActive()) return CompletableFuture.failedFuture(new IllegalArgumentException("Inactive playback session"));
        long request = requests.incrementAndGet();
        PlaybackEngine current = handoff.current();
        PlaybackEngine candidate = current != null && current.session().sessionId().equals(session.sessionId()) ? current : createEngine();
        publish(Status.BUFFERING);
        return handoff.begin(candidate, engine -> engine.playSessionAsync(session)).whenComplete((started, error) -> {
            synchronized (this) {
            if (requests.get() != request) return;
            if (error == null) publish(candidate.getStatus());
            else publish(handoff.current() == null ? Status.ERROR : handoff.current().getStatus());
            }
        });
    }

    public synchronized CompletableFuture<ZonedDateTime> playDirectAsync(String identifier, FormatType format, ZonedDateTime startTime) {
        long request = requests.incrementAndGet();
        var candidate = createEngine();
        publish(Status.BUFFERING);
        return handoff.begin(candidate, engine -> engine.playDirectAsync(identifier, format, startTime)).whenComplete((started, error) -> {
            synchronized (this) {
            if (requests.get() == request) publish(error == null ? candidate.getStatus() : Status.ERROR);
            }
        });
    }

    public synchronized void stop() {
        requests.incrementAndGet();
        handoff.stop();
        publish(Status.IDLE);
    }
}
