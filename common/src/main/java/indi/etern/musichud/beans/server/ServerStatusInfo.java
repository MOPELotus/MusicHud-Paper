package indi.etern.musichud.beans.server;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ServerStatusInfo(
        String serverVersion,
        boolean canReloadConfig,
        boolean canManageServerConfig,
        boolean canManagePlayback,
        boolean canQueryPlayerInfo,
        String serverApiBaseUrl,
        boolean serverApiBaseUrlMasked,
        boolean startupBinaryApiServerWhenLaunch,
        String serverApiBinaryExecutablePath,
        double pusherVoteAdditionalRate,
        boolean useRandomCnIp,
        String binaryApiServerStatusI18nKey
) {
    public static final ServerStatusInfo EMPTY = new ServerStatusInfo(
            "",
            false,
            false,
            false,
            false,
            "",
            false,
            false,
            "",
            0.5D,
            true,
            ""
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerStatusInfo> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    ServerStatusInfo::serverVersion,
                    ByteBufCodecs.BOOL,
                    ServerStatusInfo::canReloadConfig,
                    ByteBufCodecs.BOOL,
                    ServerStatusInfo::canManageServerConfig,
                    ByteBufCodecs.BOOL,
                    ServerStatusInfo::canManagePlayback,
                    ByteBufCodecs.BOOL,
                    ServerStatusInfo::canQueryPlayerInfo,
                    ByteBufCodecs.STRING_UTF8,
                    ServerStatusInfo::serverApiBaseUrl,
                    ByteBufCodecs.BOOL,
                    ServerStatusInfo::serverApiBaseUrlMasked,
                    ByteBufCodecs.BOOL,
                    ServerStatusInfo::startupBinaryApiServerWhenLaunch,
                    ByteBufCodecs.STRING_UTF8,
                    ServerStatusInfo::serverApiBinaryExecutablePath,
                    ByteBufCodecs.DOUBLE,
                    ServerStatusInfo::pusherVoteAdditionalRate,
                    ByteBufCodecs.BOOL,
                    ServerStatusInfo::useRandomCnIp,
                    ByteBufCodecs.STRING_UTF8,
                    ServerStatusInfo::binaryApiServerStatusI18nKey,
                    ServerStatusInfo::new
            );
}
