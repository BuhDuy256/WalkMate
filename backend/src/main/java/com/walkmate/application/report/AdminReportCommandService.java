package com.walkmate.application.report;

import com.walkmate.domain.report.ReportErrorCode;
import com.walkmate.domain.report.SessionReport;
import com.walkmate.domain.report.SessionReportRepository;
import com.walkmate.domain.review.TrustScorePolicy;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserErrorCode;
import com.walkmate.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminReportCommandService {

    private final SessionReportRepository reportRepository;
    private final UserRepository userRepository;

    @Transactional
    public SessionReport resolveReport(String reportId, String adminUserId, String resolution, String note) {
        SessionReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new DomainException(ReportErrorCode.REPORT_NOT_FOUND));

        if (!"APPROVED".equals(resolution) && !"REJECTED".equals(resolution)) {
            throw new DomainException(ReportErrorCode.REPORT_INVALID_RESOLUTION);
        }

        if (report.isResolved()) {
            throw new DomainException(ReportErrorCode.REPORT_ALREADY_RESOLVED);
        }

        if ("APPROVED".equals(resolution)) {
            report.approve(adminUserId, note);
            reportRepository.update(report);
            return report;
        }

        // resolution is REJECTED
        report.reject(adminUserId, note);
        reportRepository.update(report);

        if (report.getAppliedTrustDelta() < 0) {
            User reportedUser = userRepository.findById(report.getReportedUserId())
                    .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

            int reversalDelta = -report.getAppliedTrustDelta();
            int newScore = TrustScorePolicy.apply(reportedUser.getTrustScore(), reversalDelta);
            
            reportedUser.applyTrustScore(newScore);
            userRepository.save(reportedUser);
        }

        return report;
    }
}
