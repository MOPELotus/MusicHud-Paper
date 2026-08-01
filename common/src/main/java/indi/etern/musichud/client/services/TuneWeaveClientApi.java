package indi.etern.musichud.client.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import indi.etern.musichud.server.api.impl.tuneweave.TuneWeaveEndpoint;
import indi.etern.musichud.utils.http.ApiClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Direct client-side TuneWeave client. Authentication is represented only by
 * repeated request headers, so caller credentials cannot cross the game
 * network or end up in an API query string.
 */
public final class TuneWeaveClientApi {
    private TuneWeaveClientApi() {
    }

    public static JsonElement request(String method, String path, Map<String, String> query, JsonElement payload) {
        try {
            StringBuilder uri = new StringBuilder(baseUrl()).append(path);
            Map<String, String> safeQuery = query == null ? Map.of() : new LinkedHashMap<>(query);
            if (!safeQuery.isEmpty()) {
                boolean first = true;
                uri.append('?');
                for (Map.Entry<String, String> entry : safeQuery.entrySet()) {
                    if (entry.getValue() == null) {
                        continue;
                    }
                    if (!first) {
                        uri.append('&');
                    }
                    first = false;
                    uri.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
                }
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri.toString()))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("User-Agent", "MusicHud/TuneWeave");
            // QR transactions establish a new caller credential and must not
            // accidentally inherit a previous credential for the same platform.
            if (!path.startsWith("/v1/auth/qr")) {
                for (String credential : TuneWeaveClientCredentials.all().values()) {
                    builder.header("X-TuneWeave-Credential", credential);
                }
            }
            if (payload == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8));
            }
            HttpResponse<String> response = ApiClient.CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonObject envelope = JsonParser.parseString(response.body()).getAsJsonObject();
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !envelope.has("ok") || !envelope.get("ok").getAsBoolean()) {
                throw new TuneWeaveClientException(message(envelope, response.statusCode()));
            }
            return envelope.has("data") ? envelope.get("data") : new JsonObject();
        } catch (TuneWeaveClientException e) {
            throw e;
        } catch (Exception e) {
            throw new TuneWeaveClientException("无法连接到 TuneWeave 服务（" + baseUrl() + "）", e);
        }
    }

    public static String pathReference(String reference) {
        return encode(reference).replace("+", "%20");
    }

    public static boolean isAvailable() {
        try {
            request("GET", "/healthz", Map.of(), null);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static String baseUrl() {
        return TuneWeaveEndpoint.clientBaseUrl();
    }

    private static String message(JsonObject envelope, int status) {
        if (envelope.has("error") && envelope.get("error").isJsonObject()) {
            JsonObject error = envelope.getAsJsonObject("error");
            if (error.has("message")) {
                return error.get("message").getAsString();
            }
        }
        return "TuneWeave 请求失败（HTTP " + status + "）";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static final class TuneWeaveClientException extends RuntimeException {
        public TuneWeaveClientException(String message) {
            super(message);
        }

        public TuneWeaveClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
