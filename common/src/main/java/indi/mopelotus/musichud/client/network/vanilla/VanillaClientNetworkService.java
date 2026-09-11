package indi.mopelotus.musichud.client.network.vanilla;

import indi.mopelotus.musichud.MusicHud;
import indi.mopelotus.musichud.interfaces.ClientConfig;
import indi.mopelotus.musichud.network.IClientNetworkService;
import indi.mopelotus.musichud.network.NetworkReceiver;
import indi.mopelotus.musichud.network.payloads.C2SPayload;
import indi.mopelotus.musichud.network.payloads.requestResponseCycle.ConnectRequest;
import net.minecraft.client.Minecraft;

public interface VanillaClientNetworkService extends IClientNetworkService {
    void sendToNetworkServer(C2SPayload payload);

    @Override
    default <T extends C2SPayload> void sendToServer(T payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCurrentServer() != null && (MusicHud.getConnectStatus() == MusicHud.ConnectStatus.CONNECTED
                || payload instanceof ConnectRequest)) {
            indi.mopelotus.musichud.network.PayloadFragments.sendC2S(payload, this::sendToNetworkServer);
        } else if ((minecraft.getCurrentServer() != null || minecraft.player != null)
                && ClientConfig.getInstance().getEnableIsolatedMode()){// in single player game or isolated client
            //noinspection unchecked
            NetworkReceiver<T> receiver = (NetworkReceiver<T>) IVanillaNetworkRegister.getMetaDataOrNew(payload.getClass(), null).receiver();
            if (receiver != null) {
                receiver.receive(payload, VanillaPlayerProxy.ofPlayer(minecraft.player));
            }
        } else {
            MusicHud.LOGGER.warn("Dropped C2S payload {}: connection status is {} and isolated mode is disabled",
                    payload.getClass().getName(), MusicHud.getConnectStatus());
        }

    }
}
