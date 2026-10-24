package com.walkmate.domain.tracking;

import androidx.lifecycle.LiveData;

import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

/**
 * Repository interface for GPS tracking data.
 *
 * All read/write operations are scoped to the current logged-in user to prevent
 * GPS path data from leaking between accounts on a shared device. The
 * implementation stamps the userId at the point of persistence and filters
 * by it on every read.
 */
public interface TrackingRepository {

    // ── Local (Room) ─────────────────────────────────────────────────────────

    /**
     * Persists a single GPS fix. The implementation automatically stamps the
     * currently logged-in user's ID. Must be called off the main thread.
     */
    void saveRoutePoint(RoutePoint point, DomainCallback<Long> callback);

    /**
     * Reactive read — emits the full ordered point list whenever a new row
     * is inserted for the given session and the currently logged-in user.
     */
    LiveData<List<RoutePoint>> getPointsForSession(String sessionId);

    /**
     * Returns the count of points not yet pushed to the backend.
     * Must be called off the main thread.
     */
    void getUnsyncedCount(String sessionId, DomainCallback<Integer> callback);

    // ── Remote (Backend Push) ─────────────────────────────────────────────────

    /**
     * Pushes a batch of unsynced points to the backend.
     * Must be called off the main thread.
     */
    void pushRoutePoints(String sessionId, List<RoutePoint> points, DomainCallback<Void> callback);

    /**
     * Marks the given point IDs as synced in the local DB after a successful push.
     *
     * @deprecated v2 marks points synced only through a verified batch ACK, which
     *     the queue drainer performs. Marking an arbitrary id list would defeat the
     *     lost-ACK guarantee. Implementations must treat this as a no-op.
     */
    @Deprecated
    void markPointsSynced(List<Long> ids, DomainCallback<Void> callback);

    /** Triggered by the 30-second periodic scheduler. Seals the tail and drains. */
    void triggerPeriodicSync(String sessionId);

    /**
     * Fetches partner GPS chunks incrementally from the backend.
     *
     * <p>Pass {@code afterChunkIndex = -1} on first call to receive all available chunks.
     * Subsequent calls should pass the highest {@code chunkIndex} received so far.
     *
     * <p>The callback is invoked with a {@link PartnerPathResult} whose
     * {@code newPoints} list contains decoded {@link com.google.android.gms.maps.model.LatLng}
     * values from the newly received polyline strings. If the partner has no data yet,
     * {@code newPoints} is empty and {@code partnerStatus} is {@code "PENDING"}.
     */
    void fetchPartnerPath(String sessionId, int afterChunkIndex,
                          DomainCallback<PartnerPathResult> callback);

    /**
     * Deletes all GPS points recorded by a specific user. Call this on logout
     * to prevent the previous user's path data from being visible to the next
     * user on the same device.
     */
    void clearForUser(String userId, DomainCallback<Void> callback);

    /**
     * Force-flushes all remaining unsynced Room points for the given session.
     *
     * <p>Must be called just before {@code sessionRepository.completeSession()} so
     * that no GPS data is stranded in the local DB after the backend marks the
     * session COMPLETED (which would reject any future sync calls for that session).
     *
     * <p>Bypasses the {@code syncInFlight} guard — the GPS service is already
     * stopped at this point, so no new points can arrive. The implementation
     * queues on the same single-thread executor, which guarantees any in-flight
     * periodic sync finishes before the flush begins.
     *
     * <p>On HTTP failure the caller should still proceed with completion rather
     * than leaving the user stranded on the FINISHING screen.
     */
    void flushUnsyncedBeforeComplete(String sessionId, DomainCallback<Void> callback);

    /**
     * Declares the client's final sequence to the server
     * ({@code POST /api/v2/tracking/streams/{id}/seal}) and reports the resulting
     * sync state: {@code SEALED}, {@code COMPLETE} or {@code EXPIRED}.
     *
     * <p>Must be called after {@link #flushUnsyncedBeforeComplete}, because the
     * final sequence is only known once the local write queue has drained.</p>
     *
     * <p>Retries are safe and idempotent: re-sealing with the same or a higher
     * final sequence is accepted, a lower one is refused by the server.</p>
     */
    void sealStream(String sessionId, DomainCallback<String> callback);

    /**
     * Reads the locally recorded sync state for this session
     * ({@link com.walkmate.domain.tracking.GpsSyncState}). Null until the first
     * point is accepted.
     */
    void getSyncState(String sessionId, DomainCallback<String> callback);
}
