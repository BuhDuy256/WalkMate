package com.walkmate.presentation.dto.response.tracking;

/**
 * v2 seal ACK: the authoritative deadline the server computed, so the
 * client can schedule its last-drain attempt without doing its own
 * arithmetic.
 */
public record SealStreamResponse(
        String streamId,
        String syncState,        // SEALED (or COMPLETE/EXPIRED after evaluation)
        Long finalSequence,
        String syncDeadlineAt    // ISO-8601
) {}
