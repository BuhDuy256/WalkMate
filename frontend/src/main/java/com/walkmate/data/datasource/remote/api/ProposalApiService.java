package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.proposal.WalkProposalResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ProposalApiService {

    @GET("api/v1/proposals")
    Call<ApiResponse<List<WalkProposalResponse>>> getProposals();

    @POST("api/v1/proposals/{proposalId}/accept")
    Call<ApiResponse<WalkProposalResponse>> acceptProposal(@Path("proposalId") String proposalId);

    @POST("api/v1/proposals/{proposalId}/pass")
    Call<ApiResponse<Void>> passProposal(@Path("proposalId") String proposalId);

    @DELETE("api/v1/proposals/{proposalId}")
    Call<ApiResponse<Void>> cancelProposal(@Path("proposalId") String proposalId);
}
