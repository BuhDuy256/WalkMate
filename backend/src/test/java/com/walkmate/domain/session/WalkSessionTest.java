package com.walkmate.domain.session;

import com.walkmate.domain.valueobject.SessionTrackingStats;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalkSessionTest {

  @Test
  void activateByBothParticipantsTransitionsToActive() {
    Instant start = Instant.parse("2026-03-14T07:00:00Z");
    WalkSession session = samplePendingSession(start);

    session.activate(session.getUser1Id(), start.minusSeconds(60));
    session.activate(session.getUser2Id(), start.minusSeconds(30));

    assertEquals(SessionStatus.ACTIVE, session.getStatus());
  }

  @Test
  void completeFailsWhenDurationBelowMinimum() {
    Instant start = Instant.parse("2026-03-14T07:00:00Z");
    WalkSession session = samplePendingSession(start);
    session.activate(session.getUser1Id(), start.minusSeconds(60));
    session.activate(session.getUser2Id(), start.minusSeconds(50));

    assertThrows(DomainException.class, () -> session.complete(
        session.getUser1Id(),
        start.plusSeconds(100),
        new SessionTrackingStats(new BigDecimal("1.25"), 120)));
  }

  @Test
  void systemProcessActivationWindowProducesNoShowWhenOnlyOneActivated() {
    Instant start = Instant.parse("2026-03-14T07:00:00Z");
    WalkSession session = samplePendingSession(start);
    session.activate(session.getUser1Id(), start.minusSeconds(30));

    session.systemProcessActivationWindow(start.plusSeconds(11 * 60));

    assertEquals(SessionStatus.NO_SHOW, session.getStatus());
  }

  private WalkSession samplePendingSession(Instant start) {
    return new WalkSession(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        start,
        start.plusSeconds(30 * 60),
        null,
        null,
        null,
        null,
        SessionStatus.PENDING,
        BigDecimal.ZERO,
        0,
        null,
        null,
        null,
        0);
  }
}
