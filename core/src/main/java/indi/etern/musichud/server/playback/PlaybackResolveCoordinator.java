package indi.etern.musichud.server.playback;

import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.PlaybackResolution;
import indi.etern.musichud.network.IPlayerClient;
import indi.etern.musichud.network.payloads.pushMessages.c2s.ResolvePlaybackResultMessage;
import indi.etern.musichud.network.payloads.pushMessages.s2c.ResolvePlaybackRequestMessage;

import java.time.Duration;
import java.util.Optional;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class PlaybackResolveCoordinator {
    private final ConcurrentHashMap<UUID, PendingResolve> pending = new ConcurrentHashMap<>();
    private final ResolveTransport transport;
    private final Duration timeout;

    public PlaybackResolveCoordinator(ResolveTransport transport, Duration timeout) {
        this.transport = transport;
        this.timeout = timeout;
    }

    public Optional<PlaybackResolution> resolveWith(IPlayerClient resolver,
                                                    MusicDetail requestedMusic,
                                                    int revision) {
        UUID requestId = UUID.randomUUID();
        CompletableFuture<ResolvePlaybackResultMessage> future = new CompletableFuture<>();
        pending.put(requestId, new PendingResolve(resolver.getUUID(), revision, future));
        try {
            transport.send(resolver,
                    new ResolvePlaybackRequestMessage(requestId, revision, requestedMusic));
            ResolvePlaybackResultMessage result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!result.success()) {
                return Optional.empty();
            }
            return Optional.of(new PlaybackResolution(result.musicDetail(), result.resourceInfo()));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException | TimeoutException | RuntimeException error) {
            return Optional.empty();
        } finally {
            pending.remove(requestId);
        }
    }

    public void accept(IPlayerClient resolver, ResolvePlaybackResultMessage result) {
        PendingResolve waiting = pending.get(result.requestId());
        if (waiting == null || !waiting.resolverId().equals(resolver.getUUID())
                || waiting.revision() != result.revision()) {
            return;
        }
        waiting.future().complete(result);
    }

    public static List<IPlayerClient> prioritizeResolvers(List<IPlayerClient> players, UUID ownerId) {
        return players.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(Comparator
                        .comparing((IPlayerClient player) -> !player.getUUID().equals(ownerId))
                        .thenComparing(player -> player.getUUID().toString()))
                .toList();
    }

    public static List<IPlayerClient> eligibleResolvers(List<IPlayerClient> players,
                                                        MusicDetail requestedMusic) {
        UUID ownerId = requestedMusic.getPusherInfo().getPlayerUUID();
        List<IPlayerClient> ordered = prioritizeResolvers(players, ownerId);
        if (!requestedMusic.isCloudSource()) {
            return ordered;
        }
        return ordered.stream()
                .filter(player -> player.getUUID().equals(ownerId))
                .toList();
    }

    @FunctionalInterface
    public interface ResolveTransport {
        void send(IPlayerClient resolver, ResolvePlaybackRequestMessage request);
    }

    private record PendingResolve(UUID resolverId, int revision,
                                  CompletableFuture<ResolvePlaybackResultMessage> future) {
    }
}
