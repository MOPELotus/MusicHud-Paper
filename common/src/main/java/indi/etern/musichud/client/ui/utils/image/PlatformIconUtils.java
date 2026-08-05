package indi.etern.musichud.client.ui.utils.image;

import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.BitmapFactory;
import icyllis.modernui.graphics.Image;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/** Loads the small branded SVGs as ModernUI textures once per client session. */
public final class PlatformIconUtils {
    private static final Map<TuneWeavePlatform, Image> CACHE = new EnumMap<>(TuneWeavePlatform.class);

    private PlatformIconUtils() {
    }

    public static synchronized Image image(TuneWeavePlatform platform) {
        Image cached = CACHE.get(platform);
        if (cached != null) return cached;
        String path = "/assets/music_hud/textures/platforms/" + platform.apiName() + ".svg";
        try (InputStream input = MusicHud.class.getResourceAsStream(path)) {
            if (input == null) return null;
            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, 48f);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, 48f);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            transcoder.transcode(new TranscoderInput(input), new TranscoderOutput(output));
            try (Bitmap bitmap = BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size())) {
                Image result = Image.createTextureFromBitmap(bitmap);
                CACHE.put(platform, result);
                return result;
            }
        } catch (Exception error) {
            MusicHud.getLogger(PlatformIconUtils.class).warn("Failed to load {} platform icon", platform.apiName(), error);
            return null;
        }
    }
}
