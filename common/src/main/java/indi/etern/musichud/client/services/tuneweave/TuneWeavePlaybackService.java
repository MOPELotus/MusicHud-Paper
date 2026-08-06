package indi.etern.musichud.client.services.tuneweave;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.Fee;
import indi.etern.musichud.beans.music.FormatType;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.MusicResourceInfo;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.server.api.tuneweave.TuneWeaveApiClient;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.elements;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.integer;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.longValue;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.object;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.string;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.stringList;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.stringMap;
import static indi.etern.musichud.client.services.tuneweave.TuneWeaveJson.unwrap;

/** Resolves playable streams for every TuneWeave-backed Music HUD track kind. */
final class TuneWeavePlaybackService {
    private final TuneWeaveGateway gateway;

    TuneWeavePlaybackService(TuneWeaveGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway);
    }

    MusicResourceInfo resolve(MusicDetail musicDetail, Quality quality) {
        if (musicDetail == null || musicDetail.getSourceRef().isBlank()) {
            return MusicResourceInfo.NONE;
        }
        String reference = musicDetail.getSourceRef();
        TuneWeavePlatform platform = TuneWeaveReference.platformOrDefault(
                reference, gateway.defaultPlatform());
        if (musicDetail.isClientHostedUni()) {
            return resolveUniItem(musicDetail, quality);
        }
        if ("radio_station".equals(musicDetail.getSourceKind())) {
            return resolveRadio(musicDetail, platform);
        }

        String path = switch (musicDetail.getSourceKind()) {
            case "video" -> "/v1/videos/" + TuneWeaveApiClient.encodePathSegment(reference)
                    + "/audio-stream";
            case "podcast_episode" -> "/v1/episodes/"
                    + TuneWeaveApiClient.encodePathSegment(reference) + "/stream";
            default -> "/v1/tracks/" + TuneWeaveApiClient.encodePathSegment(reference) + "/stream";
        };
        Map<String, String> query = new LinkedHashMap<>();
        query.put("quality", qualityName(quality));
        if ("video".equals(musicDetail.getSourceKind())) {
            query.put("type", "video");
            if (!musicDetail.getSourcePartRef().isBlank()) {
                query.put("part", musicDetail.getSourcePartRef());
            }
        }
        JsonObject response = unwrap(gateway.requestForPlatform(
                platform, "GET", path, query, null).data());
        JsonObject stream = "podcast_episode".equals(musicDetail.getSourceKind())
                ? object(response.get("stream")) : response;
        return streamResource(musicDetail, stream, true);
    }

    private MusicResourceInfo resolveRadio(MusicDetail musicDetail, TuneWeavePlatform platform) {
        if (!TuneWeaveReference.isStyledRadio(musicDetail.getSourceRef())) {
            JsonObject station = unwrap(gateway.requestForPlatform(
                    platform, "GET", "/v1/radio/stations/"
                            + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef()),
                    Map.of(), null).data());
            String url = string(station, "stream_url", "");
            return url.isBlank() ? MusicResourceInfo.NONE : new MusicResourceInfo(
                    musicDetail.getId(), url, 0, 0L, FormatType.AUTO, "", Fee.UNSET,
                    musicDetail.getDurationMillis(), stringMap(station.get("headers")));
        }

        JsonObject queue = object(gateway.requestForPlatform(
                platform, "GET", "/v1/radio/stations/"
                        + TuneWeaveApiClient.encodePathSegment(musicDetail.getSourceRef()) + "/tracks",
                Map.of("limit", musicDetail.getSourcePartRef().isBlank() ? "1" : "100"), null).data());
        List<JsonElement> items = elements(queue.get("items"));
        JsonObject item = items.stream().map(TuneWeaveJson::unwrap)
                .filter(value -> musicDetail.getSourcePartRef().equals(string(value, "ref", "")))
                .findFirst()
                .or(() -> items.stream().findFirst().map(TuneWeaveJson::unwrap))
                .orElseGet(JsonObject::new);
        String url = string(item, "stream_url", "");
        return url.isBlank() ? MusicResourceInfo.NONE : new MusicResourceInfo(
                musicDetail.getId(), url, 0, 0L, FormatType.AUTO, "", Fee.UNSET,
                integer(item, "duration_ms", musicDetail.getDurationMillis()),
                stringMap(item.get("headers")));
    }

    private MusicResourceInfo resolveUniItem(MusicDetail musicDetail, Quality quality) {
        JsonObject body = new JsonObject();
        body.add("item", uniItem(musicDetail));
        body.addProperty("quality", qualityName(quality));
        JsonObject data = object(gateway.requestWithAllCredentials(
                "POST", "/v1/uni/items/stream", Map.of(), body).data());
        return streamResource(musicDetail, object(data.get("stream")), false);
    }

    private static JsonObject uniItem(MusicDetail musicDetail) {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("title", musicDetail.getName());
        JsonArray artists = new JsonArray();
        musicDetail.getArtists().stream().map(Artist::getName).forEach(artists::add);
        snapshot.add("artists", artists);
        addNullable(snapshot, "album", musicDetail.getAlbum().getName());
        snapshot.addProperty("duration_ms", musicDetail.getDurationMillis());
        snapshot.add("isrc", JsonNull.INSTANCE);
        String coverUrl = musicDetail.getAlbum().getPicUrl();
        if (coverUrl != null && coverUrl.startsWith("https://")) {
            snapshot.addProperty("cover_url", coverUrl);
        } else {
            snapshot.add("cover_url", JsonNull.INSTANCE);
        }
        snapshot.add("version_tags", new JsonArray());

        JsonObject snapshotExtensions = new JsonObject();
        snapshotExtensions.addProperty("canonical_ref", musicDetail.getSourceRef());
        snapshotExtensions.add("playable", JsonNull.INSTANCE);
        snapshotExtensions.add("available_qualities", new JsonArray());
        addNulls(snapshotExtensions, List.of("mv_ref", "video_kind", "published_at",
                "podcast_ref", "audio_ref", "serial_number", "description", "category",
                "region", "current_program", "has_direct_stream"));
        snapshot.add("extensions", snapshotExtensions);

        JsonObject item = new JsonObject();
        item.addProperty("id", String.format(Locale.ROOT, "item_%016x", musicDetail.getId()));
        item.addProperty("position", 0);
        item.addProperty("kind", musicDetail.getSourceKind());
        item.addProperty("source_ref", musicDetail.getSourceRef());
        item.add("snapshot", snapshot);
        item.addProperty("added_at_ms", System.currentTimeMillis());
        JsonObject itemExtensions = new JsonObject();
        addNulls(itemExtensions, List.of("import_source_index", "import_source_ref",
                "import_source_type", "imported_from_item_id"));
        item.add("extensions", itemExtensions);
        return item;
    }

    private static MusicResourceInfo streamResource(MusicDetail musicDetail,
                                                     JsonObject stream, boolean includeBackups) {
        String url = string(stream, "url", "");
        if (url.isBlank()) {
            return MusicResourceInfo.NONE;
        }
        if (!includeBackups) {
            return new MusicResourceInfo(musicDetail.getId(), url,
                    integer(stream, "bitrate", 0), longValue(stream, "size", 0L),
                    FormatType.fromSerializedName(string(stream, "format", string(stream, "codec", ""))),
                    "", Fee.UNSET, integer(stream, "duration_ms", musicDetail.getDurationMillis()),
                    stringMap(stream.get("headers")));
        }
        return new MusicResourceInfo(musicDetail.getId(), url,
                integer(stream, "bitrate", 0), longValue(stream, "size", 0L),
                FormatType.fromSerializedName(string(stream, "format", string(stream, "codec", ""))),
                "", Fee.UNSET, integer(stream, "duration_ms", musicDetail.getDurationMillis()),
                stringMap(stream.get("headers")), stringList(stream.get("backup_urls")));
    }

    private static void addNullable(JsonObject target, String property, String value) {
        if (value == null || value.isBlank()) {
            target.add(property, JsonNull.INSTANCE);
        } else {
            target.addProperty(property, value);
        }
    }

    private static void addNulls(JsonObject target, List<String> properties) {
        properties.forEach(property -> target.add(property, JsonNull.INSTANCE));
    }

    private static String qualityName(Quality quality) {
        if (quality == null) {
            return "auto";
        }
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
}
