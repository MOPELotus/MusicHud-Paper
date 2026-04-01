package indi.etern.musichud.network.payloads.pushMessages.c2s;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.ServerManagementServerService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public final class ForceSkipCurrentMusicMessage implements C2SPayload {
    public static final ForceSkipCurrentMusicMessage MESSAGE = new ForceSkipCurrentMusicMessage();
    public static final StreamCodec<RegistryFriendlyByteBuf, ForceSkipCurrentMusicMessage> CODEC = StreamCodec.unit(MESSAGE);

    @RegisterMark
    public static final class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    ForceSkipCurrentMusicMessage.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((payload, player) -> ServerManagementServerService.getInstance().forceSkipCurrent(player))
            );
        }
    }
}
