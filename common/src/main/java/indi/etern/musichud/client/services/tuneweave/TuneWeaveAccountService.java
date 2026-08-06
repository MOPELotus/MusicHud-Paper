package indi.etern.musichud.client.services.tuneweave;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.UserCategoryPlaylists;
import indi.etern.musichud.server.api.tuneweave.TuneWeaveApiClient;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.bool;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.elements;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.object;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.string;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.unwrap;

/** Loads the authenticated user's collections and recommendation queue. */
final class TuneWeaveAccountService {
    private final TuneWeaveGateway gateway;
    private final TuneWeaveAuthenticationService authentication;
    private final TuneWeaveEntityMapper entities;
    private final Map<TuneWeavePlatform, String> favoritePlaylistReferences = new ConcurrentHashMap<>();

    TuneWeaveAccountService(TuneWeaveGateway gateway,
                            TuneWeaveAuthenticationService authentication,
                            TuneWeaveEntityMapper entities) {
        this.gateway = Objects.requireNonNull(gateway);
        this.authentication = Objects.requireNonNull(authentication);
        this.entities = Objects.requireNonNull(entities);
    }

    UserCategoryPlaylists loadPlaylists() {
        return loadPlaylists(gateway.defaultPlatform());
    }

    UserCategoryPlaylists loadPlaylists(TuneWeavePlatform platform) {
        Objects.requireNonNull(platform, "platform");
        if (platform == TuneWeavePlatform.BILIBILI) {
            return loadBilibiliFavoriteFolders();
        }
        Playlist liked = entities.toPlaylist(platform, unwrap(gateway.requestForPlatform(
                platform, "GET", "/v1/account/favorites/playlist",
                Map.of("platform", platform.apiName()), null).data()));
        if (liked == Playlist.EMPTY) {
            throw new TuneWeaveApiClient.TuneWeaveException(
                    "TuneWeave favorite playlist response did not contain a reference", false);
        }
        favoritePlaylistReferences.put(platform, liked.getSourceRef());

        JsonElement data = gateway.requestForPlatform(platform, "GET", "/v1/account/playlists",
                Map.of("platform", platform.apiName(), "limit", "100", "offset", "0"), null).data();
        ObservableSequencedSet<Playlist> created = new ObservableSequencedSet<>();
        ObservableSequencedSet<Playlist> subscribed = new ObservableSequencedSet<>();
        for (JsonElement item : elements(data)) {
            JsonObject raw = unwrap(item);
            Playlist playlist = entities.toPlaylist(platform, raw);
            if (playlist == Playlist.EMPTY || liked.getSourceRef().equals(playlist.getSourceRef())) {
                continue;
            }
            (bool(raw, "subscribed", false) ? subscribed : created).add(playlist);
        }
        return new UserCategoryPlaylists(liked, created, subscribed);
    }

    LinkedHashSet<Album> loadAlbums() {
        return loadAlbums(gateway.defaultPlatform());
    }

    LinkedHashSet<Album> loadAlbums(TuneWeavePlatform platform) {
        JsonElement data = gateway.requestForPlatform(platform, "GET", "/v1/account/library/albums",
                Map.of("platform", platform.apiName(), "limit", "100", "offset", "0"), null).data();
        LinkedHashSet<Album> result = new LinkedHashSet<>();
        for (JsonElement item : elements(data)) {
            Album album = entities.toAlbum(platform, unwrap(item));
            if (album != Album.NONE) {
                result.add(album);
            }
        }
        return result;
    }

    LinkedHashSet<Artist> loadArtists() {
        return loadArtists(gateway.defaultPlatform());
    }

    LinkedHashSet<Artist> loadArtists(TuneWeavePlatform platform) {
        JsonElement data = gateway.requestForPlatform(platform, "GET", "/v1/account/following/artists",
                Map.of("platform", platform.apiName(), "limit", "100", "offset", "0"), null).data();
        LinkedHashSet<Artist> result = new LinkedHashSet<>();
        for (JsonElement item : elements(data)) {
            Artist artist = entities.toArtist(platform, unwrap(item));
            if (!artist.getSourceRef().isBlank()) {
                result.add(artist);
            }
        }
        return result;
    }

    boolean isFavoritePlaylist(Playlist playlist) {
        if (playlist == null || playlist == Playlist.EMPTY || playlist.getSourceRef().isBlank()) {
            return false;
        }
        return isFavoritePlaylistReference(playlist.getSourceRef());
    }

