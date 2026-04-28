package com.walkmate.application.tracking;

import com.walkmate.domain.session.SessionErrorCode;
import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.tracking.TrackingChunkRepository;
import com.walkmate.infrastructure.util.PolylineEncoder;
import com.walkmate.presentation.dto.request.tracking.PushRoutePointsRequest;
import com.walkmate.presentation.dto.response.tracking.PushRoutePointsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingCommandService {

    private final WalkSessionRepository   sessionRepository;
    private final TrackingChunkRepository chunkRepository;

    /**
     * Validates, encodes, and persists a batch of GPS route points for an active session.
     *
     * <ol>
     *   <li>Verify the session is {@code ACTIVE} and the caller is a participant.</li>
     *   <li>Validate every point's lat/lng bounds and that no timestamp is in the future.</li>
     *   <li>Encode coordinates as a Google Encoded Polyline string.</li>
     *   <li>Pack timestamps as a big-endian {@code BYTEA}.</li>
     *   <li>Assign the next {@code chunk_index} and insert the chunk row.</li>
     *   <li>Return the echoed {@code localId} values so the client can mark those rows synced.</li>
     * </ol>
     *
     * <p>R2 — Idempotent insertion: the chunk INSERT includes {@code syncRequestId} which
     * is stored in a UNIQUE column. If the same UUID is received a second time (due to a
     * network retry or an Android double-push), the {@link DataIntegrityViolationException}
     * is caught and the method returns HTTP 200 with the same acknowledged IDs — the
     * client marks its Room rows as synced as if it were the first successful response.
     *
     * @param syncRequestId client-generated UUID uniquely identifying this push attempt
     * @param sessionId     the session the points belong to
     * @param callerId      the authenticated user's ID
     * @param points        the batch from the client (validated by the controller's @Valid)
     * @return a response containing the acknowledged local IDs
     */
    @Transactional
    public PushRoutePointsResponse syncRoutePoints(String syncRequestId,
                                                   String sessionId,
                                                   String callerId,
                                                   List<PushRoutePointsRequest.RoutePointPayload> points) {
        // 1. Verify session exists, caller is a participant, and caller's own status is ACTIVE.
        //    The global session status is ACTIVE when anyone is walking, but a specific user
        //    may still be PENDING (not arrived yet) or already COMPLETED — neither should be
        //    allowed to push GPS data.
        WalkSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new DomainException(SessionErrorCode.SESSION_NOT_FOUND));

        boolean isCallerUserA = session.getUserIdA().equals(callerId);
        if (!isCallerUserA && !session.getUserIdB().equals(callerId)) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_PARTICIPANT);
        }

        SessionStatus callerStatus = isCallerUserA ? session.getUserAStatus() : session.getUserBStatus();
        if (callerStatus != SessionStatus.ACTIVE) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_ACTIVE);
        }

        // 2. Validate all points
        long nowMs = Instant.now().toEpochMilli();
        for (PushRoutePointsRequest.RoutePointPayload p : points) {
            if (p.lat() < -90.0 || p.lat() > 90.0) {
                throw new IllegalArgumentException("Point lat out of range: " + p.lat());
            }
            if (p.lng() < -180.0 || p.lng() > 180.0) {
                throw new IllegalArgumentException("Point lng out of range: " + p.lng());
            }
            if (p.timestamp() > nowMs) {
                throw new IllegalArgumentException("Point timestamp is in the future: " + p.timestamp());
            }
        }

        // 3. Encode coordinates as Google Encoded Polyline
        List<Double> lats = new ArrayList<>(points.size());
        List<Double> lngs = new ArrayList<>(points.size());
        for (PushRoutePointsRequest.RoutePointPayload p : points) {
            lats.add(p.lat());
            lngs.add(p.lng());
        }
        String polyline = PolylineEncoder.encode(lats, lngs);

        // 4. Pack timestamps as big-endian BYTEA (8 bytes × pointCount)
        byte[] timestampBytes = packTimestamps(points);

        // 5. Build the acknowledged ID list before attempting the insert so it is
        //    available for the idempotent-success branch as well.
        List<Long> acknowledgedIds = new ArrayList<>(points.size());
        for (PushRoutePointsRequest.RoutePointPayload p : points) {
            acknowledgedIds.add(p.localId());
        }

        // 6. Assign next chunk index and persist.
        //    R2: If the UNIQUE constraint on sync_request_id fires, this batch was already
        //    stored — return the same acknowledged IDs so the client can mark its Room rows
        //    as synced (idempotent success).
        try {
            int chunkIndex = chunkRepository.nextChunkIndex(sessionId, callerId);
            chunkRepository.saveChunk(sessionId, callerId, chunkIndex, polyline,
                                      timestampBytes, points.size(), syncRequestId);
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate sync_request_id '{}' for session '{}' — returning idempotent success",
                     syncRequestId, sessionId);
            return new PushRoutePointsResponse(acknowledgedIds);
        }

        return new PushRoutePointsResponse(acknowledgedIds);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static byte[] packTimestamps(List<PushRoutePointsRequest.RoutePointPayload> points) {
        ByteBuffer buf = ByteBuffer.allocate(Long.BYTES * points.size())
                .order(ByteOrder.BIG_ENDIAN);
        for (PushRoutePointsRequest.RoutePointPayload p : points) {
            buf.putLong(p.timestamp());
        }
        return buf.array();
    }
}
