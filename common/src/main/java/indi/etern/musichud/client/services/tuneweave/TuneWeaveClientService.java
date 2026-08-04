package indi.etern.musichud.client.services.tuneweave;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.Fee;
import indi.etern.musichud.beans.music.FormatType;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.MusicResourceInfo;
import indi.etern.musichud.beans.music.UserCategoryPlaylists;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.VipType;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.server.api.tuneweave.TuneWeaveApiClient;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Client-owned TuneWeave authentication and credential-scoped requests. */
public final class TuneWeaveClientService {
    private static final TuneWeaveClientService INSTANCE = new TuneWeaveClientService();
    private final ClientConfig config = ClientConfig.getInstance();

    private TuneWeaveClientService() {
    }

    public static TuneWeaveClientService getInstance() {
        return INSTANCE;
    }

    public TuneWeavePlatform defaultPlatform() {
        return TuneWeavePlatform.fromApiName(config.getDefaultMusicPlatform());
    }

    public void setDefaultPlatform(TuneWeavePlatform platform) {
        config.setDefaultMusicPlatform(Objects.requireNonNull(platform).apiName());
        config.save();
    }

    public boolean isAvailable() {
        return TuneWeaveApiClient.isAvailableAt(config.getTuneWeaveBaseUrl());
    }

    public boolean hasCredential(TuneWeavePlatform platform) {
        return !credential(platform).isBlank();
    }

    public String credential(TuneWeavePlatform platform) {
        return config.getTuneWeaveCredential(Objects.requireNonNull(platform).apiName());
    }

    public QrSession startQrLogin(TuneWeavePlatform platform, String loginType) {
        JsonObject body = clientModeBody(platform);
        if (loginType != null && !loginType.isBlank()) {
            body.addProperty("login_type", loginType.trim());
        }
        JsonObject data = object(requestWithoutCredential("POST", "/v1/auth/qr", Map.of(), body).data());
        return new QrSession(
                platform,
                requiredString(data, "transaction_id"),
                string(data, "url"),
                string(data, "image_data_url"),
                string(data, "expires_at")
        );
    }

    public QrPoll pollQrLogin(QrSession session) {
        JsonObject data = object(requestWithoutCredential(
                "GET",
                "/v1/auth/qr/" + TuneWeaveApiClient.encodePathSegment(session.transactionId()),
                Map.of(), null).data());
        String state = requiredString(data, "state");
        SessionProfile profile = profile(data.get("profile"));
        if ("confirmed".equals(state)) {
            saveCredential(session.platform(), data.get("caller_credential"));
        }
        return new QrPoll(state, string(data, "message"), profile);
    }

    public SessionProfile loginWithPassword(TuneWeavePlatform platform, String principalType,
                                            String principal, String password, String passwordFormat,
                                            String countryCode) {
        JsonObject body = clientModeBody(platform);
        body.addProperty("principal_type", principalType);
        body.addProperty("principal", principal);
        body.addProperty("password", password);
        if (passwordFormat != null && !passwordFormat.isBlank()) {
            body.addProperty("password_format", passwordFormat);
        }
        if (countryCode != null && !countryCode.isBlank()) {
            body.addProperty("country_code", countryCode);
        }
        JsonObject data = object(requestWithoutCredential("POST", "/v1/auth/password", Map.of(), body).data());
        saveCredential(platform, data.get("caller_credential"));
        return profile(data.get("profile"));
    }

    public ChallengeSession startSmsLogin(TuneWeavePlatform platform, String principal, String countryCode) {
        JsonObject body = clientModeBody(platform);
        body.addProperty("method", "sms");
        body.addProperty("principal", principal);
        body.addProperty("country_code", countryCode == null || countryCode.isBlank() ? "86" : countryCode);
        JsonObject data = object(requestWithoutCredential(
                "POST", "/v1/auth/challenges", Map.of(), body).data());
        return new ChallengeSession(platform, requiredString(data, "transaction_id"));
    }

