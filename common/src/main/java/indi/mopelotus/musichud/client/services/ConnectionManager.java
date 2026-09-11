package indi.mopelotus.musichud.client.services;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.widget.Toast;
import indi.mopelotus.musichud.MusicHud;
import indi.mopelotus.musichud.beans.music.MusicDetail;
import indi.mopelotus.musichud.client.audio.NowPlayingInfo;
import indi.mopelotus.musichud.client.audio.StreamAudioPlayer;
import indi.mopelotus.musichud.client.network.vanilla.VanillaPlayerProxy;
import indi.mopelotus.musichud.client.services.music.MusicService;
import indi.mopelotus.musichud.client.ui.ToastUtil;
import indi.mopelotus.musichud.interfaces.ClientConfig;
import indi.mopelotus.musichud.interfaces.IClientLoginService;
import indi.mopelotus.musichud.interfaces.IConnectionManager;
import indi.mopelotus.musichud.network.IClientNetworkService;
import indi.mopelotus.musichud.network.ProtocolInfo;
import indi.mopelotus.musichud.network.RequestResponseManager;
import indi.mopelotus.musichud.network.payloads.pushMessages.c2s.DisconnectMessage;
import indi.mopelotus.musichud.network.payloads.requestResponseCycle.ConnectRequest;
import indi.mopelotus.musichud.network.payloads.requestResponseCycle.ConnectResponse;
import indi.mopelotus.musichud.network.payloads.requestResponseCycle.GetInitialStateRequest;
import indi.mopelotus.musichud.network.payloads.requestResponseCycle.GetInitialStateResponse;
import indi.mopelotus.musichud.server.ServerPlayerRegistry;
import indi.mopelotus.musichud.utils.IClientDistUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centralized connection mode control.
 * Previously the connected/isolated switching logic was scattered across LoginService,
 * MusicService and the ConnectResponse receiver, which repeatedly caused bugs.
 */
