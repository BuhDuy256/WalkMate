package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.RatingResponseDto;
import com.walkmate.data.datasource.remote.dto.SubmitRatingRequestDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;


public interface RatingApiService {

    @POST("/api/ratings")
    Call<RatingResponseDto> submitRating(@Body SubmitRatingRequestDto request);
}
