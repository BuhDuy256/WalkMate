package com.walkmate.presentation.controller;

import com.walkmate.application.CancelWalkIntentService;
import com.walkmate.application.SubmitWalkIntentService;
import com.walkmate.domain.intent.WalkIntent;
import com.walkmate.presentation.dto.request.SubmitIntentRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.IntentResponse;
import com.walkmate.presentation.mapper.IntentMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intents")
@RequiredArgsConstructor
public class IntentController {

  private final SubmitWalkIntentService submitWalkIntentService;
  private final CancelWalkIntentService cancelWalkIntentService;
  private final IntentMapper mapper;

  // Assumes UserDetails username holds the UUID string of the authenticated user
  private UUID extractUserId(UserDetails user) {
    return UUID.fromString(user.getUsername());
  }

  @PostMapping
  public ApiResponse<IntentResponse> submit(
      @AuthenticationPrincipal UserDetails user,
      @Valid @RequestBody SubmitIntentRequest request) {
      
    UUID userId = extractUserId(user);
    WalkIntent intent = submitWalkIntentService.execute(userId, request);
    return ApiResponse.success(mapper.toResponse(intent));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<IntentResponse> cancel(
      @AuthenticationPrincipal UserDetails user,
      @PathVariable("id") UUID intentId) {
      
    UUID userId = extractUserId(user);
    WalkIntent intent = cancelWalkIntentService.execute(userId, intentId);
    return ApiResponse.success(mapper.toResponse(intent));
  }
}
