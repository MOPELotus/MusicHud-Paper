package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.CheckableImageButton;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ImageView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.utils.image.PlatformIconUtils;
import indi.etern.musichud.client.ui.utils.ui.ButtonInsetBackgroundFactory;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import net.minecraft.client.resources.language.I18n;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Compact branded platform switcher used wherever a TuneWeave route is selected. */
public final class PlatformSelector extends LinearLayout {
    private final Map<TuneWeavePlatform, CheckableImageButton> buttons = new EnumMap<>(TuneWeavePlatform.class);
    private TuneWeavePlatform selected;
    private Consumer<TuneWeavePlatform> listener;

    public PlatformSelector(Context context, TuneWeavePlatform... platforms) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        for (TuneWeavePlatform platform : platforms) {
            CheckableImageButton button = new CheckableImageButton(context);
            button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            Image image = PlatformIconUtils.image(platform);
            if (image != null) {
                button.setImageDrawable(new InsetDrawable(
                        new indi.etern.musichud.client.ui.drawable.ScaledImageDrawable(
                                context.getResources(), image, dp(18), dp(22)), dp(3)));
            }
            button.setTooltipText(I18n.get(MusicHud.MOD_ID + ".platform." + platform.apiName()));
            button.setBackground(ButtonInsetBackgroundFactory.builder()
                    .padding(new ButtonInsetBackgroundFactory.Padding(dp(4), dp(4), dp(4), dp(4)))
                    .cornerRadius(dp(4)).inset(dp(1)).build().newBackgroundDrawable());
            button.setOnClickListener(view -> select(platform, true));
            buttons.put(platform, button);
            LayoutParams params = new LayoutParams(dp(36), dp(32));
            params.setMargins(dp(2), 0, dp(2), 0);
            addView(button, params);
        }
        if (platforms.length > 0) {
            select(platforms[0], false);
        }
    }

    public TuneWeavePlatform getSelectedPlatform() {
        return selected;
    }

    public void setSelectedPlatform(TuneWeavePlatform platform) {
        if (platform != null && buttons.containsKey(platform)) {
            select(platform, false);
        }
    }

    public void setOnPlatformSelectedListener(Consumer<TuneWeavePlatform> listener) {
        this.listener = listener;
    }

    private void select(TuneWeavePlatform platform, boolean notify) {
        if (!buttons.containsKey(platform)) return;
        selected = Objects.requireNonNull(platform);
        buttons.forEach((key, button) -> button.setChecked(key == platform));
        if (notify && listener != null) listener.accept(platform);
    }
}
