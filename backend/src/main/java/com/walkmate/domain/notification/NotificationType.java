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
    REVIEW_REQUESTED,
    /** Sent to the private-invite sender to confirm their invite was dispatched. */
    INVITE_SENT,
    /** Sent to the addressee when a user sends a friend request (UC-34). */
    FRIEND_REQUEST_RECEIVED,
    /** Sent to the requester when the addressee accepts the friend request (UC-35). */
    FRIEND_REQUEST_ACCEPTED,
    /** Sent to the requester when the addressee declines the friend request (UC-35). */
    FRIEND_REQUEST_DECLINED
}
