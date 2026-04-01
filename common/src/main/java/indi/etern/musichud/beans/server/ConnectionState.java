package indi.etern.musichud.beans.server;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.Codecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public enum ConnectionState {
    NOT_CONNECTED,
    CONNECTED,
    INCAPABLE;

    public static final StreamCodec<RegistryFriendlyByteBuf, ConnectionState> STREAM_CODEC =
            Codecs.ofEnum(ConnectionState.class);

    public String i18nKey() {
        return MusicHud.MOD_ID + ".text.connectionState." + name();
    }
}
