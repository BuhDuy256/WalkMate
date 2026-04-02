package com.walkmate.presentation.controller.user;

import com.walkmate.application.user.UpdateFcmTokenCommand;
import com.walkmate.application.user.UpdateProfileCommand;
import com.walkmate.application.user.UserCommandService;
import com.walkmate.application.user.UserProfileCommandService;
import com.walkmate.application.user.UserQueryService;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.infrastructure.storage.AvatarStorageService;
import com.walkmate.presentation.dto.request.user.UpdateFcmTokenRequest;
import com.walkmate.presentation.dto.request.user.UpdateProfileRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.user.AvatarUploadResponse;
import com.walkmate.presentation.dto.response.user.UserProfileResponse;
import com.walkmate.presentation.mapper.user.UserProfileMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Profile", description = "User profile view and edit endpoints")
@RestController
@RequiredArgsConstructor
public class UserProfileController {

    private final UserQueryService           queryService;
    private final UserCommandService         userCommandService;
    private final UserProfileCommandService  commandService;
    private final UserProfileRepository      profileRepository;
    private final AvatarStorageService       storageService;
    private final UserProfileMapper          mapper;

    // ── GET /api/v1/profile/me ────────────────────────────────────────────────

    @GetMapping("/api/v1/profile/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId  = UUID.fromString(principal.userId());
        UserProfile  profile = queryService.getMyProfile(userId);
        User         user    = queryService.getUser(userId);
        List<String> tags    = profileRepository.findTagsByUserId(userId);

        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(profile, user, tags)));
    }

    // ── PUT /api/v1/profile/me ────────────────────────────────────────────────

    @PutMapping("/api/v1/profile/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {

        UUID callerId = UUID.fromString(principal.userId());

        LocalDate dob = null;
        if (request.dateOfBirth() != null && !request.dateOfBirth().isBlank()) {
            dob = LocalDate.parse(request.dateOfBirth());
        }

        UpdateProfileCommand command = new UpdateProfileCommand(
                callerId,
                request.fullName(),
                request.gender(),
                dob,
                request.bio(),
                request.searchRadius(),
                request.tags()
        );

        UserProfile  updated = commandService.updateProfile(command);
        User         user    = queryService.getUser(callerId);
        List<String> tags    = profileRepository.findTagsByUserId(callerId);

        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(updated, user, tags)));
    }

    // ── POST /api/v1/profile/avatar ───────────────────────────────────────────

    @PostMapping(value = "/api/v1/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AvatarUploadResponse>> uploadAvatar(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file) throws IOException {

        UUID   userId    = UUID.fromString(principal.userId());
        String avatarUrl = storageService.store(userId, file);
        commandService.updateAvatar(userId, avatarUrl);

        return ResponseEntity.ok(ApiResponse.success(new AvatarUploadResponse(avatarUrl)));
    }

    // ── GET /api/v1/users/{userId} ────────────────────────────────────────────

    @GetMapping("/api/v1/users/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getPublicProfile(
            @PathVariable String userId) {

        UUID         uid     = UUID.fromString(userId);
        UserProfile  profile = queryService.getProfile(uid);
        User         user    = queryService.getUser(uid);
        List<String> tags    = profileRepository.findTagsByUserId(uid);

        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(profile, user, tags)));
    }

    // ── PATCH /api/v1/users/me/fcm-token ─────────────────────────────────────

    /**
     * Registers or refreshes the device's FCM push token for the authenticated user.
     * Called automatically by the Android client whenever Firebase rotates the token.
     */
    @PatchMapping("/api/v1/users/me/fcm-token")
    public ResponseEntity<ApiResponse<Void>> updateFcmToken(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateFcmTokenRequest request) {

        userCommandService.updateFcmToken(
                new UpdateFcmTokenCommand(UUID.fromString(principal.userId()), request.fcmToken()));

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── GET /api/v1/files/avatars/{filename} ──────────────────────────────────

    @GetMapping("/api/v1/files/avatars/{filename:.+}")
    public ResponseEntity<Resource> serveAvatar(@PathVariable String filename) throws IOException {
        Path   filePath = storageService.resolve(filename);
        Resource resource = new PathResource(filePath);

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(filePath);
        if (contentType == null) contentType = "application/octet-stream";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
