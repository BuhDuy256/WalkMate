package com.walkmate.domain.tracking;

import com.walkmate.domain.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Error codes specific to the GPS v2 sync contract.
 *
 * <p>Each code maps to the HTTP status the client's retry policy must
 * react to: 409 → <em>block and alert</em> (a retry with the same
 * payload cannot succeed), 410 → <em>stop attempts</em> for this
 * stream, 413 → batch too large.</p>
 */
public enum TrackingErrorCode implements ErrorCode {

    /** Client's declared payload hash does not match the canonical recomputation. */
    TRACKING_HASH_MISMATCH("TRACKING_HASH_MISMATCH",
            "Client payload hash does not match the canonical encoding of the request",
            HttpStatus.CONFLICT),

    /** Same batch_id re-sent but with a different payload, owner, stream or range. */
    TRACKING_BATCH_ID_REUSED("TRACKING_BATCH_ID_REUSED",
            "A chunk already exists for this batch_id with a different payload or owner",
            HttpStatus.CONFLICT),

    /** A different batch_id claims a sequence range that overlaps an already-committed range. */
    TRACKING_SEQUENCE_OVERLAP("TRACKING_SEQUENCE_OVERLAP",
            "Sequence range overlaps an already-committed batch for this stream",
            HttpStatus.CONFLICT),

    /** The stream_id on the request does not match the stream stored for this (session, user). */
    TRACKING_STREAM_CONFLICT("TRACKING_STREAM_CONFLICT",
            "Another stream is already committed for this session and user",
            HttpStatus.CONFLICT),

    /** The stream is sealed and past its upload deadline. */
    TRACKING_STREAM_EXPIRED("TRACKING_STREAM_EXPIRED",
            "The upload deadline for this stream has passed",
            HttpStatus.GONE),

    /** The stream is COMPLETE — only replay ACKs of already-committed batches are possible. */
    TRACKING_STREAM_CLOSED("TRACKING_STREAM_CLOSED",
            "This stream is closed (COMPLETE); new batches are not accepted",
            HttpStatus.CONFLICT),

    /** Batch exceeds the allowed number of points. */
    TRACKING_BATCH_TOO_LARGE("TRACKING_BATCH_TOO_LARGE",
            "Batch exceeds the maximum points per request",
            HttpStatus.PAYLOAD_TOO_LARGE),

    /** A point's recorded_at is outside the accepted validity window. */
    TRACKING_RECORDED_AT_INVALID("TRACKING_RECORDED_AT_INVALID",
            "Point recorded_at is outside the accepted validity window",
            HttpStatus.UNPROCESSABLE_ENTITY),

    /** A point's coordinates/accuracy are not finite or out of bounds. */
    TRACKING_POINT_INVALID("TRACKING_POINT_INVALID",
            "Point coordinates or accuracy are invalid",
            HttpStatus.UNPROCESSABLE_ENTITY);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    TrackingErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override public String getCode()    { return code; }
    @Override public String getMessage() { return message; }
    public HttpStatus defaultStatus()  { return httpStatus; }
}
