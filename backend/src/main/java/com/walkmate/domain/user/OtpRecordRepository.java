package com.walkmate.domain.user;

import java.util.Optional;

public interface OtpRecordRepository {

    OtpRecord save(OtpRecord otpRecord);

    /** Returns the most recently issued (not yet expired) OTP for the given phone. */
    Optional<OtpRecord> findLatestByPhone(String phone);

    void deleteByPhone(String phone);
}
