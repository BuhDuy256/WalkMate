package com.walkmate.presentation.controller.report;

import com.walkmate.application.report.AdminReportCommandService;
import com.walkmate.application.report.AdminReportQueryService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.domain.report.SessionReport;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.presentation.dto.request.report.ResolveReportRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.report.AdminReportResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/reports")
@Tag(name = "Admin - Reports")
public class AdminReportController {

    private final AdminReportQueryService adminReportQueryService;
    private final AdminReportCommandService adminReportCommandService;
    private final UserProfileRepository userProfileRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminReportResponse>>> getReports(
            @RequestParam(required = false) String status) {

        List<SessionReport> reports;
        if (status != null && !status.isBlank()) {
            reports = adminReportQueryService.getReportsByStatus(status);
        } else {
            reports = adminReportQueryService.getAllReports();
        }

        Map<UUID, String> nameMap = batchLoadNames(reports);

        List<AdminReportResponse> responses = reports.stream()
                .map(r -> toAdminResponse(r, nameMap))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<AdminReportResponse>> getReport(@PathVariable String reportId) {
        SessionReport report = adminReportQueryService.getReportById(reportId);
        Map<UUID, String> nameMap = batchLoadNames(List.of(report));
        return ResponseEntity.ok(ApiResponse.success(toAdminResponse(report, nameMap)));
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

        Map<UUID, String> nameMap = batchLoadNames(List.of(report));
        return ResponseEntity.ok(ApiResponse.success(toAdminResponse(report, nameMap)));
    }

    private Map<UUID, String> batchLoadNames(List<SessionReport> reports) {
        Set<UUID> ids = reports.stream()
                .flatMap(r -> Stream.of(r.getReporterId(), r.getReportedUserId()))
                .filter(id -> id != null && !id.isBlank())
                .map(id -> {
                    try { return UUID.fromString(id); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(uuid -> uuid != null)
                .collect(Collectors.toSet());

        if (ids.isEmpty()) return Collections.emptyMap();
        return userProfileRepository.findNamesByUserIds(ids);
    }

    private AdminReportResponse toAdminResponse(SessionReport report, Map<UUID, String> nameMap) {
        String reporterName      = resolveName(report.getReporterId(), nameMap);
        String reportedUserName  = resolveName(report.getReportedUserId(), nameMap);
        return new AdminReportResponse(
                report.getReportId(),
                report.getSessionId(),
                report.getReporterId(),
                reporterName,
                report.getReportedUserId(),
                reportedUserName,
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

    private String resolveName(String userId, Map<UUID, String> nameMap) {
        if (userId == null || userId.isBlank()) return "Unknown";
        try {
            String name = nameMap.get(UUID.fromString(userId));
            return (name != null && !name.isBlank()) ? name : "Unknown";
        } catch (IllegalArgumentException e) {
            return "Unknown";
        }
    }
}
