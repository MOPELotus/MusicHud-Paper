package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.services.tuneweave.TuneWeaveClientService;

public class AlbumSubscribeState extends SubscribeState<Album> {
    private static final MusicService musicService = MusicService.getInstance();
    private static final TuneWeaveClientService tuneWeave = TuneWeaveClientService.getInstance();

    public AlbumSubscribeState(long id) {
        super(id, Album.class,
                (id1) -> musicService.loadAlbumDetail(id1, false),
                () -> musicService.loadUserCollections(false)
                        .thenApply(MusicService.UserCollections::getSubscribedAlbums),
                tuneWeave::setAlbumSubscribed
        );
    }
}
