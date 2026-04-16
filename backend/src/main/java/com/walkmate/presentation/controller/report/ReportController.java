package com.walkmate.presentation.controller.report;

import com.walkmate.application.report.ReportCommandService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.domain.report.SessionReport;
import com.walkmate.presentation.dto.request.report.SubmitReportRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Reports", description = "Session incident reporting")
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportCommandService reportCommandService;

    /**
     * POST /api/v1/sessions/{sessionId}/report
     *
     * Submits an incident report against a participant in the given session.
     * The reporter must be a participant. Duplicate reports from the same reporter
     * are rejected. Time-window rules apply for post-session reports.
     */
    @PostMapping("/api/v1/sessions/{sessionId}/report")
    public ResponseEntity<ApiResponse<Map<String, String>>> submitReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId,
            @Valid @RequestBody SubmitReportRequest request) {

        SessionReport report = reportCommandService.submitReport(
                sessionId,
                principal.userId(),
                request.reportedUserId(),
                request.reason(),
                request.evidenceUrl());

        Map<String, String> body = Map.of(
                "reportId",  report.getReportId(),
                "createdAt", report.getCreatedAt().toString()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(body));
    }
}
