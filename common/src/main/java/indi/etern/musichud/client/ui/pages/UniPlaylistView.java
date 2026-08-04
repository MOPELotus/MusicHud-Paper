package indi.etern.musichud.client.ui.pages;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.services.tuneweave.TuneWeaveClientService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.Modal;
import indi.etern.musichud.client.ui.components.RouterContainer;
import indi.etern.musichud.client.ui.utils.ui.ButtonInsetBackgroundFactory;
import net.minecraft.client.resources.language.I18n;

import java.util.List;
import java.util.function.Consumer;

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
        toolbar.addView(actionButton(context, "refresh", v -> refresh()), new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        toolbar.addView(actionButton(context, "create", v -> showNameDialog(
                I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.create"), "", name -> create(name))),
                new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        toolbar.addView(actionButton(context, "import", v -> showNameDialog(
                I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.importSource"), "", source -> importSource(source))),
                new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        addView(toolbar, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

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
            buttons.addView(actionButton(getContext(), "open", v -> RouterContainer.getInstance().pushNavigate(
                    new UniPlaylistDetailView(getContext(), playlist))), new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
            buttons.addView(actionButton(getContext(), "rename", v -> showNameDialog(
                    I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.rename"), playlist.name(), value -> rename(playlist, value))),
                    new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
            buttons.addView(actionButton(getContext(), "delete", v -> confirmDelete(playlist)), new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
            row.addView(buttons, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            LayoutParams rowParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            rowParams.setMargins(0, 0, 0, dp(8));
            list.addView(row, rowParams);
        }
    }

    private Button actionButton(Context context, String action, View.OnClickListener listener) {
        String key = switch (action) {
            case "create" -> ".button.create";
            case "import" -> ".button.import";
            case "open" -> ".button.open";
            case "rename" -> ".button.rename";
            case "delete" -> ".button.delete";
            default -> ".button.refresh";
        };
        Button button = new Button(context);
        button.setText(I18n.get(MusicHud.MOD_ID + key));
        button.setTextSize(Theme.TEXT_SIZE_SMALL);
        button.setTextColor(Theme.PRIMARY_COLOR);
        button.setBackground(ButtonInsetBackgroundFactory.builder().cornerRadius(dp(4)).inset(dp(1)).build().newBackgroundDrawable());
        button.setOnClickListener(listener);
        return button;
    }

    private void showNameDialog(String titleText, String initial, Consumer<String> consumer) {
        EditText input = new EditText(getContext(), null, R.attr.editTextOutlinedStyle);
        input.setSingleLine(true);
        input.setText(initial);
        TextView title = new TextView(getContext());
        title.setText(titleText);
        Modal modal = new Modal(getContext(), title, input,
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".button.confirm"), (button, dialog) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isBlank()) {
                        dialog.dismiss();
                        consumer.accept(value);
                    }
                }),
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".button.cancel"), (button, dialog) -> dialog.dismiss()));
        modal.show();
    }

    private void create(String name) {
        MusicHud.EXECUTOR.execute(() -> {
            try { tuneWeave.createUniPlaylist(name, ""); refreshOnUi(); }
            catch (RuntimeException error) { MuiModApi.postToUiThread(() -> showProgress(error.getMessage())); }
        });
    }

    private void importSource(String source) {
        MusicHud.EXECUTOR.execute(() -> {
            try { tuneWeave.importUniPlaylist(null, List.of(source)); refreshOnUi(); }
            catch (RuntimeException error) { MuiModApi.postToUiThread(() -> showProgress(error.getMessage())); }
        });
    }

    private void rename(TuneWeaveClientService.UniPlaylistInfo playlist, String name) {
        MusicHud.EXECUTOR.execute(() -> {
            try { tuneWeave.updateUniPlaylist(playlist.reference(), name, null); refreshOnUi(); }
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
