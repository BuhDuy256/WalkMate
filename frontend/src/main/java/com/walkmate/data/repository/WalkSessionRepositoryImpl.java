package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.SessionApiService;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.dto.request.walksession.CancelWalkSessionRequest;
import com.walkmate.data.datasource.remote.dto.request.walksession.ReportSessionRequest;
import com.walkmate.core.util.ErrorParser;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.session.SessionRouteResponse;
import com.walkmate.data.datasource.remote.dto.response.session.WalkSessionResponse;
import com.walkmate.data.mapper.SessionRouteMapper;
import com.walkmate.data.mapper.SessionSummaryMapper;
import com.walkmate.data.mapper.WalkSessionMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walksession.SessionRoute;
import com.walkmate.domain.walksession.SessionSummary;
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
        this.apiService = ApiClient.buildAuthenticatedRetrofit(sessionManager, ApiClient.getAuthApiService())
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
                    if ("VALIDATION_ERROR".equals(apiError.getCode())) {
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
                    if ("VALIDATION_ERROR".equals(apiError.getCode())) {
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
                    if ("VALIDATION_ERROR".equals(apiError.getCode())) {
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
    public void completeSession(String sessionId, DomainCallback<WalkSession> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<WalkSessionResponse>> resp =
                        apiService.completeSession(sessionId).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    WalkSessionResponse data = resp.body().getData();
                    String callerId = sessionManager.getUserId();
                    callback.onSuccess(WalkSessionMapper.toDomain(data, callerId));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "SESSION_COMPLETE_FAILED");
                    if ("VALIDATION_ERROR".equals(apiError.getCode())) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "completeSession network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void getSessionHistory(DomainCallback<List<SessionSummary>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<WalkSessionResponse>>> resp =
                        apiService.getSessionHistory().execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    List<WalkSessionResponse> data = resp.body().getData();
                    String callerId = sessionManager.getUserId();
                    callback.onSuccess(SessionSummaryMapper.toDomainList(
                            data != null ? data : Collections.emptyList(), callerId));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "SESSION_HISTORY_FAILED");
                    if ("VALIDATION_ERROR".equals(apiError.getCode())) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "getSessionHistory network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void getSessionRoute(String sessionId, DomainCallback<SessionRoute> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<SessionRouteResponse>> resp =
                        apiService.getSessionRoute(sessionId).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(SessionRouteMapper.toDomain(resp.body().getData()));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "SESSION_ROUTE_FAILED");
                    if ("VALIDATION_ERROR".equals(apiError.getCode())) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "getSessionRoute network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void reportSession(String sessionId, String reportedUserId,
                              String reason, String evidenceUrl,
                              DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp =
                        apiService.reportSession(sessionId,
                                new ReportSessionRequest(reportedUserId, reason, evidenceUrl)).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(null);
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "SESSION_REPORT_FAILED");
                    if ("VALIDATION_ERROR".equals(apiError.getCode())) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "reportSession network error", e);
                callback.onError(e);
            }
        });
    }

}
