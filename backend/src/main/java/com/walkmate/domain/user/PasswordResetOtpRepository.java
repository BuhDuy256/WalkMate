package com.walkmate.domain.user;

import java.util.Optional;

public interface PasswordResetOtpRepository {

    void save(PasswordResetOtp otp);

    /** Returns the latest non-consumed OTP record for the given email. */
    Optional<PasswordResetOtp> findActiveLatestByEmail(String email);

    /** Looks up a record by the SHA-256 hash of the reset token. */
    Optional<PasswordResetOtp> findByResetTokenHash(String resetTokenHash);

    /** Marks all active (non-consumed) OTP records for the email as consumed. */
    void invalidateActiveByEmail(String email);
}
