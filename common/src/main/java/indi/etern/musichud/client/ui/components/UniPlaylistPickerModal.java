package indi.etern.musichud.client.ui.components;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import icyllis.modernui.animation.ColorEvaluator;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.ui.ClampingScrollView;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.client.services.TuneWeaveUiService;
import indi.etern.musichud.client.services.UniPlaylistClient;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.utils.Easing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/** The TuneWeave equivalent of the established “modify track playlists” picker. */
public final class UniPlaylistPickerModal {
    private UniPlaylistPickerModal() {
    }

    public static void showTrackPicker(Context context, MusicDetail music, Runnable success, Consumer<String> failure) {
        if (music == null || music.getSourceRef().isBlank()) {
            failure.accept("该歌曲没有可加入聚合歌单的平台资源引用");
            return;
        }
        TuneWeaveUiService.request("uni-list", new JsonObject(), data -> {
            List<JsonObject> playlists = objects(data);
            if (playlists.isEmpty()) {
                failure.accept("还没有聚合歌单，请先在“聚合歌单”页面新建一个");
                return;
            }
            showTrackPicker(context, music, playlists, success, failure);
        }, failure);
    }

    public static void showReferencePicker(Context context, String reference, String kind, Runnable success, Consumer<String> failure) {
        if (reference == null || reference.isBlank()) {
            failure.accept("该内容没有可加入聚合歌单的平台资源引用");
            return;
        }
        TuneWeaveUiService.request("uni-list", new JsonObject(), data -> {
            List<JsonObject> playlists = objects(data);
            if (playlists.isEmpty()) {
                failure.accept("还没有聚合歌单，请先在“聚合歌单”页面新建一个");
                return;
            }
            showReferencePicker(context, reference, kind, playlists, success, failure);
        }, failure);
    }

    public static void showImportConfirmation(Context context, Playlist playlist, Runnable success, Consumer<String> failure) {
        if (playlist == null || playlist.getSourceRef().isBlank()) {
            failure.accept("该歌单没有可导入的平台资源引用");
            return;
        }
        TextView title = title(context, "导入为聚合歌单");
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);

        UrlImageView cover = new UrlImageView(context);
        cover.setCornerRadius(cover.dp(6));
        cover.loadUrl(playlist.getImageThumbnailUrl(96));
        content.addView(cover, new LinearLayout.LayoutParams(cover.dp(56), cover.dp(56)));

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(context);
        name.setText(playlist.getName());
        name.setTextSize(Theme.TEXT_SIZE_LARGE);
        name.setTextColor(Theme.NORMAL_TEXT_COLOR);
        textColumn.addView(name);
        TextView help = new TextView(context);
        help.setText("会按原平台顺序复制为新的聚合歌单；不会改动原歌单。");
        help.setTextSize(Theme.TEXT_SIZE_NORMAL);
        help.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        textColumn.addView(help);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
        textParams.setMargins(content.dp(12), 0, 0, 0);
        content.addView(textColumn, textParams);

