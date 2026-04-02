package com.walkmate.presentation.mapper.user;

import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.presentation.dto.response.user.UserProfileResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserProfileMapper {

    /**
     * Merges UserProfile + User stats into a single API response.
     *
     * @param profile the profile aggregate (may be a blank placeholder)
     * @param user    the auth entity (carries trust_score, distance, sessions)
     * @param tags    pre-loaded tag list for this user
     */
    public UserProfileResponse toResponse(UserProfile profile, User user, List<String> tags) {
        return new UserProfileResponse(
                user.getUserId().toString(),
                profile.getFullName(),
                profile.getGender() != null ? profile.getGender().name() : null,
                profile.getDateOfBirth() != null ? profile.getDateOfBirth().toString() : null,
                profile.getAvatarUrl(),
                profile.getBio(),
                profile.getSearchRadius(),
                user.getTrustScore(),
                user.getTotalDistanceKm(),
                user.getCompletedSessions(),
                tags
        );
    }
}
