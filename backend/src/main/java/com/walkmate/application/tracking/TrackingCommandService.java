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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
     *   <li>Atomically assign the next {@code chunk_index} and insert the chunk row.</li>
     *   <li>Return the echoed {@code localId} values so the client can mark those rows synced.</li>
     * </ol>
     *
     * @param sessionId the session the points belong to
     * @param callerId  the authenticated user's ID
     * @param points    the batch from the client (validated by the controller's @Valid)
     * @return a response containing the acknowledged local IDs
     */
    @Transactional
    public PushRoutePointsResponse syncRoutePoints(String sessionId, String callerId,
                                                   List<PushRoutePointsRequest.RoutePointPayload> points) {
        // 1. Verify session exists, is ACTIVE, and caller is a participant
        WalkSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new DomainException(SessionErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_ACTIVE);
        }
        if (!session.getUserIdA().equals(callerId) && !session.getUserIdB().equals(callerId)) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_PARTICIPANT);
        }

        // 2. Validate all points
        Instant now = Instant.now();
        long    nowMs = now.toEpochMilli();
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

        // 5. Get next chunk index and persist
        int chunkIndex = chunkRepository.nextChunkIndex(sessionId);
        chunkRepository.saveChunk(sessionId, chunkIndex, polyline, timestampBytes, points.size());

        // 6. Return acknowledged local IDs
        List<Long> acknowledgedIds = new ArrayList<>(points.size());
        for (PushRoutePointsRequest.RoutePointPayload p : points) {
            acknowledgedIds.add(p.localId());
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
