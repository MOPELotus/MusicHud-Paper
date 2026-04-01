package indi.etern.musichud.network.payloads.pushMessages.c2s;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.ServerManagementServerService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record AdminRemoveMusicFromQueueMessage(int index, long id) implements C2SPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, AdminRemoveMusicFromQueueMessage> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    AdminRemoveMusicFromQueueMessage::index,
                    ByteBufCodecs.LONG,
                    AdminRemoveMusicFromQueueMessage::id,
                    AdminRemoveMusicFromQueueMessage::new
            );

    @RegisterMark
    public static final class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    AdminRemoveMusicFromQueueMessage.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((payload, player) -> ServerManagementServerService.getInstance().forceRemoveMusicFromQueue(
                            payload.index(),
                            payload.id(),
                            player
                    ))
            );
        }
    }
}
