package indi.etern.musichud.server.api;

import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.music.actions.SubscribableType;
import indi.etern.musichud.beans.music.actions.SubscribeAction;
import indi.etern.musichud.server.api.impl.tuneweave.TuneWeaveMusicApiService;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public interface IMusicApiService {
    static IMusicApiService getInstance(ApiProvider apiProvider) {
        if (Objects.requireNonNull(apiProvider) == ApiProvider.TUNEWEAVE) {
            return TuneWeaveMusicApiService.getInstance();
        }
        throw new IllegalArgumentException("Invalid api provider");
    }

    Playlist getPlaylistDetail(long id, boolean ignoreCache, @Nullable UUID player);

    default Playlist getPlaylistDetail(long id, @Nullable UUID player) {
        return getPlaylistDetail(id, false, player);
    }

    List<Album> searchAlbums(String keywords, int offset);

    List<Artist> searchArtists(String keywords, int offset);

    List<MusicDetail> searchMusic(String keywords, int offset);

    List<Playlist> searchPlaylists(String keywords, int offset);

    <T> T search(String keywords, int offset, int limit, SearchType searchType, Function<String, T> transformer);

    List<MusicDetail> getMusicDetailByIds(List<Long> ids, UUID playerUUID);

    Album getAlbumInfoDetail(long id, boolean ignoreCache, UUID playerUUID);

    default Album getAlbumInfoDetail(long id, UUID playerUUID) {
        return getAlbumInfoDetail(id, false, playerUUID);
    }

    Artist getArtistDetail(long id, UUID playerUUID);

    List<MusicDetail> getArtistMoreMusic(long id, int offset, UUID playerUUID);

    MusicResourceInfo getResourceInfo(MusicDetail musicDetail, Quality quality, UUID playerUUID);

    UserCategoryPlaylists getPlayersUserPlaylists(boolean ignoreCache, UUID playerUUID);

    LinkedHashSet<Album> getPlayersUserSubscribedAlbums(boolean ignoreCache, UUID playerUUID);

    LinkedHashSet<Artist> getPlayersUserSubscribedArtists(boolean ignoreCache, UUID playerUUID);

    default List<Playlist> getPlayersUserSubscribedPlaylists(UUID playerUUID) {
        UserCategoryPlaylists playlists = getPlayersUserPlaylists(false, playerUUID);
        if (playlists == null) {
            return List.of();
        }
        java.util.ArrayList<Playlist> result = new java.util.ArrayList<>();
        if (playlists.getLikeList() != null && !playlists.getLikeList().equals(Playlist.EMPTY)) {
            result.add(playlists.getLikeList());
        }
        if (playlists.getCreatedPlaylist() != null) {
            result.addAll(playlists.getCreatedPlaylist());
        }
        if (playlists.getSubscribedPlaylist() != null) {
            result.addAll(playlists.getSubscribedPlaylist());
        }
        return result;
    }

    default List<Album> getPlayersUserSubscribedAlbums(UUID playerUUID) {
        return new java.util.ArrayList<>(getPlayersUserSubscribedAlbums(false, playerUUID));
    }

    default List<Artist> getPlayersUserSubscribedArtists(UUID playerUUID) {
        return new java.util.ArrayList<>(getPlayersUserSubscribedArtists(false, playerUUID));
    }

    LyricInfo getLyricInfo(MusicDetail musicDetail);

    void addToPlaylist(long playlistId, long musicId, UUID uuid);

    void removeFromPlaylist(long playlistId, long musicId, UUID uuid);

    void userSubscribe(long id, SubscribableType subscribableType, SubscribeAction action, UUID playerUUID);
}
