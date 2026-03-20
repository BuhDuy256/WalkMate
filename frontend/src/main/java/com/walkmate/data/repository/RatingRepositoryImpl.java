package com.walkmate.data.repository;

import com.walkmate.data.datasource.remote.api.RatingApiService;
import com.walkmate.data.datasource.remote.dto.RatingResponseDto;
import com.walkmate.data.datasource.remote.dto.SubmitRatingRequestDto;
import com.walkmate.data.mapper.RatingDomainToDtoMapper;
import com.walkmate.domain.rating.Rating;
import com.walkmate.domain.rating.RatingErrorCode;
import com.walkmate.domain.rating.RatingException;
import com.walkmate.domain.rating.RatingRepository;

import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

/**
 * Implementation of RatingRepository
 */
public class RatingRepositoryImpl implements RatingRepository {

    private final RatingApiService apiService;
    private final RatingDomainToDtoMapper mapper;

    public RatingRepositoryImpl(RatingApiService apiService, RatingDomainToDtoMapper mapper) {
        this.apiService = apiService;
        this.mapper = mapper;
    }

    @Override
    public Rating submitRating(Rating rating) throws RatingException {
        SubmitRatingRequestDto requestDto = mapper.mapToDto(rating);

        Call<RatingResponseDto> call = apiService.submitRating(requestDto);

        try {
            Response<RatingResponseDto> response = call.execute();

            if (response.isSuccessful() && response.body() != null) {
                return rating; // Return original rating (could map response if needed)
            } else {
                // Handle HTTP errors
                int code = response.code();
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "";

                RatingErrorCode errorCode = mapHttpErrorToRatingError(code, errorBody);
                throw new RatingException(errorCode, "Failed to submit rating: " + errorBody);
            }
        } catch (IOException e) {
            throw new RatingException(RatingErrorCode.NETWORK_ERROR, "Network error", e);
        }
    }

    private RatingErrorCode mapHttpErrorToRatingError(int httpCode, String errorBody) {
        if (errorBody.contains("RATING_ALREADY_EXISTS")) {
            return RatingErrorCode.RATING_ALREADY_EXISTS;
        } else if (errorBody.contains("RATING_SESSION_NOT_COMPLETED")) {
            return RatingErrorCode.RATING_SESSION_NOT_COMPLETED;
        } else if (errorBody.contains("RATING_SESSION_NOT_FOUND")) {
            return RatingErrorCode.RATING_SESSION_NOT_FOUND;
        } else if (httpCode == 403) {
            return RatingErrorCode.RATING_UNAUTHORIZED;
        } else {
            return RatingErrorCode.UNKNOWN_ERROR;
        }
    }
}
