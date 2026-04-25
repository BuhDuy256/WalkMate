package com.walkmate.application.user;

import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.Gender;
import com.walkmate.domain.user.UserErrorCode;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileCommandService {

    private final UserProfileRepository profileRepository;

    @Transactional
    public UserProfile updateProfile(UpdateProfileCommand command) {
        UserProfile profile = profileRepository.findByUserId(command.callerId())
                .orElseGet(() -> UserProfile.createBlank(command.callerId()));

        Gender gender = parseGender(command.gender());

        profile.update(
                command.fullName(),
                gender,
                command.dateOfBirth(),
                command.bio(),
                command.searchRadius()
        );

        UserProfile saved = profileRepository.save(profile);

        List<UUID> tagIds = command.tagIds() != null ? command.tagIds() : List.of();
        profileRepository.replaceTagsByIds(command.callerId(), tagIds);

        return saved;
    }

    @Transactional
    public UserProfile updateAvatar(UUID userId, String avatarUrl) {
        UserProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> UserProfile.createBlank(userId));

        profile.applyAvatar(avatarUrl);
        return profileRepository.save(profile);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Gender parseGender(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Gender.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DomainException(UserErrorCode.INVALID_USER_DATA, "Unknown gender value: " + raw);
        }
    }
}
