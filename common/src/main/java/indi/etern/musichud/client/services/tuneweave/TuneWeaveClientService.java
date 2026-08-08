package indi.etern.musichud.client.services.tuneweave;

import com.google.gson.JsonObject;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.LyricInfo;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.MusicResourceInfo;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.beans.music.UserCategoryPlaylists;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;

import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Stable client facade over TuneWeave's authentication, catalog, library, and playback domains. */
public final class TuneWeaveClientService {
    private static final TuneWeaveClientService INSTANCE = new TuneWeaveClientService();
    private final TuneWeaveGateway gateway = new TuneWeaveGateway();
    private final Map<TuneWeavePlatform, TuneWeaveSession> sessionProfiles = new ConcurrentHashMap<>();
    private final TuneWeaveAuthenticationService authentication =
            new TuneWeaveAuthenticationService(gateway, sessionProfiles);
    private final TuneWeaveEntityMapper entities = new TuneWeaveEntityMapper(sessionProfiles::get);
    private final TuneWeaveAccountService account =
            new TuneWeaveAccountService(gateway, authentication, entities);
    private final TuneWeaveCloudService cloud =
            new TuneWeaveCloudService(gateway, authentication, entities);
    private final TuneWeaveProgramService programs = new TuneWeaveProgramService(gateway, entities);
    private final TuneWeaveCatalogService catalog =
            new TuneWeaveCatalogService(gateway, entities, account);
    private final TuneWeavePlaylistService platformPlaylists =
            new TuneWeavePlaylistService(gateway, entities, catalog);
    private final LocalUniPlaylistStore localPlaylists = new LocalUniPlaylistStore();
    private final TuneWeaveUniPlaylistService uniPlaylists =
            new TuneWeaveUniPlaylistService(gateway, entities, localPlaylists);
    private final TuneWeavePlaybackService playback = new TuneWeavePlaybackService(gateway);

    private TuneWeaveClientService() {
    }

    public static TuneWeaveClientService getInstance() {
        return INSTANCE;
    }

    public TuneWeavePlatform defaultPlatform() {
        return gateway.defaultPlatform();
    }

    public void setDefaultPlatform(TuneWeavePlatform platform) {
        gateway.setDefaultPlatform(platform);
    }

    public boolean isAvailable() {
        return gateway.isAvailable();
    }

    public boolean hasCredential(TuneWeavePlatform platform) {
        return gateway.hasCredential(platform);
    }

    public TuneWeaveSession cachedSession(TuneWeavePlatform platform) {
        return authentication.cachedSession(platform);
    }

    public boolean hasPlaylist(long id) {
        return entities.hasPlaylist(id);
    }

    public boolean hasAlbum(long id) {
        return entities.hasAlbum(id);
    }

    public boolean hasArtist(long id) {
        return entities.hasArtist(id);
    }

    public TuneWeaveQrSession startQrLogin(TuneWeavePlatform platform, String loginType) {
        return authentication.startQrLogin(platform, loginType);
    }

    public TuneWeaveQrPoll pollQrLogin(TuneWeaveQrSession session) {
        return authentication.pollQrLogin(session);
    }

    public TuneWeaveSession loginWithPassword(TuneWeavePlatform platform, String principalType,
                                              String principal, String password, String passwordFormat,
                                              String countryCode) {
        return authentication.loginWithPassword(
                platform, principalType, principal, password, passwordFormat, countryCode);
    }

    public TuneWeaveChallengeSession startSmsLogin(TuneWeavePlatform platform, String principal,
                                                   String countryCode) {
        return authentication.startSmsLogin(platform, principal, countryCode);
    }

    public TuneWeaveSession verifySmsLogin(TuneWeaveChallengeSession session, String code) {
        return authentication.verifySmsLogin(session, code);
    }

    public TuneWeaveSession loadSession(TuneWeavePlatform platform) {
        return authentication.loadSession(platform);
    }

    public TuneWeaveSession refreshSession(TuneWeavePlatform platform) {
        return authentication.refreshSession(platform);
    }

