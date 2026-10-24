package com.walkmate.gps;

import com.walkmate.domain.tracking.TrackingErrorCode;
import com.walkmate.gps.support.GpsFixtures;
import com.walkmate.gps.support.TestBatch;
import com.walkmate.presentation.dto.response.tracking.TrackingBatchResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency guarantees of the v2 write path: overlapping ranges are rejected
 * by the database itself (GiST {@code EXCLUDE}, not an application-level
 * pre-check), the arrival cursor never collides, and a non-duplicate integrity
 * failure aborts the whole write instead of being ACKed.
 *
 * <p>Covers plan scenarios T08, T11 and T12.</p>
 */
@DisplayName("GPS v2 — concurrency, overlap and rollback (T08, T11, T12)")
class GpsV2ConcurrencyTest extends GpsV2TestSupport {

    @Test
    @DisplayName("T08 two different batch ids racing for the same range → exactly one commits")
    void t08_concurrentOverlappingRangesCommitExactlyOnce() throws Exception {
        TestBatch left = TestBatch.fifty(sessionId, userA, streamA, 1, recordedAtBase);
        TestBatch right = TestBatch.fifty(sessionId, userA, streamA, 1, recordedAtBase);

        assertThat(left.batchId()).isNotEqualTo(right.batchId());

        // Both threads are released from a barrier and hit the database together.
        List<Outcome<TrackingBatchResponse>> outcomes = runConcurrently(
                () -> instance.commandService().pushBatch(left.request(), userA),
                () -> instance.commandService().pushBatch(right.request(), userA));

        List<Outcome<TrackingBatchResponse>> winners = outcomes.stream().filter(Outcome::succeeded).toList();
        List<Outcome<TrackingBatchResponse>> losers = outcomes.stream().filter(o -> !o.succeeded()).toList();

        assertThat(winners).as("exactly one of the two overlapping batches may commit").hasSize(1);
        assertThat(losers).hasSize(1);
        assertTrackingError(losers.get(0).error(), TrackingErrorCode.TRACKING_SEQUENCE_OVERLAP);

        // One row, one range, one arrival slot consumed.
        assertThat(GpsFixtures.countV2Chunks(jdbc, sessionId, userA)).isEqualTo(1L);
        assertThat(GpsFixtures.committedRanges(jdbc, streamA)).containsExactly(new long[]{1, 50});
        assertThat(GpsFixtures.nextArrivalIndex(jdbc, streamA)).isEqualTo(1);
        assertThat(GpsFixtures.arrivalOrder(jdbc, streamA)).containsExactly(0);
    }

    @Test
    @DisplayName("T11 two parallel batches race for chunk_index → distinct arrival slots, no false ACK")
    void t11_parallelBatchesGetDistinctArrivalIndices() throws Exception {
        TestBatch head = TestBatch.fifty(sessionId, userA, streamA, 1, recordedAtBase);
        TestBatch tail = TestBatch.fifty(sessionId, userA, streamA, 51, recordedAtBase + 50_000);

        // Different ranges, so the overlap constraint must NOT fire. Only the
        // arrival-cursor allocation is contended.
        List<Outcome<TrackingBatchResponse>> outcomes = runConcurrently(
                () -> instance.commandService().pushBatch(head.request(), userA),
                () -> instance.commandService().pushBatch(tail.request(), userA));

        assertThat(outcomes).as("disjoint ranges must both succeed").allMatch(Outcome::succeeded);

        // Each batch stored exactly once — no duplicate row from the cursor race.
        assertThat(GpsFixtures.countV2Chunks(jdbc, sessionId, userA)).isEqualTo(2L);
        assertThat(GpsFixtures.committedRanges(jdbc, streamA))
                .containsExactly(new long[]{1, 50}, new long[]{51, 100});

        // The arrival cursor is unique and contiguous: 0 and 1, never 0 and 0.
        assertThat(GpsFixtures.arrivalOrder(jdbc, streamA)).containsExactly(0, 1);
        assertThat(GpsFixtures.nextArrivalIndex(jdbc, streamA)).isEqualTo(2);
    }

    @Test
    @DisplayName("T12 non-duplicate DB error → error propagates, nothing ACKed, no partial chunk")
    void t12_nonDuplicateIntegrityFailureRollsBackWithoutAck() {
        // A legacy (v1) chunk already occupies chunk_index 0 for this
        // (session, user). The v2 path will try to allocate arrival index 0 and
        // collide on session_point_chunks_unique — a real 23505 that is *not* a
        // sequence overlap and must not be mistaken for a replay.
        GpsFixtures.seedLegacyChunk(jdbc, sessionId, userA, 0, 3);

        TestBatch batch = TestBatch.fifty(sessionId, userA, streamA, 1, recordedAtBase);

        Throwable failure = expectFailure(
                () -> instance.commandService().pushBatch(batch.request(), userA));

        assertThat(failure)
                .as("a non-overlap integrity failure must surface as an error, never as an ACK")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(failure).isNotInstanceOf(com.walkmate.domain.shared.exception.DomainException.class);

        // No v2 chunk, and the whole write transaction — including the stream
        // row and the arrival-cursor bump — rolled back.
        assertThat(GpsFixtures.countV2Chunks(jdbc, sessionId, userA)).isZero();
        assertThat(GpsFixtures.countStreams(jdbc, sessionId, userA)).isZero();
        assertThat(GpsFixtures.hasChunkWithRange(jdbc, streamA, 1, 50)).isFalse();
        assertThat(GpsFixtures.chunkHashOfBatch(jdbc, batch.batchId())).isNull();
    }
}
