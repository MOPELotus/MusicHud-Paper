package indi.etern.musichud.network.payloads.pushMessages.c2s;

import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;

public record ClientPushMusicToQueueMessage(MusicDetail musicDetail) implements C2SPayload {
    public static final ByteBufCodec<ClientPushMusicToQueueMessage> CODEC = ByteBufCodec.composite(
            MusicDetail.CODEC,
            ClientPushMusicToQueueMessage::musicDetail,
            ClientPushMusicToQueueMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    ClientPushMusicToQueueMessage.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((message, player) -> {
                        MusicPlayerServerService.getInstance().pushMusicToQueue(
                                message.musicDetail, PusherInfo.ofPlayer(player));
                    })
            );
        }
    }
}
