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
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.VipType;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.server.api.tuneweave.TuneWeaveApiClient;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Client-owned TuneWeave authentication and credential-scoped requests. */
public final class TuneWeaveClientService {
    private static final TuneWeaveClientService INSTANCE = new TuneWeaveClientService();
    private final ClientConfig config = ClientConfig.getInstance();
    private final Map<Long, MusicDetail> tracksById = new ConcurrentHashMap<>();
    private final Map<Long, Playlist> playlistsById = new ConcurrentHashMap<>();
    private final Map<Long, Album> albumsById = new ConcurrentHashMap<>();
    private final Map<Long, Artist> artistsById = new ConcurrentHashMap<>();
    private final LocalUniPlaylistStore localPlaylists = new LocalUniPlaylistStore();

    private TuneWeaveClientService() {
    }

    public static TuneWeaveClientService getInstance() {
        return INSTANCE;
    }

    public TuneWeavePlatform defaultPlatform() {
        return TuneWeavePlatform.fromApiName(config.getDefaultMusicPlatform());
    }

    public void setDefaultPlatform(TuneWeavePlatform platform) {
        config.setDefaultMusicPlatform(Objects.requireNonNull(platform).apiName());
        config.save();
    }

    public boolean isAvailable() {
        return TuneWeaveApiClient.isAvailableAt(config.getTuneWeaveBaseUrl());
    }

    public boolean hasCredential(TuneWeavePlatform platform) {
        return !credential(platform).isBlank();
    }

    public boolean hasPlaylist(long id) {
        return playlistsById.containsKey(id);
    }

    public boolean hasAlbum(long id) {
        return albumsById.containsKey(id);
    }

    public boolean hasArtist(long id) {
        return artistsById.containsKey(id);
    }

    public String credential(TuneWeavePlatform platform) {
        return config.getTuneWeaveCredential(Objects.requireNonNull(platform).apiName());
    }

    public QrSession startQrLogin(TuneWeavePlatform platform, String loginType) {
        JsonObject body = clientModeBody(platform);
        if (loginType != null && !loginType.isBlank()) {
            body.addProperty("login_type", loginType.trim());
        }
        JsonObject data = object(requestWithoutCredential("POST", "/v1/auth/qr", Map.of(), body).data());
        return new QrSession(
                platform,
                requiredString(data, "transaction_id"),
                string(data, "url"),
                string(data, "image_data_url"),
                string(data, "expires_at")
        );
    }

    public QrPoll pollQrLogin(QrSession session) {
        JsonObject data = object(requestWithoutCredential(
                "GET",
                "/v1/auth/qr/" + TuneWeaveApiClient.encodePathSegment(session.transactionId()),
                Map.of(), null).data());
        String state = requiredString(data, "state");
        SessionProfile profile = profile(data.get("profile"));
        if ("confirmed".equals(state)) {
            saveCredential(session.platform(), data.get("caller_credential"));
        }
        return new QrPoll(state, string(data, "message"), profile);
    }

    public SessionProfile loginWithPassword(TuneWeavePlatform platform, String principalType,
                                            String principal, String password, String passwordFormat,
                                            String countryCode) {
        JsonObject body = clientModeBody(platform);
        body.addProperty("principal_type", principalType);
        body.addProperty("principal", principal);
        body.addProperty("password", password);
        if (passwordFormat != null && !passwordFormat.isBlank()) {
            body.addProperty("password_format", passwordFormat);
        }
        if (countryCode != null && !countryCode.isBlank()) {
            body.addProperty("country_code", countryCode);
        }
        JsonObject data = object(requestWithoutCredential("POST", "/v1/auth/password", Map.of(), body).data());
        saveCredential(platform, data.get("caller_credential"));
        return profile(data.get("profile"));
    }

    public ChallengeSession startSmsLogin(TuneWeavePlatform platform, String principal, String countryCode) {
        JsonObject body = clientModeBody(platform);
        body.addProperty("method", "sms");
        body.addProperty("principal", principal);
        body.addProperty("country_code", countryCode == null || countryCode.isBlank() ? "86" : countryCode);
        JsonObject data = object(requestWithoutCredential(
                "POST", "/v1/auth/challenges", Map.of(), body).data());
        return new ChallengeSession(platform, requiredString(data, "transaction_id"));
    }

    public SessionProfile verifySmsLogin(ChallengeSession session, String code) {
        JsonObject body = new JsonObject();
        body.addProperty("code", code);
        JsonObject data = object(requestWithoutCredential(
                "POST",
                "/v1/auth/challenges/" + TuneWeaveApiClient.encodePathSegment(session.transactionId()) + "/verify",
                Map.of(), body).data());
        saveCredential(session.platform(), data.get("caller_credential"));
        return profile(data.get("profile"));
    }

    public SessionProfile loadSession(TuneWeavePlatform platform) {
        JsonElement data = requestForPlatform(
                platform, "GET", "/v1/auth/session", Map.of("platform", platform.apiName()), null).data();
        return profile(data);
    }

    public SessionProfile refreshSession(TuneWeavePlatform platform) {
        JsonObject body = clientModeBody(platform);
        JsonObject data = object(requestForPlatform(
                platform, "POST", "/v1/auth/session/refresh", Map.of(), body).data());
        saveCredential(platform, data.get("caller_credential"));
        return profile(data.get("profile"));
    }

