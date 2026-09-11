package indi.mopelotus.musichud.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import indi.mopelotus.musichud.MusicHud;
import indi.mopelotus.musichud.beans.music.MusicDetail;
import indi.mopelotus.musichud.client.audio.NowPlayingInfo;
import indi.mopelotus.musichud.client.services.music.MusicService;
import indi.mopelotus.musichud.client.services.tuneweave.TuneWeaveClientService;
import indi.mopelotus.musichud.server.api.ApiServerManager;
import indi.mopelotus.musichud.server.api.tuneweave.TuneWeavePlatform;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Client-local diagnostics and playback controls. */
public final class TuneWeaveClientCommands {
    private TuneWeaveClientCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String alias : new String[]{"tuneweave", "mt", "musichud-tuneweave", "musichud"}) {
            dispatcher.register(root(Commands.literal(alias)));
        }
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root) {
        return root
                .then(Commands.literal("help").executes(c -> send(c.getSource(), help())))
                .then(Commands.literal("status").executes(c -> send(c.getSource(), status())))
                .then(Commands.literal("api").then(Commands.literal("status").executes(c -> send(c.getSource(), field("API 状态", apiStatus())))))
                .then(Commands.literal("me").executes(c -> send(c.getSource(), accounts())))
                .then(Commands.literal("account").executes(c -> send(c.getSource(), accounts())))
                .then(Commands.literal("skip").executes(c -> skip(c.getSource())))
                .then(Commands.literal("force-skip").executes(c -> skip(c.getSource())))
                .executes(c -> send(c.getSource(), help()));
    }

    public static <S> void registerFabric(CommandDispatcher<S> dispatcher, java.util.function.BiConsumer<S, Component> feedback) {
        for (String alias : new String[]{"tuneweave", "mt", "musichud-tuneweave", "musichud"}) {
            dispatcher.register(fabricRoot(com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal(alias), feedback));
        }
    }

    private static <S> com.mojang.brigadier.builder.LiteralArgumentBuilder<S> fabricRoot(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<S> root,
            java.util.function.BiConsumer<S, Component> feedback) {
        return root
                .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("help").executes(c -> send(feedback, c.getSource(), help())))
                .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("status").executes(c -> send(feedback, c.getSource(), status())))
                .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("api").then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("status").executes(c -> send(feedback, c.getSource(), field("API 状态", apiStatus())))))
                .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("me").executes(c -> send(feedback, c.getSource(), accounts())))
                .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("account").executes(c -> send(feedback, c.getSource(), accounts())))
                .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("skip").executes(c -> { MusicService.getInstance().voteForSkipCurrent(); send(feedback, c.getSource(), Component.translatable(MusicHud.MOD_ID + ".command.skip.sent").withStyle(ChatFormatting.GREEN)); return 1; }))
                .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("force-skip").executes(c -> { MusicService.getInstance().voteForSkipCurrent(); send(feedback, c.getSource(), Component.translatable(MusicHud.MOD_ID + ".command.skip.sent").withStyle(ChatFormatting.GREEN)); return 1; }))
                .executes(c -> send(feedback, c.getSource(), help()));
    }

    private static int send(CommandSourceStack source, Component message) { source.sendSuccess(() -> message, false); return 1; }
    private static <S> int send(java.util.function.BiConsumer<S, Component> feedback, S source, Component message) { feedback.accept(source, message); return 1; }

    private static Component help() {
        return Component.literal("━━━━━━━━ MusicHud TuneWeave ━━━━━━━━\n").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("基础\n").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("/mt status").withStyle(ChatFormatting.WHITE)).append(Component.literal("  查看 API、播放和三平台账号状态\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("/mt api status").withStyle(ChatFormatting.WHITE)).append(Component.literal("  查看 TuneWeave API\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("/mt me").withStyle(ChatFormatting.WHITE)).append(Component.literal("  查看三平台账号状态\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("/mt skip").withStyle(ChatFormatting.WHITE)).append(Component.literal("  强制/投票切歌\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("别名: /mt /musichud-tuneweave /musichud /tuneweave").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent field(String key, String value) {
        return Component.literal("[MusicHud] ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(key).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    private static MutableComponent accounts() {
        MutableComponent result = Component.literal("──── 账号状态 ────\n").withStyle(ChatFormatting.AQUA);
        TuneWeaveClientService service = TuneWeaveClientService.getInstance();
        for (TuneWeavePlatform platform : TuneWeavePlatform.values()) {
            boolean loggedIn = service.hasCredential(platform);
            result = result.append(field(platform.apiName(), loggedIn ? "已登录" : "未登录").withStyle(loggedIn ? ChatFormatting.GREEN : ChatFormatting.RED)).append("\n");
        }
        return result;
    }

    private static MutableComponent status() {
        MusicDetail detail = NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail();
        String title = detail == null || detail == MusicDetail.NONE ? "无" : detail.getName();
        return Component.literal("━━━━━━━━ MusicHud TuneWeave 状态 ━━━━━━━━\n").withStyle(ChatFormatting.GOLD)
                .append(field("API", apiStatus())).append("\n")
                .append(field("当前播放", title)).append("\n")
                .append(field("队列", String.valueOf(MusicService.getInstance().getMusicQueue().size()))).append("\n")
                .append(accounts());
    }

    private static String apiStatus() {
        ApiServerManager manager = ApiServerManager.getInstance();
        return manager == null ? "不可用" : manager.getBinaryApiServerStatus().name();
    }

    private static int skip(CommandSourceStack source) {
        MusicService.getInstance().voteForSkipCurrent();
        return send(source, Component.translatable(MusicHud.MOD_ID + ".command.skip.sent").withStyle(ChatFormatting.GREEN));
    }
}
