package com.walkmate.presentation.controller.profile;

import com.walkmate.application.profile.ProfileSetupPersistenceService;
import com.walkmate.presentation.dto.request.profile.SetupProfileRequest;
import com.walkmate.presentation.dto.response.ProfileResponse;
import com.walkmate.presentation.dto.response.ProfileSetupAckResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {
    private final ProfileSetupPersistenceService profileSetupPersistenceService;

    public ProfileController(ProfileSetupPersistenceService profileSetupPersistenceService) {
        this.profileSetupPersistenceService = profileSetupPersistenceService;
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
