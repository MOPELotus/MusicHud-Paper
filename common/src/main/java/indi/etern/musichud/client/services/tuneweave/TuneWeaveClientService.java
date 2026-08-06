package indi.etern.musichud.client.services.tuneweave;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.Fee;
import indi.etern.musichud.beans.music.FormatType;
import indi.etern.musichud.beans.music.Lyric;
import indi.etern.musichud.beans.music.LyricInfo;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.MusicResourceInfo;
import indi.etern.musichud.beans.music.UserCategoryPlaylists;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.server.api.tuneweave.TuneWeaveApiClient;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
import net.minecraft.client.resources.language.I18n;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.*;

/** Client-owned TuneWeave authentication and credential-scoped requests. */
public final class TuneWeaveClientService {
    private static final TuneWeaveClientService INSTANCE = new TuneWeaveClientService();
    private final ClientConfig config = ClientConfig.getInstance();
    private final TuneWeaveGateway gateway = new TuneWeaveGateway();
    private final Map<TuneWeavePlatform, TuneWeaveSession> sessionProfiles = new ConcurrentHashMap<>();
    private final TuneWeaveAuthenticationService authentication =
            new TuneWeaveAuthenticationService(gateway, sessionProfiles);
    private final TuneWeaveEntityMapper entities = new TuneWeaveEntityMapper(sessionProfiles::get);
    private final TuneWeaveAccountService account =
            new TuneWeaveAccountService(gateway, authentication, entities);
    private final TuneWeaveCloudService cloud =
            new TuneWeaveCloudService(gateway, authentication, entities);
    private final LocalUniPlaylistStore localPlaylists = new LocalUniPlaylistStore();

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

