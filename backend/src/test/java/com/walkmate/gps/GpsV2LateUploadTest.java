package com.walkmate.gps;

import com.walkmate.domain.tracking.TrackingErrorCode;
import com.walkmate.gps.support.GpsFixtures;
import com.walkmate.gps.support.TestBatch;
import com.walkmate.presentation.dto.request.tracking.SealStreamRequest;
import com.walkmate.presentation.dto.response.tracking.SealStreamResponse;
import com.walkmate.presentation.dto.response.tracking.TrackingBatchResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Finishing a walk is decoupled from finishing its GPS upload: the session can
 * already be {@code COMPLETED} while batches are still arriving, and only full
 * sequence coverage promotes the stream to {@code COMPLETE}. Once the upload
 * deadline has passed, late data is refused rather than silently accepted.
 *
 * <p>Covers plan scenarios T16 and T17.</p>
 */
@DisplayName("GPS v2 — late uploads and expiry (T16, T17)")
class GpsV2LateUploadTest extends GpsV2TestSupport {

    @Test
    @DisplayName("T16 session finished offline, upload arrives late before the deadline → COMPLETE")
    void t16_lateUploadWithinDeadlineCompletesTheStream() {
        // The walk finished normally — the user tapped Finish while offline.
        GpsFixtures.markUserCompleted(jdbc, sessionId, userA);

        // The queued batches drain afterwards; the caller status is COMPLETED,
        // which the contract explicitly allows (late-upload policy).
        TestBatch batch = TestBatch.fifty(sessionId, userA, streamA, 1, recordedAtBase);
        TrackingBatchResponse ack = instance.commandService().pushBatch(batch.request(), userA);
        assertThat(ack.status()).isEqualTo(TrackingBatchResponse.STATUS_COMMITTED);
        assertThat(GpsFixtures.countV2Chunks(jdbc, sessionId, userA)).isEqualTo(1L);

        // A preliminary seal cannot complete the stream yet — but here the whole
        // route is already present, so the seal finalises it in the same call.
        SealStreamResponse sealed = instance.sealService()
                .seal(streamA, userA, new SealStreamRequest(50L, recordedAtBase + 50_000));

        assertThat(sealed.finalSequence()).isEqualTo(50L);
        assertThat(sealed.syncDeadlineAt()).isNotBlank();
        assertThat(Instant.parse(sealed.syncDeadlineAt())).isAfter(Instant.now());

        // GPS sync state and the business session status are independent axes.
        assertThat(GpsFixtures.streamSyncState(jdbc, streamA)).isEqualTo("COMPLETE");
    }

    @Test
    @DisplayName("T17a sealed, prefix missing, deadline passed → late batch refused, nothing written")
    void t17a_expiredStreamRejectsLateBatches() {
        // Only the tail arrived before the walk ended.
        TestBatch tail = TestBatch.fifty(sessionId, userA, streamA, 51, recordedAtBase + 50_000);
        instance.commandService().pushBatch(tail.request(), userA);
        instance.sealService().seal(streamA, userA, new SealStreamRequest(100L, recordedAtBase + 100_000));

        // The 7-day late-upload window closes without the missing prefix.
        GpsFixtures.expireStream(jdbc, streamA);

        long chunksBefore = GpsFixtures.countV2Chunks(jdbc, sessionId, userA);

        TestBatch late = TestBatch.fifty(sessionId, userA, streamA, 1, recordedAtBase);
        Throwable failure = expectFailure(
                () -> instance.commandService().pushBatch(late.request(), userA));

        assertTrackingError(failure, TrackingErrorCode.TRACKING_STREAM_EXPIRED);
        assertThat(GpsFixtures.countV2Chunks(jdbc, sessionId, userA)).isEqualTo(chunksBefore);
        assertThat(GpsFixtures.hasChunkWithRange(jdbc, streamA, 1, 50)).isFalse();

        // The already-committed tail is untouched by the rejected late batch.
        assertThat(GpsFixtures.committedRanges(jdbc, streamA))
                .containsExactly(new long[]{51, 100});

        // The rejection is durable: the expiry that pushBatch detected while
        // rejecting the batch ran in a transaction that rolled back, so the
        // idempotent housekeeping call is what records it. Re-batching then
        // keeps failing with 410 rather than re-evaluating every time.
        instance.sealService().expire(streamA, userA);
        assertThat(GpsFixtures.streamSyncState(jdbc, streamA)).isEqualTo("EXPIRED");

        Throwable again = expectFailure(() -> instance.commandService()
                .pushBatch(TestBatch.fifty(sessionId, userA, streamA, 1, recordedAtBase).request(), userA));
        assertTrackingError(again, TrackingErrorCode.TRACKING_STREAM_EXPIRED);
        assertThat(GpsFixtures.countV2Chunks(jdbc, sessionId, userA)).isEqualTo(chunksBefore);
    }

    @Test
    @DisplayName("T17b covered range whose deadline passed → COMPLETE wins over EXPIRED")
    void t17b_fullCoverageIsCompleteEvenAfterTheDeadline() {
        for (long from : new long[]{1L, 51L}) {
            instance.commandService().pushBatch(
                    TestBatch.fifty(sessionId, userA, streamA, from, recordedAtBase + from * 1000).request(),
                    userA);
        }

        instance.sealService().seal(streamA, userA, new SealStreamRequest(100L, recordedAtBase + 100_000));

        // A later evaluation (deadline already elapsed) must not downgrade a
        // fully covered route to EXPIRED — coverage is checked before expiry.
        GpsFixtures.expireStream(jdbc, streamA);
        instance.streamRepository().evaluateCompletion(streamA);

        assertThat(GpsFixtures.streamSyncState(jdbc, streamA))
                .as("a complete route stays COMPLETE regardless of the upload deadline")
                .isEqualTo("COMPLETE");
        assertThat(GpsFixtures.countV2Chunks(jdbc, sessionId, userA)).isEqualTo(2L);
    }
}
