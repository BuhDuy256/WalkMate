package com.walkmate.application.report;

import com.walkmate.domain.report.ReportErrorCode;
import com.walkmate.domain.report.SessionReport;
import com.walkmate.domain.report.SessionReportRepository;
import com.walkmate.domain.session.SessionErrorCode;
import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReportCommandService {

    private static final Duration COMPLETED_REPORT_WINDOW = Duration.ofHours(72);
    private static final Duration TERMINAL_REPORT_WINDOW  = Duration.ofHours(24);

    private final WalkSessionRepository   sessionRepository;
    private final SessionReportRepository reportRepository;

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

        // 4. Validate by session status
        Instant now = Instant.now();
        switch (session.getStatus()) {
            case PENDING:
            case CANCELLED:
                throw new DomainException(ReportErrorCode.REPORT_SESSION_INVALID_STATUS);

            case ACTIVE:
            case NO_SHOW:
                // always allowed
                break;

            case COMPLETED:
                if (now.isAfter(session.getEndedAt().plus(COMPLETED_REPORT_WINDOW))) {
                    throw new DomainException(ReportErrorCode.REPORT_WINDOW_EXPIRED);
                }
                break;

            case ABORTED:
                if (now.isAfter(session.getEndedAt().plus(TERMINAL_REPORT_WINDOW))) {
                    throw new DomainException(ReportErrorCode.REPORT_WINDOW_EXPIRED);
                }
                break;
        }

        // 5. Guard duplicate
        if (reportRepository.existsBySessionAndReporter(sessionId, reporterId)) {
            throw new DomainException(ReportErrorCode.REPORT_ALREADY_SUBMITTED);
        }

        // 6. Persist
        SessionReport report = SessionReport.create(sessionId, reporterId, reportedUserId, reason, evidenceUrl);
        reportRepository.save(report);
        return report;
    }
}
