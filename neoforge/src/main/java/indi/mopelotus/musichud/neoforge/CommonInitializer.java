package indi.mopelotus.musichud.neoforge;

import indi.mopelotus.musichud.MusicHud;
import indi.mopelotus.musichud.client.ui.hud.HudRendererManager;
import indi.mopelotus.musichud.platform.Environment;
import indi.mopelotus.musichud.platform.mod.config.ClientConfigDefinition;
import indi.mopelotus.musichud.platform.mod.config.ServerConfigDefinition;
import indi.mopelotus.musichud.platform.mod.neoforge.event.NeoForgeClientEventService;
import indi.mopelotus.musichud.platform.mod.neoforge.event.NeoForgeCommonEventService;
import indi.mopelotus.musichud.platform.mod.neoforge.network.NeoForgeNetworkManager;
import indi.mopelotus.musichud.platform.mod.neoforge.registry.NeoForgeKeyRegistryService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import indi.mopelotus.musichud.client.commands.TuneWeaveClientCommands;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.fml.ModList;

@Mod(MusicHud.MOD_ID)
public final class CommonInitializer {
    private final IEventBus modEventBus;

    @SuppressWarnings("unused")
    public CommonInitializer(IEventBus eventBus, ModContainer container) {
        if (ModList.get().isLoaded("music_hud")) {
            throw new IllegalStateException("MusicHud TuneWeave cannot run with upstream MusicHud (music_hud). Remove upstream MusicHud.");
        }
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
            NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                    TuneWeaveClientCommands.register(event.getDispatcher()));
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

}

