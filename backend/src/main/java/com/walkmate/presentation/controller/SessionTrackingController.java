package com.walkmate.presentation.controller;

import com.walkmate.application.AppendSessionPointsService;
import com.walkmate.domain.valueobject.SessionPoint;
import com.walkmate.domain.valueobject.SessionTrackingStats;
import com.walkmate.presentation.dto.request.AppendSessionPointsRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.SessionTrackingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionTrackingController {
  private final AppendSessionPointsService appendSessionPointsService;

  @PostMapping("/{id}/points:append")
  public ApiResponse<SessionTrackingResponse> appendPoints(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AppendSessionPointsRequest request) {
    List<SessionPoint> points = request.points().stream()
        .map(point -> new SessionPoint(point.pointOrder(), point.lat(), point.lng(), point.time()))
        .toList();

    int count = appendSessionPointsService.execute(
        UUID.fromString(jwt.getSubject()),
        id,
        points,
        new SessionTrackingStats(request.totalDistance(), request.totalDuration()));

    return ApiResponse.success(new SessionTrackingResponse(id, count, "Points appended"));
  }
}
