package com.walkmate.domain.walkintent;

public enum IntentStatus {
    OPEN,       // Actively looking for a match
    MATCHED,    // A match_proposal has been accepted by both parties
    CANCELLED,  // User cancelled before matching
    EXPIRED     // expires_at passed without a match
}
