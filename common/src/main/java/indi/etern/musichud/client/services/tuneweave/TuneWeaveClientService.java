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
    private final TuneWeaveProgramService programs = new TuneWeaveProgramService(gateway, entities);
    private final TuneWeaveCatalogService catalog =
            new TuneWeaveCatalogService(gateway, entities, account);
    private final TuneWeavePlaylistService platformPlaylists =
            new TuneWeavePlaylistService(gateway, entities, catalog);
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
        return TuneWeaveReference.isStyledRadio(reference);
    }

    public List<?> search(String keywords, SearchType searchType, int offset, TuneWeavePlatform platform) {
        return catalog.search(keywords, searchType, offset, platform);
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

}