    /** Loads the caller's account playlists without routing the private credential through Minecraft. */
    public UserCategoryPlaylists loadAccountPlaylists() {
        TuneWeavePlatform platform = defaultPlatform();
        JsonElement data = requestForPlatform(platform, "GET", "/v1/account/playlists",
                Map.of("platform", platform.apiName(), "limit", "100", "offset", "0"), null).data();
        ObservableSequencedSet<Playlist> created = new ObservableSequencedSet<>();
        ObservableSequencedSet<Playlist> subscribed = new ObservableSequencedSet<>();
        for (JsonElement item : elements(data)) {
            JsonObject raw = unwrap(item);
            Playlist playlist = toPlaylist(platform, raw);
            if (playlist == Playlist.EMPTY) {
                continue;
            }
            (bool(raw, "subscribed", false) ? subscribed : created).add(playlist);
        }
        String likeRef = "account:favorite_tracks:" + platform.apiName();
        Playlist liked = Playlist.fromTuneWeave(
                stableId(platform, "playlist:" + likeRef), likeRef, "Liked Songs", MusicHud.ICON_BASE64,
                0, 0, Profile.ANONYMOUS);
        playlistsById.put(liked.getId(), liked);
        return new UserCategoryPlaylists(liked, created, subscribed);
    }

    public java.util.LinkedHashSet<Album> loadAccountAlbums() {
        TuneWeavePlatform platform = defaultPlatform();
        JsonElement data = requestForPlatform(platform, "GET", "/v1/account/library/albums",
                Map.of("platform", platform.apiName(), "limit", "100", "offset", "0"), null).data();
        java.util.LinkedHashSet<Album> result = new java.util.LinkedHashSet<>();
        for (JsonElement item : elements(data)) {
            Album album = toAlbum(platform, unwrap(item));
            if (album != Album.NONE) result.add(album);
        }
        return result;
    }

    public java.util.LinkedHashSet<Artist> loadAccountArtists() {
        TuneWeavePlatform platform = defaultPlatform();
        JsonElement data = requestForPlatform(platform, "GET", "/v1/account/following/artists",
                Map.of("platform", platform.apiName(), "limit", "100", "offset", "0"), null).data();
        java.util.LinkedHashSet<Artist> result = new java.util.LinkedHashSet<>();
        for (JsonElement item : elements(data)) {
            Artist artist = toArtist(platform, unwrap(item));
            if (!artist.getSourceRef().isBlank()) result.add(artist);
        }
        return result;
    }

    public CloudLibrary loadCloudLibrary() {
        TuneWeavePlatform platform = cloudPlatform();
        List<CloudTrackInfo> result = new ArrayList<>();
        int offset = 0;
        long total = -1;
        long storageSize = -1;
        long storageMaxSize = -1;
        while (true) {
            TuneWeaveApiClient.TuneWeaveResponse response = requestForPlatform(
                    platform, "GET", "/v1/account/cloud/tracks",
                    Map.of("platform", platform.apiName(), "limit", "100", "offset", Integer.toString(offset)), null);
            List<JsonElement> page = elements(response.data());
            for (JsonElement value : page) result.add(toCloudTrack(platform, unwrap(value)));
            JsonObject pagination = object(response.meta().get("pagination"));
            JsonObject extensions = object(pagination.get("extensions"));
            if (total < 0) total = longValue(pagination, "total", -1);
            if (storageSize < 0) storageSize = longValue(extensions, "storage_size", -1);
            if (storageMaxSize < 0) storageMaxSize = longValue(extensions, "storage_max_size", -1);
            boolean hasMore = bool(pagination, "has_more", page.size() == 100);
            int nextOffset = integer(pagination, "next_offset", offset + page.size());
            if (!hasMore || page.isEmpty() || nextOffset <= offset) break;
            offset = nextOffset;
        }
        return new CloudLibrary(result, total < 0 ? result.size() : total, storageSize, storageMaxSize);
    }

    public void deleteCloudTrack(CloudTrackInfo cloudTrack) {
        requireReference(cloudTrack == null ? null : cloudTrack.reference(), "cloud track");
        TuneWeavePlatform platform = platformFromReference(cloudTrack.reference());
        JsonObject body = new JsonObject();
        JsonArray refs = new JsonArray();
        refs.add(cloudTrack.reference());
        body.add("refs", refs);
        requestForPlatform(platform, "DELETE", "/v1/account/cloud/tracks", Map.of(), body);
    }

