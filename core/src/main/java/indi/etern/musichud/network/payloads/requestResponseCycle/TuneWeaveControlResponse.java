package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;

import java.util.Objects;
import java.util.function.Consumer;

/** Client-side UI bridge. It contains data only; no credential ever crosses this packet. */
public record TuneWeaveControlResponse(String requestId, String action, boolean ok, String payload, String message) implements S2CPayload {
    public static final ByteBufCodec<TuneWeaveControlResponse> CODEC = ByteBufCodec.composite(
            Codecs.STRING_UTF8, TuneWeaveControlResponse::requestId,
            Codecs.STRING_UTF8, TuneWeaveControlResponse::action,
            Codecs.BOOL, TuneWeaveControlResponse::ok,
            Codecs.STRING_UTF8, TuneWeaveControlResponse::payload,
            Codecs.STRING_UTF8, TuneWeaveControlResponse::message,
            TuneWeaveControlResponse::new
    );
    private static volatile Consumer<TuneWeaveControlResponse> receiver = response -> { };

    public static void setReceiver(Consumer<TuneWeaveControlResponse> receiver) {
        TuneWeaveControlResponse.receiver = Objects.requireNonNullElse(receiver, response -> { });
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(TuneWeaveControlResponse.class, CODEC,
                    (response, player) -> receiver.accept(response));
        }
    }
}
