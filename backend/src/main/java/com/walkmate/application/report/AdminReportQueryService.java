package com.walkmate.application.report;

import com.walkmate.domain.report.ReportErrorCode;
import com.walkmate.domain.report.SessionReport;
import com.walkmate.domain.report.SessionReportRepository;
import com.walkmate.domain.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminReportQueryService {

    private final SessionReportRepository reportRepository;

    public List<SessionReport> getAllReports() {
        return reportRepository.findAll();
    }

    public List<SessionReport> getReportsByStatus(String status) {
        if (!"OPEN".equals(status) && !"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new DomainException(ReportErrorCode.REPORT_INVALID_RESOLUTION);
        }
        return reportRepository.findByStatus(status);
    }

    public SessionReport getReportById(String reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new DomainException(ReportErrorCode.REPORT_NOT_FOUND));
    }
}
