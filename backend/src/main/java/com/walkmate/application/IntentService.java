package com.walkmate.application;

import com.walkmate.domain.intent.IntentRepository;
import com.walkmate.domain.intent.MatchFilter;
import com.walkmate.domain.intent.WalkIntent;
import com.walkmate.domain.intent.WalkPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates use cases for WalkIntent and enforces Application-level Invariants.
 */
@Service
@RequiredArgsConstructor
public class IntentService {

    private final IntentRepository intentRepository;

    /**
     * Creates a new WalkIntent from UI Request components.
     * Enforces INVARIANT 2 defined in db.sql.
     */
    @Transactional
    public UUID createWalkIntent(
            UUID userId,
            Double lat,
            Double lng,
            LocalDateTime start,
            LocalDateTime end,
            WalkPurpose purpose,
            Integer radiusMeters,
            List<String> tagsPreference,
            LocalDateTime expiresAt) {

        // 1. Cross-aggregate db.sql INVARIANT 2: No overlapping OPEN intents for same user
        if (intentRepository.hasOverlappingOpenIntent(userId, start, end)) {
            // Note: Should use custom DomainException, using standard for prototype
            throw new IllegalArgumentException(
                    "INVARIANT 2 VIOLATION: You already have an OPEN Walk Intent that overlaps with [" +
                            start + " to " + end + "]."
            );
        }

        // 2. Assemble Value Object (JSONB equivalent in DB)
        MatchFilter filter = new MatchFilter();
        filter.setSearchRadiusMeters(radiusMeters);
        filter.setTagsPreference(tagsPreference);

        // 3. Command domain factory pattern
        WalkIntent intent = new WalkIntent(
                UUID.randomUUID(),
                userId,
                lat,
                lng,
                start,
                end,
                purpose,
                filter,
                expiresAt
        );

        // 4. Save mapped state
        intentRepository.save(intent);

        return intent.getId();
    }
}
