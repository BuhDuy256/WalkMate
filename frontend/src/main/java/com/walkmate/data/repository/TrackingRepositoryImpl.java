package com.walkmate.data.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.walkmate.data.datasource.local.dao.RoutePointDao;
import com.walkmate.data.datasource.local.entity.RoutePointEntity;
import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.RoutePointSyncApiService;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.dto.request.tracking.PushRoutePointsRequest;
import com.walkmate.core.util.ErrorParser;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.tracking.PushRoutePointsResponse;
import com.walkmate.data.mapper.RoutePointMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.tracking.RoutePoint;
import com.walkmate.domain.tracking.TrackingErrorCode;
import com.walkmate.domain.tracking.TrackingRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

/**
 * Concrete implementation of {@link TrackingRepository}.
 *
 * ── User scoping ───────────────────────────────────────────────────────────────
 * Every Room read and write is scoped to the currently logged-in user
 * (obtained via {@link SessionManager#getUserId()}). This prevents GPS path
 * data from leaking between accounts when two users share the same device and
 * participate in the same walk session (which shares a sessionId).
 *
 * ── Local storage ──────────────────────────────────────────────────────────────
 * All Room writes/reads happen on a single background thread via the executor.
 *
 * ── Backend Push ───────────────────────────────────────────────────────────────
 * {@link #pushRoutePoints} posts a batch of GPS points to {@code POST /api/v1/tracking/sync}.
 */
public class TrackingRepositoryImpl implements TrackingRepository {

    public interface SessionEndedListener {
        void onSessionEndedRemotely(String errorCode);
    }

    private static final String TAG = "TrackingRepo";

    /** Trigger a backend push when this many unsynced points accumulate. */
    private static final int BATCH_SIZE_THRESHOLD = 50;

    private final RoutePointDao          dao;
    private final SessionManager         sessionManager;
    private final ExecutorService        executor = Executors.newSingleThreadExecutor();

    private SessionEndedListener sessionEndedListener;

    public void setSessionEndedListener(SessionEndedListener l) {
        this.sessionEndedListener = l;
    }

    public TrackingRepositoryImpl(RoutePointDao dao, SessionManager sessionManager) {
        this.dao            = dao;
        this.sessionManager = sessionManager;
    }

    // ── Local writes ──────────────────────────────────────────────────────────

    @Override
    public void saveRoutePoint(RoutePoint point, DomainCallback<Long> callback) {
        executor.execute(() -> {
            try {
                RoutePointEntity entity = RoutePointMapper.toEntity(point);
                // Stamp the currently logged-in user so that points from different
                // accounts on the same device are always isolated in Room.
                entity.userId = currentUserId();
                long rowId = dao.insertPoint(entity);
                callback.onSuccess(rowId);

                // Auto-trigger batch push when threshold is reached.
                int unsyncedCount = dao.getUnsyncedCount(point.getSessionId(), entity.userId);
                if (unsyncedCount >= BATCH_SIZE_THRESHOLD) {
                    triggerBatchSync(point.getSessionId(), entity.userId);
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    @Override
    public void markPointsSynced(List<Long> ids, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                dao.markAsSynced(ids);
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    // ── Local reads ───────────────────────────────────────────────────────────

    @Override
    public LiveData<List<RoutePoint>> getPointsForSession(String sessionId) {
        String userId = currentUserId();
        return Transformations.map(
                dao.getPointsBySessionAndUser(sessionId, userId),
                RoutePointMapper::toDomainList
        );
    }

    @Override
    public void getUnsyncedCount(String sessionId, DomainCallback<Integer> callback) {
        executor.execute(() -> {
            try {
                int count = dao.getUnsyncedCount(sessionId, currentUserId());
                callback.onSuccess(count);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    // ── Remote push ───────────────────────────────────────────────────────────

    @Override
    public void pushRoutePoints(String sessionId, List<RoutePoint> points,
                                DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                List<RoutePointEntity> entities = new ArrayList<>(points.size());
                for (RoutePoint p : points) entities.add(RoutePointMapper.toEntity(p));

                List<PushRoutePointsRequest.RoutePointPayload> payloads =
                        RoutePointMapper.toPayloadList(entities);

                PushRoutePointsRequest request = new PushRoutePointsRequest(sessionId, payloads);

                RoutePointSyncApiService api = ApiClient.buildAuthenticatedRetrofit(sessionManager, ApiClient.getAuthApiService())
                        .create(RoutePointSyncApiService.class);

                Response<ApiResponse<PushRoutePointsResponse>> response =
                        api.pushRoutePoints(request).execute();

                if (response.isSuccessful()
                        && response.body() != null
                        && response.body().isSuccess()) {
                    List<Long> acked = response.body().getData().getAcknowledgedIds();
                    if (acked != null && !acked.isEmpty()) {
                        dao.markAsSynced(acked);
                    }
                    Log.d(TAG, "Sync succeeded — " + points.size() + " points acknowledged");
                    callback.onSuccess(null);
                } else {
                    ApiError apiError = ErrorParser.extractApiError(response, TrackingErrorCode.SYNC_FAILED);
                    String errorCode = apiError.getCode();
                    Log.w(TAG, "Sync failed: " + errorCode);
                    if ("SESSION_NOT_ACTIVE".equals(errorCode) || "SESSION_NOT_FOUND".equals(errorCode)) {
                        callback.onError(new Exception("SESSION_TERMINAL|" + errorCode));
                    } else if (response.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(errorCode));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "pushRoutePoints network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void triggerPeriodicSync(String sessionId) {
        executor.execute(() -> {
            String userId = currentUserId();
            List<RoutePointEntity> unsyncedEntities = dao.getUnsyncedPoints(sessionId, userId);
            if (unsyncedEntities != null && !unsyncedEntities.isEmpty()) {
                List<RoutePoint> domainPoints = RoutePointMapper.toDomainList(unsyncedEntities);
                pushRoutePoints(sessionId, domainPoints, new DomainCallback<Void>() {
                    @Override public void onSuccess(Void v) { /* silent success */ }
                    @Override public void onError(Exception e) {
                        if (e.getMessage() != null && e.getMessage().startsWith("SESSION_TERMINAL|")) {
                            if (sessionEndedListener != null) {
                                sessionEndedListener.onSessionEndedRemotely(e.getMessage());
                            }
                        }
                    }
                });
            }
        });
    }

    @Override
    public void clearForUser(String userId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                if (userId != null && !userId.isEmpty()) {
                    dao.deleteByUserId(userId);
                }
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void triggerBatchSync(String sessionId, String userId) {
        List<RoutePointEntity> unsyncedEntities = dao.getUnsyncedPoints(sessionId, userId);
        if (unsyncedEntities.isEmpty()) return;

        List<RoutePoint> domainPoints = RoutePointMapper.toDomainList(unsyncedEntities);
        pushRoutePoints(sessionId, domainPoints, new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.d(TAG, "Batch sync completed — " + unsyncedEntities.size() + " points pushed");
            }

            @Override
            public void onError(Exception error) {
                Log.e(TAG, "Batch sync failed — will retry: " + error.getMessage());
            }
        });
    }

    /** Returns the current user's ID, or an empty string when no session is active. */
    private String currentUserId() {
        String id = sessionManager.getUserId();
        return id != null ? id : "";
    }
}
