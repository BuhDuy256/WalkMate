package com.walkmate.data.datasource.remote.dto.response.tracking;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response payload for POST /api/v1/tracking/sync.
 * Wrapped in {@link com.walkmate.data.datasource.remote.dto.response.ApiResponse}.
 */
public class PushRoutePointsResponse {

    @SerializedName("acknowledged_ids")
    private List<Long> acknowledgedIds;

    public List<Long> getAcknowledgedIds() { return acknowledgedIds; }
}
