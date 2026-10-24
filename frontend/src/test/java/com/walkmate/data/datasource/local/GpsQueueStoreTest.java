package com.walkmate.data.datasource.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.walkmate.data.datasource.local.dao.GpsBatchDao;
import com.walkmate.data.datasource.local.dao.GpsStreamDao;
import com.walkmate.data.datasource.local.dao.RoutePointDao;
import com.walkmate.data.datasource.local.entity.GpsBatchEntity;
import com.walkmate.data.datasource.local.entity.RoutePointEntity;
import com.walkmate.domain.tracking.GpsBatch;
import com.walkmate.domain.tracking.GpsBatchState;
import com.walkmate.domain.tracking.GpsPayloadHasher;
import com.walkmate.domain.tracking.GpsSyncState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Local-queue invariants of the v2 GPS sync, exercised against a real Room
 * database (Robolectric SQLite).
 *
 * <p>These are the client-side halves of the scenarios that cannot be proven on
 * the server: a batch must exist on disk <em>before</em> the first upload attempt,
 * its payload must be reproducible byte-for-byte on a retry, and a sealed batch
 * must never absorb later points. If any of these breaks, the server's idempotency
 * cannot save the route — it would see a different payload under the same batch id
 * and correctly refuse it.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = android.app.Application.class)
public class GpsQueueStoreTest {

    private static final String SESSION = "11111111-1111-1111-1111-111111111111";
    private static final String USER    = "22222222-2222-2222-2222-222222222222";
    private static final long   BASE_TS = 1_792_824_000_000L;

