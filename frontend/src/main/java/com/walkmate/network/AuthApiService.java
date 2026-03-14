package com.walkmate.network;

import com.walkmate.ui.register.RegisterRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApiService {

    @POST("api/v1/auth/register")
    Call<ApiResponse> register(@Body RegisterRequest request);
}