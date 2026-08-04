package indi.etern.musichud.network.payloads.requestResponseCycle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.RequestHandlerRegistry;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.ResponseResult;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.server.api.tuneweave.TuneWeaveApiClient;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/** Generic bridge for every non-social TuneWeave endpoint. */
@Getter
@AllArgsConstructor
public final class TuneWeaveEndpointRequest extends ApiRequestPayload {
    public static final ByteBufCodec<TuneWeaveEndpointRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.STRING_UTF8, TuneWeaveEndpointRequest::getMethod,
                    Codecs.STRING_UTF8, TuneWeaveEndpointRequest::getPath,
                    Codecs.LARGE_STRING_UTF8, TuneWeaveEndpointRequest::getQueryJson,
                    Codecs.LARGE_STRING_UTF8, TuneWeaveEndpointRequest::getBodyJson,
                    TuneWeaveEndpointRequest::new
            )
    );

    private final String method;
    private final String path;
    private final String queryJson;
    private final String bodyJson;

    public TuneWeaveEndpointRequest(String method, String path, Map<String, String> query, JsonObject body) {
        this(method, path, query == null ? "{}" : new com.google.gson.Gson().toJson(query),
                body == null ? "null" : body.toString());
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(TuneWeaveEndpointRequest.class, CODEC, (request, player) -> {
                Map<String, String> query;
                try {
                    query = new com.google.gson.Gson().fromJson(request.queryJson, Map.class);
                } catch (RuntimeException e) {
                    return ResponseResult.of(new TuneWeaveEndpointResponse(400, "invalid_request", e.getMessage(), "null", "{}"));
                }
                JsonObject body = null;
                if (request.bodyJson != null && !request.bodyJson.isBlank() && !"null".equals(request.bodyJson)) {
                    try {
                        body = JsonParser.parseString(request.bodyJson).getAsJsonObject();
                    } catch (RuntimeException e) {
                        return ResponseResult.of(new TuneWeaveEndpointResponse(400, "invalid_request", e.getMessage(), "null", "{}"));
                    }
                }
                try {
                    TuneWeaveApiClient.TuneWeaveResponse response = TuneWeaveApiClient.request(
                            request.method, request.path, query, body);
                    return ResponseResult.of(new TuneWeaveEndpointResponse(
                            response.statusCode(), "", "", response.data().toString(), response.meta().toString()));
                } catch (TuneWeaveApiClient.TuneWeaveException e) {
                    return ResponseResult.of(new TuneWeaveEndpointResponse(
                            e.getStatusCode(), e.getCode(), e.getMessage(), e.getDetails().toString(), "{}"));
                }
            });
        }
    }
}
