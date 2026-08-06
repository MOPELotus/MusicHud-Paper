package indi.etern.musichud.client.services.tuneweave;

import indi.etern.musichud.server.api.tuneweave.TuneWeavePlatform;

final class TuneWeaveReference {
    private TuneWeaveReference() {
    }

    static void require(String reference, String kind) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("TuneWeave " + kind + " reference is missing");
        }
    }

    static TuneWeavePlatform platform(String reference) {
        require(reference, "resource");
        int separator = reference.indexOf(':');
        if (separator <= 0) {
            throw new IllegalArgumentException("Invalid TuneWeave reference: " + reference);
        }
        return TuneWeavePlatform.fromApiName(reference.substring(0, separator));
    }

    static String id(String reference) {
        require(reference, "resource");
        int separator = reference.lastIndexOf(':');
        return separator >= 0 ? reference.substring(separator + 1) : reference;
    }
}
