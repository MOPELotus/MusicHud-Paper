package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.Objects;

@NoArgsConstructor
@Setter
public class MusicResourceInfo {
    public static final ByteBufCodec<MusicResourceInfo> CODEC = ByteBufCodec.composite(
            Codecs.LONG, MusicResourceInfo::getId,
            Codecs.STRING_UTF8, MusicResourceInfo::getUrl,
            Codecs.INT, MusicResourceInfo::getBitrate,
            Codecs.LONG, MusicResourceInfo::getSize,
            Codecs.ofEnum(FormatType.class), MusicResourceInfo::getType,
            Codecs.STRING_UTF8, MusicResourceInfo::getMd5,
            Codecs.ofEnum(Fee.class), MusicResourceInfo::getFee,
            Codecs.INT, MusicResourceInfo::getTime,
            Codecs.ofStringMap(), MusicResourceInfo::getHeaders,
            MusicResourceInfo::new
    );
    public static final MusicResourceInfo NONE = new MusicResourceInfo();
    @Getter
    long id;
    String url = "";
    @SerializedName("br")
    @Getter
    int bitrate;
    @Getter
    long size;//byte
    FormatType type = FormatType.AUTO;
    String md5 = "";
    Fee fee = Fee.UNSET;
    @Getter
    int time;
    @Getter
    Map<String, String> headers = Map.of();

    public MusicResourceInfo(long id, String url, int bitrate, long size, FormatType type, String md5,
                             Fee fee, int time) {
        this(id, url, bitrate, size, type, md5, fee, time, Map.of());
    }

    public MusicResourceInfo(long id, String url, int bitrate, long size, FormatType type, String md5,
                             Fee fee, int time, Map<String, String> headers) {
        this.id = id;
        this.url = url;
        this.bitrate = bitrate;
        this.size = size;
        this.type = type;
        this.md5 = md5;
        this.fee = fee;
        this.time = time;
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public static MusicResourceInfo from(String url, MusicDetail musicDetail) {
        MusicResourceInfo musicResourceInfo = new MusicResourceInfo();
        musicResourceInfo.url = url;
        musicResourceInfo.id = musicDetail.getId();
        if (musicResourceInfo.md5 == null)
            musicResourceInfo.md5 = "";
        musicResourceInfo.time = musicDetail.getDurationMillis();
        return musicResourceInfo;
    }

    public String getUrl() {
        return Objects.requireNonNullElse(url, "");
    }

    public FormatType getType() {
        return Objects.requireNonNullElse(type, FormatType.AUTO);
    }

    public String getMd5() {
        return Objects.requireNonNullElse(md5, "");
    }

    public Fee getFee() {
        return Objects.requireNonNullElse(fee, Fee.UNSET);
    }
}
