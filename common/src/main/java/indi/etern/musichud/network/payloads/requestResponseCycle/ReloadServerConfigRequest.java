package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.server.ServerActionResult;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.ServerManagementServerService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public final class ReloadServerConfigRequest implements C2SPayload {
    public static final ReloadServerConfigRequest REQUEST = new ReloadServerConfigRequest();
    public static final StreamCodec<RegistryFriendlyByteBuf, ReloadServerConfigRequest> CODEC = StreamCodec.unit(REQUEST);

    @RegisterMark
    public static final class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    ReloadServerConfigRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((payload, player) -> {
                        ServerActionResult result = ServerManagementServerService.getInstance().reloadServerConfig(player);
                        IServerNetworkService.getInstance().sendToPlayer(player, new ReloadServerConfigResponse(result));
                    })
            );
        }
    }
}
