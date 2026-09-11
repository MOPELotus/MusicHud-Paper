package indi.mopelotus.musichud.fabric.client;

import indi.mopelotus.musichud.MusicHud;
import indi.mopelotus.musichud.client.ui.hud.HudRendererManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public final class ClientInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudRendererManager hudRendererManager = HudRendererManager.getInstance();
        HudElementRegistry.addFirst(
                Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "main_hud"),
                hudRendererManager::renderFrame
        );
    }
}

