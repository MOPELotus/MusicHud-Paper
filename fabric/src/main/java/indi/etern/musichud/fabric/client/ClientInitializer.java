package indi.etern.musichud.fabric.client;

import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.utils.RegistrationManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

public final class ClientInitializer implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Some client-only registrations, especially key mappings, are more reliable
        // when retried from the dedicated Fabric client entrypoint.
        RegistrationManager.performClientRegistration();
        ModConfigEvents.loading(MusicHud.MOD_ID).register((config) -> {
            HudRendererManager hudRendererManager = HudRendererManager.getInstance();

            HudElementRegistry.addFirst(
                    MusicHud.location("main_hud"),
                    hudRendererManager::renderFrame
            );
        });
    }
}
