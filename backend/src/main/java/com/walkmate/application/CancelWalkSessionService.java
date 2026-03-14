package com.walkmate.application;

import com.walkmate.domain.session.SessionRepository;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.infrastructure.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CancelWalkSessionService {
    private final SessionRepository sessionRepository;
    private final Clock clock;

    @Transactional
    public WalkSession execute(UUID userId, UUID sessionId, String reason) {
        WalkSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        session.cancel(userId, reason, clock.instant());
        return sessionRepository.save(session);
    }
}
