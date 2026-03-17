package com.walkmate.presentation.controller;

import com.walkmate.application.CreateTestWalkSessionService;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.presentation.dto.request.CreateTestSessionRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.SessionResponse;
import com.walkmate.presentation.mapper.SessionMapper;
import com.walkmate.presentation.util.UserIdentityExtractor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test/sessions")
@RequiredArgsConstructor
public class SessionTestController {
    private final CreateTestWalkSessionService createTestWalkSessionService;
    private final SessionMapper mapper;

    @PostMapping
    public ApiResponse<SessionResponse> createTestSession(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @Valid @RequestBody CreateTestSessionRequest request) {
        // Reuse existing identity extraction to keep security behavior consistent.
        UserIdentityExtractor.extractUserId(jwt, userIdHeader);

        WalkSession session = createTestWalkSessionService.execute(
                request.user1Id(),
                request.user2Id(),
                request.scheduledStartTime(),
                request.scheduledEndTime(),
                request.mutualConfirmation());

        return ApiResponse.success(mapper.toResponse(session));
    }
}
