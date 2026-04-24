package com.walkmate.domain.walkintent;

import java.util.Optional;
import java.util.UUID;

public interface MatchingPreferenceRepository {

    /**
     * Finds the stored preference for a user.
     * Returns empty if the user has never been trained — the caller should substitute
     * {@link MatchingPreference#defaultFor(UUID)} in that case.
     */
    Optional<MatchingPreference> findByUserId(UUID userId);

    /** Upserts the preference row (insert or update on conflict). */
    void save(MatchingPreference preference);
}
