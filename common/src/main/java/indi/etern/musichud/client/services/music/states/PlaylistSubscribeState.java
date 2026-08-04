package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.services.tuneweave.TuneWeaveClientService;

public class PlaylistSubscribeState extends SubscribeState<Playlist> {
    private static final MusicService musicService = MusicService.getInstance();
    private static final TuneWeaveClientService tuneWeave = TuneWeaveClientService.getInstance();

    public PlaylistSubscribeState(long id) {
        super(id, Playlist.class,
                (id1) -> musicService.loadPlaylistDetail(id1, false),
                () -> musicService.loadUserCollections(false)
                        .thenApply((MusicService.UserCollections userCollections) ->
                                userCollections.getUserCategoryPlaylists().getSubscribedPlaylist()
                        ),
                tuneWeave::setPlaylistSubscribed
        );
    }
}
