package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.SequencedSet;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Playlist implements MusicCollection {
    private static final String TUNEWEAVE_REFERENCE_MARKER = "__MUSIC_HUD_TUNEWEAVE_REF__:";
    public static final ByteBufCodec<Playlist> CODEC = ByteBufCodec.composite(
            Codecs.LONG,
            Playlist::getId,
            Codecs.STRING_UTF8,
            Playlist::getName,
            Codecs.LONG,
            Playlist::getCoverImgId,
            Codecs.STRING_UTF8,
            Playlist::getCodecCoverImgIdString,
            Codecs.STRING_UTF8,
            Playlist::getCoverImgUrl,
            Codecs.INT,
            Playlist::getMusicTrackCount,
            Codecs.INT,
            Playlist::getPlayedCount,
            Profile.CODEC,
            Playlist::getCreator,
            Codecs.ofEnum(Privacy.class),
            Playlist::getPrivacy,
            Codecs.ofCollection(LinkedHashSet::new, () -> MusicDetail.CODEC),
            Playlist::getTracks,
            PusherInfo.CODEC,
            Playlist::getPusherInfo,
            Playlist::new
    );

    public static final Playlist EMPTY = new Playlist();

    @Getter
    long id = -1;
    String name = "";
    @Getter
    String sourceRef = "";
    @Getter
    long coverImgId = -1;
    @SerializedName("trackCount")
    @Getter
    @Setter
    int musicTrackCount;
    @SerializedName("playCount")
    @Getter
    int playedCount;
    String coverImgId_str = "";
    String coverImgUrl = MusicHud.ICON_BASE64;
    Profile creator = Profile.ANONYMOUS;
    Privacy privacy = Privacy.PUBLIC;
    SequencedSet<MusicDetail> tracks = new LinkedHashSet<>();

    // Not contained in the original API response, set separately
    @Getter
    PusherInfo pusherInfo = PusherInfo.EMPTY;

    protected Playlist(
            long id,
            String name,
            long coverImgId,
            String coverImgId_str,
            String coverImgUrl,
            int musicTrackCount,
            int playedCount,
            Profile creator,
            Privacy privacy,
            SequencedSet<MusicDetail> tracks,
            PusherInfo pusherInfo
    ) {
        this.id = id;
        this.name = name;
        this.coverImgId = coverImgId;
        if (coverImgId_str != null && coverImgId_str.startsWith(TUNEWEAVE_REFERENCE_MARKER)) {
            this.sourceRef = coverImgId_str.substring(TUNEWEAVE_REFERENCE_MARKER.length());
            this.coverImgId_str = "";
        } else {
            this.coverImgId_str = coverImgId_str;
        }
        this.coverImgUrl = coverImgUrl;
        this.musicTrackCount = musicTrackCount;
        this.playedCount = playedCount;
        this.creator = creator;
        this.privacy = privacy;
        this.tracks = tracks;
        this.pusherInfo = pusherInfo;
    }

    public static Playlist privacyBlocked(long id, Profile creator) {
        Playlist playlist = new Playlist();
        playlist.id = id;
        playlist.privacy = Privacy.PRIVATE;
        playlist.creator = creator;
        return playlist;
    }

    public static Playlist empty(long id) {
        Playlist playlist = new Playlist();
        playlist.id = id;
        return playlist;
    }

    public static Playlist fromTuneWeave(long id, String sourceRef, String name, String coverUrl) {
        Playlist playlist = new Playlist();
        playlist.id = id;
        playlist.sourceRef = Objects.requireNonNullElse(sourceRef, "");
        playlist.name = Objects.requireNonNullElse(name, "");
        playlist.coverImgUrl = Objects.requireNonNullElse(coverUrl, MusicHud.ICON_BASE64);
        return playlist;
    }

    public String getName() {
        return Objects.requireNonNullElse(name, "");
    }

    @Override
    public String getNameI18nKey() {
        return MusicHud.MOD_ID + ".text.playlist";
    }

    @Override
    public String getImageThumbnailUrl(int size) {
        return getThumbnailCoverUrl(size);
    }

    @Override
    public SequencedSet<MusicDetail> getMusicDetails() {
        return getTracks();
    }

    public String getCoverImgId_str() {
        return Objects.requireNonNullElse(coverImgId_str, "");
    }

    /**
     * The legacy wire structure has nine fields and its codec deliberately
     * skips arity ten.  A TuneWeave playlist never has an NCM cover-id string,
     * so reserve that unused slot for its canonical reference while retaining
     * full compatibility for legacy playlists.
     */
    private String getCodecCoverImgIdString() {
        return sourceRef.isBlank() ? getCoverImgId_str() : TUNEWEAVE_REFERENCE_MARKER + sourceRef;
    }

    public String getCoverImgUrl() {
        return Objects.requireNonNullElse(coverImgUrl, "");
    }

    public String getThumbnailCoverUrl(int size) {
        if (coverImgUrl.startsWith("data:image")) {
            return coverImgUrl;
        } else {
            return coverImgUrl + "?param=" + size + "y" + size;
        }
    }

    public Profile getCreator() {
        return Objects.requireNonNullElse(creator, Profile.ANONYMOUS);
    }

    public Privacy getPrivacy() {
        return Objects.requireNonNullElse(privacy, Privacy.PUBLIC);
    }

    public SequencedSet<MusicDetail> getTracks() {
        if (tracks == null || tracks.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return tracks.stream().filter(Objects::nonNull)
                .collect(LinkedHashSet::new, Set::add, LinkedHashSet::addAll);
    }

    public void setTracks(Collection<MusicDetail> tracks) {
        this.tracks = tracks == null ? new LinkedHashSet<>() : tracks.stream()
                .filter(Objects::nonNull)
                .collect(LinkedHashSet::new, Set::add, LinkedHashSet::addAll);
        this.musicTrackCount = this.tracks.size();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Playlist playlist
                && playlist.id == id
                && playlist.name.equals(name)
                && playlist.coverImgUrl.equals(coverImgUrl)
                && playlist.pusherInfo.equals(pusherInfo);
    }

    @Override
    public boolean equalsLoose(Object obj) {
        return obj instanceof Playlist playlist
                && playlist.id == id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, coverImgUrl, pusherInfo.getPlayerUUID());
    }

    @Override
    public Playlist copyWithPusherInfo(PusherInfo pusherInfo) {
        Playlist playlist = new Playlist();
        playlist.id = id;
        playlist.sourceRef = sourceRef;
        playlist.name = name;
        playlist.coverImgId = coverImgId;
        playlist.coverImgId_str = coverImgId_str;
        playlist.coverImgUrl = coverImgUrl;
        playlist.musicTrackCount = musicTrackCount;
        playlist.playedCount = playedCount;
        playlist.tracks = tracks;
        playlist.creator = creator;
        playlist.privacy = privacy;
        playlist.pusherInfo = pusherInfo;
        return playlist;
    }

    public Playlist copyWithSensitiveErased() {
        if (privacy == Privacy.PRIVATE) {
            Playlist playlist = new Playlist();
            playlist.id = id;
            playlist.name = "Private Playlist";
            playlist.coverImgId = -1;
            playlist.coverImgUrl = MusicHud.ICON_BASE64;
            playlist.creator = creator == Profile.ANONYMOUS ? Profile.PRIVATE_MASK : creator;
            playlist.privacy = privacy;
            playlist.pusherInfo = pusherInfo;
            return playlist;
        } else {
            return copyWithPusherInfo(pusherInfo);
        }
    }
}
