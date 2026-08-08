package indi.etern.musichud.client.services.tuneweave;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.server.api.tuneweave.TuneWeaveApiClient;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.bool;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.elements;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.integer;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.object;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.string;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.unwrap;

/** Owns podcast and radio discovery, library operations, and playable track conversion. */
final class TuneWeaveProgramService {
    private final TuneWeaveGateway gateway;
    private final TuneWeaveEntityMapper entities;

    TuneWeaveProgramService(TuneWeaveGateway gateway, TuneWeaveEntityMapper entities) {
        this.gateway = Objects.requireNonNull(gateway);
        this.entities = Objects.requireNonNull(entities);
    }

    List<TuneWeavePodcastCategory> loadPodcastCategories(TuneWeavePlatform platform) {
        JsonObject data = object(gateway.requestForPlatform(
                platform, "GET", "/v1/podcasts/categories",
                Map.of("platform", platform.apiName(), "kind", "all"), null).data());
        List<TuneWeavePodcastCategory> result = new ArrayList<>();
        for (JsonElement value : elements(data.get("categories"))) {
            JsonObject category = unwrap(value);
            String id = string(category, "id", "");
            if (!id.isBlank()) {
                result.add(new TuneWeavePodcastCategory(id, string(category, "name", id),
                        string(category, "icon_url", "")));
            }
        }
        return result;
    }

