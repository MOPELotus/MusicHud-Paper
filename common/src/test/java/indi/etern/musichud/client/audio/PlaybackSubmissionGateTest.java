package indi.mopelotus.musichud.client.audio;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackSubmissionGateTest {
    @Test void eachTrackCanSubmitButCompletionAndStopCannotDuplicate() {
        var gate = new PlaybackSubmissionGate();
        gate.activate(1, UUID.randomUUID());
        assertTrue(gate.claim(1, true));
        assertFalse(gate.claim(1, true));
        gate.activate(2, UUID.randomUUID());
        assertTrue(gate.claim(2, true));
    }

    @Test void invalidMetadataDoesNotConsumeTheClaim() {
        var gate = new PlaybackSubmissionGate();
        gate.activate(1, UUID.randomUUID());
        assertFalse(gate.claim(1, false));
        assertTrue(gate.claim(1, true));
    }

    @Test void refreshAndRetryKeepSessionClaimAndRejectOldCallbacks() {
        var gate = new PlaybackSubmissionGate();
        UUID session = UUID.randomUUID();
        gate.activate(1, session);
        gate.activate(2, session);
        assertFalse(gate.claim(1, true));
        assertTrue(gate.claim(2, true));
        gate.activate(3, session);
        assertFalse(gate.claim(3, true));
        gate.activate(1, UUID.randomUUID());
        assertFalse(gate.claim(1, true));
    }

    @Test void stoppedSessionCannotBeRevivedByLateCompletion() {
        var gate = new PlaybackSubmissionGate();
        gate.activate(1, UUID.randomUUID());
        gate.invalidate(2);
        gate.activate(1, UUID.randomUUID());
        assertFalse(gate.claim(1, true));
        gate.activate(3, UUID.randomUUID());
        assertTrue(gate.claim(3, true));
    }
}
