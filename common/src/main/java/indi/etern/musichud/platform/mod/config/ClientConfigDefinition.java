package indi.etern.musichud.platform.mod.config;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.AutoConnectServerFilterType;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.beans.user.ProfileConfigData;
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
    private boolean enableMarqueeText = true;
    private boolean mixWithVanillaSoundVolume = true;
    private boolean muted;
    private int soundVolume = 100;
    private int soundVolumeInterval = 10;
    private Quality primaryChosenQuality = Quality.LOSSLESS;
    private double mainScreenAdditionalBackgroundDarken = 0.5;
    private double hudBackgroundMixAlpha = 0.5;
    private String hudVerticalPosition = "TOP";
    private String hudHorizontalPosition = "LEFT";
    private int hudOffsetX = 16;
    private int hudOffsetY = 16;
    private int hudWidth = 152;
    private int hudHeight = 52;
    private int hudCornerRadius = 8;
    private String clientCookie = "";
    private String clientAccountConfig = "";
    private String tuneWeaveClientApiBaseUrl = "http://127.0.0.1:7832";
    private String tuneWeaveClientCredentials = "{}";
    private boolean manageTuneWeaveLocally;
    private boolean enabledInIntegratedServer = true;
    private boolean enableAutoConnect = true;
    private boolean enableIsolatedMode = true;
    private AutoConnectServerFilterType connectServerFilterType = AutoConnectServerFilterType.BLACK_LIST;
    private String autoConnectBlackList = "[]";
    private String autoConnectWhiteList = "[]";
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
        enableMarqueeText = SimpleTomlConfig.getBoolean(values, "enableMarqueeText", enableMarqueeText);
        mixWithVanillaSoundVolume = SimpleTomlConfig.getBoolean(values, "mixWithVanillaSoundVolume", mixWithVanillaSoundVolume);
        muted = SimpleTomlConfig.getBoolean(values, "muted", SimpleTomlConfig.getBoolean(values, "Muted", muted));
        soundVolume = SimpleTomlConfig.getInt(values, "soundVolume", soundVolume);
        soundVolumeInterval = SimpleTomlConfig.getInt(values, "soundVolumeInterval", soundVolumeInterval);
        primaryChosenQuality = SimpleTomlConfig.getEnum(values, "primaryChosenQuality", Quality.class, primaryChosenQuality);
        mainScreenAdditionalBackgroundDarken = SimpleTomlConfig.getDouble(values, "mainScreenAdditionalBackgroundDarken", mainScreenAdditionalBackgroundDarken);
        hudBackgroundMixAlpha = SimpleTomlConfig.getDouble(values, "hudBackgroundMixAlpha", hudBackgroundMixAlpha);
        hudVerticalPosition = SimpleTomlConfig.getString(values, "verticalPosition", hudVerticalPosition);
        hudHorizontalPosition = SimpleTomlConfig.getString(values, "horizontalPosition", hudHorizontalPosition);
        hudOffsetX = SimpleTomlConfig.getInt(values, "hudOffsetX", hudOffsetX);
        hudOffsetY = SimpleTomlConfig.getInt(values, "hudOffsetY", hudOffsetY);
        hudWidth = SimpleTomlConfig.getInt(values, "hudWidth", hudWidth);
        hudHeight = SimpleTomlConfig.getInt(values, "hudHeight", hudHeight);
        hudCornerRadius = SimpleTomlConfig.getInt(values, "hudCornerRadius", hudCornerRadius);
        clientCookie = SimpleTomlConfig.getString(values, "clientCookie", clientCookie);
        clientAccountConfig = SimpleTomlConfig.getString(values, "clientAccountConfig", clientAccountConfig);
        tuneWeaveClientApiBaseUrl = SimpleTomlConfig.getString(values, "tuneWeaveClientApiBaseUrl", tuneWeaveClientApiBaseUrl);
        tuneWeaveClientCredentials = SimpleTomlConfig.getString(values, "tuneWeaveClientCredentials", tuneWeaveClientCredentials);
        manageTuneWeaveLocally = SimpleTomlConfig.getBoolean(values, "manageTuneWeaveLocally", manageTuneWeaveLocally);
        enabledInIntegratedServer = SimpleTomlConfig.getBoolean(
                values,
                "enabledInIntegratedServer",
                SimpleTomlConfig.getBoolean(values, "enableEmbeddedServer", enabledInIntegratedServer)
        );
        enableAutoConnect = SimpleTomlConfig.getBoolean(values, "enableAutoConnect", enableAutoConnect);
        enableIsolatedMode = SimpleTomlConfig.getBoolean(values, "enableClientOnlyMode", enableIsolatedMode);
        connectServerFilterType = SimpleTomlConfig.getEnum(values, "autoConnectServerFilterType", AutoConnectServerFilterType.class, connectServerFilterType);
        autoConnectBlackList = SimpleTomlConfig.getString(values, "autoConnectBlackList", autoConnectBlackList);
        autoConnectWhiteList = SimpleTomlConfig.getString(values, "autoConnectWhiteList", autoConnectWhiteList);
        migrateEmptyWhiteListDefault(values);
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
    public void setMixWithVanillaSoundVolume(boolean mixWithVanillaSoundVolume) {
        this.mixWithVanillaSoundVolume = mixWithVanillaSoundVolume;
    }

    @Override
    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    @Override
    public void setSoundVolume(int soundVolume) {
        if (soundVolume == 0) {
            muted = true;
            return;
        }
        muted = false;
        this.soundVolume = soundVolume;
    }

    @Override
    public void forceSetSoundVolume(int soundVolume) {
        muted = soundVolume == 0;
        this.soundVolume = soundVolume;
    }

    @Override
    public void setSoundVolumeInterval(int soundVolumeInterval) {
        this.soundVolumeInterval = soundVolumeInterval;
    }

    @Override
    public void setPrimaryChosenQuality(Quality primaryChosenQuality) {
        this.primaryChosenQuality = primaryChosenQuality == null ? Quality.LOSSLESS : primaryChosenQuality;
    }

    @Override
    public void setMainScreenAdditionalBackgroundDarken(double additionalBackgroundDarken) {
        mainScreenAdditionalBackgroundDarken = additionalBackgroundDarken;
    }

    @Override
    public void setHudBackgroundMixAlpha(double hudBackgroundMixAlpha) {
        this.hudBackgroundMixAlpha = hudBackgroundMixAlpha;
    }

    @Override
    public void setHudVerticalPosition(String hudVerticalPosition) {
        this.hudVerticalPosition = hudVerticalPosition == null || hudVerticalPosition.isBlank() ? "TOP" : hudVerticalPosition;
    }

    @Override
    public void setHudHorizontalPosition(String hudHorizontalPosition) {
        this.hudHorizontalPosition = hudHorizontalPosition == null || hudHorizontalPosition.isBlank() ? "LEFT" : hudHorizontalPosition;
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
    public void setTuneWeaveClientApiBaseUrl(String tuneWeaveClientApiBaseUrl) {
        this.tuneWeaveClientApiBaseUrl = tuneWeaveClientApiBaseUrl == null || tuneWeaveClientApiBaseUrl.isBlank()
                ? "http://127.0.0.1:7832" : tuneWeaveClientApiBaseUrl.trim();
    }

    @Override
    public void setTuneWeaveClientCredentials(String tuneWeaveClientCredentials) {
        this.tuneWeaveClientCredentials = tuneWeaveClientCredentials == null || tuneWeaveClientCredentials.isBlank()
                ? "{}" : tuneWeaveClientCredentials;
    }

    @Override
    public void setManageTuneWeaveLocally(boolean manageTuneWeaveLocally) {
        this.manageTuneWeaveLocally = manageTuneWeaveLocally;
    }

    @Override
    public void setEnabledInIntegratedServer(boolean enabledInIntegratedServer) {
        this.enabledInIntegratedServer = enabledInIntegratedServer;
    }

    @Override
    public void setEnableAutoConnect(boolean autoConnect) {
        enableAutoConnect = autoConnect;
    }

    @Override
    public void setEnableIsolatedMode(boolean autoConnect) {
        enableIsolatedMode = autoConnect;
    }

    @Override
    public void setConnectServerFilterType(AutoConnectServerFilterType autoConnectServerFilterType) {
        connectServerFilterType = autoConnectServerFilterType == null ? AutoConnectServerFilterType.BLACK_LIST : autoConnectServerFilterType;
    }

    @Override
    public void setBlackList(List<String> blackList) {
        autoConnectBlackList = JsonUtil.gson.toJson(blackList == null ? List.of() : blackList);
    }

    @Override
    public void setWhiteList(List<String> whiteList) {
        autoConnectWhiteList = JsonUtil.gson.toJson(whiteList == null ? List.of() : whiteList);
    }

    @Override
    public void setEnableMarqueeText(boolean enableMarqueeText) {
        this.enableMarqueeText = enableMarqueeText;
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
    public boolean getMixWithVanillaSoundVolume() {
        return mixWithVanillaSoundVolume;
    }

    @Override
    public boolean getMuted() {
        return muted;
    }

    @Override
    public int getSoundVolume() {
        return soundVolume;
    }

    @Override
    public int getSoundVolumeInterval() {
        return soundVolumeInterval;
    }

    @Override
    public Quality getPrimaryChosenQuality() {
        return primaryChosenQuality;
    }

    @Override
    public double getMainScreenAdditionalBackgroundDarken() {
        return mainScreenAdditionalBackgroundDarken;
    }

    @Override
    public double getHudBackgroundMixAlpha() {
        return hudBackgroundMixAlpha;
    }

    @Override
    public String getHudVerticalPosition() {
        return hudVerticalPosition;
    }

    @Override
    public String getHudHorizontalPosition() {
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
    public String getTuneWeaveClientApiBaseUrl() {
        return tuneWeaveClientApiBaseUrl;
    }

    @Override
    public String getTuneWeaveClientCredentials() {
        return tuneWeaveClientCredentials;
    }

    @Override
    public boolean getManageTuneWeaveLocally() {
        return manageTuneWeaveLocally;
    }

    @Override
    public boolean getEnabledInIntegratedServer() {
        return enabledInIntegratedServer;
    }

    @Override
    public boolean getEnableAutoConnect() {
        return enableAutoConnect;
    }

    @Override
    public boolean getEnableIsolatedMode() {
        return enableIsolatedMode;
    }

    @Override
    public AutoConnectServerFilterType getConnectServerFilterType() {
        return connectServerFilterType;
    }

    @Override
    public List<String> getBlackList() {
        return parseList(autoConnectBlackList);
    }

    @Override
    public List<String> getWhiteList() {
        return parseList(autoConnectWhiteList);
    }

    @Override
    public boolean getEnableMarqueeText() {
        return enableMarqueeText;
    }

    @Override
    public synchronized void save() {
        SimpleTomlConfig.write(SimpleTomlConfig.path(FILE_NAME), List.of(
                new SimpleTomlConfig.Entry("enable", "Enable Music Hud functions", enable),
                new SimpleTomlConfig.Entry("showTranslatedCnLyrics", "Show translated Chinese lyrics", showTranslatedCnLyrics),
                new SimpleTomlConfig.Entry("disableVanillaMusic", "Disable vanilla game music", disableVanillaMusic),
                new SimpleTomlConfig.Entry("hideHudWhenNotPlaying", "Hide HUD when not playing music", hideHudWhenNotPlaying),
                new SimpleTomlConfig.Entry("enableHud", "Enable HUD", enableHud),
                new SimpleTomlConfig.Entry("enableMarqueeText", "Enable marquee animation on overflow text", enableMarqueeText),
                new SimpleTomlConfig.Entry("mixWithVanillaSoundVolume", "Mix Music Hud volume with vanilla music volume", mixWithVanillaSoundVolume),
                new SimpleTomlConfig.Entry("muted", "Record muted switch", muted),
                new SimpleTomlConfig.Entry("soundVolume", "Sound volume for Music Hud audio", soundVolume),
                new SimpleTomlConfig.Entry("soundVolumeInterval", "Sound volume interval for hot key adjustment", soundVolumeInterval),
                new SimpleTomlConfig.Entry("primaryChosenQuality", "Primary chosen quality", primaryChosenQuality.name()),
                new SimpleTomlConfig.Entry("mainScreenAdditionalBackgroundDarken", "Main screen additional background darken rate", mainScreenAdditionalBackgroundDarken),
                new SimpleTomlConfig.Entry("hudBackgroundMixAlpha", "HUD background mix alpha", hudBackgroundMixAlpha),
                new SimpleTomlConfig.Entry("verticalPosition", "Vertical position (TOP|CENTER|BOTTOM)", hudVerticalPosition),
                new SimpleTomlConfig.Entry("horizontalPosition", "Horizontal position (LEFT|CENTER|RIGHT)", hudHorizontalPosition),
                new SimpleTomlConfig.Entry("hudOffsetX", "HUD offset x", hudOffsetX),
                new SimpleTomlConfig.Entry("hudOffsetY", "HUD offset y", hudOffsetY),
                new SimpleTomlConfig.Entry("hudWidth", "HUD width", hudWidth),
                new SimpleTomlConfig.Entry("hudHeight", "HUD height", hudHeight),
                new SimpleTomlConfig.Entry("hudCornerRadius", "HUD rounded corner radius", hudCornerRadius),
                new SimpleTomlConfig.Entry("clientCookie", "Client NCM cookie json", clientCookie),
                new SimpleTomlConfig.Entry("clientAccountConfig", "Client account config json", clientAccountConfig),
                new SimpleTomlConfig.Entry("tuneWeaveClientApiBaseUrl", "TuneWeave address used for this client's local accounts", tuneWeaveClientApiBaseUrl),
                new SimpleTomlConfig.Entry("tuneWeaveClientCredentials", "Local TuneWeave caller credentials JSON; never sent to a game server", tuneWeaveClientCredentials),
                new SimpleTomlConfig.Entry("manageTuneWeaveLocally", "Automatically download, verify and run local TuneWeave for this client", manageTuneWeaveLocally),
                new SimpleTomlConfig.Entry("enabledInIntegratedServer", "Enable embedded server for singleplayer or LAN multiplayer", enabledInIntegratedServer),
                new SimpleTomlConfig.Entry("enableAutoConnect", "Enable auto connect", enableAutoConnect),
                new SimpleTomlConfig.Entry("enableClientOnlyMode", "Enable client-only isolated mode", enableIsolatedMode),
                new SimpleTomlConfig.Entry("autoConnectServerFilterType", "Auto connecting servers filter type (WHITE_LIST|BLACK_LIST)", connectServerFilterType.name()),
                new SimpleTomlConfig.Entry("autoConnectBlackList", "Auto connecting servers black list JSON", autoConnectBlackList),
                new SimpleTomlConfig.Entry("autoConnectWhiteList", "Auto connecting servers white list JSON", autoConnectWhiteList)
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

    @SuppressWarnings("unchecked")
    private static List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = JsonUtil.gson.fromJson(json, List.class);
            return values == null ? List.of() : values;
        } catch (RuntimeException e) {
            MusicHud.LOGGER.warn("Failed to parse client config list", e);
            return List.of();
        }
    }

    private void migrateEmptyWhiteListDefault(Map<String, String> values) {
        String configuredFilterType = values.get("autoConnectServerFilterType");
        if (!enableAutoConnect
                || configuredFilterType == null
                || !configuredFilterType.equalsIgnoreCase(AutoConnectServerFilterType.WHITE_LIST.name())
                || !parseList(autoConnectBlackList).isEmpty()
                || !parseList(autoConnectWhiteList).isEmpty()) {
            return;
        }

        connectServerFilterType = AutoConnectServerFilterType.BLACK_LIST;
    }
}

