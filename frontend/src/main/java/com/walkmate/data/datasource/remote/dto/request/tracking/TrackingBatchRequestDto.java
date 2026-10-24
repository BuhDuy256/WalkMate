package com.walkmate.data.datasource.remote.dto.request.tracking;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Request body for {@code POST /api/v2/tracking/batches}.
 *
 * <p>Every field here is derived from a <em>frozen</em> local batch row. The
 * same {@code batch_id} must always carry the same {@code payload_hash} and the
 * same points: the server treats a mismatch as
 * {@code 409 TRACKING_BATCH_ID_REUSED} and refuses to overwrite the committed
 * route.</p>
 *
 * <p>There is no {@code local_id}: the v2 server identity is
 * {@code (stream_id, sequence)}.</p>
 */
public class TrackingBatchRequestDto {

    @SerializedName("session_id")
    private final String sessionId;

    @SerializedName("stream_id")
    private final String streamId;

    @SerializedName("batch_id")
    private final String batchId;

    @SerializedName("seq_start")
    private final long seqStart;

    @SerializedName("seq_end")
    private final long seqEnd;

    @SerializedName("payload_hash")
    private final String payloadHash;

    @SerializedName("points")
    private final List<PointDto> points;

    public TrackingBatchRequestDto(String sessionId, String streamId, String batchId,
                                   long seqStart, long seqEnd, String payloadHash,
                                   List<PointDto> points) {
        this.sessionId = sessionId;
        this.streamId = streamId;
        this.batchId = batchId;
        this.seqStart = seqStart;
        this.seqEnd = seqEnd;
        this.payloadHash = payloadHash;
        this.points = points;
    }

    public String getSessionId()   { return sessionId; }
    public String getStreamId()    { return streamId; }
    public String getBatchId()     { return batchId; }
    public long getSeqStart()      { return seqStart; }
    public long getSeqEnd()        { return seqEnd; }
    public String getPayloadHash() { return payloadHash; }
    public List<PointDto> getPoints() { return points; }

    /** One GPS fix in immutable sequence order. */
    public static class PointDto {

        @SerializedName("sequence")
        private final long sequence;

        /** Fix time from {@code Location.getTime()}, not the callback time. */
        @SerializedName("recorded_at")
        private final long recordedAt;

        @SerializedName("lat")
        private final double lat;

        @SerializedName("lng")
        private final double lng;

        @SerializedName("accuracy")
        private final float accuracy;

        public PointDto(long sequence, long recordedAt, double lat, double lng, float accuracy) {
            this.sequence = sequence;
            this.recordedAt = recordedAt;
            this.lat = lat;
            this.lng = lng;
            this.accuracy = accuracy;
        }

        public long getSequence()   { return sequence; }
        public long getRecordedAt() { return recordedAt; }
        public double getLat()      { return lat; }
        public double getLng()      { return lng; }
        public float getAccuracy()  { return accuracy; }
    }
}
