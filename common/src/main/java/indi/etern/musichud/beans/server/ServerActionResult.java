package indi.etern.musichud.beans.server;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ServerActionResult(boolean success, String message, ServerStatusInfo serverStatusInfo) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerActionResult> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    ServerActionResult::success,
                    ByteBufCodecs.STRING_UTF8,
                    ServerActionResult::message,
                    ServerStatusInfo.CODEC,
                    ServerActionResult::serverStatusInfo,
                    ServerActionResult::new
            );
}
