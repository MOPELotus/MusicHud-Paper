package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.ProtocolCapability;
import indi.etern.musichud.network.ProtocolInfo;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.server.ServerPlayerRegistry;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;

import java.util.Set;
import java.util.Objects;

public record ConnectRequest(String projectId, Version clientVersion,
                             Set<ProtocolCapability> capabilities) implements C2SPayload {
    public static final ByteBufCodec<ConnectRequest> CODEC =
            ByteBufCodec.composite(
                    Codecs.STRING_UTF8, ConnectRequest::projectId,
                    Version.PACKET_CODEC, ConnectRequest::clientVersion,
                    Codecs.ofSet(() -> Codecs.ofEnum(ProtocolCapability.class)),
                    ConnectRequest::capabilities,
                    ConnectRequest::new);

    public ConnectRequest {
        projectId = Objects.requireNonNullElse(projectId, "");
        Objects.requireNonNull(clientVersion, "clientVersion");
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    public static ConnectRequest current() {
        return new ConnectRequest(ProtocolInfo.PROJECT_ID, Version.CURRENT, ProtocolInfo.CAPABILITIES);
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        private static ClientConfig clientConfig;

        static {
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                try {
                    clientConfig = ClientConfig.getInstance();
                } catch (UnsupportedOperationException e) {
                    clientConfig = null;
                }
            }
        }

        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    ConnectRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((startQRLoginRequest, player) -> {
                        boolean compatible = ProtocolInfo.isCompatible(
                                startQRLoginRequest.projectId(), startQRLoginRequest.clientVersion(),
                                startQRLoginRequest.capabilities());
                        if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT && !clientConfig.getEnabledInIntegratedServer()) {
                            if (compatible) {
                                ServerPlayerRegistry.getInstance().join(player);
                            }
                            return;
                        }
                        indi.etern.musichud.network.payloads.requestResponseCycle.ConnectResponse response =
                                ConnectResponse.current(compatible);
                        IServerNetworkService.getInstance().sendToPlayer(player, response);
                        if (compatible) {
                            ServerPlayerRegistry.getInstance().join(player);
//                            MusicPlayerServerService.getInstance().sendSyncPlayingStatusToPlayer(player);
                        }
                    })
            );
        }
    }
}
