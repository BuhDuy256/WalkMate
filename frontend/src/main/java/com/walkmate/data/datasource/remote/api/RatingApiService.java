package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.RatingResponseDto;
import com.walkmate.data.datasource.remote.dto.SubmitRatingRequestDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Retrofit API interface for Rating
 */
public interface RatingApiService {

    @POST("/api/v1/ratings")
    Call<RatingResponseDto> submitRating(@Body SubmitRatingRequestDto request);
}
