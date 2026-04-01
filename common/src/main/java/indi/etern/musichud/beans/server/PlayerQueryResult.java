package indi.etern.musichud.beans.server;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record PlayerQueryResult(boolean found, String query, String message, PlayerStatusInfo playerStatusInfo) {
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerQueryResult> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    PlayerQueryResult::found,
                    ByteBufCodecs.STRING_UTF8,
                    PlayerQueryResult::query,
                    ByteBufCodecs.STRING_UTF8,
                    PlayerQueryResult::message,
                    PlayerStatusInfo.CODEC,
                    PlayerQueryResult::playerStatusInfo,
                    PlayerQueryResult::new
            );
}
