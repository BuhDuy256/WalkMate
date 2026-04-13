package com.walkmate.presentation.dto.response.social;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a friendship row returned to the client.
 * JSON field names match DB column names (requester_id / addressee_id).
 */
public record FriendshipResponse(
        @JsonProperty("friendship_id") String friendshipId,
        @JsonProperty("requester_id")  String requesterId,
        @JsonProperty("addressee_id")  String addresseeId,
        String status,
        @JsonProperty("created_at")    String createdAt
) {}
