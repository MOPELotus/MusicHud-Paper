package indi.etern.musichud.client.services.tuneweave;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.Lyric;
import indi.etern.musichud.beans.music.LyricInfo;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.server.api.tuneweave.TuneWeaveApiClient;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.bool;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.elements;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.integer;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.longValue;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.object;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.requiredString;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.string;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.unwrap;

/** Owns collection details, track metadata, video metadata, subtitles, and search. */
final class TuneWeaveCatalogService {
    private static final int PAGE_SIZE = 100;

    private final TuneWeaveGateway gateway;
    private final TuneWeaveEntityMapper entities;
    private final TuneWeaveAccountService account;

    TuneWeaveCatalogService(TuneWeaveGateway gateway, TuneWeaveEntityMapper entities,
                            TuneWeaveAccountService account) {
        this.gateway = Objects.requireNonNull(gateway);
        this.entities = Objects.requireNonNull(entities);
        this.account = Objects.requireNonNull(account);
    }

    Playlist loadPlaylistDetail(long id) {
        Playlist cached = entities.playlist(id);
        if (cached == null || cached.getSourceRef().isBlank()) {
            return Playlist.EMPTY;
        }
        return loadPlaylistDetail(cached.getSourceRef());
    }

    Playlist loadPlaylistDetail(String reference) {
        if (reference == null || reference.isBlank()) {
            return Playlist.EMPTY;
        }
        TuneWeavePlatform platform = platform(reference);
        if (platform != TuneWeavePlatform.BILIBILI
                && account.isFavoritePlaylistReference(reference)) {
            return loadFavoritePlaylist(platform, reference);
        }

        Playlist playlist = entities.toPlaylist(platform, unwrap(gateway.requestForPlatform(
                platform, "GET", "/v1/playlists/"
                        + TuneWeaveApiClient.encodePathSegment(reference), Map.of(), null).data()));
        List<MusicDetail> tracks = new ArrayList<>();
        for (JsonElement item : loadAllOffsetPages(platform,
                "/v1/playlists/" + TuneWeaveApiClient.encodePathSegment(reference) + "/items",
                Map.of())) {
            JsonObject raw = unwrap(item);
            MusicDetail track = "video".equals(string(raw, "kind", "track"))
                    ? entities.toVideoTrack(platform, raw) : entities.toTrack(platform, raw);
            if (track != MusicDetail.NONE) {
                tracks.add(track);
            }
        }
        setPlaylistTracks(playlist, tracks);
        entities.cachePlaylist(playlist);
        return playlist;
    }

    Album loadAlbumDetail(long id) {
        Album cached = entities.album(id);
        return cached == null ? Album.NONE : loadAlbumDetail(cached.getSourceRef());
    }

    Album loadAlbumDetail(String reference) {
        if (reference == null || reference.isBlank()) {
            return Album.NONE;
        }
        TuneWeavePlatform platform = platform(reference);
        Album album = entities.toAlbum(platform, unwrap(gateway.requestForPlatform(
                platform, "GET", "/v1/albums/"
                        + TuneWeaveApiClient.encodePathSegment(reference), Map.of(), null).data()));
        List<MusicDetail> tracks = new ArrayList<>();
        for (JsonElement item : loadAllOffsetPages(platform, "/v1/albums/"
                + TuneWeaveApiClient.encodePathSegment(reference) + "/tracks", Map.of())) {
            MusicDetail track = entities.toTrack(platform, unwrap(item));
            if (track != MusicDetail.NONE) {
                tracks.add(track);
            }
        }
        ObservableSequencedSet<MusicDetail> albumTracks = new ObservableSequencedSet<>();
        albumTracks.addAll(tracks);
        album.setMusicDetails(albumTracks);
        return album;
    }

    Artist loadArtistDetail(long id) {
        Artist cached = entities.artist(id);
        return cached == null ? new Artist() : loadArtistDetail(cached.getSourceRef());
    }

    Artist loadArtistDetail(String reference) {
        if (reference == null || reference.isBlank()) {
            return new Artist();
        }
        TuneWeavePlatform platform = platform(reference);
        return entities.toArtist(platform, unwrap(gateway.requestForPlatform(
                platform, "GET", "/v1/artists/"
                        + TuneWeaveApiClient.encodePathSegment(reference), Map.of(), null).data()));
    }

