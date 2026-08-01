package indi.etern.musichud.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.platform.plugin.velocity.config.VelocityServerConfig;
import indi.etern.musichud.platform.plugin.velocity.event.VelocityEventService;
import indi.etern.musichud.platform.plugin.velocity.network.VelocityNetworkManager;
import indi.etern.musichud.server.api.ApiServerManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Plugin(
        id = "musichud",
        name = "MusicHud",
        version = "1.2.15",
        description = "MusicHud cross-server service for Velocity",
        authors = {"Etern", "Lotus"}
)
public final class VelocityInitializer {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private VelocityNetworkManager networkManager;

    @Inject
    public VelocityInitializer(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = canonicalDataDirectory(dataDirectory);
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        MusicHud.setCurrentEnvironment(Environment.of(Environment.Side.SERVER, Environment.Platform.VELOCITY));
        VelocityServerConfig.getInstance().initialize(dataDirectory);

        networkManager = VelocityNetworkManager.getInstance();
        networkManager.initialize(proxy);
        try {
            MusicHud.init();
            MusicHud.onConfigLoaded();
            logger.info("MusicHud Velocity initialized with shared server core at {}", dataDirectory);
        } catch (RuntimeException exception) {
            shutdown();
            throw exception;
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!(event.getSource() instanceof Player player) || networkManager == null) {
            return;
        }
        String channel = event.getIdentifier().getId();
        if (!networkManager.handles(channel)) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        networkManager.handle(player, channel, event.getData());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        VelocityEventService.getInstance().firePlayerQuit(event.getPlayer());
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        shutdown();
    }

    private void shutdown() {
        VelocityEventService.getInstance().fireProxyStopping();
        ApiServerManager apiServerManager = ApiServerManager.getInstance();
        if (apiServerManager != null) {
            apiServerManager.stopApiServer();
        }
        if (networkManager != null) {
            networkManager.close();
            networkManager = null;
        }
    }

    private Path canonicalDataDirectory(Path velocityDataDirectory) {
        Path parent = velocityDataDirectory.getParent();
        Path target = parent == null ? Path.of("MusicHud") : parent.resolve("MusicHud");
        if (!velocityDataDirectory.equals(target) && Files.exists(velocityDataDirectory) && !Files.exists(target)) {
            try {
                Files.move(velocityDataDirectory, target);
            } catch (IOException exception) {
                logger.warn("Unable to migrate Velocity MusicHud data directory from {} to {}", velocityDataDirectory, target, exception);
            }
        }
        return target;
    }
}
