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
public class SweepMaxDurationWalksWorker {

    private final JpaSessionRepository repository;
    private final MaxDurationSubService maxDurationSubService;

    @Scheduled(fixedRate = 60000) // Runs every minute
    public void sweep() {
        List<WalkSession> targets = repository.findExpiredActiveSessionsForUpdate(100);
        for (WalkSession session : targets) {
            maxDurationSubService.forceCompleteIsolated(session.getSessionId());
        }
    }
}

@Service
@RequiredArgsConstructor
class MaxDurationSubService {
    private final JpaSessionRepository repository;
    private final Clock clock;

    // REQUIRES_NEW guarantees each chunk row executes in its own isolated transaction
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forceCompleteIsolated(java.util.UUID sessionId) {
        repository.findById(sessionId).ifPresent(session -> {
            session.systemForceComplete(clock);
            repository.save(session);
        });
    }
}
