package indi.mopelotus.musichud.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import indi.mopelotus.musichud.beans.music.FormatType;
import indi.mopelotus.musichud.client.network.vanilla.VanillaPlayerProxy;
import indi.mopelotus.musichud.network.IServerNetworkService;
import indi.mopelotus.musichud.network.payloads.pushMessages.s2c.DebugPlaytestMessage;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class MusicHudPlaytestCommand {
    private MusicHudPlaytestCommand() {
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("musichud-tuneweave")
                        .then(Commands.literal("playtest")
                                .then(Commands.literal("stop").executes(context -> stop(context.getSource())))
                                .then(Commands.argument("identifier", StringArgumentType.greedyString())
                                        .executes(context -> play(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "identifier")
                                        )))
        )));
    }

    private static int play(CommandSourceStack source, String identifier) {
        source.sendFailure(Component.translatable("musichud_tuneweave.command.playtest.localOnly"));
        return 0;
    }

    private static int stop(CommandSourceStack source) {
        return play(source, "");
    }
}
