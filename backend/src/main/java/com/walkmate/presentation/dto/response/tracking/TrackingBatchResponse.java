package com.walkmate.presentation.dto.response.tracking;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for {@code POST /api/v2/tracking/batches}.
 *
 * <p>Returned only on success paths:
 *
 * <ul>
 *   <li>{@code COMMITTED} — the batch (or its earlier commit, for a replay)
 *       is durable in PostgreSQL.</li>
 *   <li>{@code PENDING} — reserved for the OFFLINE_FAST upload mode
 *       (server stores the batch only after a post-commit persistence
 *       barrier). The current build always returns {@code COMMITTED}; the
 *       client contract anticipates {@code PENDING} so the retry logic does
 *       not need to change when that mode is enabled server-side.</li>
 * </ul>
 *
 * <p>{@code ack_fields} is present for {@code PENDING} rows (client must
 * re-verify before marking synced); the current build always returns {@code
 * COMMITTED} with {@code ack_fields = null}. The client treats a 200 as a
 * verifiable ACK exactly as it does for a committed replay.</p>
 */
public record TrackingBatchResponse(

        @JsonProperty("batch_id")
        String batchId,

        @JsonProperty("stream_id")
        String streamId,

        @JsonProperty("seq_start")
        long seqStart,

        @JsonProperty("seq_end")
        long seqEnd,

        /** The server-recomputed canonical SHA-256 — the value the client must echo back. */
        @JsonProperty("payload_hash")
        String payloadHash,

        @JsonProperty("accepted_point_count")
        int acceptedPointCount,

        @JsonProperty("status")
        String status,

        /** Non-null only when {@code status == "PENDING"}; null for COMMITTED. */
        @JsonProperty("ack_fields")
        Boolean ackFields
) {
    public static final String STATUS_COMMITTED = "COMMITTED";
    public static final String STATUS_PENDING   = "PENDING";
}
