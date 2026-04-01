package com.walkmate.domain.user;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByEmail(String email);

    Optional<User> findById(String userId);

    User save(User user);

    /** Returns the top {@code limit} users ordered by total_points DESC, trust_score DESC. */
    List<User> findTopByPoints(int limit);
}
