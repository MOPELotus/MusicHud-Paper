package indi.mopelotus.musichud.network.payloads.pushMessages.s2c;

import indi.mopelotus.musichud.MusicHud;
import indi.mopelotus.musichud.beans.music.QueueItem;
import indi.mopelotus.musichud.interfaces.CommonRegister;
import indi.mopelotus.musichud.interfaces.IClientMusicService;
import indi.mopelotus.musichud.interfaces.RegisterMark;
import indi.mopelotus.musichud.network.ByteBufCodec;
import indi.mopelotus.musichud.network.Codecs;
import indi.mopelotus.musichud.network.INetworkRegister;
import indi.mopelotus.musichud.network.NetworkReceiver;
import indi.mopelotus.musichud.network.payloads.S2CPayload;
import indi.mopelotus.musichud.platform.Environment;

import java.util.Queue;

public record RefreshMusicQueueMessage(Queue<QueueItem> queue) implements S2CPayload {
    public static final ByteBufCodec<RefreshMusicQueueMessage> CODEC = ByteBufCodec.composite(
            Codecs.ofQueue(() -> QueueItem.CODEC),
            RefreshMusicQueueMessage::queue,
            RefreshMusicQueueMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            NetworkReceiver<RefreshMusicQueueMessage> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = (message, context) -> MusicHud.EXECUTOR.execute(() ->
                        IClientMusicService.getInstance().refreshQueue(message.queue)
                );
            }
            INetworkRegister.getInstance().autoRegisterPayload(
                    RefreshMusicQueueMessage.class, CODEC,
                    receiver
            );
        }
    }
}
