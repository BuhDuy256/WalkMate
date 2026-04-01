package com.walkmate.presentation.dto.response.session;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO returned for all session-related endpoints.
 *
 * Timestamps are serialised as ISO-8601 strings. Nullable fields are omitted
 * from JSON when null by the global Jackson config.
 */
public record WalkSessionResponse(

        @JsonProperty("session_id")
        String sessionId,

        @JsonProperty("proposal_id")
        String proposalId,

        @JsonProperty("user_id_a")
        String userIdA,

        @JsonProperty("user_id_b")
        String userIdB,

        @JsonProperty("meeting_point_lat")
        double meetingPointLat,

        @JsonProperty("meeting_point_lng")
        double meetingPointLng,

        @JsonProperty("scheduled_start")
        String scheduledStart,          // ISO-8601

        @JsonProperty("scheduled_end")
        String scheduledEnd,            // ISO-8601

        @JsonProperty("status")
        String status,

        @JsonProperty("created_at")
        String createdAt,               // ISO-8601

        @JsonProperty("started_at")
        String startedAt,               // ISO-8601, null until ACTIVE

        @JsonProperty("ended_at")
        String endedAt,                 // ISO-8601, null until terminal

        @JsonProperty("user_a_activated_at")
        String userAActivatedAt,        // ISO-8601, null until user A arrives

        @JsonProperty("user_b_activated_at")
        String userBActivatedAt,        // ISO-8601, null until user B arrives

        @JsonProperty("cancellation_reason")
        String cancellationReason,      // null unless CANCELLED

        @JsonProperty("abort_reason")
        String abortReason              // null unless ABORTED
) {}
