package indi.etern.musichud.client.services.tuneweave;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.VipType;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.*;

/** Converts TuneWeave snapshots to Music HUD domain entities and owns their identity cache. */
final class TuneWeaveEntityMapper {
    private final Map<Long, MusicDetail> tracks = new ConcurrentHashMap<>();
    private final Map<Long, Playlist> playlists = new ConcurrentHashMap<>();
    private final Map<Long, Album> albums = new ConcurrentHashMap<>();
    private final Map<Long, Artist> artists = new ConcurrentHashMap<>();
    private final Function<TuneWeavePlatform, TuneWeaveSession> sessionLookup;

    TuneWeaveEntityMapper(Function<TuneWeavePlatform, TuneWeaveSession> sessionLookup) {
        this.sessionLookup = Objects.requireNonNull(sessionLookup);
    }

    boolean hasPlaylist(long id) {
        return playlists.containsKey(id);
    }

    boolean hasAlbum(long id) {
        return albums.containsKey(id);
    }

    boolean hasArtist(long id) {
        return artists.containsKey(id);
    }

    Playlist playlist(long id) {
        return playlists.get(id);
    }

    Collection<Playlist> playlists() {
        return playlists.values();
    }

    Album album(long id) {
        return albums.get(id);
    }

    Artist artist(long id) {
        return artists.get(id);
    }

    void cacheTrack(MusicDetail track) {
        if (track != null && track != MusicDetail.NONE) tracks.put(track.getId(), track);
    }

    void cachePlaylist(Playlist playlist) {
        if (playlist != null && playlist != Playlist.EMPTY) playlists.put(playlist.getId(), playlist);
    }

    void removePlaylist(long id) {
        playlists.remove(id);
    }

    Playlist toPlaylist(TuneWeavePlatform platform, JsonObject object) {
        String reference = string(object, "ref");
        if (reference == null || reference.isBlank()) reference = string(object, "reference");
        if (reference == null || reference.isBlank()) return Playlist.EMPTY;

        JsonObject creatorObject = object.has("creator") && object.get("creator").isJsonObject()
                ? object.getAsJsonObject("creator") : new JsonObject();
        String creatorRef = string(creatorObject, "ref");
        TuneWeaveSession session = sessionLookup.apply(platform);
        String creatorUserId = TuneWeaveIdentity.userIdFromReference(platform, creatorRef);
        boolean currentUser = session != null && (creatorObject.isEmpty()
                || creatorUserId.equals(TuneWeaveIdentity.userIdFromReference(platform, session.userId())));
        Profile creator = currentUser
                ? session.toMusicHudProfile()
                : new Profile(string(creatorObject, "name", ""), "",
                stableId(platform, creatorUserId), VipType.NORMAL);
        String cover = imageUrl(object, "cover_url", "pic_url", "cover");
        Playlist playlist = Playlist.fromTuneWeave(
                stableId(platform, "playlist:" + reference), reference,
                string(object, "name", reference), cover.isBlank() ? MusicHud.ICON_BASE64 : cover,
                integer(object, "track_count", integer(object, "item_count", 0)),
                integer(object, "play_count", 0), creator);
        cachePlaylist(playlist);
        return playlist;
    }

    Album toAlbum(TuneWeavePlatform platform, JsonObject object) {
        String reference = string(object, "ref", string(object, "reference", ""));
        if (reference.isBlank()) return Album.NONE;
        LinkedHashSet<Artist> albumArtists = new LinkedHashSet<>();
        JsonElement artistData = object.get("artists");
        if (artistData != null && artistData.isJsonArray()) {
            artistData.getAsJsonArray().forEach(value -> {
                if (value.isJsonObject()) albumArtists.add(toArtist(platform, value.getAsJsonObject()));
            });
        }
        Album album = new Album(stableId(platform, "album:" + reference),
                string(object, "name", reference),
                imageUrl(object, "cover_url", "pic_url", "cover_pic_url", "pic"),
                string(object, "kind", ""), string(object, "company", ""),
                integer(object, "track_count", 0), new ObservableSequencedSet<>(), albumArtists,
                PusherInfo.EMPTY, reference);
        albums.put(album.getId(), album);
        return album;
    }

    Artist toArtist(TuneWeavePlatform platform, JsonObject object) {
        String reference = string(object, "ref", string(object, "reference", ""));
        String name = string(object, "name", reference);
        Artist artist = new Artist(stableId(platform,
                "artist:" + (reference.isBlank() ? name : reference)), name,
                string(object, "avatar_url", string(object, "cover_url", "")),
                integer(object, "album_count", integer(object, "album_size", 0)),
                integer(object, "music_count", integer(object, "music_size", 0)),
                string(object, "description", ""), new ArrayList<>(),
                integer(object, "total_music_count", integer(object, "music_count", 0)), reference);
        if (!reference.isBlank()) artists.put(artist.getId(), artist);
        return artist;
    }

