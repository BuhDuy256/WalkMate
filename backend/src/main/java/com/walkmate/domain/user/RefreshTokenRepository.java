package com.walkmate.domain.user;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenValue(String tokenValue);

    /** Marks the token with the given id as revoked without deleting it (enables reuse detection). */
    void revokeById(UUID tokenId);

    void deleteByUserIdAndDeviceId(UUID userId, String deviceId);

    void deleteAllByUserId(UUID userId);
}
