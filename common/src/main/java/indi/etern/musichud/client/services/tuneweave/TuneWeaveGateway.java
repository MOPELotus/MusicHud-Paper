package indi.etern.musichud.client.services.tuneweave;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.server.api.tuneweave.TuneWeaveApiClient;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owns TuneWeave client-mode transport and credential persistence. */
final class TuneWeaveGateway {
    private static final String CREDENTIAL_FORMAT = "tuneweave_credential_v1";
    private static final String CREDENTIAL_PREFIX = "twc1_";

    private final ClientConfig config = ClientConfig.getInstance();

    TuneWeavePlatform defaultPlatform() {
        return TuneWeavePlatform.fromApiName(config.getDefaultMusicPlatform());
    }

    void setDefaultPlatform(TuneWeavePlatform platform) {
        config.setDefaultMusicPlatform(Objects.requireNonNull(platform).apiName());
        config.save();
    }

    boolean isAvailable() {
        return TuneWeaveApiClient.isAvailableAt(config.getTuneWeaveBaseUrl());
    }

    boolean hasCredential(TuneWeavePlatform platform) {
        return !credential(platform).isBlank();
    }

    String credential(TuneWeavePlatform platform) {
        return config.getTuneWeaveCredential(Objects.requireNonNull(platform).apiName());
    }

    TuneWeaveApiClient.TuneWeaveResponse requestForPlatform(
            TuneWeavePlatform platform, String method, String path,
            Map<String, String> query, JsonElement body) {
        String value = credential(platform);
        List<String> credentials = value.isBlank() ? List.of() : List.of(value);
        return request(method, path, query, body, credentials);
    }

    TuneWeaveApiClient.TuneWeaveResponse requestWithAllCredentials(
            String method, String path, Map<String, String> query, JsonElement body) {
        List<String> credentials = new ArrayList<>(TuneWeavePlatform.values().length);
        for (TuneWeavePlatform platform : TuneWeavePlatform.values()) {
            String value = credential(platform);
            if (!value.isBlank()) credentials.add(value);
        }
        return request(method, path, query, body, credentials);
    }

    TuneWeaveApiClient.TuneWeaveResponse requestWithoutCredential(
            String method, String path, Map<String, String> query, JsonElement body) {
        return request(method, path, query, body, List.of());
    }

    void saveCredential(TuneWeavePlatform expectedPlatform, JsonElement element) {
        JsonObject credential = TuneWeaveJson.object(element);
        String format = TuneWeaveJson.requiredString(credential, "format");
        String platform = TuneWeaveJson.requiredString(credential, "platform");
        String value = TuneWeaveJson.requiredString(credential, "value");
        if (!CREDENTIAL_FORMAT.equals(format)
                || TuneWeavePlatform.fromApiName(platform) != expectedPlatform
                || !value.startsWith(CREDENTIAL_PREFIX)) {
            throw new TuneWeaveApiClient.TuneWeaveException(
                    "TuneWeave returned an invalid caller credential", false);
        }
        config.setTuneWeaveCredential(expectedPlatform.apiName(), value);
        config.setDefaultMusicPlatform(expectedPlatform.apiName());
        config.save();
    }

    void clearCredential(TuneWeavePlatform platform) {
        config.clearTuneWeaveCredential(Objects.requireNonNull(platform).apiName());
        config.save();
    }

    private TuneWeaveApiClient.TuneWeaveResponse request(
            String method, String path, Map<String, String> query,
            JsonElement body, List<String> credentials) {
        return TuneWeaveApiClient.requestAt(
                config.getTuneWeaveBaseUrl(), method, path, query, body, credentials);
    }
}
