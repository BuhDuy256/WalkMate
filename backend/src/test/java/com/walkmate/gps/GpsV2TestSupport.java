package com.walkmate.gps;

import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.tracking.TrackingContractException;
import com.walkmate.domain.tracking.TrackingErrorCode;
import com.walkmate.gps.support.GpsFixtures;
import com.walkmate.gps.support.GpsPostgresTestBase;
import com.walkmate.gps.support.GpsTestInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared scaffolding for the v2 GPS scenario tests (T04–T17).
 *
 * <p>Every scenario drives the real services against the real migrated
 * schema and asserts on committed SQL state, never on a service return value
 * alone. The Android-side scenarios (T01–T03, T05, T13–T15, T18) require the
 * Room queue / WorkManager harness and are covered by the Android test
 * module, not here.</p>
 */
abstract class GpsV2TestSupport extends GpsPostgresTestBase {

    protected JdbcClient jdbc;
    protected GpsTestInstance instance;

    protected String sessionId;
    protected String userA;
    protected String userB;
    protected String streamA;

    /** Epoch millis of the most recent point this test is allowed to create. */
    protected long recordedAtBase;

    @BeforeEach
    void resetDatabase() {
        jdbc = rawClient();

        jdbc.sql("""
                        TRUNCATE TABLE session_point_chunks, tracking_streams,
                                       match_proposal, walk_intent, walk_session,
                                       hotspot, user_profile, user_account CASCADE
                        """)
                .update();

        sessionId = UUID.randomUUID().toString();
        userA = UUID.randomUUID().toString();
        userB = UUID.randomUUID().toString();
        streamA = UUID.randomUUID().toString();
        recordedAtBase = System.currentTimeMillis() - 3_600_000L; // 1h ago

        GpsFixtures.seedUser(jdbc, userA, "gps-a-" + userA + "@example.com");
        GpsFixtures.seedUser(jdbc, userB, "gps-b-" + userB + "@example.com");
        GpsFixtures.seedSessionChain(jdbc, sessionId, userA, userB,
                "ACTIVE", "ACTIVE", "ACTIVE");

        instance = GpsTestInstance.start(jdbcUrl(), "gps-" + sessionId.substring(0, 8));
    }

    @AfterEach
    void closeInstance() {
        if (instance != null) {
            instance.close();
        }
    }

    // ── Assertion helpers ─────────────────────────────────────────────────────

    protected static void assertTrackingError(Throwable error, TrackingErrorCode expected) {
        assertThat(error)
                .as("expected TrackingContractException(%s) but got %s", expected, error)
                .isInstanceOf(TrackingContractException.class);
        TrackingContractException contract = (TrackingContractException) error;
        assertThat(contract.getErrorCode()).isSameAs(expected);
    }

    protected static void assertSessionError(Throwable error, com.walkmate.domain.session.SessionErrorCode expected) {
        assertThat(error).isInstanceOf(DomainException.class);
        assertThat(((DomainException) error).getErrorCode()).isSameAs(expected);
    }

    /** Runs a call that must fail, returning the thrown exception. */
    protected static Throwable expectFailure(Runnable call) {
        try {
            call.run();
        } catch (Throwable t) {
            return t;
        }
        throw new AssertionError("expected the call to fail, but it completed normally");
    }
}
