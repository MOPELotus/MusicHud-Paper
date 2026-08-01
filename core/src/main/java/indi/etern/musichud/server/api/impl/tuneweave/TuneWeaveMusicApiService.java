package indi.etern.musichud.server.api.impl.tuneweave;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.Lyric;
import indi.etern.musichud.beans.music.LyricInfo;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.MusicResourceInfo;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.server.api.IMusicApiService;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Adapter from TuneWeave's string resource references to the legacy Music HUD
 * transport model.  Numeric IDs are opaque cache keys only; every upstream
 * request is made with the original {@code platform:id} reference.
 */
public final class TuneWeaveMusicApiService implements IMusicApiService {
    private static volatile TuneWeaveMusicApiService instance;
    private final Map<Long, MusicDetail> tracksById = new ConcurrentHashMap<>();
    private final Map<Long, Playlist> playlistsById = new ConcurrentHashMap<>();
    private final Map<Long, Album> albumsById = new ConcurrentHashMap<>();
    private final Map<Long, Artist> artistsById = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheIds = new ConcurrentHashMap<>();
    private final Map<Long, String> sourceRefs = new ConcurrentHashMap<>();
    /**
     * A Uni item is not interchangeable with its source reference: it carries
     * the account selection and ordered fallback policy chosen at import time.
     * Keep that indirection until the player asks for a stream at its selected
     * quality.
     */
    private final Map<Long, UniItemTarget> uniItemsByTrackId = new ConcurrentHashMap<>();

    private TuneWeaveMusicApiService() {
    }

    public static TuneWeaveMusicApiService getInstance() {
        if (instance == null) {
            synchronized (TuneWeaveMusicApiService.class) {
                if (instance == null) {
                    instance = new TuneWeaveMusicApiService();
                }
            }
        }
        return instance;
    }

    public MusicDetail getOrFetchPlayable(String reference, String kind) {
        String resolvedKind = "video".equals(kind) ? "video" : "track";
        long id = cacheId("track:" + reference);
        MusicDetail cached = tracksById.get(id);
        if (cached != null && resolvedKind.equals(cached.getSourceKind())) {
            return cached;
        }
        String path = "video".equals(resolvedKind) ? "/v1/videos/" : "/v1/tracks/";
        return toTrack(object(TuneWeaveApiClient.get(path + TuneWeaveApiClient.pathReference(reference))), resolvedKind);
    }

    public MusicDetail getOrFetchUniPlayable(String playlistReference, String itemId) {
        JsonObject item = findUniItem(playlistReference, itemId);
        String sourceRef = string(item, "source_ref", "");
        if (sourceRef.isBlank()) {
            throw new IllegalArgumentException("TuneWeave Uni item has no source reference");
        }
        JsonObject snapshot = object(item.get("snapshot"));
        String kind = "video".equals(string(item, "kind", "track")) ? "video" : "track";
        long id = cacheId("uni-item:" + playlistReference + ':' + itemId);
        List<Artist> artists = new ArrayList<>();
        for (String artistName : strings(snapshot.get("artists"))) {
            long artistId = cacheId("artist:" + sourceRef + ':' + artistName);
            artists.add(new Artist(artistId, artistName, "", 0, 0, "", new ArrayList<>(), 0));
        }
        String albumName = string(snapshot, "album", "");
        Album album = albumName.isBlank() ? Album.NONE : new Album(
                cacheId("album:" + sourceRef), albumName, string(snapshot, "cover_url", ""),
                new ArrayList<>(), artists, PusherInfo.EMPTY);
        MusicDetail detail = MusicDetail.fromTuneWeave(sourceRef, kind, id,
                string(snapshot, "title", sourceRef), artists, album,
                integer(snapshot, "duration_ms", 0), List.of());
        tracksById.put(id, detail);
        uniItemsByTrackId.put(id, new UniItemTarget(playlistReference, itemId));
        return detail;
    }

