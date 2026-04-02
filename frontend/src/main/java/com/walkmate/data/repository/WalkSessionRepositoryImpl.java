package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.SessionApiService;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.dto.request.walksession.AbortWalkSessionRequest;
import com.walkmate.data.datasource.remote.dto.request.walksession.CancelWalkSessionRequest;
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
                    callback.onError(new Exception(extractErrorCode(resp.body(), "SESSIONS_FETCH_FAILED")));
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
                    callback.onError(new Exception(extractErrorCode(resp.body(), "SESSION_ACTIVATE_FAILED")));
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
                    callback.onError(new Exception(extractErrorCode(resp.body(), "SESSION_CANCEL_FAILED")));
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
                    callback.onError(new Exception(extractErrorCode(resp.body(), "SESSION_ABORT_FAILED")));
                }
            } catch (IOException e) {
                Log.e(TAG, "abortSession network error", e);
                callback.onError(e);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private <T> String extractErrorCode(ApiResponse<T> body, String fallback) {
        if (body != null && body.getError() != null && body.getError().getCode() != null) {
            return body.getError().getCode();
        }
        return fallback;
    }
}
