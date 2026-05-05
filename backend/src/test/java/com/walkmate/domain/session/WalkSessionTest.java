package com.walkmate.domain.session;

import com.walkmate.domain.shared.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalkSessionTest {

    @Test
    void recordActivation_firstParticipant_startsSessionAndSetsStartedAt() {
        Instant start = Instant.now().plusSeconds(60);
        Instant end = start.plusSeconds(3600);
        WalkSession s = WalkSession.create("p1","a","b","h", start, end);

        s.recordActivation("a", Instant.now());

        assertThat(s.getUserAStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(s.getStartedAt()).isNotNull();
        assertThat(s.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    void recordActivation_invalidParticipant_throws() {
        WalkSession s = WalkSession.create("p1","a","b","h", Instant.now(), Instant.now().plusSeconds(600));
        assertThrows(DomainException.class, () -> s.recordActivation("x", Instant.now()));
    }

    @Test
    void complete_activeParticipant_transitionsToCompletedWhenBothTerminal() {
        Instant start = Instant.now().minusSeconds(300);
        Instant end = start.plusSeconds(3600);
        WalkSession s = WalkSession.create("p1","a","b","h", start, end);

        // both activate then complete
        s.recordActivation("a", Instant.now().minusSeconds(200));
        s.recordActivation("b", Instant.now().minusSeconds(190));

        s.complete("a", Instant.now().minusSeconds(100));
        // after a completes, global should still be ACTIVE until both complete
        assertThat(s.getUserAStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(s.getStatus()).isEqualTo(SessionStatus.ACTIVE);

        s.complete("b", Instant.now().minusSeconds(50));
        assertThat(s.getUserBStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(s.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(s.getEndedAt()).isNotNull();
    }

    @Test
    void cancel_pendingSession_setsCancelled() {
        WalkSession s = WalkSession.create("p1","a","b","h", Instant.now().plusSeconds(60), Instant.now().plusSeconds(3600));
        s.cancel("no show", "a");
        assertThat(s.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(s.getCancelledBy()).isEqualTo("a");
    }

    @Test
    void markNoShow_pending_thenCompletesWhenBothNoShow() {
        Instant now = Instant.now();
        WalkSession s = WalkSession.create("p1","a","b","h", now.plusSeconds(60), now.plusSeconds(3600));
        s.markNoShow("a", now);
        assertThat(s.getUserAStatus()).isEqualTo(SessionStatus.NO_SHOW);
        // global status remains derived
        s.markNoShow("b", now);
        assertThat(s.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    void recordFinalDistance_updatesDistanceForParticipant() {
        WalkSession s = WalkSession.create("p1","a","b","h", Instant.now(), Instant.now().plusSeconds(600));
        s.recordFinalDistance("a", 3.5);
        assertThat(s.getUserADistanceKm()).isEqualTo(3.5);
        s.recordFinalDistance("b", -1.0);
        // negative distances are sanitized to 0.0
        assertThat(s.getUserBDistanceKm()).isEqualTo(0.0);
    }

    @Test
    void recordQrVerification_idempotencyAndParticipantChecks() {
        WalkSession s = WalkSession.create("p1","a","b","h", Instant.now(), Instant.now().plusSeconds(600));
        // allow verification when status is PENDING or ACTIVE per domain
        s.recordQrVerification("a");
        // second verification of same side should throw
        assertThrows(RuntimeException.class, () -> s.recordQrVerification("a"));
        // invalid user
        assertThrows(RuntimeException.class, () -> s.recordQrVerification("x"));
    }
}
