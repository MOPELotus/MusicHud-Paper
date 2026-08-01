package indi.etern.musichud.network.payloads.requestResponseCycle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.api.impl.tuneweave.TuneWeaveApiClient;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import indi.etern.musichud.server.api.impl.tuneweave.TuneWeaveMusicApiService;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;

import java.util.LinkedHashMap;
import java.util.Map;

/** A deliberately small allow-list for UI operations backed by TuneWeave. */
public record TuneWeaveControlRequest(String requestId, String action, String payload) implements C2SPayload {
    public static final ByteBufCodec<TuneWeaveControlRequest> CODEC = ByteBufCodec.composite(
            Codecs.STRING_UTF8, TuneWeaveControlRequest::requestId,
            Codecs.STRING_UTF8, TuneWeaveControlRequest::action,
            Codecs.STRING_UTF8, TuneWeaveControlRequest::payload,
            TuneWeaveControlRequest::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(TuneWeaveControlRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((request, player) -> {
                        try {
                            String response = dispatch(request.action, request.payload, PusherInfo.ofPlayer(player));
                            IServerNetworkService.getInstance().sendToPlayer(player,
                                    new TuneWeaveControlResponse(request.requestId, request.action, true, response, ""));
                        } catch (RuntimeException e) {
                            IServerNetworkService.getInstance().sendToPlayer(player,
                                    new TuneWeaveControlResponse(request.requestId, request.action, false, "{}", e.getMessage()));
                        }
                    }));
        }
    }

    private static String dispatch(String action, String payload, PusherInfo pusherInfo) {
        JsonObject body = object(payload);
        return switch (action) {
            case "platforms" -> TuneWeaveApiClient.get("/v1/platforms").toString();
            case "capabilities" -> TuneWeaveApiClient.get("/v1/capabilities", optionalPlatformQuery(body)).toString();
            case "account-profile" -> TuneWeaveApiClient.get("/v1/account/profile", accountQuery(body)).toString();
            case "account-playlists" -> TuneWeaveApiClient.get("/v1/account/playlists", pagedAccountQuery(body)).toString();
            case "favorite-tracks" -> TuneWeaveApiClient.get("/v1/account/favorites/tracks", pagedAccountQuery(body)).toString();
            case "account-albums" -> TuneWeaveApiClient.get("/v1/account/library/albums", pagedAccountQuery(body)).toString();
            case "account-artists" -> TuneWeaveApiClient.get("/v1/account/following/artists", pagedAccountQuery(body)).toString();
            case "account-history" -> TuneWeaveApiClient.get("/v1/account/history", historyAccountQuery(body)).toString();
            case "recommendation-tracks" -> TuneWeaveApiClient.get("/v1/recommendations/tracks", recommendationQuery(body)).toString();
            case "recommendation-playlists" -> TuneWeaveApiClient.get("/v1/recommendations/playlists", recommendationQuery(body)).toString();
            case "favorite-track-add" -> TuneWeaveApiClient.put(
                    "/v1/account/favorites/tracks/" + TuneWeaveApiClient.pathReference(required(body, "ref")),
                    mutationAccountQuery(body)).toString();
            case "favorite-track-remove" -> TuneWeaveApiClient.delete(
                    "/v1/account/favorites/tracks/" + TuneWeaveApiClient.pathReference(required(body, "ref")),
                    mutationAccountQuery(body)).toString();
            case "qr-start" -> TuneWeaveApiClient.post("/v1/auth/qr", qrBody(body)).toString();
            case "qr-poll" -> TuneWeaveApiClient.get("/v1/auth/qr/" + TuneWeaveApiClient.pathReference(required(body, "transaction_id"))).toString();
            case "auth-logout" -> TuneWeaveApiClient.delete("/v1/auth/session", accountQuery(body)).toString();
            case "uni-list" -> TuneWeaveApiClient.get("/v1/uni/playlists", pageQuery(body)).toString();
            case "uni-create" -> TuneWeaveApiClient.post("/v1/uni/playlists", createPlaylistBody(body)).toString();
            case "uni-update" -> TuneWeaveApiClient.patch(
                    "/v1/uni/playlists/" + TuneWeaveApiClient.pathReference(required(body, "ref")),
                    updatePlaylistBody(body)).toString();
            case "uni-delete" -> TuneWeaveApiClient.delete(
                    "/v1/uni/playlists/" + TuneWeaveApiClient.pathReference(required(body, "ref"))).toString();
            case "uni-items" -> TuneWeaveApiClient.get(
                    "/v1/uni/playlists/" + TuneWeaveApiClient.pathReference(required(body, "ref")) + "/items",
                    pageQuery(body)).toString();
            case "uni-add" -> TuneWeaveApiClient.post(
                    "/v1/uni/playlists/" + TuneWeaveApiClient.pathReference(required(body, "ref")) + "/items",
                    requiredObject(body, "body")).toString();
            case "uni-remove" -> TuneWeaveApiClient.delete(
                    "/v1/uni/playlists/" + TuneWeaveApiClient.pathReference(required(body, "ref"))
                            + "/items/" + TuneWeaveApiClient.pathReference(required(body, "item_id"))).toString();
            case "uni-order" -> TuneWeaveApiClient.patch(
                    "/v1/uni/playlists/" + TuneWeaveApiClient.pathReference(required(body, "ref")) + "/items/order",
                    requiredObject(body, "body")).toString();
            case "uni-import" -> TuneWeaveApiClient.post("/v1/uni/playlists/imports", body).toString();
            case "queue-ref" -> queueReference(body, pusherInfo);
            case "queue-uni-item" -> queueUniItem(body, pusherInfo);
            case "queue-collection" -> queueCollection(body, pusherInfo);
            default -> throw new IllegalArgumentException("Unsupported TuneWeave UI action: " + action);
        };
    }

    private static String queueReference(JsonObject body, PusherInfo pusherInfo) {
        String reference = required(body, "ref");
        String kind = body.has("kind") ? body.get("kind").getAsString() : "track";
        MusicDetail detail = TuneWeaveMusicApiService.getInstance().getOrFetchPlayable(reference, kind);
        MusicPlayerServerService.getInstance().pushMusicToQueue(detail.getId(), pusherInfo);
        return "{}";
    }

    private static String queueUniItem(JsonObject body, PusherInfo pusherInfo) {
        MusicDetail detail = TuneWeaveMusicApiService.getInstance().getOrFetchUniPlayable(
                required(body, "ref"), required(body, "item_id"));
        MusicPlayerServerService.getInstance().pushMusicToQueue(detail.getId(), pusherInfo);
        return "{}";
    }

    private static String queueCollection(JsonObject body, PusherInfo pusherInfo) {
        int count = 0;
        for (MusicDetail detail : TuneWeaveMusicApiService.getInstance().getCollectionTracks(
                required(body, "ref"), required(body, "collection"))) {
            MusicPlayerServerService.getInstance().pushMusicToQueue(detail.getId(), pusherInfo);
            count++;
        }
        JsonObject result = new JsonObject();
        result.addProperty("queued", count);
        return result.toString();
    }

    private static JsonObject qrBody(JsonObject source) {
        JsonObject result = new JsonObject();
        result.addProperty("platform", required(source, "platform"));
        if (source.has("account")) {
            result.addProperty("account", source.get("account").getAsString());
        }
        if (source.has("login_type")) {
            result.addProperty("login_type", source.get("login_type").getAsString());
        }
        // Credentials stay in TuneWeave's server store and are never returned to Minecraft clients.
        result.addProperty("credential_mode", "server");
        return result;
    }

    private static JsonObject createPlaylistBody(JsonObject source) {
        JsonObject result = new JsonObject();
        result.addProperty("name", required(source, "name"));
        if (source.has("description")) {
            result.addProperty("description", source.get("description").getAsString());
        }
        return result;
    }

    private static JsonObject updatePlaylistBody(JsonObject source) {
        JsonObject result = new JsonObject();
        if (source.has("name")) {
            result.addProperty("name", required(source, "name"));
        }
        if (source.has("description")) {
            result.addProperty("description", source.get("description").getAsString());
        }
        if (result.entrySet().isEmpty()) {
            throw new IllegalArgumentException("A Uni playlist update requires name or description");
        }
        return result;
    }

    private static Map<String, String> accountQuery(JsonObject source) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("platform", required(source, "platform"));
        if (source.has("account")) {
            result.put("account", source.get("account").getAsString());
        }
        return result;
    }

    private static Map<String, String> pagedAccountQuery(JsonObject source) {
        Map<String, String> result = accountQuery(source);
        result.putAll(pageQuery(source));
        return result;
    }

    private static Map<String, String> historyAccountQuery(JsonObject source) {
        Map<String, String> result = pagedAccountQuery(source);
        copyQuery(source, result, "period");
        return result;
    }

    private static Map<String, String> recommendationQuery(JsonObject source) {
        Map<String, String> result = pagedAccountQuery(source);
        copyQuery(source, result, "refresh");
        return result;
    }

    private static Map<String, String> mutationAccountQuery(JsonObject source) {
        Map<String, String> result = new LinkedHashMap<>();
        if (source.has("account")) {
            result.put("account", source.get("account").getAsString());
        }
        return result;
    }

    private static Map<String, String> optionalPlatformQuery(JsonObject source) {
        Map<String, String> result = new LinkedHashMap<>();
        copyQuery(source, result, "platform");
        return result;
    }

    private static Map<String, String> pageQuery(JsonObject source) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("limit", source.has("limit") ? source.get("limit").getAsString() : "100");
        result.put("offset", source.has("offset") ? source.get("offset").getAsString() : "0");
        return result;
    }

    private static void copyQuery(JsonObject source, Map<String, String> target, String key) {
        if (source.has(key) && !source.get(key).isJsonNull() && !source.get(key).getAsString().isBlank()) {
            target.put(key, source.get(key).getAsString());
        }
    }

    private static String required(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull() || object.get(key).getAsString().isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return object.get(key).getAsString();
    }

    private static JsonObject requiredObject(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonObject()) {
            throw new IllegalArgumentException("Missing required object: " + key);
        }
        return object.getAsJsonObject(key);
    }

    private static JsonObject object(String value) {
        try {
            JsonElement parsed = JsonParser.parseString(value == null || value.isBlank() ? "{}" : value);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid TuneWeave UI payload");
        }
    }
}
