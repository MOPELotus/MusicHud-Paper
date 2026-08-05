package indi.etern.musichud.client.ui.pages;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.ArrayAdapter;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.Spinner;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.services.tuneweave.TuneWeaveClientService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.Modal;
import indi.etern.musichud.client.ui.components.RouterContainer;
import indi.etern.musichud.client.ui.components.UrlImageView;
import indi.etern.musichud.client.utils.ui.ButtonInsetBackgroundFactory;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/** Consumer-facing podcast and radio catalog. Creator/write endpoints are intentionally absent. */
public final class PodcastRadioView extends LinearLayout {
    private enum Mode { PODCASTS, RADIO, LIBRARY }

    private static final TuneWeavePlatform PLATFORM = TuneWeavePlatform.NETEASE;
    private final TuneWeaveClientService tuneWeave = TuneWeaveClientService.getInstance();
    private final LinearLayout filters;
    private final LinearLayout rows;
    private final TextView status;
    private Mode mode = Mode.PODCASTS;
    private List<TuneWeaveClientService.PodcastCategoryInfo> podcastCategories = List.of();
    private List<TuneWeaveClientService.RadioOptionInfo> radioCategories = List.of();
    private List<TuneWeaveClientService.RadioOptionInfo> radioRegions = List.of();
    private String podcastCategoryId = "";
    private String radioCategoryId = "";
    private String radioRegionId = "";

