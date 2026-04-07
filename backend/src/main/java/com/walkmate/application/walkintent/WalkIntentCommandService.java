package com.walkmate.application.walkintent;

import com.walkmate.domain.hotspot.HotspotErrorCode;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.walkintent.MatchingConstraints;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentErrorCode;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalkIntentCommandService {

    private final WalkIntentRepository walkIntentRepository;
    private final HotspotRepository hotspotRepository;
    private final SocialRepository socialRepository;

    @Transactional
    public WalkIntent createIntent(CreateWalkIntentCommand command) {
        // 1. Validate the hotspot exists
        hotspotRepository.findById(command.hotspotId())
                .orElseThrow(() -> new DomainException(HotspotErrorCode.HOTSPOT_NOT_FOUND));

        // 2. Guard: no overlapping OPEN or MATCHING intent for this user in the same window
        if (walkIntentRepository.hasOverlappingActiveIntent(
                command.userId(), command.timeWindowStart(), command.timeWindowEnd())) {
            throw new DomainException(WalkIntentErrorCode.INTENT_OVERLAPPING);
        }

        // 3. If private, validate an ACCEPTED friendship exists with the invited user (I-7)
        if (command.isPrivate() && command.invitedFriendId() != null) {
            if (!socialRepository.areAcceptedFriends(
                    UUID.fromString(command.userId()),
                    UUID.fromString(command.invitedFriendId()))) {
                throw new DomainException(WalkIntentErrorCode.INTENT_PRIVATE_FRIEND_NOT_ACCEPTED);
            }
        }

        // 4. Rich domain model validates time range and constraints internally
        WalkIntent intent = WalkIntent.create(
                command.hotspotId(),
                command.userId(),
                command.timeWindowStart(),
                command.timeWindowEnd(),
                new MatchingConstraints(command.ageMin(), command.ageMax()),
                command.isPrivate(),
                command.invitedFriendId(),
                command.description()
        );

        // 5. Persist
        return walkIntentRepository.save(intent);
    }

    @Transactional
    public void cancelIntent(String intentId, String callerId) {
        // 1. Load — throws INTENT_NOT_FOUND if missing
        WalkIntent intent = walkIntentRepository.findById(intentId)
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));

        // 2. Ownership check
        if (!intent.getUserId().equals(callerId)) {
            throw new DomainException(WalkIntentErrorCode.INTENT_NOT_OWNER);
        }

        // 3. Domain enforces state: throws if already CANCELLED or CONSUMED
        intent.cancel();

        // 4. Persist updated status (soft delete — row kept for audit)
        walkIntentRepository.save(intent);
    }
}
