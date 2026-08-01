package indi.etern.musichud.client.ui.pages.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.HorizontalScrollView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.client.services.TuneWeaveUiService;
import indi.etern.musichud.client.services.TuneWeaveClientCredentials;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.components.MusicHudIconButton;
import indi.etern.musichud.client.ui.components.UrlImageView;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackgroundFactory;

import java.util.ArrayList;
import java.util.List;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/** Account selector backed by client-local TuneWeave caller credentials. */
public final class TuneWeaveAccountView extends LinearLayout {
    private final LinearLayout platformSelector;
    private final LinearLayout accountContent;
    private final List<JsonObject> platforms = new ArrayList<>();
    private String selectedPlatform = "";
    private String qrTransactionId = "";

    public TuneWeaveAccountView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(32), dp(32), dp(32), dp(48));

        TextView title = new TextView(context);
        title.setText("音乐账户");
        title.setTextSize(Theme.TEXT_SIZE_LARGER);
        title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        addView(title);

        TextView help = new TextView(context);
        help.setText("选择已启用的平台。登录凭证只保存在此 Minecraft 客户端，可跨单人、局域网和服务器复用，不会发送给游戏服务器。");
        help.setTextSize(Theme.TEXT_SIZE_NORMAL);
        help.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        addView(help, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(8), 0, 0));

        HorizontalScrollView platformSelectorScroll = new HorizontalScrollView(context);
        platformSelectorScroll.setFillViewport(true);
        platformSelector = new LinearLayout(context);
        platformSelector.setOrientation(HORIZONTAL);
        platformSelector.setGravity(Gravity.CENTER_VERTICAL);
        platformSelectorScroll.addView(platformSelector, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        addView(platformSelectorScroll, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(16), 0, 0));

        accountContent = new LinearLayout(context);
        accountContent.setOrientation(VERTICAL);
        addView(accountContent, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(20), 0, 0));
        loadPlatforms();
    }

    private void loadPlatforms() {
        accountContent.removeAllViews();
        status("正在读取 TuneWeave 平台…", false);
        TuneWeaveUiService.request("platforms", new JsonObject(), data -> {
            platforms.clear();
            for (JsonObject platform : objects(data)) {
                String id = string(platform, "platform");
                if (booleanValue(platform, "registered") && !"uni".equals(id)
                        && supportsAccount(platform)) {
                    platforms.add(platform);
                }
            }
            if (platforms.isEmpty()) {
                status("TuneWeave 没有注册可登录的平台", true);
                return;
            }
            JsonObject defaultPlatform = platforms.stream().filter(item -> booleanValue(item, "default"))
                    .findFirst().orElse(platforms.getFirst());
            selectPlatform(string(defaultPlatform, "platform"));
        }, this::statusError);
    }

    private void selectPlatform(String platform) {
        selectedPlatform = platform;
        platformSelector.removeAllViews();
        for (JsonObject item : platforms) {
            String id = string(item, "platform");
            MusicHudIconButton badge = new MusicHudIconButton(getContext(), platformIconResource(id), platformName(id));
            badge.setAlpha(id.equals(selectedPlatform) ? 1f : 0.56f);
            badge.setOnClickListener(view -> selectPlatform(id));
            platformSelector.addView(badge, margins(new LayoutParams(dp(36), dp(36)), 0, 0, dp(8), 0));
        }
        loadProfile();
    }

    private void loadProfile() {
        accountContent.removeAllViews();
        status("正在读取 " + platformName(selectedPlatform) + " 账户…", false);
        TuneWeaveUiService.request("account-profile", accountRequest(), this::showProfile, error -> showLogin(error));
    }

    private void showProfile(JsonElement data) {
        accountContent.removeAllViews();
        // /v1/account/profile returns a unified UserProfile.  Identity lives
        // in its nested user object, while older TuneWeave versions returned
        // the identity at the top level; accept both layouts.
        JsonObject profile = object(data);
        JsonObject identity = object(profile.get("user"));
        if (identity.entrySet().isEmpty()) {
            identity = profile;
        }
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        UrlImageView avatar = new UrlImageView(getContext());
        avatar.setCircular(true);
        avatar.loadUrl(string(identity, "avatar_url"));
        row.addView(avatar, new LayoutParams(dp(64), dp(64)));
        LinearLayout text = new LinearLayout(getContext());
        text.setOrientation(VERTICAL);
        TextView name = new TextView(getContext());
        name.setText(firstString(identity, "nickname", "name", "display_name"));
        name.setTextSize(Theme.TEXT_SIZE_LARGE);
        name.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        text.addView(name);
        TextView id = new TextView(getContext());
        id.setText("ID: " + firstString(identity, "id", "user_id", "ref"));
        id.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        text.addView(id);
        row.addView(text, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), dp(16), 0, 0, 0));
        accountContent.addView(row);

        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(HORIZONTAL);
        Button playlists = button("我的歌单");
        playlists.setOnClickListener(view -> loadLibrary("account-playlists", "我的歌单", LibraryKind.PLAYLIST));
        actions.addView(playlists);
        Button favorites = button("喜欢的歌曲");
        favorites.setOnClickListener(view -> loadLibrary("favorite-tracks", "喜欢的歌曲", LibraryKind.FAVORITE_TRACK));
        actions.addView(favorites, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), dp(8), 0, 0, 0));
        Button recommendations = button("推荐歌曲");
        recommendations.setOnClickListener(view -> loadLibrary("recommendation-tracks", "推荐歌曲", LibraryKind.TRACK));
        actions.addView(recommendations, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), dp(8), 0, 0, 0));
        Button refresh = button("刷新");
        refresh.setOnClickListener(view -> loadProfile());
        actions.addView(refresh, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), dp(8), 0, 0, 0));
        accountContent.addView(actions, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), 0, dp(16), 0, 0));

        LinearLayout libraryActions = new LinearLayout(getContext());
        libraryActions.setOrientation(HORIZONTAL);
        Button albums = button("收藏专辑");
        albums.setOnClickListener(view -> loadLibrary("account-albums", "收藏专辑", LibraryKind.ALBUM));
        libraryActions.addView(albums);
        Button artists = button("关注歌手");
        artists.setOnClickListener(view -> loadLibrary("account-artists", "关注歌手", LibraryKind.ARTIST));
        libraryActions.addView(artists, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), dp(8), 0, 0, 0));
        Button history = button("最近播放");
        history.setOnClickListener(view -> loadLibrary("account-history", "最近播放", LibraryKind.HISTORY_TRACK));
        libraryActions.addView(history, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), dp(8), 0, 0, 0));
        Button logout = button("退出此账户");
        logout.setOnClickListener(view -> logout());
        libraryActions.addView(logout, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), dp(8), 0, 0, 0));
        accountContent.addView(libraryActions, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), 0, dp(8), 0, 0));

        Button recommendedPlaylists = button("推荐歌单（导入为聚合歌单）");
        recommendedPlaylists.setOnClickListener(view ->
                loadLibrary("recommendation-playlists", "推荐歌单", LibraryKind.PLAYLIST));
        accountContent.addView(recommendedPlaylists,
                margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), 0, dp(8), 0, 0));
    }

    private void loadLibrary(String action, String title, LibraryKind kind) {
        accountContent.removeAllViews();
        status("正在读取" + title + "…", false);
        TuneWeaveUiService.request(action, accountRequest(), data -> {
            accountContent.removeAllViews();
            TextView heading = new TextView(getContext());
            heading.setText(title);
            heading.setTextSize(Theme.TEXT_SIZE_LARGE);
            heading.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            accountContent.addView(heading);
            List<JsonObject> items = objects(data);
            if (items.isEmpty()) {
                status("暂无内容", false);
            }
            for (JsonObject item : items) {
                addLibraryItem(item, kind);
            }
            Button back = button("返回账户");
            back.setOnClickListener(view -> loadProfile());
            accountContent.addView(back, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), 0, dp(16), 0, 0));
        }, this::showLogin);
    }

    private void addLibraryItem(JsonObject item, LibraryKind kind) {
        JsonObject resource = resource(item);
        String reference = firstString(resource, "ref", "track_ref");
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.setBackground(ButtonInsetBackgroundFactory.builder().cornerRadius(dp(10)).inset(dp(1))
                .padding(new ButtonInsetBackgroundFactory.Padding(0, 0, 0, 0)).build().newBackgroundDrawable());

        TextView mark = new TextView(getContext());
        mark.setText(kind == LibraryKind.PLAYLIST ? "♫" : "♪");
        mark.setTextSize(Theme.TEXT_SIZE_LARGE);
        mark.setTextColor(Theme.PRIMARY_COLOR);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(ButtonInsetBackgroundFactory.builder().cornerRadius(dp(7)).inset(0)
                .padding(new ButtonInsetBackgroundFactory.Padding(0, 0, 0, 0)).build().newBackgroundDrawable());
        row.addView(mark, new LayoutParams(dp(48), dp(48)));

        LinearLayout textColumn = new LinearLayout(getContext());
        textColumn.setOrientation(VERTICAL);
        TextView name = new TextView(getContext());
        name.setText(firstString(resource, "name", "title", "ref"));
        name.setTextSize(Theme.TEXT_SIZE_LARGE);
        name.setTextColor(Theme.NORMAL_TEXT_COLOR);
        name.setSingleLine(true);
        textColumn.addView(name);
        TextView source = new TextView(getContext());
        source.setText(reference);
        source.setTextSize(Theme.TEXT_SIZE_NORMAL);
        source.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        source.setSingleLine(true);
        textColumn.addView(source);
        row.addView(textColumn, margins(new LayoutParams(0, WRAP_CONTENT, 1), dp(12), 0, dp(4), 0));

        configureLibraryActions(row, item, reference, kind);
        accountContent.addView(row, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(8), 0, 0));
    }

    private void configureLibraryActions(LinearLayout row, JsonObject item, String reference, LibraryKind kind) {
        if (reference.isBlank()) return;
        switch (kind) {
            case PLAYLIST -> {
                MusicHudIconButton importButton = new MusicHudIconButton(getContext(), "actions/import", "导入为聚合歌单");
                importButton.setOnClickListener(view -> importPlatformPlaylist(item));
                row.addView(importButton, new LayoutParams(dp(34), dp(34)));
            }
            case ALBUM -> row.setOnClickListener(view -> queueCollection(reference, "album"));
            case ARTIST -> row.setOnClickListener(view -> queueCollection(reference, "artist"));
            case FAVORITE_TRACK -> {
                row.setOnClickListener(view -> queueTrack(reference, string(resource(item), "type")));
                addTrackActions(row, reference, string(resource(item), "type"), false);
            }
            case TRACK, HISTORY_TRACK -> {
                row.setOnClickListener(view -> queueTrack(reference, string(resource(item), "type")));
                addTrackActions(row, reference, string(resource(item), "type"), true);
            }
        }
    }

    private void addTrackActions(LinearLayout row, String reference, String type, boolean canFavorite) {
        MusicHudIconButton addToUni = new MusicHudIconButton(getContext(), "actions/playlist_plus", "添加到聚合歌单");
        addToUni.setOnClickListener(view -> indi.etern.musichud.client.services.UniPlaylistClient.showReferencePicker(
                getContext(), reference, "video".equals(type) ? "video" : "track",
                () -> ToastUtil.show("已加入聚合歌单"), this::statusError));
        row.addView(addToUni, new LayoutParams(dp(34), dp(34)));
        MusicHudIconButton favorite = new MusicHudIconButton(getContext(), canFavorite ? "actions/heart_outline" : "actions/heart", canFavorite ? "收藏歌曲" : "取消收藏");
        favorite.setOnClickListener(view -> changeFavorite(reference, canFavorite));
        row.addView(favorite, new LayoutParams(dp(34), dp(34)));
    }

    private void queueTrack(String reference, String type) {
        JsonObject queue = new JsonObject();
        queue.addProperty("ref", reference);
        queue.addProperty("kind", "video".equals(type) ? "video" : "track");
        TuneWeaveUiService.request("queue-ref", queue,
                ignored -> ToastUtil.show("已加入播放队列"), this::statusError);
    }

    private void queueCollection(String reference, String collection) {
        JsonObject queue = new JsonObject();
        queue.addProperty("ref", reference);
        queue.addProperty("collection", collection);
        TuneWeaveUiService.request("queue-collection", queue, data ->
                        ToastUtil.show("已加入 " + string(object(data), "queued") + " 首歌曲"),
                this::statusError);
    }

    private void changeFavorite(String reference, boolean favorite) {
        JsonObject request = accountRequest();
        request.addProperty("ref", reference);
        TuneWeaveUiService.request(favorite ? "favorite-track-add" : "favorite-track-remove", request,
                ignored -> ToastUtil.show(favorite ? "已收藏" : "已取消收藏"), this::statusError);
    }

    private void importPlatformPlaylist(JsonObject item) {
        JsonObject resource = resource(item);
        String reference = firstString(resource, "ref", "playlist_ref");
        indi.etern.musichud.client.services.UniPlaylistClient.showImportConfirmation(
                getContext(), reference, firstString(resource, "name", "title", "ref"),
                () -> ToastUtil.show("已导入为聚合歌单"), this::statusError);
    }

    private void showLogin(String reason) {
        accountContent.removeAllViews();
        status("尚未登录 " + platformName(selectedPlatform) + "\n" + reason, true);
        if (selectedPlatformSupports("qr_login")) {
            Button qr = button("二维码登录");
            qr.setOnClickListener(view -> startQrLogin());
            accountContent.addView(qr, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), 0, dp(16), 0, 0));
        } else {
            status("该平台未声明二维码登录能力，请在本地 TuneWeave 中完成登录。", false);
        }
        if (!qrTransactionId.isBlank()) {
            Button poll = button("检查登录状态");
            poll.setOnClickListener(view -> pollQrLogin());
            accountContent.addView(poll, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), 0, dp(8), 0, 0));
        }
    }

    private void startQrLogin() {
        JsonObject request = accountRequest();
        TuneWeaveUiService.request("qr-start", request, data -> {
            JsonObject result = object(data);
            qrTransactionId = string(result, "transaction_id");
            accountContent.removeAllViews();
            TextView text = new TextView(getContext());
            text.setText("请使用 " + platformName(selectedPlatform) + " 扫码登录，然后点击“检查登录状态”。");
            text.setTextColor(Theme.NORMAL_TEXT_COLOR);
            accountContent.addView(text);
            UrlImageView qrImage = new UrlImageView(getContext());
            qrImage.loadUrl(firstString(result, "image_data_url", "url"));
            accountContent.addView(qrImage, margins(new LayoutParams(dp(192), dp(192)), 0, dp(16), 0, 0));
            Button poll = button("检查登录状态");
            poll.setOnClickListener(view -> pollQrLogin());
            accountContent.addView(poll, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), 0, dp(12), 0, 0));
        }, this::showLogin);
    }

    private void pollQrLogin() {
        JsonObject request = new JsonObject();
        request.addProperty("transaction_id", qrTransactionId);
        TuneWeaveUiService.request("qr-poll", request, data -> {
            JsonObject state = object(data);
            if ("confirmed".equals(string(state, "state"))) {
                JsonObject credential = object(state.get("caller_credential"));
                String value = string(credential, "value");
                if (value.isBlank()) {
                    statusError("TuneWeave 未返回客户端凭证；请确认登录请求使用了 client 凭证模式。");
                    return;
                }
                TuneWeaveClientCredentials.put(selectedPlatform, value);
                qrTransactionId = "";
                loadProfile();
            } else {
                ToastUtil.show("登录状态：" + string(state, "state"));
            }
        }, this::statusError);
    }

    private void logout() {
        TuneWeaveUiService.request("auth-logout", accountRequest(), ignored -> {
            TuneWeaveClientCredentials.remove(selectedPlatform);
            qrTransactionId = "";
            showLogin("账户已退出");
        }, this::statusError);
    }

    private JsonObject accountRequest() {
        JsonObject request = new JsonObject();
        request.addProperty("platform", selectedPlatform);
        return request;
    }

    private void status(String text, boolean error) {
        TextView message = new TextView(getContext());
        message.setText(text);
        message.setTextColor(error ? Theme.ERROR_TEXT_COLOR : Theme.SECONDARY_TEXT_COLOR);
        message.setTextSize(Theme.TEXT_SIZE_NORMAL);
        accountContent.addView(message);
    }

    private void statusError(String text) {
        accountContent.removeAllViews();
        status(text, true);
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

    private LayoutParams margins(LayoutParams params, int left, int top, int right, int bottom) {
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private boolean selectedPlatformSupports(String capability) {
        return platforms.stream().anyMatch(platform -> selectedPlatform.equals(string(platform, "platform"))
                && hasCapability(platform, capability));
    }

    private static boolean supportsAccount(JsonObject platform) {
        return hasCapability(platform, "qr_login")
                || hasCapability(platform, "account_profile")
                || hasCapability(platform, "account_playlists")
                || hasCapability(platform, "favorites");
    }

    private static boolean hasCapability(JsonObject platform, String expected) {
        if (!platform.has("capabilities") || !platform.get("capabilities").isJsonArray()) {
            return false;
        }
        for (JsonElement capability : platform.getAsJsonArray("capabilities")) {
            if (expected.equals(capability.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static String platformIconResource(String id) {
        return switch (id) {
            case "netease" -> "platforms/netease";
            case "qq" -> "platforms/qq";
            case "bilibili" -> "platforms/bilibili";
            default -> "platforms/music";
        };
    }

    private static String platformName(String id) {
        return switch (id) {
            case "netease" -> "网易云音乐";
            case "qq" -> "QQ 音乐";
            case "bilibili" -> "哔哩哔哩";
            case "kugou" -> "酷狗音乐";
            case "kuwo" -> "酷我音乐";
            case "migu" -> "咪咕音乐";
            default -> id;
        };
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

    private static JsonObject resource(JsonObject item) {
        for (String key : List.of("track", "resource", "item")) {
            JsonObject nested = object(item.get(key));
            if (!nested.entrySet().isEmpty()) {
                return nested;
            }
        }
        return item;
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static boolean booleanValue(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() && object.get(key).getAsBoolean();
    }

    private static String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = string(object, key);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private enum LibraryKind {
        PLAYLIST,
        FAVORITE_TRACK,
        TRACK,
        ALBUM,
        ARTIST,
        HISTORY_TRACK
    }
}
