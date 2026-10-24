package com.walkmate.presentation.dto.request.tracking;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * Request body for {@code POST /api/v2/tracking/batches}.
 *
 * <p>Contract notes (see {@code buhduy/implementation_plan_gps_walkmate.md} §4.3):
 *
 * <ul>
 *   <li>{@code batch_id} is the client's <em>immutable</em> UUID for this batch.
 *       Retries MUST resubmit the identical batch id with an identical payload;
 *       the server verifies and ACKs replays, and rejects any reuse of a batch
 *       id for a different payload with {@code TRACKING_BATCH_ID_REUSED}.</li>
 *   <li>{@code seq_start} / {@code seq_end} are the contiguous source
 *       sequence numbers assigned by the device; {@code seq_end - seq_start + 1}
 *       must equal {@code points.size()}.</li>
 *   <li>{@code payload_hash} is the client's SHA-256 over the
 *       <em>canonical</em> byte encoding (see {@code GpsPayloadEncoder}),
 *       recomputed and compared by the server after parsing.</li>
 *   <li>{@code local_id} (the v1 Room surrogate key) is intentionally absent:
 *       v2 identity is {@code (stream_id, sequence)}.</li>
 * </ul>
 *
 * <p>Deep validation (sequence contiguity, recorded_at windows, payload
 * size) happens in the command service because it needs the batch-wide
 * context; this record only carries bean-validation primitives.</p>
 */
public record TrackingBatchRequest(

        @NotBlank(message = "session_id is required")
        @JsonProperty("session_id")
        String sessionId,

        @NotBlank(message = "stream_id is required")
        @JsonProperty("stream_id")
        String streamId,

        @NotBlank(message = "batch_id is required")
        @Pattern(regexp = UUID_REGEX, message = "batch_id must be a UUID")
        @JsonProperty("batch_id")
        String batchId,

        @NotNull(message = "seq_start is required")
        @JsonProperty("seq_start")
        Long seqStart,

        @NotNull(message = "seq_end is required")
        @JsonProperty("seq_end")
        Long seqEnd,

        @NotBlank(message = "payload_hash is required")
        @Pattern(regexp = "^[0-9a-f]{64}$",
                 message = "payload_hash must be a 64-char lowercase hex SHA-256")
        @JsonProperty("payload_hash")
        String payloadHash,

        @NotEmpty(message = "points must not be empty")
        @Valid
        @JsonProperty("points")
        List<V2Point> points
) {
    public static final String UUID_REGEX =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    /**
     * A single GPS point in immutable sequence order.
     *
     * @param sequence    device-assigned source sequence (1-based)
     * @param recordedAt  epoch millis from {@code Location.getTime()} — the fix
     *                    time, NOT the callback/push time
     * @param lat         latitude degrees
     * @param lng         longitude degrees
     * @param accuracy    horizontal accuracy in meters (device-estimated)
     */
    public record V2Point(

            @NotNull(message = "sequence is required")
            @PositiveOrZero(message = "sequence must be >= 0")
            @JsonProperty("sequence")
            long sequence,

            @NotNull(message = "recorded_at is required")
            @PositiveOrZero(message = "recorded_at must be >= 0")
            @JsonProperty("recorded_at")
            long recordedAt,

            @NotNull(message = "lat is required")
            @DecimalMin(value = "-90.0", message = "lat must be >= -90")
            @DecimalMax(value = "90.0", message = "lat must be <= 90")
            @JsonProperty("lat")
            double lat,

            @NotNull(message = "lng is required")
            @DecimalMin(value = "-180.0", message = "lng must be >= -180")
            @DecimalMax(value = "180.0", message = "lng must be <= 180")
            @JsonProperty("lng")
            double lng,

            @NotNull(message = "accuracy is required")
            @DecimalMin(value = "0.0", message = "accuracy must be >= 0")
            @JsonProperty("accuracy")
            float accuracy
    ) {}
}
