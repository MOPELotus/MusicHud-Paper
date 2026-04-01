package indi.etern.musichud.beans.server;

import indi.etern.musichud.beans.login.LoginType;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.VipType;
import indi.etern.musichud.network.Codecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record PlayerStatusInfo(
        String playerName,
        UUID playerUuid,
        ConnectionState connectionState,
        String modVersion,
        LoginType loginType,
        Profile profile,
        VipType vipType
) {
    public static final PlayerStatusInfo EMPTY = new PlayerStatusInfo(
            "",
            new UUID(0L, 0L),
            ConnectionState.NOT_CONNECTED,
            "",
            LoginType.UNLOGGED,
            Profile.ANONYMOUS,
            VipType.NORMAL
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerStatusInfo> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    PlayerStatusInfo::playerName,
                    Codecs.UUID,
                    PlayerStatusInfo::playerUuid,
                    ConnectionState.STREAM_CODEC,
                    PlayerStatusInfo::connectionState,
                    ByteBufCodecs.STRING_UTF8,
                    PlayerStatusInfo::modVersion,
                    LoginType.PACKET_CODEC,
                    PlayerStatusInfo::loginType,
                    Profile.STREAM_CODEC,
                    PlayerStatusInfo::profile,
                    VipType.STREAM_CODEC,
                    PlayerStatusInfo::vipType,
                    PlayerStatusInfo::new
            );

    public boolean hasLoggedAccount() {
        return loginType != LoginType.UNLOGGED && loginType != LoginType.ANONYMOUS && profile != null && !Profile.ANONYMOUS.equals(profile);
    }
}
