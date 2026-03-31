package com.walkmate.domain.session;

public enum SessionStatus {
    PENDING,    // Both accepted — waiting for the scheduled walk time
    ACTIVE,     // Walk is in progress
    COMPLETED,  // Walk finished normally
    NO_SHOW,    // One or both users did not show up
    CANCELLED,  // Cancelled before the walk started
    ABORTED     // Emergency mid-walk cancellation
}
