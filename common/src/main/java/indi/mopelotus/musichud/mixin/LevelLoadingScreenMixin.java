package indi.mopelotus.musichud.mixin;

import indi.mopelotus.musichud.client.ui.hud.HudRendererManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public class LevelLoadingScreenMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void musichud_tuneweave$renderHud(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                               float partialTick, CallbackInfo ci) {
        graphics.nextStratum();
        HudRendererManager.getInstance().renderFrame(graphics, null);
    }
}