    public PodcastRadioView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(24), dp(16), dp(24));

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(action(".button.back", v -> RouterContainer.getInstance().popNavigate()),
                new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        TextView title = new TextView(context);
        title.setText(I18n.get(MusicHud.MOD_ID + ".text.programs.title"));
        title.setTextSize(Theme.TEXT_SIZE_LARGER);
        title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        toolbar.addView(title, new LayoutParams(0, WRAP_CONTENT, 1));
        toolbar.addView(action(".text.programs.podcasts", v -> switchMode(Mode.PODCASTS)),
                new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        toolbar.addView(action(".text.programs.radio", v -> switchMode(Mode.RADIO)),
                new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        toolbar.addView(action(".text.programs.library", v -> switchMode(Mode.LIBRARY)),
                new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        toolbar.addView(action(".button.refresh", v -> refresh()), new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        addView(toolbar, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        TextView description = new TextView(context);
        description.setText(I18n.get(MusicHud.MOD_ID + ".text.programs.description"));
        description.setTextSize(Theme.TEXT_SIZE_NORMAL);
        description.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        LayoutParams descriptionParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        descriptionParams.setMargins(dp(48), dp(4), 0, dp(8));
        addView(description, descriptionParams);

        filters = new LinearLayout(context);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        addView(filters, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        status = new TextView(context);
        status.setTextSize(Theme.TEXT_SIZE_NORMAL);
        status.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        LayoutParams statusParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        statusParams.setMargins(dp(48), dp(4), 0, dp(10));
        addView(status, statusParams);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        rows = new LinearLayout(context);
        rows.setOrientation(VERTICAL);
        scroll.addView(rows, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        addView(scroll, new LayoutParams(MATCH_PARENT, 0, 1));

        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { refresh(); }
            @Override public void onViewDetachedFromWindow(View v) { }
        });
    }

    private void switchMode(Mode next) {
        if (mode == next) return;
        mode = next;
        refresh();
    }

    private void refresh() {
        showStatus(I18n.get(MusicHud.MOD_ID + ".text.programs.loading"));
        MusicHud.EXECUTOR.execute(() -> {
            try {
                switch (mode) {
                    case PODCASTS -> {
                        List<TuneWeaveClientService.PodcastCategoryInfo> categories = tuneWeave.loadPodcastCategories(PLATFORM);
                        List<TuneWeaveClientService.PodcastInfo> podcasts = tuneWeave.loadPodcasts(PLATFORM, podcastCategoryId);
                        MuiModApi.postToUiThread(() -> renderPodcasts(categories, podcasts));
                    }
                    case RADIO -> {
                        TuneWeaveClientService.RadioTaxonomyInfo taxonomy = tuneWeave.loadRadioTaxonomy(PLATFORM);
                        List<TuneWeaveClientService.RadioStationInfo> stations = new ArrayList<>(
                                tuneWeave.loadRadioStations(PLATFORM, radioCategoryId, radioRegionId));
                        for (TuneWeaveClientService.RadioStationInfo station : tuneWeave.loadStyledRadioStations(PLATFORM)) {
                            if (stations.stream().noneMatch(value -> value.reference().equals(station.reference()))) {
                                stations.add(station);
                            }
                        }
                        MuiModApi.postToUiThread(() -> renderRadio(taxonomy, stations));
                    }
                    case LIBRARY -> {
                        List<TuneWeaveClientService.PodcastInfo> podcasts = tuneWeave.loadAccountPodcasts(PLATFORM);
                        List<TuneWeaveClientService.RadioStationInfo> stations = tuneWeave.loadAccountRadioStations(PLATFORM);
                        MuiModApi.postToUiThread(() -> renderLibrary(podcasts, stations));
                    }
                }
            } catch (RuntimeException error) {
                MuiModApi.postToUiThread(() -> showStatus(message(error)));
            }
        });
    }

    private void renderPodcasts(List<TuneWeaveClientService.PodcastCategoryInfo> categories,
                                List<TuneWeaveClientService.PodcastInfo> podcasts) {
        podcastCategories = List.copyOf(categories);
        filters.removeAllViews();
        Spinner category = spinner(categoryLabels(categories, ".text.programs.allCategories"));
        category.setSelection(indexOfPodcastCategory());
        category.setOnItemSelectedListener((parent, view, position, id) -> {
            String next = position == 0 ? "" : podcastCategories.get(position - 1).id();
            if (!next.equals(podcastCategoryId)) {
                podcastCategoryId = next;
                refresh();
            }
        });
        filters.addView(category, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        filters.addView(label(I18n.get(MusicHud.MOD_ID + ".text.programs.podcastCount")
                .replace("{}", Integer.toString(podcasts.size()))), new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        renderRows(rowsFor(podcasts, this::openPodcast));
    }

    private void renderRadio(TuneWeaveClientService.RadioTaxonomyInfo taxonomy,
                             List<TuneWeaveClientService.RadioStationInfo> stations) {
        radioCategories = List.copyOf(taxonomy.categories());
        radioRegions = List.copyOf(taxonomy.regions());
        filters.removeAllViews();
        Spinner category = spinner(optionLabels(radioCategories, ".text.programs.allCategories"));
        Spinner region = spinner(optionLabels(radioRegions, ".text.programs.allRegions"));
        category.setSelection(indexOfOption(radioCategories, radioCategoryId));
        region.setSelection(indexOfOption(radioRegions, radioRegionId));
        category.setOnItemSelectedListener((parent, view, position, id) -> {
            String next = position == 0 ? "" : radioCategories.get(position - 1).id();
            if (!next.equals(radioCategoryId)) { radioCategoryId = next; refresh(); }
        });
        region.setOnItemSelectedListener((parent, view, position, id) -> {
            String next = position == 0 ? "" : radioRegions.get(position - 1).id();
            if (!next.equals(radioRegionId)) { radioRegionId = next; refresh(); }
        });
        filters.addView(category, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        filters.addView(region, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        filters.addView(label(I18n.get(MusicHud.MOD_ID + ".text.programs.stationCount")
                .replace("{}", Integer.toString(stations.size()))), new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        renderRows(rowsFor(stations, this::openRadio));
    }

    private void renderLibrary(List<TuneWeaveClientService.PodcastInfo> podcasts,
                               List<TuneWeaveClientService.RadioStationInfo> stations) {
        filters.removeAllViews();
        filters.addView(label(I18n.get(MusicHud.MOD_ID + ".text.programs.libraryCount")
                .replace("{podcasts}", Integer.toString(podcasts.size()))
                .replace("{stations}", Integer.toString(stations.size()))),
                new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        List<View> result = new ArrayList<>();
        if (!podcasts.isEmpty()) result.add(section(I18n.get(MusicHud.MOD_ID + ".text.programs.podcasts")));
        result.addAll(rowsFor(podcasts, this::openPodcast));
        if (!stations.isEmpty()) result.add(section(I18n.get(MusicHud.MOD_ID + ".text.programs.radio")));
        result.addAll(rowsFor(stations, this::openRadio));
        renderRows(result);
    }

    private List<View> rowsFor(List<?> values, Consumer<Object> opener) {
        List<View> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof TuneWeaveClientService.PodcastInfo podcast) {
                result.add(programRow(podcast, v -> opener.accept(podcast)));
            } else if (value instanceof TuneWeaveClientService.RadioStationInfo station) {
                result.add(programRow(station, v -> opener.accept(station)));
            }
        }
        return result;
    }

    private void renderRows(List<View> values) {
        rows.removeAllViews();
        if (values.isEmpty()) {
            showStatus(I18n.get(MusicHud.MOD_ID + ".text.programs.empty"));
            return;
        }
        status.setText("");
        for (View value : values) {
            if (value.getTag() instanceof String tag && "section".equals(tag)) {
                rows.addView(value, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            } else {
                rows.addView(value, rowParams());
            }
        }
    }

    private View programRow(TuneWeaveClientService.PodcastInfo podcast, View.OnClickListener open) {
        return programRow(podcast.name(), podcast.description(), podcast.coverUrl(),
                I18n.get(MusicHud.MOD_ID + ".text.programs.episodes")
                        .replace("{}", Long.toString(podcast.episodeCount())), open,
                podcast.subscribed(), v -> togglePodcast(podcast));
    }

    private View programRow(TuneWeaveClientService.RadioStationInfo station, View.OnClickListener open) {
        String detail = station.currentProgram().isBlank() ? station.category() : station.currentProgram();
        return programRow(station.name(), station.description(), station.coverUrl(), detail, open,
                station.subscribed(), v -> toggleRadio(station));
    }

    private View programRow(String name, String description, String coverUrl, String detail,
                            View.OnClickListener open, boolean subscribed, View.OnClickListener toggle) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(ButtonInsetBackgroundFactory.builder().cornerRadius(dp(6)).inset(dp(1))
                .build().newBackgroundDrawable());
        UrlImageView cover = new UrlImageView(getContext());
        cover.setCornerRadius(dp(8));
        cover.loadUrl(coverUrl.isBlank() ? MusicHud.ICON_BASE64 : coverUrl);
        row.addView(cover, new LayoutParams(dp(68), dp(68)));
        LinearLayout text = new LinearLayout(getContext());
        text.setOrientation(VERTICAL);
        TextView title = new TextView(getContext());
        title.setText(name);
        title.setTextSize(Theme.TEXT_SIZE_LARGE);
        title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        title.setMaxLines(2);
        text.addView(title, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        TextView sub = new TextView(getContext());
        sub.setText(detail + (description.isBlank() ? "" : "  ·  " + description));
        sub.setTextSize(Theme.TEXT_SIZE_SMALL);
        sub.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        sub.setMaxLines(2);
        text.addView(sub, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        LayoutParams textParams = new LayoutParams(0, WRAP_CONTENT, 1);
        textParams.setMargins(dp(12), 0, dp(8), 0);
        row.addView(text, textParams);
        row.addView(action(".button.open", open), new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        row.addView(action(subscribed ? ".button.unsubscribe" : ".button.subscribe", toggle),
                new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        row.setOnClickListener(open);
        return row;
    }

    private void openPodcast(Object value) {
        if (value instanceof TuneWeaveClientService.PodcastInfo podcast) {
            RouterContainer.getInstance().pushNavigate(new PodcastDetailView(getContext(), podcast));
        }
    }

    private void openRadio(Object value) {
        if (value instanceof TuneWeaveClientService.RadioStationInfo station) {
            RouterContainer.getInstance().pushNavigate(new RadioDetailView(getContext(), station));
        }
    }

    private void togglePodcast(TuneWeaveClientService.PodcastInfo podcast) {
        mutate(() -> tuneWeave.setPodcastSubscribed(podcast, !podcast.subscribed()));
    }

    private void toggleRadio(TuneWeaveClientService.RadioStationInfo station) {
        mutate(() -> tuneWeave.setRadioStationSubscribed(station, !station.subscribed()));
    }

    private void mutate(Runnable operation) {
        showStatus(I18n.get(MusicHud.MOD_ID + ".text.programs.saving"));
        MusicHud.EXECUTOR.execute(() -> {
            try { operation.run(); MuiModApi.postToUiThread(this::refresh); }
            catch (RuntimeException error) { MuiModApi.postToUiThread(() -> showStatus(message(error))); }
        });
    }

    private Spinner spinner(List<String> values) {
        Spinner spinner = new Spinner(getContext());
        spinner.setAdapter(new ArrayAdapter<>(getContext(), values.toArray(String[]::new)));
        return spinner;
    }

    private Button action(String key, View.OnClickListener listener) {
        Button button = new Button(getContext());
        button.setText(key.startsWith(".") ? I18n.get(MusicHud.MOD_ID + key) : key);
        button.setTextSize(Theme.TEXT_SIZE_SMALL);
        button.setTextColor(Theme.PRIMARY_COLOR);
        button.setBackground(ButtonInsetBackgroundFactory.builder().cornerRadius(dp(4)).inset(dp(1))
                .build().newBackgroundDrawable());
        button.setOnClickListener(listener);
        return button;
    }

    private TextView label(String text) {
        TextView value = new TextView(getContext());
        value.setText(text);
        value.setTextSize(Theme.TEXT_SIZE_SMALL);
        value.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.setMargins(dp(12), 0, 0, 0);
        value.setLayoutParams(params);
        return value;
    }

    private TextView section(String text) {
        TextView value = label(text);
        value.setTag("section");
        value.setTextSize(Theme.TEXT_SIZE_LARGE);
        value.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        value.setPadding(0, dp(12), 0, dp(6));
        return value;
    }

    private List<String> categoryLabels(List<TuneWeaveClientService.PodcastCategoryInfo> values, String allKey) {
        List<String> result = new ArrayList<>();
        result.add(I18n.get(MusicHud.MOD_ID + allKey));
        values.forEach(value -> result.add(value.name()));
        return result;
    }

    private List<String> optionLabels(List<TuneWeaveClientService.RadioOptionInfo> values, String allKey) {
        List<String> result = new ArrayList<>();
        result.add(I18n.get(MusicHud.MOD_ID + allKey));
        values.forEach(value -> result.add(value.name()));
        return result;
    }

    private int indexOfPodcastCategory() {
        for (int i = 0; i < podcastCategories.size(); i++) {
            if (podcastCategories.get(i).id().equals(podcastCategoryId)) return i + 1;
        }
        return 0;
    }

    private static int indexOfOption(List<TuneWeaveClientService.RadioOptionInfo> values, String id) {
        for (int i = 0; i < values.size(); i++) if (values.get(i).id().equals(id)) return i + 1;
        return 0;
    }

    private LayoutParams rowParams() {
        LayoutParams params = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(6));
        return params;
    }

    private void showStatus(String text) { status.setText(text == null ? "" : text); }

    private static String message(RuntimeException error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }
}
