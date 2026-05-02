package com.walkmate.presentation.controller.report;

import com.walkmate.application.report.AdminReportCommandService;
import com.walkmate.application.report.AdminReportQueryService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.domain.report.SessionReport;
import com.walkmate.presentation.dto.request.report.ResolveReportRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.report.AdminReportResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/reports")
@Tag(name = "Admin - Reports")
public class AdminReportController {

    private final AdminReportQueryService adminReportQueryService;
    private final AdminReportCommandService adminReportCommandService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminReportResponse>>> getReports(
            @RequestParam(required = false) String status) {
        
        List<SessionReport> reports;
        if (status != null && !status.isBlank()) {
            reports = adminReportQueryService.getReportsByStatus(status);
        } else {
            reports = adminReportQueryService.getAllReports();
        }

        List<AdminReportResponse> responses = reports.stream()
                .map(this::toAdminResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<AdminReportResponse>> getReport(@PathVariable String reportId) {
        SessionReport report = adminReportQueryService.getReportById(reportId);
        return ResponseEntity.ok(ApiResponse.success(toAdminResponse(report)));
    }

    @PatchMapping("/{reportId}/resolve")
    public ResponseEntity<ApiResponse<AdminReportResponse>> resolveReport(
            @PathVariable String reportId,
            @Valid @RequestBody ResolveReportRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        SessionReport report = adminReportCommandService.resolveReport(
                reportId,
                principal.userId(),
                request.resolution(),
                request.resolutionNote()
        );

        return ResponseEntity.ok(ApiResponse.success(toAdminResponse(report)));
    }

    private AdminReportResponse toAdminResponse(SessionReport report) {
        return new AdminReportResponse(
                report.getReportId(),
                report.getSessionId(),
                report.getReporterId(),
                report.getReportedUserId(),
                report.getReason(),
                report.getEvidenceUrl(),
                report.getStatus(),
                report.getAppliedTrustDelta(),
                report.getCreatedAt() != null ? report.getCreatedAt().toString() : null,
                report.getResolvedBy(),
                report.getResolvedAt() != null ? report.getResolvedAt().toString() : null,
                report.getResolutionNote()
        );
    }
}
