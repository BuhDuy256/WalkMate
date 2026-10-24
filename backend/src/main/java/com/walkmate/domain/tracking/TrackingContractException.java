package com.walkmate.domain.tracking;

import com.walkmate.domain.shared.exception.DomainException;

/**
 * A v2 sync-contract violation: not found, not a participant, overlap,
 * hash mismatch, stream closed, etc.
 *
 * <p>Thrown from the application layer and translated by
 * {@code GlobalExceptionHandler} into the HTTP status each code's
 * {@link TrackingErrorCode#defaultStatus()} declares (409/410/413) so the
 * client retry policy can react deterministically.</p>
 */
public class TrackingContractException extends DomainException {

    public TrackingContractException(TrackingErrorCode code) {
        super(code);
    }

    public TrackingContractException(TrackingErrorCode code, String customMessage) {
        super(code, customMessage);
    }

    public TrackingContractException(TrackingErrorCode code, String customMessage, Throwable cause) {
        super(code, customMessage);
        initCause(cause);
    }

    @Override
    public com.walkmate.domain.shared.exception.ErrorCode getErrorCode() {
        return (com.walkmate.domain.shared.exception.ErrorCode) super.getErrorCode();
    }

    /** Convenience for handlers/tests: the tracking-specific code, or null. */
    public TrackingErrorCode trackingCode() {
        return com.walkmate.domain.shared.exception.DomainException.class
                .isInstance(this) && super.getErrorCode() instanceof TrackingErrorCode te
                ? te : null;
    }
}
