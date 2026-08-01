package indi.etern.musichud.client.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import icyllis.modernui.core.Context;
import indi.etern.musichud.client.ui.components.UniPlaylistPickerModal;

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
        addTrack(activePlaylistRef, music, success, failure);
    }

    public static void showTrackPicker(Context context, MusicDetail music, Runnable success, Consumer<String> failure) {
        UniPlaylistPickerModal.showTrackPicker(context, music, success, failure);
    }

    public static void showReferencePicker(Context context, String reference, String kind, Runnable success, Consumer<String> failure) {
        UniPlaylistPickerModal.showReferencePicker(context, reference, kind, success, failure);
    }

    public static void showImportConfirmation(Context context, Playlist playlist, Runnable success, Consumer<String> failure) {
        UniPlaylistPickerModal.showImportConfirmation(context, playlist, success, failure);
    }

    public static void showImportConfirmation(Context context, String reference, String name, Runnable success, Consumer<String> failure) {
        UniPlaylistPickerModal.showImportConfirmation(context, reference, name, success, failure);
    }

    public static void addTrack(String playlistRef, MusicDetail music, Runnable success, Consumer<String> failure) {
        if (playlistRef == null || playlistRef.isBlank()) {
            failure.accept("没有选择聚合歌单");
            return;
        }
        if (music == null || music.getSourceRef().isBlank()) {
            failure.accept("该歌曲没有可用的 TuneWeave 资源引用");
            return;
        }
        addReference(playlistRef, music.getSourceRef(), "video".equals(music.getSourceKind()) ? "video" : "track", success, failure);
    }

    public static void addReference(String playlistRef, String reference, String kind, Runnable success, Consumer<String> failure) {
        if (playlistRef == null || playlistRef.isBlank()) {
            failure.accept("没有选择聚合歌单");
            return;
        }
        if (reference == null || reference.isBlank()) {
            failure.accept("该内容没有可加入聚合歌单的平台资源引用");
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("ref", playlistRef);
        JsonObject body = new JsonObject();
        JsonArray items = new JsonArray();
        JsonObject item = new JsonObject();
        item.addProperty("ref", reference);
        item.addProperty("kind", "video".equals(kind) ? "video" : "track");
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
        importPlaylist(playlist.getSourceRef(), playlist.getName(), success, failure);
    }

    public static void importPlaylist(String reference, String name, Runnable success, Consumer<String> failure) {
        String[] parts = reference.split(":", 2);
        if (parts.length != 2) {
            failure.accept("歌单引用格式无效");
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("name", name);
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
