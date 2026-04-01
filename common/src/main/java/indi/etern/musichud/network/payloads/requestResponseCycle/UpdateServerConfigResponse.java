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

public record UpdateServerConfigResponse(ServerActionResult result) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateServerConfigResponse> CODEC =
            StreamCodec.composite(
                    ServerActionResult.CODEC,
                    UpdateServerConfigResponse::result,
                    UpdateServerConfigResponse::new
            );

    @RegisterMark
    public static final class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            NetworkReceiver<UpdateServerConfigResponse> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = (payload, player) -> ServerManagementService.getInstance().handleServerConfigUpdateResult(payload.result());
            }
            INetworkRegister.getInstance().autoRegisterPayload(UpdateServerConfigResponse.class, CODEC, receiver);
        }
    }
}
