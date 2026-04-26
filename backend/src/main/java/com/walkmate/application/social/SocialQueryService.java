package com.walkmate.application.social;

import com.walkmate.domain.social.Friendship;
import com.walkmate.domain.social.SocialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SocialQueryService {

    private final SocialRepository  socialRepository;
    private final FriendQueryService friendQueryService;

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
     * Delegates to FriendQueryService which reads accepted friendship rows.
     */
    @Transactional(readOnly = true)
    public List<UUID> getFriends(UUID callerId) {
        return friendQueryService.getFriends(callerId);
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(UUID followerId, UUID followeeId) {
        return socialRepository.isFollowing(followerId, followeeId);
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(UUID blockerId, UUID blockedId) {
        return socialRepository.isBlocked(blockerId, blockedId);
    }

    @Transactional(readOnly = true)
    public boolean areFriends(UUID userId1, UUID userId2) {
        return socialRepository.areAcceptedFriends(userId1, userId2);
    }

    /**
     * Returns a structured friendship status between two users.
     * status is one of: "NONE", "PENDING_SENT", "PENDING_RECEIVED", "FRIENDS".
     * pendingRequestId is non-null only for PENDING_RECEIVED (used by the addressee to accept/decline).
     */
    @Transactional(readOnly = true)
    public FriendshipStatusResult getFriendshipStatus(UUID callerId, UUID targetId) {
        if (socialRepository.areAcceptedFriends(callerId, targetId))
            return new FriendshipStatusResult("FRIENDS", null);

        Optional<Friendship> pending = socialRepository.findPendingFriendship(callerId, targetId);
        if (pending.isPresent()) {
            Friendship f = pending.get();
            if (f.getRequesterId().equals(callerId))
                return new FriendshipStatusResult("PENDING_SENT", f.getFriendshipId());
            else
                return new FriendshipStatusResult("PENDING_RECEIVED", f.getFriendshipId());
        }

        return new FriendshipStatusResult("NONE", null);
    }
}