    /** Fetches a playable catalog collection without routing it through NCM IDs. */
    public List<MusicDetail> getCollectionTracks(String reference, String collectionKind) {
        String path = switch (collectionKind) {
            case "album" -> "/v1/albums/";
            case "artist" -> "/v1/artists/";
            case "playlist" -> "/v1/playlists/";
            default -> throw new IllegalArgumentException("Unsupported playable collection: " + collectionKind);
        };
        List<MusicDetail> result = new ArrayList<>();
        for (JsonElement element : items(TuneWeaveApiClient.get(
                path + TuneWeaveApiClient.pathReference(reference) + "/tracks",
                Map.of("limit", "100", "offset", "0")))) {
            JsonObject item = object(element);
            String type = string(item, "type", "track");
            result.add(toTrack(itemData(item), "video".equals(type) ? "video" : "track"));
        }
        return result;
    }

    @Override
    public Playlist getPlaylistDetail(long id, @Nullable UUID player) {
        Playlist cached = playlistsById.get(id);
        if (cached == null || cached.getSourceRef().isBlank()) {
            return cached == null ? Playlist.empty(id) : cached;
        }
        JsonObject playlistData = object(TuneWeaveApiClient.get("/v1/playlists/" + TuneWeaveApiClient.pathReference(cached.getSourceRef())));
        Playlist playlist = toPlaylist(playlistData);
        JsonElement tracksData = TuneWeaveApiClient.get(
                "/v1/playlists/" + TuneWeaveApiClient.pathReference(playlist.getSourceRef()) + "/tracks",
                Map.of("limit", "100", "offset", "0"));
        List<MusicDetail> tracks = new ArrayList<>();
        for (JsonElement element : items(tracksData)) {
            tracks.add(toTrack(itemData(element), "track"));
        }
        playlist.setTracks(tracks);
        playlistsById.put(playlist.getId(), playlist);
        return playlist;
    }

    @Override
    public List<Album> searchAlbums(String keywords, int offset) {
        return searchAlbums(keywords, offset, defaultSearchPlatform());
    }

    public List<Album> searchAlbums(String keywords, int offset, String platform) {
        List<Album> result = new ArrayList<>();
        for (JsonElement element : search(keywords, "album", offset, platform)) {
            result.add(toAlbum(itemData(element)));
        }
        return result;
    }

    @Override
    public List<Artist> searchArtists(String keywords, int offset) {
        return searchArtists(keywords, offset, defaultSearchPlatform());
    }

    public List<Artist> searchArtists(String keywords, int offset, String platform) {
        List<Artist> result = new ArrayList<>();
        for (JsonElement element : search(keywords, "artist", offset, platform)) {
            result.add(toArtist(itemData(element)));
        }
        return result;
    }

    @Override
    public List<MusicDetail> searchMusic(String keywords, int offset) {
        return searchMusic(keywords, offset, defaultSearchPlatform());
    }

    public List<MusicDetail> searchMusic(String keywords, int offset, String platform) {
        List<MusicDetail> result = new ArrayList<>();
        Set<String> references = new HashSet<>();
        for (JsonElement element : search(keywords, "track", offset, platform)) {
            JsonObject item = object(element);
            String type = string(item, "type", "track");
            if ("track".equals(type) || "video".equals(type)) {
                MusicDetail detail = toTrack(itemData(item), type);
                if (references.add(detail.getSourceRef())) {
                    result.add(detail);
                }
            }
        }
        // Bilibili and several other providers expose playable material as a
        // video resource.  It is intentionally presented as an audio result.
        try {
            for (JsonElement element : search(keywords, "video", offset, platform)) {
                JsonObject item = object(element);
                MusicDetail detail = toTrack(itemData(item), "video");
                if (!detail.getSourceRef().isBlank() && references.add(detail.getSourceRef())) {
                    result.add(detail);
                }
            }
        } catch (RuntimeException ignored) {
            // Video catalog search is optional for providers that only expose tracks.
        }
        return result;
    }

    @Override
    public List<Playlist> searchPlaylists(String keywords, int offset) {
        return searchPlaylists(keywords, offset, defaultSearchPlatform());
    }

    public List<Playlist> searchPlaylists(String keywords, int offset, String platform) {
        List<Playlist> result = new ArrayList<>();
        for (JsonElement element : search(keywords, "playlist", offset, platform)) {
            result.add(toPlaylist(itemData(element)));
        }
        return result;
    }

    @Override
    public <T> T search(String keywords, int offset, int limit, SearchType searchType, Function<String, T> transformer) {
        // This legacy extension point returned raw NCM JSON.  New code should use
        // the typed methods above; retain an empty JSON result for compatibility.
        return transformer.apply("[]");
    }