    MusicDetail toTrack(TuneWeavePlatform platform, JsonObject input) {
        JsonObject object = mergeSnapshot(input);
        String reference = string(object, "ref", string(object, "reference", ""));
        if (reference.isBlank()) return MusicDetail.NONE;

        List<Artist> trackArtists = new ArrayList<>();
        JsonElement artistData = object.get("artists");
        if (artistData != null && artistData.isJsonArray()) {
            artistData.getAsJsonArray().forEach(value -> {
                if (value.isJsonObject()) {
                    trackArtists.add(toArtist(platform, value.getAsJsonObject()));
                } else if (value.isJsonPrimitive()) {
                    String name = value.getAsString();
                    if (!name.isBlank()) {
                        trackArtists.add(new Artist(stableId(platform, "artist-name:" + name), name,
                                "", 0, 0, "", new ArrayList<>(), 0, ""));
                    }
                }
            });
        }

        Album album = Album.NONE;
        JsonElement albumData = object.get("album");
        if (albumData != null && albumData.isJsonObject()) {
            album = toAlbum(platform, albumData.getAsJsonObject());
        }
        if (album == Album.NONE) {
            String cover = imageUrl(object, "cover_url", "pic_url", "cover_pic_url", "pic");
            String albumName = albumData != null && albumData.isJsonPrimitive()
                    ? albumData.getAsString()
                    : string(object, "album_name", string(object, "name", reference));
            if (!cover.isBlank() || !albumName.isBlank()) {
                album = new Album(stableId(platform, "track-album:" + reference), albumName,
                        cover.isBlank() ? MusicHud.ICON_BASE64 : cover, "", "", 0,
                        new ObservableSequencedSet<>(), new LinkedHashSet<>(trackArtists),
                        PusherInfo.EMPTY, "");
            }
        }

        MusicDetail result = MusicDetail.fromTuneWeave(stableId(platform, "track:" + reference),
                reference, "video".equals(string(object, "kind", "track")) ? "video" : "track",
                string(object, "name", string(object, "title", reference)),
                integer(object, "duration_ms", 0), album, trackArtists);
        result.setPusherInfo(PusherInfo.EMPTY);
        cacheTrack(result);
        return result;
    }

    TuneWeaveClientService.CloudTrackInfo toCloudTrack(
            TuneWeavePlatform platform, JsonObject object) {
        String reference = string(object, "ref", "");
        JsonObject trackData = object.has("track") && object.get("track").isJsonObject()
                ? object.getAsJsonObject("track") : object;
        MusicDetail track = toTrack(platform, trackData);
        track.setExtraInfo(new MusicDetail.ExtraInfo(true, 0, false));
        return new TuneWeaveClientService.CloudTrackInfo(reference, track,
                string(object, "filename", ""), longValue(object, "file_size", 0L),
                string(object, "file_type", ""), longValue(object, "bitrate", 0L),
                string(object, "md5", ""), string(object, "added_at", ""),
                string(object, "matched_track_ref", ""));
    }

    TuneWeaveClientService.PodcastInfo toPodcast(
            TuneWeavePlatform platform, JsonObject object) {
        String reference = string(object, "ref", string(object, "reference", ""));
        JsonObject creator = unwrap(object.get("creator"));
        return new TuneWeaveClientService.PodcastInfo(reference, string(object, "name", reference),
                string(object, "description", ""), string(object, "cover_url", ""),
                string(creator, "name", ""), string(object, "category", ""),
                string(object, "secondary_category", ""), longValue(object, "episode_count", 0),
                longValue(object, "subscriber_count", 0), longValue(object, "play_count", 0),
                bool(object, "subscribed", false));
    }

    TuneWeaveClientService.PodcastEpisodeInfo toPodcastEpisode(
            TuneWeavePlatform platform, JsonObject object) {
        String reference = string(object, "ref", string(object, "reference", ""));
        JsonObject creator = unwrap(object.get("creator"));
        JsonObject audio = unwrap(object.get("audio"));
        return new TuneWeaveClientService.PodcastEpisodeInfo(reference,
                referenceValue(object.get("podcast_ref")), string(object, "name", reference),
                string(object, "description", ""), string(object, "cover_url", ""),
                string(creator, "name", ""), string(audio, "ref", ""),
                integer(object, "duration_ms", integer(audio, "duration_ms", 0)),
                string(object, "published_at", ""), longValue(object, "serial_number", 0),
                bool(object, "has_lyrics", false));
    }

    List<TuneWeaveClientService.RadioOptionInfo> toRadioOptions(JsonElement element) {
        List<TuneWeaveClientService.RadioOptionInfo> result = new ArrayList<>();
        for (JsonElement value : elements(element)) {
            JsonObject option = unwrap(value);
            String id = string(option, "id", "");
            if (!id.isBlank()) {
                result.add(new TuneWeaveClientService.RadioOptionInfo(id, string(option, "name", id)));
            }
        }
        return result;
    }

    TuneWeaveClientService.RadioStationInfo toRadioStation(
            TuneWeavePlatform platform, JsonObject object, String fallbackCategory) {
        String reference = string(object, "ref", string(object, "reference", ""));
        return new TuneWeaveClientService.RadioStationInfo(reference,
                string(object, "name", reference), string(object, "description", ""),
                string(object, "cover_url", ""), string(object, "category", fallbackCategory),
                string(object, "region", ""), string(object, "current_program", ""),
                bool(object, "subscribed", false));
    }

