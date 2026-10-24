package com.walkmate.data.datasource.remote.dto.request.tracking;

import com.google.gson.annotations.SerializedName;

/**
 * Request body for {@code POST /api/v2/tracking/streams/{streamId}/seal}.
 *
 * <p>Declares the last sequence the device ever recorded. The server stamps the
 * late-upload deadline and promotes the stream to {@code COMPLETE} as soon as
 * coverage of {@code [1, finalSequence]} is satisfied — which may be a later
 * retry, since batches can still be in the queue when Finish is tapped.</p>
 */
public class SealStreamRequestDto {

    @SerializedName("final_sequence")
    private final long finalSequence;

    /** Epoch millis the walk actually ended; null keeps the server's stored value. */
    @SerializedName("recorded_ended_at")
    private final Long recordedEndedAt;

    public SealStreamRequestDto(long finalSequence, Long recordedEndedAt) {
        this.finalSequence = finalSequence;
        this.recordedEndedAt = recordedEndedAt;
    }

    public long getFinalSequence()   { return finalSequence; }
    public Long getRecordedEndedAt() { return recordedEndedAt; }
}