    @Override
    public List<MusicDetail> getMusicDetailByIds(List<Long> ids, UUID playerUUID) {
        List<MusicDetail> result = new ArrayList<>();
        for (Long id : ids) {
            MusicDetail detail = tracksById.get(id);
            if (detail != null) {
                result.add(detail);
            }
        }
        return result;
    }

    @Override
    public Album getAlbumInfoDetail(long id, UUID playerUUID) {
        Album cached = albumsById.get(id);
        if (cached == null) {
            return Album.NONE;
        }
        String ref = sourceRefs.get(id);
        if (ref == null) {
            return cached;
        }
        try {
            JsonObject data = object(TuneWeaveApiClient.get("/v1/albums/" + TuneWeaveApiClient.pathReference(ref)));
            Album album = toAlbum(data);
            for (JsonElement element : items(TuneWeaveApiClient.get("/v1/albums/" + TuneWeaveApiClient.pathReference(ref) + "/tracks", Map.of("limit", "100", "offset", "0")))) {
                album.getMusicDetails().add(toTrack(itemData(element), "track"));
            }
            return album;
        } catch (RuntimeException ignored) {
            return cached;
        }
    }

    @Override
    public Artist getArtistDetail(long id, UUID playerUUID) {
        Artist cached = artistsById.get(id);
        String reference = sourceRefs.get(id);
        if (reference == null || reference.isBlank()) {
            return cached == null ? new Artist() : cached;
        }
        try {
            return toArtist(object(TuneWeaveApiClient.get(
                    "/v1/artists/" + TuneWeaveApiClient.pathReference(reference))));
        } catch (RuntimeException ignored) {
            return cached == null ? new Artist() : cached;
        }
    }

    @Override
    public List<MusicDetail> getArtistMoreMusic(long id, int offset, UUID playerUUID) {
        String ref = sourceRefs.get(id);
        if (ref == null) {
            return List.of();
        }
        List<MusicDetail> result = new ArrayList<>();
        for (JsonElement element : items(TuneWeaveApiClient.get("/v1/artists/" + TuneWeaveApiClient.pathReference(ref) + "/tracks", Map.of("limit", "50", "offset", Integer.toString(offset))))) {
            result.add(toTrack(itemData(element), "track"));
        }
        return result;
    }

    @Override
    public MusicResourceInfo getResourceInfo(MusicDetail musicDetail, Quality quality, UUID playerUUID) {
        if (musicDetail == null || musicDetail.equals(MusicDetail.NONE) || musicDetail.getSourceRef().isBlank()) {
            return MusicResourceInfo.NONE;
        }
        UniItemTarget uniItem = uniItemsByTrackId.get(musicDetail.getId());
        if (uniItem != null) {
            // Server-owned Uni playlists are addressed through the generic
            // playlist stream route.  The /v1/uni namespace manages their
            // metadata and ordered items only.
            JsonObject stream = object(TuneWeaveApiClient.get(
                    "/v1/playlists/" + TuneWeaveApiClient.pathReference(uniItem.playlistReference)
                            + "/items/" + TuneWeaveApiClient.pathReference(uniItem.itemId) + "/stream",
                    streamQuery(quality)));
            return resourceInfo(musicDetail, stream);
        }
        String base = "video".equals(musicDetail.getSourceKind()) ? "/v1/videos/" : "/v1/tracks/";
        String suffix = "video".equals(musicDetail.getSourceKind()) ? "/audio-stream" : "/stream";
        JsonObject stream = object(TuneWeaveApiClient.get(
                base + TuneWeaveApiClient.pathReference(musicDetail.getSourceRef()) + suffix,
                streamQuery(quality)));
        return resourceInfo(musicDetail, stream);
    }

    private MusicResourceInfo resourceInfo(MusicDetail musicDetail, JsonObject stream) {
        String url = string(stream, "url", "");
        if (url.isBlank()) {
            return MusicResourceInfo.NONE;
        }
        return MusicResourceInfo.fromTuneWeave(
                musicDetail,
                url,
                integer(stream, "bitrate", 0),
                longValue(stream, "size", 0L),
                string(stream, "format", string(stream, "codec", "")),
                stringMap(stream.get("headers"))
        );
    }

