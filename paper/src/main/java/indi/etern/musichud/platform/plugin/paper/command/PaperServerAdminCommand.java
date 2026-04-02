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
import indi.etern.musichud.client.music.decoder.AudioDecodeProbe;
import indi.etern.musichud.client.music.decoder.AudioDecoderFactory;
import indi.etern.musichud.platform.plugin.paper.config.ServerConfigDefinition;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ApiServerManager;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import indi.etern.musichud.server.api.impl.ncm.LoginApiService;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
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

public final class PaperServerAdminCommand implements CommandExecutor, TabCompleter {
    private static final String COMMAND_NAME = "musichudserver";
    private static final String ADMIN_PERMISSION = "musichud.admin";
    private static final String RELOAD_PERMISSION = "musichud.reload";
    private static final List<String> ROOT_SUBCOMMANDS = List.of("help", "status", "player", "config", "api", "playback", "test");
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
        PluginCommand command = Objects.requireNonNull(plugin.getCommand(COMMAND_NAME), "Command not declared: " + COMMAND_NAME);
        command.setExecutor(this);
        command.setTabCompleter(this);
        refreshAppliedConfigSnapshot();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || equalsAny(args[0], "help", "?")) {
            sendHelp(sender, label);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> handleStatus(sender);
            case "player" -> handlePlayer(sender, args);
            case "config" -> handleConfig(sender, args);
            case "api" -> handleApi(sender, args);
            case "playback" -> handlePlayback(sender, args);
            case "test" -> handleTest(sender, args);
            default -> {
                send(sender, "未知子命令，使用 /" + label + " help 查看帮助。");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filterSuggestions(ROOT_SUBCOMMANDS, args[0]);
        }

        String root = args[0].toLowerCase(Locale.ROOT);
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

        return List.of();
    }

    private boolean handleStatus(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return true;
        }
        ServerConfigDefinition serverConfig = ServerConfigDefinition.getInstance();
        MusicPlayerServerService musicService = MusicPlayerServerService.getInstance();
        Map<ServerPlayer, LoginApiService.PlayerLoginInfo> loginInfoMap = ILoginApiService.getInstance(ApiProvider.NCM).getLoginedPlayerInfoMap();
        Map<ServerPlayer, Set<IdlePlaySource>> idleSources = musicService.getIdlePlaySourcesSnapshot();
        long loggedAccountCount = loginInfoMap.values().stream()
                .filter(info -> info != null && info.getLoginCookieInfo() != null && info.getLoginCookieInfo().type() != null)
                .filter(info -> info.getLoginCookieInfo().type() != indi.etern.musichud.beans.login.LoginType.UNLOGGED)
                .count();
        int idleSourceCount = idleSources.values().stream().mapToInt(Set::size).sum();
        send(sender, "MusicHud " + Version.current);
        send(sender, "API 状态: " + ApiServerManager.getBinaryApiServerStatus());
        send(sender, "API 地址: " + serverConfig.getServerApiBaseUrl());
        send(sender, "内置 API 开机启动: " + serverConfig.getStartupBinaryApiServerWhenLaunch());
        send(sender, "在线玩家: " + Bukkit.getOnlinePlayers().size() + "，已追踪登录状态: " + loginInfoMap.size() + "，有账号信息: " + loggedAccountCount);
        send(sender, "当前播放: " + describeMusic(musicService.getCurrentMusicDetail()));
        send(sender, "下一首空闲播放: " + describeMusic(musicService.getNextIdleMusicDetail()));
        send(sender, "队列数量: " + musicService.getMusicQueue().size() + "，空闲播放拥有者: " + idleSources.size() + "，空闲播放源: " + idleSourceCount);
        return true;
    }

    private boolean handlePlayer(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            send(sender, "用法: /" + COMMAND_NAME + " player <玩家名>");
            return true;
        }
        Player player = findOnlinePlayer(args[1]);
        if (player == null) {
            send(sender, "未找到在线玩家: " + args[1]);
            return true;
        }
        ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        LoginApiService.PlayerLoginInfo loginInfo = ILoginApiService.getInstance(ApiProvider.NCM).getLoginInfoByServerPlayer(serverPlayer);
        long queuedByPlayer = MusicPlayerServerService.getInstance().getMusicQueue().stream()
                .filter(musicDetail -> musicDetail.getPusherInfo() != null && player.getUniqueId().equals(musicDetail.getPusherInfo().playerUUID()))
                .count();
        int idleSourceCount = MusicPlayerServerService.getInstance().getIdlePlaySourcesSnapshot()
                .getOrDefault(serverPlayer, Set.of())
                .size();

        send(sender, "玩家: " + player.getName());
        send(sender, "UUID: " + player.getUniqueId());
        if (loginInfo == null) {
            send(sender, "登录状态: 未追踪");
        } else {
            LoginCookieInfo cookieInfo = loginInfo.getLoginCookieInfo();
            send(sender, "登录类型: " + (cookieInfo == null ? "UNKNOWN" : cookieInfo.type()));
            send(sender, "VIP 类型: " + Objects.requireNonNullElse(loginInfo.getVipType(), indi.etern.musichud.beans.user.VipType.NORMAL));
            if (loginInfo.getProfile() != null) {
                send(sender, "账号: " + loginInfo.getProfile().getNickname() + " (UID: " + loginInfo.getProfile().getUserId() + ")");
            }
            if (cookieInfo != null) {
                send(sender, "Cookie 时间: " + cookieInfo.generateTime());
            }
        }
        send(sender, "排队歌曲: " + queuedByPlayer + "，空闲播放源: " + idleSourceCount);
        return true;
    }

    private boolean handleConfig(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "用法: /" + COMMAND_NAME + " config <show|set|save|reload> ...");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "show" -> handleConfigShow(sender);
            case "set" -> handleConfigSet(sender, args);
            case "save" -> handleConfigSave(sender);
            case "reload" -> handleConfigReload(sender);
            default -> {
                send(sender, "未知 config 子命令: " + args[1]);
                yield true;
            }
        };
    }

    private boolean handleConfigShow(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return true;
        }
        ServerConfigDefinition serverConfig = ServerConfigDefinition.getInstance();
        send(sender, "serverApiBaseUrl = " + serverConfig.getServerApiBaseUrl());
        send(sender, "startupBinaryApiServerWhenLaunch = " + serverConfig.getStartupBinaryApiServerWhenLaunch());
        send(sender, "serverApiBinaryExecutablePath = " + serverConfig.getConfiguredServerApiBinaryExecutablePath());
        send(sender, "resolvedServerApiBinaryExecutablePath = " + serverConfig.getServerApiBinaryExecutablePath());
        send(sender, "pusherVoteAdditionalRate = " + serverConfig.getPusherVoteAdditionalRate());
        send(sender, "useRandomCnIp = " + serverConfig.getUseRandomCnIp());
        return true;
    }

    private boolean handleConfigSet(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 4) {
            send(sender, "用法: /" + COMMAND_NAME + " config set <键> <值>");
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
                    send(sender, "未知配置键: " + key);
                    return true;
                }
            }
        } catch (IllegalArgumentException e) {
            send(sender, "配置值无效: " + e.getMessage());
            return true;
        }
        send(sender, "已更新内存中的配置 " + key + " = " + value + "，使用 /" + COMMAND_NAME + " config save 持久化并应用。");
        return true;
    }

    private boolean handleConfigSave(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return true;
        }
        ServerConfigDefinition serverConfig = ServerConfigDefinition.getInstance();
        serverConfig.save();
        syncEmbeddedApiServer();
        send(sender, "服务器配置已保存。");
        return true;
    }

    private boolean handleConfigReload(CommandSender sender) {
        if (!requireReload(sender)) {
            return true;
        }
        ServerConfigDefinition serverConfig = ServerConfigDefinition.getInstance();
        serverConfig.reloadFromDisk();
        syncEmbeddedApiServer();
        send(sender, "服务器配置已从磁盘重载。");
        return true;
    }

    private boolean handleApi(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            send(sender, "用法: /" + COMMAND_NAME + " api <status|restart|stop>");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                send(sender, "API 状态: " + ApiServerManager.getBinaryApiServerStatus());
                yield true;
            }
            case "restart" -> {
                ApiServerManager.restartApiServer();
                send(sender, "已请求重启内置 API 服务器。");
                yield true;
            }
            case "stop" -> {
                ApiServerManager.stopApiServer();
                send(sender, "已请求停止内置 API 服务器。");
                yield true;
            }
            default -> {
                send(sender, "未知 api 子命令: " + args[1]);
                yield true;
            }
        };
    }

    private boolean handlePlayback(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            send(sender, "用法: /" + COMMAND_NAME + " playback <skip|queue|idle> ...");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "skip" -> {
                MusicPlayerServerService.getInstance().forceSkipCurrent();
                send(sender, "已请求强制切歌。");
                yield true;
            }
            case "queue" -> handlePlaybackQueue(sender, args);
            case "idle" -> handlePlaybackIdle(sender, args);
            default -> {
                send(sender, "未知 playback 子命令: " + args[1]);
                yield true;
            }
        };
    }

    private boolean handlePlaybackQueue(CommandSender sender, String[] args) {
        MusicPlayerServerService musicService = MusicPlayerServerService.getInstance();
        List<MusicDetail> queue = new ArrayList<>(musicService.getMusicQueue());
        if (args.length == 2) {
            if (queue.isEmpty()) {
                send(sender, "当前播放队列为空。");
                return true;
            }
            send(sender, "当前播放队列:");
            for (int i = 0; i < queue.size(); i++) {
                MusicDetail musicDetail = queue.get(i);
                send(sender, "#" + i + " " + describeMusic(musicDetail));
            }
            return true;
        }
        if (args.length >= 4 && equalsAny(args[2], "remove")) {
            int index;
            try {
                index = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                send(sender, "队列索引无效: " + args[3]);
                return true;
            }
            if (index < 0 || index >= queue.size()) {
                send(sender, "队列索引超出范围。");
                return true;
            }
            MusicDetail target = queue.get(index);
            musicService.forceRemoveMusicDetailFromQueue(index, target.getId());
            send(sender, "已从队列移除: " + describeMusic(target));
            return true;
        }
        send(sender, "用法: /" + COMMAND_NAME + " playback queue [remove <索引>]");
        return true;
    }

    private boolean handlePlaybackIdle(CommandSender sender, String[] args) {
        MusicPlayerServerService musicService = MusicPlayerServerService.getInstance();
        Map<ServerPlayer, Set<IdlePlaySource>> snapshot = new LinkedHashMap<>(musicService.getIdlePlaySourcesSnapshot());
        if (args.length == 2) {
            if (snapshot.isEmpty()) {
                send(sender, "当前没有空闲播放源。");
                return true;
            }
            send(sender, "当前空闲播放源:");
            snapshot.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().getName().getString(), String.CASE_INSENSITIVE_ORDER))
                    .forEach(entry -> {
                        for (IdlePlaySource source : entry.getValue()) {
                            String collectionName = source.getMusicCollection() == null ? "<未加载>" : source.getMusicCollection().getName();
                            send(sender, entry.getKey().getName().getString() + " -> " + describeCollectionType(source.getType()) + " " + source.getId() + " [" + collectionName + "]");
                        }
                    });
            return true;
        }
        if (args.length >= 6 && equalsAny(args[2], "remove")) {
            Player owner = findOnlinePlayer(args[3]);
            if (owner == null) {
                send(sender, "未找到在线玩家: " + args[3]);
                return true;
            }
            Class<?> collectionClass = parseCollectionClass(args[4]);
            if (collectionClass == null) {
                send(sender, "无效的播放源类型，支持 playlist 或 album。");
                return true;
            }
            long id;
            try {
                id = Long.parseLong(args[5]);
            } catch (NumberFormatException e) {
                send(sender, "播放源 ID 无效: " + args[5]);
                return true;
            }
            musicService.forceRemoveIdlePlaySource(owner.getUniqueId(), id, collectionClass);
            send(sender, "已请求移除空闲播放源: " + owner.getName() + " / " + describeCollectionType(collectionClass) + " / " + id);
            return true;
        }
        send(sender, "用法: /" + COMMAND_NAME + " playback idle [remove <玩家> <playlist|album> <ID>]");
        return true;
    }

    private boolean handleTest(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            send(sender, "用法: /" + COMMAND_NAME + " test <路径或URL> [declaredFormat]");
            return true;
        }
        ParsedTestInput parsedInput = parseTestInput(args);
        send(sender, "开始测试解码器: " + parsedInput.identifier());
        MusicHud.EXECUTOR.execute(() -> {
            try {
                AudioDecodeProbe probe = AudioDecoderFactory.probe(parsedInput.identifier(), parsedInput.declaredFormat());
                runOnServerThread(() -> {
                    send(sender, "解码成功。");
                    send(sender, "identifier = " + probe.identifier());
                    send(sender, "declaredFormat = " + probe.declaredFormat());
                    send(sender, "detectedFormat = " + probe.detectedFormat());
                    send(sender, "backend = " + probe.backend());
                    send(sender, "channels = " + probe.channelCount());
                    send(sender, "sampleRate = " + probe.sampleRate());
                    send(sender, "openAlFormat = " + formatOpenAl(probe.openAlFormat()));
                    send(sender, "probeBytesRead = " + probe.probeBytesRead());
                });
            } catch (IOException e) {
                runOnServerThread(() -> send(sender, "解码失败: " + rootMessage(e)));
            } catch (RuntimeException e) {
                runOnServerThread(() -> send(sender, "解码失败: " + rootMessage(e)));
            }
        });
        return true;
    }

    private void syncEmbeddedApiServer() {
        ServerConfigDefinition serverConfig = ServerConfigDefinition.getInstance();
        boolean currentStartup = serverConfig.getStartupBinaryApiServerWhenLaunch();
        String currentBinaryPath = serverConfig.getServerApiBinaryExecutablePath();
        if (!currentStartup) {
            ApiServerManager.stopApiServer();
            refreshAppliedConfigSnapshot();
            return;
        }
        boolean changed = currentStartup != appliedStartupBinary || !Objects.equals(currentBinaryPath, appliedBinaryPath);
        if (changed || ApiServerManager.getBinaryApiServerStatus() == ApiServerManager.BinaryApiServerStatus.STOPPED) {
            ApiServerManager.restartApiServer();
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

    private void sendHelp(CommandSender sender, String label) {
        send(sender, "/" + label + " status");
        send(sender, "/" + label + " player <玩家名>");
        send(sender, "/" + label + " config show");
        send(sender, "/" + label + " config set <键> <值>");
        send(sender, "/" + label + " config save");
        send(sender, "/" + label + " config reload");
        send(sender, "/" + label + " api <status|restart|stop>");
        send(sender, "/" + label + " playback skip");
        send(sender, "/" + label + " playback queue [remove <索引>]");
        send(sender, "/" + label + " playback idle [remove <玩家> <playlist|album> <ID>]");
        send(sender, "/" + label + " test <路径或URL> [declaredFormat]");
    }

    private boolean requireAdmin(CommandSender sender) {
        if (hasPermission(sender, ADMIN_PERMISSION)) {
            return true;
        }
        send(sender, "你没有权限执行这个命令，需要权限: " + ADMIN_PERMISSION);
        return false;
    }

    private boolean requireReload(CommandSender sender) {
        if (hasPermission(sender, ADMIN_PERMISSION) || hasPermission(sender, RELOAD_PERMISSION)) {
            return true;
        }
        send(sender, "你没有权限执行这个命令，需要权限: " + RELOAD_PERMISSION);
        return false;
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        return !(sender instanceof Player player) || player.hasPermission(permission);
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage("[MusicHud] " + message);
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
                : musicDetail.getPusherInfo().playerName();
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

    private static ParsedTestInput parseTestInput(String[] args) {
        if (args.length == 2) {
            return new ParsedTestInput(args[1], FormatType.AUTO);
        }
        FormatType candidate = FormatType.fromSerializedName(args[args.length - 1]);
        boolean explicitFormat = candidate != FormatType.GENERIC
                || equalsAny(args[args.length - 1], "generic", "auto");
        if (explicitFormat) {
            return new ParsedTestInput(joinArgs(args, 1, args.length - 1), candidate);
        }
        return new ParsedTestInput(joinArgs(args, 1, args.length), FormatType.AUTO);
    }

    private static String joinArgs(String[] args, int startInclusive, int endExclusive) {
        return String.join(" ", Arrays.copyOfRange(args, startInclusive, endExclusive));
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
