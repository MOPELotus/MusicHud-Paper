package indi.etern.musichud.client.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.payloads.requestResponseCycle.TuneWeaveControlRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.TuneWeaveControlResponse;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Serializes UI requests and keeps the packet layer free of client UI classes. */
public final class TuneWeaveUiService {
    private static final Map<String, Consumer<TuneWeaveControlResponse>> PENDING = new ConcurrentHashMap<>();
    private static final Set<String> SERVER_QUEUE_ACTIONS = Set.of("queue-ref", "queue-uni-item", "queue-collection");

    static {
        TuneWeaveControlResponse.setReceiver(response -> {
            Consumer<TuneWeaveControlResponse> callback = PENDING.remove(response.requestId());
            if (callback != null) {
                MuiModApi.postToUiThread(() -> callback.accept(response));
            }
        });
    }

    private TuneWeaveUiService() {
    }

    public static void request(String action, JsonObject payload, Consumer<JsonElement> success, Consumer<String> failure) {
        if (!SERVER_QUEUE_ACTIONS.contains(action)) {
            requestFromClient(action, payload, success, failure);
            return;
        }
        String requestId = UUID.randomUUID().toString();
        PENDING.put(requestId, response -> {
            if (response.ok()) {
                try {
                    success.accept(JsonParser.parseString(response.payload()));
                } catch (Exception e) {
                    failure.accept("TuneWeave 返回了无效数据");
                }
            } else {
                failure.accept(response.message().isBlank() ? "TuneWeave 请求失败" : response.message());
            }
        });
        IClientNetworkService.getInstance().sendToServer(new TuneWeaveControlRequest(requestId, action, payload.toString()));
        MusicHud.EXECUTOR.execute(() -> {
            try {
                Thread.sleep(25_000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            Consumer<TuneWeaveControlResponse> callback = PENDING.remove(requestId);
            if (callback != null) {
                MuiModApi.postToUiThread(() -> failure.accept("TuneWeave 请求超时"));
            }
        });
    }

    private static void requestFromClient(String action, JsonObject payload, Consumer<JsonElement> success, Consumer<String> failure) {
        MusicHud.EXECUTOR.execute(() -> {
            try {
                JsonElement result = dispatchClient(action, payload == null ? new JsonObject() : payload);
                MuiModApi.postToUiThread(() -> success.accept(result));
            } catch (RuntimeException e) {
                String message = e.getMessage();
                MuiModApi.postToUiThread(() -> failure.accept(message == null || message.isBlank() ? "TuneWeave 请求失败" : message));
            }
        });
    }

    private static JsonElement dispatchClient(String action, JsonObject body) {
        return switch (action) {
            case "platforms" -> TuneWeaveClientApi.request("GET", "/v1/platforms", Map.of(), null);
            case "capabilities" -> TuneWeaveClientApi.request("GET", "/v1/capabilities", optionalPlatformQuery(body), null);
            case "account-profile" -> TuneWeaveClientApi.request("GET", "/v1/account/profile", accountQuery(body), null);
            case "account-playlists" -> TuneWeaveClientApi.request("GET", "/v1/account/playlists", pagedAccountQuery(body), null);
            case "favorite-tracks" -> TuneWeaveClientApi.request("GET", "/v1/account/favorites/tracks", pagedAccountQuery(body), null);
            case "account-albums" -> TuneWeaveClientApi.request("GET", "/v1/account/library/albums", pagedAccountQuery(body), null);
            case "account-artists" -> TuneWeaveClientApi.request("GET", "/v1/account/following/artists", pagedAccountQuery(body), null);
            case "account-history" -> TuneWeaveClientApi.request("GET", "/v1/account/history", historyAccountQuery(body), null);
            case "recommendation-tracks" -> TuneWeaveClientApi.request("GET", "/v1/recommendations/tracks", recommendationQuery(body), null);
            case "recommendation-playlists" -> TuneWeaveClientApi.request("GET", "/v1/recommendations/playlists", recommendationQuery(body), null);
            case "favorite-track-add" -> TuneWeaveClientApi.request("PUT", "/v1/account/favorites/tracks/" + TuneWeaveClientApi.pathReference(required(body, "ref")), Map.of(), null);
            case "favorite-track-remove" -> TuneWeaveClientApi.request("DELETE", "/v1/account/favorites/tracks/" + TuneWeaveClientApi.pathReference(required(body, "ref")), Map.of(), null);
            case "qr-start" -> TuneWeaveClientApi.request("POST", "/v1/auth/qr", Map.of(), qrBody(body));
            case "qr-poll" -> TuneWeaveClientApi.request("GET", "/v1/auth/qr/" + TuneWeaveClientApi.pathReference(required(body, "transaction_id")), Map.of(), null);
            case "auth-logout" -> TuneWeaveClientApi.request("DELETE", "/v1/auth/session", accountQuery(body), null);
            case "uni-list" -> TuneWeaveClientApi.request("GET", "/v1/uni/playlists", pageQuery(body), null);
            case "uni-create" -> TuneWeaveClientApi.request("POST", "/v1/uni/playlists", Map.of(), createPlaylistBody(body));
            case "uni-update" -> TuneWeaveClientApi.request("PATCH", "/v1/uni/playlists/" + TuneWeaveClientApi.pathReference(required(body, "ref")), Map.of(), updatePlaylistBody(body));
            case "uni-delete" -> TuneWeaveClientApi.request("DELETE", "/v1/uni/playlists/" + TuneWeaveClientApi.pathReference(required(body, "ref")), Map.of(), null);
            case "uni-items" -> TuneWeaveClientApi.request("GET", "/v1/uni/playlists/" + TuneWeaveClientApi.pathReference(required(body, "ref")) + "/items", pageQuery(body), null);
            case "uni-add" -> TuneWeaveClientApi.request("POST", "/v1/uni/playlists/" + TuneWeaveClientApi.pathReference(required(body, "ref")) + "/items", Map.of(), requiredObject(body, "body"));
            case "uni-remove" -> TuneWeaveClientApi.request("DELETE", "/v1/uni/playlists/" + TuneWeaveClientApi.pathReference(required(body, "ref")) + "/items/" + TuneWeaveClientApi.pathReference(required(body, "item_id")), Map.of(), null);
            case "uni-order" -> TuneWeaveClientApi.request("PATCH", "/v1/uni/playlists/" + TuneWeaveClientApi.pathReference(required(body, "ref")) + "/items/order", Map.of(), requiredObject(body, "body"));
            case "uni-import" -> TuneWeaveClientApi.request("POST", "/v1/uni/playlists/imports", Map.of(), body);
            default -> throw new IllegalArgumentException("不支持的 TuneWeave 客户端操作：" + action);
        };
    }

    private static JsonObject qrBody(JsonObject source) {
        JsonObject result = new JsonObject();
        result.addProperty("platform", required(source, "platform"));
        if (source.has("login_type")) result.addProperty("login_type", source.get("login_type").getAsString());
        result.addProperty("credential_mode", "client");
        return result;
    }

    private static JsonObject createPlaylistBody(JsonObject source) {
        JsonObject result = new JsonObject();
        result.addProperty("name", required(source, "name"));
        if (source.has("description")) result.addProperty("description", source.get("description").getAsString());
        return result;
    }

    private static JsonObject updatePlaylistBody(JsonObject source) {
        JsonObject result = new JsonObject();
        if (source.has("name")) result.addProperty("name", required(source, "name"));
        if (source.has("description")) result.addProperty("description", source.get("description").getAsString());
        if (result.entrySet().isEmpty()) throw new IllegalArgumentException("更新聚合歌单时必须填写名称或说明");
        return result;
    }

    private static Map<String, String> accountQuery(JsonObject source) {
        return Map.of("platform", required(source, "platform"));
    }

    private static Map<String, String> pagedAccountQuery(JsonObject source) {
        Map<String, String> result = new java.util.LinkedHashMap<>(accountQuery(source));
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

    private static Map<String, String> optionalPlatformQuery(JsonObject source) {
        return source.has("platform") && !source.get("platform").getAsString().isBlank()
                ? Map.of("platform", source.get("platform").getAsString()) : Map.of();
    }

    private static Map<String, String> pageQuery(JsonObject source) {
        return Map.of("limit", source.has("limit") ? source.get("limit").getAsString() : "100",
                "offset", source.has("offset") ? source.get("offset").getAsString() : "0");
    }

    private static void copyQuery(JsonObject source, Map<String, String> target, String key) {
        if (source.has(key) && !source.get(key).isJsonNull() && !source.get(key).getAsString().isBlank()) {
            target.put(key, source.get(key).getAsString());
        }
    }

    private static String required(JsonObject source, String key) {
        if (!source.has(key) || source.get(key).isJsonNull() || source.get(key).getAsString().isBlank()) {
            throw new IllegalArgumentException("缺少字段：" + key);
        }
        return source.get(key).getAsString();
    }

    private static JsonObject requiredObject(JsonObject source, String key) {
        if (!source.has(key) || !source.get(key).isJsonObject()) throw new IllegalArgumentException("缺少对象字段：" + key);
        return source.getAsJsonObject(key);
    }
}
