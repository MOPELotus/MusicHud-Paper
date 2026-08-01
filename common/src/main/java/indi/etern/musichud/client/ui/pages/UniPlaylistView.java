package indi.etern.musichud.client.ui.pages;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.client.services.TuneWeaveUiService;
import indi.etern.musichud.client.services.UniPlaylistClient;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.components.Modal;
import indi.etern.musichud.client.ui.components.MusicHudIconButton;
import indi.etern.musichud.client.ui.components.UrlImageView;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackgroundFactory;

import java.util.ArrayList;
import java.util.List;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/** Server-owned TuneWeave playlists. Import and add actions live beside their source content. */
public final class UniPlaylistView extends LinearLayout {
    private final LinearLayout content;
    private String selectedRef = "";
    private String selectedName = "";

    public UniPlaylistView(Context context) {
        super(context);
        setOrientation(VERTICAL);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        addView(scroll, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setPadding(dp(32), dp(32), dp(32), dp(48));
        scroll.addView(content, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        showList();
    }

    public void refresh() {
        showList();
    }

    private void showList() {
        selectedRef = "";
        selectedName = "";
        UniPlaylistClient.setActivePlaylistRef("");
        content.removeAllViews();
        addHeading("聚合歌单");
        addDescription("把不同平台的音乐保存到一个播放顺序中。歌曲行右侧的歌单图标可添加单曲；平台歌单卡片的下载图标可导入整个歌单。");

        LinearLayout actionRow = new LinearLayout(getContext());
        actionRow.setOrientation(HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        MusicHudIconButton create = new MusicHudIconButton(getContext(), "actions/playlist_plus", "新建聚合歌单");
        create.setOnClickListener(view -> showCreateDialog());
        actionRow.addView(create, new LayoutParams(dp(36), dp(36)));
        TextView createText = actionText("新建聚合歌单");
        createText.setOnClickListener(view -> showCreateDialog());
        actionRow.addView(createText, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), dp(8), 0, 0, 0));
        content.addView(actionRow, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(16), 0, 0));

        TextView loading = message("正在读取聚合歌单…", Theme.SECONDARY_TEXT_COLOR);
        content.addView(loading, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), 0, dp(24), 0, 0));
        TuneWeaveUiService.request("uni-list", new JsonObject(), data -> {
            content.removeView(loading);
            List<JsonObject> playlists = objects(data);
            if (playlists.isEmpty()) {
                TextView empty = message("还没有聚合歌单。你可以新建一个空歌单，或从搜索、账户页面导入现有歌单。", Theme.SECONDARY_TEXT_COLOR);
                content.addView(empty, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(24), 0, 0));
                return;
            }
            for (JsonObject playlist : playlists) addPlaylistCard(playlist);
        }, error -> {
            loading.setText(error);
            loading.setTextColor(Theme.ERROR_TEXT_COLOR);
        });
    }

    private void showCreateDialog() {
        TextView title = dialogTitle("新建聚合歌单");
        LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(VERTICAL);
        TextView help = message("创建后可在任意搜索结果或现有歌单的操作按钮中添加内容。", Theme.SECONDARY_TEXT_COLOR);
        form.addView(help);
        EditText name = new EditText(getContext());
        name.setHint("歌单名称");
        form.addView(name, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(12), 0, 0));
        new Modal(getContext(), form, title,
                new Modal.ActionButton("创建", (button, modal) -> {
                    String text = name.getText().toString().trim();
                    if (text.isBlank()) {
                        ToastUtil.show("请输入歌单名称");
                        return;
                    }
                    JsonObject request = new JsonObject();
                    request.addProperty("name", text);
                    TuneWeaveUiService.request("uni-create", request, ignored -> {
                        modal.dismiss();
                        showList();
                    }, this::showError);
                }),
                new Modal.ActionButton("取消", (button, modal) -> modal.dismiss())
        ).show();
    }

    private void showDetail(JsonObject playlist) {
        selectedRef = string(playlist, "ref");
        selectedName = string(playlist, "name");
        UniPlaylistClient.setActivePlaylistRef(selectedRef);
        content.removeAllViews();

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        MusicHudIconButton back = new MusicHudIconButton(getContext(), "actions/arrow_up", "返回聚合歌单列表");
        back.setRotation(90f);
        back.setOnClickListener(view -> showList());
        header.addView(back, new LayoutParams(dp(36), dp(36)));
        LinearLayout headingColumn = new LinearLayout(getContext());
        headingColumn.setOrientation(VERTICAL);
        TextView title = new TextView(getContext());
        title.setText(selectedName);
        title.setTextSize(Theme.TEXT_SIZE_LARGER);
        title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        headingColumn.addView(title);
        TextView hint = message("点击曲目加入播放队列；右侧按钮可调整顺序或移除。", Theme.SECONDARY_TEXT_COLOR);
        headingColumn.addView(hint);
        header.addView(headingColumn, margins(new LayoutParams(0, WRAP_CONTENT, 1), dp(8), 0, 0, 0));
        MusicHudIconButton rename = new MusicHudIconButton(getContext(), "actions/edit", "重命名聚合歌单");
        rename.setOnClickListener(view -> showRenameDialog());
        header.addView(rename, new LayoutParams(dp(36), dp(36)));
        MusicHudIconButton delete = new MusicHudIconButton(getContext(), "actions/delete", "删除聚合歌单");
        delete.setOnClickListener(view -> showDeleteDialog());
        header.addView(delete, margins(new LayoutParams(dp(36), dp(36)), dp(4), 0, 0, 0));
        content.addView(header);

        TextView loading = message("正在读取歌曲…", Theme.SECONDARY_TEXT_COLOR);
        content.addView(loading, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), 0, dp(24), 0, 0));
        JsonObject request = new JsonObject();
        request.addProperty("ref", selectedRef);
        TuneWeaveUiService.request("uni-items", request, data -> {
            content.removeView(loading);
            List<JsonObject> items = objects(data);
            if (items.isEmpty()) {
                content.addView(message("这个聚合歌单还是空的。请在歌曲行点击右侧歌单图标来添加。", Theme.SECONDARY_TEXT_COLOR),
                        margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(24), 0, 0));
                return;
            }
            for (int index = 0; index < items.size(); index++) {
                addPlaylistItem(items, index);
            }
        }, error -> {
            loading.setText(error);
            loading.setTextColor(Theme.ERROR_TEXT_COLOR);
        });
    }

    private void showRenameDialog() {
        TextView title = dialogTitle("重命名聚合歌单");
        LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(VERTICAL);
        EditText name = new EditText(getContext());
        name.setText(selectedName);
        name.setHint("歌单名称");
        form.addView(name, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        new Modal(getContext(), form, title,
                new Modal.ActionButton("保存", (button, modal) -> updateName(name.getText().toString(), modal)),
                new Modal.ActionButton("取消", (button, modal) -> modal.dismiss())
        ).show();
    }

    private void showDeleteDialog() {
        TextView title = dialogTitle("删除聚合歌单？");
        LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(VERTICAL);
        form.addView(message("“" + selectedName + "”及其中的全部项目会被删除，此操作无法撤销。", Theme.SECONDARY_TEXT_COLOR));
        new Modal(getContext(), form, title,
                new Modal.ActionButton("删除", (button, modal) -> {
                    JsonObject request = new JsonObject();
                    request.addProperty("ref", selectedRef);
                    TuneWeaveUiService.request("uni-delete", request, ignored -> {
                        modal.dismiss();
                        showList();
                    }, this::showError);
                }),
                new Modal.ActionButton("取消", (button, modal) -> modal.dismiss())
        ).show();
    }

    private void updateName(String name, Modal modal) {
        String value = name == null ? "" : name.trim();
        if (value.isBlank()) {
            ToastUtil.show("歌单名称不能为空");
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("ref", selectedRef);
        request.addProperty("name", value);
        TuneWeaveUiService.request("uni-update", request, ignored -> {
            selectedName = value;
            modal.dismiss();
            reloadDetail();
        }, this::showError);
    }

    private void addPlaylistCard(JsonObject playlist) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setClickable(true);
        card.setBackground(ButtonInsetBackgroundFactory.builder().cornerRadius(dp(10)).inset(dp(1))
                .padding(new ButtonInsetBackgroundFactory.Padding(0, 0, 0, 0)).build().newBackgroundDrawable());

        TextView cover = playlistMark();
        card.addView(cover, new LayoutParams(dp(56), dp(56)));
        LinearLayout textColumn = new LinearLayout(getContext());
        textColumn.setOrientation(VERTICAL);
        TextView name = new TextView(getContext());
        name.setText(string(playlist, "name"));
        name.setTextSize(Theme.TEXT_SIZE_LARGE);
        name.setTextColor(Theme.NORMAL_TEXT_COLOR);
        textColumn.addView(name);
        String itemCount = string(playlist, "item_count");
        TextView amount = message(itemCount.isBlank() ? "聚合歌单" : "共 " + itemCount + " 项", Theme.SECONDARY_TEXT_COLOR);
        textColumn.addView(amount);
        String description = string(playlist, "description");
        if (!description.isBlank()) {
            TextView detail = message(description, Theme.SECONDARY_TEXT_COLOR);
            detail.setMaxLines(1);
            textColumn.addView(detail);
        }
        card.addView(textColumn, margins(new LayoutParams(0, WRAP_CONTENT, 1), dp(12), 0, 0, 0));
        MusicHudIconButton edit = new MusicHudIconButton(getContext(), "actions/edit", "管理聚合歌单");
        edit.setOnClickListener(view -> showDetail(playlist));
        card.addView(edit, new LayoutParams(dp(36), dp(36)));
        card.setOnClickListener(view -> showDetail(playlist));
        content.addView(card, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(12), 0, 0));
    }

    private void addPlaylistItem(List<JsonObject> items, int index) {
        JsonObject item = items.get(index);
        JsonObject snapshot = object(item.get("snapshot"));
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(6), dp(6), dp(6));
        row.setClickable(true);
        row.setBackground(ButtonInsetBackgroundFactory.builder().cornerRadius(dp(10)).inset(dp(1))
                .padding(new ButtonInsetBackgroundFactory.Padding(0, 0, 0, 0)).build().newBackgroundDrawable());

        row.addView(itemCover(snapshot), new LayoutParams(dp(56), dp(56)));
        LinearLayout textColumn = new LinearLayout(getContext());
        textColumn.setOrientation(VERTICAL);
        TextView name = new TextView(getContext());
        name.setText(string(snapshot, "title"));
        name.setTextSize(Theme.TEXT_SIZE_LARGE);
        name.setTextColor(Theme.NORMAL_TEXT_COLOR);
        name.setSingleLine(true);
        textColumn.addView(name);
        String artists = stringList(snapshot.get("artists"));
        String album = string(snapshot, "album");
        TextView detail = message(artists.isBlank() ? album : artists + (album.isBlank() ? "" : " · " + album), Theme.SECONDARY_TEXT_COLOR);
        detail.setMaxLines(1);
        textColumn.addView(detail);
        TextView source = message(string(item, "source_ref"), Theme.SECONDARY_TEXT_COLOR);
        source.setTextSize(Theme.TEXT_SIZE_SMALL);
        source.setMaxLines(1);
        textColumn.addView(source);
        row.addView(textColumn, margins(new LayoutParams(0, WRAP_CONTENT, 1), dp(12), 0, dp(4), 0));

        MusicHudIconButton up = new MusicHudIconButton(getContext(), "actions/arrow_up", "上移");
        up.setEnabled(index > 0);
        up.setOnClickListener(view -> reorder(items, index, index - 1));
        row.addView(up, new LayoutParams(dp(30), dp(30)));
        MusicHudIconButton down = new MusicHudIconButton(getContext(), "actions/arrow_down", "下移");
        down.setEnabled(index < items.size() - 1);
        down.setOnClickListener(view -> reorder(items, index, index + 1));
        row.addView(down, new LayoutParams(dp(30), dp(30)));
        MusicHudIconButton remove = new MusicHudIconButton(getContext(), "actions/delete", "从聚合歌单移除");
        remove.setOnClickListener(view -> removeItem(string(item, "id")));
        row.addView(remove, new LayoutParams(dp(30), dp(30)));

        row.setOnClickListener(view -> queueItem(string(item, "id")));
        content.addView(row, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(8), 0, 0));
    }

    private View itemCover(JsonObject snapshot) {
        FrameLayout frame = new FrameLayout(getContext());
        TextView fallback = playlistMark();
        frame.addView(fallback, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        String coverUrl = string(snapshot, "cover_url");
        if (!coverUrl.isBlank()) {
            UrlImageView image = new UrlImageView(getContext());
            image.setCornerRadius(image.dp(7));
            image.loadUrl(coverUrl);
            frame.addView(image, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        }
        return frame;
    }

    private TextView playlistMark() {
        TextView mark = new TextView(getContext());
        mark.setText("♫");
        mark.setTextSize(Theme.TEXT_SIZE_LARGE);
        mark.setTextColor(Theme.PRIMARY_COLOR);
        mark.setGravity(Gravity.CENTER);
        ShapeDrawable background = new ShapeDrawable();
        background.setCornerRadius(mark.dp(8));
        background.setColor(0xFF322B2A);
        mark.setBackground(background);
        return mark;
    }

    private void queueItem(String itemId) {
        if (itemId.isBlank()) return;
        JsonObject request = new JsonObject();
        request.addProperty("ref", selectedRef);
        request.addProperty("item_id", itemId);
        TuneWeaveUiService.request("queue-uni-item", request,
                ignored -> ToastUtil.show("已加入播放队列"), this::showError);
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
        if (to < 0 || to >= items.size()) return;
        JsonArray ids = new JsonArray();
        for (int index = 0; index < items.size(); index++) {
            int source = index;
            if (index == from) source = to;
            else if (index == to) source = from;
            ids.add(string(items.get(source), "id"));
        }
        JsonObject body = new JsonObject();
        body.add("item_ids", ids);
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

    private TextView actionText(String text) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(Theme.TEXT_SIZE_NORMAL);
        view.setTextColor(Theme.PRIMARY_COLOR);
        view.setClickable(true);
        return view;
    }

    private void addHeading(String text) {
        TextView title = new TextView(getContext());
        title.setText(text);
        title.setTextSize(Theme.TEXT_SIZE_LARGER);
        title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        content.addView(title, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
    }

    private void addDescription(String text) {
        content.addView(message(text, Theme.SECONDARY_TEXT_COLOR),
                margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(8), 0, 0));
    }

    private TextView message(String text, int color) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(Theme.TEXT_SIZE_NORMAL);
        view.setTextColor(color);
        return view;
    }

    private TextView dialogTitle(String text) {
        TextView title = new TextView(getContext());
        title.setText(text);
        title.setTextSize(Theme.TEXT_SIZE_LARGE);
        return title;
    }

    private void showError(String error) {
        ToastUtil.show(error);
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

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static String stringList(JsonElement element) {
        if (element == null || !element.isJsonArray()) return "";
        List<String> values = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonNull()) values.add(value.getAsString());
        }
        return String.join(" / ", values);
    }
}
