package indi.etern.musichud.client.services;

import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.AutoConnectServerFilterType;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.login.LoginType;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.ProfileConfigData;
import indi.etern.musichud.client.interfaces.IClientEventService;
import indi.etern.musichud.client.network.vanilla.VanillaPlayerProxy;
import indi.etern.musichud.client.ui.pages.account.AccountBaseView;
import indi.etern.musichud.client.ui.pages.account.LoginView;
import indi.etern.musichud.client.services.tuneweave.TuneWeaveClientService;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.interfaces.*;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.pushMessages.c2s.AnonymousLoginMessage;
import indi.etern.musichud.network.payloads.pushMessages.c2s.CookieLoginMessage;
import indi.etern.musichud.network.payloads.pushMessages.c2s.LogoutMessage;
import indi.etern.musichud.network.payloads.pushMessages.s2c.LoginResultMessage;
import indi.etern.musichud.server.api.impl.ncm.LoginApiService;
import indi.etern.musichud.server.api.ApiServerManager;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LoginService implements IClientLoginService {
    private static final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final Logger logger = MusicHud.getLogger(LoginService.class);
    private static final TuneWeaveClientService tuneWeave = TuneWeaveClientService.getInstance();
    private static volatile LoginService instance = null;
    private final List<Consumer<LoginState>> loginStateListeners = new CopyOnWriteArrayList<>();
    private volatile LoginState loginState = getLoginState();
    @Getter
    private volatile String lastLoginErrorMessage;
    private final AtomicInteger platformSessionVersion = new AtomicInteger(0);
    @Getter
    NetworkReceiver<LoginResultMessage> loginResultReceiver = (loginResult, player) -> {
        MusicHud.EXECUTOR.submit(() -> {
            Thread.currentThread().setName("MHWorker-Login-V");
            LoginCookieInfo loginCookieInfo = loginResult.loginCookieInfo();
            LoginType type = loginCookieInfo.type();
            Profile profile = loginResult.profile();
            if (type != LoginType.UNLOGGED && type != LoginType.ANONYMOUS && loginResult.success()) {
                loginCookieInfo.setToClientCookie();
                Profile.setCurrent(profile);
                lastLoginErrorMessage = null;
            } else if (type == LoginType.ANONYMOUS && Profile.ANONYMOUS.equals(profile)) {
                loginCookieInfo.setToClientCookie();
                if (availableTuneWeavePlatform() == null) {
                    Profile.setCurrent(Profile.ANONYMOUS);
                } else {
                    restoreTuneWeaveSession();
                }
                lastLoginErrorMessage = null;
            } else {
                logger.warn("Login failed");
                lastLoginErrorMessage = resolveLoginErrorMessage(loginResult.message());
            }
            notifyLoginStateChanged();
            AccountBaseView accountBaseView = AccountBaseView.getInstance();
            if (accountBaseView != null) {
                if (loginResult.success()) {
                    ProfileConfigData profileConfigData = ProfileConfigData.getInstance();
                    profileConfigData.setProfile(profile);
                    profileConfigData.saveToConfig();
                    MuiModApi.postToUiThread(accountBaseView::refresh);
                } else {
                    MuiModApi.postToUiThread(() -> {
                        accountBaseView.refresh();
                        String message = resolveLoginErrorMessage(loginResult.message());
                        accountBaseView.onLoginFailed(message);
                        LoginView loginView = LoginView.getInstance();
                        if (loginView != null) {
                            loginView.reset();
                            loginView.errorText(message);
                        }
                    });
                }
            }
        });
    };

    public static LoginService getInstance() {
        if (instance == null) {
            synchronized (LoginService.class) {
                if (instance == null) {
                    instance = new LoginService();
                }
            }
        }
        return instance;
    }

    @Override
    public boolean isLogined() {
        return getLoginState() == LoginState.LOGGED_IN;
    }

    @Override
    public LoginState getLoginState() {
        LoginCookieInfo loginCookieInfo = LoginCookieInfo.clientCurrentCookie();
        LoginType type = loginCookieInfo.type();
        Profile current = Profile.getCurrent();
        boolean tuneWeaveCredential = availableTuneWeavePlatform() != null;
        boolean realProfile = current != null && !current.equals(Profile.ANONYMOUS);
        if (tuneWeaveCredential && realProfile) {
            return LoginState.LOGGED_IN;
        }
        if (type == LoginType.ANONYMOUS || Profile.ANONYMOUS.equals(current)) {
            return LoginState.ANONYMOUS;
        }
        return LoginState.UNLOGGED;
    }

    @Override
    public Unregister addLoginStateListener(Consumer<LoginState> listener) {
        loginStateListeners.add(listener);
        return () -> loginStateListeners.remove(listener);
    }

    private void notifyLoginStateChanged() {
        LoginState state = getLoginState();
        if (state == loginState) return;
        loginState = state;
        loginStateListeners.forEach(listener -> listener.accept(state));
    }

    private static String resolveLoginErrorMessage(String message) {
        if (message != null && message.startsWith(MusicHud.MOD_ID)) {
            return I18n.get(message);
        }
        return message;
    }

    public void clearLastLoginErrorMessage() {
        lastLoginErrorMessage = null;
    }

    @Override
    public boolean hasPreviousLoginInfo() {
        // TuneWeave owns platform credentials in client mode. The old server
        // cookie is intentionally ignored by the new API integration.
        return availableTuneWeavePlatform() != null;
    }

    public boolean hasAnyTuneWeaveLogin() {
        for (TuneWeavePlatform platform : TuneWeavePlatform.values()) {
            if (tuneWeave.hasCredential(platform)) return true;
        }
        return false;
    }

    @Override
    public void loginToServer() {
        logger.info("Joining the Music HUD server anonymously; TuneWeave credentials stay client-owned");
        loginAsAnonymousToServer();
        if (availableTuneWeavePlatform() != null) {
            restoreTuneWeaveSession();
        }
    }

    private void loginAsAnonymousToServer() {
        LoginCookieInfo loginCookieInfo = LoginCookieInfo.clientCurrentCookie();
        if (loginCookieInfo.type() == LoginType.ANONYMOUS) {
            clientNetworkService.sendToServer(new CookieLoginMessage(loginCookieInfo, false));
        } else {
            clientNetworkService.sendToServer(AnonymousLoginMessage.REQUEST);
        }
    }

    @Override
    public void logoutAndReloginAsAnonymous() {
        platformSessionVersion.incrementAndGet();
        MusicHud.EXECUTOR.execute(() -> {
            try {
                tuneWeave.logout(tuneWeave.defaultPlatform());
            } catch (RuntimeException error) {
                tuneWeave.clearCredential(tuneWeave.defaultPlatform());
                logger.warn("TuneWeave logout failed; discarded the local caller credential");
            }
            clientNetworkService.sendToServer(LogoutMessage.MESSAGE);
            Profile.setCurrent(Profile.ANONYMOUS);
            notifyLoginStateChanged();
            loginAsAnonymousToServer();
            refreshAccountView();
        });
    }

    public void completeTuneWeaveLogin(TuneWeaveClientService.SessionProfile sessionProfile) {
        if (sessionProfile == null || !sessionProfile.authenticated()) {
            throw new IllegalArgumentException("TuneWeave did not return an authenticated profile");
        }
        tuneWeave.setDefaultPlatform(sessionProfile.platform());
        MusicService.getInstance().invalidateUserCollections();
        Profile profile = sessionProfile.toMusicHudProfile();
        Profile.setCurrent(profile);
        ProfileConfigData profileConfigData = ProfileConfigData.getInstance();
        profileConfigData.setProfile(profile);
        profileConfigData.saveToConfig();
        lastLoginErrorMessage = null;
        notifyLoginStateChanged();
        refreshAccountView();
    }

    public void switchTuneWeavePlatform(TuneWeavePlatform platform) {
        int version = platformSessionVersion.incrementAndGet();
        tuneWeave.setDefaultPlatform(platform);
        MusicService.getInstance().invalidateUserCollections();
        if (!tuneWeave.hasCredential(platform)) {
            Profile.setCurrent(Profile.ANONYMOUS);
            notifyLoginStateChanged();
            refreshAccountView();
            return;
        }
        MusicHud.EXECUTOR.execute(() -> {
            try {
                TuneWeaveClientService.SessionProfile profile = tuneWeave.loadSession(platform);
                if (version != platformSessionVersion.get() || tuneWeave.defaultPlatform() != platform) return;
                completeTuneWeaveLogin(profile);
            } catch (RuntimeException error) {
                if (version != platformSessionVersion.get()) return;
                lastLoginErrorMessage = error.getMessage();
                refreshAccountView();
            }
        });
    }

    public void restoreTuneWeaveSession() {
        TuneWeavePlatform platform = availableTuneWeavePlatform();
        if (platform == null) return;
        int version = platformSessionVersion.incrementAndGet();
        tuneWeave.setDefaultPlatform(platform);
        MusicHud.EXECUTOR.execute(() -> {
            try {
                TuneWeaveClientService.SessionProfile profile = tuneWeave.loadSession(platform);
                if (version != platformSessionVersion.get() || tuneWeave.defaultPlatform() != platform) return;
                completeTuneWeaveLogin(profile);
            } catch (RuntimeException error) {
                if (version != platformSessionVersion.get()) return;
                lastLoginErrorMessage = error.getMessage();
                logger.warn("Failed to restore the client-owned TuneWeave session: {}", error.getMessage());
                refreshAccountView();
            }
        });
    }

    private static TuneWeavePlatform availableTuneWeavePlatform() {
        TuneWeavePlatform preferred = tuneWeave.defaultPlatform();
        if (tuneWeave.hasCredential(preferred)) return preferred;
        for (TuneWeavePlatform platform : TuneWeavePlatform.values()) {
            if (tuneWeave.hasCredential(platform)) return platform;
        }
        return null;
    }

    private static void refreshAccountView() {
        AccountBaseView accountBaseView = AccountBaseView.getInstance();
        if (accountBaseView != null) {
            MuiModApi.postToUiThread(accountBaseView::refresh);
        }
    }

    @RegisterMark
    public static final class RegisterImpl implements ClientRegister {
        @Override
        public void register() {
            IClientEventService eventService = IClientEventService.getInstance();
            ApiServerManager apiServerManager = ApiServerManager.getInstance();
            if (apiServerManager != null) {
                apiServerManager.getApiStatusListeners().add(status -> {
                    LoginService loginService = LoginService.getInstance();
                    if (status == ApiServerManager.BinaryApiServerStatus.RUNNING
                            && Minecraft.getInstance().player != null
                            && loginService.hasPreviousLoginInfo()
                            && !loginService.isLogined()) {
                        loginService.loginToServer();
                    }
                });
            }
            eventService.registerClientPlayerJoin((player) -> {
                MusicHud.EXECUTOR.execute(() -> {
                    ServerData currentServer = Minecraft.getInstance().getCurrentServer();
                    if (currentServer != null) {
                        boolean autoConnectToServer = clientConfig.getEnableAutoConnect();
                        if (autoConnectToServer) {
                            AutoConnectServerFilterType connectServerFilterType = clientConfig.getConnectServerFilterType();
                            if ((connectServerFilterType == AutoConnectServerFilterType.BLACK_LIST
                                    && clientConfig.getBlackList().stream().noneMatch(i -> Pattern.matches(i, currentServer.ip)))
                                    || (connectServerFilterType == AutoConnectServerFilterType.WHITE_LIST
                                    && clientConfig.getWhiteList().stream().anyMatch(i -> Pattern.matches(i, currentServer.ip)))) {
                                IConnectionManager.getInstance().connectToExternalServer();
                            } else {
                                IConnectionManager.getInstance().launchIsolated();
                            }
                        } else {
                            IConnectionManager.getInstance().launchIsolated();
                        }
                    } else {
                        // Single Player: try external first, fall back to isolated on timeout
                        IConnectionManager.getInstance().connectToExternalServer();
                    }
                });
            });
            eventService.registerClientPlayerQuit((player) -> {
                MusicHud.EXECUTOR.execute(() -> {
                    if (MusicHud.getConnectStatus() == MusicHud.ConnectStatus.NOT_CONNECTED) {
                        if (clientConfig.getEnableIsolatedMode()) {
                            LoginApiService.getInstance().logout(VanillaPlayerProxy.ofPlayer(player));
                        }
                    } else {
                        MusicHud.setConnectStatus(MusicHud.ConnectStatus.NOT_CONNECTED);
                    }
                });
            });
        }
    }
}
