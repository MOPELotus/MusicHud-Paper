package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import indi.etern.musichud.interfaces.Unregister;
import net.minecraft.client.resources.language.I18n;

public final class ToggleFavoriteIntelligenceButton extends ToggleIconButton {
    private final MusicService musicService = MusicService.getInstance();
    private Playlist playlist;
    private Unregister unregister;

    public ToggleFavoriteIntelligenceButton(Context context) {
        super(context, new Appearance(
                () -> I18n.get(MusicHud.MOD_ID + ".button.disableFavoriteIntelligence"),
                () -> I18n.get(MusicHud.MOD_ID + ".button.enableFavoriteIntelligence"),
                () -> ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/heart_filled.png"),
                () -> ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/heart.png")
        ));
    }

    public void bindPlaylist(Playlist playlist) {
        this.playlist = playlist;
        setChecked(musicService.isFavoriteIntelligenceEnabled(playlist));
        if (unregister != null) unregister.unregister();
        unregister = musicService.onFavoriteIntelligenceStateChange(enabled ->
                MuiModApi.postToUiThread(() -> setChecked(
                        enabled && musicService.isFavoriteIntelligenceEnabled(playlist))));
    }

    @Override
    public boolean performClick() {
        boolean handled = super.performClick();
        Playlist bound = playlist;
        if (bound == null) return handled;
        boolean requested = isChecked();
        setEnabled(false);
        musicService.setFavoriteIntelligenceEnabled(bound, requested).whenComplete((enabled, error) ->
                MuiModApi.postToUiThread(() -> {
                    setEnabled(true);
                    setChecked(error == null && Boolean.TRUE.equals(enabled));
                    if (error != null) {
                        ToastUtil.show(I18n.get(MusicHud.MOD_ID + ".text.favoriteIntelligenceError"));
                    }
                }));
        return handled;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (unregister != null) {
            unregister.unregister();
            unregister = null;
        }
    }
}
