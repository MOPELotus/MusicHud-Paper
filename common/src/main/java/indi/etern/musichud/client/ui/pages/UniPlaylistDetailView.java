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
import icyllis.modernui.widget.ArrayAdapter;
import icyllis.modernui.widget.Spinner;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.services.tuneweave.TuneWeaveClientService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.Modal;
import indi.etern.musichud.client.ui.components.RouterContainer;
import indi.etern.musichud.client.ui.utils.ui.ButtonInsetBackgroundFactory;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public final class UniPlaylistDetailView extends LinearLayout {
    private final TuneWeaveClientService tuneWeave = TuneWeaveClientService.getInstance();
    private final TuneWeaveClientService.UniPlaylistInfo playlist;
    private final LinearLayout itemsLayout;
    private List<TuneWeaveClientService.UniItemInfo> items = List.of();

    public UniPlaylistDetailView(Context context, TuneWeaveClientService.UniPlaylistInfo playlist) {
        super(context);
        this.playlist = playlist;
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(24), dp(16), dp(24));

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        Button back = action(context, ".button.back", v -> RouterContainer.getInstance().popNavigate());
        toolbar.addView(back, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        TextView title = new TextView(context);
        title.setText(playlist.name());
        title.setTextSize(Theme.TEXT_SIZE_LARGER);
        title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        toolbar.addView(title, new LayoutParams(0, WRAP_CONTENT, 1));
        toolbar.addView(action(context, ".button.refresh", v -> refresh()), new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        toolbar.addView(action(context, ".button.add", v -> showAddDialog()), new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        addView(toolbar, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        TextView description = new TextView(context);
        description.setText(playlist.description());
        description.setTextSize(Theme.TEXT_SIZE_NORMAL);
        description.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        LayoutParams descriptionParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        descriptionParams.setMargins(dp(52), dp(4), 0, dp(16));
        addView(description, descriptionParams);

        ScrollView scroll = new ScrollView(context);
        itemsLayout = new LinearLayout(context);
        itemsLayout.setOrientation(VERTICAL);
        scroll.addView(itemsLayout, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        addView(scroll, new LayoutParams(MATCH_PARENT, 0, 1));
        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { refresh(); }
            @Override public void onViewDetachedFromWindow(View v) { }
        });
    }

    private void refresh() {
        MusicHud.EXECUTOR.execute(() -> {
            try {
                items = tuneWeave.listUniPlaylistItems(playlist.reference());
                MuiModApi.postToUiThread(this::render);
            } catch (RuntimeException error) {
                MuiModApi.postToUiThread(() -> showMessage(error.getMessage()));
            }
        });
    }

    private void render() {
        itemsLayout.removeAllViews();
        if (items.isEmpty()) {
            showMessage(I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.emptyItems"));
            return;
        }
        for (int index = 0; index < items.size(); index++) {
            final int itemIndex = index;
            TuneWeaveClientService.UniItemInfo item = items.get(index);
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView number = new TextView(getContext());
            number.setText((index + 1) + ".");
            number.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            row.addView(number, new LayoutParams(dp(32), WRAP_CONTENT));
            LinearLayout text = new LinearLayout(getContext());
            text.setOrientation(VERTICAL);
            TextView title = new TextView(getContext());
            title.setText(item.title());
            title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            title.setTextSize(Theme.TEXT_SIZE_NORMAL);
            text.addView(title, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            TextView meta = new TextView(getContext());
            meta.setText(item.artists().isEmpty() ? item.sourceRef() : String.join(" / ", item.artists()));
            meta.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            meta.setTextSize(Theme.TEXT_SIZE_SMALL);
            text.addView(meta, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            row.addView(text, new LayoutParams(0, WRAP_CONTENT, 1));
            if (itemIndex > 0) row.addView(action(getContext(), ".button.moveUp", v -> move(itemIndex, itemIndex - 1)), new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
            if (itemIndex + 1 < items.size()) row.addView(action(getContext(), ".button.moveDown", v -> move(itemIndex, itemIndex + 1)), new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
            row.addView(action(getContext(), ".button.remove", v -> remove(item)), new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
            row.setPadding(dp(8), dp(6), dp(8), dp(6));
            LayoutParams rowParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            rowParams.setMargins(0, 0, 0, dp(4));
            itemsLayout.addView(row, rowParams);
        }
    }

    private Button action(Context context, String key, View.OnClickListener listener) {
        Button button = new Button(context);
        button.setText(key.startsWith(".") ? I18n.get(MusicHud.MOD_ID + key) : key);
        button.setTextSize(Theme.TEXT_SIZE_SMALL);
        button.setTextColor(Theme.PRIMARY_COLOR);
        button.setBackground(ButtonInsetBackgroundFactory.builder().cornerRadius(dp(4)).inset(dp(1)).build().newBackgroundDrawable());
        button.setOnClickListener(listener);
        return button;
    }

    private void showAddDialog() {
        LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(VERTICAL);
        Spinner platform = new Spinner(getContext());
        platform.setAdapter(new ArrayAdapter<>(getContext(), new String[]{
                I18n.get(MusicHud.MOD_ID + ".platform.netease"),
                I18n.get(MusicHud.MOD_ID + ".platform.qq"),
                I18n.get(MusicHud.MOD_ID + ".platform.bilibili")
        }));
        form.addView(platform, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        Spinner kind = new Spinner(getContext());
        kind.setAdapter(new ArrayAdapter<>(getContext(), new String[]{
                I18n.get(MusicHud.MOD_ID + ".uniPlaylist.item.track"),
                I18n.get(MusicHud.MOD_ID + ".uniPlaylist.item.video")
        }));
        form.addView(kind, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        EditText input = new EditText(getContext(), null, R.attr.editTextOutlinedStyle);
        input.setHint(I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.refHint"));
        input.setSingleLine(true);
        form.addView(input, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        TextView title = new TextView(getContext());
        title.setText(I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.add"));
        new Modal(getContext(), title, form,
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".button.confirm"), (b, modal) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isBlank()) {
                        String prefix = switch (platform.getSelectedItemPosition()) {
                            case 1 -> "qq";
                            case 2 -> "bilibili";
                            default -> "netease";
                        };
                        String itemKind = kind.getSelectedItemPosition() == 1 ? "video" : "track";
                        modal.dismiss();
                        add(prefix + ":" + value, itemKind);
                    }
                }),
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".button.cancel"), (b, modal) -> modal.dismiss())).show();
    }

    private void add(String reference, String kind) {
        MusicHud.EXECUTOR.execute(() -> { try { tuneWeave.addUniPlaylistItem(playlist.reference(), reference, kind); refresh(); }
        catch (RuntimeException error) { MuiModApi.postToUiThread(() -> showMessage(error.getMessage())); } });
    }

    private void remove(TuneWeaveClientService.UniItemInfo item) {
        MusicHud.EXECUTOR.execute(() -> { try { tuneWeave.deleteUniPlaylistItem(playlist.reference(), item.id()); refresh(); }
        catch (RuntimeException error) { MuiModApi.postToUiThread(() -> showMessage(error.getMessage())); } });
    }

    private void move(int from, int to) {
        List<String> ids = new ArrayList<>(items.stream().map(TuneWeaveClientService.UniItemInfo::id).toList());
        String id = ids.remove(from);
        ids.add(to, id);
        MusicHud.EXECUTOR.execute(() -> { try { tuneWeave.reorderUniPlaylistItems(playlist.reference(), ids); refresh(); }
        catch (RuntimeException error) { MuiModApi.postToUiThread(() -> showMessage(error.getMessage())); } });
    }

    private void showMessage(String message) {
        itemsLayout.removeAllViews();
        TextView text = new TextView(getContext());
        text.setText(message == null || message.isBlank() ? I18n.get(MusicHud.MOD_ID + ".button.loadingError") : message);
        text.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        itemsLayout.addView(text, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
    }
}
