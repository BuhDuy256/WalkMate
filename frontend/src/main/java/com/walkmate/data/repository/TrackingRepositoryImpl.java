package com.walkmate.data.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.walkmate.data.datasource.local.dao.RoutePointDao;
import com.walkmate.data.datasource.local.entity.RoutePointEntity;
import com.walkmate.data.mapper.RoutePointMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.tracking.RoutePoint;
import com.walkmate.domain.tracking.TrackingRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Concrete implementation of {@link TrackingRepository}.
 *
 * ── Local storage ──────────────────────────────────────────────────────────────
 * All Room writes/reads happen on a single background thread via the executor,
 * consistent with the architecture's ExecutorService mandate.
 *
 * ── Backend Push (MOCK) ────────────────────────────────────────────────────────
 * {@link #pushRoutePoints} simulates a successful POST /api/v1/tracking/sync
 * without making a real network call. It logs the payload that would be sent,
 * waits 800 ms to mimic network latency, then reports success.
 *
 * To wire up the real Retrofit call when the backend is ready:
 *   1. Inject a {@code RoutePointSyncApiService} via the constructor.
 *   2. Replace the mock body in pushRoutePoints() with:
 *        Call<ApiResponse<PushRoutePointsResponse>> call =
 *            syncApiService.pushRoutePoints(request);
 *        Response<ApiResponse<PushRoutePointsResponse>> response = call.execute();
 *        if (response.isSuccessful() && response.body() != null
 *                && response.body().isSuccess()) {
 *            callback.onSuccess(null);
 *        } else {
 *            callback.onError(new Exception(TrackingErrorCode.SYNC_FAILED));
 *        }
 */
public class TrackingRepositoryImpl implements TrackingRepository {

    private static final String TAG = "TrackingRepo";

    /** Threshold: trigger a backend push when this many unsynced points accumulate. */
    private static final int BATCH_SIZE_THRESHOLD = 50;

    private final RoutePointDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public TrackingRepositoryImpl(RoutePointDao dao) {
        this.dao = dao;
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
        // Map Room entity LiveData → domain model LiveData in one step.
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

    // ── Remote push (MOCK) ────────────────────────────────────────────────────

    @Override
    public void pushRoutePoints(String sessionId, List<RoutePoint> points,
                                DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                // ── MOCK: log what would be sent, then simulate 800 ms network latency ──
                Log.d(TAG, "[MOCK SYNC] POST /api/v1/tracking/sync"
                        + " | sessionId=" + sessionId
                        + " | pointCount=" + points.size());

                Thread.sleep(800);  // simulated network round-trip

                // ── MOCK: always succeeds ────────────────────────────────────────────
                Log.d(TAG, "[MOCK SYNC] Success — " + points.size() + " points acknowledged");
                callback.onSuccess(null);

                /* ── REAL Retrofit call (uncomment when backend is ready) ─────────────
                List<RoutePointEntity> entities = new ArrayList<>();
                for (RoutePoint p : points) entities.add(RoutePointMapper.toEntity(p));

                PushRoutePointsRequest request = new PushRoutePointsRequest(
                        sessionId, RoutePointMapper.toPayloadList(entities));

                Call<ApiResponse<PushRoutePointsResponse>> call =
                        syncApiService.pushRoutePoints(request);
                Response<ApiResponse<PushRoutePointsResponse>> response = call.execute();

                if (response.isSuccessful()
                        && response.body() != null
                        && response.body().isSuccess()) {
                    callback.onSuccess(null);
                } else {
                    String errMsg = response.body() != null && response.body().getError() != null
                            ? response.body().getError().getMessage()
                            : "Unknown sync error";
                    callback.onError(new Exception(errMsg));
                }
                ─────────────────────────────────────────────────────────────────── */

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                callback.onError(e);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Fetches all unsynced points for the session and pushes them as a batch.
     * Called internally from saveRoutePoint when the threshold is crossed.
     */
    private void triggerBatchSync(String sessionId) {
        List<RoutePointEntity> unsyncedEntities = dao.getUnsyncedPoints(sessionId);
        if (unsyncedEntities.isEmpty()) return;

        List<RoutePoint> domainPoints = RoutePointMapper.toDomainList(unsyncedEntities);
        pushRoutePoints(sessionId, domainPoints, new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Extract IDs and mark as synced in the local DB.
                List<Long> ids = new ArrayList<>(unsyncedEntities.size());
                for (RoutePointEntity e : unsyncedEntities) ids.add(e.id);
                dao.markAsSynced(ids);
                Log.d(TAG, "[MOCK SYNC] Marked " + ids.size() + " points as synced");
            }

            @Override
            public void onError(Exception error) {
                Log.e(TAG, "[MOCK SYNC] Batch sync failed: " + error.getMessage());
                // Points remain unsynced; next threshold crossing will retry.
            }
        });
    }
}
