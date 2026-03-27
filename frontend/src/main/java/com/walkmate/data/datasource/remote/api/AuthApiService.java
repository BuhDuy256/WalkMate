package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.request.user.LoginRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.RegisterRequestDto;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.user.LoginResponseDto;
import com.walkmate.data.datasource.remote.dto.response.user.RegisterResponseDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApiService {

    @POST("api/v1/auth/register")
    Call<ApiResponse<RegisterResponseDto>> register(@Body RegisterRequestDto request);

    @POST("api/v1/auth/login")
    Call<ApiResponse<LoginResponseDto>> login(@Body LoginRequestDto request);
}
