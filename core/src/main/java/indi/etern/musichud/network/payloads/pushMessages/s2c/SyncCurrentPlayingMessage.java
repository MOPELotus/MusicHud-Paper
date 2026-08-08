package indi.etern.musichud.network.payloads.pushMessages.s2c;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.PlaybackSession;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.IClientMusicService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;

public record SyncCurrentPlayingMessage(PlaybackSession playbackSession, MusicDetail nextIdle) implements S2CPayload {
    public static final ByteBufCodec<SyncCurrentPlayingMessage> CODEC = ByteBufCodec.composite(
            PlaybackSession.CODEC,
            SyncCurrentPlayingMessage::playbackSession,
            MusicDetail.CODEC,
            SyncCurrentPlayingMessage::nextIdle,
            SyncCurrentPlayingMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            NetworkReceiver<SyncCurrentPlayingMessage> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = (message, player) -> MusicHud.EXECUTOR.execute(() -> {
                    IClientMusicService musicService = IClientMusicService.getInstance();
                    musicService.switchMusic(message.playbackSession, message.nextIdle, "");
                });
            }
            INetworkRegister.getInstance().autoRegisterPayload(SyncCurrentPlayingMessage.class, CODEC, receiver);
        }
    }
}
