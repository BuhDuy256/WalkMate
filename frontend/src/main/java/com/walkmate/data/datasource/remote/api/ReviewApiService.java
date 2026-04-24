package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.request.review.SubmitReviewRequest;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.review.ReviewResponse;
import com.walkmate.data.datasource.remote.dto.response.review.ReviewTagResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ReviewApiService {

    @POST("api/v1/sessions/{sessionId}/review")
    Call<ApiResponse<ReviewResponse>> submitReview(
            @Path("sessionId") String sessionId,
            @Body SubmitReviewRequest body);

    @GET("api/v1/users/{userId}/reviews")
    Call<ApiResponse<List<ReviewResponse>>> getReviewsForUser(
            @Path("userId") String userId);

    @GET("api/v1/reviews/tags")
    Call<ApiResponse<List<ReviewTagResponse>>> getReviewTags();
}