    /** Loads the caller's account playlists without routing the private credential through Minecraft. */
    public UserCategoryPlaylists loadAccountPlaylists() {
        return account.loadPlaylists();
    }

    public UserCategoryPlaylists loadAccountPlaylists(TuneWeavePlatform platform) {
        return account.loadPlaylists(platform);
    }

    public LinkedHashSet<Album> loadAccountAlbums() {
        return account.loadAlbums();
    }

    public LinkedHashSet<Album> loadAccountAlbums(TuneWeavePlatform platform) {
        return account.loadAlbums(platform);
    }

    public LinkedHashSet<Artist> loadAccountArtists() {
        return account.loadArtists();
    }

    public LinkedHashSet<Artist> loadAccountArtists(TuneWeavePlatform platform) {
        return account.loadArtists(platform);
    }

    public TuneWeaveCloudLibrary loadCloudLibrary() {
        return cloud.loadLibrary();
    }

    public void deleteCloudTrack(TuneWeaveCloudTrack cloudTrack) {
        cloud.deleteTrack(cloudTrack);
    }

    public String uploadCloudTrack(Path file, String songName, String artist, String album) {
        return cloud.uploadTrack(file, songName, artist, album);
    }

    public String importCloudTrack(String md5, String sourceTrackId, long bitrate, long fileSize,
                                   String fileType, String songName, String artist, String album) {
        return cloud.importTrack(md5, sourceTrackId, bitrate, fileSize,
                fileType, songName, artist, album);
    }

    public boolean matchCloudTrack(TuneWeaveCloudTrack cloudTrack, String targetTrackId) {
        return cloud.matchTrack(cloudTrack, targetTrackId);
    }

    public LyricInfo loadCloudLyrics(TuneWeaveCloudTrack cloudTrack) {
        return cloud.loadLyrics(cloudTrack);
    }

    public LyricInfo loadCloudLyrics(MusicDetail musicDetail) {
        if (musicDetail == null || !musicDetail.isCloudSource()
                || musicDetail.getSourcePartRef().isBlank()) {
            return LyricInfo.NONE;
        }
        return cloud.loadLyrics(musicDetail.getSourcePartRef());
    }

    public MusicDetail loadCloudTrack(MusicDetail musicDetail) {
        if (musicDetail == null || !musicDetail.isCloudSource()
                || musicDetail.getSourcePartRef().isBlank()) {
            return MusicDetail.NONE;
        }
        return cloud.loadTrack(musicDetail.getSourcePartRef()).track();
    }

    public void downloadCloudTrack(TuneWeaveCloudTrack cloudTrack, Path target) {
        cloud.downloadTrack(cloudTrack, target);
    }

    public List<TuneWeavePodcastCategory> loadPodcastCategories(TuneWeavePlatform platform) {
        return programs.loadPodcastCategories(platform);
    }

    public List<TuneWeavePodcast> loadPodcasts(TuneWeavePlatform platform, String categoryId) {
        return programs.loadPodcasts(platform, categoryId);
    }

    public List<TuneWeavePodcast> loadAccountPodcasts(TuneWeavePlatform platform) {
        return programs.loadAccountPodcasts(platform);
    }

    public TuneWeavePodcast loadPodcastDetail(String reference) {
        return programs.loadPodcastDetail(reference);
    }

    public List<TuneWeavePodcastEpisode> loadPodcastEpisodes(TuneWeavePodcast podcast) {
        return programs.loadPodcastEpisodes(podcast);
    }

    public TuneWeavePodcastEpisode loadPodcastEpisodeDetail(String reference) {
        return programs.loadPodcastEpisodeDetail(reference);
    }

    public MusicDetail podcastEpisodeTrack(TuneWeavePodcast podcast,
                                           TuneWeavePodcastEpisode episode) {
        return programs.podcastEpisodeTrack(podcast, episode);
    }

    public MusicDetail loadProgramPlaybackDetail(MusicDetail musicDetail) {
        return programs.loadPlaybackDetail(musicDetail);
    }