    List<TuneWeavePodcast> loadPodcasts(TuneWeavePlatform platform, String categoryId) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("platform", platform.apiName());
        query.put("catalog", categoryId == null || categoryId.isBlank()
                ? "featured" : "category_featured");
        if (categoryId != null && !categoryId.isBlank()) {
            query.put("category_id", categoryId.trim());
        }
        return loadPodcastPages(platform, "/v1/podcasts", query);
    }

    List<TuneWeavePodcast> loadAccountPodcasts(TuneWeavePlatform platform) {
        return loadPodcastPages(platform, "/v1/account/library/podcasts",
                new LinkedHashMap<>(Map.of("platform", platform.apiName())));
    }

    TuneWeavePodcast loadPodcastDetail(String reference) {
        TuneWeaveReference.require(reference, "podcast");
        TuneWeavePlatform platform = TuneWeaveReference.platform(reference);
        return entities.toPodcast(platform, unwrap(gateway.requestForPlatform(
                platform, "GET", "/v1/podcasts/"
                        + TuneWeaveApiClient.encodePathSegment(reference), Map.of(), null).data()));
    }

    List<TuneWeavePodcastEpisode> loadPodcastEpisodes(TuneWeavePodcast podcast) {
        TuneWeaveReference.require(podcast == null ? null : podcast.reference(), "podcast");
        TuneWeavePlatform platform = TuneWeaveReference.platform(podcast.reference());
        List<TuneWeavePodcastEpisode> result = new ArrayList<>();
        int offset = 0;
        while (true) {
            TuneWeaveApiClient.TuneWeaveResponse response = gateway.requestForPlatform(
                    platform, "GET", "/v1/podcasts/"
                            + TuneWeaveApiClient.encodePathSegment(podcast.reference()) + "/episodes",
                    Map.of("limit", "100", "offset", Integer.toString(offset),
                            "ascending", "false"), null);
            List<JsonElement> page = elements(response.data());
            for (JsonElement value : page) {
                TuneWeavePodcastEpisode episode = entities.toPodcastEpisode(platform, unwrap(value));
                if (!episode.reference().isBlank()) {
                    result.add(episode);
                }
            }
            JsonObject pagination = object(response.meta().get("pagination"));
            boolean hasMore = bool(pagination, "has_more", page.size() == 100);
            int nextOffset = integer(pagination, "next_offset", offset + page.size());
            if (!hasMore || page.isEmpty() || nextOffset <= offset) {
                break;
            }
            offset = nextOffset;
        }
        return result;
    }

    TuneWeavePodcastEpisode loadPodcastEpisodeDetail(String reference) {
        TuneWeaveReference.require(reference, "podcast episode");
        TuneWeavePlatform platform = TuneWeaveReference.platform(reference);
        return entities.toPodcastEpisode(platform, unwrap(gateway.requestForPlatform(
                platform, "GET", "/v1/episodes/"
                        + TuneWeaveApiClient.encodePathSegment(reference), Map.of(), null).data()));
    }

    MusicDetail podcastEpisodeTrack(TuneWeavePodcast podcast, TuneWeavePodcastEpisode episode) {
        TuneWeavePlatform platform = TuneWeaveReference.platform(episode.reference());
        String creatorName = !episode.creatorName().isBlank()
                ? episode.creatorName() : podcast.creatorName();
        List<Artist> creators = creatorName.isBlank() ? List.of() : List.of(new Artist(
                entities.stableId(platform, "podcast-creator:" + creatorName), creatorName,
                "", 0, 0, "", new ArrayList<>(), 0, ""));
        String cover = episode.coverUrl().isBlank() ? podcast.coverUrl() : episode.coverUrl();
        Album album = new Album(entities.stableId(platform, "podcast:" + podcast.reference()),
                podcast.name(), cover.isBlank() ? MusicHud.ICON_BASE64 : cover,
                "Podcast", "", 0, new ObservableSequencedSet<>(),
                new LinkedHashSet<>(creators), PusherInfo.EMPTY, "");
        MusicDetail track = MusicDetail.fromTuneWeave(
                entities.stableId(platform, "podcast-episode:" + episode.reference()),
                episode.reference(), "podcast_episode", episode.name(),
                episode.durationMillis(), album, creators);
        track.setPusherInfo(PusherInfo.EMPTY);
        entities.cacheTrack(track);
        return track;
    }

    MusicDetail loadPlaybackDetail(MusicDetail requested) {
        if (requested == null) {
            return MusicDetail.NONE;
        }
        if ("podcast_episode".equals(requested.getSourceKind())) {
            TuneWeavePodcastEpisode episode = loadPodcastEpisodeDetail(requested.getSourceRef());
            TuneWeavePlatform platform = TuneWeaveReference.platform(episode.reference());
            MusicDetail track = MusicDetail.fromTuneWeave(
                    entities.stableId(platform, "podcast-episode:" + episode.reference()),
                    episode.reference(), "podcast_episode", episode.name(),
                    episode.durationMillis(), requested.getAlbum(), requested.getArtists());
            track.setPusherInfo(PusherInfo.EMPTY);
            entities.cacheTrack(track);
            return track;
        }
        if ("radio_station".equals(requested.getSourceKind())) {
            TuneWeaveRadioStation station = loadRadioStationDetail(requested.getSourceRef());
            return loadRadioPlaybackQueue(station).stream()
                    .filter(track -> track.getId() == requested.getId()
                            && track.getSourcePartRef().equals(requested.getSourcePartRef()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "TuneWeave radio item is no longer available"));
        }
        throw new IllegalArgumentException(
                "Unsupported TuneWeave program kind: " + requested.getSourceKind());
    }

    void setPodcastSubscribed(TuneWeavePodcast podcast, boolean subscribed) {
        TuneWeaveReference.require(podcast == null ? null : podcast.reference(), "podcast");
        TuneWeavePlatform platform = TuneWeaveReference.platform(podcast.reference());
        gateway.requestForPlatform(platform, subscribed ? "PUT" : "DELETE",
                "/v1/account/library/podcasts/"
                        + TuneWeaveApiClient.encodePathSegment(podcast.reference()), Map.of(), null);
    }

    TuneWeaveRadioTaxonomy loadRadioTaxonomy(TuneWeavePlatform platform) {
        JsonObject data = object(gateway.requestForPlatform(
                platform, "GET", "/v1/radio/taxonomy",
                Map.of("platform", platform.apiName()), null).data());
        return new TuneWeaveRadioTaxonomy(entities.toRadioOptions(data.get("categories")),
                entities.toRadioOptions(data.get("regions")));
    }

    List<TuneWeaveRadioStation> loadRadioStations(TuneWeavePlatform platform,
                                                  String categoryId, String regionId) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("platform", platform.apiName());
        if (categoryId != null && !categoryId.isBlank()) {
            query.put("category_id", categoryId.trim());
        }
        if (regionId != null && !regionId.isBlank()) {
            query.put("region_id", regionId.trim());
        }
        return loadRadioStationCatalog(platform, query);
    }

    List<TuneWeaveRadioStation> loadStyledRadioStations(TuneWeavePlatform platform) {
        JsonObject data = object(gateway.requestForPlatform(
                platform, "GET", "/v1/radio/styles",
                Map.of("platform", platform.apiName(), "sources", "0,1,2"), null).data());
        LinkedHashMap<String, TuneWeaveRadioStation> result = new LinkedHashMap<>();
        for (JsonElement sourceValue : elements(data.get("sources"))) {
            JsonObject source = unwrap(sourceValue);
            for (JsonElement styleValue : elements(source.get("styles"))) {
                JsonObject style = unwrap(styleValue);
                String styleName = string(style, "localized_name", string(style, "name", ""));
                for (JsonElement channelValue : elements(style.get("channels"))) {
                    TuneWeaveRadioStation station = entities.toRadioStation(
                            platform, unwrap(channelValue), styleName);
                    if (!station.reference().isBlank()) {
                        result.putIfAbsent(station.reference(), station);
                    }
                }
            }
        }
        return List.copyOf(result.values());
    }

    List<TuneWeaveRadioStation> loadAccountRadioStations(TuneWeavePlatform platform) {
        LinkedHashMap<String, TuneWeaveRadioStation> result = new LinkedHashMap<>();
        for (String catalog : List.of("broadcast", "styled")) {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("platform", platform.apiName());
            query.put("catalog", catalog);
            if ("styled".equals(catalog)) {
                query.put("sources", "0,1,2");
            }
            for (TuneWeaveRadioStation station : loadRadioStationPages(
                    platform, "/v1/account/library/radio-stations", query)) {
                result.putIfAbsent(station.reference(), station);
            }
        }
        return List.copyOf(result.values());
    }

    TuneWeaveRadioStation loadRadioStationDetail(String reference) {
        TuneWeaveReference.require(reference, "radio station");
        TuneWeavePlatform platform = TuneWeaveReference.platform(reference);
        return entities.toRadioStation(platform, unwrap(gateway.requestForPlatform(
                platform, "GET", "/v1/radio/stations/"
                        + TuneWeaveApiClient.encodePathSegment(reference), Map.of(), null).data()), "");
    }

    List<MusicDetail> loadRadioPlaybackQueue(TuneWeaveRadioStation station) {
        TuneWeaveReference.require(station == null ? null : station.reference(), "radio station");
        TuneWeavePlatform platform = TuneWeaveReference.platform(station.reference());
        if (!TuneWeaveReference.isStyledRadio(station.reference())) {
            return List.of(liveRadioTrack(platform, station));
        }

        JsonObject data = object(gateway.requestForPlatform(
                platform, "GET", "/v1/radio/stations/"
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
            Album album = radioAlbum(platform, station, cover, artists);
            String identity = itemReference.isBlank()
                    ? station.reference() + ':' + title : itemReference;
            MusicDetail track = MusicDetail.fromTuneWeave(
                    entities.stableId(platform, "radio-item:" + identity),
                    station.reference(), "radio_station", title,
                    integer(item, "duration_ms", 0), album, artists);
            track.setSourcePartRef(itemReference);
            track.setPusherInfo(PusherInfo.EMPTY);
            entities.cacheTrack(track);
            result.add(track);
        }
        return result;
    }

    void setRadioStationSubscribed(TuneWeaveRadioStation station, boolean subscribed) {
        TuneWeaveReference.require(station == null ? null : station.reference(), "radio station");
        TuneWeavePlatform platform = TuneWeaveReference.platform(station.reference());
        gateway.requestForPlatform(platform, subscribed ? "PUT" : "DELETE",
                "/v1/account/library/radio-stations/"
                        + TuneWeaveApiClient.encodePathSegment(station.reference()), Map.of(), null);
    }

    private List<TuneWeavePodcast> loadPodcastPages(TuneWeavePlatform platform, String path,
                                                     Map<String, String> baseQuery) {
        List<TuneWeavePodcast> result = new ArrayList<>();
        int offset = 0;
        while (true) {
            Map<String, String> query = new LinkedHashMap<>(baseQuery);
            query.put("limit", "100");
            query.put("offset", Integer.toString(offset));
            TuneWeaveApiClient.TuneWeaveResponse response =
                    gateway.requestForPlatform(platform, "GET", path, query, null);
            List<JsonElement> page = elements(response.data());
            for (JsonElement value : page) {
                TuneWeavePodcast podcast = entities.toPodcast(platform, unwrap(value));
                if (!podcast.reference().isBlank()) {
                    result.add(podcast);
                }
            }
            JsonObject pagination = object(response.meta().get("pagination"));
            boolean hasMore = bool(pagination, "has_more", page.size() == 100);
            JsonElement nextOffsetValue = pagination.get("next_offset");
            if (!hasMore || page.isEmpty() || nextOffsetValue == null || nextOffsetValue.isJsonNull()) {
                break;
            }
            int nextOffset = nextOffsetValue.getAsInt();
            if (nextOffset <= offset) {
                break;
            }
            offset = nextOffset;
        }
        return result;
    }

    private List<TuneWeaveRadioStation> loadRadioStationCatalog(
            TuneWeavePlatform platform, Map<String, String> baseQuery) {
        LinkedHashMap<String, TuneWeaveRadioStation> result = new LinkedHashMap<>();
        Map<String, String> cursor = Map.of();
        String previousCursor = "";
        while (true) {
            Map<String, String> query = new LinkedHashMap<>(baseQuery);
            query.put("limit", "100");
            query.putAll(cursor);
            TuneWeaveApiClient.TuneWeaveResponse response = gateway.requestForPlatform(
                    platform, "GET", "/v1/radio/stations", query, null);
            List<JsonElement> page = elements(response.data());
            for (JsonElement value : page) {
                TuneWeaveRadioStation station = entities.toRadioStation(platform, unwrap(value), "");
                if (!station.reference().isBlank()) {
                    result.putIfAbsent(station.reference(), station);
                }
            }
            JsonObject pagination = object(response.meta().get("pagination"));
            if (!bool(pagination, "has_more", false) || page.isEmpty()) {
                break;
            }
            JsonObject nextCursor = unwrap(unwrap(pagination.get("extensions")).get("next_cursor"));
            String lastId = string(nextCursor, "id", "");
            String score = string(nextCursor, "score", "");
            String cursorKey = lastId + ':' + score;
            if (lastId.isBlank() || score.isBlank() || cursorKey.equals(previousCursor)) {
                break;
            }
            previousCursor = cursorKey;
            cursor = Map.of("last_id", lastId, "score", score);
        }
        return List.copyOf(result.values());
    }

    private List<TuneWeaveRadioStation> loadRadioStationPages(
            TuneWeavePlatform platform, String path, Map<String, String> baseQuery) {
        List<TuneWeaveRadioStation> result = new ArrayList<>();
        int offset = 0;
        while (true) {
            Map<String, String> query = new LinkedHashMap<>(baseQuery);
            query.put("limit", "100");
            query.put("offset", Integer.toString(offset));
            TuneWeaveApiClient.TuneWeaveResponse response =
                    gateway.requestForPlatform(platform, "GET", path, query, null);
            List<JsonElement> page = elements(response.data());
            for (JsonElement value : page) {
                TuneWeaveRadioStation station = entities.toRadioStation(platform, unwrap(value), "");
                if (!station.reference().isBlank()) {
                    result.add(station);
                }
            }
            JsonObject pagination = object(response.meta().get("pagination"));
            boolean hasMore = bool(pagination, "has_more", page.size() == 100);
            JsonElement nextOffsetValue = pagination.get("next_offset");
            if (!hasMore || page.isEmpty() || nextOffsetValue == null || nextOffsetValue.isJsonNull()) {
                break;
            }
            int nextOffset = nextOffsetValue.getAsInt();
            if (nextOffset <= offset) {
                break;
            }
            offset = nextOffset;
        }
        return result;
    }

    private MusicDetail liveRadioTrack(TuneWeavePlatform platform, TuneWeaveRadioStation station) {
        String title = station.currentProgram().isBlank() ? station.name() : station.currentProgram();
        Album album = radioAlbum(platform, station, station.coverUrl(), List.of());
        MusicDetail track = MusicDetail.fromTuneWeave(
                entities.stableId(platform, "radio-live:" + station.reference()),
                station.reference(), "radio_station", title, 24 * 60 * 60 * 1000,
                album, List.of());
        track.setPusherInfo(PusherInfo.EMPTY);
        entities.cacheTrack(track);
        return track;
    }

    private Album radioAlbum(TuneWeavePlatform platform, TuneWeaveRadioStation station,
                             String cover, List<Artist> artists) {
        return new Album(entities.stableId(platform, "radio-station:" + station.reference()),
                station.name(), cover.isBlank() ? MusicHud.ICON_BASE64 : cover,
                "Radio", "", 0, new ObservableSequencedSet<>(),
                new LinkedHashSet<>(artists), PusherInfo.EMPTY, "");
    }
}
