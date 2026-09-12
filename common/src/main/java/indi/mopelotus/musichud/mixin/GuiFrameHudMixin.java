package indi.mopelotus.musichud.mixin;

import indi.mopelotus.musichud.client.ui.hud.HudRendererManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(Gui.class)
public abstract class GuiFrameHudMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void musichud_tuneweave$renderFrameFallback(DeltaTracker deltaTracker, boolean shouldRenderLevel,
                                                          boolean resourcesLoaded, CallbackInfo ci,
                                                          ProfilerFiller profiler, int mouseX, int mouseY,
                                                          GuiGraphicsExtractor graphics) {
        graphics.nextStratum();
        HudRendererManager.getInstance().renderFrame(graphics, deltaTracker);
    }
}
