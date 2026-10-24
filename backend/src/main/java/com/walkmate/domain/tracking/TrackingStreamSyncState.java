package com.walkmate.domain.tracking;

/**
 * Lifecycle of a GPS upload stream (one per {@code (walk session, user)}).
 *
 * <p>This is deliberately a state machine independent of the session's
 * business status: a session may be {@code COMPLETED} while its streams
 * are still {@code OPEN}/{@code SEALED} (late uploads are accepted
 * until the stream's upload deadline).</p>
 *
 * <ul>
 *   <li>{@link #OPEN}     — batches may be ingested.</li>
 *   <li>{@link #SEALED}   — the client declared {@code expected_final_sequence};
 *                           late batches are accepted until the deadline,
 *                           after which the stream becomes {@link #EXPIRED}.</li>
 *   <li>{@link #COMPLETE} — full coverage of {@code [1, expected_final_sequence]}
 *                           is verified; read-only.</li>
 *   <li>{@link #EXPIRED}  — the upload deadline elapsed without full coverage;
 *                           read-only, partial route retained.</li>
 * </ul>
 */
public enum TrackingStreamSyncState {
    OPEN,
    SEALED,
    COMPLETE,
    EXPIRED
}
