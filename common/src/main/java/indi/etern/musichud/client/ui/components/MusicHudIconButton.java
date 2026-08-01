package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.widget.ImageButton;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackgroundFactory;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;

/** Compact icon-only control used for track and collection actions. */
public final class MusicHudIconButton extends ImageButton {
    public MusicHudIconButton(Context context, String iconName, String description) {
        super(context);
        setScaleType(ScaleType.CENTER);
        setContentDescription(description);
        setTooltipText(description);
        setBackground(ButtonInsetBackgroundFactory.builder()
                .cornerRadius(dp(6)).inset(dp(1))
                .padding(new ButtonInsetBackgroundFactory.Padding(dp(6), dp(6), dp(6), dp(6)))
                .build().newBackgroundDrawable());
        setIcon(iconName);
    }

    public void setIcon(String iconName) {
        Image icon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/" + iconName + ".png");
        if (icon != null) {
            setImageDrawable(new ScaledImageDrawable(getContext().getResources(), icon, dp(18)));
        }
    }
}
