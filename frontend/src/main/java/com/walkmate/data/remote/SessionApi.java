package com.walkmate.data.remote;

import com.walkmate.data.remote.dto.ApiResponseDto;
import com.walkmate.data.remote.dto.AppendSessionPointsRequestDto;
import com.walkmate.data.remote.dto.CompleteSessionRequestDto;
import com.walkmate.data.remote.dto.SessionResponseDto;
import com.walkmate.data.remote.dto.SessionTrackingResponseDto;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SessionApi {

    @POST("api/v1/sessions/{id}/activate")
    Call<ApiResponseDto<SessionResponseDto>> activate(
            @Path("id") String sessionId,
            @Header("X-User-Id") String userIdHeader);

    @POST("api/v1/sessions/{id}/cancel")
    Call<ApiResponseDto<SessionResponseDto>> cancel(
            @Path("id") String sessionId,
            @Body Map<String, String> body,
            @Header("X-User-Id") String userIdHeader);

    @POST("api/v1/sessions/{id}/abort")
    Call<ApiResponseDto<SessionResponseDto>> abort(
            @Path("id") String sessionId,
            @Body Map<String, String> body,
            @Header("X-User-Id") String userIdHeader);

    @POST("api/v1/sessions/{id}/complete")
    Call<ApiResponseDto<SessionResponseDto>> complete(
            @Path("id") String sessionId,
            @Body CompleteSessionRequestDto body,
            @Header("X-User-Id") String userIdHeader);

    @POST("api/v1/sessions/{id}/points:append")
    Call<ApiResponseDto<SessionTrackingResponseDto>> appendPoints(
            @Path("id") String sessionId,
            @Body AppendSessionPointsRequestDto body,
            @Header("X-User-Id") String userIdHeader);

    @GET("api/v1/sessions/{id}")
    Call<ApiResponseDto<SessionResponseDto>> getById(@Path("id") String sessionId);
}
