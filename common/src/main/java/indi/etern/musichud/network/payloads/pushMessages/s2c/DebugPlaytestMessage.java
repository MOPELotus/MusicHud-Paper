package indi.etern.musichud.network.payloads.pushMessages.s2c;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.FormatType;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;

public record DebugPlaytestMessage(String identifier, FormatType declaredFormat, boolean stop) implements S2CPayload {
    public static final ByteBufCodec<DebugPlaytestMessage> CODEC = ByteBufCodec.composite(
            Codecs.STRING_UTF8,
            DebugPlaytestMessage::identifier,
            Codecs.STRING_UTF8,
            message -> message.declaredFormat().name(),
            Codecs.BOOL,
            DebugPlaytestMessage::stop,
            (identifier, declaredFormatName, stop) -> new DebugPlaytestMessage(identifier, FormatType.fromSerializedName(declaredFormatName), stop)
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            NetworkReceiver<DebugPlaytestMessage> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = (message, player) -> MusicHud.EXECUTOR.execute(() -> {
                    StreamAudioPlayer streamAudioPlayer = StreamAudioPlayer.getInstance();
                    if (message.stop()) {
                        NowPlayingInfo.getInstance().stop();
                        streamAudioPlayer.stop();
                        return;
                    }
                    NowPlayingInfo.getInstance().stop();
                    streamAudioPlayer.playDirectAsync(message.identifier(), message.declaredFormat(), null)
                            .exceptionally(e -> {
                                MusicHud.getLogger(DebugPlaytestMessage.class).error("Failed to play debug test source: {}", message.identifier(), e);
                                return null;
                            });
                });
            }
            INetworkRegister.getInstance().autoRegisterPayload(
                    DebugPlaytestMessage.class,
                    CODEC,
                    receiver
            );
        }
    }
}

