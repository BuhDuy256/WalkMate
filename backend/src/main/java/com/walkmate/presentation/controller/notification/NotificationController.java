package com.walkmate.presentation.controller.notification;

import com.walkmate.application.notification.NotificationCommandService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.notification.NotificationResponse;
import com.walkmate.presentation.mapper.notification.NotificationMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Notifications", description = "In-app notification feed")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationCommandService notificationCommandService;
    private final NotificationMapper         notificationMapper;

    /**
     * GET /api/v1/notifications
     * Returns all notifications for the authenticated user, newest first.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> listNotifications(
            @AuthenticationPrincipal UserPrincipal principal) {

        List<NotificationResponse> responses = notificationCommandService
                .listForUser(principal.userId())
                .stream()
                .map(notificationMapper::toResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * POST /api/v1/notifications/{notificationId}/read
     * Marks a notification as READ and sets read_at. Idempotent.
     */
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String notificationId) {

        notificationCommandService.markRead(notificationId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
