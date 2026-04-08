package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.api.WalkIntentApiService;
import com.walkmate.data.datasource.remote.dto.request.walkintent.CreateWalkIntentRequest;
import com.walkmate.core.util.ErrorParser;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.proposal.WalkProposalResponse;
import com.walkmate.data.datasource.remote.dto.response.walkintent.WalkIntentResponse;
import com.walkmate.data.mapper.WalkIntentMapper;
import com.walkmate.data.mapper.WalkProposalMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import com.walkmate.domain.walkproposal.WalkProposal;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class WalkIntentRepositoryImpl implements WalkIntentRepository {

    private static final String TAG = "WalkIntentRepo";

    private final WalkIntentApiService apiService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public WalkIntentRepositoryImpl(Context context) {
        SessionManager sessionManager = new SessionManager(context);
        this.apiService = ApiClient.buildAuthenticatedRetrofit(sessionManager)
                .create(WalkIntentApiService.class);
    }

    // ---------------------------------------------------------------------------
    // Interface methods
    // ---------------------------------------------------------------------------

    @Override
    public void createIntent(String hotspotId, String date, float timeStart, float timeEnd,
                             int ageMin, int ageMax, List<String> tags,
                             boolean isPrivate, String invitedFriendId,
                             String description,
                             DomainCallback<WalkIntent> callback) {
        executor.execute(() -> {
            try {
                CreateWalkIntentRequest request = new CreateWalkIntentRequest(
                        hotspotId, date, timeStart, timeEnd, ageMin, ageMax, tags,
                        isPrivate, invitedFriendId, description);

                Response<ApiResponse<WalkIntentResponse>> resp =
                        apiService.createIntent(request).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(WalkIntentMapper.toDomain(resp.body().getData()));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "INTENT_CREATE_FAILED");
                    if (resp.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "createIntent network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void listActiveIntents(DomainCallback<List<WalkIntent>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<WalkIntentResponse>>> resp =
                        apiService.listActiveIntents().execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    List<WalkIntent> intents = WalkIntentMapper.toDomainList(
                            resp.body().getData() != null ? resp.body().getData() : Collections.emptyList());
                    callback.onSuccess(intents);
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "INTENT_FETCH_FAILED");
                    if (resp.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "listActiveIntents network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void findMatch(String intentId, DomainCallback<WalkProposal> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<WalkProposalResponse>> resp =
                        apiService.findMatch(intentId).execute();

                if (resp.code() == 204) {
                    // No candidate available yet — signal with null
                    callback.onSuccess(null);
                    return;
                }
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(WalkProposalMapper.toDomain(resp.body().getData()));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "MATCH_NOT_FOUND");
                    if (resp.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "findMatch network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void cancelIntent(String intentId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = apiService.cancelIntent(intentId).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(null);
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "INTENT_CANCEL_FAILED");
                    if (resp.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "cancelIntent network error", e);
                callback.onError(e);
            }
        });
    }

}
