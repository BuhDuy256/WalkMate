package com.walkmate.application.user;

import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserErrorCode;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository        userRepository;
    private final UserProfileRepository profileRepository;

    /**
     * Returns the profile for the authenticated caller.
     * If no profile row exists yet it is created lazily (blank).
     */
    @Transactional
    public UserProfile getMyProfile(UUID callerId) {
        ensureUserExists(callerId);
        return profileRepository.findByUserId(callerId)
                .orElseGet(() -> profileRepository.save(UserProfile.createBlank(callerId)));
    }

    /**
     * Returns the profile of any user by their public userId.
     * Used for the public-facing profile view.
     */
    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        ensureUserExists(userId);
        return profileRepository.findByUserId(userId)
                .orElseGet(() -> UserProfile.createBlank(userId));
    }

    /** Looks up the auth/stats part of a user. */
    @Transactional(readOnly = true)
    public User getUser(UUID userId) {
        return userRepository.findById(userId.toString())
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void ensureUserExists(UUID userId) {
        userRepository.findById(userId.toString())
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));
    }
}
