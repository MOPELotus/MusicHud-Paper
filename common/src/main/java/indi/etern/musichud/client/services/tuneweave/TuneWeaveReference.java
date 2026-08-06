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

    static TuneWeavePlatform platformOrDefault(String reference, TuneWeavePlatform fallback) {
        require(reference, "resource");
        if (reference.startsWith("account:favorite_tracks:")) {
            return TuneWeavePlatform.fromApiName(reference.substring(reference.lastIndexOf(':') + 1));
        }
        int separator = reference.indexOf(':');
        return separator > 0
                ? TuneWeavePlatform.fromApiName(reference.substring(0, separator))
                : fallback;
    }

    static String id(String reference) {
        require(reference, "resource");
        int separator = reference.indexOf(':');
        return separator >= 0 ? reference.substring(separator + 1) : reference;
    }

    static boolean isStyledRadio(String reference) {
        return reference != null && reference.contains(":difm:");
    }
}
