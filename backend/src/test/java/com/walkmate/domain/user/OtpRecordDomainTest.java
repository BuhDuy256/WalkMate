package com.walkmate.domain.user;

import com.walkmate.domain.shared.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OtpRecord#verify(String, OtpCodeMatcher)}.
 * Covers: happy path, already-used, expired, wrong code, and max-attempts lockout.
 */
class OtpRecordDomainTest {

    private static final OtpCodeMatcher ALWAYS_MATCH = (raw, hash) -> raw.equals(hash);
    private static final OtpCodeMatcher NEVER_MATCH  = (raw, hash) -> false;

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void verify_correctCode_setsUsedTrueAndIncrementsAttemptCount() {
        OtpRecord record = freshOtp("123456");

        record.verify("123456", ALWAYS_MATCH);

        assertThat(record.isUsed()).isTrue();
        assertThat(record.getAttemptCount()).isEqualTo(1);
    }

    // ── Already used ─────────────────────────────────────────────────────────

    @Test
    void verify_alreadyUsed_throwsOtpAlreadyUsed() {
        OtpRecord used = new OtpRecord(UUID.randomUUID(), "+84901234567", "123456",
                Instant.now().plusSeconds(300), true, 1);

        assertThatThrownBy(() -> used.verify("123456", ALWAYS_MATCH))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_OTP_ALREADY_USED));
    }

    // ── Expired ───────────────────────────────────────────────────────────────

    @Test
    void verify_expired_throwsOtpExpired() {
        OtpRecord expired = new OtpRecord(UUID.randomUUID(), "+84901234567", "123456",
                Instant.now().minusSeconds(1), false, 0);

        assertThatThrownBy(() -> expired.verify("123456", ALWAYS_MATCH))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_OTP_EXPIRED));
    }

    // ── Wrong code ────────────────────────────────────────────────────────────

    @Test
    void verify_wrongCode_throwsOtpInvalidAndIncrementsAttemptCount() {
        OtpRecord record = freshOtp("123456");

        assertThatThrownBy(() -> record.verify("000000", NEVER_MATCH))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_OTP_INVALID));

        assertThat(record.getAttemptCount()).isEqualTo(1);
        assertThat(record.isUsed()).isFalse();
    }

    // ── Max attempts exceeded ─────────────────────────────────────────────────

    @Test
    void verify_maxAttemptsExceeded_throwsOtpAttemptsExceeded() {
        OtpRecord record = freshOtp("123456");

        // Exhaust all 5 allowed attempts with wrong codes
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> record.verify("000000", NEVER_MATCH))
                    .isInstanceOf(DomainException.class)
                    .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.USER_OTP_INVALID));
        }

        // 6th attempt — attempt counter (6) exceeds MAX_ATTEMPTS (5) → lockout
        assertThatThrownBy(() -> record.verify("000000", NEVER_MATCH))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_OTP_ATTEMPTS_EXCEEDED));

        assertThat(record.getAttemptCount()).isEqualTo(6);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OtpRecord freshOtp(String codeHash) {
        return OtpRecord.issue("+84901234567", codeHash, Instant.now().plusSeconds(300));
    }
}