    public String uploadCloudTrack(Path file, String songName, String artist, String album) {
        Objects.requireNonNull(file, "file");
        TuneWeavePlatform platform = cloudPlatform();
        try {
            if (!Files.isRegularFile(file)) throw new IllegalArgumentException("Cloud upload must be a file");
            long size = Files.size(file);
            if (size <= 0 || size > 500L * 1024 * 1024) {
                throw new IllegalArgumentException("Cloud upload file must be between 1 byte and 500 MiB");
            }
            String filename = file.getFileName().toString();
            String contentType = cloudContentType(file);
            String md5 = digestFile(file, "MD5");
            long bitrate = 999_000L;
            JsonObject ticketBody = new JsonObject();
            ticketBody.addProperty("md5", md5);
            ticketBody.addProperty("file_size", size);
            ticketBody.addProperty("filename", filename);
            ticketBody.addProperty("bitrate", bitrate);
            ticketBody.addProperty("content_type", contentType);
            JsonObject ticket = object(requestForPlatform(platform, "POST", "/v1/account/cloud/uploads/ticket",
                    Map.of("platform", platform.apiName()), ticketBody).data());
            boolean uploadRequired = bool(ticket, "upload_required", true);
            String provisionalTrackId = requiredString(ticket, "provisional_track_id");
            String resourceId = requiredString(ticket, "resource_id");
            if (uploadRequired) {
                TuneWeaveApiClient.uploadTicketFile(requiredString(ticket, "upload_url"),
                        requiredString(ticket, "upload_method"), stringMap(ticket.get("upload_headers")), file);
            }
            JsonObject completion = new JsonObject();
            completion.addProperty("provisional_track_id", provisionalTrackId);
            completion.addProperty("resource_id", resourceId);
            completion.addProperty("md5", md5);
            completion.addProperty("filename", filename);
            completion.addProperty("bitrate", bitrate);
            if (songName != null && !songName.isBlank()) completion.addProperty("song_name", songName.trim());
            if (artist != null && !artist.isBlank()) completion.addProperty("artist", artist.trim());
            if (album != null && !album.isBlank()) completion.addProperty("album", album.trim());
            JsonObject data = object(requestForPlatform(platform, "POST", "/v1/account/cloud/uploads/complete",
                    Map.of("platform", platform.apiName()), completion).data());
            return string(data, "track_ref", "");
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("Failed to read the selected cloud upload file", error);
        }
    }

    public String importCloudTrack(String md5, String sourceTrackId, long bitrate, long fileSize,
                                   String fileType, String songName, String artist, String album) {
        TuneWeavePlatform platform = cloudPlatform();
        JsonObject body = new JsonObject();
        body.addProperty("md5", md5 == null ? "" : md5.trim());
        if (sourceTrackId != null && !sourceTrackId.isBlank()) body.addProperty("source_track_id", sourceTrackId.trim());
        body.addProperty("bitrate", bitrate);
        body.addProperty("file_size", fileSize);
        body.addProperty("file_type", fileType == null ? "" : fileType.trim());
        body.addProperty("song_name", songName == null ? "" : songName.trim());
        body.addProperty("artist", artist == null ? "" : artist.trim());
        body.addProperty("album", album == null ? "" : album.trim());
        JsonObject data = object(requestForPlatform(platform, "POST", "/v1/account/cloud/imports",
                Map.of("platform", platform.apiName()), body).data());
        return string(data, "track_ref", "");
    }

    public boolean matchCloudTrack(CloudTrackInfo cloudTrack, String targetTrackId) {
        requireReference(cloudTrack == null ? null : cloudTrack.reference(), "cloud track");
        TuneWeavePlatform platform = platformFromReference(cloudTrack.reference());
        JsonObject body = new JsonObject();
        body.addProperty("user_id", cloudUserId(platform));
        body.addProperty("cloud_track_id", idFromReference(cloudTrack.reference()));
        body.addProperty("target_track_id", targetTrackId == null || targetTrackId.isBlank()
                ? "0" : idFromReference(targetTrackId.trim()));
        JsonObject data = object(requestForPlatform(platform, "POST", "/v1/account/cloud/matches",
                Map.of("platform", platform.apiName()), body).data());
        return bool(data, "matched", false);
    }

    public LyricInfo loadCloudLyrics(CloudTrackInfo cloudTrack) {
        requireReference(cloudTrack == null ? null : cloudTrack.reference(), "cloud track");
        TuneWeavePlatform platform = platformFromReference(cloudTrack.reference());
        JsonObject data = object(requestForPlatform(platform, "GET", "/v1/account/cloud/lyrics", Map.of(
                "platform", platform.apiName(), "user_id", cloudUserId(platform),
                "sid", idFromReference(cloudTrack.reference())), null).data());
        return new LyricInfo(new Lyric(string(data, "plain", "")),
                new Lyric(string(data, "translated", "")),
                new Lyric(string(data, "word_synced", "")),
                new Lyric(string(data, "romanized", "")));
    }

    public void downloadCloudTrack(CloudTrackInfo cloudTrack, Path target) {
        requireReference(cloudTrack == null ? null : cloudTrack.reference(), "cloud track");
        Objects.requireNonNull(target, "target");
        TuneWeavePlatform platform = platformFromReference(cloudTrack.reference());
        JsonObject data = object(requestForPlatform(platform, "GET", "/v1/account/cloud/tracks/"
                + TuneWeaveApiClient.encodePathSegment(cloudTrack.reference()) + "/download",
                Map.of(), null).data());
        if (!bool(data, "available", !string(data, "url", "").isBlank())) {
            throw new IllegalArgumentException("Cloud source file is not available for download");
        }
        TuneWeaveApiClient.downloadMediaFile(requiredString(data, "url"),
                stringMap(data.get("headers")), target);
    }

