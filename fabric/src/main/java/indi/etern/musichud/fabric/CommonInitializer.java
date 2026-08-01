package indi.etern.musichud.fabric;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.platform.mod.config.ClientConfigDefinition;
import indi.etern.musichud.platform.mod.config.ServerConfigDefinition;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class CommonInitializer implements ModInitializer {

    @Override
    public void onInitialize() {
        boolean inClient = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
        MusicHud.setCurrentEnvironment(Environment.of(inClient ? Environment.Side.CLIENT : Environment.Side.SERVER, Environment.Platform.FABRIC));
        MusicHud.setConfigDirectory(FabricLoader.getInstance().getConfigDir());
        ServerConfigDefinition.getInstance().load();
        if (inClient) {
            ClientConfigDefinition.getInstance().load();
        }
        MusicHudPlaytestCommand.register();
        MusicHud.init();
        MusicHud.onConfigLoaded();
    }
}
