package indi.etern.musichud.network.payloads.pushMessages.s2c;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.PlaybackResolution;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.IClientMusicService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.network.payloads.pushMessages.c2s.ResolvePlaybackResultMessage;
import indi.etern.musichud.platform.Environment;

import java.util.UUID;

/** A narrowly scoped request for resolving the current or next public playback only. */
public record ResolvePlaybackRequestMessage(UUID requestId, int revision,
                                            MusicDetail requestedMusic) implements S2CPayload {
    public static final ByteBufCodec<ResolvePlaybackRequestMessage> CODEC = ByteBufCodec.composite(
            Codecs.UUID, ResolvePlaybackRequestMessage::requestId,
            Codecs.INT, ResolvePlaybackRequestMessage::revision,
            MusicDetail.CODEC, ResolvePlaybackRequestMessage::requestedMusic,
            ResolvePlaybackRequestMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            NetworkReceiver<ResolvePlaybackRequestMessage> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = (message, player) -> MusicHud.EXECUTOR.execute(() -> {
                    ResolvePlaybackResultMessage result;
                    try {
                        PlaybackResolution resolution = IClientMusicService.getInstance()
                                .resolvePublicPlayback(message.requestedMusic());
                        result = ResolvePlaybackResultMessage.success(
                                message.requestId(), message.revision(), resolution);
                    } catch (Exception ignored) {
                        result = ResolvePlaybackResultMessage.failure(
                                message.requestId(), message.revision(), "resolve_failed");
                    }
                    IClientNetworkService.getInstance().sendToServer(result);
                });
            }
            INetworkRegister.getInstance().autoRegisterPayload(
                    ResolvePlaybackRequestMessage.class, CODEC, receiver);
        }
    }
}
