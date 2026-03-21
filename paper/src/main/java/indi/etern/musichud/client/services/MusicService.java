package indi.etern.musichud.client.services;

import indi.etern.musichud.beans.music.AlbumInfo;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

/**
 * Server-side placeholder so shared payload registrations can compile on Paper.
 * Runtime music playback state continues to live on the client.
 */
public final class MusicService {
    private static final MusicService INSTANCE = new MusicService();

    private MusicService() {
    }

    public static MusicService getInstance() {
        return INSTANCE;
    }

    public CompletableFuture<AlbumInfo> loadAlbumDetail(long id) {
        return CompletableFuture.completedFuture(AlbumInfo.NONE);
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

    public void switchMusic(MusicDetail musicDetail, ZonedDateTime startTime, String message) {
    }
}
