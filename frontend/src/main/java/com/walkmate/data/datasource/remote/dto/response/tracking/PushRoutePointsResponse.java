package com.walkmate.data.datasource.remote.dto.response.tracking;

/**
 * Response payload for POST /api/v1/tracking/sync.
 * Wrapped in {@link com.walkmate.data.datasource.remote.dto.response.ApiResponse}.
 */
public class PushRoutePointsResponse {

    /** Number of points the server successfully persisted. */
    private int syncedCount;

    public int getSyncedCount() { return syncedCount; }
}
