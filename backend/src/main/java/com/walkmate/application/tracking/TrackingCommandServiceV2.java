package com.walkmate.application.tracking;

import com.walkmate.application.user.UserPrincipal;
import com.walkmate.domain.session.SessionErrorCode;
import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.tracking.GpsPayloadEncoder;
import com.walkmate.domain.tracking.GpsStream;
import com.walkmate.domain.tracking.TrackingChunkRepository;
import com.walkmate.domain.tracking.TrackingErrorCode;
import com.walkmate.domain.tracking.TrackingStreamRepository;
import com.walkmate.domain.tracking.TrackingStreamSyncState;
import com.walkmate.domain.tracking.TrackingContractException;
import com.walkmate.infrastructure.util.PolylineEncoder;
import com.walkmate.presentation.dto.request.tracking.TrackingBatchRequest;
import com.walkmate.presentation.dto.response.tracking.TrackingBatchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-side v2 GPS sync: immutable batch writes with commit-or-proof ACK.
 *
 * <p>Guarantees enforced here (design: {@code buhduy/implementation_plan_gps_walkmate.md} §4.3):
 *
 * <ol>
 *   <li><b>Batch idempotency</b> — {@code batch_id} is the dedup key
 *       ({@code sync_request_id} column, UNIQUE). A replay of an
 *       already-committed batch is verified and re-ACKed; a batch id reused
 *       for a different payload / owner is rejected with
 *       {@code TRACKING_BATCH_ID_REUSED}.</li>
 *   <li><b>No sequence overlap</b> — two batches for the same stream whose
 *       {@code [seq_start, seq_end]} ranges intersect are rejected by the DB's
 *       GiST EXCLUDE constraint, surfaced as
 *       {@code TRACKING_SEQUENCE_OVERLAP} (HTTP 409). The loser of a race
 *       rolls back its whole transaction; nothing is ACKed that is not
 *       committed.</li>
 *   <li><b>Commit-or-proof ACK</b> — the response is built only after the
 *       chunk row is committed; import / decode errors never ACK a batch the
 *       database did not accept.</li>
 *   <li><b>Payload integrity</b> — the canonical SHA-256 is recomputed on the
 *       server from the parsed points and compared to the client-declared
 *       hash; mismatch is {@code TRACKING_HASH_MISMATCH} (HTTP 409).</li>
 * </ol>
 *
 * <p>Late-upload policy: batches are accepted after the participant's personal
 * walk status is {@code COMPLETED}, until the stream's seal deadline — walk
 * completion and GPS sync completion are intentionally decoupled.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingCommandServiceV2 {

    /** Default upper bound for v2 uploads; aligns with the client's batch size. */
    public static final int MAX_POINTS_PER_BATCH = 50;

    /** Clock skew tolerance on the future boundary of {@code recorded_at}. */
    public static final Duration RECORDED_AT_FUTURE_TOLERANCE = Duration.ofMinutes(10);

    /** Points recorded earlier than this are rejected (stale / clock-broken). */
    public static final Duration RECORDED_AT_MAX_AGE = Duration.ofDays(90);

    /** Upload window after a stream seal (late-upload policy). */
    public static final Duration LATE_UPLOAD_WINDOW = Duration.ofDays(7);

    private final WalkSessionRepository     sessionRepository;
    private final TrackingStreamRepository  streamRepository;
    private final TrackingChunkRepository   chunkRepository;

    /**
     * Ingests one immutable v2 batch.
     *
     * @param request  client batch (immutable after first send)
     * @param callerId authenticated user id (never taken from the body)
     * @return ACK containing the server-recomputed canonical hash; the client
     *         verifies and marks the matching batch ACKED in Room
     */
    @Transactional
    public TrackingBatchResponse pushBatch(TrackingBatchRequest request, String callerId) {
        // 1 — shape validation
        validate(request);

        // 2 — session + authorization + policy
        WalkSession session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new DomainException(SessionErrorCode.SESSION_NOT_FOUND));

        boolean isCallerUserA = session.getUserIdA().equals(callerId);
        if (!isCallerUserA && !session.getUserIdB().equals(callerId)) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_PARTICIPANT);
        }
        SessionStatus callerStatus = isCallerUserA ? session.getUserAStatus() : session.getUserBStatus();
        if (callerStatus != SessionStatus.ACTIVE && callerStatus != SessionStatus.COMPLETED) {
            throw new DomainException(SessionErrorCode.SESSION_ALREADY_TERMINAL);
        }

        // 3 — stream: upsert (idempotent) + row-level lock.
        //    A conflicting stream id (second device / reset stream) throws
        //    TrackingStreamConflictException → 409 TRACKING_STREAM_CONFLICT.
        GpsStream stream;
        boolean replayOfKnownBatch = chunkRepository.existsV2BatchBySyncRequestId(request.batchId());
        try {
            stream = streamRepository.upsertStream(request.sessionId(), callerId, request.streamId());
            stream = streamRepository.lockForUpdate(request.streamId());
        } catch (com.walkmate.domain.tracking.TrackingStreamConflictException e) {
            // A replay of an already-committed batch may arrive after the
            // stream has changed ownership state — but the batch id is
            // globally unique, so verify it below before a conflict answer.
            if (replayOfKnownBatch) {
                return ackExistingBatch(request, callerId);
            }
            throw new TrackingContractException(TrackingErrorCode.TRACKING_STREAM_CONFLICT, e.getMessage());
        }

        // 4 — hash verification (canonical recompute, bound to authenticated user)
        String recomputedHash = GpsPayloadEncoder.hash(
                request.sessionId(), callerId, request.streamId(),
                request.seqStart(), request.seqEnd(),
                toEncoderPoints(request));

        if (!recomputedHash.equalsIgnoreCase(request.payloadHash())) {
            throw new TrackingContractException(TrackingErrorCode.TRACKING_HASH_MISMATCH,
                    "server recomputed " + recomputedHash + " but client declared " + request.payloadHash());
        }

        // 5 — replay path: batch id already committed → verify + ACK
        if (replayOfKnownBatch) {
            return ackExistingBatch(request, callerId);
        }

        // 6 — closed-stream gate (after replay check: replays must keep working)
        if (stream.syncState() == TrackingStreamSyncState.COMPLETE
                || stream.syncState() == TrackingStreamSyncState.EXPIRED) {
            if (stream.syncState() == TrackingStreamSyncState.EXPIRED) {
                throw new TrackingContractException(TrackingErrorCode.TRACKING_STREAM_EXPIRED,
                        "stream " + stream.streamId() + " passed its upload deadline");
            }
            throw new TrackingContractException(TrackingErrorCode.TRACKING_STREAM_CLOSED,
                    "stream " + stream.streamId() + " is " + stream.syncState());
        }

        // 7 — seal-deadline gate for sealed streams
        if (stream.syncState() == TrackingStreamSyncState.SEALED
                && stream.uploadDeadlineAt() != null
                && stream.uploadDeadlineAt().isBefore(Instant.now())) {
            streamRepository.evaluateCompletion(stream.streamId()); // may flip to EXPIRED
            throw new TrackingContractException(TrackingErrorCode.TRACKING_STREAM_EXPIRED,
                    "stream " + stream.streamId() + " passed its upload deadline");
        }

        // 7 — route/build
        List<TrackingBatchRequest.V2Point> points = request.points();
        List<Double> lats = new ArrayList<>(points.size());
        List<Double> lngs = new ArrayList<>(points.size());
        for (TrackingBatchRequest.V2Point p : points) {
            lats.add(p.lat());
            lngs.add(p.lng());
        }
        String polyline = PolylineEncoder.encode(lats, lngs);
        byte[] timestampBytes = packTimestamps(points);

        // 8 — allocate arrival index under the stream lock, then insert.
        //    The GiST EXCLUDE constraint rejects range overlaps regardless of
        //    serialization: the loser's INSERT aborts the whole transaction.
        int chunkIndex = streamRepository.allocateArrivalIndex(request.streamId(), request.batchId());
        boolean inserted;
        try {
            inserted = chunkRepository.trySaveV2Chunk(
                    request.sessionId(), callerId, request.streamId(), chunkIndex,
                    polyline, timestampBytes, points.size(),
                    request.seqStart(), request.seqEnd(), recomputedHash,
                    request.batchId());
        } catch (DataIntegrityViolationException e) {
            // Postgres reports a GiST EXCLUDE violation with SQLSTATE 23P01
            // (exclusion_constraint_violation). Spring's translator wraps the
            // driver exception, so the state must be read from the cause chain.
            // Any other integrity failure must NOT be turned into "overlap".
            if (isExclusionViolation(e)) {
                throw new TrackingContractException(TrackingErrorCode.TRACKING_SEQUENCE_OVERLAP,
                        "insert of [" + request.seqStart() + "," + request.seqEnd()
                                + "] overlaps committed ranges in stream " + request.streamId(), e);
            }
            throw e;
        }

        // 9 — two concurrent requests with the same batch id: the loser of the
        //     unique race lands here (ON CONFLICT DO NOTHING → inserted=false).
        //     Re-verify + ACK the winner's committed row.
        if (!inserted) {
            return ackExistingBatch(request, callerId);
        }

        // 10 — opportunity to flip SEALED → COMPLETE/EXPIRED (no response change)
        if (stream.syncState() == TrackingStreamSyncState.SEALED
                && stream.expectedFinalSequence() != null) {
            streamRepository.evaluateCompletion(request.streamId());
        }

        return new TrackingBatchResponse(
                request.batchId(), request.streamId(),
                request.seqStart(), request.seqEnd(),
                recomputedHash, points.size(),
                TrackingBatchResponse.STATUS_COMMITTED, null);
    }

    /**
     * Verify an already-committed batch and return its ACK.
     * Fails closed: the committed row must agree on stream, ownership, range
     * and canonical hash — otherwise the batch id was <em>reused</em>.
     */
    private TrackingBatchResponse ackExistingBatch(TrackingBatchRequest request, String callerId) {
        TrackingChunkRepository.CommittedBatchRow stored =
                chunkRepository.findCommittedBatch(request.batchId()).orElseThrow(
                        () -> new IllegalStateException(
                                "batch " + request.batchId() + " disappeared between checks"));

        boolean streamMatches  = stored.streamId().equals(request.streamId());
        boolean sessionMatches = stored.sessionId().equals(request.sessionId());
        boolean rangeMatches   = stored.seqStart() == request.seqStart()
                                 && stored.seqEnd() == request.seqEnd();
        boolean ownerMatches   = stored.userId().equals(callerId);

        if (!streamMatches || !sessionMatches || !rangeMatches || !ownerMatches
                || !stored.payloadHash().equalsIgnoreCase(request.payloadHash())) {
            throw new TrackingContractException(TrackingErrorCode.TRACKING_BATCH_ID_REUSED,
                    "batch id " + request.batchId() + " already committed with different stream/range/owner/hash");
        }

        return new TrackingBatchResponse(
                request.batchId(), stored.streamId(),
                stored.seqStart(), stored.seqEnd(),
                stored.payloadHash(), stored.pointCount(),
                TrackingBatchResponse.STATUS_COMMITTED, null);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validate(TrackingBatchRequest request) {
        int size = request.points().size();
        if (size > MAX_POINTS_PER_BATCH) {
            throw new TrackingContractException(TrackingErrorCode.TRACKING_BATCH_TOO_LARGE,
                    "batch has " + size + " points; limit is " + MAX_POINTS_PER_BATCH);
        }
        if (request.seqStart() == null || request.seqStart() < 1) {
            throw new TrackingContractException(TrackingErrorCode.TRACKING_POINT_INVALID,
                    "seq_start must be >= 1");
        }
        if (request.seqEnd() == null
                || request.seqEnd() != request.seqStart() + size - 1) {
            throw new TrackingContractException(TrackingErrorCode.TRACKING_POINT_INVALID,
                    "seq_end must equal seq_start + size - 1 for a contiguous batch");
        }
        for (int i = 0; i < size; i++) {
            TrackingBatchRequest.V2Point p = request.points().get(i);
            if (p.sequence() != request.seqStart() + i) {
                throw new TrackingContractException(TrackingErrorCode.TRACKING_POINT_INVALID,
                        "point " + i + " sequence " + p.sequence()
                                + " breaks contiguous order (expected " + (request.seqStart() + i) + ")");
            }
            if (Double.isNaN(p.lat()) || Double.isNaN(p.lng())
                    || Double.isInfinite(p.lat()) || Double.isInfinite(p.lng())
                    || Float.isNaN(p.accuracy())) {
                throw new TrackingContractException(TrackingErrorCode.TRACKING_POINT_INVALID,
                        "point " + i + " contains non-finite values");
            }
            if (!recordedAtValid(p.recordedAt())) {
                throw new TrackingContractException(TrackingErrorCode.TRACKING_RECORDED_AT_INVALID,
                        "point " + i + " recorded_at " + p.recordedAt() + " outside validity window");
            }
        }
    }

    /**
     * True when the failure is PostgreSQL's exclusion-constraint violation
     * (SQLSTATE 23P01) raised by the {@code gps_v2_no_seq_overlap} constraint.
     * Spring's translator wraps the driver exception, so the state is read from
     * the cause chain rather than from the thrown type.
     */
    private static boolean isExclusionViolation(Throwable error) {
        for (Throwable t = error; t != null && t != t.getCause(); t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && "23P01".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static boolean recordedAtValid(long recordedAt) {
        Instant t  = Instant.ofEpochMilli(recordedAt);
        Instant now = Instant.now();
        return !t.isAfter(now.plus(RECORDED_AT_FUTURE_TOLERANCE))
             && !t.isBefore(now.minus(RECORDED_AT_MAX_AGE));
    }

    private static List<GpsPayloadEncoder.Point> toEncoderPoints(TrackingBatchRequest request) {
        List<GpsPayloadEncoder.Point> out = new ArrayList<>(request.points().size());
        for (TrackingBatchRequest.V2Point p : request.points()) {
            out.add(new GpsPayloadEncoder.Point(
                    p.sequence(), p.recordedAt(), p.lat(), p.lng(), p.accuracy()));
        }
        return out;
    }

    private static byte[] packTimestamps(List<TrackingBatchRequest.V2Point> points) {
        ByteBuffer buf = ByteBuffer.allocate(Long.BYTES * points.size())
                .order(ByteOrder.BIG_ENDIAN);
        for (TrackingBatchRequest.V2Point p : points) {
            buf.putLong(p.recordedAt());
        }
        return buf.array();
    }
}