        new Modal(context, content, title,
                new Modal.ActionButton("导入", (button, modal) -> {
                    UniPlaylistClient.importPlaylist(playlist, () -> {
                        modal.dismiss();
                        success.run();
                    }, failure);
                }),
                new Modal.ActionButton("取消", (button, modal) -> modal.dismiss())
        ).show();
    }

    public static void showImportConfirmation(Context context, String reference, String name, Runnable success, Consumer<String> failure) {
        if (reference == null || reference.isBlank()) {
            failure.accept("该歌单没有可导入的平台资源引用");
            return;
        }
        TextView title = title(context, "导入为聚合歌单");
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = new TextView(context);
        mark.setText("♫");
        mark.setTextSize(Theme.TEXT_SIZE_LARGE);
        mark.setTextColor(Theme.PRIMARY_COLOR);
        mark.setGravity(Gravity.CENTER);
        ShapeDrawable markBackground = new ShapeDrawable();
        markBackground.setCornerRadius(mark.dp(6));
        markBackground.setColor(0xFF322B2A);
        mark.setBackground(markBackground);
        content.addView(mark, new LinearLayout.LayoutParams(mark.dp(56), mark.dp(56)));
        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(context);
        label.setText(name);
        label.setTextSize(Theme.TEXT_SIZE_LARGE);
        label.setTextColor(Theme.NORMAL_TEXT_COLOR);
        textColumn.addView(label);
        TextView help = new TextView(context);
        help.setText("会按原平台顺序复制为新的聚合歌单；不会改动原歌单。");
        help.setTextSize(Theme.TEXT_SIZE_NORMAL);
        help.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        textColumn.addView(help);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
        textParams.setMargins(content.dp(12), 0, 0, 0);
        content.addView(textColumn, textParams);
        new Modal(context, content, title,
                new Modal.ActionButton("导入", (button, modal) -> UniPlaylistClient.importPlaylist(reference, name, () -> {
                    modal.dismiss();
                    success.run();
                }, failure)),
                new Modal.ActionButton("取消", (button, modal) -> modal.dismiss())
        ).show();
    }

    private static void showTrackPicker(Context context, MusicDetail music, List<JsonObject> playlists,
                                        Runnable success, Consumer<String> failure) {
        TextView title = title(context, "添加曲目到聚合歌单");
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        Set<String> selected = new LinkedHashSet<>();
        for (JsonObject playlist : playlists) {
            String ref = string(playlist, "ref");
            if (ref.isBlank()) continue;
            PlaylistToggleRow row = new PlaylistToggleRow(context, playlist, checked -> {
                if (checked) selected.add(ref);
                else selected.remove(ref);
            });
            rows.addView(row, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }

        ClampingScrollView scroll = new ClampingScrollView(context);
        scroll.addView(rows, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        int height = Math.clamp(rows.dp(68) * Math.max(1, playlists.size()) + rows.dp(8), rows.dp(120), rows.dp(360));
        scroll.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, height));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(scroll);

        new Modal(context, content, title,
                new Modal.ActionButton("确认", (button, modal) -> {
                    if (selected.isEmpty()) {
                        failure.accept("请至少选择一个聚合歌单");
                        return;
                    }
                    addToSelected(music, selected, modal, success, failure);
                }),
                new Modal.ActionButton("取消", (button, modal) -> modal.dismiss())
        ).show();
    }

    private static void showReferencePicker(Context context, String reference, String kind, List<JsonObject> playlists,
                                            Runnable success, Consumer<String> failure) {
        TextView title = title(context, "添加曲目到聚合歌单");
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        Set<String> selected = new LinkedHashSet<>();
        for (JsonObject playlist : playlists) {
            String ref = string(playlist, "ref");
            if (ref.isBlank()) continue;
            PlaylistToggleRow row = new PlaylistToggleRow(context, playlist, checked -> {
                if (checked) selected.add(ref);
                else selected.remove(ref);
            });
            rows.addView(row, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }
        ClampingScrollView scroll = new ClampingScrollView(context);
        scroll.addView(rows, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        int height = Math.clamp(rows.dp(68) * Math.max(1, playlists.size()) + rows.dp(8), rows.dp(120), rows.dp(360));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(scroll, new LinearLayout.LayoutParams(MATCH_PARENT, height));
        new Modal(context, content, title,
                new Modal.ActionButton("确认", (button, modal) -> {
                    if (selected.isEmpty()) {
                        failure.accept("请至少选择一个聚合歌单");
                        return;
                    }
                    addReferenceToSelected(reference, kind, selected, modal, success, failure);
                }),
                new Modal.ActionButton("取消", (button, modal) -> modal.dismiss())
        ).show();
    }

    private static void addToSelected(MusicDetail music, Set<String> selected, Modal modal,
                                      Runnable success, Consumer<String> failure) {
        AtomicInteger remaining = new AtomicInteger(selected.size());
        for (String ref : selected) {
            UniPlaylistClient.addTrack(ref, music, () -> {
                if (remaining.decrementAndGet() == 0) {
                    modal.dismiss();
                    success.run();
                }
            }, failure);
        }
    }

    private static void addReferenceToSelected(String reference, String kind, Set<String> selected, Modal modal,
                                               Runnable success, Consumer<String> failure) {
        AtomicInteger remaining = new AtomicInteger(selected.size());
        for (String ref : selected) {
            UniPlaylistClient.addReference(ref, reference, kind, () -> {
                if (remaining.decrementAndGet() == 0) {
                    modal.dismiss();
                    success.run();
                }
            }, failure);
        }
    }

    private static TextView title(Context context, String text) {
        TextView title = new TextView(context);
        title.setText(text);
        title.setTextSize(Theme.TEXT_SIZE_LARGE);
        return title;
    }

    private static List<JsonObject> objects(JsonElement element) {
        JsonArray array = element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
        List<JsonObject> result = new ArrayList<>();
        array.forEach(item -> {
            if (item.isJsonObject()) result.add(item.getAsJsonObject());
        });
        return result;
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static final class PlaylistToggleRow extends LinearLayout {
        private static final int EMPTY = 0x00000000;
        private static final int CHECKED = 0xD9E0BFB7;

        private final ShapeDrawable background;
        private final TextView name;
        private final TextView count;
        private final Consumer<Boolean> onChange;
        private int currentBackground = EMPTY;
        private boolean checked;
        private ValueAnimator animation;

        private PlaylistToggleRow(Context context, JsonObject playlist, Consumer<Boolean> onChange) {
            super(context);
            this.onChange = onChange;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(60));
            setPadding(dp(8), dp(8), dp(8), dp(8));

            background = new ShapeDrawable();
            background.setCornerRadius(dp(6));
            background.setColor(ColorStateList.valueOf(EMPTY));
            setBackground(new InsetDrawable(background, dp(2)));

            TextView mark = new TextView(context);
            mark.setText("♫");
            mark.setTextSize(Theme.TEXT_SIZE_LARGE);
            mark.setTextColor(Theme.PRIMARY_COLOR);
            mark.setGravity(Gravity.CENTER);
            ShapeDrawable markBackground = new ShapeDrawable();
            markBackground.setCornerRadius(dp(5));
            markBackground.setColor(0xFF322B2A);
            mark.setBackground(markBackground);
            addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));

            LinearLayout textColumn = new LinearLayout(context);
            textColumn.setOrientation(VERTICAL);
            name = new TextView(context);
            name.setText(string(playlist, "name"));
            name.setMaxLines(2);
            name.setTextSize(Theme.TEXT_SIZE_LARGE);
            name.setTextColor(Theme.NORMAL_TEXT_COLOR);
            textColumn.addView(name);
            count = new TextView(context);
            String amount = string(playlist, "item_count");
            count.setText(amount.isBlank() ? "聚合歌单" : "共 " + amount + " 项");
            count.setTextSize(Theme.TEXT_SIZE_NORMAL);
            count.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            textColumn.addView(count);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
            textParams.setMargins(dp(12), 0, 0, 0);
            addView(textColumn, textParams);

            setOnClickListener(view -> {
                checked = !checked;
                animateTo(checked);
                onChange.accept(checked);
            });
        }

        private void animateTo(boolean value) {
            if (animation != null) animation.cancel();
            int targetBackground = value ? CHECKED : EMPTY;
            int targetName = value ? 0xFF000000 : Theme.NORMAL_TEXT_COLOR;
            int targetCount = value ? 0xFF333333 : Theme.SECONDARY_TEXT_COLOR;
            int initialBackground = currentBackground;
            int initialName = Color.toArgb(name.getCurrentTextColor());
            int initialCount = Color.toArgb(count.getCurrentTextColor());
            currentBackground = targetBackground;
            animation = ValueAnimator.ofFloat(0f, 1f);
            animation.addUpdateListener(valueAnimator -> {
                float progress = valueAnimator.getAnimatedFraction();
                background.setColor(ColorStateList.valueOf(ColorEvaluator.evaluate(progress, initialBackground, targetBackground)));
                name.setTextColor(ColorEvaluator.evaluate(progress, initialName, targetName));
                count.setTextColor(ColorEvaluator.evaluate(progress, initialCount, targetCount));
                invalidate();
            });
            animation.setDuration(200);
            animation.setInterpolator(Easing.EASE_OUT_QUAD);
            animation.start();
        }
    }
}
