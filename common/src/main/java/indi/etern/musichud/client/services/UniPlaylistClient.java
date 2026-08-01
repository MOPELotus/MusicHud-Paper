package indi.etern.musichud.client.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;

import java.util.function.Consumer;

/** Shared selected destination for adding results from search and collection views. */
public final class UniPlaylistClient {
    private static volatile String activePlaylistRef = "";

    private UniPlaylistClient() {
    }

    public static String getActivePlaylistRef() {
        return activePlaylistRef;
    }

    public static void setActivePlaylistRef(String ref) {
        activePlaylistRef = ref == null ? "" : ref;
    }

    public static void addTrack(MusicDetail music, Runnable success, Consumer<String> failure) {
        if (activePlaylistRef.isBlank()) {
            failure.accept("请先在“聚合歌单”页面选择目标歌单");
            return;
        }
        if (music == null || music.getSourceRef().isBlank()) {
            failure.accept("该歌曲没有可用的 TuneWeave 资源引用");
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("ref", activePlaylistRef);
        JsonObject body = new JsonObject();
        JsonArray items = new JsonArray();
        JsonObject item = new JsonObject();
        item.addProperty("ref", music.getSourceRef());
        item.addProperty("kind", "video".equals(music.getSourceKind()) ? "video" : "track");
        items.add(item);
        body.add("items", items);
        request.add("body", body);
        TuneWeaveUiService.request("uni-add", request, ignored -> success.run(), failure);
    }

    public static void importPlaylist(Playlist playlist, Runnable success, Consumer<String> failure) {
        if (playlist == null || playlist.getSourceRef().isBlank()) {
            failure.accept("该歌单没有可导入的平台资源引用");
            return;
        }
        String[] parts = playlist.getSourceRef().split(":", 2);
        if (parts.length != 2) {
            failure.accept("歌单引用格式无效");
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("name", playlist.getName());
        JsonArray sources = new JsonArray();
        JsonObject source = new JsonObject();
        source.addProperty("platform", parts[0]);
        source.addProperty("type", "playlist");
        source.addProperty("id", parts[1]);
        sources.add(source);
        request.add("sources", sources);
        TuneWeaveUiService.request("uni-import", request, ignored -> success.run(), failure);
    }
}
