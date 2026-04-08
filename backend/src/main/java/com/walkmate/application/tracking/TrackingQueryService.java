package com.walkmate.application.tracking;

import com.walkmate.domain.session.SessionErrorCode;
import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.tracking.TrackingChunkRepository;
import com.walkmate.presentation.dto.response.session.SessionRouteResponse;
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

        int durationMinutes = (int) (session.getTotalDurationSeconds() / 60);

        return new SessionRouteResponse(
                sessionId,
                pathA,
                pathB,
                session.getTotalDistanceKm(),
                durationMinutes
        );
    }
}
