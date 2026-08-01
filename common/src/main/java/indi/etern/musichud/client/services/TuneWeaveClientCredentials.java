package indi.etern.musichud.client.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.utils.JsonUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/** Client-local store for TuneWeave caller credentials, keyed by platform. */
public final class TuneWeaveClientCredentials {
    private TuneWeaveClientCredentials() {
    }

    public static synchronized Map<String, String> all() {
        String stored = ClientConfig.getInstance().getTuneWeaveClientCredentials();
        Map<String, String> result = new LinkedHashMap<>();
        try {
            JsonElement parsed = JsonParser.parseString(stored == null || stored.isBlank() ? "{}" : stored);
            if (!parsed.isJsonObject()) {
                return result;
            }
            for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    String value = entry.getValue().getAsString();
                    if (!value.isBlank()) {
                        result.put(entry.getKey(), value);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // A malformed local value behaves as an empty credential store;
            // it is replaced on the next successful login.
        }
        return result;
    }

    public static synchronized void put(String platform, String credential) {
        if (platform == null || platform.isBlank() || credential == null || credential.isBlank()) {
            return;
        }
        Map<String, String> credentials = all();
        credentials.put(platform, credential);
        save(credentials);
    }

    public static synchronized void remove(String platform) {
        Map<String, String> credentials = all();
        credentials.remove(platform);
        save(credentials);
    }

    private static void save(Map<String, String> credentials) {
        JsonObject object = new JsonObject();
        credentials.forEach(object::addProperty);
        ClientConfig config = ClientConfig.getInstance();
        config.setTuneWeaveClientCredentials(JsonUtil.gson.toJson(object));
        config.save();
    }
}