    public void setPodcastSubscribed(TuneWeavePodcast podcast, boolean subscribed) {
        programs.setPodcastSubscribed(podcast, subscribed);
    }

    public TuneWeaveRadioTaxonomy loadRadioTaxonomy(TuneWeavePlatform platform) {
        return programs.loadRadioTaxonomy(platform);
    }

    public List<TuneWeaveRadioStation> loadRadioStations(TuneWeavePlatform platform,
                                                         String categoryId, String regionId) {
        return programs.loadRadioStations(platform, categoryId, regionId);
    }

    public List<TuneWeaveRadioStation> loadStyledRadioStations(TuneWeavePlatform platform) {
        return programs.loadStyledRadioStations(platform);
    }

    public List<TuneWeaveRadioStation> loadAccountRadioStations(TuneWeavePlatform platform) {
        return programs.loadAccountRadioStations(platform);
    }

    public TuneWeaveRadioStation loadRadioStationDetail(String reference) {
        return programs.loadRadioStationDetail(reference);
    }

    public List<MusicDetail> loadRadioPlaybackQueue(TuneWeaveRadioStation station) {
        return programs.loadRadioPlaybackQueue(station);
    }

    public void setRadioStationSubscribed(TuneWeaveRadioStation station, boolean subscribed) {
        programs.setRadioStationSubscribed(station, subscribed);
    }

    public Playlist loadPlaylistDetail(long id) {
        return catalog.loadPlaylistDetail(id);
    }

    public Playlist loadPlaylistDetail(String reference) {
        return catalog.loadPlaylistDetail(reference);
    }

    public Album loadAlbumDetail(long id) {
        return catalog.loadAlbumDetail(id);
    }

    public Album loadAlbumDetail(String reference) {
        return catalog.loadAlbumDetail(reference);
    }

    public Artist loadArtistDetail(long id) {
        return catalog.loadArtistDetail(id);
    }

    public Artist loadArtistDetail(String reference) {
        return catalog.loadArtistDetail(reference);
    }

    public List<MusicDetail> loadArtistTracks(long id, int offset) {
        return catalog.loadArtistTracks(id, offset);
    }

    public List<MusicDetail> loadArtistTracks(String reference, int offset) {
        return catalog.loadArtistTracks(reference, offset);
    }

    public MusicDetail loadTrackDetail(MusicDetail musicDetail) {
        return catalog.loadTrackDetail(musicDetail);
    }

    public TuneWeaveVideo loadVideoDetail(MusicDetail musicDetail) {
        return catalog.loadVideoDetail(musicDetail);
    }

    public MusicDetail loadVideoPlaybackDetail(MusicDetail musicDetail) {
        return catalog.loadVideoPlaybackDetail(musicDetail);
    }

    public List<TuneWeaveVideoPart> loadVideoParts(String reference) {
        return catalog.loadVideoParts(reference);
    }

    public MusicDetail videoPartTrack(TuneWeaveVideo video, TuneWeaveVideoPart part) {
        return catalog.videoPartTrack(video, part);
    }

    public LyricInfo loadLyrics(MusicDetail musicDetail) {
        return catalog.loadLyrics(musicDetail);
    }

    public LyricInfo loadVideoLyrics(MusicDetail musicDetail) {
        return catalog.loadVideoLyrics(musicDetail);
    }

    public void setTrackFavorite(MusicDetail musicDetail, boolean favorite) {
        platformPlaylists.setTrackFavorite(musicDetail, favorite);
    }

    public void setPlaylistSubscribed(Playlist playlist, boolean subscribed) {
        platformPlaylists.setPlaylistSubscribed(playlist, subscribed);
    }

    public void setAlbumSubscribed(Album album, boolean subscribed) {
        platformPlaylists.setAlbumSubscribed(album, subscribed);
    }

    public void setArtistSubscribed(Artist artist, boolean subscribed) {
        platformPlaylists.setArtistSubscribed(artist, subscribed);
    }

    public void modifyPlaylistTracks(Playlist playlist, MusicDetail musicDetail, boolean add) {
        platformPlaylists.modifyTracks(playlist, musicDetail, add);
    }

