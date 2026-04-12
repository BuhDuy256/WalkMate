package com.walkmate.infrastructure.sms;

import com.walkmate.application.user.SmsGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Development stub — logs the OTP code instead of sending a real SMS.
 * Replace with Twilio / AWS SNS implementation before going to production.
 */
@Component
public class NoOpSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(NoOpSmsGateway.class);

    @Override
    public void send(String toPhone, String message) {
        log.info("[NoOpSmsGateway] SMS to {}: {}", toPhone, message);
    }
}
