package com.walkmate.infrastructure.repository.user;

import com.walkmate.domain.user.OtpRecord;
import com.walkmate.domain.user.OtpRecordRepository;
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
public class OtpRecordJdbcRepository implements OtpRecordRepository {

    private final JdbcClient jdbcClient;

    @Override
    public OtpRecord save(OtpRecord otpRecord) {
        UUID otpId = otpRecord.getOtpId() != null ? otpRecord.getOtpId() : UUID.randomUUID();
        OtpRecord persisted = otpRecord.getOtpId() != null
                ? otpRecord
                : new OtpRecord(
                        otpId,
                        otpRecord.getPhone(),
                        otpRecord.getCodeHash(),
                        otpRecord.getExpiresAt(),
                        otpRecord.isUsed(),
                        otpRecord.getAttemptCount());

        jdbcClient.sql("""
                        INSERT INTO otp_record (otp_id, phone, code_hash, expires_at, used, attempt_count)
                        VALUES (:otpId, :phone, :codeHash, :expiresAt, :used, :attemptCount)
                        ON CONFLICT (otp_id) DO UPDATE SET
                            code_hash     = EXCLUDED.code_hash,
                            expires_at    = EXCLUDED.expires_at,
                            used          = EXCLUDED.used,
                            attempt_count = EXCLUDED.attempt_count
                        """)
                .param("otpId",        persisted.getOtpId())
                .param("phone",        persisted.getPhone())
                .param("codeHash",     persisted.getCodeHash())
                .param("expiresAt",    Timestamp.from(persisted.getExpiresAt()))
                .param("used",         persisted.isUsed())
                .param("attemptCount", persisted.getAttemptCount())
                .update();

        return persisted;
    }

    @Override
    public Optional<OtpRecord> findLatestByPhone(String phone) {
        return jdbcClient.sql("""
                        SELECT otp_id, phone, code_hash, expires_at, used, attempt_count
                        FROM otp_record
                        WHERE phone = :phone
                        ORDER BY expires_at DESC
                        LIMIT 1
                        """)
                .param("phone", phone)
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Override
    public void deleteByPhone(String phone) {
        jdbcClient.sql("DELETE FROM otp_record WHERE phone = :phone")
                .param("phone", phone)
                .update();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OtpRecord mapRow(ResultSet rs) throws SQLException {
        return new OtpRecord(
                rs.getObject("otp_id", UUID.class),
                rs.getString("phone"),
                rs.getString("code_hash"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getBoolean("used"),
                rs.getInt("attempt_count")
        );
    }
}
