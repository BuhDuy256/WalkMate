package com.walkmate.presentation.controller.profile;

import com.walkmate.application.profile.ProfileAvatarUploadService;
import com.walkmate.application.profile.ProfileSetupPersistenceService;
import com.walkmate.presentation.dto.response.ProfileAvatarUploadResponse;
import com.walkmate.presentation.dto.request.profile.SetupProfileRequest;
import com.walkmate.presentation.dto.response.ProfileResponse;
import com.walkmate.presentation.dto.response.ProfileSetupAckResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {
    private final ProfileAvatarUploadService profileAvatarUploadService;
    private final ProfileSetupPersistenceService profileSetupPersistenceService;

    public ProfileController(
            ProfileAvatarUploadService profileAvatarUploadService,
            ProfileSetupPersistenceService profileSetupPersistenceService
    ) {
        this.profileAvatarUploadService = profileAvatarUploadService;
        this.profileSetupPersistenceService = profileSetupPersistenceService;
    }

    @PostMapping(value = "/{userId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfileAvatarUploadResponse> uploadAvatar(
            @PathVariable UUID userId,
            @RequestPart("file") MultipartFile file
    ) {
        String avatarUrl = profileAvatarUploadService.uploadAvatar(userId, file);
        return ResponseEntity.ok(new ProfileAvatarUploadResponse(avatarUrl));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<ProfileSetupAckResponse> setupProfile(
            @PathVariable UUID userId,
            @Valid @RequestBody SetupProfileRequest request
    ) {
        profileSetupPersistenceService.saveProfile(userId, request);
        return ResponseEntity.status(HttpStatus.OK).body(new ProfileSetupAckResponse(true, "Đã lưu thành công"));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ProfileResponse> getProfile(
            @PathVariable UUID userId,
            @RequestParam(required = false) UUID viewerId
    ) {
        // Keep viewerId in signature for backward compatibility while auth/view permissions are not wired yet.
        return ResponseEntity.ok(profileSetupPersistenceService.getProfile(userId));
    }
}
