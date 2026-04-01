package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.server.ServerActionResult;
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

public record UpdateServerConfigRequest(
        String serverApiBaseUrl,
        boolean startupBinaryApiServerWhenLaunch,
        String serverApiBinaryExecutablePath,
        double pusherVoteAdditionalRate,
        boolean useRandomCnIp
) implements C2SPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateServerConfigRequest> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    UpdateServerConfigRequest::serverApiBaseUrl,
                    ByteBufCodecs.BOOL,
                    UpdateServerConfigRequest::startupBinaryApiServerWhenLaunch,
                    ByteBufCodecs.STRING_UTF8,
                    UpdateServerConfigRequest::serverApiBinaryExecutablePath,
                    ByteBufCodecs.DOUBLE,
                    UpdateServerConfigRequest::pusherVoteAdditionalRate,
                    ByteBufCodecs.BOOL,
                    UpdateServerConfigRequest::useRandomCnIp,
                    UpdateServerConfigRequest::new
            );

    @RegisterMark
    public static final class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    UpdateServerConfigRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((payload, player) -> {
                        ServerActionResult result = ServerManagementServerService.getInstance().updateServerConfig(
                                player,
                                payload.serverApiBaseUrl(),
                                payload.startupBinaryApiServerWhenLaunch(),
                                payload.serverApiBinaryExecutablePath(),
                                payload.pusherVoteAdditionalRate(),
                                payload.useRandomCnIp()
                        );
                        IServerNetworkService.getInstance().sendToPlayer(player, new UpdateServerConfigResponse(result));
                    })
            );
        }
    }
}
