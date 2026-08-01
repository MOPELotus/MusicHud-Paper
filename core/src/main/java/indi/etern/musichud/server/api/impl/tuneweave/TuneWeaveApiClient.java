package indi.etern.musichud.server.api.impl.tuneweave;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import indi.etern.musichud.utils.http.ApiClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small, strict client for TuneWeave's {@code /v1} JSON envelope. */
public final class TuneWeaveApiClient {
    private TuneWeaveApiClient() {
    }

    public static boolean isAvailable() {
        try {
            request("GET", "/healthz", Map.of(), null);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static JsonElement get(String path) {
        return get(path, Map.of());
    }

    public static JsonElement get(String path, Map<String, String> query) {
        return request("GET", path, query, null);
    }

    public static JsonElement post(String path, JsonElement payload) {
        return request("POST", path, Map.of(), payload);
    }

    public static JsonElement patch(String path, JsonElement payload) {
        return request("PATCH", path, Map.of(), payload);
    }

    public static JsonElement put(String path) {
        return put(path, Map.of(), null);
    }

    public static JsonElement put(String path, Map<String, String> query) {
        return put(path, query, null);
    }

    public static JsonElement put(String path, Map<String, String> query, JsonElement payload) {
        return request("PUT", path, query, payload);
    }

    public static JsonElement delete(String path) {
        return request("DELETE", path, Map.of(), null);
    }

    public static JsonElement delete(String path, Map<String, String> query) {
        return request("DELETE", path, query, null);
    }

    public static String pathReference(String reference) {
        return encode(reference).replace("+", "%20");
    }

    public static String baseUrl() {
        return TuneWeaveEndpoint.serverBaseUrl();
    }

    private static JsonElement request(String method, String path, Map<String, String> query, JsonElement payload) {
        try {
            Map<String, String> safeQuery = query == null ? Map.of() : new LinkedHashMap<>(query);
            StringBuilder uri = new StringBuilder(baseUrl()).append(path);
            if (!safeQuery.isEmpty()) {
                uri.append('?');
                boolean first = true;
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
            if (payload != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = ApiClient.CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonObject envelope = JsonParser.parseString(response.body()).getAsJsonObject();
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !envelope.has("ok") || !envelope.get("ok").getAsBoolean()) {
                String message = "TuneWeave request failed (HTTP " + response.statusCode() + ')';
                if (envelope.has("error") && envelope.get("error").isJsonObject()) {
                    JsonObject error = envelope.getAsJsonObject("error");
                    if (error.has("message")) {
                        message = error.get("message").getAsString();
                    }
                }
                throw new TuneWeaveApiException(message);
            }
            return envelope.has("data") ? envelope.get("data") : new JsonObject();
        } catch (TuneWeaveApiException e) {
            throw e;
        } catch (Exception e) {
            throw new TuneWeaveApiException("Cannot reach TuneWeave at " + baseUrl(), e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static final class TuneWeaveApiException extends RuntimeException {
        public TuneWeaveApiException(String message) {
            super(message);
        }

        public TuneWeaveApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