    public SessionProfile verifySmsLogin(ChallengeSession session, String code) {
        JsonObject body = new JsonObject();
        body.addProperty("code", code);
        JsonObject data = object(requestWithoutCredential(
                "POST",
                "/v1/auth/challenges/" + TuneWeaveApiClient.encodePathSegment(session.transactionId()) + "/verify",
                Map.of(), body).data());
        saveCredential(session.platform(), data.get("caller_credential"));
        return profile(data.get("profile"));
    }

    public SessionProfile loadSession(TuneWeavePlatform platform) {
        JsonElement data = requestForPlatform(
                platform, "GET", "/v1/auth/session", Map.of("platform", platform.apiName()), null).data();
        return profile(data);
    }

    public SessionProfile refreshSession(TuneWeavePlatform platform) {
        JsonObject body = clientModeBody(platform);
        JsonObject data = object(requestForPlatform(
                platform, "POST", "/v1/auth/session/refresh", Map.of(), body).data());
        saveCredential(platform, data.get("caller_credential"));
        return profile(data.get("profile"));
    }

    /** Loads the caller's account playlists without routing the private credential through Minecraft. */
    public UserCategoryPlaylists loadAccountPlaylists() {
        TuneWeavePlatform platform = defaultPlatform();
        JsonElement data = requestForPlatform(platform, "GET", "/v1/account/playlists",
                Map.of("platform", platform.apiName(), "limit", "100", "offset", "0"), null).data();
        ObservableSequencedSet<Playlist> created = new ObservableSequencedSet<>();
        ObservableSequencedSet<Playlist> subscribed = new ObservableSequencedSet<>();
        for (JsonElement item : elements(data)) {
            JsonObject raw = unwrap(item);
            Playlist playlist = toPlaylist(platform, raw);
            if (playlist == Playlist.EMPTY) {
                continue;
            }
            (bool(raw, "subscribed", false) ? subscribed : created).add(playlist);
        }
        String likeRef = "account:favorite_tracks:" + platform.apiName();
        Playlist liked = Playlist.fromTuneWeave(
                stableId(platform, "playlist:" + likeRef), likeRef, "Liked Songs", MusicHud.ICON_BASE64,
                0, 0, Profile.ANONYMOUS);
        return new UserCategoryPlaylists(liked, created, subscribed);
    }

    public List<UniPlaylistInfo> listUniPlaylists() {
        JsonElement data = requestWithAllCredentials("GET", "/v1/uni/playlists",
                Map.of("limit", "100", "offset", "0"), null).data();
        List<UniPlaylistInfo> result = new ArrayList<>();
        for (JsonElement item : elements(data)) {
            JsonObject object = unwrap(item);
            String reference = string(object, "ref", string(object, "resource_ref", ""));
            if (!reference.isBlank()) {
                result.add(new UniPlaylistInfo(reference, string(object, "name", reference),
                        string(object, "description", ""), integer(object, "item_count", 0)));
            }
        }
        return result;
    }

