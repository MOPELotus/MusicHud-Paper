package indi.etern.musichud.network.payloads.requestResponseCycle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import indi.etern.musichud.server.api.impl.tuneweave.TuneWeaveMusicApiService;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;


/**
 * Game-network control surface for server playback only. Personal TuneWeave
 * operations run directly on the client so caller credentials cannot ever be
 * proxied through a Minecraft server.
 */
public record TuneWeaveControlRequest(String requestId, String action, String payload) implements C2SPayload {
    public static final ByteBufCodec<TuneWeaveControlRequest> CODEC = ByteBufCodec.composite(
            Codecs.STRING_UTF8, TuneWeaveControlRequest::requestId,
            Codecs.STRING_UTF8, TuneWeaveControlRequest::action,
            Codecs.STRING_UTF8, TuneWeaveControlRequest::payload,
            TuneWeaveControlRequest::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(TuneWeaveControlRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((request, player) -> {
                        try {
                            String response = dispatch(request.action, request.payload, PusherInfo.ofPlayer(player));
                            IServerNetworkService.getInstance().sendToPlayer(player,
                                    new TuneWeaveControlResponse(request.requestId, request.action, true, response, ""));
                        } catch (RuntimeException e) {
                            IServerNetworkService.getInstance().sendToPlayer(player,
                                    new TuneWeaveControlResponse(request.requestId, request.action, false, "{}", e.getMessage()));
                        }
                    }));
        }
    }

    private static String dispatch(String action, String payload, PusherInfo pusherInfo) {
        JsonObject body = object(payload);
        return switch (action) {
            case "queue-ref" -> queueReference(body, pusherInfo);
            case "queue-uni-item" -> queueUniItem(body, pusherInfo);
            case "queue-collection" -> queueCollection(body, pusherInfo);
            default -> throw new IllegalArgumentException("Unsupported TuneWeave UI action: " + action);
        };
    }

    private static String queueReference(JsonObject body, PusherInfo pusherInfo) {
        String reference = required(body, "ref");
        String kind = body.has("kind") ? body.get("kind").getAsString() : "track";
        MusicDetail detail = TuneWeaveMusicApiService.getInstance().getOrFetchPlayable(reference, kind);
        MusicPlayerServerService.getInstance().pushMusicToQueue(detail.getId(), pusherInfo);
        return "{}";
    }

    private static String queueUniItem(JsonObject body, PusherInfo pusherInfo) {
        MusicDetail detail = TuneWeaveMusicApiService.getInstance().getOrFetchUniPlayable(
                required(body, "ref"), required(body, "item_id"));
        MusicPlayerServerService.getInstance().pushMusicToQueue(detail.getId(), pusherInfo);
        return "{}";
    }

    private static String queueCollection(JsonObject body, PusherInfo pusherInfo) {
        int count = 0;
        for (MusicDetail detail : TuneWeaveMusicApiService.getInstance().getCollectionTracks(
                required(body, "ref"), required(body, "collection"))) {
            MusicPlayerServerService.getInstance().pushMusicToQueue(detail.getId(), pusherInfo);
            count++;
        }
        JsonObject result = new JsonObject();
        result.addProperty("queued", count);
        return result.toString();
    }

    private static String required(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull() || object.get(key).getAsString().isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return object.get(key).getAsString();
    }

    private static JsonObject object(String value) {
        try {
            JsonElement parsed = JsonParser.parseString(value == null || value.isBlank() ? "{}" : value);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid TuneWeave UI payload");
        }
    }
}
