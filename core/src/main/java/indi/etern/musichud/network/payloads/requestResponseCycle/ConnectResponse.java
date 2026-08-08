package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.IConnectionManager;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.ProtocolCapability;
import indi.etern.musichud.network.ProtocolInfo;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.server.api.ApiProvider;

import java.util.List;
import java.util.Objects;
import java.util.Set;


public record ConnectResponse(boolean accepted, String projectId, Version serverVersion,
                              Set<ProtocolCapability> capabilities,
                              List<ApiProvider> availableApis) implements S2CPayload {
    public static final ByteBufCodec<ConnectResponse> CODEC =
            ByteBufCodec.composite(
                    Codecs.BOOL,
                    ConnectResponse::accepted,
                    Codecs.STRING_UTF8,
                    ConnectResponse::projectId,
                    Version.PACKET_CODEC,
                    ConnectResponse::serverVersion,
                    Codecs.ofSet(() -> Codecs.ofEnum(ProtocolCapability.class)),
                    ConnectResponse::capabilities,
                    Codecs.ofList(() -> Codecs.ofEnum(ApiProvider.class)),
                    ConnectResponse::availableApis,
                    ConnectResponse::new
            );

    public ConnectResponse {
        projectId = Objects.requireNonNullElse(projectId, "");
        Objects.requireNonNull(serverVersion, "serverVersion");
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        availableApis = availableApis == null ? List.of() : List.copyOf(availableApis);
    }

    public static ConnectResponse current(boolean accepted) {
        return new ConnectResponse(accepted, ProtocolInfo.PROJECT_ID, Version.CURRENT,
                ProtocolInfo.CAPABILITIES, List.of(ApiProvider.TUNEWEAVE));
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            NetworkReceiver<ConnectResponse> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = (payload, player) -> IConnectionManager.getInstance().onConnectResponse(payload);
            }
            INetworkRegister.getInstance().autoRegisterPayload(
                    ConnectResponse.class, CODEC,
                    receiver
            );
        }
    }
}
