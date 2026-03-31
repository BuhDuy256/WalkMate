package com.walkmate.domain.walkintent;

public enum IntentStatus {
    OPEN,       // Actively looking for a match
    CONSUMED,   // Matched — a proposal has been accepted by both parties (canonical Phase-0 name)
    CANCELLED,  // User cancelled before matching
    EXPIRED,    // expires_at passed without a match

    /** @deprecated DB legacy value — superseded by CONSUMED. Retained so existing rows deserialise without error. */
    @Deprecated
    MATCHED
}
