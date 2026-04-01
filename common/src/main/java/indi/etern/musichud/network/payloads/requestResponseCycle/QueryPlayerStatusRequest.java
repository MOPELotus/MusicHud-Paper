package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.server.PlayerQueryResult;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.ServerManagementServerService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record QueryPlayerStatusRequest(String query) implements C2SPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, QueryPlayerStatusRequest> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    QueryPlayerStatusRequest::query,
                    QueryPlayerStatusRequest::new
            );

    @RegisterMark
    public static final class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    QueryPlayerStatusRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((payload, player) -> {
                        PlayerQueryResult result = ServerManagementServerService.getInstance().queryPlayerInfo(player, payload.query());
                        IServerNetworkService.getInstance().sendToPlayer(player, new QueryPlayerStatusResponse(result));
                    })
            );
        }
    }
}
