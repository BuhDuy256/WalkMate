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
 * ── Local storage ──────────────────────────────────────────────────────────────
 * All Room writes/reads happen on a single background thread via the executor,
 * consistent with the architecture's ExecutorService mandate.
 *
 * ── Backend Push ───────────────────────────────────────────────────────────────
 * {@link #pushRoutePoints} posts a batch of GPS points to {@code POST /api/v1/tracking/sync}.
 * On success the caller's {@link #triggerBatchSync} marks the same rows as synced.
 * On network failure the points remain in Room with {@code isSynced = false} and
 * will be retried the next time the 50-point threshold is crossed.
 */
public class TrackingRepositoryImpl implements TrackingRepository {

    private static final String TAG = "TrackingRepo";

    /** Trigger a backend push when this many unsynced points accumulate. */
    private static final int BATCH_SIZE_THRESHOLD = 50;

    private final RoutePointDao          dao;
    private final SessionManager         sessionManager;
    private final ExecutorService        executor = Executors.newSingleThreadExecutor();

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
                long rowId = dao.insertPoint(entity);
                callback.onSuccess(rowId);

                // Auto-trigger batch push when threshold is reached.
                int unsyncedCount = dao.getUnsyncedCount(point.getSessionId());
                if (unsyncedCount >= BATCH_SIZE_THRESHOLD) {
                    triggerBatchSync(point.getSessionId());
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
        return Transformations.map(
                dao.getPointsBySessionId(sessionId),
                RoutePointMapper::toDomainList
        );
    }

    @Override
    public void getUnsyncedCount(String sessionId, DomainCallback<Integer> callback) {
        executor.execute(() -> {
            try {
                int count = dao.getUnsyncedCount(sessionId);
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
                // Convert domain objects → entities → remote payloads
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
                    Log.d(TAG, "Sync succeeded — " + points.size() + " points acknowledged");
                    callback.onSuccess(null);
                } else {
                    String errCode = response.body() != null && response.body().getError() != null
                            ? response.body().getError().getCode()
                            : TrackingErrorCode.SYNC_FAILED;
                    Log.w(TAG, "Sync failed: " + errCode);
                    callback.onError(new Exception(errCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "pushRoutePoints network error", e);
                callback.onError(e);
            }
        });
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Fetches all unsynced points for the session and pushes them as a batch.
     * Called internally from {@link #saveRoutePoint} when the threshold is crossed.
     * On success marks the same rows as synced. On failure, points remain in Room
     * for retry on the next threshold crossing.
     */
    private void triggerBatchSync(String sessionId) {
        List<RoutePointEntity> unsyncedEntities = dao.getUnsyncedPoints(sessionId);
        if (unsyncedEntities.isEmpty()) return;

        List<RoutePoint> domainPoints = RoutePointMapper.toDomainList(unsyncedEntities);
        pushRoutePoints(sessionId, domainPoints, new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                List<Long> ids = new ArrayList<>(unsyncedEntities.size());
                for (RoutePointEntity e : unsyncedEntities) ids.add(e.id);
                dao.markAsSynced(ids);
                Log.d(TAG, "Marked " + ids.size() + " points as synced");
            }

            @Override
            public void onError(Exception error) {
                // Points remain unsynced; next threshold crossing will retry.
                Log.e(TAG, "Batch sync failed — will retry: " + error.getMessage());
            }
        });
    }
}
