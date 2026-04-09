package com.walkmate.application.user;

/** Port for dispatching SMS messages. Implement with Twilio, AWS SNS, etc. */
public interface SmsGateway {
    void send(String toPhone, String message);
}
