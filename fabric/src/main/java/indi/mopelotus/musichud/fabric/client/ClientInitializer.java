package indi.mopelotus.musichud.fabric.client;

import indi.mopelotus.musichud.MusicHud;
import indi.mopelotus.musichud.client.ui.hud.HudRendererManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import indi.mopelotus.musichud.client.commands.TuneWeaveClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class ClientInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudRendererManager hudRendererManager = HudRendererManager.getInstance();
        HudElementRegistry.addFirst(
                Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "main_hud"),
                hudRendererManager::renderFrame
        );
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
                TuneWeaveClientCommands.registerFabric(dispatcher, (source, message) ->
                        ((FabricClientCommandSource) source).sendFeedback(message)));
    }
}