    public Playlist loadPlaylistDetail(long id) {
        Playlist cached = playlistsById.get(id);
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
        if (reference.startsWith("account:favorite_tracks:")) {
            Playlist playlist = playlistsById.values().stream()
                    .filter(value -> reference.equals(value.getSourceRef()))
                    .findFirst()
                    .orElseGet(() -> Playlist.fromTuneWeave(stableId(platform, "playlist:" + reference),
                            reference, "Liked Songs", MusicHud.ICON_BASE64, 0, 0, Profile.ANONYMOUS));
            List<MusicDetail> tracks = new ArrayList<>();
            for (JsonElement item : elements(requestForPlatform(platform, "GET", "/v1/account/favorites/tracks",
                    Map.of("platform", platform.apiName(), "limit", "100", "offset", "0"), null).data())) {
                MusicDetail track = toTrack(platform, unwrap(item));
                if (track != MusicDetail.NONE) tracks.add(track);
            }
            ObservableSequencedSet<MusicDetail> likedTracks = new ObservableSequencedSet<>();
            likedTracks.addAll(tracks);
            playlist.setTracks(likedTracks);
            playlist.setMusicTrackCount(tracks.size());
            playlistsById.put(playlist.getId(), playlist);
            return playlist;
        }
        Playlist playlist = toPlaylist(platform, unwrap(requestForPlatform(
                platform, "GET", "/v1/playlists/"
                        + TuneWeaveApiClient.encodePathSegment(reference), Map.of(), null).data()));
        List<MusicDetail> tracks = new ArrayList<>();
        for (JsonElement item : elements(requestForPlatform(platform, "GET",
                "/v1/playlists/" + TuneWeaveApiClient.encodePathSegment(reference) + "/items",
                Map.of("limit", "100", "offset", "0"), null).data())) {
            JsonObject raw = unwrap(item);
            MusicDetail track = "video".equals(string(raw, "kind", "track"))
                    ? toVideoTrack(platformFromReference(reference), raw)
                    : toTrack(platformFromReference(reference), raw);
            if (track != MusicDetail.NONE) tracks.add(track);
        }
        ObservableSequencedSet<MusicDetail> playlistTracks = new ObservableSequencedSet<>();
        playlistTracks.addAll(tracks);
        playlist.setTracks(playlistTracks);
        playlist.setMusicTrackCount(tracks.size());
        playlistsById.put(playlist.getId(), playlist);
        return playlist;
    }

    public Album loadAlbumDetail(long id) {
        Album cached = albumsById.get(id);
        return cached == null ? Album.NONE : loadAlbumDetail(cached.getSourceRef());
    }

    public Album loadAlbumDetail(String reference) {
        if (reference == null || reference.isBlank()) return Album.NONE;
        TuneWeavePlatform platform = platformFromReference(reference);
        Album album = toAlbum(platform, unwrap(requestForPlatform(platform, "GET", "/v1/albums/"
                + TuneWeaveApiClient.encodePathSegment(reference), Map.of(), null).data()));
        List<MusicDetail> tracks = new ArrayList<>();
        for (JsonElement item : elements(requestForPlatform(platform, "GET", "/v1/albums/"
                + TuneWeaveApiClient.encodePathSegment(reference) + "/tracks",
                Map.of("limit", "100", "offset", "0"), null).data())) {
            MusicDetail track = toTrack(platform, unwrap(item));
            if (track != MusicDetail.NONE) tracks.add(track);
        }
        ObservableSequencedSet<MusicDetail> albumTracks = new ObservableSequencedSet<>();
        albumTracks.addAll(tracks);
        album.setMusicDetails(albumTracks);
        return album;
    }

    public Artist loadArtistDetail(long id) {
        Artist cached = artistsById.get(id);
        return cached == null ? new Artist() : loadArtistDetail(cached.getSourceRef());
    }

    public Artist loadArtistDetail(String reference) {
        if (reference == null || reference.isBlank()) return new Artist();
        TuneWeavePlatform platform = platformFromReference(reference);
        return toArtist(platform, unwrap(requestForPlatform(platform, "GET", "/v1/artists/"
                + TuneWeaveApiClient.encodePathSegment(reference), Map.of(), null).data()));
    }

