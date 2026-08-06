package indi.etern.musichud.client.services.tuneweave;

import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;

public record TuneWeaveChallengeSession(TuneWeavePlatform platform, String transactionId) {
}
