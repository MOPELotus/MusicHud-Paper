package indi.etern.musichud.network.payloads.pushMessages.c2s;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.api.MusicPlayerServerService;

import java.util.UUID;

public record PlaybackResourceFailureMessage(UUID sessionId, int revision) implements C2SPayload {
    public static final ByteBufCodec<PlaybackResourceFailureMessage> CODEC = ByteBufCodec.composite(
            Codecs.UUID, PlaybackResourceFailureMessage::sessionId,
            Codecs.INT, PlaybackResourceFailureMessage::revision,
            PlaybackResourceFailureMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    PlaybackResourceFailureMessage.class, CODEC,
                    (message, player) -> MusicPlayerServerService.getInstance()
                            .reportPlaybackResourceFailure(player, message));
        }
    }
}
