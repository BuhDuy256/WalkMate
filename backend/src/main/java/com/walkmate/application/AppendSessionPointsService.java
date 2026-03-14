package com.walkmate.application;

import com.walkmate.domain.session.DomainException;
import com.walkmate.domain.session.SessionRepository;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.valueobject.SessionPoint;
import com.walkmate.domain.valueobject.SessionTrackingStats;
import com.walkmate.infrastructure.exception.ErrorCode;
import com.walkmate.infrastructure.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppendSessionPointsService {
    private final SessionRepository sessionRepository;

    @Transactional
    public int execute(UUID userId, UUID sessionId, List<SessionPoint> points, SessionTrackingStats stats) {
        WalkSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (!session.getUser1Id().equals(userId) && !session.getUser2Id().equals(userId)) {
            throw new DomainException(ErrorCode.SESSION_NOT_PARTICIPANT, "user is not participant of this session");
        }

        session.updateTrackingSummary(stats);
        sessionRepository.save(session);
        return sessionRepository.appendSessionPoints(sessionId, points);
    }
}
