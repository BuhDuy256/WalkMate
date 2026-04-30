package com.walkmate.application.session;

public interface SessionQrTokenProvider {

    /** Generates a short-lived signed token that encodes {@code userId} and {@code sessionId}. */
    String generateQrToken(String userId, String sessionId);

    /**
     * Validates the token and returns the {@code userId} embedded in it.
     * Throws a {@link com.walkmate.domain.shared.exception.DomainException} with
     * {@code SESSION_QR_TOKEN_INVALID} or {@code SESSION_QR_TOKEN_EXPIRED} on failure.
     */
    String validateQrToken(String token, String expectedSessionId);
}
