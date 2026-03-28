package indi.etern.musichud.client.services;

import indi.etern.musichud.beans.music.AlbumInfo;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.Playlist;

import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

/**
 * Server-side placeholder so shared payload registrations can compile on Paper.
 * Runtime music playback state continues to live on the client.
 */
public final class MusicService {
    private static final MusicService INSTANCE = new MusicService();
    private final Queue<MusicDetail> musicQueue = new ArrayDeque<>();

    private MusicService() {
    }

    public static MusicService getInstance() {
        return INSTANCE;
    }

    public Queue<MusicDetail> getMusicQueue() {
        return musicQueue;
    }

    public CompletableFuture<AlbumInfo> loadAlbumDetail(long id, boolean ignoreCache) {
        return loadAlbumDetail(id);
    }

    public CompletableFuture<AlbumInfo> loadAlbumDetail(long id) {
        return CompletableFuture.completedFuture(AlbumInfo.NONE);
    }

    public CompletableFuture<Playlist> loadPlaylistDetail(long id, boolean ignoreCache) {
        return loadPlaylistDetail(id);
    }

    public CompletableFuture<Playlist> loadPlaylistDetail(long id) {
        return CompletableFuture.completedFuture(Playlist.EMPTY);
    }

    public CompletableFuture<Artist> loadArtist(long id) {
        return CompletableFuture.completedFuture(new Artist());
    }

    public CompletableFuture<List<MusicDetail>> loadArtistMusic(long id, int offset) {
        return CompletableFuture.completedFuture(List.of());
    }

    public void refreshQueue(Queue<MusicDetail> queue) {
    }

    public void switchMusic(MusicDetail musicDetail, MusicDetail nextIdleMusicDetail, ZonedDateTime startTime, String message) {
    }

    public void switchMusic(MusicDetail musicDetail, ZonedDateTime startTime, String message) {
    }

    public void updateAllIdlePlaySources(List<Playlist> playlistSources, List<AlbumInfo> albumSources) {
    }
}
