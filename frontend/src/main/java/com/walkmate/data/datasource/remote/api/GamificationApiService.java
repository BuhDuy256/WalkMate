package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.gamification.BadgeCatalogResponse;
import com.walkmate.data.datasource.remote.dto.response.gamification.BadgeResponse;
import com.walkmate.data.datasource.remote.dto.response.gamification.UserStatsResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface GamificationApiService {

    @GET("api/v1/users/{userId}/badges")
    Call<ApiResponse<List<BadgeResponse>>> getBadges(@Path("userId") String userId);

    @GET("api/v1/badges/catalog")
    Call<ApiResponse<List<BadgeCatalogResponse>>> getBadgeCatalog();

    @GET("api/v1/users/{userId}/stats")
    Call<ApiResponse<UserStatsResponse>> getStats(@Path("userId") String userId);

}
