package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.request.tracking.PushRoutePointsRequest;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.tracking.PushRoutePointsResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Retrofit interface for the legacy GPS batch-sync endpoint.
 *
 * <p><b>Retired for new clients.</b> This app now uses
 * {@link TrackingApiServiceV2}; the v1 contract is kept only so an older
 * installed build keeps working during rollout. It is not referenced by any
 * code path in this module.</p>
 *
 * Contract: POST /api/v1/tracking/sync
 *   Body  → {@link PushRoutePointsRequest}
 *   200   → {@link ApiResponse}<{@link PushRoutePointsResponse}>
 */
public interface RoutePointSyncApiService {

    @POST("api/v1/tracking/sync")
    Call<ApiResponse<PushRoutePointsResponse>> pushRoutePoints(
            @Body PushRoutePointsRequest request);
}
