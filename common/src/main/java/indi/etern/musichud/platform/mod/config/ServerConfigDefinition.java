package indi.etern.musichud.platform.mod.config;

import indi.etern.musichud.interfaces.ServerConfig;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ServerConfigDefinition implements ServerConfig {
    private static final String FILE_NAME = "music_hud-server.toml";
    @Getter
    private static final ServerConfigDefinition instance = new ServerConfigDefinition();

    private String serverApiBaseUrl = "http://127.0.0.1:7832";
    private boolean startupBinaryApiServerWhenLaunch = true;
    private String serverApiBinaryExecutablePath = "MusicHud/tuneweave";
    private double pusherVoteAdditionalRate = 0.5;
    private boolean useRandomCnIp = true;
    private String corsAllowOrigin = "*";
    private boolean enableProxy;
    private String proxyUrl = "https://your-proxy-url.com/?proxy=";
    private boolean enableGeneralUnblock = true;
    private boolean enableFlac = true;
    private boolean selectMaxBr;
    private boolean followSourceOrder = true;
    private int port = 7832;
    @Setter
    @Getter
    private boolean configured;

    private ServerConfigDefinition() {
    }

    public synchronized void load() {
        Path path = SimpleTomlConfig.path(FILE_NAME);
        Map<String, String> values = SimpleTomlConfig.read(path);
        serverApiBaseUrl = SimpleTomlConfig.getString(values, "serverApiBaseUrl", serverApiBaseUrl);
        startupBinaryApiServerWhenLaunch = SimpleTomlConfig.getBoolean(
                values,
                "startupBinaryApiServerWhenLaunch",
                startupBinaryApiServerWhenLaunch
        );
        serverApiBinaryExecutablePath = SimpleTomlConfig.getString(
                values,
                "serverApiBinaryExecutablePath",
                serverApiBinaryExecutablePath
        );
        pusherVoteAdditionalRate = clamp(SimpleTomlConfig.getDouble(
                values,
                "pusherVoteAdditionalRate",
                pusherVoteAdditionalRate
        ));
        useRandomCnIp = SimpleTomlConfig.getBoolean(values, "useRandomCnIp", useRandomCnIp);
        corsAllowOrigin = SimpleTomlConfig.getString(values, "corsAllowOrigin", corsAllowOrigin);
        enableProxy = SimpleTomlConfig.getBoolean(values, "enableProxy", enableProxy);
        proxyUrl = SimpleTomlConfig.getString(values, "proxyUrl", proxyUrl);
        enableGeneralUnblock = SimpleTomlConfig.getBoolean(values, "enableGeneralUnblock", enableGeneralUnblock);
        enableFlac = SimpleTomlConfig.getBoolean(values, "enableFlac", enableFlac);
        selectMaxBr = SimpleTomlConfig.getBoolean(values, "selectMaxBr", selectMaxBr);
        followSourceOrder = SimpleTomlConfig.getBoolean(values, "followSourceOrder", followSourceOrder);
        port = clampPort(SimpleTomlConfig.getInt(values, "port", port));
        // TuneWeave replaced the legacy API's 3000 default. Migrate an untouched
        // local config that already points at the new loopback origin.
        if ("http://127.0.0.1:7832".equals(serverApiBaseUrl) && port == 3000) {
            port = 7832;
        }
        configured = true;
        save();
    }

    @Override
    public void setServerApiBaseUrl(String serverApiBaseUrl) {
        this.serverApiBaseUrl = serverApiBaseUrl;
    }

    @Override
    public void setStartupBinaryApiServerWhenLaunch(boolean startupBinaryApiServerWhenLaunch) {
        this.startupBinaryApiServerWhenLaunch = startupBinaryApiServerWhenLaunch;
    }

    @Override
    public void setServerApiBinaryExecutablePath(String serverApiBinaryExecutablePath) {
        this.serverApiBinaryExecutablePath = serverApiBinaryExecutablePath;
    }

    @Override
    public void setPusherVoteAdditionalRate(double pusherVoteAdditionalRate) {
        this.pusherVoteAdditionalRate = clamp(pusherVoteAdditionalRate);
    }

    @Override
    public void setUseRandomCnIp(boolean useRandomCnIp) {
        this.useRandomCnIp = useRandomCnIp;
    }

    @Override
    public String getCorsAllowOrigin() {
        return corsAllowOrigin;
    }

    @Override
    public void setCorsAllowOrigin(String corsAllowOrigin) {
        this.corsAllowOrigin = corsAllowOrigin == null ? "*" : corsAllowOrigin;
    }

    @Override
    public boolean getEnableProxy() {
        return enableProxy;
    }

    @Override
    public void setEnableProxy(boolean enableProxy) {
        this.enableProxy = enableProxy;
    }

    @Override
    public String getProxyUrl() {
        return proxyUrl;
    }

    @Override
    public void setProxyUrl(String proxyUrl) {
        this.proxyUrl = proxyUrl == null ? "" : proxyUrl;
    }

    @Override
    public boolean getEnableGeneralUnblock() {
        return enableGeneralUnblock;
    }

    @Override
    public void setEnableGeneralUnblock(boolean enableGeneralUnblock) {
        this.enableGeneralUnblock = enableGeneralUnblock;
    }

    @Override
    public boolean getEnableFlac() {
        return enableFlac;
    }

    @Override
    public void setEnableFlac(boolean enableFlac) {
        this.enableFlac = enableFlac;
    }

    @Override
    public boolean getSelectMaxBr() {
        return selectMaxBr;
    }

    @Override
    public void setSelectMaxBr(boolean selectMaxBr) {
        this.selectMaxBr = selectMaxBr;
    }

    @Override
    public boolean getFollowSourceOrder() {
        return followSourceOrder;
    }

    @Override
    public void setFollowSourceOrder(boolean followSourceOrder) {
        this.followSourceOrder = followSourceOrder;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void setPort(int port) {
        this.port = clampPort(port);
    }

    @Override
    public String getServerApiBaseUrl() {
        return serverApiBaseUrl;
    }

    @Override
    public boolean getStartupBinaryApiServerWhenLaunch() {
        return startupBinaryApiServerWhenLaunch;
    }

    @Override
    public String getServerApiBinaryExecutablePath() {
        return serverApiBinaryExecutablePath;
    }

    @Override
    public double getPusherVoteAdditionalRate() {
        return pusherVoteAdditionalRate;
    }

    @Override
    public boolean getUseRandomCnIp() {
        return useRandomCnIp;
    }

    @Override
    public synchronized void save() {
        SimpleTomlConfig.write(SimpleTomlConfig.path(FILE_NAME), List.of(
                new SimpleTomlConfig.Entry("serverApiBaseUrl", "Server API base URL configuration", serverApiBaseUrl),
                new SimpleTomlConfig.Entry("startupBinaryApiServerWhenLaunch", "Start binary API server when game launches", startupBinaryApiServerWhenLaunch),
                new SimpleTomlConfig.Entry("serverApiBinaryExecutablePath", "Server API binary executable path", serverApiBinaryExecutablePath),
                new SimpleTomlConfig.Entry("pusherVoteAdditionalRate", "Skip vote threshold added by the current pusher (0.0 - 1.0)", pusherVoteAdditionalRate),
                new SimpleTomlConfig.Entry("useRandomCnIp", "Use random Chinese IP provided by API server", useRandomCnIp),
                new SimpleTomlConfig.Entry("corsAllowOrigin", "CORS allow origin for the API server", corsAllowOrigin),
                new SimpleTomlConfig.Entry("enableProxy", "Enable reverse proxy support", enableProxy),
                new SimpleTomlConfig.Entry("proxyUrl", "Proxy server URL", proxyUrl),
                new SimpleTomlConfig.Entry("enableGeneralUnblock", "Enable global unblock", enableGeneralUnblock),
                new SimpleTomlConfig.Entry("enableFlac", "Enable FLAC lossless quality", enableFlac),
                new SimpleTomlConfig.Entry("selectMaxBr", "Select highest bitrate when lossless quality is enabled", selectMaxBr),
                new SimpleTomlConfig.Entry("followSourceOrder", "Match sources strictly in source-list order", followSourceOrder),
                new SimpleTomlConfig.Entry("port", "API server listening port", port)
        ));
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static int clampPort(int value) {
        return Math.max(1, Math.min(65535, value));
    }
}
