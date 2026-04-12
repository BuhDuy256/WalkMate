package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.request.user.SetVisibilityRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.UpdateFcmTokenRequestDto;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.user.SetVisibilityResponseDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.PATCH;

/**
 * Retrofit interface for user account management endpoints (authenticated).
 * Distinct from AuthApiService (public/unauthenticated) and UserProfileApiService (profile data).
 */
public interface UserApiService {

    @PATCH("api/v1/users/me/fcm-token")
    Call<ApiResponse<Void>> updateFcmToken(@Body UpdateFcmTokenRequestDto request);

    @PATCH("api/v1/users/me/visibility")
    Call<ApiResponse<SetVisibilityResponseDto>> setVisibility(@Body SetVisibilityRequestDto request);
}
