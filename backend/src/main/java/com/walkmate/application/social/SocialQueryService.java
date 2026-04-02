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

    @Transactional(readOnly = true)
    public boolean isFollowing(UUID followerId, UUID followeeId) {
        return socialRepository.isFollowing(followerId, followeeId);
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(UUID blockerId, UUID blockedId) {
        return socialRepository.isBlocked(blockerId, blockedId);
    }
}
