package com.walkmate.presentation.dto.response.report;

public record AdminReportResponse(
    String reportId,
    String sessionId,
    String reporterId,
    String reportedUserId,
    String reason,
    String evidenceUrl,
    String status,
    int appliedTrustDelta,
    String createdAt,
    String resolvedBy,
    String resolvedAt,
    String resolutionNote
) {}
