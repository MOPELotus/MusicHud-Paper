package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.utils.JsonUtil;
import lombok.*;

import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;

@AllArgsConstructor(access = AccessLevel.PUBLIC)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Setter
public class MusicResourceInfo {
    public static final ByteBufCodec<MusicResourceInfo> CODEC = ByteBufCodec.composite(
            Codecs.LONG,
            MusicResourceInfo::getId,
            Codecs.STRING_UTF8,
            MusicResourceInfo::getUrl,
            Codecs.STRING_UTF8,
            MusicResourceInfo::getHeadersJson,
            Codecs.INT,
            MusicResourceInfo::getBitrate,
            Codecs.LONG,
            MusicResourceInfo::getSize,
            Codecs.ofEnum(FormatType.class),
            MusicResourceInfo::getType,
            Codecs.STRING_UTF8,
            MusicResourceInfo::getMd5,
            Codecs.ofEnum(Fee.class),
            MusicResourceInfo::getFee,
            Codecs.INT,
            MusicResourceInfo::getTime,
            MusicResourceInfo::new
    );
    public static final MusicResourceInfo NONE = new MusicResourceInfo();
    @Getter
    long id;
    String url = "";
    String headersJson = "{}";
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

    public static MusicResourceInfo from(String url, MusicDetail musicDetail) {
        MusicResourceInfo musicResourceInfo = new MusicResourceInfo();
        musicResourceInfo.url = url;
        musicResourceInfo.id = musicDetail.getId();
        if (musicResourceInfo.md5 == null)
            musicResourceInfo.md5 = "";
        musicResourceInfo.time = musicDetail.getDurationMillis();
        return musicResourceInfo;
    }

    public static MusicResourceInfo fromTuneWeave(
            MusicDetail musicDetail, String url, int bitrate, long size, String format,
            Map<String, String> requestHeaders
    ) {
        MusicResourceInfo result = new MusicResourceInfo(
                musicDetail.getId(), url, "{}", bitrate, size, parseFormat(format),
                "", Fee.FREE, musicDetail.getDurationMillis());
        result.setRequestHeaders(requestHeaders);
        return result;
    }

    private static FormatType parseFormat(String format) {
        if (format == null || format.isBlank()) {
            return FormatType.AUTO;
        }
        try {
            return FormatType.valueOf(format.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return FormatType.AUTO;
        }
    }

    public String getUrl() {
        return Objects.requireNonNullElse(url, "");
    }

    public String getHeadersJson() {
        return Objects.requireNonNullElse(headersJson, "{}");
    }

    public void setRequestHeaders(Map<String, String> headers) {
        headersJson = JsonUtil.gson.toJson(headers == null ? Map.of() : headers);
    }

    public Map<String, String> getRequestHeaders() {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            JsonUtil.gson.fromJson(getHeadersJson(), com.google.gson.JsonObject.class)
                    .entrySet()
                    .forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
        } catch (Exception ignored) {
        }
        return result;
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