    MusicDetail toVideoTrack(TuneWeavePlatform platform, JsonObject object) {
        String reference = string(object, "ref", string(object, "reference", ""));
        if (reference.isBlank()) return MusicDetail.NONE;
        JsonObject snapshot = object.has("snapshot") && object.get("snapshot").isJsonObject()
                ? object.getAsJsonObject("snapshot") : new JsonObject();
        List<Artist> creators = videoCreatorInfos(object, snapshot).stream()
                .map(creator -> videoCreatorArtist(platform, creator)).toList();
        String title = string(object, "title",
                string(object, "name", string(snapshot, "title", reference)));
        String coverUrl = string(object, "cover_url", string(snapshot, "cover_url", ""));
        Album album = videoAlbum(platform, reference, title, coverUrl, creators);
        MusicDetail result = MusicDetail.fromTuneWeave(stableId(platform, "video:" + reference),
                reference, "video", title,
                integer(object, "duration_ms", integer(snapshot, "duration_ms", 0)), album, creators);
        result.setPusherInfo(PusherInfo.EMPTY);
        cacheTrack(result);
        return result;
    }

    TuneWeaveClientService.VideoInfo toVideoInfo(JsonObject object) {
        String reference = string(object, "ref", "");
        List<TuneWeaveClientService.VideoCreatorInfo> creators =
                videoCreatorInfos(object, new JsonObject());
        JsonObject extensions = object.has("extensions") && object.get("extensions").isJsonObject()
                ? object.getAsJsonObject("extensions") : new JsonObject();
        return new TuneWeaveClientService.VideoInfo(reference, string(object, "title", reference),
                string(object, "description", ""), string(object, "cover_url", ""),
                integer(object, "duration_ms", 0), string(object, "published_at", ""),
                longValue(object, "play_count", 0), creators, integer(extensions, "part_count", 1));
    }

    List<TuneWeaveClientService.VideoCreatorInfo> videoCreatorInfos(
            JsonObject object, JsonObject snapshot) {
        LinkedHashMap<String, TuneWeaveClientService.VideoCreatorInfo> creators = new LinkedHashMap<>();
        JsonElement creatorData = object.get("creators");
        if (creatorData != null && creatorData.isJsonArray()) {
            creatorData.getAsJsonArray().forEach(value -> {
                if (value.isJsonObject()) {
                    JsonObject creator = value.getAsJsonObject();
                    addVideoCreator(creators, string(creator, "ref", ""),
                            string(creator, "name", ""), string(creator, "avatar_url", ""));
                } else if (value.isJsonPrimitive()) {
                    addVideoCreator(creators, "", value.getAsString(), "");
                }
            });
        }
        if (creators.isEmpty()) {
            for (String key : List.of("creator", "owner")) {
                JsonElement value = object.get(key);
                if (value != null && value.isJsonObject()) {
                    JsonObject creator = value.getAsJsonObject();
                    addVideoCreator(creators, string(creator, "ref", ""),
                            string(creator, "name", ""), string(creator, "avatar_url", ""));
                }
            }
        }
        if (creators.isEmpty()) {
            String uploader = string(object, "uploader", "");
            if (!uploader.isBlank()) addVideoCreator(creators, "", uploader, "");
        }
        if (creators.isEmpty() && snapshot.has("artists") && snapshot.get("artists").isJsonArray()) {
            snapshot.getAsJsonArray("artists").forEach(value -> {
                if (value.isJsonPrimitive()) addVideoCreator(creators, "", value.getAsString(), "");
            });
        }
        return List.copyOf(creators.values());
    }

    Artist videoCreatorArtist(
            TuneWeavePlatform platform, TuneWeaveClientService.VideoCreatorInfo creator) {
        String identity = creator.reference().isBlank() ? creator.name() : creator.reference();
        return new Artist(stableId(platform, "video-creator:" + identity), creator.name(),
                creator.avatarUrl(), 0, 0, "", new ArrayList<>(), 0, creator.reference());
    }

    Album videoAlbum(TuneWeavePlatform platform, String reference, String title,
                     String coverUrl, List<Artist> creators) {
        return new Album(stableId(platform, "video-album:" + reference), title,
                coverUrl == null || coverUrl.isBlank() ? MusicHud.ICON_BASE64 : coverUrl,
                "Video", "Bilibili", 1, new ObservableSequencedSet<>(),
                new LinkedHashSet<>(creators), PusherInfo.EMPTY, "");
    }

    long stableId(TuneWeavePlatform platform, String value) {
        return TuneWeaveIdentity.stableId(platform, value);
    }

    private static void addVideoCreator(
            Map<String, TuneWeaveClientService.VideoCreatorInfo> creators,
            String reference, String name, String avatarUrl) {
        if (name == null || name.isBlank()) return;
        String identity = reference == null || reference.isBlank() ? "name:" + name : reference;
        creators.putIfAbsent(identity, new TuneWeaveClientService.VideoCreatorInfo(
                Objects.requireNonNullElse(reference, ""), name,
                Objects.requireNonNullElse(avatarUrl, "")));
    }
}
