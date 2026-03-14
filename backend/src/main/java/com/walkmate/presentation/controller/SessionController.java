package com.walkmate.presentation.controller;

import com.walkmate.application.AbortWalkSessionService;
import com.walkmate.application.ActivateWalkSessionService;
import com.walkmate.application.CancelWalkSessionService;
import com.walkmate.application.CompleteWalkSessionService;
import com.walkmate.application.GetWalkSessionService;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.valueobject.SessionTrackingStats;
import com.walkmate.presentation.dto.request.AbortSessionRequest;
import com.walkmate.presentation.dto.request.CancelSessionRequest;
import com.walkmate.presentation.dto.request.CompleteSessionRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.SessionResponse;
import com.walkmate.presentation.mapper.SessionMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {
  private final ActivateWalkSessionService activateService;
  private final CancelWalkSessionService cancelService;
  private final CompleteWalkSessionService completeService;
  private final AbortWalkSessionService abortService;
  private final GetWalkSessionService getService;
  private final SessionMapper mapper;

  @PostMapping("/{id}/activate")
  public ApiResponse<SessionResponse> activate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    WalkSession session = activateService.execute(extractUserId(jwt), id);
    return ApiResponse.success(mapper.toResponse(session));
  }

  @PostMapping("/{id}/cancel")
  public ApiResponse<SessionResponse> cancel(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CancelSessionRequest request) {
    WalkSession session = cancelService.execute(extractUserId(jwt), id, request.reason());
    return ApiResponse.success(mapper.toResponse(session));
  }

  @PostMapping("/{id}/complete")
  public ApiResponse<SessionResponse> complete(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CompleteSessionRequest request) {
    WalkSession session = completeService.execute(
        extractUserId(jwt),
        id,
        new SessionTrackingStats(request.distance(), request.duration()));
    return ApiResponse.success(mapper.toResponse(session));
  }

  @PostMapping("/{id}/abort")
  public ApiResponse<SessionResponse> abort(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AbortSessionRequest request) {
    WalkSession session = abortService.execute(extractUserId(jwt), id, request.reason());
    return ApiResponse.success(mapper.toResponse(session));
  }

  @GetMapping("/{id}")
  public ApiResponse<SessionResponse> getById(@PathVariable UUID id) {
    WalkSession session = getService.execute(id);
    return ApiResponse.success(mapper.toResponse(session));
  }

  private UUID extractUserId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
