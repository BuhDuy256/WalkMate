package com.walkmate.domain.proposal;

import com.walkmate.domain.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProposalErrorCode implements ErrorCode {
    PROPOSAL_NOT_FOUND("Match proposal not found"),
    PROPOSAL_NOT_PARTICIPANT("You are not a participant in this proposal"),
    PROPOSAL_ALREADY_TERMINAL("Proposal is no longer in PENDING state"),
    PROPOSAL_INTENT_NO_LONGER_OPEN("One or both intents are no longer OPEN — matching conflict");

    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
