package com.walkmate.presentation.controller;

import com.walkmate.application.ActivateSessionUseCase;
import com.walkmate.application.EmergencyAbortUseCase;
import com.walkmate.presentation.dto.request.EmergencyAbortRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.UUID;

// CustomUserDetails wrapper assumed from Spring Security context
import com.walkmate.infrastructure.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final ActivateSessionUseCase activateSessionUseCase;
    private final EmergencyAbortUseCase emergencyAbortUseCase;

    @PostMapping("/{id}/activate")
    public ApiResponse<Void> activateSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails user) {
        
        activateSessionUseCase.execute(user.getId(), id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/abort")
    public ApiResponse<Void> abortSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody EmergencyAbortRequest request) {
        
        emergencyAbortUseCase.execute(user.getId(), id, request.reason());
        return ApiResponse.success(null);
    }
}
