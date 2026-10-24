package com.walkmate.presentation.dto.request.tracking;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * v2 stream seal request: the client declares its final sequence number
 * (and the recorded end time, when it has one). Retries with the same
 * values are idempotent; a retry with a higher final sequence extends the
 * stream's range.
 */
public record SealStreamRequest(

        @NotNull
        @Positive
        Long finalSequence,

        /**
         * Epoch milliseconds the recorded walk actually ended. May be
         * null — the server keeps the previously-sealed value when it is.
         */
        Long recordedEndedAt
) {}
