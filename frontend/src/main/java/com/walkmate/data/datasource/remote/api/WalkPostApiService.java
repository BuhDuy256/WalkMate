package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.request.walkpost.CreateWalkPostRequest;
import com.walkmate.data.datasource.remote.dto.request.walkpost.UpdateWalkPostVisibilityRequest;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.walkpost.WalkPostResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface WalkPostApiService {

    @POST("api/v1/sessions/{sessionId}/posts")
    Call<ApiResponse<WalkPostResponse>> createPost(
            @Path("sessionId") String sessionId,
            @Body CreateWalkPostRequest body);

    @GET("api/v1/profiles/me/posts")
    Call<ApiResponse<List<WalkPostResponse>>> getMyPosts();

    @GET("api/v1/profiles/{userId}/posts")
    Call<ApiResponse<List<WalkPostResponse>>> getUserPosts(@Path("userId") String userId);

    @PATCH("api/v1/walk-posts/{postId}/visibility")
    Call<ApiResponse<WalkPostResponse>> updateVisibility(
            @Path("postId") String postId,
            @Body UpdateWalkPostVisibilityRequest body);

    @DELETE("api/v1/walk-posts/{postId}")
    Call<ApiResponse<Void>> deletePost(@Path("postId") String postId);
}
