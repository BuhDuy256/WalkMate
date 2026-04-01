package com.walkmate.domain.proposal;

public enum ProposalStatus {
    PENDING,   // Awaiting both users' acceptance
    CONFIRMED, // Both users accepted
    REJECTED,  // One user passed
    EXPIRED    // expires_at passed without both accepting
}
