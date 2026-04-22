package com.walkmate.presentation.dto.response.session;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO returned for all session-related endpoints.
 *
 * Timestamps are serialised as ISO-8601 strings. Nullable fields are omitted
 * from JSON when null by the global Jackson config.
 *
 * Per-participant fields (user_a_status / user_b_status and the individual
 * metrics) allow the frontend to render the correct controls for the local
 * user without depending on the global status alone.
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

        // ── Global session status ─────────────────────────────────────────────
        @JsonProperty("status")
        String status,

        @JsonProperty("created_at")
        String createdAt,               // ISO-8601

        @JsonProperty("started_at")
        String startedAt,               // ISO-8601, set on first participant activation

        @JsonProperty("ended_at")
        String endedAt,                 // ISO-8601, set when last participant terminates

        // ── Per-participant arrival timestamps ────────────────────────────────
        @JsonProperty("user_a_activated_at")
        String userAActivatedAt,        // ISO-8601, null until user A arrives

        @JsonProperty("user_b_activated_at")
        String userBActivatedAt,        // ISO-8601, null until user B arrives

        // ── Per-participant independent lifecycle ─────────────────────────────
        @JsonProperty("user_a_status")
        String userAStatus,             // PENDING | ACTIVE | COMPLETED | …

        @JsonProperty("user_b_status")
        String userBStatus,             // PENDING | ACTIVE | COMPLETED | …

        @JsonProperty("user_a_ended_at")
        String userAEndedAt,            // ISO-8601, null until user A finishes

        @JsonProperty("user_b_ended_at")
        String userBEndedAt,            // ISO-8601, null until user B finishes

        // ── Per-participant walk metrics ──────────────────────────────────────
        @JsonProperty("user_a_distance_km")
        double userADistanceKm,

        @JsonProperty("user_a_duration_seconds")
        long userADurationSeconds,

        @JsonProperty("user_b_distance_km")
        double userBDistanceKm,

        @JsonProperty("user_b_duration_seconds")
        long userBDurationSeconds,

        // ── Cancellation / abort metadata ─────────────────────────────────────
        @JsonProperty("cancellation_reason")
        String cancellationReason,      // null unless CANCELLED

        @JsonProperty("abort_reason")
        String abortReason,             // null unless ABORTED

        @JsonProperty("is_reviewed")
        boolean isReviewed
) {}
