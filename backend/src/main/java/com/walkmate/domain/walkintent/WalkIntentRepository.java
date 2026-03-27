package com.walkmate.domain.walkintent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WalkIntentRepository {
    WalkIntent save(WalkIntent intent);
    Optional<WalkIntent> findById(String id);
    void delete(String id);

    /**
     * Stage 1 of matching: DB-level hard filter.
     * Returns OPEN intents at the same hotspot whose time window overlaps
     * the given window by at least minDuration, and whose age-preference
     * range intersects with [ageMin, ageMax]. Excludes the requesting user.
     *
     * Time-overlap condition (Research.md §4):
     *   candidate.time_window_start < (timeWindowEnd   - minDuration)
     *   candidate.time_window_end   > (timeWindowStart + minDuration)
     *
     * Age-range overlap:
     *   candidate.age_min <= ageMax
     *   candidate.age_max >= ageMin
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
