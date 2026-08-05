package indi.etern.musichud.client.ui.components;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.CheckableImageButton;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ImageView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.utils.image.PlatformIconUtils;
import indi.etern.musichud.client.ui.utils.ui.ButtonInsetBackgroundFactory;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import net.minecraft.client.resources.language.I18n;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Compact branded platform switcher used wherever a TuneWeave route is selected. */
public final class PlatformSelector extends LinearLayout {
    private static final ColorStateList SEGMENT_STATES = new ColorStateList(
            new int[][]{
                    new int[]{R.attr.state_checked},
                    new int[]{R.attr.state_pressed},
                    new int[]{R.attr.state_hovered},
                    StateSet.WILD_CARD
            },
            new int[]{
                    0x35E0BFB7,
                    0x18FFFFFF,
                    0x10FFFFFF,
                    0x00000000
            }
    );

    private final Map<TuneWeavePlatform, CheckableImageButton> buttons = new EnumMap<>(TuneWeavePlatform.class);
    private TuneWeavePlatform selected;
    private Consumer<TuneWeavePlatform> listener;

    public PlatformSelector(Context context, TuneWeavePlatform... platforms) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(2), dp(2), dp(2), dp(2));
        setBackground(ButtonInsetBackgroundFactory.builder()
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .cornerRadius(dp(6)).inset(dp(1)).build().newBackgroundDrawable());
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
                    .backgroundColor(SEGMENT_STATES)
                    .padding(new ButtonInsetBackgroundFactory.Padding(dp(4), dp(4), dp(4), dp(4)))
                    .cornerRadius(dp(4)).inset(0).build().newBackgroundDrawable());
            button.setOnClickListener(view -> select(platform, true));
            button.setContentDescription(I18n.get(MusicHud.MOD_ID + ".platform." + platform.apiName()));
            buttons.put(platform, button);
            LayoutParams params = new LayoutParams(dp(38), dp(30));
            params.setMargins(dp(1), 0, dp(1), 0);
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