    public UniPlaylistInfo createUniPlaylist(String name, String description) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("description", description == null ? "" : description);
        return toUniPlaylist(requestWithAllCredentials("POST", "/v1/uni/playlists", Map.of(), body).data());
    }

    public UniPlaylistInfo updateUniPlaylist(String reference, String name, String description) {
        JsonObject body = new JsonObject();
        if (name != null) body.addProperty("name", name);
        if (description != null) body.addProperty("description", description);
        return toUniPlaylist(requestWithAllCredentials("PATCH", uniPath(reference), Map.of(), body).data());
    }

    public void deleteUniPlaylist(String reference) {
        requestWithAllCredentials("DELETE", uniPath(reference), Map.of(), null);
    }

    public List<UniItemInfo> listUniPlaylistItems(String reference) {
        JsonElement data = requestWithAllCredentials("GET", uniPath(reference) + "/items",
                Map.of("limit", "500", "offset", "0"), null).data();
        List<UniItemInfo> result = new ArrayList<>();
        for (JsonElement item : elements(data)) {
            JsonObject object = unwrap(item);
            JsonObject snapshot = object.has("snapshot") && object.get("snapshot").isJsonObject()
                    ? object.getAsJsonObject("snapshot") : new JsonObject();
            String itemRef = string(object, "ref", string(object, "source_ref", ""));
            if (itemRef.isBlank()) continue;
            List<String> artists = new ArrayList<>();
            JsonElement artistData = snapshot.get("artists");
            if (artistData != null && artistData.isJsonArray()) {
                artistData.getAsJsonArray().forEach(value -> artists.add(value.getAsString()));
            }
            result.add(new UniItemInfo(string(object, "id", ""), integer(object, "position", result.size()),
                    string(object, "kind", "track"), itemRef,
                    string(snapshot, "title", itemRef), artists));
        }
        return result;
    }

    public void addUniPlaylistItems(String reference, List<String> resourceRefs) {
        if (resourceRefs == null || resourceRefs.isEmpty()) return;
        JsonObject body = new JsonObject();
        JsonArray items = new JsonArray();
        resourceRefs.stream().filter(value -> value != null && !value.isBlank()).limit(100).forEach(value -> {
            JsonObject item = new JsonObject();
            item.addProperty("ref", value);
            item.addProperty("kind", "track");
            items.add(item);
        });
        body.add("items", items);
        body.add("accounts", new JsonObject());
        requestWithAllCredentials("POST", uniPath(reference) + "/items", Map.of(), body);
    }

    public void deleteUniPlaylistItem(String reference, String itemId) {
        requestWithAllCredentials("DELETE", uniPath(reference) + "/items/"
                + TuneWeaveApiClient.encodePathSegment(itemId), Map.of(), null);
    }

    public void reorderUniPlaylistItems(String reference, List<String> itemIds) {
        JsonObject body = new JsonObject();
        JsonArray ids = new JsonArray();
        itemIds.forEach(ids::add);
        body.add("item_ids", ids);
        requestWithAllCredentials("PATCH", uniPath(reference) + "/items/order", Map.of(), body);
    }

    public UniPlaylistInfo importUniPlaylist(String name, List<String> sourceRefs) {
        JsonObject body = new JsonObject();
        if (name != null && !name.isBlank()) body.addProperty("name", name);
        JsonArray sources = new JsonArray();
        sourceRefs.stream().filter(value -> value != null && !value.isBlank()).limit(50).forEach(value -> {
            JsonObject source = new JsonObject();
            source.addProperty("ref", value);
            source.addProperty("type", "playlist");
            sources.add(source);
        });
        body.add("sources", sources);
        return toUniPlaylist(requestWithAllCredentials("POST", "/v1/uni/playlists/imports", Map.of(), body).data());
    }

    public MusicResourceInfo getMusicResourceInfo(MusicDetail musicDetail,
                                                   indi.etern.musichud.beans.music.Quality quality) {
        if (musicDetail == null || musicDetail.getSourceRef().isBlank()) {
            return MusicResourceInfo.NONE;
        }
        String reference = musicDetail.getSourceRef();
        TuneWeavePlatform platform = TuneWeavePlatform.fromApiName(reference.contains(":")
                ? reference.substring(0, reference.indexOf(':')) : config.getDefaultMusicPlatform());
        String path = "video".equals(musicDetail.getSourceKind())
                ? "/v1/videos/" + TuneWeaveApiClient.encodePathSegment(reference) + "/audio-stream"
                : "/v1/tracks/" + TuneWeaveApiClient.encodePathSegment(reference) + "/stream";
        Map<String, String> query = new LinkedHashMap<>();
        query.put("quality", qualityName(quality));
        query.put("fallback", "true");
        query.put("fallback_platforms", "netease,qq,kugou,migu,kuwo,soda");
        JsonObject stream = unwrap(requestForPlatform(platform, "GET", path, query, null).data());
        String url = string(stream, "url", "");
        if (url.isBlank()) {
            return MusicResourceInfo.NONE;
        }
        return new MusicResourceInfo(musicDetail.getId(), url, integer(stream, "bitrate", 0),
                longValue(stream, "size", 0L), FormatType.fromSerializedName(
                string(stream, "format", string(stream, "codec", ""))), "", Fee.UNSET,
                integer(stream, "duration_ms", musicDetail.getDurationMillis()), stringMap(stream.get("headers")));
    }

    public List<?> search(String keywords, SearchType searchType, int offset, TuneWeavePlatform platform) {
        String type = switch (searchType) {
            case ALBUM -> "album";
            case ARTIST -> "artist";
            case PLAYLIST -> "playlist";
            default -> "track";
        };
        JsonElement data = requestForPlatform(platform, "GET", "/v1/search", Map.of(
                "q", keywords, "type", type, "platform", platform.apiName(),
                "limit", "50", "offset", Integer.toString(Math.max(0, offset))), null).data();
        List<JsonObject> rawItems = new ArrayList<>();
        for (JsonElement item : elements(data)) {
            JsonObject object = unwrap(item);
            if (!object.isEmpty()) rawItems.add(object);
        }
        return switch (searchType) {
            case ALBUM -> rawItems.stream().map(value -> toAlbum(platform, value)).toList();
            case ARTIST -> rawItems.stream().map(value -> toArtist(platform, value)).toList();
            case PLAYLIST -> rawItems.stream().map(value -> toPlaylist(platform, value)).toList();
            default -> rawItems.stream().map(value -> toTrack(platform, value)).toList();
        };
    }

    private static String uniPath(String reference) {
        return "/v1/uni/playlists/" + TuneWeaveApiClient.encodePathSegment(reference);
    }

    private static UniPlaylistInfo toUniPlaylist(JsonElement element) {
        JsonObject object = unwrap(element);
        String reference = string(object, "ref", string(object, "resource_ref", ""));
        return new UniPlaylistInfo(reference, string(object, "name", reference),
                string(object, "description", ""), integer(object, "item_count", 0));
    }

    public void logout(TuneWeavePlatform platform) {
        if (!hasCredential(platform)) {
            return;
        }
        requestForPlatform(platform, "DELETE", "/v1/auth/session",
                Map.of("platform", platform.apiName(), "credential_mode", "client"), null);
        clearCredential(platform);
    }

    public void clearCredential(TuneWeavePlatform platform) {
        config.clearTuneWeaveCredential(platform.apiName());
        config.save();
    }

    public TuneWeaveApiClient.TuneWeaveResponse requestForPlatform(
            TuneWeavePlatform platform, String method, String path, Map<String, String> query, JsonElement body) {
        String value = credential(platform);
        List<String> credentials = value.isBlank() ? List.of() : List.of(value);
        return request(method, path, query, body, credentials);
    }

    public TuneWeaveApiClient.TuneWeaveResponse requestWithAllCredentials(
            String method, String path, Map<String, String> query, JsonElement body) {
        List<String> credentials = new ArrayList<>(TuneWeavePlatform.values().length);
        for (TuneWeavePlatform platform : TuneWeavePlatform.values()) {
            String value = credential(platform);
            if (!value.isBlank()) {
                credentials.add(value);
            }
        }
        return request(method, path, query, body, credentials);
    }

    private TuneWeaveApiClient.TuneWeaveResponse requestWithoutCredential(
            String method, String path, Map<String, String> query, JsonElement body) {
        return request(method, path, query, body, List.of());
    }

    private TuneWeaveApiClient.TuneWeaveResponse request(
            String method, String path, Map<String, String> query, JsonElement body, List<String> credentials) {
        return TuneWeaveApiClient.requestAt(
                config.getTuneWeaveBaseUrl(), method, path, query, body, credentials);
    }

    private void saveCredential(TuneWeavePlatform expectedPlatform, JsonElement element) {
        JsonObject credential = object(element);
        String format = requiredString(credential, "format");
        String platform = requiredString(credential, "platform");
        String value = requiredString(credential, "value");
        if (!"tuneweave_credential_v1".equals(format)
                || TuneWeavePlatform.fromApiName(platform) != expectedPlatform
                || !value.startsWith("twc1_")) {
            throw new TuneWeaveApiClient.TuneWeaveException("TuneWeave returned an invalid caller credential", false);
        }
        config.setTuneWeaveCredential(expectedPlatform.apiName(), value);
        config.setDefaultMusicPlatform(expectedPlatform.apiName());
        config.save();
    }

    private static JsonObject clientModeBody(TuneWeavePlatform platform) {
        JsonObject body = new JsonObject();
        body.addProperty("platform", platform.apiName());
        body.addProperty("credential_mode", "client");
        return body;
    }

    private static SessionProfile profile(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        JsonObject value = object(element);
        return new SessionProfile(
                TuneWeavePlatform.fromApiName(requiredString(value, "platform")),
                string(value, "user_id"),
                string(value, "nickname"),
                string(value, "avatar_url"),
                value.has("authenticated") && value.get("authenticated").getAsBoolean()
        );
    }

    private static JsonObject object(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            throw new TuneWeaveApiClient.TuneWeaveException("TuneWeave response is missing an object", false);
        }
        return element.getAsJsonObject();
    }

    private static String requiredString(JsonObject object, String key) {
        String value = string(object, key);
        if (value == null || value.isBlank()) {
            throw new TuneWeaveApiClient.TuneWeaveException("TuneWeave response is missing " + key, false);
        }
        return value;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value instanceof JsonNull || value.isJsonNull() ? null : value.getAsString();
    }

    private static String string(JsonObject object, String key, String fallback) {
        String value = string(object, key);
        return value == null ? fallback : value;
    }

    private static long stableUserId(TuneWeavePlatform platform, String userId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (platform.apiName() + ':' + Objects.requireNonNullElse(userId, ""))
                            .getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong() & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Playlist toPlaylist(TuneWeavePlatform platform, JsonObject object) {
        String reference = string(object, "ref");
        if (reference == null || reference.isBlank()) {
            reference = string(object, "reference");
        }
        if (reference == null || reference.isBlank()) {
            return Playlist.EMPTY;
        }
        JsonObject creatorObject = object.has("creator") && object.get("creator").isJsonObject()
                ? object.getAsJsonObject("creator") : new JsonObject();
        String creatorRef = string(creatorObject, "ref");
        Profile creator = new Profile(
                string(creatorObject, "name", ""), "", stableId(platform, "user:" + creatorRef), VipType.NORMAL);
        return Playlist.fromTuneWeave(
                stableId(platform, "playlist:" + reference), reference,
                string(object, "name", reference), string(object, "cover_url", ""),
                integer(object, "track_count", integer(object, "item_count", 0)),
                integer(object, "play_count", 0), creator);
    }

    private static Album toAlbum(TuneWeavePlatform platform, JsonObject object) {
        String reference = string(object, "ref", string(object, "reference", ""));
        if (reference.isBlank()) return Album.NONE;
        java.util.LinkedHashSet<Artist> artists = new java.util.LinkedHashSet<>();
        JsonElement artistData = object.get("artists");
        if (artistData != null && artistData.isJsonArray()) {
            artistData.getAsJsonArray().forEach(value -> {
                if (value.isJsonObject()) artists.add(toArtist(platform, value.getAsJsonObject()));
            });
        }
        return new Album(stableId(platform, "album:" + reference), string(object, "name", reference),
                string(object, "cover_url", string(object, "pic_url", "")), string(object, "kind", ""),
                string(object, "company", ""), integer(object, "track_count", 0),
                new ObservableSequencedSet<>(), artists, indi.etern.musichud.beans.music.PusherInfo.EMPTY, reference);
    }

    private static Artist toArtist(TuneWeavePlatform platform, JsonObject object) {
        String reference = string(object, "ref", string(object, "reference", ""));
        String name = string(object, "name", reference);
        return new Artist(stableId(platform, "artist:" + (reference.isBlank() ? name : reference)), name,
                string(object, "avatar_url", string(object, "cover_url", "")),
                integer(object, "album_count", integer(object, "album_size", 0)),
                integer(object, "music_count", integer(object, "music_size", 0)),
                string(object, "description", ""), new ArrayList<>(),
                integer(object, "total_music_count", integer(object, "music_count", 0)), reference);
    }

    private static MusicDetail toTrack(TuneWeavePlatform platform, JsonObject object) {
        String reference = string(object, "ref", string(object, "reference", ""));
        if (reference.isBlank()) return MusicDetail.NONE;
        List<Artist> artists = new ArrayList<>();
        JsonElement artistData = object.get("artists");
        if (artistData != null && artistData.isJsonArray()) {
            artistData.getAsJsonArray().forEach(value -> {
                if (value.isJsonObject()) artists.add(toArtist(platform, value.getAsJsonObject()));
            });
        }
        Album album = Album.NONE;
        JsonElement albumData = object.get("album");
        if (albumData != null && albumData.isJsonObject()) album = toAlbum(platform, albumData.getAsJsonObject());
        MusicDetail result = MusicDetail.fromTuneWeave(stableId(platform, "track:" + reference), reference,
                "video".equals(string(object, "kind", "track")) ? "video" : "track",
                string(object, "name", string(object, "title", reference)),
                integer(object, "duration_ms", 0), album, artists);
        result.setPusherInfo(indi.etern.musichud.beans.music.PusherInfo.EMPTY);
        return result;
    }

    private static long stableId(TuneWeavePlatform platform, String value) {
        return stableUserId(platform, value);
    }

    private static List<JsonElement> elements(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return List.of();
        }
        if (value.isJsonArray()) {
            List<JsonElement> result = new ArrayList<>();
            value.getAsJsonArray().forEach(result::add);
            return result;
        }
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            for (String key : List.of("items", "playlists", "results")) {
                JsonElement nested = object.get(key);
                if (nested instanceof JsonArray) {
                    List<JsonElement> result = new ArrayList<>();
                    nested.getAsJsonArray().forEach(result::add);
                    return result;
                }
            }
        }
        return List.of();
    }

    private static JsonObject unwrap(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return new JsonObject();
        }
        JsonObject object = element.getAsJsonObject();
        return object.has("data") && object.get("data").isJsonObject()
                ? object.getAsJsonObject("data") : object;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsBoolean();
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsInt();
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsLong();
    }

    private static Map<String, String> stringMap(JsonElement element) {
        if (element == null || !element.isJsonObject()) return Map.of();
        Map<String, String> result = new java.util.LinkedHashMap<>();
        element.getAsJsonObject().entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonNull()) result.put(entry.getKey(), entry.getValue().getAsString());
        });
        return result;
    }

    private static String qualityName(indi.etern.musichud.beans.music.Quality quality) {
        if (quality == null) return "auto";
        return switch (quality) {
            case STANDARD -> "standard";
            case HIGHER -> "higher";
            case EX_HIGH -> "high";
            case LOSSLESS -> "lossless";
            case HIRES -> "hires";
            case JY_EFFECT, SKY -> "spatial";
            case DOLBY -> "dolby";
            case JY_MASTER -> "master";
            case NONE -> "auto";
        };
    }

    public record QrSession(TuneWeavePlatform platform, String transactionId, String url,
                            String imageDataUrl, String expiresAt) {
    }

    public record QrPoll(String state, String message, SessionProfile profile) {
        public boolean terminal() {
            return "confirmed".equals(state) || "expired".equals(state) || "failed".equals(state);
        }
    }

    public record ChallengeSession(TuneWeavePlatform platform, String transactionId) {
    }

    public record UniPlaylistInfo(String reference, String name, String description, int itemCount) {
    }

    public record UniItemInfo(String id, int position, String kind, String sourceRef,
                              String title, List<String> artists) {
        public UniItemInfo {
            artists = artists == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(artists));
        }
    }

    public record SessionProfile(TuneWeavePlatform platform, String userId, String nickname,
                                 String avatarUrl, boolean authenticated) {
        public Profile toMusicHudProfile() {
            return new Profile(
                    nickname == null || nickname.isBlank() ? platform.apiName() : nickname,
                    avatarUrl == null ? "" : avatarUrl,
                    stableUserId(platform, userId),
                    VipType.NORMAL
            );
        }
    }
}
