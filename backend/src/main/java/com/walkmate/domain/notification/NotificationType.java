package com.walkmate.domain.notification;

/**
 * All notification types produced by WalkMate lifecycle events.
 *
 * <ul>
 *   <li>{@link #PROPOSAL_RECEIVED} — a new match proposal arrived for the user.</li>
 *   <li>{@link #SESSION_CONFIRMED} — both users accepted; a walk session was created.</li>
 *   <li>{@link #SESSION_ACTIVE} — both users activated (arrived); the walk has started.</li>
 *   <li>{@link #REVIEW_REQUESTED} — the session ended; user is invited to leave a review.</li>
 * </ul>
 */
public enum NotificationType {
    PROPOSAL_RECEIVED,
    SESSION_CONFIRMED,
    SESSION_ACTIVE,
    REVIEW_REQUESTED
}
