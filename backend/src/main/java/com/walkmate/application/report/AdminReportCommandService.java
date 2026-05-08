package com.walkmate.application.report;

import com.walkmate.application.walkintent.AiTrainingService;
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

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminReportCommandService {

    private final SessionReportRepository reportRepository;
    private final UserRepository          userRepository;
    private final AiTrainingService       aiTrainingService;

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

            // Task 2.2b: AI weight training fires only after admin confirms the report
            // is genuine. This prevents false/malicious reports from permanently inflating
            // the reporter's weightBehavior before any review takes place.
            aiTrainingService.trainWeightsFromReport(
                    UUID.fromString(report.getReporterId()), report.getReason());

            return report;
        }

        // resolution is REJECTED — reverse the trust penalty on the reported user.
        // The reporter's AI weights are intentionally left unchanged: since training
        // was deferred to this resolution step, there is nothing to roll back.
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
