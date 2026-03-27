package com.walkmate.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("api/v1/auth/register")
    Call<ResponseBody> register(@Body RegisterRequest request);
}