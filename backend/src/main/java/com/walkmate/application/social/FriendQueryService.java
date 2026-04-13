package com.walkmate.application.social;

import com.walkmate.domain.social.Friendship;
import com.walkmate.domain.social.SocialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendQueryService {

    private final SocialRepository socialRepository;

    public List<UUID> getFriends(UUID callerId) {
        return socialRepository.getAcceptedFriendIds(callerId);
    }

    public List<Friendship> getIncomingRequests(UUID callerId) {
        return socialRepository.findIncomingPendingRequests(callerId);
    }

    public List<Friendship> getOutgoingRequests(UUID callerId) {
        return socialRepository.findOutgoingPendingRequests(callerId);
    }
}
