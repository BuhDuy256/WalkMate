package com.walkmate.application.session;

import com.walkmate.domain.review.WalkReviewRepository;
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
    private final WalkReviewRepository  reviewRepository;

    /**
     * Returns a summary list of terminal sessions for the caller, newest first.
     * Each entry includes {@code is_reviewed} so the UI can show/hide the
     * "Leave a Review" button without an extra round-trip.
     */
    @Transactional(readOnly = true)
    public List<SessionSummaryResponse> getSessionHistory(String callerId) {
        List<WalkSession> sessions = sessionRepository.findCompletedByUserId(callerId);
        return sessions.stream()
                .map(s -> toSummary(s, callerId))
                .collect(Collectors.toList());
    }

    private SessionSummaryResponse toSummary(WalkSession s, String callerId) {
        boolean isCallerUserA = callerId.equals(s.getUserIdA());
        String  partnerId     = isCallerUserA ? s.getUserIdB() : s.getUserIdA();
        boolean isReviewed    = reviewRepository.existsBySessionAndReviewer(s.getSessionId(), callerId);
        double  distanceKm    = isCallerUserA ? s.getUserADistanceKm()      : s.getUserBDistanceKm();
        long    durationSec   = isCallerUserA ? s.getUserADurationSeconds() : s.getUserBDurationSeconds();
        return new SessionSummaryResponse(
                s.getSessionId(),
                s.getStatus().name(),
                partnerId,
                s.getScheduledStart().toString(),
                s.getScheduledEnd().toString(),
                distanceKm,
                (int) (durationSec / 60),
                isReviewed
        );
    }
}
