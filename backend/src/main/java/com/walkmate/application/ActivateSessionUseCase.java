package com.walkmate.application;

import com.walkmate.domain.session.entity.WalkSession;
import com.walkmate.infrastructure.repository.JpaSessionRepository;
import com.walkmate.domain.session.exception.DomainRuleException;
import com.walkmate.infrastructure.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivateSessionUseCase {
    
    private final JpaSessionRepository repository;
    private final Clock clock; // Injected for deterministic testing

    @Transactional
    public WalkSession execute(UUID userId, UUID sessionId) {
        WalkSession session = repository.findById(sessionId)
            .orElseThrow(() -> new DomainRuleException(ErrorCode.SESSION_NOT_FOUND, "Session not found"));

        session.activate(userId, clock);
        return repository.save(session);
    }
}
