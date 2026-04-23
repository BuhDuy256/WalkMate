package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.request.walksession.CancelWalkSessionRequest;
import com.walkmate.data.datasource.remote.dto.request.walksession.ReportSessionRequest;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.session.SessionRouteResponse;
import com.walkmate.data.datasource.remote.dto.response.session.SessionSummaryResponse;
import com.walkmate.data.datasource.remote.dto.response.session.WalkSessionResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SessionApiService {

    @GET("api/v1/sessions/active")
    Call<ApiResponse<List<WalkSessionResponse>>> getActiveSessions();

    @POST("api/v1/sessions/{sessionId}/activate")
    Call<ApiResponse<WalkSessionResponse>> activateSession(@Path("sessionId") String sessionId);

    @POST("api/v1/sessions/{sessionId}/cancel")
    Call<ApiResponse<Void>> cancelSession(@Path("sessionId") String sessionId,
                                          @Body CancelWalkSessionRequest body);

    // UC-19 — Complete an active walk session
    @POST("api/v1/sessions/{sessionId}/complete")
    Call<ApiResponse<WalkSessionResponse>> completeSession(
            @Path("sessionId") String sessionId);

    // UC-22 — Fetch terminal session history list
    @GET("api/v1/sessions/history")
    Call<ApiResponse<List<SessionSummaryResponse>>> getSessionHistory();

    // UC-23 — Fetch GPS route for a completed session
    @GET("api/v1/sessions/{sessionId}/route")
    Call<ApiResponse<SessionRouteResponse>> getSessionRoute(
            @Path("sessionId") String sessionId);

    // UC-25 — Submit an incident report
    @POST("api/v1/sessions/{sessionId}/report")
    Call<ApiResponse<Void>> reportSession(
            @Path("sessionId") String sessionId,
            @Body ReportSessionRequest body);
}
