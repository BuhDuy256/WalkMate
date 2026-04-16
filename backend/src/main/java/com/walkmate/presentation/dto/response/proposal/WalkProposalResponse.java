package com.walkmate.presentation.dto.response.proposal;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload for all proposal-related endpoints.
 *
 * When status == "CONFIRMED", sessionId is populated with the created
 * session's ID. The client can then call GET /api/v1/sessions/active
 * to load the full session details.
 *
 * myAcceptanceStatus reflects whether the authenticated caller has already
 * tapped "Accept" on this proposal: "ACCEPTED" or "PENDING".
 * The UI uses this to render the correct button state on the proposal card
 * (e.g., show "Waiting for partner..." instead of "Accept" when ACCEPTED).
 */
public record WalkProposalResponse(

        @JsonProperty("proposal_id")
        String proposalId,

        @JsonProperty("callers_intent_id")
        String callersIntentId,

        @JsonProperty("matched_intent_id")
        String matchedIntentId,

        @JsonProperty("callers_user_id")
        String callersUserId,

        @JsonProperty("matched_user_id")
        String matchedUserId,

        @JsonProperty("proposed_time_start")
        String proposedTimeStart,   // ISO-8601

        @JsonProperty("proposed_time_end")
        String proposedTimeEnd,     // ISO-8601

        @JsonProperty("proposed_lat")
        double proposedLat,

        @JsonProperty("proposed_lng")
        double proposedLng,

        String status,

        @JsonProperty("expires_at")
        String expiresAt,

        @JsonProperty("session_id")
        String sessionId,           // null until CONFIRMED

        @JsonProperty("my_acceptance_status")
        String myAcceptanceStatus   // "PENDING" | "ACCEPTED"
) {
}
