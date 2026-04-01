package indi.etern.musichud.network.payloads.pushMessages.c2s;

import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.ServerManagementServerService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record AdminRemoveIdlePlaySourceMessage(UUID ownerUuid, long id, Class<?> collectionType) implements C2SPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, AdminRemoveIdlePlaySourceMessage> CODEC =
            StreamCodec.composite(
                    Codecs.UUID,
                    AdminRemoveIdlePlaySourceMessage::ownerUuid,
                    ByteBufCodecs.LONG,
                    AdminRemoveIdlePlaySourceMessage::id,
                    Codecs.CLASS,
                    AdminRemoveIdlePlaySourceMessage::collectionType,
                    AdminRemoveIdlePlaySourceMessage::new
            );

    @RegisterMark
    public static final class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    AdminRemoveIdlePlaySourceMessage.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((payload, player) -> ServerManagementServerService.getInstance().forceRemoveIdlePlaySource(
                            payload.ownerUuid(),
                            payload.id(),
                            payload.collectionType(),
                            player
                    ))
            );
        }
    }
}
