package indi.etern.musichud.client.ui.hud.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

/** Draws the current TuneWeave source mark in the HUD title row. */
public final class PlatformIconRenderer implements HudRenderer {
    private static final float RASTER_SIZE = 256f;
    private static final Map<TuneWeavePlatform, Identifier> TEXTURES = new EnumMap<>(TuneWeavePlatform.class);
    private static final EnumSet<TuneWeavePlatform> FAILED = EnumSet.noneOf(TuneWeavePlatform.class);
    private Layout layout;
    private volatile TuneWeavePlatform platform;

    public void configure(Layout layout) {
        this.layout = layout;
    }

    public void setPlatform(TuneWeavePlatform platform) {
        this.platform = platform;
    }

    @Override
    public void render(HudRenderContext context) {
        if (layout == null || platform == null) return;
        Identifier texture = texture(platform);
        if (texture == null) return;
        Layout.AbsolutePosition position = layout.calcAbsolutePosition(context);
        int x = (int) position.x();
        int y = (int) position.y();
        int width = Math.max(1, (int) layout.getWidth());
        int height = Math.max(1, (int) layout.getHeight());
        context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0,
                width, height, width, height);
    }

    private static synchronized Identifier texture(TuneWeavePlatform platform) {
        Identifier cached = TEXTURES.get(platform);
        if (cached != null) return cached;
        if (FAILED.contains(platform)) return null;
        String path = "/assets/music_hud/textures/platforms/" + platform.apiName() + ".svg";
        try (InputStream input = MusicHud.class.getResourceAsStream(path)) {
            if (input == null) return null;
            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, RASTER_SIZE);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, RASTER_SIZE);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            transcoder.transcode(new TranscoderInput(input), new TranscoderOutput(output));
            NativeImage image = NativeImage.read(new ByteArrayInputStream(output.toByteArray()));
            DynamicTexture texture = new DynamicTexture(() -> "musichud_platform_" + platform.apiName(), image);
            Identifier id = Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "platform/" + platform.apiName());
            Minecraft.getInstance().getTextureManager().register(id, texture);
            TEXTURES.put(platform, id);
            return id;
        } catch (Exception error) {
            FAILED.add(platform);
            MusicHud.getLogger(PlatformIconRenderer.class).warn("Failed to load HUD platform icon {}", platform.apiName(), error);
            return null;
        }
    }
}
