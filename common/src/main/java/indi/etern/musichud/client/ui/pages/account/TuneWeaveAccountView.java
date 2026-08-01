package indi.etern.musichud.client.ui.pages.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.client.services.TuneWeaveUiService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.components.UrlImageView;
import indi.etern.musichud.client.ui.utils.ui.ButtonInsetBackgroundFactory;

import java.util.ArrayList;
import java.util.List;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/** Account selector for TuneWeave server-managed accounts. */
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
        help.setText("选择已启用的平台。账户由 TuneWeave 服务端保管，Minecraft 客户端不会接收登录凭证。");
        help.setTextSize(Theme.TEXT_SIZE_NORMAL);
        help.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        addView(help, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(8), 0, 0));

        platformSelector = new LinearLayout(context);
        platformSelector.setOrientation(HORIZONTAL);
        platformSelector.setGravity(Gravity.CENTER_VERTICAL);
        addView(platformSelector, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(16), 0, 0));

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
            Button badge = button(platformBadge(id));
            badge.setTextColor(id.equals(selectedPlatform) ? Theme.EMPHASIZE_TEXT_COLOR : Theme.PRIMARY_COLOR);
            badge.setSelected(id.equals(selectedPlatform));
            badge.setOnClickListener(view -> selectPlatform(id));
            platformSelector.addView(badge, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), 0, 0, dp(8), 0));
        }
        loadProfile();
    }

    private void loadProfile() {
        accountContent.removeAllViews();
        status("正在读取 " + displayName(selectedPlatform) + " 账户…", false);
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
                TextView line = new TextView(getContext());
                JsonObject resource = resource(item);
                line.setText(firstString(resource, "name", "title", "ref"));
                line.setTextSize(Theme.TEXT_SIZE_NORMAL);
                line.setTextColor(Theme.NORMAL_TEXT_COLOR);
                String reference = firstString(resource, "ref", "track_ref");
                if (!reference.isBlank()) {
                    line.setClickable(true);
                    configureLibraryAction(line, item, reference, kind);
                }
                accountContent.addView(line, margins(new LayoutParams(MATCH_PARENT, WRAP_CONTENT), 0, dp(8), 0, 0));
            }
            Button back = button("返回账户");
            back.setOnClickListener(view -> loadProfile());
            accountContent.addView(back, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), 0, dp(16), 0, 0));
        }, this::showLogin);
    }

    private void configureLibraryAction(TextView line, JsonObject item, String reference, LibraryKind kind) {
        switch (kind) {
            case PLAYLIST -> line.setOnClickListener(view -> importPlatformPlaylist(item));
            case ALBUM -> line.setOnClickListener(view -> queueCollection(reference, "album"));
            case ARTIST -> line.setOnClickListener(view -> queueCollection(reference, "artist"));
            case FAVORITE_TRACK -> {
                line.setOnClickListener(view -> queueTrack(reference, string(resource(item), "type")));
                line.setOnLongClickListener(view -> {
                    changeFavorite(reference, false);
                    return true;
                });
            }
            case TRACK, HISTORY_TRACK -> line.setOnClickListener(view -> queueTrack(reference, string(resource(item), "type")));
        }
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
        String reference = string(item, "ref");
        String[] parts = reference.split(":", 2);
        if (parts.length != 2) {
            statusError("歌单引用格式无效：" + reference);
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("name", firstString(item, "name", "title", "ref"));
        JsonArray sources = new JsonArray();
        JsonObject source = new JsonObject();
        source.addProperty("platform", parts[0]);
        source.addProperty("type", "playlist");
        source.addProperty("id", parts[1]);
        source.addProperty("account", "default");
        sources.add(source);
        request.add("sources", sources);
        TuneWeaveUiService.request("uni-import", request,
                ignored -> ToastUtil.show("已导入为聚合歌单"), this::statusError);
    }

    private void showLogin(String reason) {
        accountContent.removeAllViews();
        status("尚未登录 " + displayName(selectedPlatform) + "\n" + reason, true);
        if (selectedPlatformSupports("qr_login")) {
            Button qr = button("二维码登录");
            qr.setOnClickListener(view -> startQrLogin());
            accountContent.addView(qr, margins(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT), 0, dp(16), 0, 0));
        } else {
            status("该平台未声明二维码登录能力，请先在 TuneWeave 服务端完成登录。", false);
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
            text.setText("请使用 " + displayName(selectedPlatform) + " 扫码登录，然后点击“检查登录状态”。");
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
                qrTransactionId = "";
                loadProfile();
            } else {
                ToastUtil.show("登录状态：" + string(state, "state"));
            }
        }, this::statusError);
    }

    private void logout() {
        TuneWeaveUiService.request("auth-logout", accountRequest(), ignored -> {
            qrTransactionId = "";
            showLogin("账户已退出");
        }, this::statusError);
    }

    private JsonObject accountRequest() {
        JsonObject request = new JsonObject();
        request.addProperty("platform", selectedPlatform);
        request.addProperty("account", "default");
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

    private static String platformBadge(String id) {
        return switch (id) {
            // Text marks keep the selector self-contained: no remote brand
            // image is fetched before the player has explicitly connected.
            case "netease" -> "♫  网易云音乐";
            case "qq" -> "Q♪  QQ 音乐";
            case "bilibili" -> "▷  哔哩哔哩";
            case "kugou" -> "K  酷狗音乐";
            case "kuwo" -> "W  酷我音乐";
            case "migu" -> "M  咪咕音乐";
            default -> id;
        };
    }

    private static String displayName(String id) {
        return platformBadge(id);
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
