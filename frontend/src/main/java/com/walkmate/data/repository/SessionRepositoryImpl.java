package com.walkmate.data.repository;

import androidx.lifecycle.LiveData;

import com.walkmate.core.Result;
import com.walkmate.core.ResultCallback;
import com.walkmate.data.local.dao.SessionLocalDao;
import com.walkmate.data.local.dao.SessionPointLocalDao;
import com.walkmate.data.local.entity.SessionLocalEntity;
import com.walkmate.data.local.entity.SessionPointLocalEntity;
import com.walkmate.data.remote.SessionApi;
import com.walkmate.data.remote.dto.ApiResponseDto;
import com.walkmate.data.remote.dto.CompleteSessionRequestDto;
import com.walkmate.data.remote.dto.SessionResponseDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SessionRepositoryImpl implements SessionRepository {

    private static final String USER_ID_HEADER = "c7a989f0-9f68-43f2-9ca8-160c4e301ce5";

    private final SessionApi api;
    private final SessionLocalDao sessionLocalDao;
    private final SessionPointLocalDao pointLocalDao;
    private final ExecutorService ioExecutor;

    public SessionRepositoryImpl(
            SessionApi api,
            SessionLocalDao sessionLocalDao,
            SessionPointLocalDao pointLocalDao,
            ExecutorService ioExecutor) {
        this.api = api;
        this.sessionLocalDao = sessionLocalDao;
        this.pointLocalDao = pointLocalDao;
        this.ioExecutor = ioExecutor;
    }

    @Override
    public LiveData<List<SessionPointLocalEntity>> observePoints(String sessionId) {
        return pointLocalDao.observeAllBySession(sessionId);
    }

    @Override
    public void initLocalSession(String sessionId) {
        ioExecutor.execute(() -> {
            SessionLocalEntity existing = sessionLocalDao.getById(sessionId);
            if (existing != null) {
                return;
            }
            SessionLocalEntity session = new SessionLocalEntity();
            session.sessionId = sessionId;
            session.backendState = "PENDING";
            session.uiState = "IDLE";
            session.totalDistanceMeters = 0.0;
            session.totalDurationSeconds = 0L;
            session.lastPointOrder = -1;
            session.hasPendingSync = false;
            session.lastErrorMessage = null;
            session.updatedAt = System.currentTimeMillis();
            sessionLocalDao.upsert(session);
        });
    }

    @Override
    public int getNextPointOrder(String sessionId) {
        int max = pointLocalDao.getMaxOrder(sessionId);
        return max + 1;
    }

    @Override
    public void updateLocalStats(String sessionId, double distance, long duration, int lastPointOrder) {
        ioExecutor.execute(() -> sessionLocalDao.updateStats(
                sessionId,
                distance,
                duration,
                pointLocalDao.countUnsynced(sessionId) > 0,
                lastPointOrder,
                System.currentTimeMillis()));
    }

    @Override
    public int countUnsynced(String sessionId) {
        return pointLocalDao.countUnsynced(sessionId);
    }

    @Override
    public double getLocalDistance(String sessionId) {
        SessionLocalEntity session = sessionLocalDao.getById(sessionId);
        return session == null ? 0.0 : session.totalDistanceMeters;
    }

    @Override
    public long getLocalDuration(String sessionId) {
        SessionLocalEntity session = sessionLocalDao.getById(sessionId);
        return session == null ? 0L : session.totalDurationSeconds;
    }

    @Override
    public void activate(String sessionId, ResultCallback<String> callback) {
        api.activate(sessionId, USER_ID_HEADER).enqueue(new Callback<ApiResponseDto<SessionResponseDto>>() {
            @Override
            public void onResponse(Call<ApiResponseDto<SessionResponseDto>> call,
                    Response<ApiResponseDto<SessionResponseDto>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().success
                        && response.body().data != null) {
                    ioExecutor.execute(() -> updateState(sessionId, "ACTIVE", "TRACKING_ACTIVE", null));
                    callback.onResult(Result.success(response.body().data.status));
                    return;
                }
                callback.onResult(Result.error(new IllegalStateException(messageOf(response.body()))));
            }

            @Override
            public void onFailure(Call<ApiResponseDto<SessionResponseDto>> call, Throwable t) {
                callback.onResult(Result.error(t));
            }
        });
    }

    @Override
    public void cancel(String sessionId, String reason, ResultCallback<String> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("reason", reason);

        api.cancel(sessionId, body, USER_ID_HEADER).enqueue(new Callback<ApiResponseDto<SessionResponseDto>>() {
            @Override
            public void onResponse(Call<ApiResponseDto<SessionResponseDto>> call,
                    Response<ApiResponseDto<SessionResponseDto>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().success) {
                    ioExecutor.execute(() -> updateState(sessionId, "CANCELLED", "COMPLETED", null));
                    callback.onResult(Result.success("CANCELLED"));
                    return;
                }
                callback.onResult(Result.error(new IllegalStateException(messageOf(response.body()))));
            }

            @Override
            public void onFailure(Call<ApiResponseDto<SessionResponseDto>> call, Throwable t) {
                callback.onResult(Result.error(t));
            }
        });
    }

    @Override
    public void abort(String sessionId, String reason, ResultCallback<String> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("reason", reason);

        api.abort(sessionId, body, USER_ID_HEADER).enqueue(new Callback<ApiResponseDto<SessionResponseDto>>() {
            @Override
            public void onResponse(Call<ApiResponseDto<SessionResponseDto>> call,
                    Response<ApiResponseDto<SessionResponseDto>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().success) {
                    ioExecutor.execute(() -> updateState(sessionId, "ABORTED", "COMPLETED", null));
                    callback.onResult(Result.success("ABORTED"));
                    return;
                }
                callback.onResult(Result.error(new IllegalStateException(messageOf(response.body()))));
            }

            @Override
            public void onFailure(Call<ApiResponseDto<SessionResponseDto>> call, Throwable t) {
                callback.onResult(Result.error(t));
            }
        });
    }

    @Override
    public void complete(String sessionId, double distance, long duration, ResultCallback<String> callback) {
        CompleteSessionRequestDto request = new CompleteSessionRequestDto(distance, duration);
        api.complete(sessionId, request, USER_ID_HEADER).enqueue(new Callback<ApiResponseDto<SessionResponseDto>>() {
            @Override
            public void onResponse(Call<ApiResponseDto<SessionResponseDto>> call,
                    Response<ApiResponseDto<SessionResponseDto>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().success) {
                    ioExecutor.execute(() -> updateState(sessionId, "COMPLETED", "COMPLETED", null));
                    callback.onResult(Result.success("COMPLETED"));
                    return;
                }
                callback.onResult(Result.error(new IllegalStateException(messageOf(response.body()))));
            }

            @Override
            public void onFailure(Call<ApiResponseDto<SessionResponseDto>> call, Throwable t) {
                callback.onResult(Result.error(t));
            }
        });
    }

    private void updateState(String sessionId, String backendState, String uiState, String error) {
        SessionLocalEntity session = sessionLocalDao.getById(sessionId);
        if (session == null) {
            session = new SessionLocalEntity();
            session.sessionId = sessionId;
        }
        session.backendState = backendState;
        session.uiState = uiState;
        session.lastErrorMessage = error;
        session.updatedAt = System.currentTimeMillis();
        sessionLocalDao.upsert(session);
    }

    private String messageOf(ApiResponseDto<?> body) {
        if (body != null && body.error != null && body.error.message != null) {
            return body.error.message;
        }
        return "Request failed";
    }
}
