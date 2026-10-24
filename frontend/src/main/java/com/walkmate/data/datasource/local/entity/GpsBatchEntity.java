package com.walkmate.data.datasource.local.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import androidx.annotation.NonNull;

/**
 * One immutable upload batch in the persistent queue — the local half of the
 * server's {@code session_point_chunks} row.
 *
 * <p>The row is written in the same transaction that freezes point membership,
 * so {@code batchId}, the sequence range and the payload hash all describe the
 * same content forever. Retries re-read this row and resend the identical
 * payload, which is what lets the server recognise a lost ACK as a replay
 * instead of a new batch.</p>
 *
 * <p>{@code leaseUntil} provides crash recovery: a worker that dies mid-upload
 * leaves the row {@code IN_FLIGHT} with an expiring lease, after which any
 * worker may re-claim it.</p>
 */
@Entity(
        tableName = "gps_batches",
        indices = {
                @Index(name = "index_gps_batches_owner",
                        value = {"sessionId", "userId", "state"}),
                @Index(name = "index_gps_batches_stream_range",
                        value = {"streamId", "seqStart"})
        }
)
public class GpsBatchEntity {

    @PrimaryKey
    @NonNull
    public String batchId = "";

    public String sessionId;
    public String userId;
    public String streamId;

    public long seqStart;
    public long seqEnd;

    /** Canonical SHA-256 of the frozen payload, recomputed identically on retry. */
    public String payloadHash;

    /** One of {@link com.walkmate.domain.tracking.GpsBatchState}. */
    public String state;

    public int retryCount;

    /** Epoch millis before which no retry should be attempted (backoff). */
    public long nextAttemptAt;

    /** Epoch millis after which an IN_FLIGHT batch may be re-claimed. */
    public long leaseUntil;

    /** Populated only for BLOCKED rows, to make the refusal diagnosable. */
    public String blockReason;

    public long createdAt;
    public Long ackedAt;
}
