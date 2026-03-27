package com.walkmate.domain.profile;

import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository {
    Profile upsert(Profile profile);
    Optional<Profile> findByUserId(UUID userId);
}
