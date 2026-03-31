package com.walkmate.domain.proposal;

public enum ProposalStatus {
    PENDING,    // Awaiting both users' acceptance
    CONFIRMED,  // Both accepted — canonical Phase-0 name (DB legacy: ACCEPTED)
    REJECTED,   // One user passed
    EXPIRED,    // expires_at passed without both accepting

    /** @deprecated DB legacy value — superseded by CONFIRMED. */
    @Deprecated
    ACCEPTED
}
