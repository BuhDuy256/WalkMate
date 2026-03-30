package com.walkmate.domain.tracking;

import android.util.Log;

import com.walkmate.domain.shared.DomainCallback;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Domain-layer service that owns the GPS point processing pipeline for a
 * single walk session.
 *
 * Responsibilities:
 *   1. Receive raw GPS fixes from the Android foreground service on the main thread.
 *   2. Hand them off to a dedicated background executor to avoid blocking the UI.
 *   3. Run each fix through {@link LocationFilterPolicy} to eliminate noise.
 *   4. Persist accepted fixes via {@link TrackingRepository}.
 *
 * This class has NO Android framework imports — it is a pure Java domain service.
 * Android-specific code (FusedLocationProviderClient, Notification) lives in
 * {@code WalkTrackerService}.
 *
 * ── PHASE 2 FIX: stopTracking() / executor lifecycle ─────────────────────────
 * The executor is shut down gracefully in {@link #stopTracking()} so that any
 * in-flight Room write completes before the thread pool is destroyed. Failing to
 * do this was the source of the thread-leak identified in the architecture review.
 */
public class SessionTrackingService {

    private static final String TAG = "SessionTrackingSvc";

    private final TrackingRepository repository;
    private final LocationFilterPolicy filterPolicy;

    /**
     * Single background thread for all filter + save operations.
     * Sequential execution keeps Room writes ordered by arrival time.
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile String currentSessionId;

    public SessionTrackingService(TrackingRepository repository) {
        this.repository = repository;
        this.filterPolicy = new LocationFilterPolicy();
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    /**
     * Starts recording for the given session.
     * Safe to call from any thread.
     */
    public void startSession(String sessionId) {
        currentSessionId = sessionId;
        executor.execute(filterPolicy::reset);
        Log.d(TAG, "Session started: " + sessionId);
    }

    /**
     * Pauses recording: resets the filter so the first fix after resume is
     * not rejected for being "too close" to the last accepted point.
     * Safe to call from any thread.
     */
    public void pauseSession() {
        executor.execute(filterPolicy::reset);
        Log.d(TAG, "Session paused — filter state cleared");
    }

    // ── GPS point intake ──────────────────────────────────────────────────────

    /**
     * Called by the Android foreground service on the main thread for every
     * new location fix. Processing is immediately handed off to the executor.
     *
     * @param lat      WGS-84 latitude
     * @param lng      WGS-84 longitude
     * @param accuracy horizontal accuracy radius in metres
     */
    public void onLocationReceived(double lat, double lng, float accuracy) {
        final String sessionId = currentSessionId;
        if (sessionId == null) {
            return; // Service not started or already stopped
        }

        executor.execute(() -> processPoint(sessionId, lat, lng, accuracy));
    }

    // ── PHASE 2 FIX ──────────────────────────────────────────────────────────

    /**
     * Gracefully shuts down the executor.
     *
     * <p>Calls {@link ExecutorService#shutdown()} which allows all already-
     * submitted tasks (pending Room writes) to complete before the thread
     * terminates, preventing data loss. New tasks submitted after this call
     * are rejected silently.
     *
     * <p>Called from {@code WalkTrackerService.onDestroy()} to ensure the
     * service thread is cleaned up when the OS kills the foreground service.
     */
    public void stopTracking() {
        currentSessionId = null;
        executor.shutdown(); // graceful — finishes in-flight writes before terminating
        Log.d(TAG, "Executor shut down — tracking stopped");
    }

    // ── Internal processing ───────────────────────────────────────────────────

    private void processPoint(String sessionId, double lat, double lng, float accuracy) {
        if (!filterPolicy.shouldAccept(lat, lng, accuracy)) {
            Log.v(TAG, "Point filtered out — accuracy=" + accuracy + "m, lat=" + lat + " lng=" + lng);
            return;
        }

        RoutePoint point = new RoutePoint(
                0L,                         // id = 0; Room assigns the real ID on insert
                sessionId,
                lat,
                lng,
                System.currentTimeMillis(),
                accuracy,
                false                       // isSynced = false; repository handles sync
        );

        repository.saveRoutePoint(point, new DomainCallback<Long>() {
            @Override
            public void onSuccess(Long assignedId) {
                filterPolicy.accept(lat, lng);
                Log.v(TAG, "Point saved — id=" + assignedId
                        + ", lat=" + lat + ", lng=" + lng);
            }

            @Override
            public void onError(Exception error) {
                // Point was not saved; do NOT call filterPolicy.accept() so the
                // next fix that passes the distance check gets another chance.
                Log.e(TAG, "Failed to save point: " + error.getMessage());
            }
        });
    }
}
