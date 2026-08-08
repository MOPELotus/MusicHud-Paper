package indi.etern.musichud.network.payloads.pushMessages.c2s;

import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.MusicResourceInfo;
import indi.etern.musichud.beans.music.PlaybackResolution;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.api.MusicPlayerServerService;

import java.util.UUID;

public record ResolvePlaybackResultMessage(UUID requestId, int revision, boolean success,
                                           MusicDetail musicDetail,
                                           MusicResourceInfo resourceInfo,
                                           String failureCode) implements C2SPayload {
    public static final ByteBufCodec<ResolvePlaybackResultMessage> CODEC = ByteBufCodec.composite(
            Codecs.UUID, ResolvePlaybackResultMessage::requestId,
            Codecs.INT, ResolvePlaybackResultMessage::revision,
            Codecs.BOOL, ResolvePlaybackResultMessage::success,
            MusicDetail.CODEC, ResolvePlaybackResultMessage::musicDetail,
            MusicResourceInfo.CODEC, ResolvePlaybackResultMessage::resourceInfo,
            Codecs.STRING_UTF8, ResolvePlaybackResultMessage::failureCode,
            ResolvePlaybackResultMessage::new
    );

    public static ResolvePlaybackResultMessage success(UUID requestId, int revision,
                                                       PlaybackResolution resolution) {
        return new ResolvePlaybackResultMessage(requestId, revision, true,
                resolution.musicDetail(), resolution.resourceInfo(), "");
    }

    public static ResolvePlaybackResultMessage failure(UUID requestId, int revision,
                                                       String failureCode) {
        return new ResolvePlaybackResultMessage(requestId, revision, false,
                MusicDetail.NONE, MusicResourceInfo.NONE, failureCode);
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    ResolvePlaybackResultMessage.class, CODEC,
                    (message, player) -> MusicPlayerServerService.getInstance()
                            .acceptPlaybackResolution(player, message));
        }
    }
}
