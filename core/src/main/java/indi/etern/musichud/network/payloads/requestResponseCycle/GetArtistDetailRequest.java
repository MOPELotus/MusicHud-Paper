package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GetArtistDetailRequest extends ApiRequestPayload {
    public static final ByteBufCodec<GetArtistDetailRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(Codecs.LONG, GetArtistDetailRequest::getId, GetArtistDetailRequest::new)
    );
    private final long id;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GetArtistDetailRequest.class, CODEC, (request, player) -> {
                Artist artist = IMusicApiService.getInstance(ApiProvider.TUNEWEAVE).getArtistDetail(request.getId(), player.getUUID());
                return artist == null ? ResponseResult.ignore() : ResponseResult.of(new GetArtistDetailResponse(artist));
            });
        }
    }
}
