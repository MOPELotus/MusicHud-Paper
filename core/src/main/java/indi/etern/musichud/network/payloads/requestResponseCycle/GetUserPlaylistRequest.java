package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.UserCategoryPlaylists;
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
public class GetUserPlaylistRequest extends ApiRequestPayload {
    public static final ByteBufCodec<GetUserPlaylistRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(Codecs.BOOL, GetUserPlaylistRequest::isIgnoreCache, GetUserPlaylistRequest::new)
    );
    private final boolean ignoreCache;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GetUserPlaylistRequest.class, CODEC, (request, player) -> {
                UserCategoryPlaylists playlists = IMusicApiService.getInstance(ApiProvider.TUNEWEAVE)
                        .getPlayersUserPlaylists(request.isIgnoreCache(), player.getUUID());
                return ResponseResult.of(new GetUserPlaylistResponse(playlists));
            });
        }
    }
}
