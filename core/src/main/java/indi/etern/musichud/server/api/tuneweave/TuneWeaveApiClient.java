package indi.etern.musichud.server.api.tuneweave;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.ServerConfig;
import indi.etern.musichud.utils.JsonUtil;
import lombok.Getter;
import org.apache.logging.log4j.Logger;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

/** Strict JSON client for TuneWeave's v1 response envelope. */
public final class TuneWeaveApiClient {
    private static final Logger LOGGER = MusicHud.getLogger(TuneWeaveApiClient.class);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    private TuneWeaveApiClient() {
    }

    public static boolean isAvailable() {
        return isAvailableAt(baseUrl());
    }

    public static boolean isAvailableAt(String baseUrl) {
        try {
            requestAt(baseUrl, "GET", "/healthz", Map.of(), null, java.util.List.of());
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static JsonElement get(String path, Map<String, String> query) {
        return request("GET", path, query, null).data();
    }

    public static JsonElement post(String path, Map<String, String> query, JsonElement body) {
        return request("POST", path, query, body).data();
    }

    public static JsonElement put(String path, Map<String, String> query, JsonElement body) {
        return request("PUT", path, query, body).data();
    }

    public static JsonElement patch(String path, Map<String, String> query, JsonElement body) {
        return request("PATCH", path, query, body).data();
    }

    public static JsonElement delete(String path, Map<String, String> query, JsonElement body) {
        return request("DELETE", path, query, body).data();
    }

    public static TuneWeaveResponse request(String method, String path, Map<String, String> query, JsonElement body) {
        return requestAt(baseUrl(), method, path, query, body, java.util.List.of());
    }

    /**
     * Executes a request against an explicit TuneWeave instance. Caller credentials are
     * request headers only; they are never included in URLs, response errors, or logs.
     */
    public static TuneWeaveResponse requestAt(String baseUrl, String method, String path,
                                              Map<String, String> query, JsonElement body,
                                              Collection<String> callerCredentials) {
        if (!TuneWeaveRoutePolicy.isAllowed(method, path)) {
            throw new TuneWeaveException("TuneWeave route is not allowed: " + method + ' ' + path, false);
        }
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String uri = buildUri(normalizedBaseUrl, TuneWeaveRoutePolicy.normalizePath(path), query);
        String requestId = "mh-" + UUID.randomUUID();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(25))
                .header("Accept", "application/json")
                .header("User-Agent", "MusicHud-TuneWeave/1")
                .header("X-Request-ID", requestId);
        if (callerCredentials != null) {
            callerCredentials.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .limit(8)
                    .forEach(value -> builder.header("X-TuneWeave-Credential", value));
        }
        if (body == null || body.isJsonNull()) {
            builder.method(method.toUpperCase(), HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method.toUpperCase(), HttpRequest.BodyPublishers.ofString(
                            body.toString(), StandardCharsets.UTF_8));
        }
        int attempts = "GET".equalsIgnoreCase(method) ? 3 : 1;
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpResponse<String> response = CLIENT.send(
                        builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                return parseResponse(response.statusCode(), response.body());
            } catch (TuneWeaveException e) {
                last = e;
                if (!e.isRetryable() || attempt == attempts) {
                    throw e;
                }
            } catch (HttpTimeoutException | ConnectException e) {
                last = new TuneWeaveException("TuneWeave is unavailable", e, true);
            } catch (Exception e) {
                last = new TuneWeaveException("TuneWeave request failed: " + method + ' ' + path, e, false);
            }
            try {
                Thread.sleep(250L * attempt);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new TuneWeaveException("Interrupted while retrying TuneWeave", interrupted, false);
            }
        }
        throw last == null ? new TuneWeaveException("TuneWeave request failed", false) : last;
    }

    public static String baseUrl() {
        try {
            return normalizeBaseUrl(ServerConfig.getInstance().getServerApiBaseUrl());
        } catch (Throwable ignored) {
            return "http://127.0.0.1:7832";
        }
    }

    public static String normalizeBaseUrl(String value) {
        String base = value == null || value.isBlank() ? "http://127.0.0.1:7832" : value.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(base);
        } catch (IllegalArgumentException error) {
            throw new TuneWeaveException("TuneWeave base URL is invalid", error, false);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new TuneWeaveException("TuneWeave base URL must be an HTTP(S) origin", false);
        }
        return base;
    }

    public static String encodePathSegment(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String buildUri(String base, String path, Map<String, String> query) {
        StringBuilder result = new StringBuilder(base).append(path);
        boolean first = true;
        if (query != null) {
            for (Map.Entry<String, String> entry : new LinkedHashMap<>(query).entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                    continue;
                }
                result.append(first ? '?' : '&');
                first = false;
                result.append(encodePathSegment(entry.getKey())).append('=').append(encodePathSegment(entry.getValue()));
            }
        }
        return result.toString();
    }

    private static TuneWeaveResponse parseResponse(int status, String rawBody) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(rawBody == null || rawBody.isBlank() ? "{}" : rawBody);
        } catch (RuntimeException e) {
            throw new TuneWeaveException("TuneWeave returned invalid JSON (HTTP " + status + ')', e, false);
        }
        JsonObject envelope = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        boolean ok = status >= 200 && status < 300 && envelope.has("ok") && envelope.get("ok").getAsBoolean();
        if (!ok) {
            JsonObject error = envelope.has("error") && envelope.get("error").isJsonObject()
                    ? envelope.getAsJsonObject("error") : new JsonObject();
            String code = string(error, "code", "http_" + status);
            String message = string(error, "message", "TuneWeave request failed (HTTP " + status + ')');
            boolean retryable = error.has("retryable") && error.get("retryable").getAsBoolean();
            throw new TuneWeaveException(code + ": " + message, status, code, retryable,
                    error.has("details") ? error.get("details") : JsonNull.INSTANCE);
        }
        JsonElement data = envelope.has("data") ? envelope.get("data") : JsonNull.INSTANCE;
        JsonObject meta = envelope.has("meta") && envelope.get("meta").isJsonObject()
                ? envelope.getAsJsonObject("meta") : new JsonObject();
        return new TuneWeaveResponse(status, data, meta);
    }

    private static String string(JsonObject object, String name, String fallback) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    public record TuneWeaveResponse(int statusCode, JsonElement data, JsonObject meta) {
    }

    public static class TuneWeaveException extends RuntimeException {
        @Getter
        private final int statusCode;
        @Getter
        private final String code;
        @Getter
        private final boolean retryable;
        @Getter
        private final JsonElement details;

        public TuneWeaveException(String message, boolean retryable) {
            this(message, 0, "client_error", retryable, JsonNull.INSTANCE, null);
        }

        public TuneWeaveException(String message, Throwable cause, boolean retryable) {
            this(message, 0, "client_error", retryable, JsonNull.INSTANCE, cause);
        }

        public TuneWeaveException(String message, int statusCode, String code, boolean retryable, JsonElement details) {
            this(message, statusCode, code, retryable, details, null);
        }

        private TuneWeaveException(String message, int statusCode, String code, boolean retryable,
                                   JsonElement details, Throwable cause) {
            super(message, cause);
            this.statusCode = statusCode;
            this.code = code;
            this.retryable = retryable;
            this.details = details == null ? JsonNull.INSTANCE : details;
        }
    }
}
