package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.ApiResponsePayload;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class TuneWeaveEndpointResponse extends ApiResponsePayload {
    public static final ByteBufCodec<TuneWeaveEndpointResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.INT, TuneWeaveEndpointResponse::getStatusCode,
                    Codecs.STRING_UTF8, TuneWeaveEndpointResponse::getErrorCode,
                    Codecs.STRING_UTF8, TuneWeaveEndpointResponse::getErrorMessage,
                    Codecs.LARGE_STRING_UTF8, TuneWeaveEndpointResponse::getDataJson,
                    Codecs.LARGE_STRING_UTF8, TuneWeaveEndpointResponse::getMetaJson,
                    TuneWeaveEndpointResponse::new
            )
    );

    private final int statusCode;
    private final String errorCode;
    private final String errorMessage;
    private final String dataJson;
    private final String metaJson;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    TuneWeaveEndpointResponse.class, CODEC,
                    (response, player) -> RequestResponseManager.complete(response));
        }
    }
}
