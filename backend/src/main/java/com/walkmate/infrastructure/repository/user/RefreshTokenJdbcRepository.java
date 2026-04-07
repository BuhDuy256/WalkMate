package com.walkmate.infrastructure.repository.user;

import com.walkmate.domain.user.RefreshToken;
import com.walkmate.domain.user.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RefreshTokenJdbcRepository implements RefreshTokenRepository {

    private final JdbcClient jdbcClient;

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        UUID tokenId = refreshToken.getTokenId() != null ? refreshToken.getTokenId() : UUID.randomUUID();
        RefreshToken persisted = refreshToken.getTokenId() != null
                ? refreshToken
                : new RefreshToken(
                        tokenId,
                        refreshToken.getUserId(),
                        refreshToken.getDeviceId(),
                        refreshToken.getTokenValue(),
                        refreshToken.getCreatedAt(),
                        refreshToken.getExpiresAt());

        jdbcClient.sql("""
                        INSERT INTO refresh_token (token_id, user_id, device_id, token_value, created_at, expires_at)
                        VALUES (:tokenId, :userId, :deviceId, :tokenValue, :createdAt, :expiresAt)
                        ON CONFLICT (token_id) DO UPDATE SET
                            device_id   = EXCLUDED.device_id,
                            token_value = EXCLUDED.token_value,
                            expires_at  = EXCLUDED.expires_at
                        """)
                .param("tokenId",    persisted.getTokenId())
                .param("userId",     persisted.getUserId())
                .param("deviceId",   persisted.getDeviceId())
                .param("tokenValue", persisted.getTokenValue())
                .param("createdAt",  Timestamp.from(persisted.getCreatedAt()))
                .param("expiresAt",  Timestamp.from(persisted.getExpiresAt()))
                .update();

        return persisted;
    }

    @Override
    public Optional<RefreshToken> findByTokenValue(String tokenValue) {
        return jdbcClient.sql("""
                        SELECT token_id, user_id, device_id, token_value, created_at, expires_at
                        FROM refresh_token
                        WHERE token_value = :tokenValue
                        """)
                .param("tokenValue", tokenValue)
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Override
    public void deleteByUserIdAndDeviceId(UUID userId, String deviceId) {
        jdbcClient.sql("""
                        DELETE FROM refresh_token
                        WHERE user_id = :userId AND device_id = :deviceId
                        """)
                .param("userId",   userId)
                .param("deviceId", deviceId)
                .update();
    }

    @Override
    public void deleteAllByUserId(UUID userId) {
        jdbcClient.sql("DELETE FROM refresh_token WHERE user_id = :userId")
                .param("userId", userId)
                .update();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RefreshToken mapRow(ResultSet rs) throws SQLException {
        Timestamp expiresAt = rs.getTimestamp("expires_at");
        return new RefreshToken(
                rs.getObject("token_id",  UUID.class),
                rs.getObject("user_id",   UUID.class),
                rs.getString("device_id"),
                rs.getString("token_value"),
                rs.getTimestamp("created_at").toInstant(),
                expiresAt != null ? expiresAt.toInstant() : null
        );
    }
}
