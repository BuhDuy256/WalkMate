package com.walkmate.domain.session;

public enum SessionStatus {
    PENDING,    // Both accepted — waiting for the scheduled walk time
    ACTIVE,     // Walk is in progress
    COMPLETED,  // Walk finished (globally: both users reached a terminal state)
    NO_SHOW,    // Per-user only: a participant did not arrive; global always resolves to COMPLETED
    CANCELLED   // Cancelled before either participant activated
}
