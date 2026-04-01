package com.walkmate.presentation.dto.response.tracking;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response for {@code POST /api/v1/tracking/sync}.
 *
 * <p>The backend echoes back the {@code localId} values from the request so the
 * Android client can mark exactly those Room rows as synced, even in the case of
 * a partial success in future versions.</p>
 */
public record PushRoutePointsResponse(

        @JsonProperty("acknowledged_ids")
        List<Long> acknowledgedIds
) {}
