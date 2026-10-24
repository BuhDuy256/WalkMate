package com.walkmate.application.shared;

/**
 * Signals that a matching operation observed a transient concurrency conflict that
 * should be re-attempted from scratch (a fresh transaction and a fresh state read).
 *
 * <p>Distinct from {@link com.walkmate.domain.shared.exception.DomainException}: this is
 * not a business-validation failure, so the retry executor will re-run the attempt rather
 * than surfacing it immediately. Thrown, for example, when a matched candidate intent is
 * no longer OPEN by the time its row lock is acquired.</p>
 */
public class RetryableConcurrencyException extends RuntimeException {

    public RetryableConcurrencyException(String message) {
        super(message);
    }
}
