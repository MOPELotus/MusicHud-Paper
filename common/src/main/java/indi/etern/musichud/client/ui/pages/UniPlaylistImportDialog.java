package indi.etern.musichud.client.ui.pages;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.widget.ArrayAdapter;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.Spinner;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.client.services.tuneweave.TuneWeaveClientService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.Modal;
import indi.etern.musichud.client.ui.components.PlatformSelector;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static icyllis.modernui.view.View.GONE;
import static icyllis.modernui.view.View.VISIBLE;
import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/** Selects one remote collection and appends its materialized items to a local Uni Playlist. */
final class UniPlaylistImportDialog {
    private static final TuneWeaveClientService TUNE_WEAVE = TuneWeaveClientService.getInstance();
    private static final String[] SOURCE_TYPES = {"playlist", "favorite_tracks", "season", "favorite_folder"};

    private UniPlaylistImportDialog() {
    }

    static void show(Context context, TuneWeaveClientService.UniPlaylistInfo target,
                     Runnable onImported, Consumer<String> onError) {
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);

        Spinner sourceMode = new Spinner(context);
        sourceMode.setAdapter(new ArrayAdapter<>(context, new String[]{
                text(".text.uniPlaylist.source.account"),
                text(".text.uniPlaylist.source.search"),
                text(".text.uniPlaylist.source.manual")
        }));
        form.addView(sourceMode, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        PlatformSelector platform = new PlatformSelector(context, TuneWeavePlatform.values());
        platform.setSelectedPlatform(TUNE_WEAVE.defaultPlatform());
        form.addView(platform, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        Spinner accountPlaylist = new Spinner(context);
        accountPlaylist.setAdapter(new ArrayAdapter<>(context, new String[]{text(".text.uniPlaylist.loading")}));
        form.addView(accountPlaylist, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        @SuppressWarnings("unchecked")
        List<Playlist>[] accountPlaylists = new List[]{List.of()};
        AtomicInteger loadGeneration = new AtomicInteger();

        Spinner type = new Spinner(context);
        type.setAdapter(new ArrayAdapter<>(context, new String[]{
                text(".uniPlaylist.source.playlist"),
                text(".uniPlaylist.source.favoriteTracks"),
                text(".uniPlaylist.source.season"),
                text(".uniPlaylist.source.favoriteFolder")
        }));
        form.addView(type, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        EditText input = new EditText(context, null, R.attr.editTextOutlinedStyle);
        input.setSingleLine(true);
        form.addView(input, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        TextView status = new TextView(context);
        status.setTextSize(Theme.TEXT_SIZE_SMALL);
        status.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        form.addView(status, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        Runnable updateControls = () -> {
            int mode = sourceMode.getSelectedItemPosition();
            accountPlaylist.setVisibility(mode == 0 ? VISIBLE : GONE);
            type.setVisibility(mode == 2 ? VISIBLE : GONE);
            input.setVisibility(mode == 0 ? GONE : VISIBLE);
            input.setHint(text(mode == 1 ? ".field.hint.searchMusic" : ".text.uniPlaylist.sourceIdHint"));
        };
        Runnable loadAccountPlaylists = () -> {
            TuneWeavePlatform selected = platform.getSelectedPlatform();
            int generation = loadGeneration.incrementAndGet();
            status.setText(text(".text.platformPlaylist.loading"));
            MusicHud.EXECUTOR.execute(() -> {
                try {
                    var collections = TUNE_WEAVE.loadAccountPlaylists(selected);
                    List<Playlist> loaded = new ArrayList<>();
                    if (collections.getLikeList() != null && collections.getLikeList().getId() >= 0) {
                        loaded.add(collections.getLikeList());
                    }
                    if (collections.getCreatedPlaylist() != null) loaded.addAll(collections.getCreatedPlaylist());
                    if (collections.getSubscribedPlaylist() != null) loaded.addAll(collections.getSubscribedPlaylist());
                    MuiModApi.postToUiThread(() -> {
                        if (generation != loadGeneration.get()) return;
                        accountPlaylists[0] = List.copyOf(loaded);
                        accountPlaylist.setAdapter(new ArrayAdapter<>(context,
                                loaded.stream().map(Playlist::getName).toArray(String[]::new)));
                        status.setText(loaded.isEmpty() ? text(".text.platformPlaylist.empty") : "");
                    });
                } catch (RuntimeException error) {
                    MuiModApi.postToUiThread(() -> {
                        if (generation != loadGeneration.get()) return;
                        accountPlaylists[0] = List.of();
                        status.setText(message(error));
                    });
                }
            });
        };
        sourceMode.setOnItemSelectedListener((parent, view, position, id) -> {
            updateControls.run();
            if (position == 0) loadAccountPlaylists.run();
        });
        platform.setOnPlatformSelectedListener(selected -> {
            if (sourceMode.getSelectedItemPosition() == 0) loadAccountPlaylists.run();
        });
        updateControls.run();
        loadAccountPlaylists.run();

        TextView title = new TextView(context);
        title.setText(text(".text.uniPlaylist.importSource"));
        Modal modal = new Modal(context, title, form,
                new Modal.ActionButton(text(".button.import"), (button, dialog) -> {
                    int mode = sourceMode.getSelectedItemPosition();
                    TuneWeavePlatform selected = platform.getSelectedPlatform();
                    String inputValue = input.getText().toString().trim();
                    Playlist selectedPlaylist = mode == 0
                            && accountPlaylist.getSelectedItemPosition() >= 0
                            && accountPlaylist.getSelectedItemPosition() < accountPlaylists[0].size()
                            ? accountPlaylists[0].get(accountPlaylist.getSelectedItemPosition()) : null;
                    if ((mode == 0 && selectedPlaylist == null) || (mode != 0 && inputValue.isBlank())) return;
                    if (mode == 1) {
                        button.setEnabled(false);
                        status.setText(text(".text.uniPlaylist.searching"));
                        MusicHud.EXECUTOR.execute(() -> searchThenSelect(context, target, selected,
                                inputValue, onImported, onError, button, dialog, status));
                        return;
                    }
                    TuneWeaveClientService.UniImportSource source = mode == 0
                            ? source(selectedPlaylist, selected)
                            : new TuneWeaveClientService.UniImportSource(
                                    selected.apiName(), SOURCE_TYPES[type.getSelectedItemPosition()], inputValue);
                    dialog.dismiss();
                    append(target, source, onImported, onError);
                }),
                new Modal.ActionButton(text(".button.cancel"), (button, dialog) -> dialog.dismiss()));
        modal.show();
    }

    @SuppressWarnings("unchecked")
    private static void searchThenSelect(Context context, TuneWeaveClientService.UniPlaylistInfo target,
                                         TuneWeavePlatform platform, String query, Runnable onImported,
                                         Consumer<String> onError, Modal.ActionButton button, Modal parent,
                                         TextView status) {
        try {
            List<Playlist> results = (List<Playlist>) TUNE_WEAVE.search(query, SearchType.PLAYLIST, 0, platform);
            MuiModApi.postToUiThread(() -> {
                button.setEnabled(true);
                if (results.isEmpty()) {
                    status.setText(text(".text.searchNoMoreResult"));
                    return;
                }
                parent.dismiss();
                showSearchResults(context, target, platform, results, onImported, onError);
            });
        } catch (RuntimeException error) {
            MuiModApi.postToUiThread(() -> {
                button.setEnabled(true);
                status.setText(message(error));
            });
        }
    }

    private static void showSearchResults(Context context, TuneWeaveClientService.UniPlaylistInfo target,
                                          TuneWeavePlatform platform, List<Playlist> results,
                                          Runnable onImported, Consumer<String> onError) {
        Spinner choices = new Spinner(context);
        choices.setAdapter(new ArrayAdapter<>(context, results.stream()
                .map(playlist -> playlist.getName() + " (" + playlist.getMusicTrackCount() + ")")
                .toArray(String[]::new)));
        TextView title = new TextView(context);
        title.setText(text(".text.uniPlaylist.selectSearchResult"));
        new Modal(context, title, choices,
                new Modal.ActionButton(text(".button.import"), (button, dialog) -> {
                    int index = choices.getSelectedItemPosition();
                    if (index < 0 || index >= results.size()) return;
                    Playlist selected = results.get(index);
                    dialog.dismiss();
                    append(target, source(selected, platform), onImported, onError);
                }),
                new Modal.ActionButton(text(".button.cancel"), (button, dialog) -> dialog.dismiss())).show();
    }

    private static void append(TuneWeaveClientService.UniPlaylistInfo target,
                               TuneWeaveClientService.UniImportSource source,
                               Runnable onImported, Consumer<String> onError) {
        MusicHud.EXECUTOR.execute(() -> {
            try {
                TUNE_WEAVE.appendUniPlaylistSources(target.reference(), List.of(source));
                MuiModApi.postToUiThread(onImported);
            } catch (RuntimeException error) {
                MuiModApi.postToUiThread(() -> onError.accept(message(error)));
            }
        });
    }

    private static TuneWeaveClientService.UniImportSource source(Playlist playlist,
                                                                  TuneWeavePlatform platform) {
        String id = referenceId(playlist.getSourceRef(), platform);
        String type = "playlist";
        if (platform == TuneWeavePlatform.BILIBILI && id != null) {
            if (id.startsWith("favorite:")) type = "favorite_folder";
            if (id.startsWith("season:")) type = "season";
            int separator = id.indexOf(':');
            if (separator >= 0 && separator + 1 < id.length()) id = id.substring(separator + 1);
        }
        return new TuneWeaveClientService.UniImportSource(platform.apiName(), type, id);
    }

    private static String referenceId(String reference, TuneWeavePlatform platform) {
        String prefix = platform.apiName() + ':';
        return reference != null && reference.startsWith(prefix)
                ? reference.substring(prefix.length()) : reference;
    }

    private static String message(RuntimeException error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? text(".button.loadingError") : error.getMessage();
    }

    private static String text(String suffix) {
        return I18n.get(MusicHud.MOD_ID + suffix);
    }
}
