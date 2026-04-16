package com.walkmate.domain.tracking;

/**
 * Stateful GPS point filter.
 *
 * Rejects two classes of noise before a fix is persisted:
 *   1. Poor-accuracy fixes (accuracy radius worse than MAX_ACCURACY_METRES).
 *   2. Micro-movement below MIN_DISTANCE_METRES — avoids dozens of nearly
 *      identical points accumulating while the user stands still.
 *
 * Call {@link #reset()} when a walk is paused so that the first fix after
 * resume is always accepted regardless of distance from the last known point.
 *
 * This class is NOT thread-safe. It is owned by SessionTrackingService which
 * serialises all calls through its single-thread executor.
 */
public class LocationFilterPolicy {

    /**
     * Ignore fixes with an accuracy radius larger than this value (metres)
     * once a baseline point has been established.
     */
    private static final float MAX_ACCURACY_METRES = 150.0f;

    /** Ignore fixes that are closer than this distance to the last accepted point (metres). */
    private static final float MIN_DISTANCE_METRES = 1.0f;

    private Double lastLat;
    private Double lastLng;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the fix should be persisted.
     *
     * @param lat      WGS-84 latitude of the new fix
     * @param lng      WGS-84 longitude of the new fix
     * @param accuracy horizontal accuracy radius reported by the sensor (metres)
     */
    public boolean shouldAccept(double lat, double lng, float accuracy) {
        if (lastLat == null) {
            // Always accept the first fix so tracking starts immediately after Start.
            return true;
        }
        if (accuracy > MAX_ACCURACY_METRES) {
            return false;
        }
        return distanceMetres(lastLat, lastLng, lat, lng) >= MIN_DISTANCE_METRES;
    }

    /**
     * Records the accepted fix as the new "last known good" position.
     * Must be called after each point that passes {@link #shouldAccept}.
     */
    public void accept(double lat, double lng) {
        lastLat = lat;
        lastLng = lng;
    }

    /**
     * Clears the last-known position. Call this on walk pause so the next
     * resume fix is not rejected for being "too close".
     */
    public void reset() {
        lastLat = null;
        lastLng = null;
    }

    // ── Haversine distance (pure Java, no Android imports) ────────────────────

    private static float distanceMetres(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6_371_000.0; // Earth radius in metres
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return (float) (R * c);
    }
}
