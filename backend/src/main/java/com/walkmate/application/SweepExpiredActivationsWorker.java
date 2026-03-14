package com.walkmate.application;

import com.walkmate.domain.session.entity.WalkSession;
import com.walkmate.infrastructure.repository.JpaSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SweepExpiredActivationsWorker {

    private final JpaSessionRepository repository;
    private final SessionExpirationSubService expirationSubService;

    @Scheduled(fixedRate = 10000) // Runs every 10s
    public void sweep() {
        List<WalkSession> targets = repository.findExpiredPendingSessionsForUpdate(100);
        for (WalkSession session : targets) {
            expirationSubService.expireIsolated(session.getSessionId());
        }
    }
}

@Service
@RequiredArgsConstructor
class SessionExpirationSubService {
    private final JpaSessionRepository repository;
    private final Clock clock;

    // REQUIRES_NEW guarantees each chunk row executes in its own isolated transaction
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireIsolated(java.util.UUID sessionId) {
        repository.findById(sessionId).ifPresent(session -> {
            session.systemExpireToNoShowOrCancelled(clock);
            repository.save(session);
        });
    }
}