    private JsonObject findUniItem(String playlistReference, String itemId) {
        for (int offset = 0; offset < 1_000; offset += 100) {
            List<JsonElement> items = items(TuneWeaveApiClient.get(
                    "/v1/uni/playlists/" + TuneWeaveApiClient.pathReference(playlistReference) + "/items",
                    Map.of("limit", "100", "offset", Integer.toString(offset))));
            for (JsonElement element : items) {
                JsonObject item = object(element);
                if (itemId.equals(string(item, "id", ""))) {
                    return item;
                }
            }
            if (items.size() < 100) {
                break;
            }
        }
        throw new IllegalArgumentException("TuneWeave Uni item was not found: " + itemId);
    }

    private static Map<String, String> streamQuery(Quality quality) {
        return Map.of(
                "quality", tuneWeaveQuality(quality),
                "fallback", "true",
                "fallback_platforms", "qq,netease,kugou,migu,kuwo,soda"
        );
    }

    @Override
    public List<Playlist> getPlayersUserSubscribedPlaylists(UUID playerUUID) {
        return List.of();
    }

    @Override
    public List<Album> getPlayersUserSubscribedAlbums(UUID playerUUID) {
        return List.of();
    }

    @Override
    public List<Artist> getPlayersUserSubscribedArtists(UUID playerUUID) {
        return List.of();
    }

    @Override
    public LyricInfo getLyricInfo(MusicDetail musicDetail) {
        if (musicDetail == null || musicDetail.getSourceRef().isBlank() || "video".equals(musicDetail.getSourceKind())) {
            return LyricInfo.NONE;
        }
        JsonObject lyrics = object(TuneWeaveApiClient.get(
                "/v1/tracks/" + TuneWeaveApiClient.pathReference(musicDetail.getSourceRef()) + "/lyrics",
                Map.of("word_synced", "true", "translated", "true", "romanized", "true")));
        return new LyricInfo(
                new Lyric(string(lyrics, "plain", "")),
                new Lyric(string(lyrics, "translated", "")),
                new Lyric(string(lyrics, "word_synced", "")),
                new Lyric(string(lyrics, "romanized", ""))
        );
    }

    private List<JsonElement> search(String keywords, String kind, int offset, String platform) {
        return items(TuneWeaveApiClient.get("/v1/search", Map.of(
                "q", keywords,
                "type", kind,
                "platform", platform == null || platform.isBlank() ? defaultSearchPlatform() : platform,
                "limit", "50",
                "offset", Integer.toString(Math.max(0, offset))
        )));
    }

    /**
     * TuneWeave deliberately rejects {@code platform=all} when more than one
     * provider is registered.  Legacy callers therefore use the configured
     * default while the new UI explicitly sends the player's chosen platform.
     */
    private String defaultSearchPlatform() {
        String firstRegistered = "";
        JsonElement response = TuneWeaveApiClient.get("/v1/platforms");
        if (response != null && response.isJsonArray()) {
            for (JsonElement element : response.getAsJsonArray()) {
                JsonObject platform = object(element);
                String id = string(platform, "platform", "");
                if (!id.isBlank() && !"uni".equals(id) && booleanValue(platform, "registered")) {
                    if (booleanValue(platform, "default")) {
                        return id;
                    }
                    if (firstRegistered.isBlank()) {
                        firstRegistered = id;
                    }
                }
            }
        }
        if (firstRegistered.isBlank()) {
            throw new IllegalStateException("TuneWeave has no registered music platform");
        }
        return firstRegistered;
    }

    private MusicDetail toTrack(JsonObject data, String kind) {
        String ref = string(data, "ref", "");
        long id = cacheId("track:" + ref);
        List<Artist> artists = new ArrayList<>();
        for (JsonElement element : array(data, "artists")) {
            artists.add(toArtist(object(element)));
        }
        JsonObject albumData = objectOrEmpty(data.get("album"));
        Album album = albumData.entrySet().isEmpty()
                ? Album.NONE
                : toAlbum(albumData);
        List<String> aliases = strings(data.get("aliases"));
        MusicDetail track = MusicDetail.fromTuneWeave(
                ref, kind, id, string(data, "name", string(data, "title", "")), artists, album,
                integer(data, "duration_ms", 0), aliases);
        tracksById.put(id, track);
        return track;
    }

