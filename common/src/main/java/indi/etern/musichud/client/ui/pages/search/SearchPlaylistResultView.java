package indi.etern.musichud.client.ui.pages.search;

import icyllis.modernui.core.Context;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.client.ui.components.AutoFlowGridLayout;
import indi.etern.musichud.client.ui.components.MusicCollectionCard;
import indi.etern.musichud.client.services.UniPlaylistClient;
import indi.etern.musichud.client.ui.ToastUtil;
import icyllis.modernui.widget.Toast;
import lombok.Getter;

import java.util.List;

public class SearchPlaylistResultView extends AutoFlowGridLayout {
    @Getter
    private static SearchPlaylistResultView instance;
    private static List<Playlist> result;

    public SearchPlaylistResultView(Context context) {
        super(context);
        instance = this;
        setRowMinWidth(dp(143));
        refresh();
    }

    public static void setResult(List<Playlist> result) {
        SearchPlaylistResultView.result = result;
        if (instance != null) {
            instance.refresh();
        }
    }

    public void refresh() {
        removeAllViews();
        if (result != null) {
            for (Playlist playlist : result) {
                addItem(getContext(), playlist);
            }
        }
    }

    public void append(List<Playlist> playlists) {
        result.addAll(playlists);
        for (Playlist playlist : playlists) {
            addItem(getContext(), playlist);
        }
    }

    private void addItem(Context context, Playlist playlist) {
        MusicCollectionCard child = new MusicCollectionCard(context, playlist);
        child.setOnLongClickListener(view -> {
            UniPlaylistClient.importPlaylist(playlist,
                    () -> ToastUtil.show(Toast.makeText(context, "已导入到聚合歌单：" + playlist.getName(), Toast.LENGTH_SHORT)),
                    error -> ToastUtil.show(Toast.makeText(context, error, Toast.LENGTH_SHORT)));
            return true;
        });
        addView(child);
    }
}
