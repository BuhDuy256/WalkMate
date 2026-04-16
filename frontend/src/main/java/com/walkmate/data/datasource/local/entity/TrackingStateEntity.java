package com.walkmate.data.datasource.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity that persists runtime tracking state for a walk session.
 *
 * One row per active session. The row is deleted once the session
 * completes or is forcefully finished so re-opening the screen
 * does not restore a stale state.
 */
@Entity(tableName = "tracking_state")
public class TrackingStateEntity {

    @PrimaryKey
    @NonNull
    public String sessionId = "";

    /** Serialised {@link com.walkmate.domain.tracking.WalkState} enum name. */
    public String walkState;

    /** Epoch ms when the walk (or first active segment) started. */
    public long walkStartEpochMs;

    /** Total milliseconds already spent paused across all previous pauses. */
    public long pausedAccumulatedMs;

    /** Epoch ms when the current (or last) pause began. 0 if not paused. */
    public long pauseStartEpochMs;

    /** Epoch ms of last update — for debugging / staleness checks. */
    public long updatedAt;
}
