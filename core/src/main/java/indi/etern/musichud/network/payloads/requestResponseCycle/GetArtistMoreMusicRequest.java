package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class GetArtistMoreMusicRequest extends ApiRequestPayload {
    public static final ByteBufCodec<GetArtistMoreMusicRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.LONG, GetArtistMoreMusicRequest::getId,
                    Codecs.INT, GetArtistMoreMusicRequest::getOffset,
                    GetArtistMoreMusicRequest::new
            )
    );
    private final long id;
    private final int offset;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GetArtistMoreMusicRequest.class, CODEC, (request, player) -> {
                List<MusicDetail> tracks = IMusicApiService.getInstance(ApiProvider.TUNEWEAVE)
                        .getArtistMoreMusic(request.getId(), request.getOffset(), player.getUUID());
                return tracks == null ? ResponseResult.ignore()
                        : ResponseResult.of(new GetArtistMoreMusicResponse(request.getId(), request.getOffset(), tracks));
            });
        }
    }
}
