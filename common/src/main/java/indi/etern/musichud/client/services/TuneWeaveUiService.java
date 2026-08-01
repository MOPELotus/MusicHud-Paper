package indi.etern.musichud.client.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.payloads.requestResponseCycle.TuneWeaveControlRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.TuneWeaveControlResponse;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Serializes UI requests and keeps the packet layer free of client UI classes. */
public final class TuneWeaveUiService {
    private static final Map<String, Consumer<TuneWeaveControlResponse>> PENDING = new ConcurrentHashMap<>();

    static {
        TuneWeaveControlResponse.setReceiver(response -> {
            Consumer<TuneWeaveControlResponse> callback = PENDING.remove(response.requestId());
            if (callback != null) {
                MuiModApi.postToUiThread(() -> callback.accept(response));
            }
        });
    }

    private TuneWeaveUiService() {
    }

    public static void request(String action, JsonObject payload, Consumer<JsonElement> success, Consumer<String> failure) {
        String requestId = UUID.randomUUID().toString();
        PENDING.put(requestId, response -> {
            if (response.ok()) {
                try {
                    success.accept(JsonParser.parseString(response.payload()));
                } catch (Exception e) {
                    failure.accept("TuneWeave 返回了无效数据");
                }
            } else {
                failure.accept(response.message().isBlank() ? "TuneWeave 请求失败" : response.message());
            }
        });
        IClientNetworkService.getInstance().sendToServer(new TuneWeaveControlRequest(requestId, action, payload.toString()));
        MusicHud.EXECUTOR.execute(() -> {
            try {
                Thread.sleep(25_000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            Consumer<TuneWeaveControlResponse> callback = PENDING.remove(requestId);
            if (callback != null) {
                MuiModApi.postToUiThread(() -> failure.accept("TuneWeave 请求超时"));
            }
        });
    }
}