    public List<MusicDetail> loadArtistTracks(long id, int offset) {
        Artist cached = artistsById.get(id);
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
            MusicDetail track = toTrack(platform, unwrap(item));
            if (track != MusicDetail.NONE) result.add(track);
        }
        return result;
    }

    public MusicDetail loadTrackDetail(MusicDetail musicDetail) {
        if (musicDetail == null || musicDetail.getSourceRef().isBlank()
                || "video".equals(musicDetail.getSourceKind())) {
            return musicDetail == null ? MusicDetail.NONE : musicDetail;
        }
        TuneWeavePlatform platform = platformFromReference(musicDetail.getSourceRef());
        MusicDetail detail = toTrack(platform, unwrap(requestForPlatform(platform, "GET", "/v1/tracks/"
                + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef()), Map.of(), null).data()));
        if (detail != MusicDetail.NONE) {
            tracksById.put(detail.getId(), detail);
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
        return toVideoInfo(video);
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
                .map(name -> new Artist(stableId(platform, "video-creator:" + name), name,
                        "", 0, 0, "", new ArrayList<>(), 0, ""))
                .toList();
        Album album = videoAlbum(platform, video.reference(), video.title(), video.coverUrl(), creators);
        MusicDetail result = MusicDetail.fromTuneWeave(
                stableId(platform, "video-part:" + video.reference() + ':' + part.reference()),
                video.reference(), "video", part.title(), part.durationMillis(), album, creators);
        result.setSourcePartRef(part.reference());
        result.setPusherInfo(indi.etern.musichud.beans.music.PusherInfo.EMPTY);
        tracksById.put(result.getId(), result);
        return result;
    }

    public LyricInfo loadLyrics(MusicDetail musicDetail) {
        if (musicDetail == null || musicDetail.getSourceRef().isBlank()
                || "video".equals(musicDetail.getSourceKind())) {
            return LyricInfo.NONE;
        }
        TuneWeavePlatform platform = platformFromReference(musicDetail.getSourceRef());
        JsonObject lyrics = unwrap(requestForPlatform(platform, "GET", "/v1/tracks/"
                + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef()) + "/lyrics",
                Map.of("word_synced", "true", "translated", "true", "romanized", "true"), null).data());
        return new LyricInfo(new Lyric(string(lyrics, "plain", "")),
                new Lyric(string(lyrics, "translated", "")),
                new Lyric(string(lyrics, "word_synced", "")),
                new Lyric(string(lyrics, "romanized", "")));
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
            return toPlaylist(platform, playlistData.getAsJsonObject());
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
        playlistsById.remove(playlist.getId());
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
                    string(snapshot, "title", itemRef), artists));
        }
        return result;
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
        String playlistName = name == null || name.isBlank() ? materializedName : name;
        UniPlaylistInfo playlist = createUniPlaylist(playlistName, materializedDescription);
        try {
            localPlaylists.append(playlist.reference(), materialized);
            return new UniPlaylistInfo(playlist.reference(), playlist.name(), playlist.description(), materialized.size());
        } catch (RuntimeException error) {
            localPlaylists.delete(playlist.reference());
            throw error;
        }
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
        String path = "video".equals(musicDetail.getSourceKind())
                ? "/v1/videos/" + TuneWeaveApiClient.encodePathSegment(reference) + "/audio-stream"
                : "/v1/tracks/" + TuneWeaveApiClient.encodePathSegment(reference) + "/stream";
        Map<String, String> query = new LinkedHashMap<>();
        query.put("quality", qualityName(quality));
        query.put("fallback", "true");
        query.put("fallback_platforms", "netease,qq,kugou,migu,kuwo,soda");
        if ("video".equals(musicDetail.getSourceKind())) {
            query.put("type", "video");
            if (!musicDetail.getSourcePartRef().isBlank()) query.put("part", musicDetail.getSourcePartRef());
        }
        JsonObject stream = unwrap(requestForPlatform(platform, "GET", path, query, null).data());
        String url = string(stream, "url", "");
        if (url.isBlank()) {
            return MusicResourceInfo.NONE;
        }
        return new MusicResourceInfo(musicDetail.getId(), url, integer(stream, "bitrate", 0),
                longValue(stream, "size", 0L), FormatType.fromSerializedName(
                string(stream, "format", string(stream, "codec", ""))), "", Fee.UNSET,
                integer(stream, "duration_ms", musicDetail.getDurationMillis()), stringMap(stream.get("headers")));
    }

    public List<?> search(String keywords, SearchType searchType, int offset, TuneWeavePlatform platform) {
        String type = switch (searchType) {
            case ALBUM -> "album";
            case ARTIST -> "artist";
            case PLAYLIST -> "playlist";
            default -> platform == TuneWeavePlatform.BILIBILI ? "video" : "track";
        };
        JsonElement data = requestForPlatform(platform, "GET", "/v1/search", Map.of(
                "q", keywords, "type", type, "platform", platform.apiName(),
                "limit", "50", "offset", Integer.toString(Math.max(0, offset))), null).data();
        List<JsonObject> rawItems = new ArrayList<>();
        for (JsonElement item : elements(data)) {
            JsonObject object = unwrap(item);
            if (!object.isEmpty()) rawItems.add(object);
        }
        return switch (searchType) {
            case ALBUM -> rawItems.stream().map(value -> toAlbum(platform, value)).toList();
            case ARTIST -> rawItems.stream().map(value -> toArtist(platform, value)).toList();
            case PLAYLIST -> rawItems.stream().map(value -> toPlaylist(platform, value)).toList();
            default -> rawItems.stream().map(value -> platform == TuneWeavePlatform.BILIBILI
                    ? toVideoTrack(platform, value) : toTrack(platform, value)).toList();
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

    private TuneWeavePlatform cloudPlatform() {
        TuneWeavePlatform platform = defaultPlatform();
        if (platform != TuneWeavePlatform.NETEASE) {
            throw new IllegalArgumentException("Cloud library is only available for NetEase Music accounts");
        }
        return platform;
    }

    private String cloudUserId(TuneWeavePlatform platform) {
        SessionProfile profile = loadSession(platform);
        if (profile.userId() == null || profile.userId().isBlank()) {
            throw new IllegalArgumentException("TuneWeave session did not return a cloud user ID");
        }
        return profile.userId();
    }

    private static String cloudContentType(Path file) throws java.io.IOException {
        String detected = Files.probeContentType(file);
        if (detected != null && !detected.isBlank()) return detected;
        String filename = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (filename.endsWith(".flac")) return "audio/flac";
        if (filename.endsWith(".mp3")) return "audio/mpeg";
        if (filename.endsWith(".m4a") || filename.endsWith(".mp4")) return "audio/mp4";
        if (filename.endsWith(".ogg") || filename.endsWith(".opus")) return "audio/ogg";
        if (filename.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }

    private static String digestFile(Path file, String algorithm) throws java.io.IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (java.io.InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                for (int read; (read = input.read(buffer)) >= 0; ) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String idFromReference(String reference) {
        int separator = reference == null ? -1 : reference.indexOf(':');
        return separator >= 0 && separator + 1 < reference.length()
                ? reference.substring(separator + 1) : Objects.requireNonNullElse(reference, "");
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
        if (!hasCredential(platform)) {
            return;
        }
        requestForPlatform(platform, "DELETE", "/v1/auth/session",
                Map.of("platform", platform.apiName(), "credential_mode", "client"), null);
        clearCredential(platform);
    }

    public void clearCredential(TuneWeavePlatform platform) {
        config.clearTuneWeaveCredential(platform.apiName());
        config.save();
    }

    public TuneWeaveApiClient.TuneWeaveResponse requestForPlatform(
            TuneWeavePlatform platform, String method, String path, Map<String, String> query, JsonElement body) {
        String value = credential(platform);
        List<String> credentials = value.isBlank() ? List.of() : List.of(value);
        return request(method, path, query, body, credentials);
    }

    public TuneWeaveApiClient.TuneWeaveResponse requestWithAllCredentials(
            String method, String path, Map<String, String> query, JsonElement body) {
        List<String> credentials = new ArrayList<>(TuneWeavePlatform.values().length);
        for (TuneWeavePlatform platform : TuneWeavePlatform.values()) {
            String value = credential(platform);
            if (!value.isBlank()) {
                credentials.add(value);
            }
        }
        return request(method, path, query, body, credentials);
    }

    private TuneWeaveApiClient.TuneWeaveResponse requestWithoutCredential(
            String method, String path, Map<String, String> query, JsonElement body) {
        return request(method, path, query, body, List.of());
    }

    private TuneWeaveApiClient.TuneWeaveResponse request(
            String method, String path, Map<String, String> query, JsonElement body, List<String> credentials) {
        return TuneWeaveApiClient.requestAt(
                config.getTuneWeaveBaseUrl(), method, path, query, body, credentials);
    }

    private void saveCredential(TuneWeavePlatform expectedPlatform, JsonElement element) {
        JsonObject credential = object(element);
        String format = requiredString(credential, "format");
        String platform = requiredString(credential, "platform");
        String value = requiredString(credential, "value");
        if (!"tuneweave_credential_v1".equals(format)
                || TuneWeavePlatform.fromApiName(platform) != expectedPlatform
                || !value.startsWith("twc1_")) {
            throw new TuneWeaveApiClient.TuneWeaveException("TuneWeave returned an invalid caller credential", false);
        }
        config.setTuneWeaveCredential(expectedPlatform.apiName(), value);
        config.setDefaultMusicPlatform(expectedPlatform.apiName());
        config.save();
    }

    private static JsonObject clientModeBody(TuneWeavePlatform platform) {
        JsonObject body = new JsonObject();
        body.addProperty("platform", platform.apiName());
        body.addProperty("credential_mode", "client");
        return body;
    }

    private static SessionProfile profile(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        JsonObject value = object(element);
        return new SessionProfile(
                TuneWeavePlatform.fromApiName(requiredString(value, "platform")),
                string(value, "user_id"),
                string(value, "nickname"),
                string(value, "avatar_url"),
                value.has("authenticated") && value.get("authenticated").getAsBoolean()
        );
    }

    private static JsonObject object(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            throw new TuneWeaveApiClient.TuneWeaveException("TuneWeave response is missing an object", false);
        }
        return element.getAsJsonObject();
    }

    private static String requiredString(JsonObject object, String key) {
        String value = string(object, key);
        if (value == null || value.isBlank()) {
            throw new TuneWeaveApiClient.TuneWeaveException("TuneWeave response is missing " + key, false);
        }
        return value;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value instanceof JsonNull || value.isJsonNull() ? null : value.getAsString();
    }

    private static String string(JsonObject object, String key, String fallback) {
        String value = string(object, key);
        return value == null ? fallback : value;
    }

    private static long stableUserId(TuneWeavePlatform platform, String userId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (platform.apiName() + ':' + Objects.requireNonNullElse(userId, ""))
                            .getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong() & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private Playlist toPlaylist(TuneWeavePlatform platform, JsonObject object) {
        String reference = string(object, "ref");
        if (reference == null || reference.isBlank()) {
            reference = string(object, "reference");
        }
        if (reference == null || reference.isBlank()) {
            return Playlist.EMPTY;
        }
        JsonObject creatorObject = object.has("creator") && object.get("creator").isJsonObject()
                ? object.getAsJsonObject("creator") : new JsonObject();
        String creatorRef = string(creatorObject, "ref");
        Profile creator = new Profile(
                string(creatorObject, "name", ""), "", stableId(platform, "user:" + creatorRef), VipType.NORMAL);
        Playlist playlist = Playlist.fromTuneWeave(
                stableId(platform, "playlist:" + reference), reference,
                string(object, "name", reference), string(object, "cover_url", ""),
                integer(object, "track_count", integer(object, "item_count", 0)),
                integer(object, "play_count", 0), creator);
        playlistsById.put(playlist.getId(), playlist);
        return playlist;
    }

    private Album toAlbum(TuneWeavePlatform platform, JsonObject object) {
        String reference = string(object, "ref", string(object, "reference", ""));
        if (reference.isBlank()) return Album.NONE;
        java.util.LinkedHashSet<Artist> artists = new java.util.LinkedHashSet<>();
        JsonElement artistData = object.get("artists");
        if (artistData != null && artistData.isJsonArray()) {
            artistData.getAsJsonArray().forEach(value -> {
                if (value.isJsonObject()) artists.add(toArtist(platform, value.getAsJsonObject()));
            });
        }
        Album album = new Album(stableId(platform, "album:" + reference), string(object, "name", reference),
                string(object, "cover_url", string(object, "pic_url", "")), string(object, "kind", ""),
                string(object, "company", ""), integer(object, "track_count", 0),
                new ObservableSequencedSet<>(), artists, indi.etern.musichud.beans.music.PusherInfo.EMPTY, reference);
        albumsById.put(album.getId(), album);
        return album;
    }

    private Artist toArtist(TuneWeavePlatform platform, JsonObject object) {
        String reference = string(object, "ref", string(object, "reference", ""));
        String name = string(object, "name", reference);
        Artist artist = new Artist(stableId(platform, "artist:" + (reference.isBlank() ? name : reference)), name,
                string(object, "avatar_url", string(object, "cover_url", "")),
                integer(object, "album_count", integer(object, "album_size", 0)),
                integer(object, "music_count", integer(object, "music_size", 0)),
                string(object, "description", ""), new ArrayList<>(),
                integer(object, "total_music_count", integer(object, "music_count", 0)), reference);
        if (!reference.isBlank()) artistsById.put(artist.getId(), artist);
        return artist;
    }

    private MusicDetail toTrack(TuneWeavePlatform platform, JsonObject object) {
        String reference = string(object, "ref", string(object, "reference", ""));
        if (reference.isBlank()) return MusicDetail.NONE;
        List<Artist> artists = new ArrayList<>();
        JsonElement artistData = object.get("artists");
        if (artistData != null && artistData.isJsonArray()) {
            artistData.getAsJsonArray().forEach(value -> {
                if (value.isJsonObject()) artists.add(toArtist(platform, value.getAsJsonObject()));
            });
        }
        Album album = Album.NONE;
        JsonElement albumData = object.get("album");
        if (albumData != null && albumData.isJsonObject()) album = toAlbum(platform, albumData.getAsJsonObject());
        MusicDetail result = MusicDetail.fromTuneWeave(stableId(platform, "track:" + reference), reference,
                "video".equals(string(object, "kind", "track")) ? "video" : "track",
                string(object, "name", string(object, "title", reference)),
                integer(object, "duration_ms", 0), album, artists);
        result.setPusherInfo(indi.etern.musichud.beans.music.PusherInfo.EMPTY);
        tracksById.put(result.getId(), result);
        return result;
    }

    private CloudTrackInfo toCloudTrack(TuneWeavePlatform platform, JsonObject object) {
        String reference = string(object, "ref", "");
        JsonObject trackData = object.has("track") && object.get("track").isJsonObject()
                ? object.getAsJsonObject("track") : object;
        MusicDetail track = toTrack(platform, trackData);
        track.setExtraInfo(new MusicDetail.ExtraInfo(true, 0, false));
        return new CloudTrackInfo(reference, track, string(object, "filename", ""),
                longValue(object, "file_size", 0L), string(object, "file_type", ""),
                longValue(object, "bitrate", 0L), string(object, "md5", ""),
                string(object, "added_at", ""), string(object, "matched_track_ref", ""));
    }

    private MusicDetail toVideoTrack(TuneWeavePlatform platform, JsonObject object) {
        String reference = string(object, "ref", string(object, "reference", ""));
        if (reference.isBlank()) return MusicDetail.NONE;
        List<String> creatorNames = new ArrayList<>();
        JsonElement creatorData = object.get("creators");
        if (creatorData != null && creatorData.isJsonArray()) {
            creatorData.getAsJsonArray().forEach(value -> {
                if (value.isJsonObject()) creatorNames.add(string(value.getAsJsonObject(), "name", ""));
            });
        }
        if (creatorNames.isEmpty()) creatorNames.add(string(object, "uploader", "Bilibili"));
        List<Artist> creators = creatorNames.stream().filter(value -> value != null && !value.isBlank())
                .map(name -> new Artist(stableId(platform, "video-creator:" + name), name,
                        "", 0, 0, "", new ArrayList<>(), 0, ""))
                .toList();
        String title = string(object, "title", string(object, "name", reference));
        Album album = videoAlbum(platform, reference, title, string(object, "cover_url", ""), creators);
        MusicDetail result = MusicDetail.fromTuneWeave(stableId(platform, "video:" + reference), reference,
                "video", title, integer(object, "duration_ms", 0), album, creators);
        result.setPusherInfo(indi.etern.musichud.beans.music.PusherInfo.EMPTY);
        tracksById.put(result.getId(), result);
        return result;
    }

    private static Album videoAlbum(TuneWeavePlatform platform, String reference, String title,
                                    String coverUrl, List<Artist> creators) {
        return new Album(stableId(platform, "video-album:" + reference), title,
                coverUrl == null || coverUrl.isBlank() ? MusicHud.ICON_BASE64 : coverUrl,
                "Video", "Bilibili", 1, new ObservableSequencedSet<>(),
                new java.util.LinkedHashSet<>(creators), indi.etern.musichud.beans.music.PusherInfo.EMPTY, "");
    }

    private VideoInfo toVideoInfo(JsonObject object) {
        String reference = string(object, "ref", "");
        List<String> creators = new ArrayList<>();
        JsonElement creatorData = object.get("creators");
        if (creatorData != null && creatorData.isJsonArray()) {
            creatorData.getAsJsonArray().forEach(value -> {
                if (value.isJsonObject()) creators.add(string(value.getAsJsonObject(), "name", ""));
            });
        }
        JsonObject extensions = object.has("extensions") && object.get("extensions").isJsonObject()
                ? object.getAsJsonObject("extensions") : new JsonObject();
        return new VideoInfo(reference, string(object, "title", reference), string(object, "description", ""),
                string(object, "cover_url", ""), integer(object, "duration_ms", 0),
                string(object, "published_at", ""), longValue(object, "play_count", 0), creators,
                integer(extensions, "part_count", 1));
    }

    private static long stableId(TuneWeavePlatform platform, String value) {
        return stableUserId(platform, value);
    }

    private static List<JsonElement> elements(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return List.of();
        }
        if (value.isJsonArray()) {
            List<JsonElement> result = new ArrayList<>();
            value.getAsJsonArray().forEach(result::add);
            return result;
        }
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            for (String key : List.of("items", "playlists", "results")) {
                JsonElement nested = object.get(key);
                if (nested instanceof JsonArray) {
                    List<JsonElement> result = new ArrayList<>();
                    nested.getAsJsonArray().forEach(result::add);
                    return result;
                }
            }
        }
        return List.of();
    }

    private static JsonObject unwrap(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return new JsonObject();
        }
        JsonObject object = element.getAsJsonObject();
        return object.has("data") && object.get("data").isJsonObject()
                ? object.getAsJsonObject("data") : object;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsBoolean();
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsInt();
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsLong();
    }

    private static Map<String, String> stringMap(JsonElement element) {
        if (element == null || !element.isJsonObject()) return Map.of();
        Map<String, String> result = new java.util.LinkedHashMap<>();
        element.getAsJsonObject().entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonNull()) result.put(entry.getKey(), entry.getValue().getAsString());
        });
        return result;
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

    public record QrSession(TuneWeavePlatform platform, String transactionId, String url,
                            String imageDataUrl, String expiresAt) {
    }

    public record QrPoll(String state, String message, SessionProfile profile) {
        public boolean terminal() {
            return "confirmed".equals(state) || "expired".equals(state) || "failed".equals(state);
        }
    }

    public record ChallengeSession(TuneWeavePlatform platform, String transactionId) {
    }

    public record UniPlaylistInfo(String reference, String name, String description, int itemCount) {
    }

    public record UniImportSource(String platform, String type, String id) {
    }

    public record UniItemInfo(String id, int position, String kind, String sourceRef,
                              String title, List<String> artists) {
        public UniItemInfo {
            artists = artists == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(artists));
        }
    }

    public record VideoInfo(String reference, String title, String description, String coverUrl,
                            int durationMillis, String publishedAt, long playCount,
                            List<String> creators, int partCount) {
        public VideoInfo {
            creators = creators == null ? List.of() : List.copyOf(creators);
        }
    }

    public record VideoPartInfo(String reference, int page, String title, int durationMillis,
                                int width, int height) {
    }

    public record CloudTrackInfo(String reference, MusicDetail track, String filename,
                                 long fileSize, String fileType, long bitrate, String md5,
                                 String addedAt, String matchedTrackReference) {
    }

    public record CloudLibrary(List<CloudTrackInfo> tracks, long total,
                               long storageSize, long storageMaxSize) {
        public CloudLibrary {
            tracks = tracks == null ? List.of() : List.copyOf(tracks);
        }
    }

    public record SessionProfile(TuneWeavePlatform platform, String userId, String nickname,
                                 String avatarUrl, boolean authenticated) {
        public Profile toMusicHudProfile() {
            return new Profile(
                    nickname == null || nickname.isBlank() ? platform.apiName() : nickname,
                    avatarUrl == null ? "" : avatarUrl,
                    stableUserId(platform, userId),
                    VipType.NORMAL
            );
        }
    }
}
