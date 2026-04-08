package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.request.walkintent.CreateWalkIntentRequest;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.proposal.WalkProposalResponse;
import com.walkmate.data.datasource.remote.dto.response.walkintent.WalkIntentResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface WalkIntentApiService {

    @POST("api/v1/intents")
    Call<ApiResponse<WalkIntentResponse>> createIntent(@Body CreateWalkIntentRequest request);

    @GET("api/v1/intents")
    Call<ApiResponse<List<WalkIntentResponse>>> listActiveIntents();

    @POST("api/v1/intents/{intentId}/match")
    Call<ApiResponse<WalkProposalResponse>> findMatch(@Path("intentId") String intentId);

    @DELETE("api/v1/intents/{intentId}")
    Call<ApiResponse<Void>> cancelIntent(@Path("intentId") String intentId);
}