    private WalkMateDatabase db;
    private GpsQueueStore store;
    private RoutePointDao pointDao;
    private GpsBatchDao batchDao;
    private GpsStreamDao streamDao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, WalkMateDatabase.class)
                .allowMainThreadQueries()
                .build();
        store = new GpsQueueStore(db);
        pointDao = db.routePointDao();
        batchDao = db.gpsBatchDao();
        streamDao = db.gpsStreamDao();
    }

    @After
    public void tearDown() {
        db.close();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void accept(int count, long startTs) {
        String streamId = store.ensureStream(SESSION, USER);
        for (int i = 0; i < count; i++) {
            store.saveAcceptedPoint(SESSION, USER, streamId,
                    10.0 + i * 0.001, 106.0 + i * 0.0005,
                    startTs + i * 1000L, 5.0f);
        }
    }

    /**
     * Simulates the backoff window elapsing. {@code release()} deliberately defers
     * the retry, so a test that wants to observe the retry must be "later".
     */
    private void makeRetryDue(String batchId) {
        GpsBatchEntity row = batchDao.find(batchId);
        row.nextAttemptAt = System.currentTimeMillis() - 1;
        batchDao.insert(row);
    }

    // ── T01: sequences are assigned, contiguous and durable ───────────────────

    @Test
    public void t01_acceptedPointsGetContiguousSequencesStartingAtOne() {
        accept(50, BASE_TS);

        List<RoutePointEntity> rows = pointDao.getUnbatchedPoints(SESSION, USER, 100);
        assertEquals(50, rows.size());
        for (int i = 0; i < rows.size(); i++) {
            assertEquals("sequence must be 1-based and contiguous",
                    i + 1, rows.get(i).sequence.longValue());
        }
        assertEquals(50L, store.lastAllocatedSequence(SESSION, USER));
        assertNotNull("stream id is minted on first accept",
                store.ensureStream(SESSION, USER));
    }

    @Test
    public void t01_sequenceAllocationIsGapFreeAcrossRepeatedAccepts() {
        accept(1, BASE_TS);
        accept(1, BASE_TS + 1000);
        accept(1, BASE_TS + 2000);

        List<RoutePointEntity> rows = pointDao.getUnbatchedPoints(SESSION, USER, 100);
        List<Long> seqs = new ArrayList<>();
        for (RoutePointEntity r : rows) {
            seqs.add(r.sequence);
        }
        assertEquals(java.util.Arrays.asList(1L, 2L, 3L), seqs);
    }

    // ── T02: a small batch seals with a fixed id and hash ─────────────────────

    @Test
    public void t02_partialBatchSealsWithStableHashAndLaterPointsGoElsewhere() {
        accept(7, BASE_TS);

        GpsBatch first = store.sealNextBatch(SESSION, USER);
        assertNotNull("a short batch must still be sealable", first);
        assertEquals(1L, first.getSeqStart());
        assertEquals(7L, first.getSeqEnd());
        assertEquals(7, first.getPointCount());
        assertNotNull("batch id is minted at seal time, not at send time", first.getBatchId());
        assertEquals(GpsBatchState.PENDING, first.getState());

        // The stored hash must be the canonical hash of exactly this payload.
        List<GpsPayloadHasher.Point> canonical = new ArrayList<>();
        for (RoutePointEntity r : pointDao.getPointsOfBatch(first.getBatchId())) {
            canonical.add(new GpsPayloadHasher.Point(
                    r.sequence, r.timestamp, r.lat, r.lng, r.accuracy));
        }
        String expected = GpsPayloadHasher.hash(SESSION, USER, first.getStreamId(),
                1L, 7L, canonical);
        assertEquals(expected, first.getPayloadHash());

        // Points accepted afterwards must NOT join the sealed batch.
        accept(2, BASE_TS + 10_000);
        assertEquals(7, pointDao.getPointsOfBatch(first.getBatchId()).size());
        assertEquals(2, pointDao.countUnbatched(SESSION, USER));

        GpsBatch second = store.sealNextBatch(SESSION, USER);
        assertNotNull(second);
        assertEquals("the new batch continues the sequence",
                8L, second.getSeqStart());
        assertEquals(9L, second.getSeqEnd());
        assertFalse(second.getBatchId().equals(first.getBatchId()));
    }

    // ── T03/T04: sealed payload is reproducible, so a retry is a replay ───────

    @Test
    public void t04_retryReusesTheIdenticalBatchIdAndPayload() {
        accept(50, BASE_TS);
        GpsBatch sealed = store.sealNextBatch(SESSION, USER);

        // Simulate an upload whose ACK never arrived: the batch is released back
        // to the queue for a retry.
        store.release(sealed);
        assertTrue("a released batch waits out its backoff window",
                store.findDrainable(SESSION, USER).isEmpty());

        makeRetryDue(sealed.getBatchId());

        List<GpsBatch> drainable = store.findDrainable(SESSION, USER);
        assertEquals("the released batch must be re-readable", 1, drainable.size());
        GpsBatch retry = drainable.get(0);

        assertEquals("retrying must resend the same batch id",
                sealed.getBatchId(), retry.getBatchId());
        assertEquals("retrying must resend the same hash",
                sealed.getPayloadHash(), retry.getPayloadHash());
        assertEquals(sealed.getSeqStart(), retry.getSeqStart());
        assertEquals(sealed.getSeqEnd(), retry.getSeqEnd());
        assertEquals("payload must be byte-identical on retry",
                hashOf(sealed), hashOf(retry));
        assertEquals(1, retry.getRetryCount());
    }

    @Test
    public void t05_pointsArrivingDuringARetryNeverJoinTheFrozenBatch() {
        accept(50, BASE_TS);
        GpsBatch sealed = store.sealNextBatch(SESSION, USER);
        String originalHash = sealed.getPayloadHash();

        store.release(sealed);
        // New fixes keep arriving while the batch is awaiting retry.
        accept(10, BASE_TS + 100_000);

        makeRetryDue(sealed.getBatchId());
        GpsBatch retry = store.findDrainable(SESSION, USER).get(0);
        assertEquals(originalHash, retry.getPayloadHash());
        assertEquals(50, retry.getPointCount());
        assertEquals(10, pointDao.countUnbatched(SESSION, USER));
    }

    // ── T12/T13: leases make a crashed worker recoverable ─────────────────────

    @Test
    public void t13_expiredLeaseMakesAnAbandonedBatchClaimableAgain() {
        accept(50, BASE_TS);
        GpsBatch sealed = store.sealNextBatch(SESSION, USER);

        assertTrue("first claim takes the lease", store.claim(sealed.getBatchId()));
        assertFalse("a second worker must not steal a live lease",
                store.claim(sealed.getBatchId()));
        assertTrue(store.findDrainable(SESSION, USER).isEmpty());

        // The process died mid-upload: the lease expires and the batch returns.
        GpsBatchEntity row = batchDao.find(sealed.getBatchId());
        row.leaseUntil = System.currentTimeMillis() - 1;
        batchDao.insert(row);

        List<GpsBatch> recovered = store.findDrainable(SESSION, USER);
        assertEquals(1, recovered.size());
        assertEquals(sealed.getBatchId(), recovered.get(0).getBatchId());
        assertTrue("an expired lease must be re-claimable",
                store.claim(sealed.getBatchId()));
    }

    // ── ACK verification ──────────────────────────────────────────────────────

    @Test
    public void ackOnlyMarksPointsSyncedWhenRangeStreamAndHashAllMatch() {
        accept(50, BASE_TS);
        GpsBatch sealed = store.sealNextBatch(SESSION, USER);
        store.claim(sealed.getBatchId());

        boolean mismatched = store.ackVerified(sealed, sealed.getStreamId(),
                sealed.getSeqStart(), sealed.getSeqEnd(), "0000000000000000000000000000000"
                        + "0000000000000000000000000000000");
        assertFalse("a hash mismatch must never mark the batch committed", mismatched);

        GpsBatchEntity stillInFlight = batchDao.find(sealed.getBatchId());
        assertEquals(GpsBatchState.IN_FLIGHT, stillInFlight.state);
        for (RoutePointEntity r : pointDao.getPointsOfBatch(sealed.getBatchId())) {
            assertFalse("no point may be marked synced by an unverified ACK", r.isSynced);
        }

        boolean accepted = store.ackVerified(sealed, sealed.getStreamId(),
                sealed.getSeqStart(), sealed.getSeqEnd(), sealed.getPayloadHash());
        assertTrue("a fully matching ACK commits the batch", accepted);

        assertEquals(GpsBatchState.ACKED, batchDao.find(sealed.getBatchId()).state);
        for (RoutePointEntity r : pointDao.getPointsOfBatch(sealed.getBatchId())) {
            assertTrue("points move to synced only after the verified ACK", r.isSynced);
        }
    }

    @Test
    public void ackForADifferentRangeIsRejected() {
        accept(50, BASE_TS);
        GpsBatch sealed = store.sealNextBatch(SESSION, USER);
        store.claim(sealed.getBatchId());

        boolean wrongRange = store.ackVerified(sealed, sealed.getStreamId(),
                sealed.getSeqStart() + 1, sealed.getSeqEnd(), sealed.getPayloadHash());
        assertFalse("a range mismatch means this is not our batch", wrongRange);
        assertFalse(pointDao.getPointsOfBatch(sealed.getBatchId()).get(0).isSynced);
    }

    // ── Permanent refusal is parked, never dropped ────────────────────────────

    @Test
    public void blockedBatchKeepsItsPointsForDiagnosis() {
        accept(50, BASE_TS);
        GpsBatch sealed = store.sealNextBatch(SESSION, USER);

        store.block(sealed.getBatchId(), "TRACKING_HASH_MISMATCH");

        assertEquals(1, store.findBlocked(SESSION, USER).size());
        assertEquals("a blocked batch keeps all of its points",
                50, pointDao.getPointsOfBatch(sealed.getBatchId()).size());
        assertEquals("a blocked batch must not keep retrying forever",
                0, store.findDrainable(SESSION, USER).size());
    }

    // ── Seal / completion intent ──────────────────────────────────────────────

    @Test
    public void finalSequenceIsPersistedAndNeverShrinks() {
        accept(120, BASE_TS);
        store.persistFinalSequence(SESSION, USER, 120L, BASE_TS + 120_000);
        assertEquals(Long.valueOf(120L), streamDao.find(SESSION, USER).finalSequence);
        assertEquals(GpsSyncState.SEALED, streamDao.find(SESSION, USER).syncState);

        // A resumed process re-sealing with a lower value must not lose coverage.
        store.persistFinalSequence(SESSION, USER, 80L, null);
        assertEquals(Long.valueOf(120L), streamDao.find(SESSION, USER).finalSequence);
    }

    // ── Legacy quarantine ─────────────────────────────────────────────────────

    @Test
    public void legacyRowsWithoutSequenceAreNeverSealedIntoAV2Batch() {
        // Mirrors the v4→v5 upgrade: an existing unsynced row keeps streamId and
        // sequence NULL.
        RoutePointEntity legacy = new RoutePointEntity();
        legacy.sessionId = SESSION;
        legacy.userId = USER;
        legacy.lat = 10.0;
        legacy.lng = 106.0;
        legacy.timestamp = BASE_TS;
        legacy.accuracy = 5f;
        legacy.isSynced = false;
        pointDao.insertPoint(legacy);

        accept(3, BASE_TS + 5000);

        assertEquals(3, pointDao.countUnbatched(SESSION, USER));
        assertEquals(1, store.countUnsequencedLegacy(SESSION, USER));

        GpsBatch sealed = store.sealNextBatch(SESSION, USER);
        assertNotNull(sealed);
        assertEquals("sealing must start at the first real sequence, not the legacy row",
                1L, sealed.getSeqStart());
        assertEquals(3, sealed.getPointCount());

        // The legacy row is untouched and still unsynced: it was not absorbed,
        // re-sequenced, or deleted.
        assertEquals(1, store.countUnsequencedLegacy(SESSION, USER));
        for (RoutePointEntity r : pointDao.getPointsOfBatch(sealed.getBatchId())) {
            assertNotNull("the sealed batch must contain only sequenced points", r.sequence);
        }
    }

    // ── Logout cleanup ────────────────────────────────────────────────────────

    @Test
    public void clearForUserRemovesBatchesStreamAndPointsTogether() {
        accept(50, BASE_TS);
        store.sealNextBatch(SESSION, USER);

        store.clearForUser(USER);

        assertEquals(0, store.countOutstanding(SESSION, USER));
        assertNull(streamDao.find(SESSION, USER));
        assertEquals(0, pointDao.getUnbatchedPoints(SESSION, USER, 10).size());
    }

    private static String hashOf(GpsBatch batch) {
        return GpsPayloadHasher.hash(batch.getSessionId(), batch.getUserId(),
                batch.getStreamId(), batch.getSeqStart(), batch.getSeqEnd(),
                batch.getPoints());
    }
}
