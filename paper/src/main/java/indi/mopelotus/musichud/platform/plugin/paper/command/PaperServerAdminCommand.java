package indi.mopelotus.musichud.platform.plugin.paper.command;

import indi.mopelotus.musichud.MusicHud;
import indi.mopelotus.musichud.Version;
import indi.mopelotus.musichud.beans.music.MusicDetail;
import indi.mopelotus.musichud.beans.music.PlaybackSession;
import indi.mopelotus.musichud.server.api.ApiServerManager;
import indi.mopelotus.musichud.server.api.MusicPlayerServerService;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.PluginCommand;

import java.util.List;
import java.util.Collection;
import java.util.Locale;

/** Styled Paper admin command for the TuneWeave public playback server. */
public final class PaperServerAdminCommand implements CommandExecutor, TabCompleter {
    private static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "MusicHud TuneWeave" + ChatColor.DARK_GRAY + "] ";
    private static final List<String> ROOT = List.of("help", "status", "api", "playback");

    public void register(org.bukkit.plugin.java.JavaPlugin plugin) {
        PluginCommand command = plugin.getCommand("musichud");
        if (command == null) throw new IllegalStateException("musichud command is missing from plugin.yml");
        command.setExecutor(this); command.setTabCompleter(this);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return handleCommand(sender, label, args);
    }

    private boolean handleCommand(CommandSender sender, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help", "?" -> help(sender, label);
            case "status" -> status(sender);
            case "api" -> api(sender, args);
            case "playback" -> playback(sender, args);
            default -> { error(sender, "未知子命令: " + args[0]); hint(sender, "使用 /" + label + " help 查看帮助。"); }
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        raw(sender, ChatColor.GOLD + "━━━━━━━━ MusicHud TuneWeave ━━━━━━━━");
        raw(sender, ChatColor.AQUA + "基础");
        raw(sender, ChatColor.WHITE + "/" + label + " status" + ChatColor.GRAY + "  查看服务端、公共播放和队列状态");
        raw(sender, ChatColor.WHITE + "/" + label + " api status" + ChatColor.GRAY + "  查看内置 TuneWeave API");
        raw(sender, ChatColor.WHITE + "/" + label + " playback skip" + ChatColor.GRAY + "  管理员强制切歌");
        raw(sender, ChatColor.GRAY + "音乐平台账号与凭据由客户端持有；服务端不会显示 Cookie 或 token。");
        raw(sender, ChatColor.DARK_GRAY + "别名: /musichud /musichud-tuneweave");
    }

    private void status(CommandSender sender) {
        MusicPlayerServerService service = MusicPlayerServerService.getInstance();
        PlaybackSession session = service.getCurrentPlaybackSession();
        MusicDetail detail = session == null ? MusicDetail.NONE : session.musicDetail();
        header(sender, "服务端状态");
        field(sender, "版本", Version.CURRENT);
        field(sender, "API 状态", apiStatus());
        field(sender, "在线公共会话", session != null && session.isActive() ? "运行中" : "空闲");
        field(sender, "当前播放", detail == null || detail == MusicDetail.NONE ? "无" : detail.getName());
        field(sender, "队列数量", service.getMusicQueue().size());
        field(sender, "账号状态", "网易云 / QQ / Bilibili 由客户端持有");
    }

    private void api(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("status")) {
            hint(sender, "用法: /musichud api status"); return;
        }
        header(sender, "API 管理");
        field(sender, "状态", apiStatus());
    }

    private void playback(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("skip")) {
            hint(sender, "用法: /musichud playback skip"); return;
        }
        MusicPlayerServerService.getInstance().forceSkipCurrent();
        success(sender, "已请求管理员强制切歌。");
    }

    private String apiStatus() {
        ApiServerManager manager = ApiServerManager.getInstance();
        return manager == null ? "不可用" : manager.getBinaryApiServerStatus().name();
    }

    private void header(CommandSender sender, String title) { raw(sender, ChatColor.GOLD + "━━━━━━━━ " + title + " ━━━━━━━━"); }
    private void field(CommandSender sender, String key, Object value) { raw(sender, PREFIX + ChatColor.YELLOW + key + ChatColor.DARK_GRAY + " » " + ChatColor.WHITE + value); }
    private void success(CommandSender sender, String value) { raw(sender, PREFIX + ChatColor.GREEN + value); }
    private void error(CommandSender sender, String value) { raw(sender, PREFIX + ChatColor.RED + value); }
    private void hint(CommandSender sender, String value) { raw(sender, PREFIX + ChatColor.GRAY + value); }
    private void raw(CommandSender sender, String value) { sender.sendMessage(value); }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return ROOT.stream().filter(v -> v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("api")) return List.of("status");
        if (args.length == 2 && args[0].equalsIgnoreCase("playback")) return List.of("skip");
        return List.of();
    }
}
