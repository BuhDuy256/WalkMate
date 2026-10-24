package com.walkmate.gps;

import com.walkmate.domain.tracking.TrackingErrorCode;
import com.walkmate.gps.support.GpsFixtures;
import com.walkmate.gps.support.TestBatch;
import com.walkmate.presentation.dto.response.tracking.TrackingBatchResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idempotent batch writes: replaying an already-committed batch must produce
 * a verifiable ACK and must never write a second row — and a batch id that is
 * reused for different content (or by a different user) must be rejected
 * instead of silently overwriting someone else's route.
 *
 * <p>Covers plan scenarios T04, T06, T07 (payload swap and owner swap).</p>
 */
@DisplayName("GPS v2 — idempotent batch writes (T04, T06, T07)")
class GpsV2IdempotencyTest extends GpsV2TestSupport {

    @Test
    @DisplayName("T04 server committed but the ACK was lost: retry the same batch → one chunk, same hash")
    void t04_lostAckRetrySameBatchStoresExactlyOneChunk() {
        TestBatch batch = TestBatch.fifty(sessionId, userA, streamA, 1, recordedAtBase);

        // First attempt commits server-side; imagine the response never arrives.
        TrackingBatchResponse first = instance.commandService().pushBatch(batch.request(), userA);
        assertThat(first.status()).isEqualTo(TrackingBatchResponse.STATUS_COMMITTED);
        assertThat(GpsFixtures.countV2Chunks(jdbc, sessionId, userA)).isEqualTo(1L);
        int arrivalAfterFirst = GpsFixtures.nextArrivalIndex(jdbc, streamA);

        // The client retries the byte-identical batch after the timeout.
        TrackingBatchResponse retry = instance.commandService().pushBatch(batch.request(), userA);

        assertThat(retry.batchId()).isEqualTo(batch.batchId());
        assertThat(retry.payloadHash()).isEqualTo(first.payloadHash());
        assertThat(retry.acceptedPointCount()).isEqualTo(50);
        assertThat(retry.status()).isEqualTo(TrackingBatchResponse.STATUS_COMMITTED);

        // Exactly one row in the database, and the retry consumed no arrival slot.
        assertThat(GpsFixtures.countV2Chunks(jdbc, sessionId, userA)).isEqualTo(1L);
        assertThat(GpsFixtures.committedRanges(jdbc, streamA)).containsExactly(new long[]{1, 50});
        assertThat(GpsFixtures.nextArrivalIndex(jdbc, streamA)).isEqualTo(arrivalAfterFirst);

        // The stored hash is the server's canonical recomputation, not client input echo.
        assertThat(GpsFixtures.chunkHashOfBatch(jdbc, batch.batchId()))
                .isEqualTo(first.payloadHash());
    }

    @Test
    @DisplayName("T06 replay with identical batch id and hash → equivalent ACK, chunk count unchanged")
    void t06_replaySameBatchIdSameHashIsIdempotent() {
        TestBatch batch = TestBatch.fifty(sessionId, userA, streamA, 1, recordedAtBase);

        TrackingBatchResponse original = instance.commandService().pushBatch(batch.request(), userA);
        TrackingBatchResponse replay1 = instance.commandService().pushBatch(batch.request(), userA);
        TrackingBatchResponse replay2 = instance.commandService().pushBatch(batch.request(), userA);

        assertThat(replay1.payloadHash()).isEqualTo(original.payloadHash());
        assertThat(replay2.payloadHash()).isEqualTo(original.payloadHash());
        assertThat(replay2.seqStart()).isEqualTo(1);
        assertThat(replay2.seqEnd()).isEqualTo(50);
        assertThat(replay2.acceptedPointCount()).isEqualTo(original.acceptedPointCount());

        assertThat(GpsFixtures.countV2Chunks(jdbc, sessionId, userA)).isEqualTo(1L);
        assertThat(GpsFixtures.arrivalOrder(jdbc, streamA)).containsExactly(0);
    }

