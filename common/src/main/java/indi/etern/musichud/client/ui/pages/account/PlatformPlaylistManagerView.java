package indi.etern.musichud.client.ui.pages.account;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.ArrayAdapter;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.Spinner;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.client.services.tuneweave.TuneWeaveClientService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.Modal;
import indi.etern.musichud.client.ui.components.MusicCollectionDetailView;
import indi.etern.musichud.client.ui.components.RouterContainer;
import indi.etern.musichud.client.ui.components.UrlImageView;
import indi.etern.musichud.client.ui.utils.ui.ButtonInsetBackgroundFactory;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/** Management surface for playlists owned by the selected platform account. */
public final class PlatformPlaylistManagerView extends LinearLayout {
    private final TuneWeaveClientService tuneWeave = TuneWeaveClientService.getInstance();
    private final LinearLayout rows;
    private final TextView status;
    private List<Playlist> playlists = List.of();

    public PlatformPlaylistManagerView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(24), dp(16), dp(24));

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(action(".button.back", v -> RouterContainer.getInstance().popNavigate()),
                new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        TextView title = new TextView(context);
        title.setText(I18n.get(MusicHud.MOD_ID + ".text.platformPlaylist.title"));
        title.setTextSize(Theme.TEXT_SIZE_LARGER);
        title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        toolbar.addView(title, new LayoutParams(0, WRAP_CONTENT, 1));
        toolbar.addView(action(".button.refresh", v -> refresh()), new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        toolbar.addView(action(".button.create", v -> showCreateDialog()), new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        addView(toolbar, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        TextView hint = new TextView(context);
        hint.setText(I18n.get(MusicHud.MOD_ID + ".text.platformPlaylist.description"));
        hint.setTextSize(Theme.TEXT_SIZE_NORMAL);
        hint.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        LayoutParams hintParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        hintParams.setMargins(dp(48), dp(4), 0, dp(16));
        addView(hint, hintParams);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        rows = new LinearLayout(context);
        rows.setOrientation(VERTICAL);
        scroll.addView(rows, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        addView(scroll, new LayoutParams(MATCH_PARENT, 0, 1));

        status = new TextView(context);
        status.setTextSize(Theme.TEXT_SIZE_NORMAL);
        status.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        status.setVisibility(GONE);
        addView(status, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { refresh(); }
            @Override public void onViewDetachedFromWindow(View v) { }
        });
    }

    private void refresh() {
        showStatus(I18n.get(MusicHud.MOD_ID + ".text.platformPlaylist.loading"));
        MusicHud.EXECUTOR.execute(() -> {
            try {
                List<Playlist> loaded = new ArrayList<>(
                        tuneWeave.loadAccountPlaylists().getCreatedPlaylist());
                MuiModApi.postToUiThread(() -> render(loaded));
            } catch (RuntimeException error) {
                MuiModApi.postToUiThread(() -> showStatus(message(error)));
            }
        });
    }

    private void render(List<Playlist> loaded) {
        playlists = List.copyOf(loaded);
        rows.removeAllViews();
        if (loaded.isEmpty()) {
            showStatus(I18n.get(MusicHud.MOD_ID + ".text.platformPlaylist.empty"));
            return;
        }
        status.setVisibility(GONE);
        for (int index = 0; index < loaded.size(); index++) {
            rows.addView(createRow(loaded.get(index), index), rowParams());
        }
    }

    private View createRow(Playlist playlist, int index) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(ButtonInsetBackgroundFactory.builder()
                .cornerRadius(dp(6)).inset(dp(1)).build().newBackgroundDrawable());

        UrlImageView cover = new UrlImageView(getContext());
        cover.setCornerRadius(dp(6));
        cover.loadUrl(playlist.getThumbnailCoverUrl(dp(56)));
        row.addView(cover, new LayoutParams(dp(56), dp(56)));

        LinearLayout text = new LinearLayout(getContext());
        text.setOrientation(VERTICAL);
        TextView name = new TextView(getContext());
        name.setText(playlist.getName());
        name.setTextSize(Theme.TEXT_SIZE_LARGE);
        name.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        name.setMaxLines(2);
        text.addView(name, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        TextView count = new TextView(getContext());
        count.setText(I18n.get(MusicHud.MOD_ID + ".text.platformPlaylist.trackCount")
                .replace("{}", Integer.toString(playlist.getMusicTrackCount())));
        count.setTextSize(Theme.TEXT_SIZE_SMALL);
        count.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        text.addView(count, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        LayoutParams textParams = new LayoutParams(0, WRAP_CONTENT, 1);
        textParams.setMargins(dp(12), 0, dp(8), 0);
        row.addView(text, textParams);

        row.addView(action(".button.open", v -> RouterContainer.getInstance().pushNavigate(
                new MusicCollectionDetailView(getContext(), playlist, true))), new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        row.addView(action(".button.rename", v -> showRenameDialog(playlist)), new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        if (index > 0) {
            row.addView(action(".button.moveUp", v -> move(index, index - 1)),
                    new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        }
        if (index + 1 < playlists.size()) {
            row.addView(action(".button.moveDown", v -> move(index, index + 1)),
                    new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        }
        row.addView(action(".button.delete", v -> confirmDelete(playlist)),
                new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        return row;
    }

    private void showCreateDialog() {
        LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(VERTICAL);
        EditText name = new EditText(getContext(), null, R.attr.editTextOutlinedStyle);
        name.setSingleLine(true);
        name.setHint(I18n.get(MusicHud.MOD_ID + ".text.platformPlaylist.nameHint"));
        form.addView(name, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        Spinner visibility = new Spinner(getContext());
        visibility.setAdapter(new ArrayAdapter<>(getContext(), new String[]{
                I18n.get(MusicHud.MOD_ID + ".text.platformPlaylist.public"),
                I18n.get(MusicHud.MOD_ID + ".text.platformPlaylist.private")
        }));
        form.addView(visibility, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        TextView title = modalTitle(".text.platformPlaylist.create");
        new Modal(getContext(), title, form,
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".button.confirm"), (button, modal) -> {
                    String value = name.getText().toString().trim();
                    if (value.isBlank()) return;
                    modal.dismiss();
                    mutate(() -> tuneWeave.createPlatformPlaylist(value,
                            visibility.getSelectedItemPosition() == 1));
                }),
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".button.cancel"),
                        (button, modal) -> modal.dismiss())).show();
    }

    private void showRenameDialog(Playlist playlist) {
        EditText name = new EditText(getContext(), null, R.attr.editTextOutlinedStyle);
        name.setSingleLine(true);
        name.setText(playlist.getName());
        new Modal(getContext(), modalTitle(".text.platformPlaylist.rename"), name,
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".button.confirm"), (button, modal) -> {
                    String value = name.getText().toString().trim();
                    if (value.isBlank()) return;
                    modal.dismiss();
                    mutate(() -> tuneWeave.updatePlatformPlaylist(playlist, value, null));
                }),
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".button.cancel"),
                        (button, modal) -> modal.dismiss())).show();
    }

    private void confirmDelete(Playlist playlist) {
        TextView warning = new TextView(getContext());
        warning.setText(I18n.get(MusicHud.MOD_ID + ".text.platformPlaylist.deleteWarning"));
        new Modal(getContext(), modalTitle(playlist.getName()), warning,
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".button.delete"), (button, modal) -> {
                    modal.dismiss();
                    mutate(() -> tuneWeave.deletePlatformPlaylist(playlist));
                }),
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".button.cancel"),
                        (button, modal) -> modal.dismiss())).show();
    }

    private void move(int from, int to) {
        List<Playlist> reordered = new ArrayList<>(playlists);
        Playlist moved = reordered.remove(from);
        reordered.add(to, moved);
        showStatus(I18n.get(MusicHud.MOD_ID + ".text.platformPlaylist.savingOrder"));
        MusicHud.EXECUTOR.execute(() -> {
            try {
                tuneWeave.reorderPlatformPlaylists(reordered);
                MuiModApi.postToUiThread(() -> render(reordered));
            } catch (RuntimeException error) {
                MuiModApi.postToUiThread(() -> showStatus(message(error)));
            }
        });
    }

    private void mutate(Runnable mutation) {
        showStatus(I18n.get(MusicHud.MOD_ID + ".text.platformPlaylist.saving"));
        MusicHud.EXECUTOR.execute(() -> {
            try {
                mutation.run();
                MuiModApi.postToUiThread(this::refresh);
            } catch (RuntimeException error) {
                MuiModApi.postToUiThread(() -> showStatus(message(error)));
            }
        });
    }

    private Button action(String key, View.OnClickListener listener) {
        Button button = new Button(getContext());
        button.setText(key.startsWith(".") ? I18n.get(MusicHud.MOD_ID + key) : key);
        button.setTextSize(Theme.TEXT_SIZE_SMALL);
        button.setTextColor(Theme.PRIMARY_COLOR);
        button.setBackground(ButtonInsetBackgroundFactory.builder()
                .cornerRadius(dp(4)).inset(dp(1)).build().newBackgroundDrawable());
        button.setOnClickListener(listener);
        return button;
    }

    private TextView modalTitle(String key) {
        TextView title = new TextView(getContext());
        title.setText(key.startsWith(".") ? I18n.get(MusicHud.MOD_ID + key) : key);
        return title;
    }

    private LayoutParams rowParams() {
        LayoutParams params = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(6));
        return params;
    }

    private void showStatus(String value) {
        status.setText(value == null ? "" : value);
        status.setVisibility(VISIBLE);
    }

    private static String message(RuntimeException error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }
}
