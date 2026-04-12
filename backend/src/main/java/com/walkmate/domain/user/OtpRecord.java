package com.walkmate.domain.user;

import com.walkmate.domain.shared.exception.DomainException;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks a single OTP challenge issued to a phone number.
 * Rich domain: self-validates on verify() — throws if expired, already-used, or incorrect.
 */
@Getter
public class OtpRecord {

    private static final int MAX_ATTEMPTS = 5;

    private UUID    otpId;
    private String  phone;
    private String  codeHash;
    private Instant expiresAt;
    private boolean used;
    private int     attemptCount;

    protected OtpRecord() {}

    /** Rehydration constructor (repository → domain). */
    public OtpRecord(UUID otpId, String phone, String codeHash,
                     Instant expiresAt, boolean used, int attemptCount) {
        this.otpId        = otpId;
        this.phone        = phone;
        this.codeHash     = codeHash;
        this.expiresAt    = expiresAt;
        this.used         = used;
        this.attemptCount = attemptCount;
    }

    /** Factory — issues a new OTP record (not yet persisted). */
    public static OtpRecord issue(String phone, String codeHash, Instant expiresAt) {
        OtpRecord r = new OtpRecord();
        r.phone        = phone;
        r.codeHash     = codeHash;
        r.expiresAt    = expiresAt;
        r.used         = false;
        r.attemptCount = 0;
        return r;
    }

    /**
     * Verifies the raw OTP code.
     * Side-effects: increments attemptCount; sets used = true on success.
     * All domain violations are thrown as {@link DomainException}.
     */
    public void verify(String rawCode, OtpCodeMatcher matcher) {
        if (this.used) {
            throw new DomainException(UserErrorCode.USER_OTP_ALREADY_USED);
        }
        if (Instant.now().isAfter(this.expiresAt)) {
            throw new DomainException(UserErrorCode.USER_OTP_EXPIRED);
        }
        this.attemptCount++;
        if (this.attemptCount > MAX_ATTEMPTS) {
            throw new DomainException(UserErrorCode.USER_OTP_ATTEMPTS_EXCEEDED);
        }
        if (!matcher.matches(rawCode, this.codeHash)) {
            throw new DomainException(UserErrorCode.USER_OTP_INVALID);
        }
        this.used = true;
    }
}
