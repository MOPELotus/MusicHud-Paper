package indi.etern.musichud.client.services.tuneweave;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Playlist;
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
import java.util.List;
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
