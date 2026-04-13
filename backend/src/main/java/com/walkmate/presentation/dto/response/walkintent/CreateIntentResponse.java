package com.walkmate.presentation.dto.response.walkintent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.walkmate.presentation.dto.response.proposal.WalkProposalResponse;

/**
 * Presentation DTO for POST /api/v1/intents.
 * proposal is null when no inline match was found (Case A2 — intent stays OPEN).
 * proposal is populated when a candidate was found during creation (Case A1 — intent moves to MATCHING).
 */
public record CreateIntentResponse(
        @JsonProperty("intent")   WalkIntentResponse   intent,
        @JsonProperty("proposal") WalkProposalResponse proposal
) {
}