    private Playlist toPlaylist(JsonObject data) {
        String ref = string(data, "ref", "");
        long id = cacheId("playlist:" + ref);
        Playlist playlist = Playlist.fromTuneWeave(id, ref, string(data, "name", ""),
                string(data, "cover_url", string(data, "cover_img_url", "")));
        playlistsById.put(id, playlist);
        return playlist;
    }

    private Album toAlbum(JsonObject data) {
        String ref = string(data, "ref", "");
        long id = cacheId("album:" + ref);
        List<Artist> artists = new ArrayList<>();
        for (JsonElement element : array(data, "artists")) {
            artists.add(toArtist(object(element)));
        }
        Album album = new Album(id, string(data, "name", ""), string(data, "cover_url", ""),
                new ArrayList<>(), artists, PusherInfo.EMPTY);
        albumsById.put(id, album);
        return album;
    }

    private Artist toArtist(JsonObject data) {
        String ref = string(data, "ref", "");
        long id = cacheId("artist:" + ref);
        Artist artist = new Artist(id, string(data, "name", ""), string(data, "avatar_url", ""),
                0, 0, string(data, "description", ""), new ArrayList<>(), 0);
        artistsById.put(id, artist);
        return artist;
    }

    private long cacheId(String key) {
        return cacheIds.computeIfAbsent(key, source -> {
            long candidate = stableId(source);
            String collisionKey = source;
            int collision = 0;
            while (sourceRefs.putIfAbsent(candidate, referenceOf(source)) != null
                    && !referenceOf(source).equals(sourceRefs.get(candidate))) {
                candidate = stableId(collisionKey + '#' + ++collision);
            }
            return candidate;
        });
    }

    private static String referenceOf(String cacheKey) {
        int separator = cacheKey.indexOf(':');
        return separator < 0 ? cacheKey : cacheKey.substring(separator + 1);
    }

    private static long stableId(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            long id = ByteBuffer.wrap(digest).getLong() & Long.MAX_VALUE;
            return id == 0L ? 1L : id;
        } catch (Exception e) {
            long id = Integer.toUnsignedLong(value.hashCode());
            return id == 0L ? 1L : id;
        }
    }

    private static String tuneWeaveQuality(Quality quality) {
        return switch (quality) {
            case LOSSLESS -> "lossless";
            case HIRES -> "hires";
            case DOLBY -> "dolby";
            case JY_MASTER -> "master";
            case JY_EFFECT, SKY -> "spatial";
            case EX_HIGH -> "high";
            case HIGHER -> "higher";
            case STANDARD, NONE -> "standard";
        };
    }

    private static JsonObject itemData(JsonElement item) {
        JsonObject object = object(item);
        return object.has("data") ? objectOrEmpty(object.get("data")) : object;
    }

    private static List<JsonElement> items(JsonElement data) {
        JsonArray array;
        if (data != null && data.isJsonArray()) {
            array = data.getAsJsonArray();
        } else {
            JsonObject object = objectOrEmpty(data);
            JsonElement candidates = object.has("items") ? object.get("items")
                    : object.has("tracks") ? object.get("tracks") : new JsonArray();
            array = candidates.isJsonArray() ? candidates.getAsJsonArray() : new JsonArray();
        }
        List<JsonElement> result = new ArrayList<>();
        array.forEach(result::add);
        return result;
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static JsonObject objectOrEmpty(JsonElement element) {
        return object(element);
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    private static boolean booleanValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        try {
            return object.has(key) ? object.get(key).getAsLong() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static List<String> strings(JsonElement element) {
        List<String> result = new ArrayList<>();
        if (element != null && element.isJsonArray()) {
            element.getAsJsonArray().forEach(value -> result.add(value.getAsString()));
        }
        return result;
    }

    private static Map<String, String> stringMap(JsonElement element) {
        Map<String, String> result = new LinkedHashMap<>();
        if (element != null && element.isJsonObject()) {
            element.getAsJsonObject().entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
        }
        return result;
    }

    private record UniItemTarget(String playlistReference, String itemId) {
    }
}
