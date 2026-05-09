package com.walkmate.domain.walkintent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WalkIntentRepository {
    WalkIntent save(WalkIntent intent);
    Optional<WalkIntent> findById(String id);

    /**
     * Loads the intent with a pessimistic write lock (SELECT ... FOR UPDATE).
     * MUST be called inside an active @Transactional boundary.
     */
    Optional<WalkIntent> findByIdForUpdate(String id);

    void delete(String id);

    /** Returns all OPEN intents owned by the given user, ordered newest-first. */
    List<WalkIntent> findOpenByUserId(String userId);

    /**
     * Returns true if the user already has an OPEN or MATCHING intent whose
     * time window overlaps [start, end). Used to enforce the no-double-booking rule.
     */
    boolean hasOverlappingActiveIntent(String userId, Instant start, Instant end);

    /**
     * Stage 1 of matching: DB-level hard filter.
     * Returns OPEN intents at the same hotspot whose time window overlaps
     * the given window by at least minDuration, whose age-preference range
     * intersects [ageMin, ageMax], and whose gender preferences are mutually
     * Excludes the requesting user.
     *
     * @param preferredGender caller's gender preference: "ANY", "MALE", or "FEMALE"
     */
    List<WalkIntent> findOpenCandidates(
            String hotspotId,
            Instant timeWindowStart,
            Instant timeWindowEnd,
            int ageMin,
            int ageMax,
            String excludeUserId,
            Duration minDuration,
            String preferredGender
    );

    /**
     * Returns OPEN or MATCHING intents whose time_window_start is within the next
     * {@code buffer} from {@code now}, but whose window has not yet ended.
     */
    List<WalkIntent> findIntentsExpiringSoon(Instant now, Duration buffer);

    /**
     * Returns OPEN or MATCHING intents whose time_window_end has already passed.
     */
    List<WalkIntent> findOverdueOpenIntents(Instant now);
}
