package com.walkmate.infrastructure.repository.user;

import com.walkmate.domain.user.AccountStatus;
import com.walkmate.domain.user.AuthProvider;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserRepository;
import java.sql.Timestamp;
import java.util.Optional;
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
                .param("userId", user.getUserId())
                .param("email", user.getEmail())
                .param("phone", user.getPhone())
                .param("provider", user.getProvider().name())
                .param("status", user.getStatus().name())
                .param("passwordHash", user.getPasswordHash())
                .param("createdAt", Timestamp.from(user.getCreatedAt()))
                .param("lastLoginAt", user.getLastLoginAt() != null ? Timestamp.from(user.getLastLoginAt()) : null)
                .update();

        return user;
    }
}
