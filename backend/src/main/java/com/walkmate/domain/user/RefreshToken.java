package com.walkmate.domain.user;

import com.walkmate.domain.shared.exception.DomainException;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class RefreshToken {

    private UUID    tokenId;
    private UUID    userId;
    private String  deviceId;
    private String  tokenValue;
    private Instant createdAt;
    private Instant expiresAt;

    protected RefreshToken() {}

    /** Rehydration constructor (repository → domain). */
    public RefreshToken(UUID tokenId, UUID userId, String deviceId,
                        String tokenValue, Instant createdAt, Instant expiresAt) {
        this.tokenId    = tokenId;
        this.userId     = userId;
        this.deviceId   = deviceId;
        this.tokenValue = tokenValue;
        this.createdAt  = createdAt;
        this.expiresAt  = expiresAt;
    }

    private RefreshToken(UUID userId, String deviceId, String tokenValue, Instant expiresAt) {
        this.userId     = requireUserId(userId);
        this.deviceId   = requireText(deviceId, "Device ID is required");
        this.tokenValue = requireText(tokenValue, "Refresh token value is required");
        this.createdAt  = Instant.now();
        this.expiresAt  = expiresAt;
    }

    public static RefreshToken issue(UUID userId, String deviceId, String tokenValue, Instant expiresAt) {
        return new RefreshToken(userId, deviceId, tokenValue, expiresAt);
    }

    private static UUID requireUserId(UUID value) {
        if (value == null) {
            throw new DomainException(UserErrorCode.INVALID_USER_DATA, "User ID is required");
        }
        return value;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainException(UserErrorCode.INVALID_USER_DATA, message);
        }
        return value.trim();
    }
}
