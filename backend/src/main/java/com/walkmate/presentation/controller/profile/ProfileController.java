package com.walkmate.presentation.controller.profile;

import com.walkmate.application.profile.ProfileQueryService;
import com.walkmate.domain.profile.Profile;
import com.walkmate.domain.profile.ProfileTag;
import com.walkmate.presentation.dto.request.profile.SetupProfileRequest;
import com.walkmate.presentation.dto.response.ProfileResponse;
import com.walkmate.presentation.dto.response.ProfileSetupAckResponse;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {
    private final ProfileQueryService profileQueryService;

    public ProfileController(ProfileQueryService profileQueryService) {
        this.profileQueryService = profileQueryService;
    }

    @PutMapping("/setup")
    public ResponseEntity<ProfileSetupAckResponse> setupProfile(@Valid @RequestBody SetupProfileRequest request) {
        // TEMPORARY MODE: only intake/validate payload from FE for Postman testing.
        // TODO: Persist these fields to DB by mapping request -> domain model and calling
        // profileCommandService.setupProfile(...) after DB schema is updated with date_of_birth/gender
        // plus profile visibility flags/avatar and category-based tags
        // (interests, walk_vibes, best_time_to_walk).
        ProfileSetupAckResponse response = new ProfileSetupAckResponse(
                true,
                "Profile payload received successfully (not persisted yet)",
                LocalDateTime.now(),
                request.getName(),
                request.getCity(),
                request.getWalkBio(),
                request.getAvatar(),
                request.getDateOfBirth(),
                request.getGender(),
                request.getPublicProfile(),
                request.getPublicInfo(),
                request.getInterests(),
                request.getWalkVibes(),
                request.getBestTimeToWalk()
        );

        return ResponseEntity.status(HttpStatus.OK).body(response);
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
