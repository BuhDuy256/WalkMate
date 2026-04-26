package com.walkmate.application.social;

import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationType;
import com.walkmate.domain.shared.NotificationPublisher;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.social.Friendship;
import com.walkmate.domain.social.FriendshipErrorCode;
import com.walkmate.domain.social.SocialErrorCode;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FriendCommandService {

    private final SocialRepository      socialRepository;
    private final UserRepository        userRepository;
    private final NotificationPublisher notificationPublisher;

    // ── Send friend request (UC-34) ───────────────────────────────────────────

    public Friendship sendFriendRequest(UUID callerId, UUID targetId) {
        if (callerId.equals(targetId))
            throw new DomainException(FriendshipErrorCode.FRIEND_REQUEST_SELF_FORBIDDEN);

        verifyUserExists(targetId);

        if (socialRepository.isBlocked(callerId, targetId) || socialRepository.isBlocked(targetId, callerId))
            throw new DomainException(FriendshipErrorCode.FRIEND_REQUEST_BLOCKED);

        if (socialRepository.areAcceptedFriends(callerId, targetId))
            throw new DomainException(FriendshipErrorCode.FRIEND_REQUEST_ALREADY_FRIENDS);

        Optional<Friendship> existing = socialRepository.findPendingFriendship(callerId, targetId);
        if (existing.isPresent()) {
            Friendship ex = existing.get();
            if (ex.getRequesterId().equals(targetId)) {
                // B already sent a request to A — auto-accept instead of creating a duplicate
                return acceptFriendRequest(callerId, ex.getFriendshipId());
            } else {
                // A already sent a request to B — still waiting
                throw new DomainException(FriendshipErrorCode.FRIEND_REQUEST_ALREADY_PENDING);
            }
        }

        Friendship fs = Friendship.create(callerId, targetId);
        socialRepository.saveFriendship(fs);
        notificationPublisher.publish(Notification.create(
                targetId.toString(),
                NotificationType.FRIEND_REQUEST_RECEIVED,
                Map.of("friendshipId", fs.getFriendshipId(), "requesterId", callerId.toString())));
        return fs;
    }

    // ── Accept friend request (UC-35) ─────────────────────────────────────────

    public Friendship acceptFriendRequest(UUID callerId, String friendshipId) {
        Friendship friendship = socialRepository.findFriendshipById(friendshipId)
                .orElseThrow(() -> new DomainException(FriendshipErrorCode.FRIEND_REQUEST_NOT_FOUND));

        if (!friendship.getAddresseeId().equals(callerId))
            throw new DomainException(FriendshipErrorCode.FRIEND_REQUEST_NOT_PARTICIPANT);

        friendship.accept(); // guards PENDING internally
        socialRepository.saveFriendship(friendship);
        notificationPublisher.publish(Notification.create(
                friendship.getRequesterId().toString(),
                NotificationType.FRIEND_REQUEST_ACCEPTED,
                Map.of("friendshipId", friendshipId)));
        return friendship;
    }

    // ── Decline friend request (UC-35) ────────────────────────────────────────

    public void declineFriendRequest(UUID callerId, String friendshipId) {
        Friendship friendship = socialRepository.findFriendshipById(friendshipId)
                .orElseThrow(() -> new DomainException(FriendshipErrorCode.FRIEND_REQUEST_NOT_FOUND));

        if (!friendship.getAddresseeId().equals(callerId))
            throw new DomainException(FriendshipErrorCode.FRIEND_REQUEST_NOT_PARTICIPANT);

        friendship.decline(); // guards PENDING internally
        socialRepository.saveFriendship(friendship);
        notificationPublisher.publish(Notification.create(
                friendship.getRequesterId().toString(),
                NotificationType.FRIEND_REQUEST_DECLINED,
                Map.of("friendshipId", friendshipId)));
    }

    // ── Cancel outgoing friend request (UC-39) ───────────────────────────────

    public void cancelFriendRequest(UUID callerId, String friendshipId) {
        Friendship friendship = socialRepository.findFriendshipById(friendshipId)
                .orElseThrow(() -> new DomainException(FriendshipErrorCode.FRIEND_REQUEST_NOT_FOUND));

        if (!friendship.getRequesterId().equals(callerId))
            throw new DomainException(FriendshipErrorCode.FRIEND_REQUEST_NOT_REQUESTER);

        friendship.cancel();
        socialRepository.saveFriendship(friendship);
    }

    // ── Remove friend (UC-36) ─────────────────────────────────────────────────

    public void removeFriend(UUID callerId, UUID targetId) {
        verifyUserExists(targetId);

        if (!socialRepository.areAcceptedFriends(callerId, targetId))
            throw new DomainException(FriendshipErrorCode.FRIEND_REMOVE_NOT_FRIENDS);

        socialRepository.removeFriendship(callerId, targetId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void verifyUserExists(UUID userId) {
        userRepository.findById(userId.toString())
                .orElseThrow(() -> new DomainException(SocialErrorCode.SOCIAL_USER_NOT_FOUND));
    }
}
