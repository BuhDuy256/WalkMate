package com.walkmate.data.datasource.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/**
 * Room entity that persists runtime tracking state for a walk session.
 *
 * One row per (session, user) pair. The composite primary key ensures that
 * User A and User B — who share the same sessionId as walk partners — each
 * have an isolated timer state and cannot overwrite each other's data on a
 * shared device. The row is deleted once the session completes or is
 * forcefully finished so re-opening the screen does not restore a stale state.
 */
@Entity(tableName = "tracking_state", primaryKeys = {"sessionId", "userId"})
public class TrackingStateEntity {

    @NonNull
    public String sessionId = "";

    /** The logged-in user this state belongs to. */
    @NonNull
    public String userId = "";

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
