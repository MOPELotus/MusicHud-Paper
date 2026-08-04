package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.services.tuneweave.TuneWeaveClientService;

public class ArtistSubscribeState extends SubscribeState<Artist> {
    private static final MusicService musicService = MusicService.getInstance();
    private static final TuneWeaveClientService tuneWeave = TuneWeaveClientService.getInstance();

    public ArtistSubscribeState(long id) {
        super(id, Artist.class,
                (id1) -> musicService.loadArtist(id1, false),
                () -> musicService.loadUserCollections(false)
                        .thenApply(MusicService.UserCollections::getSubscribedArtists),
                tuneWeave::setArtistSubscribed
        );
    }
}
