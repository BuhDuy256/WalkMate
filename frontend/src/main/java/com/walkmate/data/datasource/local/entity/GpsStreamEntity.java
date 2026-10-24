package com.walkmate.data.datasource.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import androidx.annotation.NonNull;

/**
 * One GPS upload stream per {@code (sessionId, userId)} — Room mirror of the
 * backend {@code tracking_streams} row.
 *
 * <p>The client mints {@code streamId} once and reuses it across process
 * restarts, so a retried upload after a crash still belongs to the same stream
 * the server already knows about.</p>
 *
 * <p>{@code nextSequence} is the counter reserved under transaction when a
 * point is accepted; it is the authority for ordering, not
 * {@code MAX(sequence) + 1} read in a separate transaction.</p>
 */
@Entity(
        tableName = "gps_stream",
        primaryKeys = {"sessionId", "userId"}
)
public class GpsStreamEntity {

    @NonNull
    public String sessionId = "";

    @NonNull
    public String userId = "";

    public String streamId;

    /** Next sequence to hand out. Starts at 1. */
    public long nextSequence = 1L;

    /** Mirrors {@code GpsSyncState}; null until the stream is sealed. */
    public String syncState;

    /** Declared final sequence; null while the walk is still recording. */
    public Long finalSequence;

    /** Epoch millis the walk actually ended, declared at seal time. */
    public Long recordedEndedAt;

    public long createdAt;
    public long updatedAt;
}
