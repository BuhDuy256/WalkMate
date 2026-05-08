package com.walkmate.application.report;

import com.walkmate.application.walkintent.AiTrainingService;
import com.walkmate.domain.report.ReportErrorCode;
import com.walkmate.domain.report.SessionReport;
import com.walkmate.domain.report.SessionReportRepository;
import com.walkmate.domain.review.TrustScorePolicy;
import com.walkmate.domain.session.SessionErrorCode;
import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserErrorCode;
import com.walkmate.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportCommandService {

    /**
     * Reporting window for COMPLETED sessions (hours).
     * Override with {@code app.report.completed-window-hours} in application.properties.
     * Default: 72 hours.
     */
    @Value("${app.report.completed-window-hours:72}")
    private long completedWindowHours;

    private final WalkSessionRepository   sessionRepository;
    private final SessionReportRepository reportRepository;
    private final UserRepository          userRepository;
    private final AiTrainingService       aiTrainingService;

    @Transactional
    public SessionReport submitReport(String sessionId, String reporterId,
                                      String reportedUserId, String reason, String evidenceUrl) {

        // 1. Load session
        WalkSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new DomainException(SessionErrorCode.SESSION_NOT_FOUND));

        // 2. Verify reporter is a participant
        boolean isParticipant = reporterId.equals(session.getUserIdA())
                || reporterId.equals(session.getUserIdB());
        if (!isParticipant) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_PARTICIPANT);
        }

        // 3. Guard self-report
        if (reporterId.equals(reportedUserId)) {
            throw new DomainException(ReportErrorCode.REPORT_SELF_NOT_ALLOWED);
        }

        // 4. Validate by session status — only COMPLETED sessions are reportable
        Instant now = Instant.now();
        switch (session.getStatus()) {
            case PENDING:
            case ACTIVE:
            case CANCELLED:
                throw new DomainException(ReportErrorCode.REPORT_SESSION_INVALID_STATUS);

            case COMPLETED:
                if (now.isAfter(session.getEndedAt().plus(Duration.ofHours(completedWindowHours)))) {
                    throw new DomainException(ReportErrorCode.REPORT_WINDOW_EXPIRED);
                }
                break;

            default:
                throw new DomainException(ReportErrorCode.REPORT_SESSION_INVALID_STATUS);
        }

        // 5. Reporter eligibility — a NO_SHOW participant has no standing to report
        // Invariant S-5: session is globally COMPLETED even if one participant is NO_SHOW.
        // We must inspect the reporter's personal status, not the global status.
        SessionStatus reporterPersonalStatus = reporterId.equals(session.getUserIdA())
                ? session.getUserAStatus()
                : session.getUserBStatus();
        if (reporterPersonalStatus == SessionStatus.NO_SHOW) {
            throw new DomainException(ReportErrorCode.REPORT_REPORTER_NO_SHOW);
        }

        // 6. Guard duplicate
        if (reportRepository.existsBySessionAndReporter(sessionId, reporterId)) {
            throw new DomainException(ReportErrorCode.REPORT_ALREADY_SUBMITTED);
        }

        // 7. Compute the trust penalty delta before creating the report so the
        //    applied value is embedded in the INSERT (not a separate UPDATE).
        int theoreticalDelta = TrustScorePolicy.deltaForReason(reason);

        // No-show double-penalty guard: if the reported user is already a NO_SHOW
        // they have received -100 from gamification; do not stack a report penalty.
        SessionStatus reportedUserPersonalStatus = reportedUserId.equals(session.getUserIdA())
                ? session.getUserAStatus()
                : session.getUserBStatus();
        int actualDelta = (reportedUserPersonalStatus == SessionStatus.NO_SHOW) ? 0 : theoreticalDelta;

        // 8. Apply trust penalty synchronously (same transaction as the report save)
        if (actualDelta != 0) {
            User reportedUser = userRepository.findById(reportedUserId)
                    .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));
            int newScore = TrustScorePolicy.apply(reportedUser.getTrustScore(), actualDelta);
            reportedUser.applyTrustScore(newScore);
            userRepository.save(reportedUser);
        }

        // 9. Persist report — applied delta is recorded atomically in the same INSERT
        SessionReport report = SessionReport.create(sessionId, reporterId, reportedUserId, reason, evidenceUrl);
        report.setAppliedTrustDelta(actualDelta);
        reportRepository.save(report);

        // Task 2.2a: AI weight training is intentionally deferred to admin resolution.
        // Firing here would let false reports permanently inflate the reporter's
        // weightBehavior with no rollback path when an admin later rejects the report.
        // See AdminReportCommandService.resolveReport() for the training trigger.

        return report;
    }
}
