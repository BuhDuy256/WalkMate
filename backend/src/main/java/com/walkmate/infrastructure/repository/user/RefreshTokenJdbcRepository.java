package com.walkmate.infrastructure.repository.user;

import com.walkmate.domain.user.RefreshToken;
import com.walkmate.domain.user.RefreshTokenRepository;
import java.sql.Timestamp;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenJdbcRepository implements RefreshTokenRepository {

    private final JdbcClient jdbcClient;

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        final String sql = """
                INSERT INTO refresh_token (token_id, user_id, token_value, created_at)
                VALUES (:tokenId, :userId, :tokenValue, :createdAt)
                ON CONFLICT (token_id)
                DO UPDATE SET
                    user_id = EXCLUDED.user_id,
                    token_value = EXCLUDED.token_value
                """;

        jdbcClient.sql(sql)
                .param("tokenId", refreshToken.getTokenId())
                .param("userId", refreshToken.getUserId())
                .param("tokenValue", refreshToken.getTokenValue())
                .param("createdAt", Timestamp.from(refreshToken.getCreatedAt()))
                .update();

        return refreshToken;
    }
}
