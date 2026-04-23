package com.walkmate.presentation.dto.response.session;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for GET /api/v1/sessions/history entries.
 *
 * {@code participants} contains one entry per walk partner (always two),
 * each carrying the participant's resolved full name and personal walk stats.
 * This avoids a second round-trip from the client to look up partner names.
 *
 * {@code isReviewed} signals whether the authenticated caller has already
 * reviewed this session — the UI uses it to show/hide the "Leave a Review" button.
 */
public record SessionSummaryResponse(

        @JsonProperty("session_id")
        String sessionId,

        @JsonProperty("status")
        String status,

        @JsonProperty("scheduled_start")
        String scheduledStart,

        @JsonProperty("ended_at")
        String endedAt,

        @JsonProperty("is_reviewed")
        boolean isReviewed,

        @JsonProperty("participants")
        List<ParticipantSummaryResponse> participants
) {}