    List<MusicDetail> loadArtistTracks(long id, int offset) {
        Artist cached = entities.artist(id);
        return cached == null ? List.of() : loadArtistTracks(cached.getSourceRef(), offset);
    }

    List<MusicDetail> loadArtistTracks(String reference, int offset) {
        if (reference == null || reference.isBlank()) {
            return List.of();
        }
        TuneWeavePlatform platform = platform(reference);
        List<MusicDetail> result = new ArrayList<>();
        for (JsonElement item : elements(gateway.requestForPlatform(
                platform, "GET", "/v1/artists/"
                        + TuneWeaveApiClient.encodePathSegment(reference) + "/tracks",
                Map.of("limit", "50", "offset", Integer.toString(Math.max(0, offset))), null).data())) {
            MusicDetail track = entities.toTrack(platform, unwrap(item));
            if (track != MusicDetail.NONE) {
                result.add(track);
            }
        }
        return result;
    }

    MusicDetail loadTrackDetail(MusicDetail musicDetail) {
        if (musicDetail == null || musicDetail.getSourceRef().isBlank()
                || "video".equals(musicDetail.getSourceKind())) {
            return musicDetail == null ? MusicDetail.NONE : musicDetail;
        }
        TuneWeavePlatform platform = platform(musicDetail.getSourceRef());
        MusicDetail detail = entities.toTrack(platform, unwrap(gateway.requestForPlatform(
                platform, "GET", "/v1/tracks/"
                        + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef()),
                Map.of(), null).data()));
        entities.cacheTrack(detail);
        return detail;
    }

    TuneWeaveVideo loadVideoDetail(MusicDetail musicDetail) {
        TuneWeaveReference.require(musicDetail == null ? null : musicDetail.getSourceRef(), "video");
        TuneWeavePlatform platform = platform(musicDetail.getSourceRef());
        JsonObject detail = object(gateway.requestForPlatform(
                platform, "GET", "/v1/videos/"
                        + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef()),
                Map.of("type", "video"), null).data());
        JsonObject video = detail.has("video") && detail.get("video").isJsonObject()
                ? detail.getAsJsonObject("video") : detail;
        return entities.toVideoInfo(video);
    }

    MusicDetail loadVideoPlaybackDetail(MusicDetail requested) {
        TuneWeaveVideo video = loadVideoDetail(requested);
        if (!requested.getSourcePartRef().isBlank()) {
            TuneWeaveVideoPart part = loadVideoParts(video.reference()).stream()
                    .filter(candidate -> requested.getSourcePartRef().equals(candidate.reference()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "TuneWeave video part is no longer available"));
            return videoPartTrack(video, part);
        }
        TuneWeavePlatform platform = platform(video.reference());
        List<Artist> creators = video.creators().stream()
                .map(creator -> entities.videoCreatorArtist(platform, creator)).toList();
        Album album = entities.videoAlbum(platform, video.reference(), video.title(),
                video.coverUrl(), creators);
        MusicDetail result = MusicDetail.fromTuneWeave(
                entities.stableId(platform, "video:" + video.reference()),
                video.reference(), "video", video.title(), video.durationMillis(), album, creators);
        result.setPusherInfo(PusherInfo.EMPTY);
        entities.cacheTrack(result);
        return result;
    }

    List<TuneWeaveVideoPart> loadVideoParts(String reference) {
        TuneWeaveReference.require(reference, "video");
        TuneWeavePlatform platform = platform(reference);
        List<TuneWeaveVideoPart> result = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<JsonElement> page = elements(gateway.requestForPlatform(
                    platform, "GET", "/v1/videos/"
                            + TuneWeaveApiClient.encodePathSegment(reference) + "/parts",
                    Map.of("type", "video", "limit", Integer.toString(PAGE_SIZE),
                            "offset", Integer.toString(offset)), null).data());
            for (JsonElement item : page) {
                JsonObject value = unwrap(item);
                result.add(new TuneWeaveVideoPart(requiredString(value, "ref"),
                        integer(value, "page", result.size() + 1),
                        string(value, "title", reference), integer(value, "duration_ms", 0),
                        integer(value, "width", 0), integer(value, "height", 0)));
            }
            offset += page.size();
            if (page.size() < PAGE_SIZE) {
                break;
            }
        }
        return result;
    }

    MusicDetail videoPartTrack(TuneWeaveVideo video, TuneWeaveVideoPart part) {
        TuneWeavePlatform platform = platform(video.reference());
        List<Artist> creators = video.creators().stream()
                .map(creator -> entities.videoCreatorArtist(platform, creator)).toList();
        Album album = entities.videoAlbum(
                platform, video.reference(), video.title(), video.coverUrl(), creators);
        MusicDetail result = MusicDetail.fromTuneWeave(
                entities.stableId(platform,
                        "video-part:" + video.reference() + ':' + part.reference()),
                video.reference(), "video", part.title(), part.durationMillis(), album, creators);
        result.setSourcePartRef(part.reference());
        result.setPusherInfo(PusherInfo.EMPTY);
        entities.cacheTrack(result);
        return result;
    }

    LyricInfo loadLyrics(MusicDetail musicDetail) {
        if (musicDetail == null || musicDetail.getSourceRef().isBlank()
                || "video".equals(musicDetail.getSourceKind())
                || "radio_station".equals(musicDetail.getSourceKind())) {
            return LyricInfo.NONE;
        }
        TuneWeavePlatform platform = platform(musicDetail.getSourceRef());
        JsonObject lyrics;
        if ("podcast_episode".equals(musicDetail.getSourceKind())) {
            JsonObject data = object(gateway.requestForPlatform(
                    platform, "GET", "/v1/episodes/"
                            + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef()) + "/lyrics",
                    Map.of(), null).data());
            lyrics = object(data.get("lyrics"));
        } else {
            lyrics = unwrap(gateway.requestForPlatform(
                    platform, "GET", "/v1/tracks/"
                            + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef()) + "/lyrics",
                    Map.of("word_synced", "true", "translated", "true", "romanized", "true"),
                    null).data());
        }
        JsonObject extensions = object(lyrics.get("extensions"));
        String wordSyncedTranslated = string(extensions, "word_synced_translated",
                string(lyrics, "translated", ""));
        return new LyricInfo(new Lyric(string(lyrics, "plain", "")),
                new Lyric(string(lyrics, "translated", "")),
                new Lyric(string(lyrics, "word_synced", "")),
                new Lyric(wordSyncedTranslated));
    }

    LyricInfo loadVideoLyrics(MusicDetail musicDetail) {
        TuneWeaveReference.require(musicDetail == null ? null : musicDetail.getSourceRef(), "video");
        TuneWeavePlatform platform = platform(musicDetail.getSourceRef());
        String partReference = musicDetail.getSourcePartRef();
        if (partReference == null || partReference.isBlank()) {
            List<JsonElement> parts = elements(gateway.requestForPlatform(
                    platform, "GET", "/v1/videos/"
                            + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef()) + "/parts",
                    Map.of("type", "video", "limit", "1", "offset", "0"), null).data());
            if (parts.isEmpty()) {
                return LyricInfo.NONE;
            }
            partReference = string(unwrap(parts.getFirst()), "ref", "");
            if (partReference.isBlank()) {
                return LyricInfo.NONE;
            }
        }

        String videoPath = "/v1/videos/"
                + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef());
        Map<String, String> query = Map.of("type", "video", "part", partReference);
        JsonObject catalog = object(gateway.requestForPlatform(
                platform, "GET", videoPath + "/subtitles", query, null).data());
        JsonObject selected = selectSubtitle(catalog);
        if (selected == null) {
            return LyricInfo.NONE;
        }

        JsonObject document = object(gateway.requestForPlatform(
                platform, "GET", videoPath + "/subtitles/"
                        + TuneWeaveApiClient.encodePathSegment(string(selected, "ref", "")),
                query, null).data());
        StringBuilder lrc = new StringBuilder();
        for (JsonElement element : elements(document.get("cues"))) {
            JsonObject cue = unwrap(element);
            String text = string(cue, "text", "").replace('\r', ' ').replace('\n', ' ').trim();
            if (text.isBlank()) {
                continue;
            }
            long startMillis = Math.max(0L, longValue(cue, "start_ms", 0L));
            long minutes = startMillis / 60_000L;
            long seconds = (startMillis / 1_000L) % 60L;
            long millis = startMillis % 1_000L;
            lrc.append(String.format(Locale.ROOT,
                    "[%02d:%02d.%03d]%s%n", minutes, seconds, millis, text));
        }
        return lrc.isEmpty()
                ? LyricInfo.NONE
                : new LyricInfo(new Lyric(lrc.toString()), Lyric.NONE, Lyric.NONE, Lyric.NONE);
    }

    List<?> search(String keywords, SearchType searchType, int offset,
                   TuneWeavePlatform platform) {
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
            query.put("order", "relevance");
        }

        List<JsonObject> rawItems = new ArrayList<>();
        for (JsonElement item : elements(gateway.requestForPlatform(
                platform, "GET", "/v1/search", query, null).data())) {
            JsonObject value = unwrap(item);
            if (!value.isEmpty()) {
                rawItems.add(value);
            }
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

    private Playlist loadFavoritePlaylist(TuneWeavePlatform platform, String reference) {
        Playlist playlist = entities.playlists().stream()
                .filter(value -> reference.equals(value.getSourceRef()))
                .findFirst()
                .orElse(Playlist.EMPTY);
        List<MusicDetail> tracks = new ArrayList<>();
        for (JsonElement item : loadAllOffsetPages(platform, "/v1/account/favorites/tracks",
                Map.of("platform", platform.apiName()))) {
            MusicDetail track = entities.toTrack(platform, unwrap(item));
            if (track != MusicDetail.NONE) {
                tracks.add(track);
            }
        }
        setPlaylistTracks(playlist, tracks);
        entities.cachePlaylist(playlist);
        return playlist;
    }

    private List<JsonElement> loadAllOffsetPages(TuneWeavePlatform platform, String path,
                                                  Map<String, String> baseQuery) {
        List<JsonElement> result = new ArrayList<>();
        int offset = 0;
        while (true) {
            Map<String, String> query = new LinkedHashMap<>(baseQuery);
            query.put("limit", Integer.toString(PAGE_SIZE));
            query.put("offset", Integer.toString(offset));
            TuneWeaveApiClient.TuneWeaveResponse response =
                    gateway.requestForPlatform(platform, "GET", path, query, null);
            List<JsonElement> page = elements(response.data());
            result.addAll(page);
            JsonObject pagination = object(response.meta().get("pagination"));
            if (!bool(pagination, "has_more", false) || page.isEmpty()) {
                break;
            }
            int nextOffset = integer(pagination, "next_offset", offset + page.size());
            if (nextOffset <= offset) {
                throw new TuneWeaveApiClient.TuneWeaveException(
                        "TuneWeave returned an invalid pagination offset for " + path, false);
            }
            offset = nextOffset;
        }
        return result;
    }

    private JsonObject selectSubtitle(JsonObject catalog) {
        String defaultLanguage = string(catalog, "default_language", "");
        JsonObject selected = null;
        for (JsonElement element : elements(catalog.get("items"))) {
            JsonObject candidate = unwrap(element);
            if (string(candidate, "ref", "").isBlank()) {
                continue;
            }
            if (selected == null || (!defaultLanguage.isBlank()
                    && defaultLanguage.equals(string(candidate, "language", "")))) {
                selected = candidate;
            }
            if (!defaultLanguage.isBlank()
                    && defaultLanguage.equals(string(candidate, "language", ""))) {
                break;
            }
        }
        return selected;
    }

    private TuneWeavePlatform platform(String reference) {
        return TuneWeaveReference.platformOrDefault(reference, gateway.defaultPlatform());
    }

    private static void setPlaylistTracks(Playlist playlist, List<MusicDetail> tracks) {
        ObservableSequencedSet<MusicDetail> playlistTracks = new ObservableSequencedSet<>();
        playlistTracks.addAll(tracks);
        playlist.setTracks(playlistTracks);
        playlist.setMusicTrackCount(tracks.size());
    }
}
