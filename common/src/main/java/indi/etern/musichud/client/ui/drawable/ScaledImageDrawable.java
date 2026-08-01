package indi.etern.musichud.client.ui.drawable;

import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.resources.Resources;

/** Keeps vector-derived PNG icons visually aligned with the surrounding text. */
public class ScaledImageDrawable extends ImageDrawable {
    private final int targetHeight;

    public ScaledImageDrawable(Resources resources, Image image, int targetHeight) {
        super(resources, image);
        this.targetHeight = targetHeight;
    }

    @Override
    public int getIntrinsicWidth() {
        int width = super.getIntrinsicWidth();
        int height = super.getIntrinsicHeight();
        return targetHeight > 0 && width > 0 && height > 0
                ? Math.round((float) targetHeight * width / height) : width;
    }

    @Override
    public int getIntrinsicHeight() {
        return targetHeight > 0 ? targetHeight : super.getIntrinsicHeight();
    }
}
