package com.walkmate.application.tracking;

import com.walkmate.domain.session.SessionErrorCode;
import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.tracking.TrackingChunkRepository;
import com.walkmate.presentation.dto.response.session.SessionRouteResponse;
import com.walkmate.presentation.dto.response.tracking.PartnerPathResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TrackingQueryService {

    private final WalkSessionRepository  sessionRepository;
    private final TrackingChunkRepository chunkRepository;

    /**
     * Returns incremental partner GPS chunks for a live (ACTIVE) session.
     *
     * <p>Authorization:
     * <ul>
     *   <li>Caller must be a participant of the session.</li>
     *   <li>The global session status must be {@code ACTIVE}.</li>
     *   <li>Partner personal status is NOT checked — if the partner is still
     *       {@code PENDING} this returns an empty chunk list plus the status string
     *       so the client can show "Partner hasn't arrived yet" without treating it
     *       as an error.</li>
     * </ul>
     *
     * @param sessionId       the walk session
     * @param callerId        authenticated user's ID
     * @param afterChunkIndex only chunks with {@code chunk_index > afterChunkIndex} are
     *                        returned; pass {@code -1} on first call to fetch all chunks
     */
    @Transactional(readOnly = true)
    public PartnerPathResponse getPartnerPath(String sessionId, String callerId, int afterChunkIndex) {
        WalkSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new DomainException(SessionErrorCode.SESSION_NOT_FOUND));

        boolean isCallerUserA = callerId.equals(session.getUserIdA());
        if (!isCallerUserA && !callerId.equals(session.getUserIdB())) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_PARTICIPANT);
        }

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_ACTIVE);
        }

        String        partnerId     = isCallerUserA ? session.getUserIdB() : session.getUserIdA();
        SessionStatus partnerStatus = isCallerUserA ? session.getUserBStatus() : session.getUserAStatus();

        // Only query chunks when the partner has actually started walking.
        // For PENDING partner, return empty list — not an error.
        List<TrackingChunkRepository.ChunkRow> rows =
                (partnerStatus == SessionStatus.ACTIVE || partnerStatus == SessionStatus.COMPLETED)
                        ? chunkRepository.findChunksAfterIndex(sessionId, partnerId, afterChunkIndex)
                        : List.of();

        long lastChunkMs = rows.isEmpty() ? 0L : rows.get(rows.size() - 1).createdAtMs();

        List<PartnerPathResponse.PartnerChunk> chunks = rows.stream()
                .map(r -> new PartnerPathResponse.PartnerChunk(r.chunkIndex(), r.polyline()))
                .toList();

        return new PartnerPathResponse(chunks, lastChunkMs, partnerStatus.name());
    }

    /**
     * Returns the dual-path GPS route data for a finished session.
     * The caller must be a participant, and the session must be in a terminal state.
     */
    @Transactional(readOnly = true)
    public SessionRouteResponse getSessionRoute(String sessionId, String callerId) {
        WalkSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new DomainException(SessionErrorCode.SESSION_NOT_FOUND));

        if (!callerId.equals(session.getUserIdA()) && !callerId.equals(session.getUserIdB())) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_PARTICIPANT);
        }

        SessionStatus status = session.getStatus();
        if (status == SessionStatus.PENDING || status == SessionStatus.ACTIVE) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_FINISHED);
        }

        List<String> pathA = chunkRepository.findPolylinesBySessionAndUser(sessionId, session.getUserIdA());
        List<String> pathB = chunkRepository.findPolylinesBySessionAndUser(sessionId, session.getUserIdB());

        // Show the caller's own metrics in the route summary.
        boolean isCallerUserA = callerId.equals(session.getUserIdA());
        double  distanceKm    = isCallerUserA ? session.getUserADistanceKm()      : session.getUserBDistanceKm();
        long    durationSec   = isCallerUserA ? session.getUserADurationSeconds() : session.getUserBDurationSeconds();

        return new SessionRouteResponse(
                sessionId,
                pathA,
                pathB,
                distanceKm,
                (int) (durationSec / 60)
        );
    }
}