    public Playlist createPlatformPlaylist(String name, boolean privatePlaylist) {
        return platformPlaylists.create(name, privatePlaylist);
    }

    public void updatePlatformPlaylist(Playlist playlist, String name, String description) {
        platformPlaylists.update(playlist, name, description);
    }

    public void deletePlatformPlaylist(Playlist playlist) {
        platformPlaylists.delete(playlist);
    }

    public void reorderPlatformPlaylists(List<Playlist> playlists) {
        platformPlaylists.reorder(playlists);
    }

    public void reorderPlaylistTracks(Playlist playlist, List<MusicDetail> tracks) {
        platformPlaylists.reorderTracks(playlist, tracks);
    }

    public List<TuneWeaveUniPlaylist> listUniPlaylists() {
        return uniPlaylists.list();
    }

    public TuneWeaveUniPlaylist createUniPlaylist(String name, String description) {
        return uniPlaylists.create(name, description);
    }

    public TuneWeaveUniPlaylist updateUniPlaylist(String reference, String name, String description) {
        return uniPlaylists.update(reference, name, description);
    }

    public void deleteUniPlaylist(String reference) {
        uniPlaylists.delete(reference);
    }

    public List<TuneWeaveUniItem> listUniPlaylistItems(String reference) {
        return uniPlaylists.items(reference);
    }

    public MusicDetail uniPlaylistItemTrack(TuneWeaveUniItem item) {
        return uniPlaylists.itemTrack(item);
    }

    public void addUniPlaylistItems(String reference, List<String> resourceRefs) {
        uniPlaylists.addItems(reference, resourceRefs);
    }

    public void addUniPlaylistItem(String reference, String resourceRef, String kind) {
        uniPlaylists.addItem(reference, resourceRef, kind);
    }

    public void deleteUniPlaylistItem(String reference, String itemId) {
        uniPlaylists.deleteItem(reference, itemId);
    }

    public void reorderUniPlaylistItems(String reference, List<String> itemIds) {
        uniPlaylists.reorderItems(reference, itemIds);
    }

    public TuneWeaveUniPlaylist importUniPlaylist(String name, List<String> sourceRefs) {
        return uniPlaylists.importPlaylists(name, sourceRefs);
    }

    public TuneWeaveUniPlaylist importUniPlaylistSources(
            String name, List<TuneWeaveUniImportSource> sources) {
        return uniPlaylists.importSources(name, sources);
    }

    public TuneWeaveUniPlaylist importUniPlaylistSources(
            String name, String description, List<TuneWeaveUniImportSource> sources) {
        return uniPlaylists.importSources(name, description, sources);
    }

    public TuneWeaveUniPlaylist appendUniPlaylistSources(
            String reference, List<TuneWeaveUniImportSource> sources) {
        return uniPlaylists.appendSources(reference, sources);
    }

    public JsonObject exportUniPlaylist(String reference) {
        return uniPlaylists.exportDocument(reference);
    }

    public TuneWeaveUniPlaylist importUniPlaylistDocument(JsonObject document) {
        return uniPlaylists.importDocument(document);
    }

    public MusicResourceInfo getMusicResourceInfo(MusicDetail musicDetail, Quality quality) {
        return playback.resolve(musicDetail, quality);
    }

    public List<?> search(String keywords, SearchType searchType, int offset, TuneWeavePlatform platform) {
        return catalog.search(keywords, searchType, offset, platform);
    }

    public void logout(TuneWeavePlatform platform) {
        authentication.logout(platform);
    }

    public void clearCredential(TuneWeavePlatform platform) {
        authentication.clearCredential(platform);
    }

    public boolean isFavoritePlaylist(Playlist playlist) {
        return account.isFavoritePlaylist(playlist);
    }

    public boolean supportsFavoriteIntelligence(Playlist playlist) {
        return account.supportsFavoriteIntelligence(playlist);
    }

    public List<MusicDetail> loadFavoriteIntelligence(Playlist playlist, String startReference) {
        return account.loadFavoriteIntelligence(playlist, startReference);
    }

}
