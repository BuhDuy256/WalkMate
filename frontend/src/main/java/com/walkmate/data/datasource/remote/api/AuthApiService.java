package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.request.user.GoogleLoginRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.LoginRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.LogoutRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.RefreshTokenRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.RegisterRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.SendOtpRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.VerifyOtpRequestDto;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.user.LoginResponseDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApiService {

    @POST("api/v1/auth/register")
    Call<ApiResponse<LoginResponseDto>> register(@Body RegisterRequestDto request);

    @POST("api/v1/auth/login")
    Call<ApiResponse<LoginResponseDto>> login(@Body LoginRequestDto request);

    @POST("api/v1/auth/google")
    Call<ApiResponse<LoginResponseDto>> loginWithGoogle(@Body GoogleLoginRequestDto request);

    @POST("api/v1/auth/refresh")
    Call<ApiResponse<LoginResponseDto>> refreshToken(@Body RefreshTokenRequestDto request);

    @POST("api/v1/auth/logout")
    Call<ApiResponse<Void>> logout(@Body LogoutRequestDto request);

    @POST("api/v1/auth/logout-all")
    Call<ApiResponse<Void>> logoutAll();

    @POST("api/v1/auth/phone/send-otp")
    Call<ApiResponse<Void>> sendOtp(@Body SendOtpRequestDto request);

    @POST("api/v1/auth/phone/verify")
    Call<ApiResponse<LoginResponseDto>> verifyOtp(@Body VerifyOtpRequestDto request);
}
