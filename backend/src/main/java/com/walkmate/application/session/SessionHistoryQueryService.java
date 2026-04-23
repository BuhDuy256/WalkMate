package com.walkmate.application.session;

import com.walkmate.domain.review.WalkReviewRepository;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.presentation.dto.response.session.ParticipantSummaryResponse;
import com.walkmate.presentation.dto.response.session.SessionSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionHistoryQueryService {

    private final WalkSessionRepository sessionRepository;
    private final WalkReviewRepository  reviewRepository;
    private final UserProfileRepository profileRepository;

    /**
     * Returns a summary list of terminal sessions for the caller, newest first.
     * Partner names are resolved via a single batch query to avoid N+1 lookups.
     */
    @Transactional(readOnly = true)
    public List<SessionSummaryResponse> getSessionHistory(String callerId) {
        List<WalkSession> sessions = sessionRepository.findCompletedByUserId(callerId);
        if (sessions.isEmpty()) return Collections.emptyList();

        Set<UUID> allParticipantIds = new HashSet<>();
        for (WalkSession s : sessions) {
            allParticipantIds.add(UUID.fromString(s.getUserIdA()));
            allParticipantIds.add(UUID.fromString(s.getUserIdB()));
        }
        Map<UUID, String> names = profileRepository.findNamesByUserIds(allParticipantIds);

        return sessions.stream()
                .map(s -> toSummary(s, callerId, names))
                .collect(Collectors.toList());
    }

    private SessionSummaryResponse toSummary(WalkSession s, String callerId,
                                              Map<UUID, String> names) {
        boolean isReviewed = reviewRepository.existsBySessionAndReviewer(s.getSessionId(), callerId);

        UUID userIdA = UUID.fromString(s.getUserIdA());
        UUID userIdB = UUID.fromString(s.getUserIdB());

        ParticipantSummaryResponse participantA = new ParticipantSummaryResponse(
                s.getUserIdA(),
                names.getOrDefault(userIdA, "Unknown"),
                s.getUserADistanceKm(),
                (int) (s.getUserADurationSeconds() / 60)
        );
        ParticipantSummaryResponse participantB = new ParticipantSummaryResponse(
                s.getUserIdB(),
                names.getOrDefault(userIdB, "Unknown"),
                s.getUserBDistanceKm(),
                (int) (s.getUserBDurationSeconds() / 60)
        );

        String endedAt = s.getEndedAt() != null ? s.getEndedAt().toString() : null;

        return new SessionSummaryResponse(
                s.getSessionId(),
                s.getStatus().name(),
                s.getScheduledStart().toString(),
                endedAt,
                isReviewed,
                List.of(participantA, participantB)
        );
    }
}
