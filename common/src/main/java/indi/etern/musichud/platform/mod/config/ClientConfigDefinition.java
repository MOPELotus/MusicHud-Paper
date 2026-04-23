package indi.etern.musichud.platform.mod.config;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.client.config.ProfileConfigData;
import indi.etern.musichud.client.ui.hud.metadata.HorizontalAlign;
import indi.etern.musichud.client.ui.hud.metadata.VerticalAlign;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.utils.JsonUtil;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ClientConfigDefinition implements ClientConfig {
    private static final String FILE_NAME = "music_hud-client.toml";
    @Getter
    private static final ClientConfigDefinition instance = new ClientConfigDefinition();

    private boolean enable = true;
    private boolean showTranslatedCnLyrics = true;
    private boolean disableVanillaMusic = true;
    private boolean hideHudWhenNotPlaying = true;
    private boolean enableHud = true;
    private Quality primaryChosenQuality = Quality.LOSSLESS;
    private VerticalAlign hudVerticalPosition = VerticalAlign.TOP;
    private HorizontalAlign hudHorizontalPosition = HorizontalAlign.LEFT;
    private int hudOffsetX = 16;
    private int hudOffsetY = 16;
    private int hudWidth = 150;
    private int hudHeight = 44;
    private int hudCornerRadius = 8;
    private String clientCookie = "";
    private String clientAccountConfig = "";
    private boolean enableEmbeddedServer = true;
    @Setter
    @Getter
    private boolean configured;

    private ClientConfigDefinition() {
    }

    public synchronized void load() {
        Path path = SimpleTomlConfig.path(FILE_NAME);
        Map<String, String> values = SimpleTomlConfig.read(path);
        enable = SimpleTomlConfig.getBoolean(values, "enable", enable);
        showTranslatedCnLyrics = SimpleTomlConfig.getBoolean(values, "showTranslatedCnLyrics", showTranslatedCnLyrics);
        disableVanillaMusic = SimpleTomlConfig.getBoolean(values, "disableVanillaMusic", disableVanillaMusic);
        hideHudWhenNotPlaying = SimpleTomlConfig.getBoolean(values, "hideHudWhenNotPlaying", hideHudWhenNotPlaying);
        enableHud = SimpleTomlConfig.getBoolean(values, "enableHud", enableHud);
        primaryChosenQuality = SimpleTomlConfig.getEnum(values, "primaryChosenQuality", Quality.class, primaryChosenQuality);
        hudVerticalPosition = SimpleTomlConfig.getEnum(values, "verticalPosition", VerticalAlign.class, hudVerticalPosition);
        hudHorizontalPosition = SimpleTomlConfig.getEnum(values, "horizontalPosition", HorizontalAlign.class, hudHorizontalPosition);
        hudOffsetX = SimpleTomlConfig.getInt(values, "hudOffsetX", hudOffsetX);
        hudOffsetY = SimpleTomlConfig.getInt(values, "hudOffsetY", hudOffsetY);
        hudWidth = SimpleTomlConfig.getInt(values, "hudWidth", hudWidth);
        hudHeight = SimpleTomlConfig.getInt(values, "hudHeight", hudHeight);
        hudCornerRadius = SimpleTomlConfig.getInt(values, "hudCornerRadius", hudCornerRadius);
        clientCookie = SimpleTomlConfig.getString(values, "clientCookie", clientCookie);
        clientAccountConfig = SimpleTomlConfig.getString(values, "clientAccountConfig", clientAccountConfig);
        enableEmbeddedServer = SimpleTomlConfig.getBoolean(values, "enableEmbeddedServer", enableEmbeddedServer);
        configured = true;
        save();
    }

    @Override
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    @Override
    public void setShowTranslatedCnLyrics(boolean showTranslatedCnLyrics) {
        this.showTranslatedCnLyrics = showTranslatedCnLyrics;
    }

    @Override
    public void setDisableVanillaMusic(boolean disableVanillaMusic) {
        this.disableVanillaMusic = disableVanillaMusic;
    }

    @Override
    public void setHideHudWhenNotPlaying(boolean hideHudWhenNotPlaying) {
        this.hideHudWhenNotPlaying = hideHudWhenNotPlaying;
    }

    @Override
    public void setEnableHud(boolean enableHud) {
        this.enableHud = enableHud;
    }

    @Override
    public void setPrimaryChosenQuality(Quality primaryChosenQuality) {
        this.primaryChosenQuality = primaryChosenQuality == null ? Quality.LOSSLESS : primaryChosenQuality;
    }

    @Override
    public void setHudVerticalPosition(VerticalAlign hudVerticalPosition) {
        this.hudVerticalPosition = hudVerticalPosition == null ? VerticalAlign.TOP : hudVerticalPosition;
    }

    @Override
    public void setHudHorizontalPosition(HorizontalAlign hudHorizontalPosition) {
        this.hudHorizontalPosition = hudHorizontalPosition == null ? HorizontalAlign.LEFT : hudHorizontalPosition;
    }

    @Override
    public void setHudOffsetX(int hudOffsetX) {
        this.hudOffsetX = hudOffsetX;
    }

    @Override
    public void setHudOffsetY(int hudOffsetY) {
        this.hudOffsetY = hudOffsetY;
    }

    @Override
    public void setHudWidth(int hudWidth) {
        this.hudWidth = hudWidth;
    }

    @Override
    public void setHudHeight(int hudHeight) {
        this.hudHeight = hudHeight;
    }

    @Override
    public void setHudCornerRadius(int hudCornerRadius) {
        this.hudCornerRadius = hudCornerRadius;
    }

    @Override
    public void setClientCookie(LoginCookieInfo clientCookie) {
        this.clientCookie = clientCookie == null ? "" : JsonUtil.gson.toJson(clientCookie);
    }

    @Override
    public void setClientAccountConfig(ProfileConfigData clientAccountConfig) {
        this.clientAccountConfig = clientAccountConfig == null ? "" : JsonUtil.gson.toJson(clientAccountConfig);
    }

    @Override
    public void setEnableEmbeddedServer(boolean enableEmbeddedServer) {
        this.enableEmbeddedServer = enableEmbeddedServer;
    }

    @Override
    public boolean getEnable() {
        return enable;
    }

    @Override
    public boolean getShowTranslatedCnLyrics() {
        return showTranslatedCnLyrics;
    }

    @Override
    public boolean getDisableVanillaMusic() {
        return disableVanillaMusic;
    }

    @Override
    public boolean getHideHudWhenNotPlaying() {
        return hideHudWhenNotPlaying;
    }

    @Override
    public boolean getEnableHud() {
        return enableHud;
    }

    @Override
    public Quality getPrimaryChosenQuality() {
        return primaryChosenQuality;
    }

    @Override
    public VerticalAlign getHudVerticalPosition() {
        return hudVerticalPosition;
    }

    @Override
    public HorizontalAlign getHudHorizontalPosition() {
        return hudHorizontalPosition;
    }

    @Override
    public int getHudOffsetX() {
        return hudOffsetX;
    }

    @Override
    public int getHudOffsetY() {
        return hudOffsetY;
    }

    @Override
    public int getHudWidth() {
        return hudWidth;
    }

    @Override
    public int getHudHeight() {
        return hudHeight;
    }

    @Override
    public int getHudCornerRadius() {
        return hudCornerRadius;
    }

    @Override
    public LoginCookieInfo getClientCookie() {
        return parseJson(clientCookie, LoginCookieInfo.class);
    }

    @Override
    public ProfileConfigData getClientAccountConfig() {
        return parseJson(clientAccountConfig, ProfileConfigData.class);
    }

    @Override
    public boolean getEnableEmbeddedServer() {
        return enableEmbeddedServer;
    }

    @Override
    public synchronized void save() {
        SimpleTomlConfig.write(SimpleTomlConfig.path(FILE_NAME), List.of(
                new SimpleTomlConfig.Entry("enable", "Enable Music Hud functions", enable),
                new SimpleTomlConfig.Entry("showTranslatedCnLyrics", "Show translated Chinese lyrics", showTranslatedCnLyrics),
                new SimpleTomlConfig.Entry("disableVanillaMusic", "Disable vanilla game music", disableVanillaMusic),
                new SimpleTomlConfig.Entry("hideHudWhenNotPlaying", "Hide HUD when not playing music", hideHudWhenNotPlaying),
                new SimpleTomlConfig.Entry("enableHud", "Enable HUD", enableHud),
                new SimpleTomlConfig.Entry("primaryChosenQuality", "Primary chosen quality", primaryChosenQuality.name()),
                new SimpleTomlConfig.Entry("verticalPosition", "Vertical position (TOP|CENTER|BOTTOM)", hudVerticalPosition.name()),
                new SimpleTomlConfig.Entry("horizontalPosition", "Horizontal position (LEFT|CENTER|RIGHT)", hudHorizontalPosition.name()),
                new SimpleTomlConfig.Entry("hudOffsetX", "HUD offset x", hudOffsetX),
                new SimpleTomlConfig.Entry("hudOffsetY", "HUD offset y", hudOffsetY),
                new SimpleTomlConfig.Entry("hudWidth", "HUD width", hudWidth),
                new SimpleTomlConfig.Entry("hudHeight", "HUD height", hudHeight),
                new SimpleTomlConfig.Entry("hudCornerRadius", "HUD rounded corner radius", hudCornerRadius),
                new SimpleTomlConfig.Entry("clientCookie", "Client NCM cookie json", clientCookie),
                new SimpleTomlConfig.Entry("clientAccountConfig", "Client account config json", clientAccountConfig),
                new SimpleTomlConfig.Entry("enableEmbeddedServer", "Enable embedded server for singleplayer or LAN multiplayer", enableEmbeddedServer)
        ));
    }

    private static <T> T parseJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JsonUtil.gson.fromJson(json, type);
        } catch (RuntimeException e) {
            MusicHud.LOGGER.warn("Failed to parse client config JSON for {}", type.getSimpleName(), e);
            return null;
        }
    }
}
