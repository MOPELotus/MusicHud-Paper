package indi.etern.musichud.network.payloads.pushMessages.c2s;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.ServerPlayerRegistry;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;

public final class DisconnectMessage implements C2SPayload {
    public static final DisconnectMessage INSTANCE = new DisconnectMessage();
    public static final ByteBufCodec<DisconnectMessage> CODEC = ByteBufCodec.unit(INSTANCE);

    private DisconnectMessage() {
    }

    @RegisterMark
    public static final class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    DisconnectMessage.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((message, player) ->
                            ServerPlayerRegistry.getInstance().leave(player)));
        }
    }
}
