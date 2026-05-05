package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.report.AdminReportResponse;
import com.walkmate.domain.report.AdminReport;

public class AdminReportMapper {

    public AdminReport toDomain(AdminReportResponse dto) {
        return new AdminReport(
                dto.reportId,
                dto.sessionId,
                dto.reporterId,
                dto.reporterName != null ? dto.reporterName : "Unknown",
                dto.reportedUserId,
                dto.reportedUserName != null ? dto.reportedUserName : "Unknown",
                mapReason(dto.reason),
                dto.evidenceUrl,
                mapStatus(dto.status),
                dto.appliedTrustDelta,
                dto.createdAt,
                dto.resolvedBy,
                dto.resolvedAt,
                dto.resolutionNote
        );
    }

    private AdminReport.Status mapStatus(String raw) {
        if ("OPEN".equalsIgnoreCase(raw))     return AdminReport.Status.PENDING;
        if ("APPROVED".equalsIgnoreCase(raw)) return AdminReport.Status.APPROVED;
        if ("REJECTED".equalsIgnoreCase(raw)) return AdminReport.Status.REJECTED;
        return AdminReport.Status.PENDING;
    }

    private AdminReport.Reason mapReason(String raw) {
        if (raw == null) return AdminReport.Reason.OTHER;
        switch (raw.toUpperCase()) {
            case "SAFETY_CONCERN":      return AdminReport.Reason.SAFETY_CONCERN;
            case "PARTNER_MISCONDUCT":
            case "MISCONDUCT":          return AdminReport.Reason.MISCONDUCT;
            case "EMERGENCY":           return AdminReport.Reason.EMERGENCY;
            default:                    return AdminReport.Reason.OTHER;
        }
    }
}
