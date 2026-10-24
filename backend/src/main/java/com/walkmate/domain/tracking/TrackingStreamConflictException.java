package com.walkmate.domain.tracking;

/**
 * Thrown when the client presents a stream id that differs from the stream
 * the server already has for the same (session, user) — i.e. a second
 * device (or a reset/corrupt local stream) is trying to write into the same
 * walk. Mapped to 409 by the exception handler.
 */
public final class TrackingStreamConflictException extends RuntimeException {
    public TrackingStreamConflictException(String message) {
        super(message);
    }
}
