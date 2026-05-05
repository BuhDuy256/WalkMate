package com.walkmate.application.session;

import com.walkmate.domain.report.SessionReport;
import com.walkmate.domain.report.SessionReportRepository;
import com.walkmate.domain.review.ReviewTagRepository;
import com.walkmate.domain.review.WalkReview;
import com.walkmate.domain.review.WalkReviewRepository;
import com.walkmate.domain.session.SessionErrorCode;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserProfileSnapshot;
import com.walkmate.presentation.dto.response.session.ParticipantSummaryResponse;
import com.walkmate.presentation.dto.response.session.SessionSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionHistoryQueryService {

    private final WalkSessionRepository   sessionRepository;
    private final WalkReviewRepository    reviewRepository;
    private final ReviewTagRepository     tagRepository;
    private final SessionReportRepository reportRepository;
    private final UserProfileRepository   profileRepository;

    /**
     * Returns the summary for a single session (review + report snapshot included).
     * Used by the post-session Review and Report screens to pre-fill previously
     * submitted form data without loading the entire history list.
     */
    @Transactional(readOnly = true)
    public SessionSummaryResponse getSessionSummary(String sessionId, String callerId) {
        WalkSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new DomainException(SessionErrorCode.SESSION_NOT_FOUND));

        UUID userIdA = UUID.fromString(session.getUserIdA());
        UUID userIdB = UUID.fromString(session.getUserIdB());
        Map<UUID, UserProfileSnapshot> snapshots =
                profileRepository.findSnapshotsByUserIds(Set.of(userIdA, userIdB));

        return toSummary(session, callerId, snapshots);
    }

    /**
     * Returns a summary list of terminal sessions for the caller, newest first.
     * Partner names are resolved via a single batch query to avoid N+1 lookups.
     */
    @Transactional(readOnly = true)
    public List<SessionSummaryResponse> getSessionHistory(String callerId) {
        List<WalkSession> sessions = sessionRepository.findHistoryByUserId(callerId);
        if (sessions.isEmpty()) return Collections.emptyList();

        Set<UUID> allParticipantIds = new HashSet<>();
        for (WalkSession s : sessions) {
            allParticipantIds.add(UUID.fromString(s.getUserIdA()));
            allParticipantIds.add(UUID.fromString(s.getUserIdB()));
        }
        Map<UUID, UserProfileSnapshot> snapshots = profileRepository.findSnapshotsByUserIds(allParticipantIds);

        return sessions.stream()
                .map(s -> toSummary(s, callerId, snapshots))
                .collect(Collectors.toList());
    }

    private SessionSummaryResponse toSummary(WalkSession s, String callerId,
                                              Map<UUID, UserProfileSnapshot> snapshots) {
        boolean isReviewed  = reviewRepository.existsBySessionAndReviewer(s.getSessionId(), callerId);
        boolean isReported  = reportRepository.existsBySessionAndReporter(s.getSessionId(), callerId);

        SessionSummaryResponse.ReviewSnapshot reviewSnap = null;
        if (isReviewed) {
            Optional<WalkReview> maybeReview = reviewRepository.findBySessionAndReviewer(s.getSessionId(), callerId);
            if (maybeReview.isPresent()) {
                WalkReview review = maybeReview.get();
                List<String> tagIds = tagRepository.findTagIdsByReviewId(review.getReviewId());
                reviewSnap = new SessionSummaryResponse.ReviewSnapshot(
                        review.getRatingStars(), review.getComment(), tagIds);
            }
        }

        SessionSummaryResponse.ReportSnapshot reportSnap = null;
        if (isReported) {
            Optional<SessionReport> maybeReport = reportRepository.findBySessionAndReporter(s.getSessionId(), callerId);
            if (maybeReport.isPresent()) {
                SessionReport report = maybeReport.get();
                reportSnap = new SessionSummaryResponse.ReportSnapshot(
                        report.getReason(), report.getEvidenceUrl());
            }
        }

        UUID userIdA = UUID.fromString(s.getUserIdA());
        UUID userIdB = UUID.fromString(s.getUserIdB());

        UserProfileSnapshot snapA = snapshots.getOrDefault(userIdA, new UserProfileSnapshot("Unknown", null));
        UserProfileSnapshot snapB = snapshots.getOrDefault(userIdB, new UserProfileSnapshot("Unknown", null));

        ParticipantSummaryResponse participantA = new ParticipantSummaryResponse(
                s.getUserIdA(),
                snapA.fullName(),
                snapA.avatarUrl(),
                s.getUserADistanceKm(),
                (int) (s.getUserADurationSeconds() / 60),
                s.getUserAStatus().name()
        );
        ParticipantSummaryResponse participantB = new ParticipantSummaryResponse(
                s.getUserIdB(),
                snapB.fullName(),
                snapB.avatarUrl(),
                s.getUserBDistanceKm(),
                (int) (s.getUserBDurationSeconds() / 60),
                s.getUserBStatus().name()
        );

        String endedAt = s.getEndedAt() != null ? s.getEndedAt().toString() : null;

        UserProfileSnapshot callerSnap = callerId.equals(s.getUserIdA()) ? snapA : snapB;
        String callerAvatarUrl = callerSnap.avatarUrl();

        return new SessionSummaryResponse(
                s.getSessionId(),
                s.getStatus().name(),
                s.getScheduledStart().toString(),
                endedAt,
                isReviewed,
                isReported,
                reviewSnap,
                reportSnap,
                s.getMeetingPointLat(),
                s.getMeetingPointLng(),
                List.of(participantA, participantB),
                callerAvatarUrl,
                s.getHotspotName()
        );
    }
}
