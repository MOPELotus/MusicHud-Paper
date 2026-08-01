package indi.etern.musichud.client.ui.pages.search;

import icyllis.modernui.core.Context;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.Toast;
import com.google.gson.JsonObject;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.services.UniPlaylistClient;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.components.MusicListItem;
import indi.etern.musichud.client.ui.utils.ui.ButtonInsetBackgroundFactory;
import lombok.Getter;
import net.minecraft.client.resources.language.I18n;

import java.util.List;
import java.util.stream.Collectors;

public class SearchMusicResultView extends LinearLayout {
    @Getter
    private static SearchMusicResultView instance;
    private static List<MusicDetail> result;

    public SearchMusicResultView(Context context) {
        super(context);
        instance = this;
        setOrientation(LinearLayout.VERTICAL);
        refresh();
    }

    public static void setResult(List<MusicDetail> result) {
        SearchMusicResultView.result = result;
        if (instance != null) {
            instance.refresh();
        }
    }

    public void refresh() {
        removeAllViews();
        if (result != null) {
            for (MusicDetail musicDetail : result) {
                addItem(getContext(), musicDetail);
            }
        }
    }

    public void append(List<MusicDetail> musicDetails) {
        result.addAll(musicDetails);
        for (MusicDetail musicDetail : musicDetails) {
            addItem(getContext(), musicDetail);
        }
    }

    private void addItem(Context context, MusicDetail musicDetail) {
        var musicLayout = new MusicListItem(context);
        musicLayout.bindData(musicDetail);
        var background = ButtonInsetBackgroundFactory.builder()
                .cornerRadius(dp(12))
                .inset(dp(1))
                .padding(new ButtonInsetBackgroundFactory.Padding(dp(4), dp(4), dp(4), dp(4))).build().newBackgroundDrawable();
        musicLayout.setBackground(background);

        musicLayout.setClickable(true);
        String artistsName = musicDetail.getArtists().stream()
                .map(Artist::getName).collect(Collectors.joining(" / "));
        musicLayout.setOnClickListener((view) -> {
            MusicService.getInstance().sendPushMusicToQueue(musicDetail);
            ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.pushedMusicToPlaylist") + "\n" + musicDetail.getName() + " - " + artistsName, Toast.LENGTH_SHORT));
        });
        musicLayout.addAction("actions/playlist_plus", "添加到聚合歌单", view ->
                UniPlaylistClient.showTrackPicker(context, musicDetail,
                        () -> ToastUtil.show(Toast.makeText(context, "已加入聚合歌单：" + musicDetail.getName(), Toast.LENGTH_SHORT)),
                        error -> ToastUtil.show(Toast.makeText(context, error, Toast.LENGTH_SHORT))));
        musicLayout.addAction("actions/heart_outline", "收藏歌曲", view -> favorite(musicDetail, context));
        addView(musicLayout, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private static void favorite(MusicDetail music, Context context) {
        if (music.getSourceRef().isBlank()) {
            ToastUtil.show(Toast.makeText(context, "该歌曲不能收藏", Toast.LENGTH_SHORT));
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("ref", music.getSourceRef());
        indi.etern.musichud.client.services.TuneWeaveUiService.request("favorite-track-add", request,
                ignored -> ToastUtil.show(Toast.makeText(context, "已收藏：" + music.getName(), Toast.LENGTH_SHORT)),
                error -> ToastUtil.show(Toast.makeText(context, error, Toast.LENGTH_SHORT)));
    }
}
