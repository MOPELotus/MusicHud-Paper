package dev.architectury.networking;

import indi.etern.musichud.paper.MusicHudPaper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public final class NetworkManager {
    private NetworkManager() {
    }

    public static <T extends CustomPacketPayload> void registerReceiver(
            Side side,
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkReceiver<T> receiver
    ) {
        MusicHudPaper.getInstance().registerReceiver(side, type, codec, receiver);
    }

    public static void registerS2CPayloadType(
            CustomPacketPayload.Type<? extends CustomPacketPayload> type,
            StreamCodec<? super RegistryFriendlyByteBuf, ? extends CustomPacketPayload> codec
    ) {
        MusicHudPaper.getInstance().registerS2CPayloadTypeUnchecked(type, codec);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        MusicHudPaper.getInstance().sendToPlayer(player, payload);
    }

    public static void sendToPlayers(Collection<ServerPlayer> players, CustomPacketPayload payload) {
        MusicHudPaper.getInstance().sendToPlayers(players, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
    }

    public enum Side {
        C2S,
        S2C
    }

    @FunctionalInterface
    public interface NetworkReceiver<T extends CustomPacketPayload> {
        void receive(T payload, PacketContext context);
    }

    public interface PacketContext {
        ServerPlayer getPlayer();

        void queue(Runnable runnable);
    }
}
