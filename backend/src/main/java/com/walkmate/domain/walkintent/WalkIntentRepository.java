package com.walkmate.domain.walkintent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalkIntentRepository {
    WalkIntent save(WalkIntent intent);
    Optional<WalkIntent> findById(String id);

    /**
     * Loads the intent with a pessimistic write lock (SELECT ... FOR UPDATE).
     * MUST be called inside an active @Transactional boundary.
     * Use this before consuming an intent to prevent concurrent double-consumption.
     */
    Optional<WalkIntent> findByIdForUpdate(String id);

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

    /**
     * Returns OPEN or MATCHING intents whose time_window_start is within the next
     * {@code buffer} from {@code now}, but whose window has not yet ended.
     * Used by the scheduler to auto-expire intents T−5 min before start (GAP-14).
     */
    List<WalkIntent> findIntentsExpiringSoon(Instant now, Duration buffer);

    /**
     * Returns OPEN or MATCHING intents whose time_window_end has already passed.
     * Used by the scheduler to auto-expire overdue intents (GAP-13).
     */
    List<WalkIntent> findOverdueOpenIntents(Instant now);
}
