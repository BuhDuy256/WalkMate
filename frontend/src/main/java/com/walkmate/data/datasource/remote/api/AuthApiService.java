package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.ApiResponse;
import com.walkmate.data.datasource.remote.dto.LoginRequestDto;
import com.walkmate.data.datasource.remote.dto.LoginResponseDto;
import com.walkmate.data.datasource.remote.dto.RegisterRequestDto;
import com.walkmate.data.datasource.remote.dto.RegisterResponseDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApiService {

    @POST("api/v1/auth/register")
    Call<ApiResponse<RegisterResponseDto>> register(@Body RegisterRequestDto request);

    @POST("api/v1/auth/login")
    Call<ApiResponse<LoginResponseDto>> login(@Body LoginRequestDto request);
}
