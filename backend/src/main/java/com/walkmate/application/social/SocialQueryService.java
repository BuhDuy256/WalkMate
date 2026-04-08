package com.walkmate.application.social;

import com.walkmate.domain.social.SocialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SocialQueryService {

    private final SocialRepository socialRepository;

    @Transactional(readOnly = true)
    public List<UUID> getFollowers(UUID userId) {
        return socialRepository.getFollowerIds(userId);
    }

    @Transactional(readOnly = true)
    public List<UUID> getFollowing(UUID userId) {
        return socialRepository.getFolloweeIds(userId);
    }

    /**
     * Returns the friend list for the private-intent friend-picker (UC-08).
     *
     * There is no dedicated Friendship table yet, so "friends" is defined as
     * the set of users the caller is currently following. This mirrors the
     * behaviour of GET /api/v1/users/{userId}/following but is scoped to the
     * authenticated caller and carries an intent-specific name so a real
     * friendship model can be dropped in later without changing the endpoint.
     */
    @Transactional(readOnly = true)
    public List<UUID> getFriends(UUID callerId) {
        return socialRepository.getFolloweeIds(callerId);
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(UUID followerId, UUID followeeId) {
        return socialRepository.isFollowing(followerId, followeeId);
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(UUID blockerId, UUID blockedId) {
        return socialRepository.isBlocked(blockerId, blockedId);
    }
}
