package indi.etern.musichud.paper;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

final class ArchitecturyPayloadCodec {
    private ArchitecturyPayloadCodec() {
    }

    static byte[] encode(
            StreamCodec<? super RegistryFriendlyByteBuf, ? extends CustomPacketPayload> codec,
            CustomPacketPayload payload,
            ServerPlayer player
    ) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            RegistryFriendlyByteBuf friendlyByteBuf = new RegistryFriendlyByteBuf(buffer, player.registryAccess());
            @SuppressWarnings("unchecked")
            StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload> castCodec =
                    (StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload>) codec;
            castCodec.encode(friendlyByteBuf, payload);
            byte[] bytes = new byte[friendlyByteBuf.readableBytes()];
            friendlyByteBuf.readBytes(bytes);
            return wrapPayload(bytes);
        } finally {
            buffer.release();
        }
    }

    static <T extends CustomPacketPayload> T decode(
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            byte[] bytes,
            ServerPlayer player
    ) {
        ByteBuf buffer = Unpooled.wrappedBuffer(unwrapPayload(bytes));
        try {
            RegistryFriendlyByteBuf friendlyByteBuf = new RegistryFriendlyByteBuf(buffer, player.registryAccess());
            return codec.decode(friendlyByteBuf);
        } finally {
            buffer.release();
        }
    }

    static byte[] wrapPayload(byte[] payloadBytes) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(buffer);
            friendlyByteBuf.writeByteArray(payloadBytes);
            byte[] wrapped = new byte[friendlyByteBuf.readableBytes()];
            friendlyByteBuf.readBytes(wrapped);
            return wrapped;
        } finally {
            buffer.release();
        }
    }

    static byte[] unwrapPayload(byte[] bytes) {
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        try {
            FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(buffer);
            return friendlyByteBuf.readByteArray(bytes.length);
        } finally {
            buffer.release();
        }
    }
}
