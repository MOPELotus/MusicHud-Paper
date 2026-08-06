package indi.etern.musichud.client.services.tuneweave;

import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;

public record TuneWeaveQrSession(TuneWeavePlatform platform, String transactionId, String url,
                                 String imageDataUrl, String expiresAt) {
}
