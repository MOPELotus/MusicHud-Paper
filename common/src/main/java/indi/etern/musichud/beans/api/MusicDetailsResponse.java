package indi.etern.musichud.beans.api;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.beans.music.MusicDetail;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class MusicDetailsResponse {
    @Getter
    int code;
    @SerializedName("songs")
    List<MusicDetail> musicDetails;
    @SerializedName("privileges")
    List<PrivilegeInfo> privileges;

    public List<MusicDetail> getMusicDetails() {
        if (musicDetails == null || musicDetails.isEmpty()) {
            return List.of();
        }
        List<MusicDetail> details = musicDetails.stream().filter(Objects::nonNull).toList();
        if (privileges != null && !privileges.isEmpty()) {
            Map<Long, Boolean> cloudSourceById = privileges.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(
                            PrivilegeInfo::getId,
                            PrivilegeInfo::isCloudSource,
                            Boolean::logicalOr
                    ));
            details.forEach(musicDetail -> {
                if (Boolean.TRUE.equals(cloudSourceById.get(musicDetail.getId()))) {
                    musicDetail.setCloudSource(true);
                }
            });
        }
        return details;
    }

    @Getter
    public static class PrivilegeInfo {
        long id;
        @SerializedName("cs")
        boolean cloudSource;
    }
}
