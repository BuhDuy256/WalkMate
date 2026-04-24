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
     * Must be called off the main thread.
     */
    void markPointsSynced(List<Long> ids, DomainCallback<Void> callback);

    /** Triggered by the 30-second periodic scheduler. Syncs all unsynced points. */
    void triggerPeriodicSync(String sessionId);

    /**
     * Deletes all GPS points recorded by a specific user. Call this on logout
     * to prevent the previous user's path data from being visible to the next
     * user on the same device.
     */
    void clearForUser(String userId, DomainCallback<Void> callback);
}