    public String credential(TuneWeavePlatform platform) {
        return gateway.credential(platform);
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

    public java.util.LinkedHashSet<Album> loadAccountAlbums() {
        return account.loadAlbums();
    }

    public java.util.LinkedHashSet<Album> loadAccountAlbums(TuneWeavePlatform platform) {
        return account.loadAlbums(platform);
    }

    public java.util.LinkedHashSet<Artist> loadAccountArtists() {
        return account.loadArtists();
    }

    public java.util.LinkedHashSet<Artist> loadAccountArtists(TuneWeavePlatform platform) {
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

    public void downloadCloudTrack(TuneWeaveCloudTrack cloudTrack, Path target) {
        cloud.downloadTrack(cloudTrack, target);
    }

    public List<PodcastCategoryInfo> loadPodcastCategories(TuneWeavePlatform platform) {
        JsonObject data = object(requestForPlatform(platform, "GET", "/v1/podcasts/categories",
                Map.of("platform", platform.apiName(), "kind", "all"), null).data());
        List<PodcastCategoryInfo> result = new ArrayList<>();
        for (JsonElement value : elements(data.get("categories"))) {
            JsonObject category = unwrap(value);
            String id = string(category, "id", "");
            if (!id.isBlank()) result.add(new PodcastCategoryInfo(id, string(category, "name", id),
                    string(category, "icon_url", "")));
        }
        return result;
    }

    public List<PodcastInfo> loadPodcasts(TuneWeavePlatform platform, String categoryId) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("platform", platform.apiName());
        query.put("catalog", categoryId == null || categoryId.isBlank() ? "featured" : "category_featured");
        if (categoryId != null && !categoryId.isBlank()) query.put("category_id", categoryId.trim());
        return loadPodcastPages(platform, "/v1/podcasts", query);
    }

    public List<PodcastInfo> loadAccountPodcasts(TuneWeavePlatform platform) {
        return loadPodcastPages(platform, "/v1/account/library/podcasts",
                new LinkedHashMap<>(Map.of("platform", platform.apiName())));
    }

    private List<PodcastInfo> loadPodcastPages(TuneWeavePlatform platform, String path,
                                               Map<String, String> baseQuery) {
        List<PodcastInfo> result = new ArrayList<>();
        int offset = 0;
        while (true) {
            Map<String, String> query = new LinkedHashMap<>(baseQuery);
            query.put("limit", "100");
            query.put("offset", Integer.toString(offset));
            TuneWeaveApiClient.TuneWeaveResponse response = requestForPlatform(
                    platform, "GET", path, query, null);
            List<JsonElement> page = elements(response.data());
            for (JsonElement value : page) {
                PodcastInfo podcast = entities.toPodcast(platform, unwrap(value));
                if (!podcast.reference().isBlank()) result.add(podcast);
            }
            JsonObject pagination = object(response.meta().get("pagination"));
            boolean hasMore = bool(pagination, "has_more", page.size() == 100);
            JsonElement nextOffsetValue = pagination.get("next_offset");
            if (!hasMore || page.isEmpty() || nextOffsetValue == null || nextOffsetValue.isJsonNull()) break;
            int nextOffset = nextOffsetValue.getAsInt();
            if (nextOffset <= offset) break;
            offset = nextOffset;
        }
        return result;
    }

    public PodcastInfo loadPodcastDetail(String reference) {
        requireReference(reference, "podcast");
        TuneWeavePlatform platform = platformFromReference(reference);
        return entities.toPodcast(platform, unwrap(requestForPlatform(platform, "GET", "/v1/podcasts/"
                + TuneWeaveApiClient.encodePathSegment(reference), Map.of(), null).data()));
    }

    public List<PodcastEpisodeInfo> loadPodcastEpisodes(PodcastInfo podcast) {
        requireReference(podcast == null ? null : podcast.reference(), "podcast");
        TuneWeavePlatform platform = platformFromReference(podcast.reference());
        List<PodcastEpisodeInfo> result = new ArrayList<>();
        int offset = 0;
        while (true) {
            TuneWeaveApiClient.TuneWeaveResponse response = requestForPlatform(platform, "GET",
                    "/v1/podcasts/" + TuneWeaveApiClient.encodePathSegment(podcast.reference()) + "/episodes",
                    Map.of("limit", "100", "offset", Integer.toString(offset), "ascending", "false"), null);
            List<JsonElement> page = elements(response.data());
            for (JsonElement value : page) {
                PodcastEpisodeInfo episode = entities.toPodcastEpisode(platform, unwrap(value));
                if (!episode.reference().isBlank()) result.add(episode);
            }
            JsonObject pagination = object(response.meta().get("pagination"));
            boolean hasMore = bool(pagination, "has_more", page.size() == 100);
            int nextOffset = integer(pagination, "next_offset", offset + page.size());
            if (!hasMore || page.isEmpty() || nextOffset <= offset) break;
            offset = nextOffset;
        }
        return result;
    }

    public PodcastEpisodeInfo loadPodcastEpisodeDetail(String reference) {
        requireReference(reference, "podcast episode");
        TuneWeavePlatform platform = platformFromReference(reference);
        return entities.toPodcastEpisode(platform, unwrap(requestForPlatform(platform, "GET", "/v1/episodes/"
                + TuneWeaveApiClient.encodePathSegment(reference), Map.of(), null).data()));
    }

    public MusicDetail podcastEpisodeTrack(PodcastInfo podcast, PodcastEpisodeInfo episode) {
        TuneWeavePlatform platform = platformFromReference(episode.reference());
        String creatorName = !episode.creatorName().isBlank() ? episode.creatorName() : podcast.creatorName();
        List<Artist> creators = creatorName.isBlank() ? List.of() : List.of(new Artist(
                entities.stableId(platform, "podcast-creator:" + creatorName), creatorName,
                "", 0, 0, "", new ArrayList<>(), 0, ""));
        String cover = episode.coverUrl().isBlank() ? podcast.coverUrl() : episode.coverUrl();
        Album album = new Album(entities.stableId(platform, "podcast:" + podcast.reference()), podcast.name(),
                cover.isBlank() ? MusicHud.ICON_BASE64 : cover, "Podcast", "", 0,
                new ObservableSequencedSet<>(), new java.util.LinkedHashSet<>(creators),
                indi.etern.musichud.beans.music.PusherInfo.EMPTY, "");
        MusicDetail track = MusicDetail.fromTuneWeave(
                entities.stableId(platform, "podcast-episode:" + episode.reference()), episode.reference(), "podcast_episode",
                episode.name(), episode.durationMillis(), album, creators);
        track.setPusherInfo(indi.etern.musichud.beans.music.PusherInfo.EMPTY);
        entities.cacheTrack(track);
        return track;
    }

    public void setPodcastSubscribed(PodcastInfo podcast, boolean subscribed) {
        requireReference(podcast == null ? null : podcast.reference(), "podcast");
        TuneWeavePlatform platform = platformFromReference(podcast.reference());
        requestForPlatform(platform, subscribed ? "PUT" : "DELETE", "/v1/account/library/podcasts/"
                + TuneWeaveApiClient.encodePathSegment(podcast.reference()), Map.of(), null);
    }

    public RadioTaxonomyInfo loadRadioTaxonomy(TuneWeavePlatform platform) {
        JsonObject data = object(requestForPlatform(platform, "GET", "/v1/radio/taxonomy",
                Map.of("platform", platform.apiName()), null).data());
        return new RadioTaxonomyInfo(entities.toRadioOptions(data.get("categories")),
                entities.toRadioOptions(data.get("regions")));
    }

    public List<RadioStationInfo> loadRadioStations(TuneWeavePlatform platform,
                                                    String categoryId, String regionId) {
        Map<String, String> baseQuery = new LinkedHashMap<>();
        baseQuery.put("platform", platform.apiName());
        if (categoryId != null && !categoryId.isBlank()) baseQuery.put("category_id", categoryId.trim());
        if (regionId != null && !regionId.isBlank()) baseQuery.put("region_id", regionId.trim());
        return loadRadioStationCatalog(platform, baseQuery);
    }

    private List<RadioStationInfo> loadRadioStationCatalog(TuneWeavePlatform platform,
                                                           Map<String, String> baseQuery) {
        LinkedHashMap<String, RadioStationInfo> result = new LinkedHashMap<>();
        Map<String, String> cursor = Map.of();
        String previousCursor = "";
        while (true) {
            Map<String, String> query = new LinkedHashMap<>(baseQuery);
            query.put("limit", "100");
            query.putAll(cursor);
            TuneWeaveApiClient.TuneWeaveResponse response = requestForPlatform(
                    platform, "GET", "/v1/radio/stations", query, null);
            List<JsonElement> page = elements(response.data());
            for (JsonElement value : page) {
                RadioStationInfo station = entities.toRadioStation(platform, unwrap(value), "");
                if (!station.reference().isBlank()) result.putIfAbsent(station.reference(), station);
            }
            JsonObject pagination = object(response.meta().get("pagination"));
            if (!bool(pagination, "has_more", false) || page.isEmpty()) break;
            JsonObject extensions = unwrap(pagination.get("extensions"));
            JsonObject nextCursor = unwrap(extensions.get("next_cursor"));
            String lastId = string(nextCursor, "id", "");
            String score = string(nextCursor, "score", "");
            String cursorKey = lastId + ':' + score;
            if (lastId.isBlank() || score.isBlank() || cursorKey.equals(previousCursor)) break;
            previousCursor = cursorKey;
            cursor = Map.of("last_id", lastId, "score", score);
        }
        return List.copyOf(result.values());
    }

    public List<RadioStationInfo> loadStyledRadioStations(TuneWeavePlatform platform) {
        JsonObject data = object(requestForPlatform(platform, "GET", "/v1/radio/styles",
                Map.of("platform", platform.apiName(), "sources", "0,1,2"), null).data());
        LinkedHashMap<String, RadioStationInfo> result = new LinkedHashMap<>();
        for (JsonElement sourceValue : elements(data.get("sources"))) {
            JsonObject source = unwrap(sourceValue);
            for (JsonElement styleValue : elements(source.get("styles"))) {
                JsonObject style = unwrap(styleValue);
                String styleName = string(style, "localized_name", string(style, "name", ""));
                for (JsonElement channelValue : elements(style.get("channels"))) {
                    RadioStationInfo station = entities.toRadioStation(platform, unwrap(channelValue), styleName);
                    if (!station.reference().isBlank()) result.putIfAbsent(station.reference(), station);
                }
            }
        }
        return List.copyOf(result.values());
    }

    public List<RadioStationInfo> loadAccountRadioStations(TuneWeavePlatform platform) {
        LinkedHashMap<String, RadioStationInfo> result = new LinkedHashMap<>();
        for (String catalog : List.of("broadcast", "styled")) {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("platform", platform.apiName());
            query.put("catalog", catalog);
            if ("styled".equals(catalog)) query.put("sources", "0,1,2");
            for (RadioStationInfo station : loadRadioStationPages(
                    platform, "/v1/account/library/radio-stations", query)) {
                result.putIfAbsent(station.reference(), station);
            }
        }
        return List.copyOf(result.values());
    }

    private List<RadioStationInfo> loadRadioStationPages(TuneWeavePlatform platform, String path,
                                                         Map<String, String> baseQuery) {
        List<RadioStationInfo> result = new ArrayList<>();
        int offset = 0;
        while (true) {
            Map<String, String> query = new LinkedHashMap<>(baseQuery);
            query.put("limit", "100");
            query.put("offset", Integer.toString(offset));
            TuneWeaveApiClient.TuneWeaveResponse response = requestForPlatform(
                    platform, "GET", path, query, null);
            List<JsonElement> page = elements(response.data());
            for (JsonElement value : page) {
                RadioStationInfo station = entities.toRadioStation(platform, unwrap(value), "");
                if (!station.reference().isBlank()) result.add(station);
            }
            JsonObject pagination = object(response.meta().get("pagination"));
            boolean hasMore = bool(pagination, "has_more", page.size() == 100);
            JsonElement nextOffsetValue = pagination.get("next_offset");
            if (!hasMore || page.isEmpty() || nextOffsetValue == null || nextOffsetValue.isJsonNull()) break;
            int nextOffset = nextOffsetValue.getAsInt();
            if (nextOffset <= offset) break;
            offset = nextOffset;
        }
        return result;
    }

    public RadioStationInfo loadRadioStationDetail(String reference) {
        requireReference(reference, "radio station");
        TuneWeavePlatform platform = platformFromReference(reference);
        return entities.toRadioStation(platform, unwrap(requestForPlatform(platform, "GET", "/v1/radio/stations/"
                + TuneWeaveApiClient.encodePathSegment(reference), Map.of(), null).data()), "");
    }

    public List<MusicDetail> loadRadioPlaybackQueue(RadioStationInfo station) {
        requireReference(station == null ? null : station.reference(), "radio station");
        TuneWeavePlatform platform = platformFromReference(station.reference());
        if (!isStyledRadioReference(station.reference())) {
            String title = station.currentProgram().isBlank() ? station.name() : station.currentProgram();
            Album album = new Album(entities.stableId(platform, "radio-station:" + station.reference()), station.name(),
                    station.coverUrl().isBlank() ? MusicHud.ICON_BASE64 : station.coverUrl(), "Radio", "", 0,
                    new ObservableSequencedSet<>(), new java.util.LinkedHashSet<>(),
                    indi.etern.musichud.beans.music.PusherInfo.EMPTY, "");
            MusicDetail track = MusicDetail.fromTuneWeave(entities.stableId(platform,
                    "radio-live:" + station.reference()), station.reference(), "radio_station", title,
                    24 * 60 * 60 * 1000, album, List.of());
            track.setPusherInfo(indi.etern.musichud.beans.music.PusherInfo.EMPTY);
            entities.cacheTrack(track);
            return List.of(track);
        }
        JsonObject data = object(requestForPlatform(platform, "GET", "/v1/radio/stations/"
                + TuneWeaveApiClient.encodePathSegment(station.reference()) + "/tracks",
                Map.of("limit", "100"), null).data());
        List<MusicDetail> result = new ArrayList<>();
        for (JsonElement value : elements(data.get("items"))) {
            JsonObject item = unwrap(value);
            String itemReference = string(item, "ref", "");
            String title = string(item, "title", station.name());
            String artistName = string(item, "artist", "");
            List<Artist> artists = artistName.isBlank() ? List.of() : List.of(new Artist(
                    entities.stableId(platform, "radio-artist:" + artistName), artistName,
                    "", 0, 0, "", new ArrayList<>(), 0, ""));
            String cover = string(item, "cover_url", station.coverUrl());
            Album album = new Album(entities.stableId(platform, "radio-station:" + station.reference()), station.name(),
                    cover.isBlank() ? MusicHud.ICON_BASE64 : cover, "Radio", "", 0,
                    new ObservableSequencedSet<>(), new java.util.LinkedHashSet<>(artists),
                    indi.etern.musichud.beans.music.PusherInfo.EMPTY, "");
            MusicDetail track = MusicDetail.fromTuneWeave(entities.stableId(platform,
                    "radio-item:" + (itemReference.isBlank() ? station.reference() + ':' + title : itemReference)),
                    station.reference(), "radio_station", title, integer(item, "duration_ms", 0), album, artists);
            track.setSourcePartRef(itemReference);
            track.setPusherInfo(indi.etern.musichud.beans.music.PusherInfo.EMPTY);
            entities.cacheTrack(track);
            result.add(track);
        }
        return result;
    }

    public void setRadioStationSubscribed(RadioStationInfo station, boolean subscribed) {
        requireReference(station == null ? null : station.reference(), "radio station");
        TuneWeavePlatform platform = platformFromReference(station.reference());
        requestForPlatform(platform, subscribed ? "PUT" : "DELETE", "/v1/account/library/radio-stations/"
                + TuneWeaveApiClient.encodePathSegment(station.reference()), Map.of(), null);
    }

    public Playlist loadPlaylistDetail(long id) {
        Playlist cached = entities.playlist(id);
        if (cached == null || cached.getSourceRef().isBlank()) {
            return Playlist.EMPTY;
        }
        return loadPlaylistDetail(cached.getSourceRef());
    }

    public Playlist loadPlaylistDetail(String reference) {
        if (reference == null || reference.isBlank()) {
            return Playlist.EMPTY;
        }
        TuneWeavePlatform platform = platformFromReference(reference);
        if (platform != TuneWeavePlatform.BILIBILI
                && account.isFavoritePlaylistReference(reference)) {
            Playlist playlist = entities.playlists().stream()
                    .filter(value -> reference.equals(value.getSourceRef()))
                    .findFirst()
                    .orElse(Playlist.EMPTY);
            List<MusicDetail> tracks = new ArrayList<>();
            for (JsonElement item : loadAllOffsetPages(platform, "/v1/account/favorites/tracks",
                    Map.of("platform", platform.apiName()))) {
                MusicDetail track = entities.toTrack(platform, unwrap(item));
                if (track != MusicDetail.NONE) tracks.add(track);
            }
            ObservableSequencedSet<MusicDetail> likedTracks = new ObservableSequencedSet<>();
            likedTracks.addAll(tracks);
            playlist.setTracks(likedTracks);
            playlist.setMusicTrackCount(tracks.size());
            entities.cachePlaylist(playlist);
            return playlist;
        }
        Playlist playlist = entities.toPlaylist(platform, unwrap(requestForPlatform(
                platform, "GET", "/v1/playlists/"
                        + TuneWeaveApiClient.encodePathSegment(reference), Map.of(), null).data()));
        List<MusicDetail> tracks = new ArrayList<>();
        List<JsonElement> playlistItems = loadAllOffsetPages(platform,
                "/v1/playlists/" + TuneWeaveApiClient.encodePathSegment(reference) + "/items", Map.of());
        for (JsonElement item : playlistItems) {
            JsonObject raw = unwrap(item);
            MusicDetail track = "video".equals(string(raw, "kind", "track"))
                    ? entities.toVideoTrack(platformFromReference(reference), raw)
                    : entities.toTrack(platformFromReference(reference), raw);
            if (track != MusicDetail.NONE) tracks.add(track);
        }
        ObservableSequencedSet<MusicDetail> playlistTracks = new ObservableSequencedSet<>();
        playlistTracks.addAll(tracks);
        playlist.setTracks(playlistTracks);
        playlist.setMusicTrackCount(tracks.size());
        entities.cachePlaylist(playlist);
        return playlist;
    }

    public Album loadAlbumDetail(long id) {
        Album cached = entities.album(id);
        return cached == null ? Album.NONE : loadAlbumDetail(cached.getSourceRef());
    }

    public Album loadAlbumDetail(String reference) {
        if (reference == null || reference.isBlank()) return Album.NONE;
        TuneWeavePlatform platform = platformFromReference(reference);
        Album album = entities.toAlbum(platform, unwrap(requestForPlatform(platform, "GET", "/v1/albums/"
                + TuneWeaveApiClient.encodePathSegment(reference), Map.of(), null).data()));
        List<MusicDetail> tracks = new ArrayList<>();
        for (JsonElement item : loadAllOffsetPages(platform, "/v1/albums/"
                + TuneWeaveApiClient.encodePathSegment(reference) + "/tracks", Map.of())) {
            MusicDetail track = entities.toTrack(platform, unwrap(item));
            if (track != MusicDetail.NONE) tracks.add(track);
        }
        ObservableSequencedSet<MusicDetail> albumTracks = new ObservableSequencedSet<>();
        albumTracks.addAll(tracks);
        album.setMusicDetails(albumTracks);
        return album;
    }

    public Artist loadArtistDetail(long id) {
        Artist cached = entities.artist(id);
        return cached == null ? new Artist() : loadArtistDetail(cached.getSourceRef());
    }

    public Artist loadArtistDetail(String reference) {
        if (reference == null || reference.isBlank()) return new Artist();
        TuneWeavePlatform platform = platformFromReference(reference);
        return entities.toArtist(platform, unwrap(requestForPlatform(platform, "GET", "/v1/artists/"
                + TuneWeaveApiClient.encodePathSegment(reference), Map.of(), null).data()));
    }

    public List<MusicDetail> loadArtistTracks(long id, int offset) {
        Artist cached = entities.artist(id);
        if (cached == null) return List.of();
        return loadArtistTracks(cached.getSourceRef(), offset);
    }

    public List<MusicDetail> loadArtistTracks(String reference, int offset) {
        if (reference == null || reference.isBlank()) return List.of();
        TuneWeavePlatform platform = platformFromReference(reference);
        List<MusicDetail> result = new ArrayList<>();
        for (JsonElement item : elements(requestForPlatform(platform, "GET", "/v1/artists/"
                + TuneWeaveApiClient.encodePathSegment(reference) + "/tracks",
                Map.of("limit", "50", "offset", Integer.toString(Math.max(0, offset))), null).data())) {
            MusicDetail track = entities.toTrack(platform, unwrap(item));
            if (track != MusicDetail.NONE) result.add(track);
        }
        return result;
    }

    private List<JsonElement> loadAllOffsetPages(TuneWeavePlatform platform, String path,
                                                  Map<String, String> baseQuery) {
        List<JsonElement> result = new ArrayList<>();
        int offset = 0;
        while (true) {
            Map<String, String> query = new LinkedHashMap<>(baseQuery);
            query.put("limit", "100");
            query.put("offset", Integer.toString(offset));
            TuneWeaveApiClient.TuneWeaveResponse response = requestForPlatform(
                    platform, "GET", path, query, null);
            List<JsonElement> page = elements(response.data());
            result.addAll(page);

            JsonObject pagination = object(response.meta().get("pagination"));
            if (!bool(pagination, "has_more", false) || page.isEmpty()) break;
            int nextOffset = integer(pagination, "next_offset", offset + page.size());
            if (nextOffset <= offset) {
                throw new TuneWeaveApiClient.TuneWeaveException(
                        "TuneWeave returned an invalid pagination offset for " + path, false);
            }
            offset = nextOffset;
        }
        return result;
    }

    public MusicDetail loadTrackDetail(MusicDetail musicDetail) {
        if (musicDetail == null || musicDetail.getSourceRef().isBlank()
                || "video".equals(musicDetail.getSourceKind())) {
            return musicDetail == null ? MusicDetail.NONE : musicDetail;
        }
        TuneWeavePlatform platform = platformFromReference(musicDetail.getSourceRef());
        MusicDetail detail = entities.toTrack(platform, unwrap(requestForPlatform(platform, "GET", "/v1/tracks/"
                + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef()), Map.of(), null).data()));
        if (detail != MusicDetail.NONE) {
            entities.cacheTrack(detail);
        }
        return detail;
    }

