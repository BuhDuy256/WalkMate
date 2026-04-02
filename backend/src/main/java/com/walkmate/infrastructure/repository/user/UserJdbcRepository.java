package com.walkmate.infrastructure.repository.user;

import com.walkmate.domain.user.AccountStatus;
import com.walkmate.domain.user.AuthProvider;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserJdbcRepository implements UserRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Optional<User> findByEmail(String email) {
        return jdbcClient.sql(selectAll() + "WHERE email = :email")
                .param("email", email)
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Override
    public Optional<User> findById(String userId) {
        return jdbcClient.sql(selectAll() + "WHERE user_id = :userId")
                .param("userId", UUID.fromString(userId))
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Override
    public List<User> findTopByPoints(int limit) {
        return jdbcClient.sql(selectAll() + """
                ORDER BY total_points DESC, trust_score DESC
                LIMIT :limit
                """)
                .param("limit", limit)
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    @Override
    public User save(User user) {
        UUID userId = user.getUserId() != null ? user.getUserId() : UUID.randomUUID();
        User persisted = user.getUserId() != null
                ? user
                : new User(
                        userId,
                        user.getEmail(),
                        user.getPhone(),
                        user.getProvider(),
                        user.getStatus(),
                        user.getPasswordHash(),
                        user.getCreatedAt(),
                        user.getLastLoginAt(),
                        user.getTrustScore(),
                        user.getTotalPoints(),
                        user.getTotalDistanceKm(),
                        user.getCompletedSessions(),
                        user.getFcmToken());

        jdbcClient.sql("""
                        INSERT INTO user_account (
                            user_id, email, phone, provider, status, password_hash,
                            created_at, last_login_at, trust_score,
                            total_points, total_distance_km, completed_sessions, fcm_token
                        )
                        VALUES (
                            :userId, :email, :phone,
                            CAST(:provider AS auth_provider),
                            CAST(:status AS account_status),
                            :passwordHash, :createdAt, :lastLoginAt, :trustScore,
                            :totalPoints, :totalDistanceKm, :completedSessions, :fcmToken
                        )
                        ON CONFLICT (user_id) DO UPDATE SET
                            email              = EXCLUDED.email,
                            phone              = EXCLUDED.phone,
                            status             = EXCLUDED.status,
                            password_hash      = EXCLUDED.password_hash,
                            last_login_at      = EXCLUDED.last_login_at,
                            trust_score        = EXCLUDED.trust_score,
                            total_points       = EXCLUDED.total_points,
                            total_distance_km  = EXCLUDED.total_distance_km,
                            completed_sessions = EXCLUDED.completed_sessions,
                            fcm_token          = EXCLUDED.fcm_token
                        """)
                .param("userId",            persisted.getUserId())
                .param("email",             persisted.getEmail())
                .param("phone",             persisted.getPhone())
                .param("provider",          persisted.getProvider().name())
                .param("status",            persisted.getStatus().name())
                .param("passwordHash",      persisted.getPasswordHash())
                .param("createdAt",         Timestamp.from(persisted.getCreatedAt()))
                .param("lastLoginAt",       persisted.getLastLoginAt() != null
                        ? Timestamp.from(persisted.getLastLoginAt()) : null)
                .param("trustScore",        persisted.getTrustScore())
                .param("totalPoints",       persisted.getTotalPoints())
                .param("totalDistanceKm",   persisted.getTotalDistanceKm())
                .param("completedSessions", persisted.getCompletedSessions())
                .param("fcmToken",          persisted.getFcmToken())
                .update();

        return persisted;
    }

    @Override
    public void updateFcmToken(UUID userId, String token) {
        jdbcClient.sql("""
                        UPDATE user_account
                        SET fcm_token = :token
                        WHERE user_id = :userId
                        """)
                .param("token",  token)
                .param("userId", userId)
                .update();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String selectAll() {
        return """
                SELECT user_id, email, phone, provider, status, password_hash,
                       created_at, last_login_at, trust_score,
                       total_points, total_distance_km, completed_sessions, fcm_token
                FROM user_account
                """;
    }

    private User mapRow(ResultSet rs) throws SQLException {
        Timestamp lastLogin = rs.getTimestamp("last_login_at");
        return new User(
                rs.getObject("user_id", UUID.class),
                rs.getString("email"),
                rs.getString("phone"),
                AuthProvider.valueOf(rs.getString("provider")),
                AccountStatus.valueOf(rs.getString("status")),
                rs.getString("password_hash"),
                rs.getTimestamp("created_at").toInstant(),
                lastLogin != null ? lastLogin.toInstant() : null,
                rs.getInt("trust_score"),
                rs.getInt("total_points"),
                rs.getDouble("total_distance_km"),
                rs.getInt("completed_sessions"),
                rs.getString("fcm_token")
        );
    }
}
