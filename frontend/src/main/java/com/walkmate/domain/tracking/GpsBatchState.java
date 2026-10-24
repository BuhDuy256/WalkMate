package com.walkmate.domain.tracking;

/**
 * Lifecycle of one upload batch, mirrored in {@code gps_batches.state}.
 *
 * <p>This is the <em>upload</em> state machine and is deliberately independent
 * of the walk business state: a walk can be {@code COMPLETED} while its batches
 * are still {@code PENDING}.</p>
 *
 * <pre>
 *   PENDING ──claim──▶ IN_FLIGHT ──ACK──▶ ACKED
 *      ▲                   │
 *      └──backoff/expired lease──┘
 *      └──409/422/413──▶ BLOCKED   (kept for diagnosis, never deleted silently)
 * </pre>
 */
public final class GpsBatchState {

    /** Sealed locally, waiting for its next upload attempt. */
    public static final String PENDING = "PENDING";

    /** Leased by a worker; if the process dies the lease expires and it is re-claimable. */
    public static final String IN_FLIGHT = "IN_FLIGHT";

    /** The server ACKed this exact batch id + payload; its points are marked synced. */
    public static final String ACKED = "ACKED";

    /** The server permanently refused this batch; needs diagnosis, not retries. */
    public static final String BLOCKED = "BLOCKED";

    private GpsBatchState() {}
}
