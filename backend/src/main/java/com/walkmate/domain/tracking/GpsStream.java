package com.walkmate.domain.tracking;

import com.walkmate.domain.tracking.TrackingStreamSyncState;

import java.time.Instant;

/**
 * One GPS upload stream — one per {@code (walk session, user)} in the v2
 * contract. The mobile client mints the {@code streamId} UUID and reuses it
 * across process restarts for the same walk, so the server treats a
 * different stream id for the same (session, user) as a conflict (possible
 * multi-device write).
 *
 * <p>This is a snapshot read outside a write transaction; mutations happen
 * only inside stream write transactions (see
 * {@link TrackingStreamRepository#allocateArrivalIndex}).</p>
 */
public record GpsStream(
        String streamId,
        String sessionId,
        String userId,
        Long expectedFinalSequence,
        Instant recordedEndedAt,
        Instant uploadDeadlineAt,
        TrackingStreamSyncState syncState,
        int nextArrivalIndex
) {}
