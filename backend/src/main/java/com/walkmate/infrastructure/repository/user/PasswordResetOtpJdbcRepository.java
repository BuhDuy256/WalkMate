package com.walkmate.infrastructure.repository.user;

import com.walkmate.domain.user.PasswordResetOtp;
import com.walkmate.domain.user.PasswordResetOtpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PasswordResetOtpJdbcRepository implements PasswordResetOtpRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void save(PasswordResetOtp otp) {
        if (otp.getOtpId() == null) {
            UUID newId = UUID.randomUUID();
            jdbcClient.sql("""
                            INSERT INTO password_reset_otp
                                (otp_id, user_id, email, code_hash, otp_expires_at, attempt_count, created_at)
                            VALUES
                                (:otpId, :userId, :email, :codeHash, :otpExpiresAt, :attemptCount, :createdAt)
                            """)
                    .param("otpId",       newId)
                    .param("userId",      otp.getUserId())
                    .param("email",       otp.getEmail())
                    .param("codeHash",    otp.getCodeHash())
                    .param("otpExpiresAt", Timestamp.from(otp.getOtpExpiresAt()))
                    .param("attemptCount", otp.getAttemptCount())
                    .param("createdAt",   Timestamp.from(otp.getCreatedAt()))
                    .update();
        } else {
            jdbcClient.sql("""
                            UPDATE password_reset_otp SET
                                attempt_count          = :attemptCount,
                                verified_at            = :verifiedAt,
                                reset_token_hash       = :resetTokenHash,
                                reset_token_expires_at = :resetTokenExpiresAt,
                                consumed_at            = :consumedAt
                            WHERE otp_id = :otpId
                            """)
                    .param("otpId",              otp.getOtpId())
                    .param("attemptCount",        otp.getAttemptCount())
                    .param("verifiedAt",          toTimestamp(otp.getVerifiedAt()))
                    .param("resetTokenHash",      otp.getResetTokenHash())
                    .param("resetTokenExpiresAt", toTimestamp(otp.getResetTokenExpiresAt()))
                    .param("consumedAt",          toTimestamp(otp.getConsumedAt()))
                    .update();
        }
    }

    @Override
    public Optional<PasswordResetOtp> findActiveLatestByEmail(String email) {
        return jdbcClient.sql("""
                        SELECT otp_id, user_id, email, code_hash, otp_expires_at,
                               attempt_count, verified_at, reset_token_hash,
                               reset_token_expires_at, consumed_at, created_at
                        FROM password_reset_otp
                        WHERE email = :email AND consumed_at IS NULL
                        ORDER BY created_at DESC
                        LIMIT 1
                        """)
                .param("email", email)
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Override
    public Optional<PasswordResetOtp> findByResetTokenHash(String resetTokenHash) {
        return jdbcClient.sql("""
                        SELECT otp_id, user_id, email, code_hash, otp_expires_at,
                               attempt_count, verified_at, reset_token_hash,
                               reset_token_expires_at, consumed_at, created_at
                        FROM password_reset_otp
                        WHERE reset_token_hash = :resetTokenHash
                        """)
                .param("resetTokenHash", resetTokenHash)
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Override
    public void invalidateActiveByEmail(String email) {
        jdbcClient.sql("""
                        UPDATE password_reset_otp
                        SET consumed_at = :now
                        WHERE email = :email AND consumed_at IS NULL
                        """)
                .param("now",   Timestamp.from(Instant.now()))
                .param("email", email)
                .update();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PasswordResetOtp mapRow(ResultSet rs) throws SQLException {
        return new PasswordResetOtp(
                rs.getObject("otp_id",  UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("email"),
                rs.getString("code_hash"),
                rs.getTimestamp("otp_expires_at").toInstant(),
                rs.getInt("attempt_count"),
                toInstant(rs.getTimestamp("verified_at")),
                rs.getString("reset_token_hash"),
                toInstant(rs.getTimestamp("reset_token_expires_at")),
                toInstant(rs.getTimestamp("consumed_at")),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }
}
