package com.walkmate.domain.user;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import com.walkmate.domain.shared.exception.DomainException;

@Getter
public class RefreshToken {

    private UUID tokenId;
    private UUID userId;
    private String tokenValue;
    private Instant createdAt;

    protected RefreshToken() {
    }

    // Constructor for rehydration
    public RefreshToken(UUID tokenId, UUID userId, String tokenValue, Instant createdAt) {
        this.tokenId = tokenId;
        this.userId = userId;
        this.tokenValue = tokenValue;
        this.createdAt = createdAt;
    }

    private RefreshToken(UUID userId, String tokenValue) {
        this.tokenId = UUID.randomUUID();
        this.userId = requireUserId(userId);
        this.tokenValue = requireText(tokenValue, "Refresh token is required");
        this.createdAt = Instant.now();
    }

    public static RefreshToken issue(UUID userId, String tokenValue) {
        return new RefreshToken(userId, tokenValue);
    }

    private static UUID requireUserId(UUID value) {
        if (value == null) {
            throw new DomainException(UserErrorCode.INVALID_USER_DATA, "User id is required");
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
