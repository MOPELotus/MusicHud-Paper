package indi.etern.musichud.platform.plugin.paper.command;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.FormatType;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.client.audio.decoder.AudioDecodeProbe;
import indi.etern.musichud.client.audio.decoder.AudioDecoderFactory;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.pushMessages.s2c.DebugPlaytestMessage;
import indi.etern.musichud.platform.plugin.paper.config.ServerConfigDefinition;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ApiServerManager;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import indi.etern.musichud.server.api.impl.ncm.LoginApiService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class PaperServerAdminCommand {
    private static final String COMMAND_NAME = "musichud";
    private static final List<String> COMMAND_ALIASES = List.of("music");
    private static final String ADMIN_PERMISSION = "musichud.admin";
    private static final String RELOAD_PERMISSION = "musichud.reload";
    private static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "MusicHud" + ChatColor.DARK_GRAY + "] " + ChatColor.RESET;
    private static final List<String> ROOT_SUBCOMMANDS = List.of("help", "status", "player", "config", "api", "playback", "test", "playtest");
    private static final List<String> CONFIG_KEYS = List.of(
            "serverApiBaseUrl",
            "startupBinaryApiServerWhenLaunch",
            "serverApiBinaryExecutablePath",
            "pusherVoteAdditionalRate",
            "useRandomCnIp"
    );

    private final JavaPlugin plugin;
    private volatile boolean appliedStartupBinary;
    private volatile String appliedBinaryPath = "";

    public PaperServerAdminCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.registerCommand(
                COMMAND_NAME,
                "MusicHud admin, decoder test and playtest tools",
                COMMAND_ALIASES,
                new BasicCommand() {
                    @Override
                    public void execute(CommandSourceStack commandSourceStack, String[] args) {
                        handleCommand(commandSourceStack.getSender(), COMMAND_NAME, args);
                    }

                    @Override
                    public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
                        return handleTabComplete(commandSourceStack.getSender(), COMMAND_NAME, args);
                    }
                }
        );
        refreshAppliedConfigSnapshot();
    }

    private boolean handleCommand(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].isBlank()) || equalsAny(args[0], "help", "?")) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> handleStatus(sender);
            case "player" -> handlePlayer(sender, args);
            case "config" -> handleConfig(sender, args);
            case "api" -> handleApi(sender, args);
            case "playback" -> handlePlayback(sender, args);
            case "test" -> handleTest(sender, args);
            case "playtest" -> handlePlaytest(sender, args);
            default -> {
                sendError(sender, "未知子命令: " + args[0]);
                sendHint(sender, "使用 /" + COMMAND_NAME + " help 查看帮助。");
                yield true;
            }
        };
    }

    private List<String> handleTabComplete(CommandSender sender, String alias, String[] args) {
        if (args == null || args.length == 0) {
            return ROOT_SUBCOMMANDS;
        }
        String firstArg = args[0] == null ? "" : args[0];
        if (args.length == 1) {
            return filterSuggestions(ROOT_SUBCOMMANDS, firstArg);
        }

        String root = firstArg.toLowerCase(Locale.ROOT);
        if ("player".equals(root) && args.length == 2) {
            return filterSuggestions(onlinePlayerNames(), args[1]);
        }
        if ("config".equals(root)) {
            if (args.length == 2) {
                return filterSuggestions(List.of("show", "set", "save", "reload"), args[1]);
            }
            if (args.length == 3 && equalsAny(args[1], "set")) {
                return filterSuggestions(CONFIG_KEYS, args[2]);
            }
            if (args.length == 4 && equalsAny(args[1], "set")) {
                String key = args[2];
                if (isBooleanConfigKey(key)) {
                    return filterSuggestions(List.of("true", "false"), args[3]);
                }
            }
        }
        if ("api".equals(root) && args.length == 2) {
            return filterSuggestions(List.of("status", "restart", "stop"), args[1]);
        }
        if ("playback".equals(root)) {
            if (args.length == 2) {
                return filterSuggestions(List.of("skip", "queue", "idle"), args[1]);
            }
            if (equalsAny(args[1], "queue") && args.length == 3) {
                return filterSuggestions(List.of("remove"), args[2]);
            }
            if (equalsAny(args[1], "idle")) {
                if (args.length == 3) {
                    return filterSuggestions(List.of("remove"), args[2]);
                }
                if (args.length == 4 && equalsAny(args[2], "remove")) {
                    return filterSuggestions(onlinePlayerNames(), args[3]);
                }
                if (args.length == 5 && equalsAny(args[2], "remove")) {
                    return filterSuggestions(List.of("playlist", "album"), args[4]);
                }
            }
        }
        if ("test".equals(root) && args.length >= 2) {
            return filterSuggestions(Arrays.stream(FormatType.values()).map(Enum::name).toList(), args[args.length - 1]);
        }
        if ("playtest".equals(root)) {
            if (args.length == 2) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("stop");
                if (sender instanceof Player) {
                    suggestions.add("self");
                }
                suggestions.addAll(onlinePlayerNames());
                return filterSuggestions(suggestions, args[1]);
            }
            if (args.length >= 3 && equalsAny(args[1], "stop")) {
                return filterSuggestions(onlinePlayerNames(), args[2]);
            }
            if (sender instanceof Player && looksLikeIdentifierToken(args[1]) && args.length >= 3) {
                return filterSuggestions(Arrays.stream(FormatType.values()).map(Enum::name).toList(), args[args.length - 1]);
            }
            if (sender instanceof Player && equalsAny(args[1], "self", "@s") && args.length >= 4) {
                return filterSuggestions(Arrays.stream(FormatType.values()).map(Enum::name).toList(), args[args.length - 1]);
            }
            if (args.length >= 4) {
                return filterSuggestions(Arrays.stream(FormatType.values()).map(Enum::name).toList(), args[args.length - 1]);
            }
        }

        return List.of();
    }

    private boolean handleStatus(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return true;
        }
        ServerConfigDefinition serverConfig = ServerConfigDefinition.getInstance();
        MusicPlayerServerService musicService = MusicPlayerServerService.getInstance();
        Map<ServerPlayer, LoginApiService.PlayerLoginInfo> loginInfoMap = ILoginApiService.getInstance(ApiProvider.NCM).getPlayerInfoMap();
        Map<ServerPlayer, Set<IdlePlaySource>> idleSources = musicService.getIdlePlaySourcesSnapshot();
        long loggedAccountCount = loginInfoMap.values().stream()
                .filter(info -> info != null && info.getLoginCookieInfo() != null && info.getLoginCookieInfo().type() != null)
                .filter(info -> info.getLoginCookieInfo().type() != indi.etern.musichud.beans.login.LoginType.UNLOGGED)
                .count();
        int idleSourceCount = idleSources.values().stream().mapToInt(Set::size).sum();
        sendCommandHeader(sender, "服务端状态");
        sendField(sender, "版本", Version.current);
        ApiServerManager apiServerManager = ApiServerManager.getInstance();
        sendField(sender, "API 状态", formatStatusValue(apiServerManager.getBinaryApiServerStatus()));
        sendField(sender, "API 地址", serverConfig.getServerApiBaseUrl());
        sendField(sender, "内置 API 开机启动", formatBooleanValue(serverConfig.getStartupBinaryApiServerWhenLaunch()));
        sendSectionTitle(sender, "在线情况");
        sendField(sender, "在线玩家", Bukkit.getOnlinePlayers().size());
        sendField(sender, "已追踪登录状态", loginInfoMap.size());
        sendField(sender, "有账号信息", loggedAccountCount);
        sendSectionTitle(sender, "播放情况");
        sendField(sender, "当前播放", describeMusic(musicService.getCurrentMusicDetail()));
        sendField(sender, "下一首空闲播放", describeMusic(musicService.getNextIdleMusicDetail()));
        sendField(sender, "队列数量", musicService.getMusicQueue().size());
        sendField(sender, "空闲播放拥有者", idleSources.size());
        sendField(sender, "空闲播放源", idleSourceCount);
        return true;
    }

    private boolean handlePlayer(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            sendUsage(sender, "/" + COMMAND_NAME + " player <玩家名>");
            return true;
        }
        Player player = findOnlinePlayer(args[1]);
        if (player == null) {
            sendError(sender, "未找到在线玩家: " + args[1]);
            return true;
        }
        ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        LoginApiService.PlayerLoginInfo loginInfo = ILoginApiService.getInstance(ApiProvider.NCM).getLoginInfoByServerPlayer(serverPlayer);
        long queuedByPlayer = MusicPlayerServerService.getInstance().getMusicQueue().stream()
                .filter(musicDetail -> musicDetail.getPusherInfo() != null && player.getUniqueId().equals(musicDetail.getPusherInfo().getPlayerUUID()))
                .count();
        int idleSourceCount = MusicPlayerServerService.getInstance().getIdlePlaySourcesSnapshot()
                .getOrDefault(serverPlayer, Set.of())
                .size();

        sendCommandHeader(sender, "玩家详情");
        sendField(sender, "玩家", player.getName());
        sendField(sender, "UUID", player.getUniqueId());
        if (loginInfo == null) {
            sendField(sender, "登录状态", ChatColor.RED + "未追踪");
        } else {
            LoginCookieInfo cookieInfo = loginInfo.getLoginCookieInfo();
            sendField(sender, "登录类型", cookieInfo == null ? ChatColor.GRAY + "UNKNOWN" : ChatColor.GREEN + cookieInfo.type().name());
            sendField(sender, "VIP 类型", Objects.requireNonNullElse(loginInfo.getVipType(), indi.etern.musichud.beans.user.VipType.NORMAL));
            if (loginInfo.getProfile() != null) {
                sendField(sender, "账号", loginInfo.getProfile().getNickname() + " (UID: " + loginInfo.getProfile().getUserId() + ")");
            }
            if (cookieInfo != null) {
                sendField(sender, "Cookie 时间", cookieInfo.generateTime());
            }
        }
        sendSectionTitle(sender, "播放统计");
        sendField(sender, "排队歌曲", queuedByPlayer);
        sendField(sender, "空闲播放源", idleSourceCount);
        return true;
    }

    private boolean handleConfig(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/" + COMMAND_NAME + " config <show|set|save|reload> ...");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "show" -> handleConfigShow(sender);
            case "set" -> handleConfigSet(sender, args);
            case "save" -> handleConfigSave(sender);
            case "reload" -> handleConfigReload(sender);
            default -> {
                sendError(sender, "未知 config 子命令: " + args[1]);
                yield true;
            }
        };
    }

    private boolean handleConfigShow(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return true;
        }
        ServerConfigDefinition serverConfig = ServerConfigDefinition.getInstance();
        sendCommandHeader(sender, "当前配置");
        sendField(sender, "serverApiBaseUrl", serverConfig.getServerApiBaseUrl());
        sendField(sender, "startupBinaryApiServerWhenLaunch", formatBooleanValue(serverConfig.getStartupBinaryApiServerWhenLaunch()));
        sendField(sender, "serverApiBinaryExecutablePath", serverConfig.getConfiguredServerApiBinaryExecutablePath());
        sendField(sender, "resolvedServerApiBinaryExecutablePath", serverConfig.getServerApiBinaryExecutablePath());
        sendField(sender, "pusherVoteAdditionalRate", serverConfig.getPusherVoteAdditionalRate());
        sendField(sender, "useRandomCnIp", formatBooleanValue(serverConfig.getUseRandomCnIp()));
        return true;
    }

    private boolean handleConfigSet(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 4) {
            sendUsage(sender, "/" + COMMAND_NAME + " config set <键> <值>");
            return true;
        }
        ServerConfigDefinition serverConfig = ServerConfigDefinition.getInstance();
        String key = args[2];
        String value = joinArgs(args, 3);
        try {
            switch (normalizeConfigKey(key)) {
                case "serverApiBaseUrl" -> serverConfig.setServerApiBaseUrl(value.trim());
                case "startupBinaryApiServerWhenLaunch" -> serverConfig.setStartupBinaryApiServerWhenLaunch(parseBoolean(value));
                case "serverApiBinaryExecutablePath" -> serverConfig.setServerApiBinaryExecutablePath(value.trim());
                case "pusherVoteAdditionalRate" -> serverConfig.setPusherVoteAdditionalRate(Double.parseDouble(value.trim()));
                case "useRandomCnIp" -> serverConfig.setUseRandomCnIp(parseBoolean(value));
                default -> {
                    sendError(sender, "未知配置键: " + key);
                    return true;
                }
            }
        } catch (IllegalArgumentException e) {
            sendError(sender, "配置值无效: " + e.getMessage());
            return true;
        }
        sendSuccess(sender, "已更新内存中的配置。");
        sendField(sender, key, value);
        sendHint(sender, "使用 /" + COMMAND_NAME + " config save 持久化并应用。");
        return true;
    }

    private boolean handleConfigSave(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return true;
        }
        ServerConfigDefinition serverConfig = ServerConfigDefinition.getInstance();
        serverConfig.save();
        syncEmbeddedApiServer();
        sendSuccess(sender, "服务器配置已保存并应用。");
        return true;
    }

    private boolean handleConfigReload(CommandSender sender) {
        if (!requireReload(sender)) {
            return true;
        }
        ServerConfigDefinition serverConfig = ServerConfigDefinition.getInstance();
        serverConfig.reloadFromDisk();
        syncEmbeddedApiServer();
        sendSuccess(sender, "服务器配置已从磁盘重载。");
        return true;
    }

    private boolean handleApi(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            sendUsage(sender, "/" + COMMAND_NAME + " api <status|restart|stop>");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                sendCommandHeader(sender, "API 管理");
                sendField(sender, "API 状态", formatStatusValue(ApiServerManager.getInstance().getBinaryApiServerStatus()));
                yield true;
            }
            case "restart" -> {
                ApiServerManager.getInstance().restartApiServer();
                sendSuccess(sender, "已请求重启内置 API 服务器。");
                yield true;
            }
            case "stop" -> {
                ApiServerManager.getInstance().stopApiServer();
                sendSuccess(sender, "已请求停止内置 API 服务器。");
                yield true;
            }
            default -> {
                sendError(sender, "未知 api 子命令: " + args[1]);
                yield true;
            }
        };
    }

    private boolean handlePlayback(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            sendUsage(sender, "/" + COMMAND_NAME + " playback <skip|queue|idle> ...");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "skip" -> {
                MusicPlayerServerService.getInstance().forceSkipCurrent();
                sendSuccess(sender, "已请求强制切歌。");
                yield true;
            }
            case "queue" -> handlePlaybackQueue(sender, args);
            case "idle" -> handlePlaybackIdle(sender, args);
            default -> {
                sendError(sender, "未知 playback 子命令: " + args[1]);
                yield true;
            }
        };
    }

    private boolean handlePlaybackQueue(CommandSender sender, String[] args) {
        MusicPlayerServerService musicService = MusicPlayerServerService.getInstance();
        List<MusicDetail> queue = new ArrayList<>(musicService.getMusicQueue());
        if (args.length == 2) {
            if (queue.isEmpty()) {
                sendWarning(sender, "当前播放队列为空。");
                return true;
            }
            sendCommandHeader(sender, "播放队列");
            for (int i = 0; i < queue.size(); i++) {
                MusicDetail musicDetail = queue.get(i);
                sendListItem(sender, "#" + i + " " + describeMusic(musicDetail));
            }
            return true;
        }
        if (args.length >= 4 && equalsAny(args[2], "remove")) {
            int index;
            try {
                index = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sendError(sender, "队列索引无效: " + args[3]);
                return true;
            }
            if (index < 0 || index >= queue.size()) {
                sendError(sender, "队列索引超出范围。");
                return true;
            }
            MusicDetail target = queue.get(index);
            musicService.forceRemoveMusicDetailFromQueue(index, target.getId());
            sendSuccess(sender, "已从队列移除歌曲。");
            sendField(sender, "已移除", describeMusic(target));
            return true;
        }
        sendUsage(sender, "/" + COMMAND_NAME + " playback queue [remove <索引>]");
        return true;
    }

    private boolean handlePlaybackIdle(CommandSender sender, String[] args) {
        MusicPlayerServerService musicService = MusicPlayerServerService.getInstance();
        Map<ServerPlayer, Set<IdlePlaySource>> snapshot = new LinkedHashMap<>(musicService.getIdlePlaySourcesSnapshot());
        if (args.length == 2) {
            if (snapshot.isEmpty()) {
                sendWarning(sender, "当前没有空闲播放源。");
                return true;
            }
            sendCommandHeader(sender, "空闲播放源");
            snapshot.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().getName().getString(), String.CASE_INSENSITIVE_ORDER))
                    .forEach(entry -> {
                        for (IdlePlaySource source : entry.getValue()) {
                            String collectionName = source.getMusicCollection() == null ? "<未加载>" : source.getMusicCollection().getName();
                            sendListItem(sender, entry.getKey().getName().getString() + " -> "
                                    + describeCollectionType(source.getType()) + " " + source.getId() + " [" + collectionName + "]");
                        }
                    });
            return true;
        }
        if (args.length >= 6 && equalsAny(args[2], "remove")) {
            Player owner = findOnlinePlayer(args[3]);
            if (owner == null) {
                sendError(sender, "未找到在线玩家: " + args[3]);
                return true;
            }
            Class<?> collectionClass = parseCollectionClass(args[4]);
            if (collectionClass == null) {
                sendError(sender, "无效的播放源类型，支持 playlist 或 album。");
                return true;
            }
            long id;
            try {
                id = Long.parseLong(args[5]);
            } catch (NumberFormatException e) {
                sendError(sender, "播放源 ID 无效: " + args[5]);
                return true;
            }
            musicService.forceRemoveIdlePlaySource(owner.getUniqueId(), id, collectionClass);
            sendSuccess(sender, "已请求移除空闲播放源。");
            sendField(sender, "目标玩家", owner.getName());
            sendField(sender, "播放源类型", describeCollectionType(collectionClass));
            sendField(sender, "播放源 ID", id);
            return true;
        }
        sendUsage(sender, "/" + COMMAND_NAME + " playback idle [remove <玩家> <playlist|album> <ID>]");
        return true;
    }

    private boolean handleTest(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            sendUsage(sender, "/" + COMMAND_NAME + " test <路径或URL> [declaredFormat]");
            return true;
        }
        ParsedTestInput parsedInput;
        try {
            parsedInput = parseTestInput(args, 1);
        } catch (IllegalArgumentException e) {
            sendError(sender, e.getMessage());
            return true;
        }
        sendCommandHeader(sender, "解码探针");
        sendField(sender, "目标", parsedInput.identifier());
        sendField(sender, "声明格式", parsedInput.declaredFormat());
        sendHint(sender, "正在异步探测解码器信息...");
        MusicHud.EXECUTOR.execute(() -> {
            try {
                AudioDecodeProbe probe = AudioDecoderFactory.probe(parsedInput.identifier(), parsedInput.declaredFormat());
                runOnServerThread(() -> {
                    sendSuccess(sender, "解码成功。");
                    sendField(sender, "identifier", probe.identifier());
                    sendField(sender, "declaredFormat", probe.declaredFormat());
                    sendField(sender, "detectedFormat", probe.detectedFormat());
                    sendField(sender, "backend", probe.backend());
                    sendField(sender, "channels", probe.channelCount());
                    sendField(sender, "sampleRate", probe.sampleRate());
                    sendField(sender, "openAlFormat", formatOpenAl(probe.openAlFormat()));
                    sendField(sender, "probeBytesRead", probe.probeBytesRead());
                });
            } catch (IOException e) {
                runOnServerThread(() -> sendError(sender, "解码失败: " + rootMessage(e)));
            } catch (RuntimeException e) {
                runOnServerThread(() -> sendError(sender, "解码失败: " + rootMessage(e)));
            }
        });
        return true;
    }

    private boolean handlePlaytest(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            sendUsage(sender, "/" + COMMAND_NAME + " playtest <路径或URL> [declaredFormat]");
            sendHint(sender, "控制台用法: /" + COMMAND_NAME + " playtest <玩家> <客户端路径或URL> [declaredFormat]");
            sendHint(sender, "停止用法: /" + COMMAND_NAME + " playtest stop [玩家]");
            return true;
        }

        if (equalsAny(args[1], "stop")) {
            Player target = resolvePlaytestTarget(sender, args, 2);
            if (target == null) {
                return true;
            }
            IServerNetworkService.getInstance().sendToPlayer(((CraftPlayer) target).getHandle(),
                    new DebugPlaytestMessage("", FormatType.AUTO, true));
            sendSuccess(sender, "已请求停止客户端调试播放。");
            sendField(sender, "目标玩家", target.getName());
            return true;
        }

        Player target;
        ParsedTestInput parsedInput;
        try {
            if (sender instanceof Player player && looksLikeIdentifierToken(args[1])) {
                target = player;
                parsedInput = parseTestInput(args, 1);
            } else if (sender instanceof Player player && equalsAny(args[1], "self", "@s")) {
                target = player;
                parsedInput = parseTestInput(args, 2);
            } else {
                if (args.length < 3) {
                    sendUsage(sender, "/" + COMMAND_NAME + " playtest <玩家> <客户端路径或URL> [declaredFormat]");
                    return true;
                }
                target = findOnlinePlayer(args[1]);
                if (target == null) {
                    sendError(sender, "未找到在线玩家: " + args[1]);
                    return true;
                }
                parsedInput = parseTestInput(args, 2);
            }
        } catch (IllegalArgumentException e) {
            sendError(sender, e.getMessage());
            return true;
        }

        IServerNetworkService.getInstance().sendToPlayer(((CraftPlayer) target).getHandle(),
                new DebugPlaytestMessage(parsedInput.identifier(), parsedInput.declaredFormat(), false));
        sendSuccess(sender, "已请求客户端调试播放。");
        sendField(sender, "目标玩家", target.getName());
        sendField(sender, "客户端路径或 URL", parsedInput.identifier());
        sendField(sender, "声明格式", parsedInput.declaredFormat());
        return true;
    }

    private void syncEmbeddedApiServer() {
        ServerConfigDefinition serverConfig = ServerConfigDefinition.getInstance();
        boolean currentStartup = serverConfig.getStartupBinaryApiServerWhenLaunch();
        String currentBinaryPath = serverConfig.getServerApiBinaryExecutablePath();
        if (!currentStartup) {
            ApiServerManager.getInstance().stopApiServer();
            refreshAppliedConfigSnapshot();
            return;
        }
        boolean changed = currentStartup != appliedStartupBinary || !Objects.equals(currentBinaryPath, appliedBinaryPath);
        if (changed || ApiServerManager.getInstance().getBinaryApiServerStatus() == ApiServerManager.BinaryApiServerStatus.STOPPED) {
            ApiServerManager.getInstance().restartApiServer();
        }
        refreshAppliedConfigSnapshot();
    }

    private void refreshAppliedConfigSnapshot() {
        ServerConfigDefinition serverConfig = ServerConfigDefinition.getInstance();
        appliedStartupBinary = serverConfig.getStartupBinaryApiServerWhenLaunch();
        appliedBinaryPath = serverConfig.getServerApiBinaryExecutablePath();
    }

    private void runOnServerThread(Runnable runnable) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, runnable);
    }

    private void sendHelp(CommandSender sender) {
        String aliasText = COMMAND_ALIASES.stream()
                .map(alias -> "/" + alias)
                .collect(Collectors.joining(ChatColor.DARK_GRAY + " | " + ChatColor.WHITE));

        sendRaw(sender, ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "--------------------" + ChatColor.RESET
                + ChatColor.GOLD + " MusicHud " + ChatColor.RESET
                + ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "--------------------");
        sendRaw(sender, ChatColor.YELLOW + "主命令 " + ChatColor.DARK_GRAY + "» " + ChatColor.WHITE + "/" + COMMAND_NAME
                + ChatColor.GRAY + "    别名 " + ChatColor.DARK_GRAY + "» " + ChatColor.WHITE + aliasText);
        sendRaw(sender, ChatColor.GRAY + "输入 /" + COMMAND_NAME + " help 可再次查看此帮助。");
        sendRaw(sender, "");

        sendRaw(sender, ChatColor.AQUA + "基础");
        sendRaw(sender, ChatColor.WHITE + "/" + COMMAND_NAME + " status" + ChatColor.GRAY + " - 查看服务端与播放状态");
        sendRaw(sender, ChatColor.WHITE + "/" + COMMAND_NAME + " player <玩家名>" + ChatColor.GRAY + " - 查看玩家登录与排队信息");
        sendRaw(sender, "");

        sendRaw(sender, ChatColor.AQUA + "配置");
        sendRaw(sender, ChatColor.WHITE + "/" + COMMAND_NAME + " config show" + ChatColor.GRAY + " - 查看当前配置");
        sendRaw(sender, ChatColor.WHITE + "/" + COMMAND_NAME + " config set <键> <值>" + ChatColor.GRAY + " - 修改内存中的配置");
        sendRaw(sender, ChatColor.WHITE + "/" + COMMAND_NAME + " config save" + ChatColor.GRAY + " - 保存配置并应用");
        sendRaw(sender, ChatColor.WHITE + "/" + COMMAND_NAME + " config reload" + ChatColor.GRAY + " - 从磁盘重载配置");
        sendRaw(sender, "");

        sendRaw(sender, ChatColor.AQUA + "API");
        sendRaw(sender, ChatColor.WHITE + "/" + COMMAND_NAME + " api <status|restart|stop>" + ChatColor.GRAY + " - 管理内置 API 进程");
        sendRaw(sender, "");

        sendRaw(sender, ChatColor.AQUA + "播放");
        sendRaw(sender, ChatColor.WHITE + "/" + COMMAND_NAME + " playback skip" + ChatColor.GRAY + " - 强制切歌");
        sendRaw(sender, ChatColor.WHITE + "/" + COMMAND_NAME + " playback queue [remove <索引>]" + ChatColor.GRAY + " - 查看或移除队列");
        sendRaw(sender, ChatColor.WHITE + "/" + COMMAND_NAME + " playback idle [remove <玩家> <playlist|album> <ID>]" + ChatColor.GRAY + " - 管理空闲播放源");
        sendRaw(sender, "");

        sendRaw(sender, ChatColor.AQUA + "测试");
        sendRaw(sender, ChatColor.WHITE + "/" + COMMAND_NAME + " test <服务器路径或URL> [declaredFormat]" + ChatColor.GRAY + " - 只探测解码器信息");
        sendRaw(sender, ChatColor.WHITE + "/" + COMMAND_NAME + " playtest <客户端路径或URL> [declaredFormat]" + ChatColor.GRAY + " - 对自己发起调试播放");
        sendRaw(sender, ChatColor.WHITE + "/" + COMMAND_NAME + " playtest <玩家> <客户端路径或URL> [declaredFormat]" + ChatColor.GRAY + " - 对指定玩家发起调试播放");
        sendRaw(sender, ChatColor.WHITE + "/" + COMMAND_NAME + " playtest stop [玩家]" + ChatColor.GRAY + " - 停止调试播放");
        sendRaw(sender, ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "------------------------------------------------");
    }

    private void sendCommandHeader(CommandSender sender, String title) {
        sendRaw(sender, ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "----------------" + ChatColor.RESET
                + ChatColor.GOLD + " " + title + " " + ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "----------------");
    }

    private void sendSectionTitle(CommandSender sender, String title) {
        sendRaw(sender, ChatColor.AQUA + title);
    }

    private void sendField(CommandSender sender, String key, Object value) {
        sendRaw(sender, PREFIX + ChatColor.YELLOW + key + ChatColor.DARK_GRAY + " » " + ChatColor.WHITE + Objects.toString(value));
    }

    private void sendListItem(CommandSender sender, String value) {
        sendRaw(sender, PREFIX + ChatColor.GRAY + "- " + ChatColor.WHITE + value);
    }

    private void sendSuccess(CommandSender sender, String message) {
        sendRaw(sender, PREFIX + ChatColor.GREEN + message);
    }

    private void sendWarning(CommandSender sender, String message) {
        sendRaw(sender, PREFIX + ChatColor.YELLOW + message);
    }

    private void sendError(CommandSender sender, String message) {
        sendRaw(sender, PREFIX + ChatColor.RED + message);
    }

    private void sendHint(CommandSender sender, String message) {
        sendRaw(sender, PREFIX + ChatColor.GRAY + message);
    }

    private void sendUsage(CommandSender sender, String usage) {
        sendRaw(sender, PREFIX + ChatColor.YELLOW + "用法: " + ChatColor.WHITE + usage);
    }

    private boolean requireAdmin(CommandSender sender) {
        if (hasPermission(sender, ADMIN_PERMISSION)) {
            return true;
        }
        sendError(sender, "你没有权限执行这个命令，需要权限: " + ADMIN_PERMISSION);
        return false;
    }

    private boolean requireReload(CommandSender sender) {
        if (hasPermission(sender, ADMIN_PERMISSION) || hasPermission(sender, RELOAD_PERMISSION)) {
            return true;
        }
        sendError(sender, "你没有权限执行这个命令，需要权限: " + RELOAD_PERMISSION);
        return false;
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        return !(sender instanceof Player player) || player.hasPermission(permission);
    }

    private void send(CommandSender sender, String message) {
        sendRaw(sender, PREFIX + ChatColor.WHITE + message);
    }

    private void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(message);
    }

    private static String joinArgs(String[] args, int startInclusive) {
        return String.join(" ", Arrays.copyOfRange(args, startInclusive, args.length));
    }

    private static boolean equalsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> filterSuggestions(Collection<String> suggestions, String token) {
        String lowered = token.toLowerCase(Locale.ROOT);
        return suggestions.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(lowered))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static @Nullable Player findOnlinePlayer(String name) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private static String describeMusic(MusicDetail musicDetail) {
        if (musicDetail == null || musicDetail.equals(MusicDetail.NONE)) {
            return "<none>";
        }
        String name = musicDetail.getName() == null || musicDetail.getName().isBlank() ? "<unnamed>" : musicDetail.getName();
        String by = musicDetail.getPusherInfo() == null || musicDetail.getPusherInfo().equals(indi.etern.musichud.beans.music.PusherInfo.EMPTY)
                ? "unknown"
                : musicDetail.getPusherInfo().getPlayerName();
        return name + " (ID: " + musicDetail.getId() + ", by: " + by + ")";
    }

    private static String describeCollectionType(Class<?> type) {
        if (type == Playlist.class) {
            return "playlist";
        }
        if (type == Album.class) {
            return "album";
        }
        return type == null ? "unknown" : type.getSimpleName();
    }

    private static @Nullable Class<?> parseCollectionClass(String input) {
        if (equalsAny(input, "playlist", "playlists")) {
            return Playlist.class;
        }
        if (equalsAny(input, "album", "albums")) {
            return Album.class;
        }
        return null;
    }

    private static String normalizeConfigKey(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "serverapibaseurl", "apiurl", "url" -> "serverApiBaseUrl";
            case "startupbinaryapiserverwhenlaunch", "startupbinary", "autostart" -> "startupBinaryApiServerWhenLaunch";
            case "serverapibinaryexecutablepath", "binarypath", "binary" -> "serverApiBinaryExecutablePath";
            case "pushervoteadditionalrate", "pushervoterate", "voterate" -> "pusherVoteAdditionalRate";
            case "userandomcnip", "randomcnip" -> "useRandomCnIp";
            default -> input;
        };
    }

    private static boolean isBooleanConfigKey(String input) {
        String normalized = normalizeConfigKey(input);
        return "startupBinaryApiServerWhenLaunch".equals(normalized) || "useRandomCnIp".equals(normalized);
    }

    private static boolean parseBoolean(String input) {
        return switch (input.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> throw new IllegalArgumentException("布尔值应为 true/false、yes/no、on/off");
        };
    }

    private static String formatBooleanValue(boolean value) {
        return (value ? ChatColor.GREEN : ChatColor.RED) + Boolean.toString(value);
    }

    private static String formatStatusValue(@Nullable Object value) {
        if (value == null) {
            return ChatColor.GRAY + "<unknown>";
        }
        String text = Objects.toString(value);
        String lowered = text.toLowerCase(Locale.ROOT);
        if (lowered.contains("run") || lowered.contains("start") || lowered.contains("online")) {
            return ChatColor.GREEN + text;
        }
        if (lowered.contains("stop") || lowered.contains("fail") || lowered.contains("error") || lowered.contains("offline")) {
            return ChatColor.RED + text;
        }
        if (lowered.contains("restart") || lowered.contains("boot") || lowered.contains("load") || lowered.contains("wait")) {
            return ChatColor.YELLOW + text;
        }
        return ChatColor.WHITE + text;
    }

    private static ParsedTestInput parseTestInput(String[] args, int startInclusive) {
        int tokenCount = args.length - startInclusive;
        if (tokenCount <= 0) {
            throw new IllegalArgumentException("缺少路径或 URL");
        }
        if (tokenCount == 1) {
            return new ParsedTestInput(args[startInclusive], FormatType.AUTO);
        }
        FormatType candidate = FormatType.fromSerializedName(args[args.length - 1]);
        boolean explicitFormat = candidate != FormatType.GENERIC
                || equalsAny(args[args.length - 1], "generic", "auto");
        if (explicitFormat) {
            return new ParsedTestInput(joinArgs(args, startInclusive, args.length - 1), candidate);
        }
        return new ParsedTestInput(joinArgs(args, startInclusive, args.length), FormatType.AUTO);
    }

    private @Nullable Player resolvePlaytestTarget(CommandSender sender, String[] args, int playerIndex) {
        if (args.length > playerIndex) {
            Player target = findOnlinePlayer(args[playerIndex]);
            if (target == null) {
                sendError(sender, "未找到在线玩家: " + args[playerIndex]);
            }
            return target;
        }
        if (sender instanceof Player player) {
            return player;
        }
        sendError(sender, "控制台需要显式指定玩家。");
        return null;
    }

    private static String joinArgs(String[] args, int startInclusive, int endExclusive) {
        return String.join(" ", Arrays.copyOfRange(args, startInclusive, endExclusive));
    }

    private static boolean looksLikeIdentifierToken(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return token.contains(":")
                || token.contains("\\")
                || token.contains("/")
                || token.startsWith(".")
                || lower.endsWith(".mp3")
                || lower.endsWith(".flac")
                || lower.endsWith(".wav")
                || lower.endsWith(".ogg")
                || lower.endsWith(".opus")
                || lower.endsWith(".aac")
                || lower.endsWith(".m4a")
                || lower.endsWith(".mp4")
                || lower.endsWith(".aiff")
                || lower.endsWith(".aif")
                || lower.endsWith(".au");
    }

    private static String formatOpenAl(int format) {
        return switch (format) {
            case 0x1100 -> "AL_FORMAT_MONO8";
            case 0x1101 -> "AL_FORMAT_MONO16";
            case 0x1102 -> "AL_FORMAT_STEREO8";
            case 0x1103 -> "AL_FORMAT_STEREO16";
            default -> Integer.toString(format);
        };
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getClass().getSimpleName() + ": " + current.getMessage();
    }

    private record ParsedTestInput(String identifier, FormatType declaredFormat) {
    }
}
