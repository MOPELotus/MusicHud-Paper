package indi.etern.musichud.server;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.server.ConnectionState;
import indi.etern.musichud.beans.server.PlayerQueryResult;
import indi.etern.musichud.beans.server.PlayerStatusInfo;
import indi.etern.musichud.beans.server.ServerActionResult;
import indi.etern.musichud.beans.server.ServerStatusInfo;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.VipType;
import indi.etern.musichud.interfaces.IServerAdminService;
import indi.etern.musichud.interfaces.IServerEventService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.interfaces.ServerConfig;
import indi.etern.musichud.interfaces.ServerRegister;
import indi.etern.musichud.server.api.ApiServerManager;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerManagementServerService {
    private static volatile ServerManagementServerService instance;

    private final Map<UUID, ClientSessionInfo> clientSessionInfoMap = new ConcurrentHashMap<>();
    private final ServerConfig serverConfig = ServerConfig.getInstance();
    private final IServerAdminService serverAdminService = IServerAdminService.getInstance();

    private ServerManagementServerService() {
    }

    public static ServerManagementServerService getInstance() {
        if (instance == null) {
            synchronized (ServerManagementServerService.class) {
                if (instance == null) {
                    instance = new ServerManagementServerService();
                }
            }
        }
        return instance;
    }

    public void markConnect(ServerPlayer player, Version clientVersion, boolean compatible) {
        clientSessionInfoMap.compute(player.getUUID(), (uuid, existing) -> {
            ClientSessionInfo info = existing == null ? new ClientSessionInfo(uuid) : existing;
            info.playerName = player.getName().getString();
            info.modVersion = clientVersion.toString();
            info.connectionState = compatible ? ConnectionState.CONNECTED : ConnectionState.INCAPABLE;
            if (!compatible) {
                info.loginCookieInfo = LoginCookieInfo.UNLOGGED;
                info.profile = Profile.ANONYMOUS;
                info.vipType = VipType.NORMAL;
            }
            return info;
        });
    }

    public void markLoginState(ServerPlayer player, LoginCookieInfo loginCookieInfo, Profile profile, VipType vipType) {
        clientSessionInfoMap.compute(player.getUUID(), (uuid, existing) -> {
            ClientSessionInfo info = existing == null ? new ClientSessionInfo(uuid) : existing;
            info.playerName = player.getName().getString();
            info.connectionState = ConnectionState.CONNECTED;
            info.loginCookieInfo = Objects.requireNonNullElse(loginCookieInfo, LoginCookieInfo.UNLOGGED);
            info.profile = Objects.requireNonNullElse(profile, Profile.ANONYMOUS);
            info.vipType = Objects.requireNonNullElse(vipType, VipType.NORMAL);
            return info;
        });
    }

    public void markLogout(ServerPlayer player) {
        clientSessionInfoMap.computeIfPresent(player.getUUID(), (uuid, info) -> {
            info.playerName = player.getName().getString();
            info.connectionState = ConnectionState.CONNECTED;
            info.loginCookieInfo = LoginCookieInfo.UNLOGGED;
            info.profile = Profile.ANONYMOUS;
            info.vipType = VipType.NORMAL;
            return info;
        });
    }

    public void removePlayer(ServerPlayer player) {
        clientSessionInfoMap.remove(player.getUUID());
    }

    public ServerStatusInfo buildServerStatusInfo(ServerPlayer viewer) {
        boolean canManageConfig = serverAdminService.canManageServerConfig(viewer);
        String rawApiBaseUrl = Objects.requireNonNullElse(serverConfig.getServerApiBaseUrl(), "");
        String visibleApiBaseUrl = rawApiBaseUrl;
        boolean apiBaseUrlMasked = false;
        if (!canManageConfig && !shouldRevealApiAddress(rawApiBaseUrl)) {
            visibleApiBaseUrl = maskApiBaseUrl(rawApiBaseUrl);
            apiBaseUrlMasked = !visibleApiBaseUrl.equals(rawApiBaseUrl);
        }
        String binaryPath = canManageConfig ? serverConfig.getConfiguredServerApiBinaryExecutablePath() : "";
        return new ServerStatusInfo(
                Version.current.toString(),
                serverAdminService.canReloadConfig(viewer),
                canManageConfig,
                serverAdminService.canManagePlayback(viewer),
                serverAdminService.canQueryPlayerInfo(viewer),
                visibleApiBaseUrl,
                apiBaseUrlMasked,
                serverConfig.getStartupBinaryApiServerWhenLaunch(),
                Objects.requireNonNullElse(binaryPath, ""),
                serverConfig.getPusherVoteAdditionalRate(),
                serverConfig.getUseRandomCnIp(),
                ApiServerManager.getBinaryApiServerStatus().i18nKey()
        );
    }

    public ServerActionResult updateServerConfig(
            ServerPlayer player,
            String serverApiBaseUrl,
            boolean startupBinaryApiServerWhenLaunch,
            String serverApiBinaryExecutablePath,
            double pusherVoteAdditionalRate,
            boolean useRandomCnIp
    ) {
        if (!serverAdminService.canManageServerConfig(player)) {
            return new ServerActionResult(
                    false,
                    MusicHud.MOD_ID + ".error.noServerAdminPermission",
                    buildServerStatusInfo(player)
            );
        }
        serverConfig.setServerApiBaseUrl(serverApiBaseUrl.trim());
        serverConfig.setStartupBinaryApiServerWhenLaunch(startupBinaryApiServerWhenLaunch);
        serverConfig.setServerApiBinaryExecutablePath(serverApiBinaryExecutablePath.trim());
        serverConfig.setPusherVoteAdditionalRate(pusherVoteAdditionalRate);
        serverConfig.setUseRandomCnIp(useRandomCnIp);
        serverConfig.save();
        syncEmbeddedApiServer();
        return new ServerActionResult(
                true,
                MusicHud.MOD_ID + ".text.serverConfigSaved",
                buildServerStatusInfo(player)
        );
    }

    public ServerActionResult reloadServerConfig(ServerPlayer player) {
        if (!serverAdminService.canReloadConfig(player)) {
            return new ServerActionResult(
                    false,
                    MusicHud.MOD_ID + ".error.noServerAdminPermission",
                    buildServerStatusInfo(player)
            );
        }
        serverAdminService.reloadConfig();
        syncEmbeddedApiServer();
        return new ServerActionResult(
                true,
                MusicHud.MOD_ID + ".text.serverConfigReloaded",
                buildServerStatusInfo(player)
        );
    }

    public PlayerQueryResult queryPlayerInfo(ServerPlayer requester, String query) {
        if (!serverAdminService.canQueryPlayerInfo(requester)) {
            return new PlayerQueryResult(
                    false,
                    query,
                    MusicHud.MOD_ID + ".error.noServerAdminPermission",
                    PlayerStatusInfo.EMPTY
            );
        }
        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.isEmpty()) {
            return new PlayerQueryResult(
                    false,
                    "",
                    MusicHud.MOD_ID + ".error.playerQueryEmpty",
                    PlayerStatusInfo.EMPTY
            );
        }
        ServerPlayer target = requester.level().getServer().getPlayerList().getPlayers().stream()
                .filter(player -> player.getName().getString().equalsIgnoreCase(trimmedQuery))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return new PlayerQueryResult(
                    false,
                    trimmedQuery,
                    MusicHud.MOD_ID + ".error.playerNotFound",
                    PlayerStatusInfo.EMPTY
            );
        }
        ClientSessionInfo sessionInfo = clientSessionInfoMap.get(target.getUUID());
        PlayerStatusInfo playerStatusInfo;
        if (sessionInfo == null) {
            playerStatusInfo = new PlayerStatusInfo(
                    target.getName().getString(),
                    target.getUUID(),
                    ConnectionState.NOT_CONNECTED,
                    "",
                    LoginCookieInfo.UNLOGGED.type(),
                    Profile.ANONYMOUS,
                    VipType.NORMAL
            );
        } else {
            playerStatusInfo = new PlayerStatusInfo(
                    target.getName().getString(),
                    target.getUUID(),
                    sessionInfo.connectionState,
                    Objects.requireNonNullElse(sessionInfo.modVersion, ""),
                    sessionInfo.loginCookieInfo.type(),
                    sessionInfo.profile,
                    sessionInfo.vipType
            );
        }
        return new PlayerQueryResult(true, trimmedQuery, "", playerStatusInfo);
    }

    public boolean canManagePlayback(ServerPlayer player) {
        return serverAdminService.canManagePlayback(player);
    }

    public void forceSkipCurrent(ServerPlayer player) {
        if (!canManagePlayback(player)) {
            return;
        }
        MusicPlayerServerService.getInstance().forceSkipCurrent();
    }

    public void forceRemoveMusicFromQueue(int index, long id, ServerPlayer player) {
        if (!canManagePlayback(player)) {
            return;
        }
        MusicPlayerServerService.getInstance().forceRemoveMusicDetailFromQueue(index, id);
    }

    public void forceRemoveIdlePlaySource(UUID ownerUuid, long id, Class<?> type, ServerPlayer player) {
        if (!canManagePlayback(player)) {
            return;
        }
        MusicPlayerServerService.getInstance().forceRemoveIdlePlaySource(ownerUuid, id, type);
    }

    private void syncEmbeddedApiServer() {
        if (serverConfig.getStartupBinaryApiServerWhenLaunch()) {
            ApiServerManager.restartApiServer();
        } else {
            ApiServerManager.stopApiServer();
        }
    }

    private boolean shouldRevealApiAddress(String apiBaseUrl) {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            return true;
        }
        if (serverConfig.getStartupBinaryApiServerWhenLaunch()) {
            return true;
        }
        try {
            String host = Objects.requireNonNullElse(URI.create(apiBaseUrl).getHost(), "").toLowerCase(Locale.ROOT);
            if (host.isEmpty()) {
                return true;
            }
            if (host.equals("localhost") || host.equals("0.0.0.0") || host.equals("::1") || host.endsWith(".local")) {
                return true;
            }
            if (host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.")) {
                return true;
            }
            if (host.startsWith("172.")) {
                String[] segments = host.split("\\.");
                if (segments.length > 1) {
                    int secondSegment = Integer.parseInt(segments[1]);
                    return secondSegment >= 16 && secondSegment <= 31;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private String maskApiBaseUrl(String apiBaseUrl) {
        try {
            URI uri = URI.create(apiBaseUrl);
            StringBuilder builder = new StringBuilder();
            if (uri.getScheme() != null) {
                builder.append(uri.getScheme()).append("://");
            }
            builder.append("***");
            if (uri.getPort() != -1) {
                builder.append(':').append(uri.getPort());
            }
            if (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath())) {
                builder.append(uri.getPath());
            }
            return builder.toString();
        } catch (Exception ignored) {
            return "***";
        }
    }

    @RegisterMark
    public static final class Register implements ServerRegister {
        @Override
        public void register() {
            IServerEventService.getInstance().registerCommonPlayerQuit(player -> getInstance().removePlayer(player));
        }
    }

    private static final class ClientSessionInfo {
        @Getter
        private final UUID playerUuid;
        private String playerName = "";
        private String modVersion = "";
        private ConnectionState connectionState = ConnectionState.NOT_CONNECTED;
        private LoginCookieInfo loginCookieInfo = LoginCookieInfo.UNLOGGED;
        private Profile profile = Profile.ANONYMOUS;
        private VipType vipType = VipType.NORMAL;

        private ClientSessionInfo(UUID playerUuid) {
            this.playerUuid = playerUuid;
        }
    }
}