@SuppressWarnings("unused")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ConnectionManager implements IConnectionManager {
    private static final Logger logger = MusicHud.getLogger(ConnectionManager.class);
    private static final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final long TOGGLE_DEBOUNCE_DELAY_MILLIS = 300;
    // Network handshakes, especially LAN hosts during world startup, can exceed one second.
    // The generation guard still cancels this fallback after a real response or disconnect.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static volatile ConnectionManager instance;
    private final ConnectionActionGate connectionActions = new ConnectionActionGate(this);
    private final java.util.concurrent.atomic.AtomicInteger connectGeneration = new java.util.concurrent.atomic.AtomicInteger(0);
    private double lastPressTime;
    @Getter
    private volatile ConnectionMode mode = ConnectionMode.DISCONNECTED;

    public static ConnectionManager getInstance() {
        if (instance == null) {
            synchronized (ConnectionManager.class) {
                if (instance == null) {
                    instance = new ConnectionManager();
                }
            }
        }
        return instance;
    }

    @Override
    public synchronized void connectToExternalServer() {
        connectionActions.invalidate();
        if (clientConfig.getEnable()) {
            mode = ConnectionMode.EXTERNAL;
            MusicHud.setConnectStatus(MusicHud.ConnectStatus.NOT_CONNECTED);
            clientNetworkService.sendToServer(ConnectRequest.current());
            scheduleConnectTimeoutFallback();
        }
    }

    private void scheduleConnectTimeoutFallback() {
        int generation = connectGeneration.incrementAndGet();
        var minecraft = Minecraft.getInstance();
        Object connection = minecraft.getConnection();
        java.util.concurrent.CompletableFuture.delayedExecutor(CONNECT_TIMEOUT.toMillis(),
                java.util.concurrent.TimeUnit.MILLISECONDS, minecraft).execute(() -> {
            synchronized (ConnectionManager.this) {
                if (connection != null && connection == minecraft.getConnection() && minecraft.player != null
                        && generation == connectGeneration.get() && mode == ConnectionMode.EXTERNAL
                        && MusicHud.getConnectStatus() == MusicHud.ConnectStatus.NOT_CONNECTED) {
                    if (clientConfig.getEnableIsolatedMode()) {
                        logger.warn("No ConnectResponse within {}, falling back to isolated mode", CONNECT_TIMEOUT);
                        launchIsolated();
                    } else logger.warn("No ConnectResponse within {}; isolated mode is disabled", CONNECT_TIMEOUT);
                }
            }
        });
    }

    @Override
    public synchronized void launchIsolated() {
        connectionActions.invalidate();
        mode = ConnectionMode.ISOLATED;
        if (Minecraft.getInstance().player != null) {
            ServerPlayerRegistry.getInstance().join(
                    VanillaPlayerProxy.ofPlayer(Minecraft.getInstance().player));
        }
        IClientLoginService.getInstance().restoreSession();
        MusicService.resetCurrentMusicStatus();
        NowPlayingInfo.getInstance().stop();
        StreamAudioPlayer.getInstance().stop();
        requestInitialState();
    }

    @Override
    public synchronized void switchToIsolate() {
        disconnect();
        launchIsolated();
    }

    @Override
    public synchronized void disconnect() {
        connectionActions.invalidate();
        indi.mopelotus.musichud.network.PayloadFragments.resetClient();
        clientNetworkService.sendToServer(DisconnectMessage.INSTANCE);
        MusicService.resetCurrentMusicStatus();
        NowPlayingInfo.getInstance().stop();
        StreamAudioPlayer.getInstance().stop();
        MusicHud.setConnectStatus(MusicHud.ConnectStatus.NOT_CONNECTED);
        mode = ConnectionMode.DISCONNECTED;
        connectGeneration.incrementAndGet();
    }

    @Override
    public synchronized Boolean toggleConnection() {
        MusicHud.ConnectStatus status = MusicHud.getConnectStatus();
        if (status != MusicHud.ConnectStatus.CONNECTED && status != MusicHud.ConnectStatus.NOT_CONNECTED) return null;
        var minecraft = Minecraft.getInstance();
        Runnable action = connectionActions.prepare(minecraft::getConnection, () -> {
            if (minecraft.player == null || MusicHud.getConnectStatus() != status) return;
            if (status == MusicHud.ConnectStatus.CONNECTED) {
                if (clientConfig.getEnableIsolatedMode()) switchToIsolate(); else disconnect();
            } else connectToExternalServer();
        });
        java.util.concurrent.CompletableFuture.delayedExecutor(TOGGLE_DEBOUNCE_DELAY_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS, minecraft).execute(action);
        return status == MusicHud.ConnectStatus.CONNECTED;
    }

    @Override
    public void keyBindsToggleConnection() {
        boolean integratedServer = Minecraft.getInstance().getCurrentServer() == null;
        if (!integratedServer) {
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis - lastPressTime <= 3000) {
                lastPressTime = 0;
                Boolean connected = toggleConnection();
                if (connected != null) {
                    MuiModApi.postToUiThread(() -> {
                        //noinspection UnstableApiUsage
                        Context context = UIManager.getInstance().getDecorView().getContext();
                        if (connected) {
                            ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.disconnecting"), Toast.LENGTH_SHORT));
                        } else {
                            ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.connecting"), Toast.LENGTH_SHORT));
                        }
                    });
                }
            } else {
                lastPressTime = currentTimeMillis;
                MuiModApi.postToUiThread(() -> {
                    //noinspection UnstableApiUsage
                    Context context = UIManager.getInstance().getDecorView().getContext();
                    ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.confirmSwitchConnection"), Toast.LENGTH_SHORT));
                });
            }
        } else {
            MuiModApi.postToUiThread(() -> {
                //noinspection UnstableApiUsage
                Context context = UIManager.getInstance().getDecorView().getContext();
                ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.switchConnectionUnavailableInIntegratedServer"), Toast.LENGTH_SHORT));
            });
        }
    }

    @Override
    public void connectAsPrevious() {
        if (mode == ConnectionMode.EXTERNAL) {
            IConnectionManager.getInstance().connectToExternalServer();
        } else {
            IConnectionManager.getInstance().launchIsolated();
        }
    }

    @Override
    public synchronized void onConnectResponse(ConnectResponse payload) {
        IClientDistUtil clientDistUtil = IClientDistUtil.getInstance();
        IClientLoginService clientLoginService = IClientLoginService.getInstance();
        if (MusicHud.getConnectStatus() == MusicHud.ConnectStatus.NOT_CONNECTED) {
            logger.info("Connecting {} accepted", payload.accepted() ? "accepted" : "denied");
            if (payload.accepted()) {
                if (ProtocolInfo.isCompatible(payload.projectId(), payload.serverVersion(),
                        payload.capabilities())) {
                    if (!clientDistUtil.inIntegratedServer()
                            && MusicHud.getConnectStatus() != MusicHud.ConnectStatus.CONNECTED
                            && clientConfig.getEnableIsolatedMode()) {
                        disconnect();
                    }
                    connectGeneration.incrementAndGet();
                    MusicHud.setConnectStatus(MusicHud.ConnectStatus.CONNECTED);
                    clientLoginService.restoreSession();
                    requestInitialState();
                } else {
                    MusicHud.setConnectStatus(MusicHud.ConnectStatus.INCOMPATIBLE);
                }
            } else {
                MusicHud.setConnectStatus(MusicHud.ConnectStatus.INCOMPATIBLE);
            }
        } else if (!payload.accepted()) {
            logger.info("Disconnected");
            disconnect();
        }
        clientDistUtil.refreshMainGUI();
    }

    private void requestInitialState() {
        ConnectionMode requestedMode = mode;
        int requestedGeneration = connectGeneration.get();
        requestInitialState(requestedMode, requestedGeneration, 0);
    }

    private void requestInitialState(ConnectionMode requestedMode, int requestedGeneration, int attempt) {
        MusicHud.EXECUTOR.execute(() -> {
            synchronized (ConnectionManager.this) {
                if (mode != requestedMode || requestedGeneration != connectGeneration.get()
                        || (requestedMode == ConnectionMode.EXTERNAL
                        && MusicHud.getConnectStatus() != MusicHud.ConnectStatus.CONNECTED)) {
                    return;
                }
            }
            RequestResponseManager.send(
                            new GetInitialStateRequest(),
                            GetInitialStateResponse.class,
                            Duration.ofSeconds(5))
                    .thenAccept(response -> {
                        synchronized (ConnectionManager.this) {
                            if (mode != requestedMode || requestedGeneration != connectGeneration.get()
                                    || MusicHud.getConnectStatus() != MusicHud.ConnectStatus.CONNECTED && requestedMode == ConnectionMode.EXTERNAL) {
                                logger.debug("Initial state response ignored, connection changed from mode={} generation={}",
                                        requestedMode, requestedGeneration);
                                return;
                            }
                        }
                        MusicService.getInstance().refreshQueue(response.getQueue());
                        MusicService.getInstance().switchMusic(
                                response.getPlaybackSession(), response.getNextIdle(), "");
                        MusicService.getInstance().getIdlePlaySourceState().external().updateAll(
                                response.getPlaylistSources(), response.getAlbumSources());
                    })
                    .exceptionally(e -> {
                        if (attempt < 2) {
                            logger.debug("Initial state attempt {} failed; retrying", attempt + 1, e);
                            requestInitialState(requestedMode, requestedGeneration, attempt + 1);
                        } else {
                            logger.warn("Failed to get initial state after {} attempts", attempt + 1, e);
                        }
                        return null;
                    });
        });
    }
}
