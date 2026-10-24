package com.walkmate.data.datasource.remote.dto.response.tracking;

import com.google.gson.annotations.SerializedName;

/**
 * Response for {@code POST /api/v2/tracking/batches}.
 *
 * <p>Returned for both a first commit and a verified replay of an
 * already-committed batch, so a client that lost the first ACK can retry and
 * still converge. {@code payload_hash} is the server's canonical
 * recomputation — if it does not equal the locally stored hash the batch is
 * <em>not</em> considered acknowledged.</p>
 */
public class TrackingBatchResponseDto {

    @SerializedName("batch_id")
    private String batchId;

    @SerializedName("stream_id")
    private String streamId;

    @SerializedName("seq_start")
    private long seqStart;

    @SerializedName("seq_end")
    private long seqEnd;

    @SerializedName("payload_hash")
    private String payloadHash;

    @SerializedName("accepted_point_count")
    private int acceptedPointCount;

    /** {@code COMMITTED} today; {@code PENDING} is reserved for OFFLINE_FAST mode. */
    @SerializedName("status")
    private String status;

    public String getBatchId()              { return batchId; }
    public String getStreamId()             { return streamId; }
    public long getSeqStart()               { return seqStart; }
    public long getSeqEnd()                 { return seqEnd; }
    public String getPayloadHash()          { return payloadHash; }
    public int getAcceptedPointCount()      { return acceptedPointCount; }
    public String getStatus()               { return status; }

    public boolean isCommitted() {
        return "COMMITTED".equals(status);
    }
}
