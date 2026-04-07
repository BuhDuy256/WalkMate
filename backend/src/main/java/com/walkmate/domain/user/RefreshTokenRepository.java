package com.walkmate.domain.user;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenValue(String tokenValue);

    void deleteByUserIdAndDeviceId(UUID userId, String deviceId);

    void deleteAllByUserId(UUID userId);
}