    boolean isFavoritePlaylistReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return false;
        }
        TuneWeavePlatform platform = TuneWeaveReference.platform(reference);
        return reference.equals(favoritePlaylistReferences.get(platform));
    }

    boolean supportsFavoriteIntelligence(Playlist playlist) {
        return isFavoritePlaylist(playlist)
                && TuneWeaveReference.platform(playlist.getSourceRef()) == TuneWeavePlatform.NETEASE;
    }

    List<MusicDetail> loadFavoriteIntelligence(Playlist playlist, String startReference) {
        if (!supportsFavoriteIntelligence(playlist)) {
            throw new IllegalArgumentException(
                    "Favorite intelligence is only available for NetEase favorites");
        }
        TuneWeavePlatform platform = TuneWeavePlatform.NETEASE;
        List<JsonElement> favoriteTracks = elements(gateway.requestForPlatform(
                platform, "GET", "/v1/account/favorites/tracks",
                Map.of("platform", platform.apiName(), "limit", "1", "offset", "0"), null).data());
        if (favoriteTracks.isEmpty()) {
            return List.of();
        }
        MusicDetail seed = entities.toTrack(platform, unwrap(favoriteTracks.getFirst()));
        if (seed == MusicDetail.NONE || seed.getSourceRef().isBlank()) {
            return List.of();
        }

        Map<String, String> query = new LinkedHashMap<>();
        query.put("platform", platform.apiName());
        query.put("seed", seed.getSourceRef());
        query.put("count", "1");
        if (startReference != null && startReference.startsWith(platform.apiName() + ':')) {
            query.put("start", startReference);
        }
        JsonObject queue = object(gateway.requestForPlatform(
                platform, "GET", "/v1/account/favorites/tracks/intelligence", query, null).data());
        LinkedHashMap<String, MusicDetail> result = new LinkedHashMap<>();
        for (JsonElement value : elements(queue.get("items"))) {
            JsonObject item = unwrap(value);
            JsonObject trackData = object(item.get("track"));
            MusicDetail track = entities.toTrack(platform, trackData.isEmpty() ? item : trackData);
            if (track != MusicDetail.NONE && !track.getSourceRef().isBlank()) {
                result.putIfAbsent(track.getSourceRef(), track);
            }
        }
        return List.copyOf(result.values());
    }

    private UserCategoryPlaylists loadBilibiliFavoriteFolders() {
        TuneWeaveSession session = authentication.cachedSession(TuneWeavePlatform.BILIBILI);
        if (session == null) {
            session = authentication.loadSession(TuneWeavePlatform.BILIBILI);
        }
        if (session == null || session.userId() == null || session.userId().isBlank()) {
            throw new TuneWeaveApiClient.TuneWeaveException(
                    "Bilibili session did not provide a user id", false);
        }
        String reference = "bilibili:" + session.userId();
        JsonElement data = gateway.requestForPlatform(TuneWeavePlatform.BILIBILI, "GET",
                "/v1/users/" + TuneWeaveApiClient.encodePathSegment(reference) + "/playlists/created",
                Map.of("limit", "100", "offset", "0"), null).data();
        ObservableSequencedSet<Playlist> created = new ObservableSequencedSet<>();
        ObservableSequencedSet<Playlist> subscribed = new ObservableSequencedSet<>();
        Playlist defaultFolder = null;
        for (JsonElement item : elements(data)) {
            JsonObject raw = unwrap(item);
            String playlistReference = string(raw, "ref", "");
            if (!playlistReference.startsWith("bilibili:favorite:")) {
                continue;
            }
            Playlist playlist = entities.toPlaylist(TuneWeavePlatform.BILIBILI, raw);
            JsonObject extensions = raw.has("extensions") && raw.get("extensions").isJsonObject()
                    ? raw.getAsJsonObject("extensions") : new JsonObject();
            if (bool(extensions, "default", false) && defaultFolder == null) {
                defaultFolder = playlist;
            } else {
                created.add(playlist);
            }
        }
        if (defaultFolder == null && !created.isEmpty()) {
            defaultFolder = created.removeFirst();
        }
        if (defaultFolder == null) {
            defaultFolder = Playlist.fromTuneWeave(
                    entities.stableId(TuneWeavePlatform.BILIBILI, "playlist:account:favorites"),
                    "account:favorites:bilibili", "Bilibili Favorites", MusicHud.ICON_BASE64,
                    0, 0, session.toMusicHudProfile());
            entities.cachePlaylist(defaultFolder);
        }
        favoritePlaylistReferences.put(TuneWeavePlatform.BILIBILI, defaultFolder.getSourceRef());
        return new UserCategoryPlaylists(defaultFolder, created, subscribed);
    }
}
