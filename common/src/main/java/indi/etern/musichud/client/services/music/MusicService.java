package indi.etern.musichud.client.services.music;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.widget.Toast;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.state.IIdlePlaySourceState;
import indi.etern.musichud.beans.state.IMusicTrackState;
import indi.etern.musichud.beans.state.ISubscribeState;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.interfaces.IClientEventService;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.services.tuneweave.TuneWeaveClientService;
import indi.etern.musichud.client.services.music.states.*;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.utils.IClientDistUtil;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.ClientRegister;
import indi.etern.musichud.interfaces.IClientLoginService;
import indi.etern.musichud.interfaces.IClientMusicService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.payloads.pushMessages.c2s.ClientPushMusicToQueueMessage;
import indi.etern.musichud.network.payloads.pushMessages.c2s.ClientRemoveMusicFromQueueMessage;
import indi.etern.musichud.network.payloads.pushMessages.c2s.VoteSkipCurrentMusicMessage;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import indi.etern.musichud.utils.CollectionUpdateNotifier;
import lombok.*;
import net.minecraft.client.resources.language.I18n;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static indi.etern.musichud.server.api.impl.ncm.CommonCaches.*;

public class MusicService implements IClientMusicService {
    private static final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static volatile MusicService instance;
    private static final TuneWeaveClientService tuneWeave = TuneWeaveClientService.getInstance();

    @Getter(lazy = true)
    private final IIdlePlaySourceState idlePlaySourceState = new IdlePlaySourceState();
    @Getter
    private final Queue<QueueItem> musicQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    @Getter
    private final Set<Consumer<Queue<QueueItem>>> musicQueueRefreshListeners = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<Consumer<QueueItem>> musicQueuePushListeners = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<BiConsumer<Integer, QueueItem>> musicQueueRemoveListeners = ConcurrentHashMap.newKeySet();
    private final Set<Consumer<Boolean>> favoriteIntelligenceStateListeners = ConcurrentHashMap.newKeySet();
    private final Deque<MusicDetail> favoriteIntelligenceBuffer = new ArrayDeque<>();
    private final java.util.concurrent.atomic.AtomicBoolean favoriteIntelligenceLoading =
            new java.util.concurrent.atomic.AtomicBoolean();
    private volatile Playlist favoriteIntelligencePlaylist;
    private volatile boolean favoriteIntelligenceEnabled;
    private volatile boolean favoriteIntelligencePushPending;
    private volatile String favoriteIntelligenceLastReference = "";
    private PlaybackSession currentPublicSession = PlaybackSession.NONE;
    long lastPressTime = 0;
    private UserCollections currentUserCollections;

