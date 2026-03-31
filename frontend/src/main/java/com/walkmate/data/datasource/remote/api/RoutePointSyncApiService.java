package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.request.tracking.PushRoutePointsRequest;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.tracking.PushRoutePointsResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Retrofit interface for the GPS batch-sync endpoint.
 *
 * Contract: POST /api/v1/tracking/sync
 *   Body  → {@link PushRoutePointsRequest}
 *   200   → {@link ApiResponse}<{@link PushRoutePointsResponse}>
 *
 * Currently consumed by the MOCK implementation in TrackingRepositoryImpl.
 * When the backend is ready, replace the mock executor call with:
 *   Retrofit retrofit = ApiClient.buildAuthenticatedRetrofit(sessionManager);
 *   RoutePointSyncApiService api = retrofit.create(RoutePointSyncApiService.class);
 *   api.pushRoutePoints(request).enqueue(...);
 */
public interface RoutePointSyncApiService {

    @POST("api/v1/tracking/sync")
    Call<ApiResponse<PushRoutePointsResponse>> pushRoutePoints(
            @Body PushRoutePointsRequest request);
}
