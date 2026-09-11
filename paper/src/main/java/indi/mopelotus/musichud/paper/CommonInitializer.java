package indi.mopelotus.musichud.paper;

import indi.mopelotus.musichud.MusicHud;
import indi.mopelotus.musichud.platform.Environment;
import indi.mopelotus.musichud.platform.plugin.paper.config.ServerConfigDefinition;
import indi.mopelotus.musichud.platform.plugin.paper.event.PaperEventService;
import indi.mopelotus.musichud.platform.plugin.paper.network.PaperNetworkManager;
import indi.mopelotus.musichud.server.api.ApiServerManager;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("unused")
public final class CommonInitializer extends JavaPlugin {
    private PaperEventService eventService;
    private PaperNetworkManager networkManager;

    @Override
    public void onEnable() {
        MusicHud.setCurrentEnvironment(Environment.of(Environment.Side.SERVER, Environment.Platform.PAPER));
        MusicHud.setConfigDirectory(getDataFolder().toPath());

        saveDefaultConfig();

        eventService = PaperEventService.getInstance();
        eventService.initialize(this);
        networkManager = PaperNetworkManager.getInstance();
        networkManager.initialize(this);

        ServerConfigDefinition serverConfig = ServerConfigDefinition.getInstance();
        serverConfig.initialize(this);

        try {
            MusicHud.init();
            MusicHud.onConfigLoaded();
        } catch (RuntimeException e) {
            shutdownServices();
            throw e;
        }
    }

    @Override
    public void onDisable() {
        shutdownServices();
    }

    private void shutdownServices() {
        if (networkManager != null) {
            networkManager.close();
            networkManager = null;
        }
        if (eventService != null) {
            eventService.fireServerStopping();
        }
        if (ApiServerManager.getInstance() != null) ApiServerManager.getInstance().stopApiServer();
        eventService = null;
    }
}