    private MusicService() {
        musicQueueRefreshListeners.add(this::onQueueRefreshedForFavoriteIntelligence);
    }

    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PACKAGE)
    public static class UserCollections implements IUserCollections {
        @Setter
        private UserCategoryPlaylists userCategoryPlaylists;
        @Setter
        private ObservableSequencedSet<Album> subscribedAlbums;
        @Setter
        private ObservableSequencedSet<Artist> subscribedArtists;
        private boolean loaded = false;
        private volatile long lastReupdateCachesTimestamp = 0;

        public UserCategoryPlaylists getUserCategoryPlaylists() {
            reupdateCachesAsync();
            return userCategoryPlaylists;
        }

        public ObservableSequencedSet<Album> getSubscribedAlbums() {
            reupdateCachesAsync();
            return subscribedAlbums;
        }

        public ObservableSequencedSet<Artist> getSubscribedArtists() {
            reupdateCachesAsync();
            return subscribedArtists;
        }

        private void reupdateCachesAsync() {
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis - lastReupdateCachesTimestamp >= 60000) {
                lastReupdateCachesTimestamp = currentTimeMillis;
                MusicHud.EXECUTOR.submit(() -> {
                    if (userCategoryPlaylists != null) {
                        Playlist likeList = userCategoryPlaylists.getLikeList();
                        if (playlistsCache.asMap().putIfAbsent(likeList.getId(), likeList) == null) {
                            CollectionUpdateNotifier.notifyPlaylistUpdated(likeList.getId());
                        }
                        userCategoryPlaylists.getCreatedPlaylist()
                                .forEach(playlist -> {
                                    if (playlist.getMusicDetails() != null && playlist.getMusicDetails().size() == playlist.getMusicTrackCount()
                                            && playlistsCache.asMap().putIfAbsent(playlist.getId(), playlist) == null) {
                                        CollectionUpdateNotifier.notifyPlaylistUpdated(playlist.getId());
                                    }
                                });
                        userCategoryPlaylists.getSubscribedPlaylist()
                                .forEach(playlist -> {
                                    if (playlist.getMusicDetails() != null && playlist.getMusicDetails().size() == playlist.getMusicTrackCount()
                                            && playlistsCache.asMap().putIfAbsent(playlist.getId(), playlist) == null) {
                                        CollectionUpdateNotifier.notifyPlaylistUpdated(playlist.getId());
                                    }
                                });
                    }
                    if (subscribedAlbums != null) {
                        subscribedAlbums.forEach(album -> {
                            if (album.getMusicDetails() != null && album.getMusicDetails().size() == album.getMusicTrackCount()
                                    && albumsCache.asMap().putIfAbsent(album.getId(), album) == null) {
                                CollectionUpdateNotifier.notifyAlbumUpdated(album.getId());
                            }
                        });
                    }
                    if (subscribedArtists != null) {
                        subscribedArtists.forEach(artist -> {
                            if (artist.getMusicDetails() != null && !artist.getMusicDetails().isEmpty() && !artist.getDescription().isEmpty()) {
                                artistsCache.asMap().putIfAbsent(artist.getId(), artist);
                            }
                        });
                    }
                });
            }
        }
    }


    public static MusicService getInstance() {
        if (instance == null) {
            synchronized (MusicService.class) {
                if (instance == null) {
                    instance = new MusicService();
                }
            }
        }
        return instance;
    }

    public boolean isFavoriteIntelligenceEnabled(Playlist playlist) {
        Playlist active = favoriteIntelligencePlaylist;
        return favoriteIntelligenceEnabled && active != null && playlist != null
                && active.getSourceRef().equals(playlist.getSourceRef());
    }

    public Unregister onFavoriteIntelligenceStateChange(Consumer<Boolean> listener) {
        favoriteIntelligenceStateListeners.add(listener);
        return () -> favoriteIntelligenceStateListeners.remove(listener);
    }

    public CompletableFuture<Boolean> setFavoriteIntelligenceEnabled(Playlist playlist, boolean enabled) {
        if (!enabled) {
            disableFavoriteIntelligence();
            return CompletableFuture.completedFuture(false);
        }
        if (!tuneWeave.supportsFavoriteIntelligence(playlist)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Favorite intelligence is unavailable for this playlist"));
        }
        synchronized (favoriteIntelligenceBuffer) {
            favoriteIntelligencePlaylist = playlist;
            favoriteIntelligenceEnabled = true;
            favoriteIntelligencePushPending = false;
            favoriteIntelligenceLastReference = "";
            favoriteIntelligenceBuffer.clear();
        }
        return refillFavoriteIntelligence().thenApply(ignored -> {
            notifyFavoriteIntelligenceState(true);
            queueNextFavoriteIntelligenceTrack();
            return true;
        }).exceptionallyCompose(error -> {
            disableFavoriteIntelligence();
            return CompletableFuture.failedFuture(error);
        });
    }

    private CompletableFuture<Void> refillFavoriteIntelligence() {
        Playlist playlist = favoriteIntelligencePlaylist;
        if (!favoriteIntelligenceEnabled || playlist == null
                || !favoriteIntelligenceLoading.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        String start = favoriteIntelligenceLastReference;
        return CompletableFuture.supplyAsync(
                () -> tuneWeave.loadFavoriteIntelligence(playlist, start), MusicHud.EXECUTOR)
                .thenAccept(tracks -> {
                    if (tracks.isEmpty()) {
                        throw new IllegalStateException("TuneWeave returned an empty favorite intelligence queue");
                    }
                    synchronized (favoriteIntelligenceBuffer) {
                        for (MusicDetail track : tracks) {
                            if (!track.getSourceRef().equals(favoriteIntelligenceLastReference)) {
                                favoriteIntelligenceBuffer.addLast(track);
                            }
                        }
                    }
                }).whenComplete((ignored, error) -> favoriteIntelligenceLoading.set(false));
    }

    private void onQueueRefreshedForFavoriteIntelligence(Queue<QueueItem> queue) {
        if (!favoriteIntelligenceEnabled) return;
        if (!queue.isEmpty()) {
            favoriteIntelligencePushPending = false;
        } else if (!favoriteIntelligencePushPending) {
            queueNextFavoriteIntelligenceTrack();
        }
    }

    private void queueNextFavoriteIntelligenceTrack() {
        if (!favoriteIntelligenceEnabled || favoriteIntelligencePushPending || !musicQueue.isEmpty()) return;
        MusicDetail next;
        synchronized (favoriteIntelligenceBuffer) {
            next = favoriteIntelligenceBuffer.pollFirst();
        }
        if (next == null) {
            if (favoriteIntelligenceLoading.get()) return;
            refillFavoriteIntelligence().thenRun(this::queueNextFavoriteIntelligenceTrack)
                    .exceptionally(error -> {
                        MusicHud.LOGGER.warn("Failed to refill favorite intelligence queue", error);
                        disableFavoriteIntelligence();
                        return null;
                    });
            return;
        }
        favoriteIntelligenceLastReference = next.getSourceRef();
        favoriteIntelligencePushPending = true;
        sendPushMusicToQueue(next);
    }

    private void disableFavoriteIntelligence() {
        favoriteIntelligenceEnabled = false;
        favoriteIntelligencePlaylist = null;
        favoriteIntelligencePushPending = false;
        favoriteIntelligenceLastReference = "";
        synchronized (favoriteIntelligenceBuffer) {
            favoriteIntelligenceBuffer.clear();
        }
        notifyFavoriteIntelligenceState(false);
    }

    private void notifyFavoriteIntelligenceState(boolean enabled) {
        favoriteIntelligenceStateListeners.forEach(listener -> listener.accept(enabled));
    }

    public static void resetCurrentMusicStatus() {
        if (instance != null) {
            instance.disableFavoriteIntelligence();
            instance.resetPublicPlayback();
            instance.getIdlePlaySourceState().local().reset();
            instance.musicQueue.clear();
        }
        if (HudRendererManager.isLoaded()) {
            HudRendererManager.getInstance().reset();
        }
    }

    @Override
    public CompletableFuture<Playlist> loadPlaylistDetail(long id, boolean ignoreCache) {
        if (!ignoreCache) {
            Playlist cachedPlaylist = playlistsCache.getIfPresent(id);
            if (cachedPlaylist != null && cachedPlaylist.getMusicDetails() != null
                    && (!cachedPlaylist.getMusicDetails().isEmpty() || cachedPlaylist.getMusicTrackCount() == 0)) {
                return CompletableFuture.completedFuture(cachedPlaylist);
            }
        }
        if (tuneWeave.isAvailable()) {
            if (!tuneWeave.hasPlaylist(id)) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        "Unknown TuneWeave playlist reference: " + id));
            }
            return CompletableFuture.supplyAsync(() -> tuneWeave.loadPlaylistDetail(id), MusicHud.EXECUTOR)
                    .thenApply(playlist -> {
                        playlistsCache.put(id, playlist);
                        return playlist;
                    });
        }
        return CompletableFuture.failedFuture(new IllegalStateException("TuneWeave is unavailable"));
    }

    @Override
    public CompletableFuture<Album> loadAlbumDetail(long id, boolean ignoreCache) {
        if (!ignoreCache) {
            Album cachedAlbum = albumsCache.getIfPresent(id);
            if (cachedAlbum != null && cachedAlbum.getMusicDetails() != null
                    && (!cachedAlbum.getMusicDetails().isEmpty() || cachedAlbum.getMusicTrackCount() == 0)) {
                return CompletableFuture.completedFuture(cachedAlbum);
            }
        }
        if (tuneWeave.isAvailable()) {
            if (!tuneWeave.hasAlbum(id)) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        "Unknown TuneWeave album reference: " + id));
            }
            return CompletableFuture.supplyAsync(() -> tuneWeave.loadAlbumDetail(id), MusicHud.EXECUTOR)
                    .thenApply(album -> {
                        albumsCache.put(id, album);
                        return album;
                    });
        }
        return CompletableFuture.failedFuture(new IllegalStateException("TuneWeave is unavailable"));
    }

    @Override
    public synchronized void refreshQueue(Queue<QueueItem> queue) {
        List<QueueItem> local = new ArrayList<>(musicQueue);
        List<QueueItem> fresh = new ArrayList<>(queue);
        List<QueueItem> toRemove = new ArrayList<>();
        int i = 0, j = 0;
        while (i < local.size() && j < fresh.size()) {
            if (sameItem(local.get(i), fresh.get(j))) {
                i++;
                j++;
            } else {
                // relative order is preserved, so local[i] must have been removed
                toRemove.add(local.get(i));
                i++;
            }
        }
        while (i < local.size()) {
            toRemove.add(local.get(i++));
        }
        for (int k = toRemove.size() - 1; k >= 0; k--) {
            QueueItem removed = toRemove.get(k);
            int index = 0;
            for (QueueItem item : musicQueue) {
                if (item == removed) {
                    break;
                }
                index++;
            }
            musicQueue.remove(removed);
            int finalIndex = index;
            musicQueueRemoveListeners.forEach(l -> l.accept(finalIndex, removed));
        }
        for (; j < fresh.size(); j++) {
            QueueItem added = fresh.get(j);
            musicQueue.add(added);
            musicQueuePushListeners.forEach(l -> l.accept(added));
        }
        musicQueueRefreshListeners.forEach(l -> l.accept(queue));
    }

    private static boolean sameItem(QueueItem a, QueueItem b) {
        return a.queueUniqueID().equals(b.queueUniqueID());
    }

    @Override
    public void sendPushMusicToQueue(MusicDetail musicDetail) {
        clientNetworkService.sendToServer(new ClientPushMusicToQueueMessage(musicDetail));
    }

    @Override
    public void sendRemoveMusicFromQueue(int index, QueueItem item) {
        clientNetworkService.sendToServer(new ClientRemoveMusicFromQueueMessage(index, item.musicDetail().getId(), item.queueUniqueID()));
    }

    @Override
    public synchronized void switchMusic(PlaybackSession playbackSession,
                                         MusicDetail nextIdleMusicDetail, String message) {
        Objects.requireNonNull(playbackSession, "playbackSession");
        if (!playbackSession.supersedes(currentPublicSession)) {
            return;
        }
        currentPublicSession = playbackSession;
        applyPublicPlayback(playbackSession, nextIdleMusicDetail, message);
    }

    private synchronized void resetPublicPlayback() {
        currentPublicSession = PlaybackSession.NONE;
        applyPublicPlayback(PlaybackSession.NONE, MusicDetail.NONE, "");
    }

    private void applyPublicPlayback(PlaybackSession playbackSession,
                                     MusicDetail nextIdleMusicDetail, String message) {
        if (clientConfig.getEnable()) {
            MusicDetail musicDetail = playbackSession.musicDetail();
            if (!musicQueue.isEmpty()) {// preload image
                MusicDetail peek = musicQueue.peek().musicDetail();
                ImageUtils.downloadAsync(peek.getAlbum().getThumbnailPicUrl(240));
                HudRendererManager.getInstance().preloadAlbumImage(peek.getAlbum());
            } else if (nextIdleMusicDetail != null && !nextIdleMusicDetail.equals(MusicDetail.NONE)) {
                ImageUtils.downloadAsync(nextIdleMusicDetail.getAlbum().getThumbnailPicUrl(240));
                HudRendererManager.getInstance().preloadAlbumImage(nextIdleMusicDetail.getAlbum());
            }
            if (!message.isEmpty()) {
                MuiModApi.postToUiThread(() -> {
                    //noinspection UnstableApiUsage
                    Context context = UIManager.getInstance().getDecorView().getContext();
                    ToastUtil.show(Toast.makeText(context, message, Toast.LENGTH_SHORT));
                });
            }
            NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
            if (!musicDetail.equals(MusicDetail.NONE)) {
                ImageUtils.downloadAsync(musicDetail.getAlbum().getThumbnailPicUrl(240));
                StreamAudioPlayer streamAudioPlayer = StreamAudioPlayer.getInstance();
                nowPlayingInfo.switchMusicInfo(musicDetail, nextIdleMusicDetail);
                streamAudioPlayer.playSessionAsync(playbackSession)
                        .thenAccept(startedAt -> nowPlayingInfo.startAt(musicDetail, startedAt))
                        .exceptionally(e -> null);
            } else {//TODO optional account sync
                nowPlayingInfo.switchMusicInfo(musicDetail, nextIdleMusicDetail);
//                nowPlayingInfo.switchMusic(MusicDetail.NONE,MusicDetail.NONE,null);
                StreamAudioPlayer streamAudioPlayer = StreamAudioPlayer.getInstance();
                streamAudioPlayer.stop();
            }
        }
    }

    @Override
    public PlaybackResolution resolvePublicPlayback(MusicDetail requestedMusic) {
        if (requestedMusic == null || requestedMusic == MusicDetail.NONE
                || requestedMusic.getSourceRef().isBlank() || !tuneWeave.isAvailable()) {
            throw new IllegalArgumentException("TuneWeave playback reference is unavailable");
        }
        MusicDetail canonical;
        if (requestedMusic.isCloudSource()) {
            canonical = tuneWeave.loadCloudTrack(requestedMusic);
        } else if ("track".equals(requestedMusic.getSourceKind())) {
            canonical = tuneWeave.loadTrackDetail(requestedMusic);
        } else if ("video".equals(requestedMusic.getSourceKind())) {
            canonical = tuneWeave.loadVideoPlaybackDetail(requestedMusic);
        } else if (Set.of("podcast_episode", "radio_station")
                .contains(requestedMusic.getSourceKind())) {
            canonical = tuneWeave.loadProgramPlaybackDetail(requestedMusic);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported TuneWeave playback kind: " + requestedMusic.getSourceKind());
        }
        if (canonical == null || canonical == MusicDetail.NONE) {
            throw new IllegalStateException("TuneWeave returned no canonical track");
        }
        canonical.setSourceRef(requestedMusic.getSourceRef());
        canonical.setSourceKind(requestedMusic.getSourceKind());
        canonical.setSourcePartRef(requestedMusic.getSourcePartRef());
        canonical.setClientHostedUni(requestedMusic.isClientHostedUni());
        canonical.setCloudSource(requestedMusic.isCloudSource());
        canonical.setPusherInfo(requestedMusic.getPusherInfo());
        try {
            LyricInfo lyrics;
            if (canonical.isCloudSource()) {
                lyrics = tuneWeave.loadCloudLyrics(canonical);
            } else if ("video".equals(canonical.getSourceKind())) {
                lyrics = tuneWeave.loadVideoLyrics(canonical);
            } else {
                lyrics = tuneWeave.loadLyrics(canonical);
            }
            canonical.setLyricInfo(lyrics == null ? LyricInfo.NONE : lyrics);
        } catch (RuntimeException ignored) {
            canonical.setLyricInfo(LyricInfo.NONE);
        }
        MusicResourceInfo resource = tuneWeave.getMusicResourceInfo(
                canonical, clientConfig.getPrimaryChosenQuality());
        if (resource == null || resource == MusicResourceInfo.NONE || resource.getUrl().isBlank()) {
            throw new IllegalStateException("TuneWeave returned no playable resource");
        }
        return new PlaybackResolution(canonical, resource);
    }

    @Override
    public CompletableFuture<Artist> loadArtist(long id, boolean ignoreCache) {
        if (!ignoreCache) {
            Artist cachedArtist = artistsCache.getIfPresent(id);
            if (cachedArtist != null && cachedArtist.getMusicDetails() != null) {
                return CompletableFuture.completedFuture(cachedArtist);
            }
        }
        if (tuneWeave.isAvailable()) {
            if (!tuneWeave.hasArtist(id)) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        "Unknown TuneWeave artist reference: " + id));
            }
            return CompletableFuture.supplyAsync(() -> tuneWeave.loadArtistDetail(id), MusicHud.EXECUTOR)
                    .thenApply(artist -> {
                        artistsCache.put(id, artist);
                        return artist;
                    });
        }
        return CompletableFuture.failedFuture(new IllegalStateException("TuneWeave is unavailable"));
    }

    @Override
    public CompletableFuture<List<MusicDetail>> loadArtistMusic(long id, int offset) {
        if (tuneWeave.isAvailable()) {
            if (!tuneWeave.hasArtist(id)) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        "Unknown TuneWeave artist reference: " + id));
            }
            return CompletableFuture.supplyAsync(() -> tuneWeave.loadArtistTracks(id, offset), MusicHud.EXECUTOR);
        }
        return CompletableFuture.failedFuture(new IllegalStateException("TuneWeave is unavailable"));
    }

    @Override
    public void voteForSkipCurrent() {
        if (NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail() != null) {
            clientNetworkService.sendToServer(new VoteSkipCurrentMusicMessage(NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail().getId()));
        }
    }

    @Override
    public void keyBindsVoteSkipCurrent() {
        MusicDetail currentlyPlayingMusicDetail = NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail();
        if (currentlyPlayingMusicDetail != null && currentlyPlayingMusicDetail != MusicDetail.NONE) {
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis - lastPressTime <= 3000) {
                lastPressTime = 0;
                voteForSkipCurrent();
                MuiModApi.postToUiThread(() -> {
                    //noinspection UnstableApiUsage
                    Context context = UIManager.getInstance().getDecorView().getContext();
                    String s = IClientDistUtil.getInstance().inSinglePlayer()
                            ? I18n.get(MusicHud.MOD_ID + ".text.skipConfirmed")
                            : I18n.get(MusicHud.MOD_ID + ".text.voteForSkipConfirmed");
                    ToastUtil.show(Toast.makeText(context, s, Toast.LENGTH_SHORT));
                });
            } else {
                lastPressTime = currentTimeMillis;
                MuiModApi.postToUiThread(() -> {
                    //noinspection UnstableApiUsage
                    Context context = UIManager.getInstance().getDecorView().getContext();
                    String s = IClientDistUtil.getInstance().inSinglePlayer()
                            ? I18n.get(MusicHud.MOD_ID + ".text.confirmSkip")
                            : I18n.get(MusicHud.MOD_ID + ".text.confirmVoteForSkip");
                    ToastUtil.show(Toast.makeText(context, s, Toast.LENGTH_SHORT));
                });
            }
        }
    }

    @Override
    public CompletableFuture<UserCategoryPlaylists> loadUserPlaylists(boolean ignoreCache) {
        if (!LoginService.getInstance().isLogined()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Cannot call AccountService.loadUserPlaylists when logined as anonymous"));
        }
        if (tuneWeave.hasCredential(tuneWeave.defaultPlatform())) {
            return CompletableFuture.supplyAsync(tuneWeave::loadAccountPlaylists, MusicHud.EXECUTOR);
        }
        return CompletableFuture.failedFuture(new IllegalStateException("TuneWeave account credential is unavailable"));
    }

    @Override
    public CompletableFuture<LinkedHashSet<Album>> loadUserAlbums(boolean ignoreCache) {
        if (!LoginService.getInstance().isLogined()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Cannot call AccountService.loadUserAlbums when logined as anonymous"));
        }
        if (tuneWeave.hasCredential(tuneWeave.defaultPlatform())) {
            return CompletableFuture.supplyAsync(tuneWeave::loadAccountAlbums, MusicHud.EXECUTOR);
        }
        return CompletableFuture.failedFuture(new IllegalStateException("TuneWeave account credential is unavailable"));
    }

    @Override
    public CompletableFuture<LinkedHashSet<Artist>> loadUserArtists(boolean ignoreCache) {
        if (!LoginService.getInstance().isLogined()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Cannot call AccountService.loadUserArtists when logined as anonymous"));
        }
        if (tuneWeave.hasCredential(tuneWeave.defaultPlatform())) {
            return CompletableFuture.supplyAsync(tuneWeave::loadAccountArtists, MusicHud.EXECUTOR);
        }
        return CompletableFuture.failedFuture(new IllegalStateException("TuneWeave account credential is unavailable"));
    }

    @Override
    public CompletableFuture<Artist> loadArtistDetailAsync(Artist artist) {
        List<MusicDetail> musicDetails = artist.getMusicDetails();
        if (musicDetails == null || musicDetails.isEmpty()) {
            return loadArtist(artist.getId(), false);
        } else return CompletableFuture.completedFuture(artist);
    }

    @Override
    public CompletableFuture<Collection<MusicDetail>> loadMoreMusicOfArtist(Artist artist) {
        return MusicService.getInstance().loadArtistMusic(artist.getId(), artist.getMusicDetails().size())
                .thenApply(musicDetails1 -> {
                    artist.getMusicDetails().addAll(musicDetails1);
                    return musicDetails1;
                });
    }

    @Override
    public CompletableFuture<loadMusicCollectionMoreDataResult> loadMoreMusicOfCollection(MusicCollection musicCollection, boolean ignoreCache) {
        if (musicCollection instanceof Album album) {
            return loadAlbumDetail(album.getId(), ignoreCache)
                    .thenApply(albumInfo -> new loadMusicCollectionMoreDataResult(albumInfo, albumInfo.getMusicDetails()));
        } else if (musicCollection instanceof Playlist playlist) {
            return loadPlaylistDetail(playlist.getId(), ignoreCache)
                    .thenApply(playlist1 -> new loadMusicCollectionMoreDataResult(playlist1, playlist1.getTracks()));
        } else {
            return CompletableFuture.failedFuture(new IllegalStateException());
        }
    }

    @Override
    public IMusicTrackState getMusicTrackState(MusicDetail musicDetail) {
        if (musicDetail == null || musicDetail.equals(MusicDetail.NONE)) {
            return MusicTrackState.NONE;
        }
        return new MusicTrackState(musicDetail);
    }

    @Override
    public ISubscribeState<Playlist> getPlaylistSubscribeState(Playlist playlist) {
        return new PlaylistSubscribeState(playlist.getId());
    }

    @Override
    public ISubscribeState<Album> getAlbumSubscribeState(Album album) {
        return new AlbumSubscribeState(album.getId());
    }

    @Override
    public ISubscribeState<Artist> getArtistSubscribedState(Artist artist) {
        return new ArtistSubscribeState(artist.getId());
    }

    @Override
    public synchronized CompletableFuture<UserCollections> loadUserCollections(boolean ignoreCache) {
        if (!ignoreCache && currentUserCollections != null && currentUserCollections.loaded) {
            return CompletableFuture.completedFuture(currentUserCollections);
        } else {
            TuneWeavePlatform platform = tuneWeave.defaultPlatform();
            UserCollections requestedCollections = new UserCollections();
            currentUserCollections = requestedCollections;
            if (tuneWeave.hasCredential(platform)) {
                return CompletableFuture.supplyAsync(() -> {
                    requestedCollections.setUserCategoryPlaylists(tuneWeave.loadAccountPlaylists(platform));
                    if (platform == TuneWeavePlatform.BILIBILI) {
                        requestedCollections.setSubscribedAlbums(new ObservableSequencedSet<>());
                        requestedCollections.setSubscribedArtists(new ObservableSequencedSet<>());
                    } else {
                        requestedCollections.setSubscribedAlbums(new ObservableSequencedSet<>(
                                tuneWeave.loadAccountAlbums(platform)));
                        requestedCollections.setSubscribedArtists(new ObservableSequencedSet<>(
                                tuneWeave.loadAccountArtists(platform)));
                    }
                    requestedCollections.loaded = true;
                    return requestedCollections;
                }, MusicHud.EXECUTOR);
            }
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "TuneWeave account credential is unavailable"));
        }
    }

    public synchronized void invalidateUserCollections() {
        currentUserCollections = null;
    }

    @RegisterMark
    public static class RegisterImpl implements ClientRegister {
        @Override
        public void register() {
            LoginService.getInstance().addLoginStateListener(state -> {
                if (state != IClientLoginService.LoginState.UNLOGGED) {
                    MusicService.getInstance().getIdlePlaySourceState().local().loadFromConfig();
                }
            });
            IClientEventService.getInstance().registerClientPlayerQuit((player) -> {
                MusicHud.EXECUTOR.execute(MusicService::resetCurrentMusicStatus);
            });
        }
    }

}
