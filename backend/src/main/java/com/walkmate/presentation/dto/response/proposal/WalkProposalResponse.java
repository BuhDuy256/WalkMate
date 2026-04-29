package com.walkmate.presentation.dto.response.proposal;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

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

        @JsonProperty("matched_user_name")
        String matchedUserName,

        @JsonProperty("matched_user_avatar_url")
        String matchedUserAvatarUrl,

        @JsonProperty("matched_user_age")
        int matchedUserAge,

        @JsonProperty("matched_user_trust_score")
        int matchedUserTrustScore,

        @JsonProperty("overlapping_tags")
        List<String> overlappingTags,

        @JsonProperty("proposed_time_start")
        String proposedTimeStart,

        @JsonProperty("proposed_time_end")
        String proposedTimeEnd,

        @JsonProperty("hotspot_id")
        String hotspotId,

        @JsonProperty("hotspot_name")
        String hotspotName,

        String status,

        @JsonProperty("expires_at")
        String expiresAt,

        @JsonProperty("session_id")
        String sessionId,

        @JsonProperty("my_acceptance_status")
        String myAcceptanceStatus,

        @JsonProperty("is_private")
        boolean isPrivate
) {
}