    public VideoInfo loadVideoDetail(MusicDetail musicDetail) {
        requireReference(musicDetail == null ? null : musicDetail.getSourceRef(), "video");
        TuneWeavePlatform platform = platformFromReference(musicDetail.getSourceRef());
        JsonObject detail = object(requestForPlatform(platform, "GET", "/v1/videos/"
                + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef()),
                Map.of("type", "video"), null).data());
        JsonObject video = detail.has("video") && detail.get("video").isJsonObject()
                ? detail.getAsJsonObject("video") : detail;
        return entities.toVideoInfo(video);
    }

    public List<VideoPartInfo> loadVideoParts(String reference) {
        requireReference(reference, "video");
        TuneWeavePlatform platform = platformFromReference(reference);
        List<VideoPartInfo> result = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<JsonElement> page = elements(requestForPlatform(platform, "GET", "/v1/videos/"
                    + TuneWeaveApiClient.encodePathSegment(reference) + "/parts",
                    Map.of("type", "video", "limit", "100", "offset", Integer.toString(offset)), null).data());
            for (JsonElement item : page) {
                JsonObject object = unwrap(item);
                result.add(new VideoPartInfo(requiredString(object, "ref"), integer(object, "page", result.size() + 1),
                        string(object, "title", reference), integer(object, "duration_ms", 0),
                        integer(object, "width", 0), integer(object, "height", 0)));
            }
            offset += page.size();
            if (page.size() < 100) break;
        }
        return result;
    }

    public MusicDetail videoPartTrack(VideoInfo video, VideoPartInfo part) {
        TuneWeavePlatform platform = platformFromReference(video.reference());
        List<Artist> creators = video.creators().stream()
                .map(creator -> entities.videoCreatorArtist(platform, creator))
                .toList();
        Album album = entities.videoAlbum(platform, video.reference(), video.title(), video.coverUrl(), creators);
        MusicDetail result = MusicDetail.fromTuneWeave(
                entities.stableId(platform, "video-part:" + video.reference() + ':' + part.reference()),
                video.reference(), "video", part.title(), part.durationMillis(), album, creators);
        result.setSourcePartRef(part.reference());
        result.setPusherInfo(indi.etern.musichud.beans.music.PusherInfo.EMPTY);
        entities.cacheTrack(result);
        return result;
    }

    public LyricInfo loadLyrics(MusicDetail musicDetail) {
        if (musicDetail == null || musicDetail.getSourceRef().isBlank()
                || "video".equals(musicDetail.getSourceKind()) || "radio_station".equals(musicDetail.getSourceKind())) {
            return LyricInfo.NONE;
        }
        TuneWeavePlatform platform = platformFromReference(musicDetail.getSourceRef());
        JsonObject lyrics;
        if ("podcast_episode".equals(musicDetail.getSourceKind())) {
            JsonObject data = object(requestForPlatform(platform, "GET", "/v1/episodes/"
                    + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef()) + "/lyrics",
                    Map.of(), null).data());
            lyrics = object(data.get("lyrics"));
        } else {
            lyrics = unwrap(requestForPlatform(platform, "GET", "/v1/tracks/"
                    + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef()) + "/lyrics",
                    Map.of("word_synced", "true", "translated", "true", "romanized", "true"), null).data());
        }
        JsonObject extensions = object(lyrics.get("extensions"));
        String wordSyncedTranslated = string(extensions, "word_synced_translated",
                string(lyrics, "translated", ""));
        return new LyricInfo(new Lyric(string(lyrics, "plain", "")),
                new Lyric(string(lyrics, "translated", "")),
                new Lyric(string(lyrics, "word_synced", "")),
                new Lyric(wordSyncedTranslated));
    }

    public LyricInfo loadVideoLyrics(MusicDetail musicDetail) {
        requireReference(musicDetail == null ? null : musicDetail.getSourceRef(), "video");
        TuneWeavePlatform platform = platformFromReference(musicDetail.getSourceRef());
        String partReference = musicDetail.getSourcePartRef();
        if (partReference == null || partReference.isBlank()) {
            List<JsonElement> parts = elements(requestForPlatform(platform, "GET", "/v1/videos/"
                    + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef()) + "/parts",
                    Map.of("type", "video", "limit", "1", "offset", "0"), null).data());
            if (parts.isEmpty()) return LyricInfo.NONE;
            partReference = string(unwrap(parts.getFirst()), "ref", "");
            if (partReference.isBlank()) return LyricInfo.NONE;
        }
        String videoPath = "/v1/videos/" + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef());
        Map<String, String> query = Map.of(
                "type", "video",
                "part", partReference
        );
        JsonObject catalog = object(requestForPlatform(platform, "GET", videoPath + "/subtitles", query, null).data());
        String defaultLanguage = string(catalog, "default_language", "");
        JsonObject selected = null;
        for (JsonElement element : elements(catalog.get("items"))) {
            JsonObject candidate = unwrap(element);
            if (string(candidate, "ref", "").isBlank()) continue;
            if (selected == null || (!defaultLanguage.isBlank()
                    && defaultLanguage.equals(string(candidate, "language", "")))) {
                selected = candidate;
            }
            if (!defaultLanguage.isBlank() && defaultLanguage.equals(string(candidate, "language", ""))) {
                break;
            }
        }
        if (selected == null) return LyricInfo.NONE;

        String subtitleReference = string(selected, "ref", "");
        JsonObject document = object(requestForPlatform(platform, "GET",
                videoPath + "/subtitles/" + TuneWeaveApiClient.encodePathSegment(subtitleReference), query, null).data());
        StringBuilder lrc = new StringBuilder();
        for (JsonElement element : elements(document.get("cues"))) {
            JsonObject cue = unwrap(element);
            String text = string(cue, "text", "").replace('\r', ' ').replace('\n', ' ').trim();
            if (text.isBlank()) continue;
            long startMillis = Math.max(0L, longValue(cue, "start_ms", 0L));
            long minutes = startMillis / 60_000L;
            long seconds = (startMillis / 1_000L) % 60L;
            long millis = startMillis % 1_000L;
            lrc.append(String.format(Locale.ROOT, "[%02d:%02d.%03d]%s%n", minutes, seconds, millis, text));
        }
        if (lrc.isEmpty()) return LyricInfo.NONE;
        return new LyricInfo(new Lyric(lrc.toString()), Lyric.NONE, Lyric.NONE, Lyric.NONE);
    }

    public void setTrackFavorite(MusicDetail musicDetail, boolean favorite) {
        requireReference(musicDetail == null ? null : musicDetail.getSourceRef(), "track");
        TuneWeavePlatform platform = platformFromReference(musicDetail.getSourceRef());
        String libraryPath = "video".equals(musicDetail.getSourceKind())
                ? "/v1/account/library/videos/" : "/v1/account/favorites/tracks/";
        requestForPlatform(platform, favorite ? "PUT" : "DELETE", libraryPath
                + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef()), Map.of(), null);
    }

    public void setPlaylistSubscribed(Playlist playlist, boolean subscribed) {
        requireReference(playlist == null ? null : playlist.getSourceRef(), "playlist");
        TuneWeavePlatform platform = platformFromReference(playlist.getSourceRef());
        requestForPlatform(platform, subscribed ? "PUT" : "DELETE", "/v1/account/favorites/playlists/"
                + TuneWeaveApiClient.encodePathSegment(playlist.getSourceRef()), Map.of(), null);
    }

    public void setAlbumSubscribed(Album album, boolean subscribed) {
        requireReference(album == null ? null : album.getSourceRef(), "album");
        TuneWeavePlatform platform = platformFromReference(album.getSourceRef());
        requestForPlatform(platform, subscribed ? "PUT" : "DELETE", "/v1/account/library/albums/"
                + TuneWeaveApiClient.encodePathSegment(album.getSourceRef()), Map.of(), null);
    }

    public void setArtistSubscribed(Artist artist, boolean subscribed) {
        requireReference(artist == null ? null : artist.getSourceRef(), "artist");
        TuneWeavePlatform platform = platformFromReference(artist.getSourceRef());
        requestForPlatform(platform, subscribed ? "PUT" : "DELETE", "/v1/account/following/artists/"
                + TuneWeaveApiClient.encodePathSegment(artist.getSourceRef()), Map.of(), null);
    }

    public void modifyPlaylistTracks(Playlist playlist, MusicDetail musicDetail, boolean add) {
        requireReference(playlist == null ? null : playlist.getSourceRef(), "playlist");
        requireReference(musicDetail == null ? null : musicDetail.getSourceRef(), "track");
        if (playlist.getSourceRef().startsWith("account:favorite_tracks:")) {
            setTrackFavorite(musicDetail, add);
            return;
        }
        JsonObject body = new JsonObject();
        JsonArray refs = new JsonArray();
        refs.add(musicDetail.getSourceRef());
        body.add("refs", refs);
        TuneWeavePlatform platform = platformFromReference(playlist.getSourceRef());
        String itemPath = "video".equals(musicDetail.getSourceKind()) ? "/videos" : "/tracks";
        requestForPlatform(platform, add ? "POST" : "DELETE", "/v1/playlists/"
                + TuneWeaveApiClient.encodePathSegment(playlist.getSourceRef()) + itemPath, Map.of(), body);
    }

    public Playlist createPlatformPlaylist(String name, boolean privatePlaylist) {
        TuneWeavePlatform platform = defaultPlatform();
        JsonObject body = new JsonObject();
        body.addProperty("platform", platform.apiName());
        body.addProperty("name", name == null ? "" : name.trim());
        body.addProperty("visibility", privatePlaylist ? "private" : "public");
        body.addProperty("kind", "normal");
        JsonObject result = object(requestForPlatform(platform, "POST", "/v1/playlists", Map.of(), body).data());
        JsonElement playlistData = result.get("playlist");
        if (playlistData != null && playlistData.isJsonObject()) {
            return entities.toPlaylist(platform, playlistData.getAsJsonObject());
        }
        String reference = string(result, "playlist_ref", "");
        if (reference.isBlank()) {
            throw new TuneWeaveApiClient.TuneWeaveException(
                    "TuneWeave did not return the created playlist reference", false);
        }
        return loadPlaylistDetail(reference);
    }

    public void updatePlatformPlaylist(Playlist playlist, String name, String description) {
        requireReference(playlist == null ? null : playlist.getSourceRef(), "playlist");
        JsonObject body = new JsonObject();
        if (name != null) body.addProperty("name", name.trim());
        if (description != null) body.addProperty("description", description.trim());
        if (body.isEmpty()) return;
        TuneWeavePlatform platform = platformFromReference(playlist.getSourceRef());
        requestForPlatform(platform, "PATCH", "/v1/playlists/"
                + TuneWeaveApiClient.encodePathSegment(playlist.getSourceRef()), Map.of(), body);
    }

    public void deletePlatformPlaylist(Playlist playlist) {
        requireReference(playlist == null ? null : playlist.getSourceRef(), "playlist");
        TuneWeavePlatform platform = platformFromReference(playlist.getSourceRef());
        requestForPlatform(platform, "DELETE", "/v1/playlists/"
                + TuneWeaveApiClient.encodePathSegment(playlist.getSourceRef()), Map.of(), null);
        entities.removePlaylist(playlist.getId());
    }

    public void reorderPlatformPlaylists(List<Playlist> playlists) {
        if (playlists == null || playlists.isEmpty()) return;
        TuneWeavePlatform platform = platformFromReference(playlists.getFirst().getSourceRef());
        JsonObject body = new JsonObject();
        JsonArray refs = new JsonArray();
        for (Playlist playlist : playlists) {
            requireReference(playlist.getSourceRef(), "playlist");
            if (platformFromReference(playlist.getSourceRef()) != platform) {
                throw new IllegalArgumentException("Platform playlist order cannot mix providers");
            }
            refs.add(playlist.getSourceRef());
        }
        body.add("refs", refs);
        body.addProperty("platform", platform.apiName());
        requestForPlatform(platform, "PUT", "/v1/account/playlists/order", Map.of(), body);
    }

    public void reorderPlaylistTracks(Playlist playlist, List<MusicDetail> tracks) {
        requireReference(playlist == null ? null : playlist.getSourceRef(), "playlist");
        TuneWeavePlatform platform = platformFromReference(playlist.getSourceRef());
        JsonObject body = new JsonObject();
        JsonArray refs = new JsonArray();
        for (MusicDetail track : tracks) {
            requireReference(track == null ? null : track.getSourceRef(), "track");
            refs.add(track.getSourceRef());
        }
        body.add("refs", refs);
        requestForPlatform(platform, "PUT", "/v1/playlists/"
                + TuneWeaveApiClient.encodePathSegment(playlist.getSourceRef()) + "/tracks/order", Map.of(), body);
    }

    private static void requireReference(String reference, String kind) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("TuneWeave " + kind + " reference is missing");
        }
    }

    public List<UniPlaylistInfo> listUniPlaylists() {
        List<UniPlaylistInfo> result = new ArrayList<>();
        for (JsonObject document : localPlaylists.list()) {
            result.add(localPlaylistInfo(document));
        }
        return result;
    }

    public UniPlaylistInfo createUniPlaylist(String name, String description) {
        return localPlaylistInfo(localPlaylists.create(name, description));
    }

    public UniPlaylistInfo updateUniPlaylist(String reference, String name, String description) {
        return localPlaylistInfo(localPlaylists.update(reference, name, description));
    }

    public void deleteUniPlaylist(String reference) {
        localPlaylists.delete(reference);
    }

    public List<UniItemInfo> listUniPlaylistItems(String reference) {
        List<UniItemInfo> result = new ArrayList<>();
        for (JsonObject object : localPlaylists.items(reference)) {
            JsonObject snapshot = object.has("snapshot") && object.get("snapshot").isJsonObject()
                    ? object.getAsJsonObject("snapshot") : new JsonObject();
            String itemRef = string(object, "source_ref", "");
            if (itemRef.isBlank()) continue;
            List<String> artists = new ArrayList<>();
            JsonElement artistData = snapshot.get("artists");
            if (artistData != null && artistData.isJsonArray()) {
                artistData.getAsJsonArray().forEach(value -> artists.add(value.getAsString()));
            }
            result.add(new UniItemInfo(string(object, "id", ""), integer(object, "position", result.size()),
                    string(object, "kind", "track"), itemRef,
                    string(snapshot, "title", itemRef), artists,
                    string(snapshot, "album", ""), integer(snapshot, "duration_ms", 0),
                    string(snapshot, "cover_url", "")));
        }
        return result;
    }

    public MusicDetail uniPlaylistItemTrack(UniItemInfo item) {
        requireReference(item == null ? null : item.sourceRef(), "Uni Playlist item");
        TuneWeavePlatform platform = platformFromReference(item.sourceRef());
        List<Artist> artists = item.artists().stream()
                .map(name -> new Artist(entities.stableId(platform, "uni-artist:" + name), name,
                        "", 0, 0, "", new ArrayList<>(), 0, ""))
                .toList();
        String albumName = item.album().isBlank() ? item.title() : item.album();
        Album album = new Album(entities.stableId(platform, "uni-album:" + item.sourceRef()), albumName,
                item.coverUrl().isBlank() ? MusicHud.ICON_BASE64 : item.coverUrl(),
                I18n.get(MusicHud.MOD_ID + ".text.uniPlaylist.name"), "", 0,
                new ObservableSequencedSet<>(), new java.util.LinkedHashSet<>(artists),
                indi.etern.musichud.beans.music.PusherInfo.EMPTY, "");
        String sourceKind = "mv".equals(item.kind()) ? "video" : item.kind();
        int duration = item.durationMillis();
        if (duration <= 0 && "radio_station".equals(sourceKind)) duration = 24 * 60 * 60 * 1000;
        if (duration <= 0) throw new IllegalArgumentException("Uni Playlist item has no playable duration");
        MusicDetail track = MusicDetail.fromTuneWeave(entities.stableId(platform,
                "uni-item:" + item.id() + ':' + item.sourceRef()), item.sourceRef(), sourceKind,
                item.title(), duration, album, artists);
        track.setClientHostedUni(true);
        track.setPusherInfo(indi.etern.musichud.beans.music.PusherInfo.EMPTY);
        entities.cacheTrack(track);
        return track;
    }

    public void addUniPlaylistItems(String reference, List<String> resourceRefs) {
        if (resourceRefs == null || resourceRefs.isEmpty()) return;
        JsonObject body = new JsonObject();
        JsonArray items = new JsonArray();
        resourceRefs.stream().filter(value -> value != null && !value.isBlank()).limit(100).forEach(value -> {
            JsonObject item = new JsonObject();
            item.addProperty("ref", value);
            item.addProperty("kind", "track");
            items.add(item);
        });
        body.add("items", items);
        localPlaylists.append(reference, materializeItems(body));
    }

    public void addUniPlaylistItem(String reference, String resourceRef, String kind) {
        if (resourceRef == null || resourceRef.isBlank()) return;
        JsonObject body = new JsonObject();
        JsonArray items = new JsonArray();
        JsonObject item = new JsonObject();
        item.addProperty("ref", resourceRef);
        item.addProperty("kind", kind == null || kind.isBlank() ? "track" : kind);
        items.add(item);
        body.add("items", items);
        localPlaylists.append(reference, materializeItems(body));
    }

    public void deleteUniPlaylistItem(String reference, String itemId) {
        localPlaylists.removeItem(reference, itemId);
    }

    public void reorderUniPlaylistItems(String reference, List<String> itemIds) {
        localPlaylists.reorder(reference, itemIds);
    }

    public UniPlaylistInfo importUniPlaylist(String name, List<String> sourceRefs) {
        return importUniPlaylistSources(name, sourceRefs.stream()
                .filter(value -> value != null && !value.isBlank()).limit(50)
                .map(value -> {
                    int separator = value.indexOf(':');
                    if (separator <= 0 || separator + 1 >= value.length()) {
                        throw new IllegalArgumentException("TuneWeave playlist reference is invalid");
                    }
                    return new UniImportSource(value.substring(0, separator), "playlist",
                            value.substring(separator + 1));
                }).toList());
    }

    public UniPlaylistInfo importUniPlaylistSources(String name, List<UniImportSource> sources) {
        return importUniPlaylistSources(name, null, sources);
    }

    public UniPlaylistInfo importUniPlaylistSources(String name, String description,
                                                    List<UniImportSource> sources) {
        MaterializedImport materializedImport = materializeImportSources(sources);
        String playlistName = name == null || name.isBlank() ? materializedImport.name() : name;
        String playlistDescription = description == null
                ? materializedImport.description() : description;
        UniPlaylistInfo playlist = createUniPlaylist(playlistName, playlistDescription);
        try {
            JsonObject document = localPlaylists.append(playlist.reference(), materializedImport.items());
            return localPlaylistInfo(document);
        } catch (RuntimeException error) {
            localPlaylists.delete(playlist.reference());
            throw error;
        }
    }

    public UniPlaylistInfo appendUniPlaylistSources(String reference, List<UniImportSource> sources) {
        requireReference(reference, "local Uni Playlist");
        MaterializedImport materializedImport = materializeImportSources(sources);
        return localPlaylistInfo(localPlaylists.append(reference, materializedImport.items()));
    }

    private MaterializedImport materializeImportSources(List<UniImportSource> sources) {
        JsonObject body = new JsonObject();
        JsonArray sourceArray = new JsonArray();
        sources.stream().limit(50).forEach(source -> {
            JsonObject value = new JsonObject();
            value.addProperty("platform", source.platform());
            value.addProperty("type", source.type());
            value.addProperty("id", source.id());
            sourceArray.add(value);
        });
        body.add("sources", sourceArray);
        List<JsonObject> materialized = new ArrayList<>();
        String materializedName = "";
        String materializedDescription = "";
        int offset = 0;
        int total;
        do {
            JsonObject data = object(requestWithAllCredentials("POST", "/v1/uni/materialize/imports",
                    Map.of("limit", "500", "offset", Integer.toString(offset)), body).data());
            if (materializedName.isBlank()) materializedName = string(data, "name", "");
            if (materializedDescription.isBlank()) materializedDescription = string(data, "description", "");
            List<JsonElement> page = elements(data);
            for (JsonElement value : page) materialized.add(unwrap(value).deepCopy());
            total = integer(data, "item_count", materialized.size());
            offset += page.size();
            if (page.isEmpty()) break;
        } while (offset < total);
        return new MaterializedImport(materializedName, materializedDescription, materialized);
    }

    public JsonObject exportUniPlaylist(String reference) {
        return localPlaylists.exportDocument(reference);
    }

    public UniPlaylistInfo importUniPlaylistDocument(JsonObject document) {
        return localPlaylistInfo(localPlaylists.importDocument(Objects.requireNonNull(document)));
    }

    public MusicResourceInfo getMusicResourceInfo(MusicDetail musicDetail,
                                                   indi.etern.musichud.beans.music.Quality quality) {
        if (musicDetail == null || musicDetail.getSourceRef().isBlank()) {
            return MusicResourceInfo.NONE;
        }
        String reference = musicDetail.getSourceRef();
        TuneWeavePlatform platform = TuneWeavePlatform.fromApiName(reference.contains(":")
                ? reference.substring(0, reference.indexOf(':')) : config.getDefaultMusicPlatform());
        if (musicDetail.isClientHostedUni()) {
            return loadUniClientItemResource(musicDetail, quality);
        }
        if ("radio_station".equals(musicDetail.getSourceKind())) {
            return loadRadioResource(musicDetail, platform);
        }
        String path = switch (musicDetail.getSourceKind()) {
            case "video" -> "/v1/videos/" + TuneWeaveApiClient.encodePathSegment(reference) + "/audio-stream";
            case "podcast_episode" -> "/v1/episodes/" + TuneWeaveApiClient.encodePathSegment(reference) + "/stream";
            default -> "/v1/tracks/" + TuneWeaveApiClient.encodePathSegment(reference) + "/stream";
        };
        Map<String, String> query = new LinkedHashMap<>();
        query.put("quality", qualityName(quality));
        if ("video".equals(musicDetail.getSourceKind())) {
            query.put("type", "video");
            if (!musicDetail.getSourcePartRef().isBlank()) query.put("part", musicDetail.getSourcePartRef());
        }
        JsonObject response = unwrap(requestForPlatform(platform, "GET", path, query, null).data());
        JsonObject stream = "podcast_episode".equals(musicDetail.getSourceKind())
                ? object(response.get("stream")) : response;
        String url = string(stream, "url", "");
        if (url.isBlank()) {
            return MusicResourceInfo.NONE;
        }
        return new MusicResourceInfo(musicDetail.getId(), url, integer(stream, "bitrate", 0),
                longValue(stream, "size", 0L), FormatType.fromSerializedName(
                string(stream, "format", string(stream, "codec", ""))), "", Fee.UNSET,
                integer(stream, "duration_ms", musicDetail.getDurationMillis()), stringMap(stream.get("headers")),
                stringList(stream.get("backup_urls")));
    }

    private MusicResourceInfo loadRadioResource(MusicDetail musicDetail, TuneWeavePlatform platform) {
        if (!isStyledRadioReference(musicDetail.getSourceRef())) {
            JsonObject station = unwrap(requestForPlatform(platform, "GET", "/v1/radio/stations/"
                    + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef()), Map.of(), null).data());
            String url = string(station, "stream_url", "");
            if (url.isBlank()) return MusicResourceInfo.NONE;
            return new MusicResourceInfo(musicDetail.getId(), url, 0, 0L,
                    FormatType.AUTO, "", Fee.UNSET, musicDetail.getDurationMillis(),
                    stringMap(station.get("headers")));
        }
        JsonObject queue = object(requestForPlatform(platform, "GET", "/v1/radio/stations/"
                + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef()) + "/tracks",
                Map.of("limit", musicDetail.getSourcePartRef().isBlank() ? "1" : "100"), null).data());
        List<JsonElement> items = elements(queue.get("items"));
        JsonObject item = items.stream().map(TuneWeaveJson::unwrap)
                .filter(value -> musicDetail.getSourcePartRef().equals(string(value, "ref", "")))
                .findFirst()
                .or(() -> items.stream().findFirst().map(TuneWeaveJson::unwrap))
                .orElseGet(JsonObject::new);
        String url = string(item, "stream_url", "");
        int duration = integer(item, "duration_ms", musicDetail.getDurationMillis());
        Map<String, String> headers = stringMap(item.get("headers"));
        if (url.isBlank()) return MusicResourceInfo.NONE;
        return new MusicResourceInfo(musicDetail.getId(), url, 0, 0L,
                FormatType.AUTO, "", Fee.UNSET, duration, headers);
    }

    private MusicResourceInfo loadUniClientItemResource(MusicDetail musicDetail,
                                                         indi.etern.musichud.beans.music.Quality quality) {
        JsonObject item = new JsonObject();
        item.addProperty("id", String.format(Locale.ROOT, "item_%016x", musicDetail.getId()));
        item.addProperty("position", 0);
        item.addProperty("kind", musicDetail.getSourceKind());
        item.addProperty("source_ref", musicDetail.getSourceRef());
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("title", musicDetail.getName());
        JsonArray artists = new JsonArray();
        musicDetail.getArtists().stream().map(Artist::getName).forEach(artists::add);
        snapshot.add("artists", artists);
        String albumName = musicDetail.getAlbum().getName();
        if (albumName.isBlank()) snapshot.add("album", JsonNull.INSTANCE);
        else snapshot.addProperty("album", albumName);
        snapshot.addProperty("duration_ms", musicDetail.getDurationMillis());
        snapshot.add("isrc", JsonNull.INSTANCE);
        String coverUrl = musicDetail.getAlbum().getPicUrl();
        if (coverUrl != null && coverUrl.startsWith("https://")) snapshot.addProperty("cover_url", coverUrl);
        else snapshot.add("cover_url", JsonNull.INSTANCE);
        snapshot.add("version_tags", new JsonArray());
        JsonObject snapshotExtensions = new JsonObject();
        snapshotExtensions.addProperty("canonical_ref", musicDetail.getSourceRef());
        snapshotExtensions.add("playable", JsonNull.INSTANCE);
        snapshotExtensions.add("available_qualities", new JsonArray());
        for (String key : List.of("mv_ref", "video_kind", "published_at", "podcast_ref", "audio_ref",
                "serial_number", "description", "category", "region", "current_program", "has_direct_stream")) {
            snapshotExtensions.add(key, JsonNull.INSTANCE);
        }
        snapshot.add("extensions", snapshotExtensions);
        item.add("snapshot", snapshot);
        item.addProperty("added_at_ms", System.currentTimeMillis());
        JsonObject itemExtensions = new JsonObject();
        for (String key : List.of("import_source_index", "import_source_ref", "import_source_type",
                "imported_from_item_id")) {
            itemExtensions.add(key, JsonNull.INSTANCE);
        }
        item.add("extensions", itemExtensions);
        JsonObject body = new JsonObject();
        body.add("item", item);
        body.addProperty("quality", qualityName(quality));
        JsonObject data = object(requestWithAllCredentials("POST", "/v1/uni/items/stream", Map.of(), body).data());
        JsonObject stream = object(data.get("stream"));
        String url = string(stream, "url", "");
        if (url.isBlank()) return MusicResourceInfo.NONE;
        return new MusicResourceInfo(musicDetail.getId(), url, integer(stream, "bitrate", 0),
                longValue(stream, "size", 0L), FormatType.fromSerializedName(
                string(stream, "format", string(stream, "codec", ""))), "", Fee.UNSET,
                integer(stream, "duration_ms", musicDetail.getDurationMillis()), stringMap(stream.get("headers")));
    }

    private static boolean isStyledRadioReference(String reference) {
        return reference != null && reference.contains(":difm:");
    }

    public List<?> search(String keywords, SearchType searchType, int offset, TuneWeavePlatform platform) {
        String type = switch (searchType) {
            case ALBUM -> "album";
            case ARTIST -> "artist";
            case PLAYLIST -> "playlist";
            case RADIO -> "podcast";
            default -> platform == TuneWeavePlatform.BILIBILI ? "video" : "track";
        };
        Map<String, String> query = new LinkedHashMap<>();
        query.put("q", keywords);
        query.put("type", type);
        query.put("platform", platform.apiName());
        query.put("limit", "50");
        query.put("offset", Integer.toString(Math.max(0, offset)));
        if (platform == TuneWeavePlatform.BILIBILI && "video".equals(type)) {
            // Keep the search tab in relevance order; the provider also
            // accepts newer sorting modes, but they are not the UI default.
            query.put("order", "relevance");
        }
        JsonElement data = requestForPlatform(platform, "GET", "/v1/search", query, null).data();
        List<JsonObject> rawItems = new ArrayList<>();
        for (JsonElement item : elements(data)) {
            JsonObject object = unwrap(item);
            if (!object.isEmpty()) rawItems.add(object);
        }
        return switch (searchType) {
            case ALBUM -> rawItems.stream().map(value -> entities.toAlbum(platform, value)).toList();
            case ARTIST -> rawItems.stream().map(value -> entities.toArtist(platform, value)).toList();
            case PLAYLIST -> rawItems.stream().map(value -> entities.toPlaylist(platform, value)).toList();
            case RADIO -> rawItems.stream().map(value -> entities.toPodcast(platform, value)).toList();
            default -> rawItems.stream().map(value -> platform == TuneWeavePlatform.BILIBILI
                    ? entities.toVideoTrack(platform, value) : entities.toTrack(platform, value)).toList();
        };
    }

    private TuneWeavePlatform platformFromReference(String reference) {
        if (reference.startsWith("account:favorite_tracks:")) {
            int lastSeparator = reference.lastIndexOf(':');
            return TuneWeavePlatform.fromApiName(reference.substring(lastSeparator + 1));
        }
        int separator = reference.indexOf(':');
        return separator > 0
                ? TuneWeavePlatform.fromApiName(reference.substring(0, separator))
                : defaultPlatform();
    }

    private List<JsonObject> materializeItems(JsonObject body) {
        JsonObject data = object(requestWithAllCredentials(
                "POST", "/v1/uni/materialize/items", Map.of(), body).data());
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement value : elements(data)) result.add(unwrap(value).deepCopy());
        if (result.isEmpty()) throw new IllegalArgumentException("TuneWeave did not materialize any playlist items");
        return result;
    }

    private static UniPlaylistInfo localPlaylistInfo(JsonObject document) {
        String reference = LocalUniPlaylistStore.reference(document);
        return new UniPlaylistInfo(reference, string(document, "name", reference),
                string(document, "description", ""), integer(document, "item_count", 0));
    }

    public void logout(TuneWeavePlatform platform) {
        authentication.logout(platform);
    }

    public void clearCredential(TuneWeavePlatform platform) {
        authentication.clearCredential(platform);
    }

    public TuneWeaveApiClient.TuneWeaveResponse requestForPlatform(
            TuneWeavePlatform platform, String method, String path, Map<String, String> query, JsonElement body) {
        return gateway.requestForPlatform(platform, method, path, query, body);
    }

    public TuneWeaveApiClient.TuneWeaveResponse requestWithAllCredentials(
            String method, String path, Map<String, String> query, JsonElement body) {
        return gateway.requestWithAllCredentials(method, path, query, body);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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

    private static String qualityName(indi.etern.musichud.beans.music.Quality quality) {
        if (quality == null) return "auto";
        return switch (quality) {
            case STANDARD -> "standard";
            case HIGHER -> "higher";
            case EX_HIGH -> "high";
            case LOSSLESS -> "lossless";
            case HIRES -> "hires";
            case JY_EFFECT, SKY -> "spatial";
            case DOLBY -> "dolby";
            case JY_MASTER -> "master";
            case NONE -> "auto";
        };
    }

    public record UniPlaylistInfo(String reference, String name, String description, int itemCount) {
    }

    public record UniImportSource(String platform, String type, String id) {
    }

    private record MaterializedImport(String name, String description, List<JsonObject> items) {
    }

    public record UniItemInfo(String id, int position, String kind, String sourceRef,
                              String title, List<String> artists, String album,
                              int durationMillis, String coverUrl) {
        public UniItemInfo {
            artists = artists == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(artists));
        }
    }

    public record VideoInfo(String reference, String title, String description, String coverUrl,
                            int durationMillis, String publishedAt, long playCount,
                            List<VideoCreatorInfo> creators, int partCount) {
        public VideoInfo {
            creators = creators == null ? List.of() : List.copyOf(creators);
        }
    }

    public record VideoCreatorInfo(String reference, String name, String avatarUrl) {
        public VideoCreatorInfo {
            reference = Objects.requireNonNullElse(reference, "");
            name = Objects.requireNonNullElse(name, "");
            avatarUrl = Objects.requireNonNullElse(avatarUrl, "");
        }
    }

    public record VideoPartInfo(String reference, int page, String title, int durationMillis,
                                int width, int height) {
    }

    public record PodcastCategoryInfo(String id, String name, String iconUrl) {
    }

    public record PodcastInfo(String reference, String name, String description, String coverUrl,
                              String creatorName, String category, String secondaryCategory,
                              long episodeCount, long subscriberCount, long playCount,
                              boolean subscribed) {
    }

    public record PodcastEpisodeInfo(String reference, String podcastReference, String name,
                                     String description, String coverUrl, String creatorName,
                                     String audioReference, int durationMillis, String publishedAt,
                                     long serialNumber, boolean hasLyrics) {
    }

    public record RadioOptionInfo(String id, String name) {
    }

    public record RadioTaxonomyInfo(List<RadioOptionInfo> categories, List<RadioOptionInfo> regions) {
        public RadioTaxonomyInfo {
            categories = categories == null ? List.of() : List.copyOf(categories);
            regions = regions == null ? List.of() : List.copyOf(regions);
        }
    }

    public record RadioStationInfo(String reference, String name, String description, String coverUrl,
                                   String category, String region, String currentProgram,
                                   boolean subscribed) {
    }

}
