package com.walkmate.application.social;

import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.social.SocialErrorCode;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SocialCommandService {

    private final SocialRepository socialRepository;
    private final UserRepository   userRepository;

    // ── Follow ────────────────────────────────────────────────────────────────

    @Transactional
    public void follow(UUID callerId, UUID targetId) {
        if (callerId.equals(targetId)) {
            throw new DomainException(SocialErrorCode.FOLLOW_SELF_FOLLOW_FORBIDDEN);
        }
        verifyUserExists(targetId);
        if (socialRepository.isFollowing(callerId, targetId)) {
            throw new DomainException(SocialErrorCode.FOLLOW_ALREADY_FOLLOWING);
        }
        socialRepository.follow(callerId, targetId);
    }

    @Transactional
    public void unfollow(UUID callerId, UUID targetId) {
        verifyUserExists(targetId);
        // Idempotent: silently succeeds if not following
        socialRepository.unfollow(callerId, targetId);
    }

    // ── Block ─────────────────────────────────────────────────────────────────

    @Transactional
    public void block(UUID callerId, UUID targetId) {
        if (callerId.equals(targetId)) {
            throw new DomainException(SocialErrorCode.BLOCK_SELF_BLOCK_FORBIDDEN);
        }
        verifyUserExists(targetId);
        if (socialRepository.isBlocked(callerId, targetId)) {
            throw new DomainException(SocialErrorCode.BLOCK_ALREADY_BLOCKED);
        }
        // Silently tear down any follow relationships in both directions
        // so the blocked user stops seeing the blocker's activity
        socialRepository.unfollow(callerId, targetId);
        socialRepository.unfollow(targetId, callerId);
        socialRepository.block(callerId, targetId);
    }

    @Transactional
    public void unblock(UUID callerId, UUID targetId) {
        verifyUserExists(targetId);
        // Idempotent: silently succeeds if not blocked
        socialRepository.unblock(callerId, targetId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void verifyUserExists(UUID userId) {
        userRepository.findById(userId.toString())
                .orElseThrow(() -> new DomainException(SocialErrorCode.SOCIAL_USER_NOT_FOUND));
    }
}
