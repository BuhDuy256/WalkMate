package com.walkmate.domain.tracking;

public class TrackingErrorCode {
    public static final String SAVE_POINT_FAILED        = "TRACKING_SAVE_POINT_FAILED";
    public static final String SYNC_FAILED              = "TRACKING_SYNC_FAILED";
    public static final String FETCH_POINTS_FAILED      = "TRACKING_FETCH_POINTS_FAILED";
    public static final String FETCH_PARTNER_PATH_FAILED = "TRACKING_FETCH_PARTNER_PATH_FAILED";

    /** The same batch_id was already committed with different content or owner. */
    public static final String BATCH_ID_REUSED          = "TRACKING_BATCH_ID_REUSED";

    /** Two different batch ids claimed overlapping sequence ranges. */
    public static final String SEQUENCE_OVERLAP         = "TRACKING_SEQUENCE_OVERLAP";

    /** Another stream is already bound to this (session, user) pair. */
    public static final String STREAM_CONFLICT          = "TRACKING_STREAM_CONFLICT";

    /** The upload deadline elapsed; no further batches are accepted. */
    public static final String STREAM_EXPIRED           = "TRACKING_STREAM_EXPIRED";

    /** The stream reached full coverage; only replays of committed batches ACK. */
    public static final String STREAM_CLOSED            = "TRACKING_STREAM_CLOSED";

    /** The client-declared hash does not match the server's canonical encoding. */
    public static final String HASH_MISMATCH            = "TRACKING_HASH_MISMATCH";

    /** Batch exceeds the 50-point limit. */
    public static final String BATCH_TOO_LARGE          = "TRACKING_BATCH_TOO_LARGE";

    /** A recorded_at value fell outside the accepted validity window. */
    public static final String RECORDED_AT_INVALID      = "TRACKING_RECORDED_AT_INVALID";

    /** Coordinates, accuracy or sequence contiguity were invalid. */
    public static final String POINT_INVALID            = "TRACKING_POINT_INVALID";

    /** Local sentinel: the batch was parked locally rather than retried. */
    public static final String BATCH_BLOCKED            = "TRACKING_BATCH_BLOCKED";

    private TrackingErrorCode() {}

    /**
     * True for refusals that a retry with the same payload can never fix — the
     * batch must be parked for diagnosis instead of burning retry budget.
     */
    public static boolean isPermanent(String code) {
        return BATCH_ID_REUSED.equals(code)
                || SEQUENCE_OVERLAP.equals(code)
                || STREAM_CONFLICT.equals(code)
                || STREAM_CLOSED.equals(code)
                || HASH_MISMATCH.equals(code)
                || BATCH_TOO_LARGE.equals(code)
                || RECORDED_AT_INVALID.equals(code)
                || POINT_INVALID.equals(code);
    }
}
