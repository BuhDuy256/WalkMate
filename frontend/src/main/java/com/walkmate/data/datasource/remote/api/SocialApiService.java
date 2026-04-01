package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.social.UserSummaryResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SocialApiService {

    // ── Follow ────────────────────────────────────────────────────────────────

    @POST("api/v1/users/{userId}/follow")
    Call<ApiResponse<Void>> follow(@Path("userId") String userId);

    @DELETE("api/v1/users/{userId}/follow")
    Call<ApiResponse<Void>> unfollow(@Path("userId") String userId);

    @GET("api/v1/users/{userId}/followers")
    Call<ApiResponse<List<UserSummaryResponse>>> getFollowers(@Path("userId") String userId);

    @GET("api/v1/users/{userId}/following")
    Call<ApiResponse<List<UserSummaryResponse>>> getFollowing(@Path("userId") String userId);

    // ── Block ─────────────────────────────────────────────────────────────────

    @POST("api/v1/users/{userId}/block")
    Call<ApiResponse<Void>> block(@Path("userId") String userId);

    @DELETE("api/v1/users/{userId}/block")
    Call<ApiResponse<Void>> unblock(@Path("userId") String userId);
}
