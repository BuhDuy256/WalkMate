package com.walkmate.domain.walkintent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WalkIntentRepository {
    WalkIntent save(WalkIntent intent);
    Optional<WalkIntent> findById(String id);
    void delete(String id);

    /** Returns all OPEN intents owned by the given user, ordered newest-first. */
    List<WalkIntent> findOpenByUserId(String userId);

    /**
     * Returns true if the user already has an OPEN or CONSUMED intent whose
     * time window overlaps [start, end).  Used to enforce the no-double-booking rule.
     *
     * Overlap condition: existing.start < end  AND  existing.end > start
     */
    boolean hasOverlappingActiveIntent(String userId, Instant start, Instant end);

    /**
     * Stage 1 of matching: DB-level hard filter.
     * Returns OPEN intents at the same hotspot whose time window overlaps
     * the given window by at least minDuration, and whose age-preference
     * range intersects with [ageMin, ageMax]. Excludes the requesting user.
     */
    List<WalkIntent> findOpenCandidates(
            String hotspotId,
            Instant timeWindowStart,
            Instant timeWindowEnd,
            int ageMin,
            int ageMax,
            String excludeUserId,
            Duration minDuration
    );
}
