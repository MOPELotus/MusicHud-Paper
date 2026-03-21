package indi.etern.musichud.client.services;

import dev.architectury.networking.NetworkManager;
import indi.etern.musichud.network.pushMessages.s2c.LoginResultMessage;
import indi.etern.musichud.network.requestResponseCycle.StartQRLoginResponse;

/**
 * Server-side placeholder for shared S2C registration code.
 * Real login behavior is implemented in the client modules only.
 */
public final class LoginService {
    private static final LoginService INSTANCE = new LoginService();

    private final NetworkManager.NetworkReceiver<LoginResultMessage> loginResultReceiver = (payload, context) -> {
    };
    private final NetworkManager.NetworkReceiver<StartQRLoginResponse> qrLoginResponseReceiver = (payload, context) -> {
    };

    private LoginService() {
    }

    public static LoginService getInstance() {
        return INSTANCE;
    }

    public NetworkManager.NetworkReceiver<LoginResultMessage> getLoginResultReceiver() {
        return loginResultReceiver;
    }

    public NetworkManager.NetworkReceiver<StartQRLoginResponse> getQrLoginResponseReceiver() {
        return qrLoginResponseReceiver;
    }

    public void loginToServer() {
    }

    public void logout() {
    }
}
