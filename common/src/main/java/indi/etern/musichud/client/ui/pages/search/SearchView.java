package indi.etern.musichud.client.ui.pages.search;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.HorizontalScrollView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.client.services.TuneWeaveUiService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.MusicHudIconButton;
import indi.etern.musichud.client.ui.utils.ui.ButtonInsetBackgroundFactory;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.payloads.requestResponseCycle.SearchRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.minecraft.client.resources.language.I18n;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class SearchView extends LinearLayout {
    @Getter
    private static SearchView instance = null;
    @Getter
    private final Map<SearchType, SearchMeta> searchMetas = new HashMap<>();
    @Getter
    private final HashSet<Consumer<SearchMeta>> searchRefreshListeners = new HashSet<>();
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private EditText searchTextInput;
    private SearchResultTabPage searchResultTabPage;
    private LinearLayout platformSelector;
    private HorizontalScrollView platformSelectorScroll;
    private final List<JsonObject> platforms = new ArrayList<>();
    private String selectedPlatform = "";
    @Getter
    private String searchText;
    private static final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();

    public SearchView(Context context) {
        super(context);
        instance = this;
        refresh();
    }

    public void refresh() {
        Context context = getContext();
        removeAllViews();
        setOrientation(VERTICAL);

        boolean enabled = clientConfig.getEnable();
        if (MusicHud.getConnectStatus() != MusicHud.ConnectStatus.CONNECTED && !ClientConfig.getInstance().getEnableIsolatedMode() || !enabled) {
            setGravity(Gravity.CENTER);
            TextView textView = Theme.getNotificationTextView(context, enabled);
            addView(textView);
            return;
        }

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(HORIZONTAL);
        LayoutParams topParams = new LayoutParams(MATCH_PARENT, dp(38));
        topParams.setMargins(0, dp(32), 0, 0);
        addView(top, topParams);

        top.addView(new View(context), new LayoutParams(0, WRAP_CONTENT, 2));
        searchTextInput = new EditText(context, null, R.attr.editTextOutlinedStyle);
        searchTextInput.setTextAlignment(SearchView.TEXT_ALIGNMENT_CENTER);
        searchTextInput.setHint(I18n.get(MusicHud.MOD_ID + ".field.hint.searchMusic"));
        searchTextInput.setSingleLine();
        LayoutParams params = new LayoutParams(0, WRAP_CONTENT, 6);
        params.setMargins(dp(52), 0, 0, 0);
        top.addView(searchTextInput, params);

        Button searchButton = new Button(context);
        searchButton.setText(I18n.get(MusicHud.MOD_ID + ".button.searchMusic"));
        LayoutParams buttonParams = new LayoutParams(WRAP_CONTENT, MATCH_PARENT);
        Drawable background = ButtonInsetBackgroundFactory.builder()
                .inset(0).padding(new ButtonInsetBackgroundFactory.Padding(dp(8), 0, dp(8), 0))
                .cornerRadius(dp(4)).build().newBackgroundDrawable();
        searchButton.setBackground(background);
        buttonParams.setMargins(dp(8), 0, 0, 0);
        top.addView(searchButton, buttonParams);

        top.addView(new View(context), new LayoutParams(0, WRAP_CONTENT, 2));

        platformSelectorScroll = new HorizontalScrollView(context);
        platformSelectorScroll.setFillViewport(true);
        platformSelector = new LinearLayout(context);
        platformSelector.setOrientation(HORIZONTAL);
        platformSelector.setGravity(Gravity.CENTER);
        LayoutParams platformParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        platformParams.setMargins(dp(32), dp(12), dp(32), 0);
        platformSelectorScroll.addView(platformSelector, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        addView(platformSelectorScroll, platformParams);
        loadSearchPlatforms();

        searchResultTabPage = new SearchResultTabPage(context);

        LayoutParams resultAreaParams = new LayoutParams(MATCH_PARENT, 0, 1);
        resultAreaParams.setMargins(dp(32), 0, dp(32), 0);
        addView(searchResultTabPage, resultAreaParams);

        searchTextInput.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEY_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                refreshSearch(true);
                return true;
            }
            return false;
        });
        searchButton.setOnClickListener((v) -> refreshSearch(true));

        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                instance = null;
            }
        });
    }

    public void refreshSearch(boolean force) {
        searchText = searchTextInput.getText().toString();
        if (searchText == null || searchText.isEmpty()) return;
        int currentItem = searchResultTabPage.getPager().getCurrentItem();
        SearchType[] searchTypes = {SearchType.MUSIC, SearchType.PLAYLIST, SearchType.ALBUM, SearchType.ARTIST};
        SearchType searchType = searchTypes[currentItem];
        SearchMeta searchMeta = searchMetas.get(searchType);
        if (force || searchMeta == null || !searchMeta.text.equals(searchText)) {
            if (searchMeta != null && searchMeta.pendingFuture != null) {
                searchMeta.pendingFuture.cancel(true);
            }
            SearchMeta searchMeta1 = new SearchMeta(searchType, searchText);
            searchMeta1.pendingFuture = new CompletableFuture<>();
            searchMetas.put(searchType, searchMeta1);
            searchRefreshListeners.forEach(listener -> listener.accept(searchMeta1));
            clientNetworkService.sendToServer(new SearchRequest(searchText, searchType, 0, selectedPlatform));
        }
    }

    public void loadMoreSearchResult() {
        int currentItem = searchResultTabPage.getPager().getCurrentItem();
        SearchType[] searchTypes = {SearchType.MUSIC, SearchType.PLAYLIST, SearchType.ALBUM, SearchType.ARTIST};
        SearchType searchType = searchTypes[currentItem];
        SearchMeta searchMeta = searchMetas.get(searchType);
        String text = searchTextInput.getText().toString();
        if (searchMeta != null && searchMeta.pendingFuture == null && searchMeta.mayHasMore) {
            int offset = searchMeta.nextOffset;
            searchMeta.pendingFuture = new CompletableFuture<>();
            searchRefreshListeners.forEach(listener -> listener.accept(searchMeta));
            clientNetworkService.sendToServer(new SearchRequest(text, searchType, offset, selectedPlatform));
        }
    }

    /** Loads only registered music providers; the server receives the chosen ID directly. */
    private void loadSearchPlatforms() {
        if (platformSelector == null) {
            return;
        }
        platformSelector.removeAllViews();
        TextView loading = new TextView(getContext());
        loading.setText("正在读取音乐平台…");
        loading.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        platformSelector.addView(loading);
        TuneWeaveUiService.request("platforms", new JsonObject(), data -> {
            platforms.clear();
            JsonArray array = data != null && data.isJsonArray() ? data.getAsJsonArray() : new JsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject platform = element.getAsJsonObject();
                String id = string(platform, "platform");
                if (booleanValue(platform, "registered") && !"uni".equals(id)) {
                    platforms.add(platform);
                }
            }
            if (platforms.isEmpty()) {
                platformSelector.removeAllViews();
                TextView unavailable = new TextView(getContext());
                unavailable.setText("TuneWeave 没有注册可搜索的平台");
                unavailable.setTextColor(Theme.ERROR_TEXT_COLOR);
                platformSelector.addView(unavailable);
                return;
            }
            if (selectedPlatform.isBlank() || platforms.stream()
                    .noneMatch(platform -> selectedPlatform.equals(string(platform, "platform")))) {
                JsonObject defaultPlatform = platforms.stream().filter(platform -> booleanValue(platform, "default"))
                        .findFirst().orElse(platforms.getFirst());
                selectedPlatform = string(defaultPlatform, "platform");
            }
            rebuildSearchPlatformSelector();
        }, error -> {
            platformSelector.removeAllViews();
            TextView unavailable = new TextView(getContext());
            unavailable.setText(error);
            unavailable.setTextColor(Theme.ERROR_TEXT_COLOR);
            platformSelector.addView(unavailable);
        });
    }

    private void rebuildSearchPlatformSelector() {
        platformSelector.removeAllViews();
        for (JsonObject platform : platforms) {
            String id = string(platform, "platform");
            MusicHudIconButton button = new MusicHudIconButton(getContext(), platformIconResource(id), platformName(id));
            button.setAlpha(id.equals(selectedPlatform) ? 1f : 0.56f);
            button.setBackground(ButtonInsetBackgroundFactory.builder().cornerRadius(dp(4)).inset(dp(1))
                    .padding(new ButtonInsetBackgroundFactory.Padding(dp(6), 0, dp(6), 0)).build().newBackgroundDrawable());
            button.setOnClickListener(view -> {
                if (!id.equals(selectedPlatform)) {
                    selectedPlatform = id;
                    rebuildSearchPlatformSelector();
                    if (searchTextInput != null && !searchTextInput.getText().toString().isBlank()) {
                        refreshSearch(true);
                    }
                }
            });
            LayoutParams params = new LayoutParams(dp(36), dp(36));
            params.setMargins(0, 0, dp(8), 0);
            platformSelector.addView(button, params);
        }
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

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static boolean booleanValue(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() && object.get(key).getAsBoolean();
    }

    private void refreshSearchMeta(int offset, List<?> result, SearchType searchType) {
        boolean mayHasMore = result.size() == 50;// see also: IMusicApiService.searchXXX(...);
        SearchMeta searchMeta = searchMetas.getOrDefault(searchType, new SearchMeta(searchType, searchText));
        searchMeta.nextOffset = offset + result.size();
        searchMeta.mayHasMore = mayHasMore;
        CompletableFuture<CompletingType> pendingFuture = searchMeta.pendingFuture;
        searchMeta.pendingFuture = null;
        searchMetas.put(searchType, searchMeta);
        if (pendingFuture != null) {
            pendingFuture.complete(mayHasMore ? CompletingType.NORMAL : CompletingType.NO_MORE_RESULT);
        }
    }

    public void setSearchMusicResult(int offset, List<MusicDetail> result) {
        SearchType searchType = SearchType.MUSIC;
        refreshSearchMeta(offset, result, searchType);
        if (offset == 0) {
            SearchMusicResultView.setResult(result);
        } else {
            SearchMusicResultView instance = SearchMusicResultView.getInstance();
            if (instance != null) {
                instance.append(result);
            }
        }
    }

    public void setSearchPlaylistResult(int offset, List<Playlist> result) {
        SearchType searchType = SearchType.PLAYLIST;
        refreshSearchMeta(offset, result, searchType);
        if (offset == 0) {
            SearchPlaylistResultView.setResult(result);
        } else {
            SearchPlaylistResultView instance = SearchPlaylistResultView.getInstance();
            if (instance != null) {
                instance.append(result);
            }
        }
    }

    public void setSearchAlbumResult(int offset, List<Album> result) {
        SearchType searchType = SearchType.ALBUM;
        refreshSearchMeta(offset, result, searchType);
        if (offset == 0) {
            SearchAlbumResultView.setResult(result);
        } else {
            SearchAlbumResultView instance = SearchAlbumResultView.getInstance();
            if (instance != null) {
                instance.append(result);
            }
        }
    }

    public void setSearchArtistResult(int offset, List<Artist> result) {
        SearchType searchType = SearchType.ARTIST;
        refreshSearchMeta(offset, result, searchType);
        if (offset == 0) {
            SearchArtistResultView.setResult(result);
        } else {
            SearchArtistResultView instance = SearchArtistResultView.getInstance();
            if (instance != null) {
                instance.append(result);
            }
        }
    }

    public enum CompletingType {
        NORMAL, NO_MORE_RESULT
    }

    @Data
    @EqualsAndHashCode
    @ToString
    @Getter
    public static final class SearchMeta {
        private final SearchType searchType;
        private final String text;
        CompletableFuture<CompletingType> pendingFuture;
        private int nextOffset = 0;
        private boolean mayHasMore = true;

        private SearchMeta(SearchType searchType, String text) {
            this.searchType = searchType;
            this.text = text;
        }
    }
}
