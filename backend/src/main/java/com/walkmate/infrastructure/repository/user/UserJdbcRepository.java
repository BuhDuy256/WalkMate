package com.walkmate.infrastructure.repository.user;

import com.walkmate.domain.user.AccountStatus;
import com.walkmate.domain.user.AuthProvider;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserRepository;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserJdbcRepository implements UserRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Optional<User> findByEmail(String email) {
        final String sql = """
                SELECT user_id, email, phone, provider, status, password_hash, created_at, last_login_at
                FROM user_account
                WHERE email = :email
                """;

        return jdbcClient.sql(sql)
                .param("email", email)
                .query((rs, rowNum) -> new User(
                        rs.getObject("user_id", java.util.UUID.class),
                        rs.getString("email"),
                        rs.getString("phone"),
                        AuthProvider.valueOf(rs.getString("provider")),
                        AccountStatus.valueOf(rs.getString("status")),
                        rs.getString("password_hash"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("last_login_at") != null ? rs.getTimestamp("last_login_at").toInstant() : null))
                .optional();
    }

    @Override
    public User save(User user) {
        UUID userId = user.getUserId() != null ? user.getUserId() : UUID.randomUUID();
        User persistedUser = user.getUserId() != null
                ? user
                : new User(
                        userId,
                        user.getEmail(),
                        user.getPhone(),
                        user.getProvider(),
                        user.getStatus(),
                        user.getPasswordHash(),
                        user.getCreatedAt(),
                        user.getLastLoginAt());

        final String sql = """
                INSERT INTO user_account (
                    user_id,
                    email,
                    phone,
                    provider,
                    status,
                    password_hash,
                    created_at,
                    last_login_at
                )
                VALUES (
                    :userId,
                    :email,
                    :phone,
                    CAST(:provider AS auth_provider),
                    CAST(:status AS account_status),
                    :passwordHash,
                    :createdAt,
                    :lastLoginAt
                )
                ON CONFLICT (user_id)
                DO UPDATE SET
                    email = EXCLUDED.email,
                    phone = EXCLUDED.phone,
                    status = EXCLUDED.status,
                    password_hash = EXCLUDED.password_hash,
                    last_login_at = EXCLUDED.last_login_at
                """;

        jdbcClient.sql(sql)
                .param("userId", persistedUser.getUserId())
                .param("email", persistedUser.getEmail())
                .param("phone", persistedUser.getPhone())
                .param("provider", persistedUser.getProvider().name())
                .param("status", persistedUser.getStatus().name())
                .param("passwordHash", persistedUser.getPasswordHash())
                .param("createdAt", Timestamp.from(persistedUser.getCreatedAt()))
                .param("lastLoginAt",
                        persistedUser.getLastLoginAt() != null ? Timestamp.from(persistedUser.getLastLoginAt()) : null)
                .update();

        return persistedUser;
    }
}