    @Test
    @DisplayName("T07a replay with the same batch id but a different payload → 409, stored row untouched")
    void t07a_replaySameBatchIdDifferentPayloadIsRejected() {
        TestBatch batch = TestBatch.fifty(sessionId, userA, streamA, 1, recordedAtBase);
        TrackingBatchResponse original = instance.commandService().pushBatch(batch.request(), userA);
        String storedHash = GpsFixtures.chunkHashOfBatch(jdbc, batch.batchId());

        // Same batch id, one coordinate changed, hash not recomputed:
        // the server's canonical recomputation no longer matches.
        Throwable mutatedPayload = expectFailure(
                () -> instance.commandService().pushBatch(batch.withPointMutated().request(), userA));
        assertTrackingError(mutatedPayload, TrackingErrorCode.TRACKING_HASH_MISMATCH);

        // Same batch id, a *valid* hash for different content: the server's
        // recomputation matches, so the hash gate passes — and the batch-id
        // reuse check must be what rejects it (no silent overwrite).
        Throwable swappedPayload = expectFailure(
                () -> instance.commandService().pushBatch(
                        batch.withPointMutated()
                                .withRecomputedHash(sessionId, userA, streamA)
                                .request(),
                        userA));
        assertTrackingError(swappedPayload, TrackingErrorCode.TRACKING_BATCH_ID_REUSED);

        // The committed row is byte-for-byte what the first attempt wrote.
        assertThat(GpsFixtures.countV2Chunks(jdbc, sessionId, userA)).isEqualTo(1L);
        assertThat(GpsFixtures.chunkHashOfBatch(jdbc, batch.batchId())).isEqualTo(storedHash);
        assertThat(GpsFixtures.chunkHashOfBatch(jdbc, batch.batchId()))
                .isEqualTo(original.payloadHash());
        assertThat(GpsFixtures.committedRanges(jdbc, streamA)).containsExactly(new long[]{1, 50});
    }

    @Test
    @DisplayName("T07b partner replays another user's batch id / stream → 409, owner's data untouched")
    void t07b_partnerCannotReuseAnotherUsersBatchIdOrStream() {
        TestBatch ownerBatch = TestBatch.fifty(sessionId, userA, streamA, 1, recordedAtBase);
        instance.commandService().pushBatch(ownerBatch.request(), userA);
        String ownerHash = GpsFixtures.chunkHashOfBatch(jdbc, ownerBatch.batchId());

        // (1) User B reuses user A's batch id. The batch is globally unique and
        //     owned by A, so B's replay is rejected — it is never ACKed as if it
        //     were B's own upload.
        TestBatch stolenBatchId = TestBatch.of(sessionId, userB, streamA, 1, 50,
                recordedAtBase, ownerBatch.batchId());
        Throwable batchReuse = expectFailure(
                () -> instance.commandService().pushBatch(stolenBatchId.request(), userB));
        assertTrackingError(batchReuse, TrackingErrorCode.TRACKING_BATCH_ID_REUSED);

        // (2) User B presents user A's stream id. A stream is bound to one
        //     (session, user) pair; this must be a contract conflict, not a 500.
        String userBStream = UUID.randomUUID().toString();
        TestBatch hijackStream = TestBatch.fifty(sessionId, userB, streamA, 1, recordedAtBase);
        Throwable streamConflict = expectFailure(
                () -> instance.commandService().pushBatch(hijackStream.request(), userB));
        assertTrackingError(streamConflict, TrackingErrorCode.TRACKING_STREAM_CONFLICT);

        // Nothing of A's route was touched, and B has no stream of their own.
        assertThat(GpsFixtures.countV2Chunks(jdbc, sessionId, userA)).isEqualTo(1L);
        assertThat(GpsFixtures.countV2Chunks(jdbc, sessionId, userB)).isZero();
        assertThat(GpsFixtures.chunkHashOfBatch(jdbc, ownerBatch.batchId())).isEqualTo(ownerHash);
        assertThat(GpsFixtures.countStreams(jdbc, sessionId, userB)).isZero();

        // (3) B can still upload their own route under their own stream id.
        TestBatch ownBatch = TestBatch.fifty(sessionId, userB, userBStream, 1, recordedAtBase);
        TrackingBatchResponse ownAck = instance.commandService().pushBatch(ownBatch.request(), userB);
        assertThat(ownAck.batchId()).isEqualTo(ownBatch.batchId());
        assertThat(GpsFixtures.countV2Chunks(jdbc, sessionId, userB)).isEqualTo(1L);
    }
}
