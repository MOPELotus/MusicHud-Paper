package indi.etern.musichud.client.ui.pages;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.client.services.TuneWeaveUiService;
import indi.etern.musichud.client.services.UniPlaylistClient;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackgroundFactory;

import java.util.ArrayList;
import java.util.List;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/** TuneWeave server-owned Uni Playlist UI, named 聚合歌单 in Chinese. */
public class UniPlaylistView extends LinearLayout {
    private final LinearLayout content;
    private String selectedRef = "";
    private String selectedName = "";

    public UniPlaylistView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        addView(scrollView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setPadding(dp(32), dp(24), dp(32), dp(48));
        scrollView.addView(content, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        refresh();
    }

    public final void refresh() {
        selectedRef = "";
        selectedName = "";
        content.removeAllViews();
        addHeading("聚合歌单");
        addDescription("将网易云、QQ、B 站等平台的可播放内容按一个顺序保存；选择目标后，可在搜索结果或现有歌单中长按添加歌曲。\nB 站条目只会解析为音频播放。");

        LinearLayout createRow = row();
        EditText name = new EditText(getContext());
        name.setHint("新建聚合歌单名称");
        createRow.addView(name, new LayoutParams(0, WRAP_CONTENT, 1));
        Button create = button("新建");
        create.setOnClickListener(view -> {
            JsonObject request = new JsonObject();
            request.addProperty("name", name.getText().toString());
            TuneWeaveUiService.request("uni-create", request, ignored -> refresh(), this::showError);
        });
        createRow.addView(create, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        content.addView(createRow, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(16), 0, 0));

        addHeading("导入平台歌单");
        LinearLayout importRow = row();
        EditText platform = new EditText(getContext());
        platform.setHint("平台（netease / qq / bilibili）");
        importRow.addView(platform, new LayoutParams(0, WRAP_CONTENT, 1));
        EditText type = new EditText(getContext());
        type.setHint("类型（playlist 等）");
        importRow.addView(type, new LayoutParams(0, WRAP_CONTENT, 1));
        EditText id = new EditText(getContext());
        id.setHint("ID");
        importRow.addView(id, new LayoutParams(0, WRAP_CONTENT, 1));
        Button importButton = button("导入");
        importButton.setOnClickListener(view -> {
            JsonObject source = new JsonObject();
            source.addProperty("platform", platform.getText().toString());
            source.addProperty("type", type.getText().toString());
            source.addProperty("id", id.getText().toString());
            JsonArray sources = new JsonArray();
            sources.add(source);
            JsonObject request = new JsonObject();
            request.addProperty("name", "导入：" + id.getText());
            request.add("sources", sources);
            TuneWeaveUiService.request("uni-import", request, ignored -> refresh(), this::showError);
        });
        importRow.addView(importButton, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        content.addView(importRow, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(8), 0, 0));

        TextView loading = new TextView(getContext());
        loading.setText("正在读取聚合歌单…");
        loading.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        content.addView(loading, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), 0, dp(24), 0, 0));
        TuneWeaveUiService.request("uni-list", new JsonObject(), data -> {
            content.removeView(loading);
            List<JsonObject> playlists = objects(data);
            if (playlists.isEmpty()) {
                addDescription("还没有聚合歌单。可新建空歌单，或导入现有的平台歌单。");
                return;
            }
            for (JsonObject playlist : playlists) {
                addPlaylistCard(playlist);
            }
        }, error -> {
            loading.setText(error);
            loading.setTextColor(Theme.ERROR_TEXT_COLOR);
        });
    }

    private void showDetail(JsonObject playlist) {
        selectedRef = string(playlist, "ref");
        selectedName = string(playlist, "name");
        UniPlaylistClient.setActivePlaylistRef(selectedRef);
        content.removeAllViews();
        addHeading(selectedName);
        addDescription("当前目标已选中：搜索结果和现有歌单中的歌曲可长按加入这里。");

        LinearLayout editRow = row();
        EditText rename = new EditText(getContext());
        rename.setText(selectedName);
        rename.setHint("歌单名称");
        editRow.addView(rename, new LayoutParams(0, WRAP_CONTENT, 1));
        Button saveName = button("重命名");
        saveName.setOnClickListener(view -> updateName(rename.getText().toString()));
        editRow.addView(saveName, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        Button delete = button("删除");
        delete.setOnClickListener(view -> deletePlaylist());
        editRow.addView(delete, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), dp(8), 0, 0, 0));
        content.addView(editRow, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(12), 0, 0));

        LinearLayout addRow = row();
        EditText ref = new EditText(getContext());
        ref.setHint("直接添加资源引用，例如 qq:0039MnYb0qxYhV");
        addRow.addView(ref, new LayoutParams(0, WRAP_CONTENT, 1));
        Button add = button("添加");
        add.setOnClickListener(view -> addReference(ref.getText().toString()));
        addRow.addView(add, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        content.addView(addRow, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(12), 0, 0));

        Button back = button("返回歌单列表");
        back.setOnClickListener(view -> refresh());
        content.addView(back, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), 0, dp(12), 0, 0));

        TextView loading = new TextView(getContext());
        loading.setText("正在读取歌曲…");
        loading.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        content.addView(loading, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), 0, dp(24), 0, 0));
        JsonObject request = new JsonObject();
        request.addProperty("ref", selectedRef);
        TuneWeaveUiService.request("uni-items", request, data -> {
            content.removeView(loading);
            List<JsonObject> items = objects(data);
            for (int index = 0; index < items.size(); index++) {
                JsonObject item = items.get(index);
                JsonObject snapshot = object(item.get("snapshot"));
                LinearLayout itemRow = row();
                TextView text = new TextView(getContext());
                text.setText(string(snapshot, "title") + "\n" + string(item, "source_ref"));
                text.setTextSize(Theme.TEXT_SIZE_NORMAL);
                text.setTextColor(Theme.NORMAL_TEXT_COLOR);
                String sourceRef = string(item, "source_ref");
                String itemId = string(item, "id");
                if (!sourceRef.isBlank() && !itemId.isBlank()) {
                    text.setClickable(true);
                    text.setOnClickListener(view -> {
                        JsonObject queue = new JsonObject();
                        queue.addProperty("ref", selectedRef);
                        queue.addProperty("item_id", itemId);
                        TuneWeaveUiService.request("queue-uni-item", queue,
                                ignored -> ToastUtil.show("已加入播放队列"), this::showError);
                    });
                }
                itemRow.addView(text, new LayoutParams(0, WRAP_CONTENT, 1));
                Button up = button("↑");
                up.setEnabled(index > 0);
                int currentIndex = index;
                up.setOnClickListener(view -> reorder(items, currentIndex, currentIndex - 1));
                itemRow.addView(up, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), dp(8), 0, 0, 0));
                Button down = button("↓");
                down.setEnabled(index < items.size() - 1);
                down.setOnClickListener(view -> reorder(items, currentIndex, currentIndex + 1));
                itemRow.addView(down, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), dp(4), 0, 0, 0));
                Button remove = button("移除");
                remove.setOnClickListener(view -> removeItem(itemId));
                itemRow.addView(remove, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), dp(4), 0, 0, 0));
                content.addView(itemRow, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(8), 0, 0));
            }
        }, error -> {
            loading.setText(error);
            loading.setTextColor(Theme.ERROR_TEXT_COLOR);
        });
    }

    private void addReference(String reference) {
        if (reference == null || reference.isBlank()) {
            showError("请输入资源引用");
            return;
        }
        JsonObject item = new JsonObject();
        item.addProperty("ref", reference.trim());
        item.addProperty("kind", reference.startsWith("bilibili:bvid:") ? "video" : "track");
        JsonArray items = new JsonArray();
        items.add(item);
        JsonObject body = new JsonObject();
        body.add("items", items);
        JsonObject request = new JsonObject();
        request.addProperty("ref", selectedRef);
        request.add("body", body);
        TuneWeaveUiService.request("uni-add", request, ignored -> {
            JsonObject playlist = new JsonObject();
            playlist.addProperty("ref", selectedRef);
            playlist.addProperty("name", selectedName);
            showDetail(playlist);
        }, this::showError);
    }

    private void updateName(String name) {
        if (name == null || name.isBlank()) {
            showError("歌单名称不能为空");
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("ref", selectedRef);
        request.addProperty("name", name.trim());
        TuneWeaveUiService.request("uni-update", request, ignored -> {
            selectedName = name.trim();
            JsonObject playlist = new JsonObject();
            playlist.addProperty("ref", selectedRef);
            playlist.addProperty("name", selectedName);
            showDetail(playlist);
        }, this::showError);
    }

    private void deletePlaylist() {
        JsonObject request = new JsonObject();
        request.addProperty("ref", selectedRef);
        TuneWeaveUiService.request("uni-delete", request, ignored -> {
            UniPlaylistClient.setActivePlaylistRef("");
            refresh();
        }, this::showError);
    }

    private void removeItem(String itemId) {
        if (itemId.isBlank()) {
            showError("聚合歌单条目缺少 ID");
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("ref", selectedRef);
        request.addProperty("item_id", itemId);
        TuneWeaveUiService.request("uni-remove", request, ignored -> reloadDetail(), this::showError);
    }

    private void reorder(List<JsonObject> items, int from, int to) {
        if (to < 0 || to >= items.size()) {
            return;
        }
        JsonArray itemIds = new JsonArray();
        for (int index = 0; index < items.size(); index++) {
            int sourceIndex = index;
            if (index == from) sourceIndex = to;
            else if (index == to) sourceIndex = from;
            itemIds.add(string(items.get(sourceIndex), "id"));
        }
        JsonObject body = new JsonObject();
        body.add("item_ids", itemIds);
        JsonObject request = new JsonObject();
        request.addProperty("ref", selectedRef);
        request.add("body", body);
        TuneWeaveUiService.request("uni-order", request, ignored -> reloadDetail(), this::showError);
    }

    private void reloadDetail() {
        JsonObject playlist = new JsonObject();
        playlist.addProperty("ref", selectedRef);
        playlist.addProperty("name", selectedName);
        showDetail(playlist);
    }

    private void addPlaylistCard(JsonObject playlist) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setBackground(ButtonInsetBackgroundFactory.builder().cornerRadius(dp(8)).inset(dp(1))
                .padding(new ButtonInsetBackgroundFactory.Padding(dp(12), dp(8), dp(12), dp(8))).build().newBackgroundDrawable());
        TextView name = new TextView(getContext());
        name.setText(string(playlist, "name"));
        name.setTextSize(Theme.TEXT_SIZE_LARGE);
        name.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        card.addView(name);
        TextView detail = new TextView(getContext());
        detail.setText(string(playlist, "item_count") + " 首 · " + string(playlist, "ref"));
        detail.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        card.addView(detail);
        card.setClickable(true);
        card.setOnClickListener(view -> showDetail(playlist));
        content.addView(card, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(12), 0, 0));
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private Button button(String text) {
        Button button = new Button(getContext());
        button.setText(text);
        button.setTextColor(Theme.PRIMARY_COLOR);
        button.setTextSize(Theme.TEXT_SIZE_NORMAL);
        button.setBackground(ButtonInsetBackgroundFactory.builder().cornerRadius(dp(4)).inset(dp(1))
                .padding(new ButtonInsetBackgroundFactory.Padding(dp(8), 0, dp(8), 0)).build().newBackgroundDrawable());
        return button;
    }

    private void addHeading(String text) {
        TextView title = new TextView(getContext());
        title.setText(text);
        title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        title.setTextSize(Theme.TEXT_SIZE_LARGER);
        content.addView(title, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
    }

    private void addDescription(String text) {
        TextView description = new TextView(getContext());
        description.setText(text);
        description.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        description.setTextSize(Theme.TEXT_SIZE_NORMAL);
        content.addView(description, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(8), 0, 0));
    }

    private void showError(String text) {
        ToastUtil.show(text);
    }

    private LayoutParams margins(LayoutParams params, int left, int top, int right, int bottom) {
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private static List<JsonObject> objects(JsonElement data) {
        JsonArray array = data != null && data.isJsonArray() ? data.getAsJsonArray() : new JsonArray();
        List<JsonObject> result = new ArrayList<>();
        array.forEach(element -> {
            if (element.isJsonObject()) result.add(element.getAsJsonObject());
        });
        return result;
    }

    private static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : "";
    }
}
