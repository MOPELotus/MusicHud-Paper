package indi.etern.musichud.client.services.tuneweave;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import indi.etern.musichud.server.api.tuneweave.TuneWeaveApiClient;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;

import java.util.Map;
import java.util.Objects;

import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.object;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.requiredString;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.string;

/** Owns authentication flows and the authenticated profile cache for each platform. */
final class TuneWeaveAuthenticationService {
    private final TuneWeaveGateway gateway;
    private final Map<TuneWeavePlatform, TuneWeaveSession> sessions;

    TuneWeaveAuthenticationService(TuneWeaveGateway gateway,
                                   Map<TuneWeavePlatform, TuneWeaveSession> sessions) {
        this.gateway = Objects.requireNonNull(gateway);
        this.sessions = Objects.requireNonNull(sessions);
    }

    TuneWeaveSession cachedSession(TuneWeavePlatform platform) {
        return sessions.get(platform);
    }

    TuneWeaveQrSession startQrLogin(TuneWeavePlatform platform, String loginType) {
        JsonObject body = clientModeBody(platform);
        if (loginType != null && !loginType.isBlank()) {
            body.addProperty("login_type", loginType.trim());
        }
        JsonObject data = object(gateway.requestWithoutCredential(
                "POST", "/v1/auth/qr", Map.of(), body).data());
        return new TuneWeaveQrSession(platform, requiredString(data, "transaction_id"),
                string(data, "url"), string(data, "image_data_url"), string(data, "expires_at"));
    }

    TuneWeaveQrPoll pollQrLogin(TuneWeaveQrSession session) {
        JsonObject data = object(gateway.requestWithoutCredential(
                "GET", "/v1/auth/qr/" + TuneWeaveApiClient.encodePathSegment(session.transactionId()),
                Map.of(), null).data());
        String state = requiredString(data, "state");
        if ("confirmed".equals(state)) {
            gateway.saveCredential(session.platform(), data.get("caller_credential"));
            return new TuneWeaveQrPoll(state, string(data, "message"), loadSession(session.platform()));
        }
        return new TuneWeaveQrPoll(state, string(data, "message"), profile(data.get("profile")));
    }

    TuneWeaveSession loginWithPassword(TuneWeavePlatform platform, String principalType,
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
        JsonObject data = object(gateway.requestWithoutCredential(
                "POST", "/v1/auth/password", Map.of(), body).data());
        gateway.saveCredential(platform, data.get("caller_credential"));
        return loadSession(platform);
    }

    TuneWeaveChallengeSession startSmsLogin(TuneWeavePlatform platform, String principal,
                                             String countryCode) {
        JsonObject body = clientModeBody(platform);
        body.addProperty("method", "sms");
        body.addProperty("principal", principal);
        body.addProperty("country_code", countryCode == null || countryCode.isBlank() ? "86" : countryCode);
        JsonObject data = object(gateway.requestWithoutCredential(
                "POST", "/v1/auth/challenges", Map.of(), body).data());
        return new TuneWeaveChallengeSession(platform, requiredString(data, "transaction_id"));
    }

    TuneWeaveSession verifySmsLogin(TuneWeaveChallengeSession session, String code) {
        JsonObject body = new JsonObject();
        body.addProperty("code", code);
        JsonObject data = object(gateway.requestWithoutCredential(
                "POST", "/v1/auth/challenges/"
                        + TuneWeaveApiClient.encodePathSegment(session.transactionId()) + "/verify",
                Map.of(), body).data());
        gateway.saveCredential(session.platform(), data.get("caller_credential"));
        return loadSession(session.platform());
    }

    TuneWeaveSession loadSession(TuneWeavePlatform platform) {
        JsonElement data = gateway.requestForPlatform(platform, "GET", "/v1/auth/session",
                Map.of("platform", platform.apiName()), null).data();
        TuneWeaveSession result = enrichSessionProfile(profile(data));
        if (result != null) {
            sessions.put(platform, result);
        }
        return result;
    }

    TuneWeaveSession refreshSession(TuneWeavePlatform platform) {
        JsonObject body = clientModeBody(platform);
        JsonObject data = object(gateway.requestForPlatform(
                platform, "POST", "/v1/auth/session/refresh", Map.of(), body).data());
        gateway.saveCredential(platform, data.get("caller_credential"));
        return loadSession(platform);
    }

    void logout(TuneWeavePlatform platform) {
        if (!gateway.hasCredential(platform)) {
            return;
        }
        gateway.requestForPlatform(platform, "DELETE", "/v1/auth/session",
                Map.of("platform", platform.apiName(), "credential_mode", "client"), null);
        clearCredential(platform);
    }

    void clearCredential(TuneWeavePlatform platform) {
        sessions.remove(platform);
        gateway.clearCredential(platform);
    }

    private TuneWeaveSession enrichSessionProfile(TuneWeaveSession session) {
        if (session == null || !session.authenticated()
                || (!isBlank(session.nickname()) && !isBlank(session.avatarUrl()))) {
            return session;
        }
        try {
            JsonObject detail = object(gateway.requestForPlatform(
                    session.platform(), "GET", "/v1/account/profile",
                    Map.of("platform", session.platform().apiName()), null).data());
            JsonObject user = detail.has("user") && detail.get("user").isJsonObject()
                    ? detail.getAsJsonObject("user") : new JsonObject();
            return new TuneWeaveSession(session.platform(),
                    prefer(session.userId(), string(user, "id")),
                    prefer(session.nickname(), string(user, "name")),
                    prefer(session.avatarUrl(), string(user, "avatar_url")),
                    session.authenticated());
        } catch (TuneWeaveApiClient.TuneWeaveException error) {
            if (!"capability_not_supported".equals(error.getCode())) {
                throw error;
            }
            return session;
        }
    }

    private static JsonObject clientModeBody(TuneWeavePlatform platform) {
        JsonObject body = new JsonObject();
        body.addProperty("platform", platform.apiName());
        body.addProperty("credential_mode", "client");
        return body;
    }

    private static TuneWeaveSession profile(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        JsonObject value = object(element);
        return new TuneWeaveSession(
                TuneWeavePlatform.fromApiName(requiredString(value, "platform")),
                string(value, "user_id"), string(value, "nickname"),
                string(value, "avatar_url"),
                value.has("authenticated") && value.get("authenticated").getAsBoolean());
    }

    private static String prefer(String primary, String fallback) {
        return isBlank(primary) ? Objects.requireNonNullElse(fallback, "") : primary;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
