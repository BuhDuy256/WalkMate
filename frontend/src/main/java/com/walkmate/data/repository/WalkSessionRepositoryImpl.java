package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.SessionApiService;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.dto.request.walksession.AbortWalkSessionRequest;
import com.walkmate.data.datasource.remote.dto.request.walksession.CancelWalkSessionRequest;
import com.walkmate.core.util.ErrorParser;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.session.WalkSessionResponse;
import com.walkmate.data.mapper.WalkSessionMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.domain.walksession.WalkSessionRepository;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class WalkSessionRepositoryImpl implements WalkSessionRepository {

    private static final String TAG = "WalkSessionRepo";

    private final SessionApiService apiService;
    private final SessionManager    sessionManager;
    private final ExecutorService   executor = Executors.newCachedThreadPool();

    public WalkSessionRepositoryImpl(Context context) {
        this.sessionManager = new SessionManager(context);
        this.apiService = ApiClient.buildAuthenticatedRetrofit(sessionManager)
                .create(SessionApiService.class);
    }

    // ── Interface methods ─────────────────────────────────────────────────────

    @Override
    public void getActiveSessions(DomainCallback<List<WalkSession>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<WalkSessionResponse>>> resp =
                        apiService.getActiveSessions().execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    List<WalkSessionResponse> data = resp.body().getData();
                    String callerId = sessionManager.getUserId();
                    callback.onSuccess(WalkSessionMapper.toDomainList(
                            data != null ? data : Collections.emptyList(), callerId));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "SESSIONS_FETCH_FAILED");
                    if (resp.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "getActiveSessions network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void activateSession(String sessionId, DomainCallback<WalkSession> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<WalkSessionResponse>> resp =
                        apiService.activateSession(sessionId).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    WalkSessionResponse data = resp.body().getData();
                    String callerId = sessionManager.getUserId();
                    callback.onSuccess(WalkSessionMapper.toDomain(data, callerId));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "SESSION_ACTIVATE_FAILED");
                    if (resp.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "activateSession network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void cancelSession(String sessionId, String reason, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp =
                        apiService.cancelSession(sessionId, new CancelWalkSessionRequest(reason)).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(null);
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "SESSION_CANCEL_FAILED");
                    if (resp.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "cancelSession network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void abortSession(String sessionId, String reason, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp =
                        apiService.abortSession(sessionId, new AbortWalkSessionRequest(reason)).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(null);
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "SESSION_ABORT_FAILED");
                    if (resp.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "abortSession network error", e);
                callback.onError(e);
            }
        });
    }

}
