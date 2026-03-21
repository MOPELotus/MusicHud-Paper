package indi.etern.musichud.client.ui.pages;

import indi.etern.musichud.beans.music.AlbumInfo;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;

import java.util.List;

public final class SearchView {
    private static final SearchView INSTANCE = new SearchView();

    private SearchView() {
    }

    public static SearchView getInstance() {
        return INSTANCE;
    }

    public void setSearchMusicResult(int offset, List<MusicDetail> result) {
    }

    public void setSearchAlbumResult(int offset, List<AlbumInfo> result) {
    }

    public void setSearchArtistResult(int offset, List<Artist> result) {
    }

    public void setSearchPlaylistResult(int offset, List<Playlist> result) {
    }
}
