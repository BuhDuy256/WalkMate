package com.walkmate.domain.user;

import com.walkmate.domain.shared.exception.DomainException;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class PasswordResetOtp {

    private UUID    otpId;
    private UUID    userId;
    private String  email;
    private String  codeHash;
    private Instant otpExpiresAt;
    private int     attemptCount;
    private Instant verifiedAt;
    private String  resetTokenHash;
    private Instant resetTokenExpiresAt;
    private Instant consumedAt;
    private Instant createdAt;

    protected PasswordResetOtp() {}

    // ── Rehydration constructor (repository → domain) ─────────────────────────

    public PasswordResetOtp(UUID otpId, UUID userId, String email, String codeHash,
                            Instant otpExpiresAt, int attemptCount,
                            Instant verifiedAt, String resetTokenHash,
                            Instant resetTokenExpiresAt, Instant consumedAt,
                            Instant createdAt) {
        this.otpId               = otpId;
        this.userId              = userId;
        this.email               = email;
        this.codeHash            = codeHash;
        this.otpExpiresAt        = otpExpiresAt;
        this.attemptCount        = attemptCount;
        this.verifiedAt          = verifiedAt;
        this.resetTokenHash      = resetTokenHash;
        this.resetTokenExpiresAt = resetTokenExpiresAt;
        this.consumedAt          = consumedAt;
        this.createdAt           = createdAt;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static PasswordResetOtp create(String email, UUID userId, String codeHash, Instant otpExpiresAt) {
        PasswordResetOtp otp = new PasswordResetOtp();
        otp.otpId        = null; // assigned by DB
        otp.userId       = userId;
        otp.email        = email;
        otp.codeHash     = codeHash;
        otp.otpExpiresAt = otpExpiresAt;
        otp.attemptCount = 0;
        otp.createdAt    = Instant.now();
        return otp;
    }

    // ── Domain behaviour ──────────────────────────────────────────────────────

    /**
     * Verifies the raw OTP code and, on success, stamps the verified state and
     * attaches the reset token hash for the next step.
     * Increments attemptCount on every call (including failures) — caller must
     * persist this entity after catching USER_OTP_INVALID.
     */
    public void verifyOtp(String rawOtp, PasswordMatcher matcher,
                          String resetTokenHash, Instant resetTokenExpiresAt, Instant now) {
        if (this.consumedAt != null || this.verifiedAt != null) {
            throw new DomainException(UserErrorCode.USER_OTP_ALREADY_USED);
        }
        if (now.isAfter(this.otpExpiresAt)) {
            throw new DomainException(UserErrorCode.USER_OTP_EXPIRED);
        }
        if (this.attemptCount >= 5) {
            throw new DomainException(UserErrorCode.USER_OTP_ATTEMPTS_EXCEEDED);
        }
        this.attemptCount++;
        if (!matcher.matches(rawOtp, this.codeHash)) {
            throw new DomainException(UserErrorCode.USER_OTP_INVALID);
        }
        this.verifiedAt          = now;
        this.resetTokenHash      = resetTokenHash;
        this.resetTokenExpiresAt = resetTokenExpiresAt;
    }

    /** Validates the reset token during the confirm-password step. */
    public void validateResetToken(Instant now) {
        if (this.verifiedAt == null || this.consumedAt != null) {
            throw new DomainException(UserErrorCode.USER_RESET_TOKEN_INVALID);
        }
        if (this.resetTokenHash == null) {
            throw new DomainException(UserErrorCode.USER_RESET_TOKEN_INVALID);
        }
        if (now.isAfter(this.resetTokenExpiresAt)) {
            throw new DomainException(UserErrorCode.USER_RESET_TOKEN_INVALID);
        }
    }

    /** Marks this OTP record as consumed after a successful password reset. */
    public void consume(Instant now) {
        if (this.consumedAt != null) {
            throw new DomainException(UserErrorCode.USER_RESET_TOKEN_INVALID);
        }
        this.consumedAt = now;
    }
}
