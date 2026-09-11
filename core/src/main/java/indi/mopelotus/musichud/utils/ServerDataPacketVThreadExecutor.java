package indi.mopelotus.musichud.utils;

import indi.mopelotus.musichud.MusicHud;
import indi.mopelotus.musichud.network.IPlayerClient;
import indi.mopelotus.musichud.network.NetworkReceiver;
import indi.mopelotus.musichud.network.payloads.IPayload;
import indi.mopelotus.musichud.throwable.ApiException;

import java.util.function.BiConsumer;

public class ServerDataPacketVThreadExecutor {
    public static <T extends IPayload> NetworkReceiver<T> execute(
            BiConsumer<T, IPlayerClient> consumer
    ) {
        return (payload, player) -> {
            var admission = indi.mopelotus.musichud.network.ServerPacketAdmission.capture(payload, player);
            if (!admission.allowed()) return;
            MusicHud.EXECUTOR.execute(() -> {
                if (!admission.allowed()) return;
                Thread.currentThread().setName("MHWorker-Network-V");
//                if (player instanceof Player player) {
                    try {
                        consumer.accept(payload, player);
                    } catch (ApiException e) {
                        MusicHud.getLogger(payload.getClass()).error(e);
                    } catch (Exception e) {
                        MusicHud.getLogger(payload.getClass()).error(e);
                        //noinspection CallToPrintStackTrace
                        e.printStackTrace();
                    }
//                } else {
//                    throw new IllegalStateException("Player must be a server player");
//                }
            });
        };
    }
}
