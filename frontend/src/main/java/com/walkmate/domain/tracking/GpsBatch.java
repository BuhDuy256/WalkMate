package com.walkmate.domain.tracking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One immutable upload batch as it is stored locally: a contiguous sequence
 * range that was frozen the moment it was sealed.
 *
 * <p>The membership is immutable by construction — points carry the
 * {@code batchId} they were sealed into, so re-building the request on a retry
 * reproduces exactly the same payload. This is what makes a lost-ACK retry
 * verifiable by the server instead of looking like a brand-new batch.</p>
 */
public class GpsBatch {

    private final String batchId;
    private final String sessionId;
    private final String userId;
    private final String streamId;
    private final long seqStart;
    private final long seqEnd;
    private final String payloadHash;
    private final String state;
    private final int retryCount;
    private final long nextAttemptAt;
    private final List<GpsPayloadHasher.Point> points;

    public GpsBatch(String batchId, String sessionId, String userId, String streamId,
                    long seqStart, long seqEnd, String payloadHash, String state,
                    int retryCount, long nextAttemptAt,
                    List<GpsPayloadHasher.Point> points) {
        this.batchId = batchId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.streamId = streamId;
        this.seqStart = seqStart;
        this.seqEnd = seqEnd;
        this.payloadHash = payloadHash;
        this.state = state;
        this.retryCount = retryCount;
        this.nextAttemptAt = nextAttemptAt;
        this.points = points != null
                ? Collections.unmodifiableList(new ArrayList<>(points))
                : Collections.<GpsPayloadHasher.Point>emptyList();
    }

    public String getBatchId()          { return batchId; }
    public String getSessionId()        { return sessionId; }
    public String getUserId()           { return userId; }
    public String getStreamId()         { return streamId; }
    public long getSeqStart()           { return seqStart; }
    public long getSeqEnd()             { return seqEnd; }
    public String getPayloadHash()      { return payloadHash; }
    public String getState()            { return state; }
    public int getRetryCount()          { return retryCount; }
    public long getNextAttemptAt()      { return nextAttemptAt; }
    public List<GpsPayloadHasher.Point> getPoints() { return points; }

    public int getPointCount() {
        return points.size();
    }
}
