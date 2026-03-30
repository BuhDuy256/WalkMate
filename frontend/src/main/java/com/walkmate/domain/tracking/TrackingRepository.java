package com.walkmate.domain.tracking;

import androidx.lifecycle.LiveData;

import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

/**
 * Repository interface for GPS tracking data.
 *
 * Write operations (saveRoutePoint, pushRoutePoints, markPointsSynced) are
 * one-shot and report results via DomainCallback on the calling thread.
 *
 * The read operation (getPointsForSession) returns a Room-backed LiveData that
 * fires on the main thread whenever a new row is inserted — the ViewModel
 * observes this to keep the map polyline current.
 */
public interface TrackingRepository {

    // ── Local (Room) ─────────────────────────────────────────────────────────

    /**
     * Persists a single GPS fix. Must be called off the main thread.
     * The assigned DB row ID is returned in {@code callback.onSuccess(id)}.
     */
    void saveRoutePoint(RoutePoint point, DomainCallback<Long> callback);

    /**
     * Reactive read — emits the full ordered point list whenever a new row
     * is inserted for the given session.
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
}
