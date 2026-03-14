package com.walkmate.application;

import com.walkmate.domain.session.SessionRepository;
import com.walkmate.domain.session.WalkSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcessSessionActivationWindowService {
    private final SessionRepository sessionRepository;
    private final Clock clock;

    @Transactional
    public int execute(int limit) {
        Instant now = clock.instant();
        List<WalkSession> sessions = sessionRepository.findExpiredPendingSessionsForUpdate(limit);
        int updated = 0;

        for (WalkSession session : sessions) {
            if (session.systemProcessActivationWindow(now)) {
                sessionRepository.save(session);
                updated++;
            }
        }

        return updated;
    }
}
