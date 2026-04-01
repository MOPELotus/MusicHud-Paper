package indi.etern.musichud.client.ui.components;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ui.PreferencesFragment;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import icyllis.modernui.widget.Toast;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.beans.server.ConnectionState;
import indi.etern.musichud.beans.server.PlayerQueryResult;
import indi.etern.musichud.beans.server.PlayerStatusInfo;
import indi.etern.musichud.beans.server.ServerStatusInfo;
import indi.etern.musichud.client.services.ServerManagementService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackground;
import lombok.Getter;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class ServerManagementView extends LinearLayout {
    @Getter
    private static volatile ServerManagementView instance;
    private final ServerManagementService serverManagementService = ServerManagementService.getInstance();
    private String lastPlayerQuery = "";
    private String lastPlayerQueryResult = "";
    private final Consumer<ServerStatusInfo> statusListener = status -> MuiModApi.postToUiThread(this::refresh);

    public ServerManagementView(Context context) {
        super(context);
        instance = this;
        setOrientation(VERTICAL);
        refresh();
        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                serverManagementService.getStatusListeners().add(statusListener);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                serverManagementService.getStatusListeners().remove(statusListener);
                if (instance == ServerManagementView.this) {
                    instance = null;
                }
            }
        });
        serverManagementService.refreshServerStatus();
    }

    public void refresh() {
        removeAllViews();
        Context context = getContext();
        ServerStatusInfo serverStatusInfo = serverManagementService.getServerStatusInfo();

        LinearLayout remoteCategory = PreferencesFragment.createCategoryList(
                this,
                I18n.get(MusicHud.MOD_ID + ".config.category.remoteServer")
        );

        addInfoLine(remoteCategory,
                I18n.get(MusicHud.MOD_ID + ".text.version.client"),
                Version.current.toString()
        );

        if (!serverManagementService.isSingleplayerContext()) {
            String serverVersion = serverStatusInfo.serverVersion().isBlank()
                    ? I18n.get(MusicHud.MOD_ID + ".text.unknown")
                    : serverStatusInfo.serverVersion();
            addInfoLine(remoteCategory,
                    I18n.get(MusicHud.MOD_ID + ".text.version.server"),
                    serverVersion
            );
            addInfoLine(remoteCategory,
                    I18n.get(MusicHud.MOD_ID + ".text.connectionStatus"),
                    I18n.get((MusicHud.MOD_ID + ".text.connectionState." + MusicHud.getStatus().name()))
            );
            if (!serverStatusInfo.serverApiBaseUrl().isBlank()) {
                addInfoLine(remoteCategory,
                        I18n.get(MusicHud.MOD_ID + ".text.serverApiBaseUrl"),
                        serverStatusInfo.serverApiBaseUrl()
                );
            }
            if (serverStatusInfo.serverApiBaseUrlMasked()) {
                addSecondaryInfoLine(remoteCategory, I18n.get(MusicHud.MOD_ID + ".text.serverApiMasked"));
            }
            if (!serverStatusInfo.binaryApiServerStatusI18nKey().isBlank()) {
                addInfoLine(remoteCategory,
                        I18n.get(MusicHud.MOD_ID + ".text.binaryApiStatusLabel"),
                        I18n.get(serverStatusInfo.binaryApiServerStatusI18nKey())
                );
            }
        }

        if (MusicHud.getStatus() != MusicHud.ConnectStatus.CONNECTED) {
            addSecondaryInfoLine(remoteCategory, I18n.get(MusicHud.MOD_ID + ".text.remoteServerUnavailable"));
        }

        if (serverStatusInfo.canReloadConfig()) {
            Button reloadButton = createActionButton(context, I18n.get(MusicHud.MOD_ID + ".button.reloadServerConfig"));
            reloadButton.setOnClickListener(v -> serverManagementService.reloadRemoteServerConfig()
                    .thenAccept(result -> MuiModApi.postToUiThread(() -> {
                        showToast(serverManagementService.translateMessage(result.message()));
                    })));
            remoteCategory.addView(reloadButton, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        }

        if (serverStatusInfo.canManageServerConfig()) {
            RemoteServerConfigDraft draft = new RemoteServerConfigDraft(serverStatusInfo);

            LinearLayout apiUrlInputBox = createInputBox(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.remoteServer.serverApiBaseUrl"),
                    draft.serverApiBaseUrl
            );
            EditText apiUrlInput = apiUrlInputBox.findViewById(R.id.input);
            remoteCategory.addView(apiUrlInputBox);

            new PreferencesFragment.BooleanOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.remoteServer.startupBinaryApiServerWhenLaunch"),
                    () -> draft.startupBinaryApiServerWhenLaunch,
                    value -> draft.startupBinaryApiServerWhenLaunch = value
            ).create(remoteCategory);

            LinearLayout binaryPathInputBox = createInputBox(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.remoteServer.serverApiBinaryExecutablePath"),
                    draft.serverApiBinaryExecutablePath
            );
            EditText binaryPathInput = binaryPathInputBox.findViewById(R.id.input);
            remoteCategory.addView(binaryPathInputBox);

            new PreferencesFragment.FloatOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.remoteServer.pusherVoteAdditionalRate"),
                    () -> draft.pusherVoteAdditionalRate,
                    value -> draft.pusherVoteAdditionalRate = value
            ).setRange(0, 1).create(remoteCategory);

            new PreferencesFragment.BooleanOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.remoteServer.useRandomCnIp"),
                    () -> draft.useRandomCnIp,
                    value -> draft.useRandomCnIp = value
            ).create(remoteCategory);

            Button saveButton = createActionButton(context, I18n.get(MusicHud.MOD_ID + ".button.saveServerConfig"));
            saveButton.setOnClickListener(v -> serverManagementService.updateRemoteServerConfig(
                    apiUrlInput == null ? draft.serverApiBaseUrl : apiUrlInput.getText().toString(),
                    draft.startupBinaryApiServerWhenLaunch,
                    binaryPathInput == null ? draft.serverApiBinaryExecutablePath : binaryPathInput.getText().toString(),
                    draft.pusherVoteAdditionalRate,
                    draft.useRandomCnIp
            ).thenAccept(result -> MuiModApi.postToUiThread(() -> {
                showToast(serverManagementService.translateMessage(result.message()));
            })));
            LayoutParams saveParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            saveParams.setMargins(0, dp(8), 0, 0);
            remoteCategory.addView(saveButton, saveParams);
        }

        if (serverStatusInfo.canQueryPlayerInfo()) {
            addSecondaryInfoLine(remoteCategory, I18n.get(MusicHud.MOD_ID + ".text.playerQueryDescription"));
            LinearLayout queryInputBox = createInputBox(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".text.playerQuery"),
                    lastPlayerQuery
            );
            EditText queryInput = queryInputBox.findViewById(R.id.input);
            remoteCategory.addView(queryInputBox);

            Button queryButton = createActionButton(context, I18n.get(MusicHud.MOD_ID + ".button.queryPlayerInfo"));
            queryButton.setOnClickListener(v -> {
                String query = queryInput == null ? "" : queryInput.getText().toString().trim();
                lastPlayerQuery = query;
                serverManagementService.queryPlayerStatus(query)
                        .thenAccept(result -> MuiModApi.postToUiThread(() -> {
                            lastPlayerQueryResult = formatPlayerQueryResult(result);
                            refresh();
                        }));
            });
            LayoutParams queryParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            queryParams.setMargins(0, dp(8), 0, 0);
            remoteCategory.addView(queryButton, queryParams);

            if (!lastPlayerQueryResult.isBlank()) {
                TextView resultView = new TextView(context);
                resultView.setTextSize(Theme.TEXT_SIZE_NORMAL);
                resultView.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                resultView.setText(lastPlayerQueryResult);
                LayoutParams resultParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                resultParams.setMargins(0, dp(12), 0, 0);
                remoteCategory.addView(resultView, resultParams);
            }
        }

        LayoutParams categoryParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        categoryParams.setMargins(0, dp(6), 0, dp(24));
        addView(remoteCategory, categoryParams);
    }

    private LinearLayout createInputBox(Context context, String title, String value) {
        LinearLayout inputBox = PreferencesFragment.createInputBox(context, title);
        EditText input = inputBox.findViewById(R.id.input);
        if (input != null) {
            input.setMinimumWidth(dp(256));
            input.setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
            input.setText(value);
            input.setOnKeyListener((v, c, e) -> {
                if (c == GLFW.GLFW_KEY_ENTER) {
                    input.clearFocus();
                    return true;
                }
                return false;
            });
        }
        return inputBox;
    }

    private Button createActionButton(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextColor(Theme.PRIMARY_COLOR);
        button.setTextSize(Theme.TEXT_SIZE_NORMAL);
        button.setGravity(Gravity.CENTER);
        button.setBackground(ButtonInsetBackground.builder()
                .inset(0)
                .cornerRadius(dp(8))
                .padding(new ButtonInsetBackground.Padding(dp(12), dp(4), dp(12), dp(4)))
                .build().get());
        return button;
    }

    private void addInfoLine(LinearLayout target, String label, String value) {
        TextView textView = new TextView(getContext());
        textView.setTextSize(Theme.TEXT_SIZE_NORMAL);
        textView.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        textView.setText(label + ": " + value);
        LayoutParams params = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        target.addView(textView, params);
    }

    private void addSecondaryInfoLine(LinearLayout target, String text) {
        TextView textView = new TextView(getContext());
        textView.setTextSize(Theme.TEXT_SIZE_NORMAL);
        textView.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        textView.setText(text);
        LayoutParams params = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        target.addView(textView, params);
    }

    private String formatPlayerQueryResult(PlayerQueryResult result) {
        if (!result.found()) {
            return serverManagementService.translateMessage(result.message());
        }
        PlayerStatusInfo info = result.playerStatusInfo();
        StringBuilder builder = new StringBuilder();
        builder.append(I18n.get(MusicHud.MOD_ID + ".text.playerStatus.name"))
                .append(": ")
                .append(info.playerName())
                .append('\n');
        builder.append(I18n.get(MusicHud.MOD_ID + ".text.playerStatus.uuid"))
                .append(": ")
                .append(info.playerUuid())
                .append('\n');
        builder.append(I18n.get(MusicHud.MOD_ID + ".text.playerStatus.connection"))
                .append(": ")
                .append(I18n.get(info.connectionState().i18nKey()))
                .append('\n');
        builder.append(I18n.get(MusicHud.MOD_ID + ".text.playerStatus.modVersion"))
                .append(": ")
                .append(info.modVersion().isBlank() ? I18n.get(MusicHud.MOD_ID + ".text.unknown") : info.modVersion())
                .append('\n');
        builder.append(I18n.get(MusicHud.MOD_ID + ".text.playerStatus.loginType"))
                .append(": ")
                .append(I18n.get(MusicHud.MOD_ID + ".text.loginType." + info.loginType().name()))
                .append('\n');
        if (info.hasLoggedAccount()) {
            builder.append(I18n.get(MusicHud.MOD_ID + ".text.playerStatus.account"))
                    .append(": ")
                    .append(info.profile().getNickname())
                    .append(" (")
                    .append(info.profile().getUserId())
                    .append(')')
                    .append('\n');
        } else {
            builder.append(I18n.get(MusicHud.MOD_ID + ".text.playerStatus.account"))
                    .append(": ")
                    .append(I18n.get(MusicHud.MOD_ID + ".text.notLoggedIn"))
                    .append('\n');
        }
        builder.append(I18n.get(MusicHud.MOD_ID + ".text.playerStatus.vipType"))
                .append(": ")
                .append(I18n.get(MusicHud.MOD_ID + ".text.vipType." + info.vipType().name()));
        return builder.toString();
    }

    private void showToast(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        ToastUtil.show(Toast.makeText(getContext(), message, Toast.LENGTH_SHORT));
    }

    private static final class RemoteServerConfigDraft {
        private String serverApiBaseUrl;
        private boolean startupBinaryApiServerWhenLaunch;
        private String serverApiBinaryExecutablePath;
        private double pusherVoteAdditionalRate;
        private boolean useRandomCnIp;

        private RemoteServerConfigDraft(ServerStatusInfo serverStatusInfo) {
            this.serverApiBaseUrl = serverStatusInfo.serverApiBaseUrl();
            this.startupBinaryApiServerWhenLaunch = serverStatusInfo.startupBinaryApiServerWhenLaunch();
            this.serverApiBinaryExecutablePath = serverStatusInfo.serverApiBinaryExecutablePath();
            this.pusherVoteAdditionalRate = serverStatusInfo.pusherVoteAdditionalRate();
            this.useRandomCnIp = serverStatusInfo.useRandomCnIp();
        }
    }
}
