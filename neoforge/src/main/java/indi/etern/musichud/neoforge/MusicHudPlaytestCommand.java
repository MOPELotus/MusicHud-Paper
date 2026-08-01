package indi.etern.musichud.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import indi.etern.musichud.beans.music.FormatType;
import indi.etern.musichud.client.network.vanilla.VanillaPlayerProxy;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.pushMessages.s2c.DebugPlaytestMessage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class MusicHudPlaytestCommand {
    private MusicHudPlaytestCommand() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("musichud")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("playtest")
                        .then(Commands.literal("stop").executes(context -> stop(context.getSource())))
                        .then(Commands.argument("identifier", StringArgumentType.greedyString())
                                .executes(context -> play(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "identifier")
                                )))));
    }

    private static int play(CommandSourceStack source, String identifier) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only a player can receive a local MusicHud playtest."));
            return 0;
        }
        IServerNetworkService.getInstance().sendToPlayer(
                VanillaPlayerProxy.ofPlayer(player),
                new DebugPlaytestMessage(identifier, FormatType.AUTO, false)
        );
        source.sendSuccess(() -> Component.literal("Requested MusicHud playtest playback."), false);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only a player can receive a local MusicHud playtest."));
            return 0;
        }
        IServerNetworkService.getInstance().sendToPlayer(
                VanillaPlayerProxy.ofPlayer(player),
                new DebugPlaytestMessage("", FormatType.AUTO, true)
        );
        source.sendSuccess(() -> Component.literal("Stopped MusicHud playtest playback."), false);
        return 1;
    }
}
