package indi.etern.musichud.client.services;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.server.PlayerQueryResult;
import indi.etern.musichud.beans.server.ServerActionResult;
import indi.etern.musichud.beans.server.ServerStatusInfo;
import indi.etern.musichud.interfaces.ClientRegister;
import indi.etern.musichud.interfaces.IClientEventService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.payloads.pushMessages.c2s.AdminRemoveIdlePlaySourceMessage;
import indi.etern.musichud.network.payloads.pushMessages.c2s.AdminRemoveMusicFromQueueMessage;
import indi.etern.musichud.network.payloads.pushMessages.c2s.ForceSkipCurrentMusicMessage;
import indi.etern.musichud.network.payloads.requestResponseCycle.GetServerStatusRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.QueryPlayerStatusRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.ReloadServerConfigRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.UpdateServerConfigRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ServerManagementService {
    private static volatile ServerManagementService instance;
    private final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();
    private final List<Consumer<ServerStatusInfo>> statusListeners = new CopyOnWriteArrayList<>();
    private volatile ServerStatusInfo serverStatusInfo = ServerStatusInfo.EMPTY;
    private volatile CompletableFuture<ServerActionResult> pendingServerConfigUpdate;
    private volatile CompletableFuture<ServerActionResult> pendingServerConfigReload;
    private volatile CompletableFuture<PlayerQueryResult> pendingPlayerQuery;

    private ServerManagementService() {
    }

    public static ServerManagementService getInstance() {
        if (instance == null) {
            synchronized (ServerManagementService.class) {
                if (instance == null) {
                    instance = new ServerManagementService();
                }
            }
        }
        return instance;
    }

    public List<Consumer<ServerStatusInfo>> getStatusListeners() {
        return statusListeners;
    }

    public ServerStatusInfo getServerStatusInfo() {
        return serverStatusInfo;
    }

    public void updateServerStatus(ServerStatusInfo serverStatusInfo) {
        this.serverStatusInfo = serverStatusInfo == null ? ServerStatusInfo.EMPTY : serverStatusInfo;
        for (Consumer<ServerStatusInfo> listener : statusListeners) {
            listener.accept(this.serverStatusInfo);
        }
    }

    public void refreshServerStatus() {
        if (MusicHud.getStatus() == MusicHud.ConnectStatus.CONNECTED) {
            clientNetworkService.sendToServer(GetServerStatusRequest.REQUEST);
        } else {
            updateServerStatus(ServerStatusInfo.EMPTY);
        }
    }

    public CompletableFuture<ServerActionResult> updateRemoteServerConfig(
            String serverApiBaseUrl,
            boolean startupBinaryApiServerWhenLaunch,
            String serverApiBinaryExecutablePath,
            double pusherVoteAdditionalRate,
            boolean useRandomCnIp
    ) {
        CompletableFuture<ServerActionResult> future = new CompletableFuture<>();
        pendingServerConfigUpdate = future;
        clientNetworkService.sendToServer(new UpdateServerConfigRequest(
                serverApiBaseUrl,
                startupBinaryApiServerWhenLaunch,
                serverApiBinaryExecutablePath,
                pusherVoteAdditionalRate,
                useRandomCnIp
        ));
        return future;
    }

    public void handleServerConfigUpdateResult(ServerActionResult result) {
        updateServerStatus(result.serverStatusInfo());
        CompletableFuture<ServerActionResult> future = pendingServerConfigUpdate;
        if (future != null) {
            pendingServerConfigUpdate = null;
            future.complete(result);
        }
    }

    public CompletableFuture<ServerActionResult> reloadRemoteServerConfig() {
        CompletableFuture<ServerActionResult> future = new CompletableFuture<>();
        pendingServerConfigReload = future;
        clientNetworkService.sendToServer(ReloadServerConfigRequest.REQUEST);
        return future;
    }

    public void handleServerConfigReloadResult(ServerActionResult result) {
        updateServerStatus(result.serverStatusInfo());
        CompletableFuture<ServerActionResult> future = pendingServerConfigReload;
        if (future != null) {
            pendingServerConfigReload = null;
            future.complete(result);
        }
    }

    public CompletableFuture<PlayerQueryResult> queryPlayerStatus(String query) {
        CompletableFuture<PlayerQueryResult> future = new CompletableFuture<>();
        pendingPlayerQuery = future;
        clientNetworkService.sendToServer(new QueryPlayerStatusRequest(query));
        return future;
    }

    public void handlePlayerStatusQueryResult(PlayerQueryResult result) {
        CompletableFuture<PlayerQueryResult> future = pendingPlayerQuery;
        if (future != null) {
            pendingPlayerQuery = null;
            future.complete(result);
        }
    }

    public void forceSkipCurrent() {
        clientNetworkService.sendToServer(ForceSkipCurrentMusicMessage.MESSAGE);
    }

    public void adminRemoveMusicFromQueue(int index, MusicDetail musicDetail) {
        clientNetworkService.sendToServer(new AdminRemoveMusicFromQueueMessage(index, musicDetail.getId()));
    }

    public void adminRemoveIdlePlaySource(MusicCollection musicCollection) {
        clientNetworkService.sendToServer(new AdminRemoveIdlePlaySourceMessage(
                musicCollection.getPusherInfo().playerUUID(),
                musicCollection.getId(),
                musicCollection.getClass()
        ));
    }

    public boolean canManagePlayback() {
        return serverStatusInfo.canManagePlayback();
    }

    public boolean isSingleplayerContext() {
        return Minecraft.getInstance().hasSingleplayerServer();
    }

    public String translateMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        if (message.startsWith(MusicHud.MOD_ID + ".")) {
            return I18n.get(message);
        }
        return message;
    }

    public void reset() {
        updateServerStatus(ServerStatusInfo.EMPTY);
        pendingPlayerQuery = null;
        pendingServerConfigReload = null;
        pendingServerConfigUpdate = null;
    }

    @RegisterMark
    public static final class RegisterImpl implements ClientRegister {
        @Override
        public void register() {
            IClientEventService.getInstance().registerClientPlayerQuit(player -> getInstance().reset());
        }
    }
}
