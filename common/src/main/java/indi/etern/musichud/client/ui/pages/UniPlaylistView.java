package indi.etern.musichud.client.ui.pages;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.ImageButton;
import icyllis.modernui.widget.ImageView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.ArrayAdapter;
import icyllis.modernui.widget.Spinner;
import icyllis.modernui.widget.TextView;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.client.services.tuneweave.TuneWeaveClientService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.Modal;
import indi.etern.musichud.client.ui.components.PlatformSelector;
import indi.etern.musichud.client.ui.components.RouterContainer;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.ButtonInsetBackgroundFactory;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/** Local TuneWeave Uni Playlist directory and management entry point. */
public final class UniPlaylistView extends LinearLayout {
    private final TuneWeaveClientService tuneWeave = TuneWeaveClientService.getInstance();
    private final LinearLayout list;
    private final TextView progress;

    public UniPlaylistView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(24), dp(16), dp(24));

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        title.setText(I18n.get(MusicHud.MOD_ID + ".text.page.uniPlaylists"));
        title.setTextSize(Theme.TEXT_SIZE_LARGER);
        title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        toolbar.addView(title, new LayoutParams(0, WRAP_CONTENT, 1));
        toolbar.addView(actionButton(context, "refresh", v -> refresh()), actionParams());
        addView(toolbar, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.RIGHT);
            actions.addView(actionButton(context, "create", v -> UniPlaylistMetadataDialog.show(
                getContext(), ".text.uniPlaylist.create", "", "", this::create)),
                actionParams());
            actions.addView(actionButton(context, "import", v -> showMultiImportDialog()),
                actionParams());
        actions.addView(actionButton(context, "importDocument", v -> importDocument()), actionParams());
        LayoutParams actionsParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        actionsParams.setMargins(0, dp(8), 0, 0);
        addView(actions, actionsParams);

        TextView hint = new TextView(context);
        hint.setText(I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.description"));
        hint.setTextSize(Theme.TEXT_SIZE_NORMAL);
        hint.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        LayoutParams hintParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        hintParams.setMargins(0, dp(4), 0, dp(16));
        addView(hint, hintParams);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        list = new LinearLayout(context);
        list.setOrientation(VERTICAL);
        scroll.addView(list, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        addView(scroll, new LayoutParams(MATCH_PARENT, 0, 1));
        progress = new TextView(context);
        progress.setTextSize(Theme.TEXT_SIZE_NORMAL);
        progress.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        progress.setVisibility(View.GONE);
        addView(progress, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { refresh(); }
            @Override public void onViewDetachedFromWindow(View v) { }
        });
    }

    public void refresh() {
        showProgress(I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.loading"));
        MusicHud.EXECUTOR.execute(() -> {
            try {
                List<TuneWeaveClientService.UniPlaylistInfo> playlists = tuneWeave.listUniPlaylists();
                MuiModApi.postToUiThread(() -> render(playlists));
            } catch (RuntimeException error) {
                MuiModApi.postToUiThread(() -> showProgress(error.getMessage()));
            }
        });
    }

    private void render(List<TuneWeaveClientService.UniPlaylistInfo> playlists) {
        list.removeAllViews();
        if (playlists.isEmpty()) {
            showProgress(I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.empty"));
            return;
        }
        progress.setVisibility(View.GONE);
        for (TuneWeaveClientService.UniPlaylistInfo playlist : playlists) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(ButtonInsetBackgroundFactory.builder().cornerRadius(dp(6)).inset(dp(1)).build().newBackgroundDrawable());
            TextView name = new TextView(getContext());
            name.setText(playlist.name());
            name.setTextSize(Theme.TEXT_SIZE_LARGE);
            name.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            row.addView(name, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            TextView description = new TextView(getContext());
            description.setText(playlist.description().isBlank() ? I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.noDescription") : playlist.description());
            description.setTextSize(Theme.TEXT_SIZE_NORMAL);
            description.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            row.addView(description, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            LinearLayout buttons = new LinearLayout(getContext());
            buttons.setGravity(Gravity.RIGHT);
            buttons.addView(actionButton(getContext(), "open", v -> {
                RouterContainer router = RouterContainer.getInstance();
                if (router != null) router.pushNavigate(new UniPlaylistDetailView(getContext(), playlist));
            }), actionParams());
            buttons.addView(actionButton(getContext(), "edit", v -> UniPlaylistMetadataDialog.show(
                    getContext(), ".text.uniPlaylist.edit", playlist.name(), playlist.description(),
                    (nameValue, descriptionValue) -> update(playlist, nameValue, descriptionValue))),
                    actionParams());
            buttons.addView(actionButton(getContext(), "export", v -> export(playlist)), actionParams());
            buttons.addView(actionButton(getContext(), "delete", v -> confirmDelete(playlist)), actionParams());
            row.addView(buttons, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            LayoutParams rowParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            rowParams.setMargins(0, 0, 0, dp(8));
            list.addView(row, rowParams);
        }
    }

    private ImageButton actionButton(Context context, String action, View.OnClickListener listener) {
        String key = switch (action) {
            case "create" -> ".button.create";
            case "import" -> ".button.import";
            case "open" -> ".button.open";
            case "edit" -> ".button.edit";
            case "delete" -> ".button.delete";
            case "export" -> ".button.export";
            case "importDocument" -> ".button.importDocument";
            default -> ".button.refresh";
        };
        String icon = switch (action) {
            case "create" -> "list_plus.png";
            case "import" -> "link.png";
            case "importDocument" -> "unlink.png";
            case "open" -> "arrow_left.png";
            case "edit" -> "settings.png";
            case "export" -> "link.png";
            case "delete" -> "trash_2.png";
            default -> "rotate_cw.png";
        };
        ImageButton button = new ImageButton(context);
        String label = I18n.get(MusicHud.MOD_ID + key);
        button.setTooltipText(label);
        button.setContentDescription(label);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Image image = ImageUtils.getImageFromResource(
                "/assets/music_hud/textures/gui/icons/" + icon);
        if (image != null) {
            button.setImageDrawable(new InsetDrawable(new ScaledImageDrawable(
                    context.getResources(), image, dp(16), dp(16)), dp(5)));
        }
        if ("open".equals(action)) button.setRotation(180);
        button.setBackground(ButtonInsetBackgroundFactory.builder().cornerRadius(dp(4)).inset(dp(1)).build().newBackgroundDrawable());
        button.setOnClickListener(listener);
        return button;
    }

    private LayoutParams actionParams() {
        LayoutParams params = new LayoutParams(dp(32), dp(32));
        params.setMargins(dp(1), 0, dp(1), 0);
        return params;
    }

    private void create(String name, String description) {
        MusicHud.EXECUTOR.execute(() -> {
            try { tuneWeave.createUniPlaylist(name, description); refreshOnUi(); }
            catch (RuntimeException error) { MuiModApi.postToUiThread(() -> showProgress(error.getMessage())); }
        });
    }

    private void showMultiImportDialog() {
        UniPlaylistImportDialog.showCreate(getContext(), this::refresh,
                error -> showProgress(error == null ? "" : error));
    }

    private void showImportDialog() {
        LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(VERTICAL);
        EditText name = new EditText(getContext(), null, R.attr.editTextOutlinedStyle);
        name.setSingleLine(true);
        name.setHint(I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.nameHint"));
        form.addView(name, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        Spinner sourceMode = new Spinner(getContext());
        sourceMode.setAdapter(new ArrayAdapter<>(getContext(), new String[]{
                I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.source.account"),
                I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.source.search"),
                I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.source.manual")
        }));
        form.addView(sourceMode, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        PlatformSelector platform = new PlatformSelector(getContext(), TuneWeavePlatform.values());
        platform.setSelectedPlatform(tuneWeave.defaultPlatform());
        form.addView(platform, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        Spinner accountPlaylist = new Spinner(getContext());
        accountPlaylist.setAdapter(new ArrayAdapter<>(getContext(), new String[]{
                I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.loading")
        }));
        form.addView(accountPlaylist, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        @SuppressWarnings("unchecked")
        List<Playlist>[] accountPlaylists = new List[]{List.of()};
        AtomicInteger playlistLoadGeneration = new AtomicInteger();

        String[] sourceTypes = {"playlist", "favorite_tracks", "season", "favorite_folder"};
        Spinner type = new Spinner(getContext());
        type.setAdapter(new ArrayAdapter<>(getContext(), new String[]{
                I18n.get(MusicHud.MOD_ID + ".uniPlaylist.source.playlist"),
                I18n.get(MusicHud.MOD_ID + ".uniPlaylist.source.favoriteTracks"),
                I18n.get(MusicHud.MOD_ID + ".uniPlaylist.source.season"),
                I18n.get(MusicHud.MOD_ID + ".uniPlaylist.source.favoriteFolder")
        }));
        form.addView(type, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        EditText id = new EditText(getContext(), null, R.attr.editTextOutlinedStyle);
        id.setSingleLine(true);
        id.setHint(I18n.get(MusicHud.MOD_ID + ".field.hint.searchMusic"));
        form.addView(id, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        TextView sourceStatus = new TextView(getContext());
        sourceStatus.setTextSize(Theme.TEXT_SIZE_SMALL);
        sourceStatus.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        form.addView(sourceStatus, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        Runnable updateSourceControls = () -> {
            int mode = sourceMode.getSelectedItemPosition();
            accountPlaylist.setVisibility(mode == 0 ? VISIBLE : GONE);
            type.setVisibility(mode == 2 ? VISIBLE : GONE);
            id.setVisibility(mode == 0 ? GONE : VISIBLE);
            id.setHint(I18n.get(MusicHud.MOD_ID + (mode == 1
                    ? ".field.hint.searchMusic" : ".text.uniPlaylist.sourceIdHint")));
        };
        Runnable loadAccountPlaylists = () -> {
            TuneWeavePlatform selected = selectedPlatform(platform);
            int generation = playlistLoadGeneration.incrementAndGet();
            sourceStatus.setText(I18n.get(MusicHud.MOD_ID + ".text.platformPlaylist.loading"));
            MusicHud.EXECUTOR.execute(() -> {
                try {
                    var collections = tuneWeave.loadAccountPlaylists(selected);
                    List<Playlist> loaded = new ArrayList<>();
                    loaded.addAll(collections.getCreatedPlaylist());
                    loaded.addAll(collections.getSubscribedPlaylist());
                    MuiModApi.postToUiThread(() -> {
                        if (generation != playlistLoadGeneration.get()) return;
                        accountPlaylists[0] = List.copyOf(loaded);
                        accountPlaylist.setAdapter(new ArrayAdapter<>(getContext(), loaded.stream()
                                .map(Playlist::getName).toArray(String[]::new)));
                        sourceStatus.setText(loaded.isEmpty()
                                ? I18n.get(MusicHud.MOD_ID + ".text.platformPlaylist.empty") : "");
                    });
                } catch (RuntimeException error) {
                    MuiModApi.postToUiThread(() -> {
                        if (generation == playlistLoadGeneration.get()) {
                            accountPlaylists[0] = List.of();
                            sourceStatus.setText(error.getMessage());
                        }
                    });
                }
            });
        };
        sourceMode.setOnItemSelectedListener((parent, view, position, itemId) -> {
            updateSourceControls.run();
            if (position == 0) loadAccountPlaylists.run();
        });
        platform.setOnPlatformSelectedListener(selected -> {
            if (sourceMode.getSelectedItemPosition() == 0) loadAccountPlaylists.run();
        });
        updateSourceControls.run();
        loadAccountPlaylists.run();

        TextView dialogTitle = new TextView(getContext());
        dialogTitle.setText(I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.importSource"));
        Modal modal = new Modal(getContext(),
                dialogTitle, form,
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".button.confirm"), (button, dialog) -> {
                    int mode = sourceMode.getSelectedItemPosition();
                    String input = id.getText().toString().trim();
                    Playlist selectedPlaylist = mode == 0
                            && accountPlaylist.getSelectedItemPosition() >= 0
                            && accountPlaylist.getSelectedItemPosition() < accountPlaylists[0].size()
                            ? accountPlaylists[0].get(accountPlaylist.getSelectedItemPosition()) : null;
                    if ((mode == 0 && selectedPlaylist == null) || (mode != 0 && input.isBlank())) return;
                    TuneWeavePlatform selected = selectedPlatform(platform);
                    String playlistName = name.getText().toString().trim();
                    if (mode == 1) {
                        button.setEnabled(false);
                        sourceStatus.setText(I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.searching"));
                        MusicHud.EXECUTOR.execute(() -> {
                            try {
                                @SuppressWarnings("unchecked")
                                List<Playlist> results = (List<Playlist>) tuneWeave.search(
                                        input, SearchType.PLAYLIST, 0, selected);
                                MuiModApi.postToUiThread(() -> {
                                    button.setEnabled(true);
                                    if (results.isEmpty()) {
                                        sourceStatus.setText(I18n.get(MusicHud.MOD_ID + ".text.searchNoMoreResult"));
                                        return;
                                    }
                                    dialog.dismiss();
                                    showSearchImportDialog(results, selected, playlistName);
                                });
                            } catch (RuntimeException error) {
                                MuiModApi.postToUiThread(() -> {
                                    button.setEnabled(true);
                                    sourceStatus.setText(error.getMessage());
                                });
                            }
                        });
                        return;
                    }
                    dialog.dismiss();
                    MusicHud.EXECUTOR.execute(() -> {
                        try {
                            String sourceId = mode == 0
                                    ? importSourceId(selectedPlaylist, selected) : input;
                            String sourceType = mode == 0 ? importSourceType(selectedPlaylist, selected)
                                    : sourceTypes[type.getSelectedItemPosition()];
                            tuneWeave.importUniPlaylistSources(playlistName, List.of(
                                    new TuneWeaveClientService.UniImportSource(
                                            selected.apiName(), sourceType, sourceId)));
                            refreshOnUi();
                        } catch (RuntimeException error) {
                            MuiModApi.postToUiThread(() -> showProgress(error.getMessage()));
                        }
                    });
                }),
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".button.cancel"), (button, dialog) -> dialog.dismiss()));
        modal.show();
    }

    private void showSearchImportDialog(List<Playlist> results, TuneWeavePlatform platform,
                                        String requestedName) {
        Spinner resultSpinner = new Spinner(getContext());
        resultSpinner.setAdapter(new ArrayAdapter<>(getContext(), results.stream()
                .map(playlist -> playlist.getName() + " (" + playlist.getMusicTrackCount() + ")")
                .toArray(String[]::new)));

        TextView title = new TextView(getContext());
        title.setText(I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.selectSearchResult"));
        new Modal(getContext(), title, resultSpinner,
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".button.import"), (button, dialog) -> {
                    int index = resultSpinner.getSelectedItemPosition();
                    if (index < 0 || index >= results.size()) return;
                    Playlist selected = results.get(index);
                    dialog.dismiss();
                    MusicHud.EXECUTOR.execute(() -> {
                        try {
                            tuneWeave.importUniPlaylistSources(
                                    requestedName.isBlank() ? selected.getName() : requestedName,
                                    List.of(new TuneWeaveClientService.UniImportSource(
                                            platform.apiName(), importSourceType(selected, platform),
                                            importSourceId(selected, platform))));
                            refreshOnUi();
                        } catch (RuntimeException error) {
                            MuiModApi.postToUiThread(() -> showProgress(error.getMessage()));
                        }
                    });
                }),
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".button.cancel"),
                        (button, dialog) -> dialog.dismiss())).show();
    }

    private static TuneWeavePlatform selectedPlatform(PlatformSelector selector) {
        return selector.getSelectedPlatform();
    }

    private static String referenceId(String reference, TuneWeavePlatform platform) {
        String prefix = platform.apiName() + ':';
        return reference != null && reference.startsWith(prefix)
                ? reference.substring(prefix.length()) : reference;
    }

    private static String importSourceType(Playlist playlist, TuneWeavePlatform platform) {
        String id = referenceId(playlist.getSourceRef(), platform);
        if (platform == TuneWeavePlatform.BILIBILI && id != null) {
            if (id.startsWith("favorite:")) return "favorite_folder";
            if (id.startsWith("season:")) return "season";
        }
        return "playlist";
    }

    private static String importSourceId(Playlist playlist, TuneWeavePlatform platform) {
        String id = referenceId(playlist.getSourceRef(), platform);
        if (platform == TuneWeavePlatform.BILIBILI && id != null) {
            int separator = id.indexOf(':');
            if (separator >= 0 && separator + 1 < id.length()) return id.substring(separator + 1);
        }
        return id;
    }

    private void importDocument() {
        String path = TinyFileDialogs.tinyfd_openFileDialog(
                I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.importDocument"), "", null, "JSON document", false);
        if (path == null || path.isBlank()) return;
        MusicHud.EXECUTOR.execute(() -> {
            try {
                Path file = Path.of(path);
                if (Files.size(file) > 4 * 1024 * 1024) {
                    throw new IllegalArgumentException("Uni Playlist document is too large");
                }
                JsonObject document = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!"tuneweave_uni_playlist_v1".equals(document.get("format").getAsString())) {
                    throw new IllegalArgumentException("Unsupported Uni Playlist document format");
                }
                tuneWeave.importUniPlaylistDocument(document);
                refreshOnUi();
            }
            catch (RuntimeException error) { MuiModApi.postToUiThread(() -> showProgress(error.getMessage())); }
            catch (Exception error) { MuiModApi.postToUiThread(() -> showProgress(error.getMessage())); }
        });
    }

    private void export(TuneWeaveClientService.UniPlaylistInfo playlist) {
        String safeName = playlist.name().replaceAll("[^a-zA-Z0-9._-]+", "_");
        String path = TinyFileDialogs.tinyfd_saveFileDialog(
                I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.exportDocument"),
                safeName.isBlank() ? "uni-playlist.json" : safeName + ".json", null, "JSON document");
        if (path == null || path.isBlank()) return;
        MusicHud.EXECUTOR.execute(() -> {
            try {
                JsonObject document = tuneWeave.exportUniPlaylist(playlist.reference());
                Files.writeString(Path.of(path), document.toString(), StandardCharsets.UTF_8);
                MuiModApi.postToUiThread(() -> showProgress(I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.exported")));
            } catch (Exception error) {
                MuiModApi.postToUiThread(() -> showProgress(error.getMessage()));
            }
        });
    }

    private void update(TuneWeaveClientService.UniPlaylistInfo playlist, String name, String description) {
        MusicHud.EXECUTOR.execute(() -> {
            try { tuneWeave.updateUniPlaylist(playlist.reference(), name, description); refreshOnUi(); }
            catch (RuntimeException error) { MuiModApi.postToUiThread(() -> showProgress(error.getMessage())); }
        });
    }

    private void confirmDelete(TuneWeaveClientService.UniPlaylistInfo playlist) {
        TextView text = new TextView(getContext());
        text.setText(I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.deleteWarning"));
        TextView title = new TextView(getContext());
        title.setText(playlist.name());
        new Modal(getContext(), title, text,
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".button.delete"), (button, dialog) -> {
                    dialog.dismiss();
                    MusicHud.EXECUTOR.execute(() -> { tuneWeave.deleteUniPlaylist(playlist.reference()); refreshOnUi(); });
                }),
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".button.cancel"), (button, dialog) -> dialog.dismiss())).show();
    }

    private void refreshOnUi() {
        MuiModApi.postToUiThread(this::refresh);
    }

    private void showProgress(String message) { progress.setText(message); progress.setVisibility(View.VISIBLE); }
}
