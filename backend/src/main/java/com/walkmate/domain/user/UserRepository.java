package com.walkmate.domain.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findByEmail(String email);

    Optional<User> findById(String userId);

    /** Looks up a user by their OAuth provider subject (Google's stable {@code sub} claim). */
    Optional<User> findByProviderSubject(String providerSubject);

    User save(User user);

    /** Returns the top {@code limit} users ordered by total_points DESC, trust_score DESC. */
    List<User> findTopByPoints(int limit);

    /**
     * Targeted update for the FCM device token.
     * Preferred over load-update-save for this single-field write to avoid
     * the overhead of a full upsert when only the push token changes.
     */
    void updateFcmToken(UUID userId, String token);
}
