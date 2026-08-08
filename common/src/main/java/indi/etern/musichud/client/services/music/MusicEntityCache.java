package indi.etern.musichud.client.services.music;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.Playlist;

import java.util.concurrent.TimeUnit;

/** Shared client-side cache for materialized TuneWeave entities. */
public final class MusicEntityCache {
    private static final Cache<Long, Playlist> PLAYLISTS = cache();
    private static final Cache<Long, Album> ALBUMS = cache();
    private static final Cache<Long, Artist> ARTISTS = cache();

    private MusicEntityCache() {
    }

    private static <T> Cache<Long, T> cache() {
        return CacheBuilder.newBuilder()
                .expireAfterAccess(5, TimeUnit.MINUTES)
                .maximumSize(50)
                .build();
    }

    public static Playlist getPlaylist(long id) {
        return PLAYLISTS.getIfPresent(id);
    }

    public static Album getAlbum(long id) {
        return ALBUMS.getIfPresent(id);
    }

    public static Artist getArtist(long id) {
        return ARTISTS.getIfPresent(id);
    }

    public static void putPlaylist(long id, Playlist playlist) {
        PLAYLISTS.put(id, playlist);
    }

    public static void putAlbum(long id, Album album) {
        ALBUMS.put(id, album);
    }

    public static void putArtist(long id, Artist artist) {
        ARTISTS.put(id, artist);
    }

    public static boolean putPlaylistIfAbsent(long id, Playlist playlist) {
        return PLAYLISTS.asMap().putIfAbsent(id, playlist) == null;
    }

    public static boolean putAlbumIfAbsent(long id, Album album) {
        return ALBUMS.asMap().putIfAbsent(id, album) == null;
    }

    public static boolean putArtistIfAbsent(long id, Artist artist) {
        return ARTISTS.asMap().putIfAbsent(id, artist) == null;
    }
}
