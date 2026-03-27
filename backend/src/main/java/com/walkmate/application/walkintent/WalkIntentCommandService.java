package com.walkmate.application.walkintent;

import com.walkmate.domain.hotspot.HotspotErrorCode;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.walkintent.MatchingConstraints;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentErrorCode;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalkIntentCommandService {

    private final WalkIntentRepository walkIntentRepository;
    private final HotspotRepository hotspotRepository;

    @Transactional
    public WalkIntent createIntent(CreateWalkIntentCommand command) {
        // 1. Validate the hotspot exists before creating the intent
        hotspotRepository.findById(command.hotspotId())
                .orElseThrow(() -> new DomainException(HotspotErrorCode.HOTSPOT_NOT_FOUND));

        // 2. Rich domain model validates time range and constraints internally
        WalkIntent intent = WalkIntent.create(
                command.hotspotId(),
                command.userId(),
                command.timeWindowStart(),
                command.timeWindowEnd(),
                new MatchingConstraints(command.ageMin(), command.ageMax())
        );

        // 3. Persist
        return walkIntentRepository.save(intent);
    }

    @Transactional
    public void cancelIntent(String intentId) {
        // 1. Load — throws INTENT_NOT_FOUND if missing
        WalkIntent intent = walkIntentRepository.findById(intentId)
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));

        // 2. Domain enforces state: throws if already CANCELLED or MATCHED
        intent.cancel();

        // 3. Persist updated status (soft delete — row kept for audit)
        walkIntentRepository.save(intent);
    }
}
