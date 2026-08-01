package indi.etern.musichud.neoforge;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.platform.mod.config.ClientConfigDefinition;
import indi.etern.musichud.platform.mod.config.ServerConfigDefinition;
import indi.etern.musichud.platform.mod.neoforge.event.NeoForgeClientEventService;
import indi.etern.musichud.platform.mod.neoforge.event.NeoForgeCommonEventService;
import indi.etern.musichud.platform.mod.neoforge.network.NeoForgeNetworkManager;
import indi.etern.musichud.platform.mod.neoforge.registry.NeoForgeKeyRegistryService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(MusicHud.MOD_ID)
public final class CommonInitializer {
    private final IEventBus modEventBus;

    @SuppressWarnings("unused")
    public CommonInitializer(IEventBus eventBus, ModContainer container) {
        modEventBus = eventBus;
        modEventBus.register(this);

        boolean inClient = FMLEnvironment.getDist().isClient();
        MusicHud.setCurrentEnvironment(Environment.of(
                inClient ? Environment.Side.CLIENT : Environment.Side.SERVER,
                Environment.Platform.NEOFORGE
        ));
        MusicHud.setConfigDirectory(FMLPaths.CONFIGDIR.get());
        ServerConfigDefinition.getInstance().load();
        if (inClient) {
            ClientConfigDefinition.getInstance().load();
            container.registerExtensionPoint(IConfigScreenFactory.class, new ConfigScreenFactory());
        }
        MusicHud.init();
        NeoForgeCommonEventService.getInstance();
        if (inClient) {
            NeoForgeClientEventService.getInstance();
            hudRendererManager = HudRendererManager.getInstance();
            NeoForge.EVENT_BUS.addListener(CommonInitializer::onRenderGui);
            modEventBus.register(NeoForgeKeyRegistryService.getInstance());
        }
        MusicHud.onConfigLoaded();
    }

    public static HudRendererManager hudRendererManager;

    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (hudRendererManager != null) {
            hudRendererManager.renderFrame(event.getGuiGraphics(), event.getPartialTick());
        }
    }

    @SubscribeEvent
    private void onRegisterPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        NeoForgeNetworkManager.getInstance().onRegisterPayloadHandlers(event);
    }

    @SubscribeEvent
    private void onRegisterCommands(final RegisterCommandsEvent event) {
        MusicHudPlaytestCommand.register(event.getDispatcher());
    }
}

