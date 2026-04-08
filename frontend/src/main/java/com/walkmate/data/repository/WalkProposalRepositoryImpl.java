package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.ProposalApiService;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.core.util.ErrorParser;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.proposal.WalkProposalResponse;
import com.walkmate.data.mapper.WalkProposalMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkproposal.WalkProposal;
import com.walkmate.domain.walkproposal.WalkProposalRepository;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class WalkProposalRepositoryImpl implements WalkProposalRepository {

    private static final String TAG = "WalkProposalRepo";

    private final ProposalApiService apiService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public WalkProposalRepositoryImpl(Context context) {
        SessionManager sessionManager = new SessionManager(context);
        this.apiService = ApiClient.buildAuthenticatedRetrofit(sessionManager)
                .create(ProposalApiService.class);
    }

    // ---------------------------------------------------------------------------
    // Interface methods
    // ---------------------------------------------------------------------------

    @Override
    public void getProposals(DomainCallback<List<WalkProposal>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<WalkProposalResponse>>> resp =
                        apiService.getProposals().execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    List<WalkProposalResponse> data = resp.body().getData();
                    callback.onSuccess(WalkProposalMapper.toDomainList(
                            data != null ? data : Collections.emptyList()));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "PROPOSALS_FETCH_FAILED");
                    if (resp.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "getProposals network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void acceptProposal(String proposalId, DomainCallback<WalkProposal> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<WalkProposalResponse>> resp =
                        apiService.acceptProposal(proposalId).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(WalkProposalMapper.toDomain(resp.body().getData()));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "PROPOSAL_ACCEPT_FAILED");
                    if (resp.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "acceptProposal network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void passProposal(String proposalId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = apiService.passProposal(proposalId).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(null);
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "PROPOSAL_PASS_FAILED");
                    if (resp.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "passProposal network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void cancelProposal(String proposalId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = apiService.cancelProposal(proposalId).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(null);
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "PROPOSAL_CANCEL_FAILED");
                    if (resp.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "cancelProposal network error", e);
                callback.onError(e);
            }
        });
    }

}
