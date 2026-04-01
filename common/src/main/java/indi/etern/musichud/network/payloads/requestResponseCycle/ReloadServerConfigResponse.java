package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.server.ServerActionResult;
import indi.etern.musichud.client.services.ServerManagementService;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record ReloadServerConfigResponse(ServerActionResult result) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, ReloadServerConfigResponse> CODEC =
            StreamCodec.composite(
                    ServerActionResult.CODEC,
                    ReloadServerConfigResponse::result,
                    ReloadServerConfigResponse::new
            );

    @RegisterMark
    public static final class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            NetworkReceiver<ReloadServerConfigResponse> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = (payload, player) -> ServerManagementService.getInstance().handleServerConfigReloadResult(payload.result());
            }
            INetworkRegister.getInstance().autoRegisterPayload(ReloadServerConfigResponse.class, CODEC, receiver);
        }
    }
}
