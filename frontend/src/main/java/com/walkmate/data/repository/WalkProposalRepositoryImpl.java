package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.ProposalApiService;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.proposal.WalkProposalResponse;
import com.walkmate.data.mapper.WalkProposalMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkproposal.WalkProposal;
import com.walkmate.domain.walkproposal.WalkProposalRepository;
import com.walkmate.domain.walksession.WalkSession;

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
                    callback.onError(new Exception(extractErrorCode(resp.body(), "PROPOSALS_FETCH_FAILED")));
                }
            } catch (IOException e) {
                Log.e(TAG, "getProposals network error", e);
                callback.onError(e);
            }
        });
    }

    /**
     * Accepts a proposal.
     * - If both users have now accepted (status == CONFIRMED), calls onSuccess with the new WalkSession.
     * - If only this user has accepted (status == PENDING), calls onSuccess(null) — the caller
     *   should interpret null as "waiting for the other participant".
     */
    @Override
    public void acceptProposal(String proposalId, DomainCallback<WalkSession> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<WalkProposalResponse>> resp =
                        apiService.acceptProposal(proposalId).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    WalkProposalResponse proposal = resp.body().getData();
                    if (proposal.getSessionId() != null) {
                        callback.onSuccess(WalkProposalMapper.toSession(proposal));
                    } else {
                        // Partial accept — waiting for the other participant
                        callback.onSuccess(null);
                    }
                } else {
                    callback.onError(new Exception(extractErrorCode(resp.body(), "PROPOSAL_ACCEPT_FAILED")));
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
                    callback.onError(new Exception(extractErrorCode(resp.body(), "PROPOSAL_PASS_FAILED")));
                }
            } catch (IOException e) {
                Log.e(TAG, "passProposal network error", e);
                callback.onError(e);
            }
        });
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private <T> String extractErrorCode(ApiResponse<T> body, String fallback) {
        if (body != null && body.getError() != null && body.getError().getCode() != null) {
            return body.getError().getCode();
        }
        return fallback;
    }
}
