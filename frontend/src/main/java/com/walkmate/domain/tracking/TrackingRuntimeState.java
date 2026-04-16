package com.walkmate.domain.tracking;

/**
 * Immutable domain snapshot of runtime tracking state for a walk session.
 * Persisted to Room so the session can be resumed after the screen exits.
 */
public class TrackingRuntimeState {

    private final String sessionId;
    private final WalkState walkState;
    private final long walkStartEpochMs;
    private final long pausedAccumulatedMs;
    private final long pauseStartEpochMs;
    private final long updatedAt;

    public TrackingRuntimeState(
            String sessionId,
            WalkState walkState,
            long walkStartEpochMs,
            long pausedAccumulatedMs,
            long pauseStartEpochMs,
            long updatedAt) {
        this.sessionId           = sessionId;
        this.walkState           = walkState;
        this.walkStartEpochMs    = walkStartEpochMs;
        this.pausedAccumulatedMs = pausedAccumulatedMs;
        this.pauseStartEpochMs   = pauseStartEpochMs;
        this.updatedAt           = updatedAt;
    }

    public String getSessionId()           { return sessionId; }
    public WalkState getWalkState()        { return walkState; }
    public long getWalkStartEpochMs()      { return walkStartEpochMs; }
    public long getPausedAccumulatedMs()   { return pausedAccumulatedMs; }
    public long getPauseStartEpochMs()     { return pauseStartEpochMs; }
    public long getUpdatedAt()             { return updatedAt; }
}
