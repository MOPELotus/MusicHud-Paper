package indi.etern.musichud.client.services.tuneweave;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuneWeaveMappingTest {
    @Test
    void stableIdsAreDeterministicAndPlatformScoped() {
        long netease = TuneWeaveIdentity.stableId(TuneWeavePlatform.NETEASE, "track:123");

        assertEquals(netease,
                TuneWeaveIdentity.stableId(TuneWeavePlatform.NETEASE, "track:123"));
        assertNotEquals(netease,
                TuneWeaveIdentity.stableId(TuneWeavePlatform.QQ, "track:123"));
    }

    @Test
    void referencesPreserveFavoriteAliasesAndProviderIds() {
        assertEquals(TuneWeavePlatform.BILIBILI,
                TuneWeaveReference.platform("bilibili:video:BV1example"));
        assertEquals(TuneWeavePlatform.QQ,
                TuneWeaveReference.platformOrDefault(
                        "account:favorite_tracks:qq", TuneWeavePlatform.NETEASE));
        assertEquals(TuneWeavePlatform.NETEASE,
                TuneWeaveReference.platformOrDefault("123", TuneWeavePlatform.NETEASE));
        assertEquals("cloud:123", TuneWeaveReference.id("netease:cloud:123"));
        assertThrows(IllegalArgumentException.class, () -> TuneWeaveReference.platform("missing"));
    }

    @Test
    void snapshotMergeKeepsExplicitFieldsAndFillsMissingFields() {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("title", "snapshot title");
        snapshot.addProperty("cover_url", "http://example.test/cover.jpg");
        JsonObject source = new JsonObject();
        source.addProperty("title", "explicit title");
        source.add("cover_url", JsonNull.INSTANCE);
        source.add("snapshot", snapshot);

        JsonObject merged = TuneWeaveJson.mergeSnapshot(source);

        assertEquals("explicit title", TuneWeaveJson.string(merged, "title"));
        assertEquals("https://example.test/cover.jpg",
                TuneWeaveJson.imageUrl(merged, "cover_url"));
    }

    @Test
    void listEnvelopeParsingAcceptsItemsAndDropsBlankStrings() {
        JsonArray items = new JsonArray();
        items.add("first");
        items.add("");
        items.add(JsonNull.INSTANCE);
        items.add("second");
        JsonObject envelope = new JsonObject();
        envelope.add("items", items);

        assertEquals(4, TuneWeaveJson.elements(envelope).size());
        assertEquals(List.of("first", "second"), TuneWeaveJson.stringList(items));
    }

    @Test
    void cloudMappingPreservesOwnerScopedSelector() {
        JsonObject track = new JsonObject();
        track.addProperty("ref", "netease:track:42");
        track.addProperty("name", "Cloud track");
        track.addProperty("duration_ms", 60_000);
        JsonObject cloud = new JsonObject();
        cloud.addProperty("ref", "netease:cloud:42");
        cloud.add("track", track);

        TuneWeaveCloudTrack mapped = new TuneWeaveEntityMapper(platform -> null)
                .toCloudTrack(TuneWeavePlatform.NETEASE, cloud);

        assertEquals("netease:cloud:42", mapped.track().getSourcePartRef());
        assertTrue(mapped.track().isCloudSource());
    }
}
