package com.walkmate.domain.tracking;

/**
 * Sync state of one GPS stream, as reported by the backend
 * ({@code gps_stream.syncState} locally, {@code tracking_streams.sync_state}
 * server-side).
 *
 * <p>It is an axis of its own, separate from the walk business status: a
 * session may be {@code COMPLETED} while the stream is still {@code OPEN} or
 * {@code SEALED} and late batches are still arriving.</p>
 */
public final class GpsSyncState {

    /** Batches may still be pushed; the client has not declared a final sequence. */
    public static final String OPEN = "OPEN";

    /** Final sequence declared; late batches accepted until the upload deadline. */
    public static final String SEALED = "SEALED";

    /** Every sequence in {@code [1, finalSequence]} is present on the server. */
    public static final String COMPLETE = "COMPLETE";

    /** The upload deadline elapsed without full coverage. */
    public static final String EXPIRED = "EXPIRED";

    private GpsSyncState() {}

    public static boolean isTerminal(String state) {
        return COMPLETE.equals(state) || EXPIRED.equals(state);
    }
}
