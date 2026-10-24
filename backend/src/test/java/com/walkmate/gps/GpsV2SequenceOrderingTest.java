package com.walkmate.gps;

import com.walkmate.gps.support.GpsFixtures;
import com.walkmate.gps.support.TestBatch;
import com.walkmate.presentation.dto.request.tracking.SealStreamRequest;
import com.walkmate.presentation.dto.response.tracking.SealStreamResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sequence numbers, not arrival order, define the route. Out-of-order batches
 * must be accepted and reordered at read time, and a partial route must never
 * be reported as fully synced.
 *
 * <p>Covers plan scenarios T09 and T10.</p>
 */
@DisplayName("GPS v2 — sequence ordering and coverage (T09, T10)")
class GpsV2SequenceOrderingTest extends GpsV2TestSupport {

    @Test
    @DisplayName("T09 batches arrive 101–150, then 1–50, then 51–100 → stored in sequence order")
    void t09_outOfOrderBatchesReassembleTheFullRoute() {
        TestBatch tail = TestBatch.fifty(sessionId, userA, streamA, 101, recordedAtBase + 100_000);
        TestBatch head = TestBatch.fifty(sessionId, userA, streamA, 1, recordedAtBase);
        TestBatch middle = TestBatch.fifty(sessionId, userA, streamA, 51, recordedAtBase + 50_000);

        // No ordering requirement is imposed on the client.
        instance.commandService().pushBatch(tail.request(), userA);
        instance.commandService().pushBatch(head.request(), userA);
        instance.commandService().pushBatch(middle.request(), userA);

        assertThat(GpsFixtures.countV2Chunks(jdbc, sessionId, userA)).isEqualTo(3L);

        // Route reconstruction is ordered by seq_start — 1..150 regardless of upload order.
        assertThat(GpsFixtures.committedRanges(jdbc, streamA)).containsExactly(
                new long[]{1, 50},
                new long[]{51, 100},
                new long[]{101, 150});

        // The arrival cursor reflects *arrival*, which is the opposite order here
        // (tail, head, middle). Proving the two orders are independent.
        assertThat(GpsFixtures.arrivalOrder(jdbc, streamA)).containsExactly(0, 1, 2);

        // Sealing at the true final sequence still completes the stream.
        SealStreamResponse sealed = instance.sealService()
                .seal(streamA, userA, new SealStreamRequest(150L, recordedAtBase + 150_000));
        assertThat(sealed.finalSequence()).isEqualTo(150L);
        assertThat(GpsFixtures.streamSyncState(jdbc, streamA)).isEqualTo("COMPLETE");
    }

    @Test
    @DisplayName("T10 only 101–150 uploaded and sealed at 150 → no false COMPLETE, prefix still missing")
    void t10_partialTailIsNotReportedAsComplete() {
        TestBatch tail = TestBatch.fifty(sessionId, userA, streamA, 101, recordedAtBase + 100_000);
        instance.commandService().pushBatch(tail.request(), userA);

        instance.sealService().seal(streamA, userA, new SealStreamRequest(150L, recordedAtBase + 150_000));

        // The seal must not imply a full route: sequence 1–100 was never uploaded.
        assertThat(GpsFixtures.streamSyncState(jdbc, streamA))
                .as("a partial tail must not be promoted to COMPLETE")
                .isEqualTo("SEALED");
        assertThat(GpsFixtures.committedRanges(jdbc, streamA))
                .containsExactly(new long[]{101, 150});

        // Nothing was implicitly back-filled for the missing prefix.
        assertThat(GpsFixtures.countV2Chunks(jdbc, sessionId, userA)).isEqualTo(1L);
        assertThat(GpsFixtures.hasChunkWithRange(jdbc, streamA, 1, 100)).isFalse();
        assertThat(GpsFixtures.nextArrivalIndex(jdbc, streamA)).isEqualTo(1);
    }
}
