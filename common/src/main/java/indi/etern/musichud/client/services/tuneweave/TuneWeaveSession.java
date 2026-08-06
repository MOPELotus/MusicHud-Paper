package indi.etern.musichud.client.services.tuneweave;

import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.VipType;
import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;

public record TuneWeaveSession(TuneWeavePlatform platform, String userId, String nickname,
                               String avatarUrl, boolean authenticated) {
    public Profile toMusicHudProfile() {
        String displayName = nickname == null || nickname.isBlank() ? platform.apiName() : nickname;
        return new Profile(displayName, avatarUrl == null ? "" : avatarUrl,
                TuneWeaveIdentity.stableId(platform,
                        TuneWeaveIdentity.userIdFromReference(platform, userId)),
                VipType.NORMAL);
    }
}
