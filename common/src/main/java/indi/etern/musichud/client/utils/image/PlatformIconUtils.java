package indi.etern.musichud.client.utils.image;

import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.BitmapFactory;
import icyllis.modernui.graphics.Image;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
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

    public static TuneWeavePlatform platform(MusicDetail detail) {
        if (detail == null || detail == MusicDetail.NONE) return null;
        TuneWeavePlatform fromReference = fromReference(detail.getSourceRef());
        if (fromReference != null) return fromReference;
        return "video".equals(detail.getSourceKind()) ? TuneWeavePlatform.BILIBILI : null;
    }

    private static TuneWeavePlatform fromReference(String reference) {
        if (reference == null) return null;
        int separator = reference.indexOf(':');
        if (separator <= 0) return null;
        String prefix = reference.substring(0, separator).trim().toLowerCase(java.util.Locale.ROOT);
        return switch (prefix) {
            case "netease", "163" -> TuneWeavePlatform.NETEASE;
            case "qq", "tencent" -> TuneWeavePlatform.QQ;
            case "bilibili", "bili" -> TuneWeavePlatform.BILIBILI;
            default -> null;
        };
    }
}
