package com.walkmate.domain.user;

import com.walkmate.domain.shared.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PasswordResetOtpTest {

    private static final String EMAIL    = "user@example.com";
    private static final UUID   USER_ID  = UUID.randomUUID();
    private static final String CODE     = "123456";
    private static final String HASH     = "hashed_" + CODE;

    // Fake matcher: hash == "hashed_" + raw
    private final PasswordMatcher matcher = (raw, encoded) -> encoded.equals("hashed_" + raw);

    private PasswordResetOtp otp;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        otp = PasswordResetOtp.create(EMAIL, USER_ID, HASH, now.plusSeconds(300));
    }

    // ── verifyOtp ─────────────────────────────────────────────────────────────

    @Test
    void verifyOtp_success_stampsVerifiedState() {
        String tokenHash = "token_hash";
        Instant tokenExpiry = now.plusSeconds(600);

        otp.verifyOtp(CODE, matcher, tokenHash, tokenExpiry, now);

        assertNotNull(otp.getVerifiedAt());
        assertEquals(tokenHash, otp.getResetTokenHash());
        assertEquals(tokenExpiry, otp.getResetTokenExpiresAt());
        assertEquals(1, otp.getAttemptCount());
    }

    @Test
    void verifyOtp_wrongCode_throwsInvalidAndIncrementsAttempt() {
        DomainException ex = assertThrows(DomainException.class, () ->
                otp.verifyOtp("000000", matcher, "hash", now.plusSeconds(600), now));

        assertEquals(UserErrorCode.USER_OTP_INVALID, ex.getErrorCode());
        assertEquals(1, otp.getAttemptCount());
    }

    @Test
    void verifyOtp_expired_throwsExpired() {
        PasswordResetOtp expired = PasswordResetOtp.create(EMAIL, USER_ID, HASH, now.minusSeconds(1));

        DomainException ex = assertThrows(DomainException.class, () ->
                expired.verifyOtp(CODE, matcher, "hash", now.plusSeconds(600), now));

        assertEquals(UserErrorCode.USER_OTP_EXPIRED, ex.getErrorCode());
    }

    @Test
    void verifyOtp_attemptsExceeded_throwsAfterFiveWrongAttempts() {
        PasswordResetOtp rehydrated = new PasswordResetOtp(
                UUID.randomUUID(), USER_ID, EMAIL, HASH,
                now.plusSeconds(300), 5,
                null, null, null, null, now.minusSeconds(10));

        DomainException ex = assertThrows(DomainException.class, () ->
                rehydrated.verifyOtp(CODE, matcher, "hash", now.plusSeconds(600), now));

        assertEquals(UserErrorCode.USER_OTP_ATTEMPTS_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void verifyOtp_alreadyUsed_throwsAlreadyUsed() {
        otp.verifyOtp(CODE, matcher, "hash", now.plusSeconds(600), now);

        DomainException ex = assertThrows(DomainException.class, () ->
                otp.verifyOtp(CODE, matcher, "hash2", now.plusSeconds(600), now));

        assertEquals(UserErrorCode.USER_OTP_ALREADY_USED, ex.getErrorCode());
    }

    // ── validateResetToken ────────────────────────────────────────────────────

    @Test
    void validateResetToken_valid_noException() {
        otp.verifyOtp(CODE, matcher, "hash", now.plusSeconds(600), now);
        assertDoesNotThrow(() -> otp.validateResetToken(now.plusSeconds(1)));
    }

    @Test
    void validateResetToken_tokenExpired_throwsInvalid() {
        otp.verifyOtp(CODE, matcher, "hash", now.plusSeconds(10), now);

        DomainException ex = assertThrows(DomainException.class, () ->
                otp.validateResetToken(now.plusSeconds(600)));

        assertEquals(UserErrorCode.USER_RESET_TOKEN_INVALID, ex.getErrorCode());
    }

    @Test
    void validateResetToken_notVerified_throwsInvalid() {
        DomainException ex = assertThrows(DomainException.class, () ->
                otp.validateResetToken(now));

        assertEquals(UserErrorCode.USER_RESET_TOKEN_INVALID, ex.getErrorCode());
    }

    // ── consume ───────────────────────────────────────────────────────────────

    @Test
    void consume_setsConsumedAt() {
        otp.verifyOtp(CODE, matcher, "hash", now.plusSeconds(600), now);
        otp.consume(now);
        assertNotNull(otp.getConsumedAt());
    }

    @Test
    void consume_twice_throwsInvalid() {
        otp.verifyOtp(CODE, matcher, "hash", now.plusSeconds(600), now);
        otp.consume(now);

        DomainException ex = assertThrows(DomainException.class, () -> otp.consume(now));
        assertEquals(UserErrorCode.USER_RESET_TOKEN_INVALID, ex.getErrorCode());
    }
}
