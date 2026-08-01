package indi.etern.musichud.server.api.impl.tuneweave;

import indi.etern.musichud.interfaces.ServerConfig;

/** The single TuneWeave endpoint advertised by the active game server. */
public final class TuneWeaveEndpoint {
    private static volatile String connectedServerBaseUrl = "";

    private TuneWeaveEndpoint() {
    }

    public static String serverBaseUrl() {
        return normalize(ServerConfig.getInstance().getServerApiBaseUrl());
    }

    public static String clientBaseUrl() {
        String endpoint = connectedServerBaseUrl;
        return endpoint.isBlank() ? serverBaseUrl() : endpoint;
    }

    public static void setConnectedServerBaseUrl(String endpoint) {
        connectedServerBaseUrl = normalize(endpoint);
    }

    public static void clearConnectedServerBaseUrl() {
        connectedServerBaseUrl = "";
    }

    private static String normalize(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "http://127.0.0.1:7832";
        }
        String value = endpoint.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
