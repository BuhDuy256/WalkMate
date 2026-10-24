package com.walkmate.data.datasource.remote.dto.response.tracking;

import com.google.gson.annotations.SerializedName;

/**
 * Response for the seal and expire endpoints: the server's authoritative view
 * of the stream after the call.
 */
public class SealStreamResponseDto {

    @SerializedName("streamId")
    private String streamId;

    /** One of {@link com.walkmate.domain.tracking.GpsSyncState}. */
    @SerializedName("syncState")
    private String syncState;

    @SerializedName("finalSequence")
    private Long finalSequence;

    /** ISO-8601 instant; null when the server never stamped a deadline. */
    @SerializedName("syncDeadlineAt")
    private String syncDeadlineAt;

    public String getStreamId()        { return streamId; }
    public String getSyncState()       { return syncState; }
    public Long getFinalSequence()     { return finalSequence; }
    public String getSyncDeadlineAt()  { return syncDeadlineAt; }
}
