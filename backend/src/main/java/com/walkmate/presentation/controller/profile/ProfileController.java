package com.walkmate.presentation.controller.profile;

import com.walkmate.application.profile.ProfileCommandService;
import com.walkmate.application.profile.ProfileQueryService;
import com.walkmate.domain.profile.InfoVisibilityMode;
import com.walkmate.domain.profile.Profile;
import com.walkmate.domain.profile.ProfileErrorCode;
import com.walkmate.domain.profile.ProfileMode;
import com.walkmate.domain.profile.ProfileTag;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.presentation.dto.request.profile.SetupProfileRequest;
import com.walkmate.presentation.dto.response.ProfileResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {
    private final ProfileCommandService profileCommandService;
    private final ProfileQueryService profileQueryService;

    public ProfileController(ProfileCommandService profileCommandService, ProfileQueryService profileQueryService) {
        this.profileCommandService = profileCommandService;
        this.profileQueryService = profileQueryService;
    }

    @PutMapping("/setup")
    public ResponseEntity<ProfileResponse> setupProfile(@Valid @RequestBody SetupProfileRequest request) {
        Profile profile = mapToDomain(request);
        Profile saved = profileCommandService.setupProfile(profile);
        return ResponseEntity.status(HttpStatus.OK).body(mapToResponse(saved));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ProfileResponse> getProfile(
            @PathVariable UUID userId,
            @RequestParam(required = false) UUID viewerId
    ) {
        UUID resolvedViewerId = viewerId != null ? viewerId : userId;
        Profile profile = profileQueryService.getProfileForViewer(userId, resolvedViewerId);
        return ResponseEntity.ok(mapToResponse(profile));
    }

    private Profile mapToDomain(SetupProfileRequest request) {
        List<ProfileTag> tags;
        try {
            tags = request.getTags().stream()
                    .map(ProfileTag::fromCode)
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw new DomainException(ProfileErrorCode.PROFILE_INVALID_TAG.name(), e.getMessage());
        }

        ProfileMode profileMode;
        InfoVisibilityMode infoVisibilityMode;
        try {
            profileMode = ProfileMode.fromCode(request.getProfileMode());
            infoVisibilityMode = InfoVisibilityMode.fromCode(request.getInfoVisibilityMode());
        } catch (IllegalArgumentException e) {
            throw new DomainException(ProfileErrorCode.PROFILE_INVALID_MODE.name(), e.getMessage());
        }

        try {
            return new Profile(
                    request.getUserId(),
                    request.getFullName(),
                    request.getCity(),
                    request.getAvatarUrl(),
                    request.getBio(),
                    profileMode,
                    infoVisibilityMode,
                    tags
            );
        } catch (IllegalArgumentException e) {
            throw new DomainException(ProfileErrorCode.PROFILE_INVALID_NAME.name(), e.getMessage());
        }
    }

    private ProfileResponse mapToResponse(Profile profile) {
        List<String> tags = profile.getTags().stream()
                .map(ProfileTag::toCode)
                .collect(Collectors.toList());

        return new ProfileResponse(
                profile.getUserId(),
                profile.getFullName(),
                profile.getCity(),
                profile.getAvatarUrl(),
                profile.getBio(),
                profile.getProfileMode().name(),
                profile.getInfoVisibilityMode().name(),
                tags,
                profile.getEmail(),
                profile.getPhone(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
