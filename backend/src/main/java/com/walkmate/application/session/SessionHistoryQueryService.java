package com.walkmate.application.session;

import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.presentation.dto.response.session.SessionSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionHistoryQueryService {

    private final WalkSessionRepository sessionRepository;

    /**
     * Returns a summary list of terminal sessions for the caller, newest first.
     */
    @Transactional(readOnly = true)
    public List<SessionSummaryResponse> getSessionHistory(String callerId) {
        List<WalkSession> sessions = sessionRepository.findCompletedByUserId(callerId);
        return sessions.stream()
                .map(s -> toSummary(s, callerId))
                .collect(Collectors.toList());
    }

    private SessionSummaryResponse toSummary(WalkSession s, String callerId) {
        String partnerId = callerId.equals(s.getUserIdA()) ? s.getUserIdB() : s.getUserIdA();
        return new SessionSummaryResponse(
                s.getSessionId(),
                s.getStatus().name(),
                partnerId,
                s.getScheduledStart().toString(),
                s.getScheduledEnd().toString(),
                s.getTotalDistanceKm(),
                (int) (s.getTotalDurationSeconds() / 60)
        );
    }
}
