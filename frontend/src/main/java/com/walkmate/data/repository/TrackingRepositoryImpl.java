package com.walkmate.data.repository;

import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.walkmate.data.datasource.local.dao.RoutePointDao;
import com.walkmate.data.datasource.local.entity.RoutePointEntity;
import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.PartnerPathApiService;
import com.walkmate.data.datasource.remote.api.RoutePointSyncApiService;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.dto.request.tracking.PushRoutePointsRequest;
import com.walkmate.core.util.ErrorParser;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.tracking.PartnerPathResponseDto;
import com.walkmate.data.datasource.remote.dto.response.tracking.PushRoutePointsResponse;
import com.walkmate.data.mapper.RoutePointMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.tracking.PartnerPathResult;
import com.walkmate.domain.tracking.RoutePoint;
import com.walkmate.domain.tracking.TrackingErrorCode;
import com.walkmate.domain.tracking.TrackingRepository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * {@code doHttpPush} is the single private helper that executes the Retrofit call
 * synchronously (called from inside an executor task). Both the 50-point batch
 * trigger and the 30 s periodic timer funnel through it, protected by the
 * {@link #syncInFlight} guard (R4).
 *
 * ── Idempotent sync (R2) ───────────────────────────────────────────────────────
 * Each push attempt generates a fresh {@link UUID} as {@code sync_request_id}.
 * The backend stores this in {@code session_point_chunks.sync_request_id} with a
 * UNIQUE constraint, so a duplicated request is silently acknowledged rather than
 * persisted twice.
 *
 * ── In-flight guard (R4) ──────────────────────────────────────────────────────
 * {@link #syncInFlight} is an {@link AtomicBoolean}. Both the batch and periodic
 * triggers call {@code compareAndSet(false, true)} before touching the network.
 * The losing caller skips silently; the winning caller resets the flag in its
 * HTTP callback (success or error).
 *
 * ── Final flush (R1) ──────────────────────────────────────────────────────────
 * {@link #flushUnsyncedBeforeComplete} is submitted to the same executor, so it
 * is naturally serialised after any queued sync task. It bypasses the guard
 * because the GPS service is already stopped at call time — no new points can
 * arrive and no new sync triggers will fire.
 */
public class TrackingRepositoryImpl implements TrackingRepository {

    public interface SessionEndedListener {
        void onSessionEndedRemotely(String errorCode);
    }

    private static final String TAG = "TrackingRepo";

    /** Trigger a backend push when this many unsynced points accumulate. */
    private static final int BATCH_SIZE_THRESHOLD = 50;

    private final RoutePointDao   dao;
    private final SessionManager  sessionManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * R4 — In-flight guard.
     *
     * Set to {@code true} atomically before any HTTP push attempt. Reset to
     * {@code false} in the HTTP callback (success or error). Any concurrent
     * trigger (batch or periodic) that loses the CAS simply returns without
     * starting a second request.
     */
    private final AtomicBoolean syncInFlight = new AtomicBoolean(false);

    private SessionEndedListener sessionEndedListener;

    public void setSessionEndedListener(SessionEndedListener l) {
        this.sessionEndedListener = l;
    }

    public TrackingRepositoryImpl(RoutePointDao dao, SessionManager sessionManager) {
        this.dao           = dao;
        this.sessionManager = sessionManager;
    }

    // ── Local writes ──────────────────────────────────────────────────────────

    @Override
    public void saveRoutePoint(RoutePoint point, DomainCallback<Long> callback) {
        executor.execute(() -> {
            try {
                RoutePointEntity entity = RoutePointMapper.toEntity(point);
                entity.userId = currentUserId();
                long rowId = dao.insertPoint(entity);
                callback.onSuccess(rowId);

                // Auto-trigger batch push when threshold is reached.
                // triggerBatchSync() submits its own executor task so it does not
                // block the current task.
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

    // ── Remote push (public interface) ────────────────────────────────────────

    /**
     * Pushes an explicit list of points. Callers outside this class should
     * prefer {@link #triggerPeriodicSync} or the automatic batch trigger;
     * this method is kept for interface compatibility.
     */
    @Override
    public void pushRoutePoints(String sessionId, List<RoutePoint> points,
                                DomainCallback<Void> callback) {
        executor.execute(() -> doHttpPush(sessionId, points, callback));
    }

    // ── Sync triggers ─────────────────────────────────────────────────────────

    /**
     * Periodic sync — called by the 30 s scheduler in
     * {@link com.walkmate.domain.tracking.SessionTrackingService}.
     *
     * <p>R4: Skips silently when {@link #syncInFlight} is already {@code true}.
     */
    @Override
    public void triggerPeriodicSync(String sessionId) {
        if (!syncInFlight.compareAndSet(false, true)) {
            Log.d(TAG, "Periodic sync skipped — a sync is already in flight");
            return;
        }
        executor.execute(() -> {
            String userId = currentUserId();
            List<RoutePointEntity> unsynced = dao.getUnsyncedPoints(sessionId, userId);
            if (unsynced == null || unsynced.isEmpty()) {
                syncInFlight.set(false);
                return;
            }
            doHttpPush(sessionId, RoutePointMapper.toDomainList(unsynced),
                    new DomainCallback<Void>() {
                        @Override
                        public void onSuccess(Void v) {
                            syncInFlight.set(false);
                            Log.d(TAG, "Periodic sync succeeded — " + unsynced.size() + " pts");
                        }

                        @Override
                        public void onError(Exception e) {
                            syncInFlight.set(false);
                            Log.e(TAG, "Periodic sync failed: " + e.getMessage());
                            if (e.getMessage() != null
                                    && e.getMessage().startsWith("SESSION_TERMINAL|")) {
                                if (sessionEndedListener != null) {
                                    sessionEndedListener.onSessionEndedRemotely(e.getMessage());
                                }
                            }
                        }
                    });
        });
    }

    // ── R1: Final flush before session complete ───────────────────────────────

    /**
     * Flushes every remaining unsynced point for the session immediately before
     * the session is marked complete on the backend.
     *
     * <p>Submitted to the same {@link #executor} so it is naturally serialised
     * after any already-queued sync task. Bypasses {@link #syncInFlight} — the
     * GPS service is stopped before this is called so no new triggers will fire.
     *
     * <p>If the HTTP call fails, {@code callback.onError} is invoked; the caller
     * ({@link com.walkmate.ui.tracking.TrackingViewModel}) should still proceed
     * with session completion to avoid stranding the user on the FINISHING screen.
     */
    @Override
    public void flushUnsyncedBeforeComplete(String sessionId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            String userId = currentUserId();
            List<RoutePointEntity> unsynced = dao.getUnsyncedPoints(sessionId, userId);
            if (unsynced == null || unsynced.isEmpty()) {
                Log.d(TAG, "Pre-complete flush: nothing to push");
                callback.onSuccess(null);
                return;
            }
            Log.d(TAG, "Pre-complete flush: pushing " + unsynced.size() + " remaining pts");
            doHttpPush(sessionId, RoutePointMapper.toDomainList(unsynced), callback);
        });
    }

    // ── Batch sync helper ─────────────────────────────────────────────────────

    /**
     * Called from inside a {@link #saveRoutePoint} executor task when the
     * unsynced count crosses {@link #BATCH_SIZE_THRESHOLD}.
     *
     * <p>R4: Acquires {@link #syncInFlight} before submitting the HTTP task so
     * the periodic timer cannot overlap with this batch push. The HTTP task is
     * queued on the executor — it will run after the current saveRoutePoint task
     * completes, keeping the executor thread free.
     */
    private void triggerBatchSync(String sessionId, String userId) {
        if (!syncInFlight.compareAndSet(false, true)) {
            Log.d(TAG, "Batch sync skipped — a sync is already in flight");
            return;
        }
        executor.execute(() -> {
            List<RoutePointEntity> unsynced = dao.getUnsyncedPoints(sessionId, userId);
            if (unsynced == null || unsynced.isEmpty()) {
                syncInFlight.set(false);
                return;
            }
            doHttpPush(sessionId, RoutePointMapper.toDomainList(unsynced),
                    new DomainCallback<Void>() {
                        @Override
                        public void onSuccess(Void v) {
                            syncInFlight.set(false);
                            Log.d(TAG, "Batch sync succeeded — " + unsynced.size() + " pts");
                        }

                        @Override
                        public void onError(Exception e) {
                            syncInFlight.set(false);
                            Log.e(TAG, "Batch sync failed — will retry on next tick: "
                                    + e.getMessage());
                        }
                    });
        });
    }

    // ── Core HTTP push ────────────────────────────────────────────────────────

    /**
     * Executes the Retrofit sync call synchronously on the calling thread
     * (always an executor thread — never the main thread).
     *
     * <p><b>R2 — Deterministic {@code sync_request_id}:</b>
     * The ID is derived from {@code sessionId + firstLocalId + lastLocalId} of the
     * batch, then hashed to a UUID v3 via {@link UUID#nameUUIDFromBytes}.
     * Because Room auto-increments {@code local_id} on insert and points are
     * queried {@code ORDER BY timestamp ASC}, the same unsynced batch always
     * produces the same seed string and therefore the same UUID.
     *
     * <p>This makes retries truly idempotent: if the backend committed the chunk
     * but the HTTP 200 was lost in transit (network timeout), the next attempt
     * carries the identical UUID → backend UNIQUE constraint fires →
     * {@code DataIntegrityViolationException} caught → HTTP 200 returned without
     * a second INSERT. The app then marks its Room rows as synced.
     *
     * <p><b>Known edge case:</b> If new GPS points join the Room between a failed
     * attempt and its retry (possible during an active walk, impossible during the
     * final flush after {@code stopGpsService()}), the last {@code local_id}
     * changes → new UUID → the original batch is stored again. This is an
     * inherent limitation of content-addressable IDs without per-point ACKs;
     * it is acceptable for this project's scope.
     *
     * <p><b>Synchronous Retrofit (.execute()):</b> the HTTP call blocks this
     * executor thread until a response or exception is received. {@code .enqueue()}
     * must never be used here — it would dispatch to OkHttp's thread pool and
     * return immediately, allowing the executor to pick up the next queued task
     * (e.g. flushUnsyncedBeforeComplete) before the HTTP call finishes, breaking
     * the serialisation guarantee entirely.
     *
     * <p>On success, marks acknowledged Room rows as synced.
     * On a terminal session error ({@code SESSION_NOT_ACTIVE} / {@code SESSION_NOT_FOUND}),
     * wraps the error code so callers can stop the foreground service.
     */
    private void doHttpPush(String sessionId, List<RoutePoint> points,
                             DomainCallback<Void> callback) {
        try {
            // R2 — deterministic UUID: same batch content → same ID → true idempotency.
            // UUID.nameUUIDFromBytes produces a valid UUID v3 string accepted by the
            // backend's UUID.fromString() parser, while remaining fully deterministic.
            long   firstLocalId      = points.get(0).getId();
            long   lastLocalId       = points.get(points.size() - 1).getId();
            String deterministicSeed = sessionId + "_" + firstLocalId + "_" + lastLocalId;
            String syncRequestId     = UUID.nameUUIDFromBytes(
                    deterministicSeed.getBytes(StandardCharsets.UTF_8)).toString();

            List<RoutePointEntity> entities = new ArrayList<>(points.size());
            for (RoutePoint p : points) entities.add(RoutePointMapper.toEntity(p));

            List<PushRoutePointsRequest.RoutePointPayload> payloads =
                    RoutePointMapper.toPayloadList(entities);

            PushRoutePointsRequest request =
                    new PushRoutePointsRequest(syncRequestId, sessionId, payloads);

            RoutePointSyncApiService api =
                    ApiClient.buildAuthenticatedRetrofit(sessionManager, ApiClient.getAuthApiService())
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
                Log.d(TAG, "Sync OK — " + points.size() + " pts, reqId=" + syncRequestId);
                callback.onSuccess(null);
            } else {
                ApiError apiError =
                        ErrorParser.extractApiError(response, TrackingErrorCode.SYNC_FAILED);
                String errorCode = apiError.getCode();
                Log.w(TAG, "Sync HTTP error: " + errorCode);
                if ("SESSION_NOT_ACTIVE".equals(errorCode)
                        || "SESSION_NOT_FOUND".equals(errorCode)) {
                    callback.onError(new Exception("SESSION_TERMINAL|" + errorCode));
                } else if (response.code() == 422) {
                    callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                } else {
                    callback.onError(new Exception(errorCode));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "doHttpPush network error", e);
            callback.onError(e);
        }
    }

    // ── User cleanup ──────────────────────────────────────────────────────────

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

    // ── Partner path fetch ────────────────────────────────────────────────────

    /**
     * Fetches partner GPS chunks since {@code afterChunkIndex} from the backend,
     * decodes each Google Encoded Polyline string into a {@code List<LatLng>},
     * and returns the aggregated result via {@code callback}.
     *
     * <p>Runs on the shared executor thread. Uses synchronous {@code .execute()}
     * to keep the executor task sequential (same pattern as {@link #doHttpPush}).
     *
     * <p>On a terminal-session error ({@code SESSION_NOT_ACTIVE} /
     * {@code SESSION_NOT_FOUND}) the error message is prefixed with
     * {@code "SESSION_TERMINAL|"} so the ViewModel can stop polling.
     */
    @Override
    public void fetchPartnerPath(String sessionId, int afterChunkIndex,
                                 DomainCallback<PartnerPathResult> callback) {
        executor.execute(() -> {
            try {
                PartnerPathApiService api =
                        ApiClient.buildAuthenticatedRetrofit(sessionManager, ApiClient.getAuthApiService())
                                .create(PartnerPathApiService.class);

                retrofit2.Response<ApiResponse<PartnerPathResponseDto>> response =
                        api.getPartnerPath(sessionId, afterChunkIndex).execute();

                if (response.isSuccessful()
                        && response.body() != null
                        && response.body().isSuccess()) {

                    PartnerPathResponseDto dto = response.body().getData();

                    List<LatLng> decoded = new ArrayList<>();
                    int lastChunkIndex = afterChunkIndex;

                    if (dto.getChunks() != null) {
                        for (PartnerPathResponseDto.PartnerChunkDto chunk : dto.getChunks()) {
                            if (chunk.getPolyline() != null && !chunk.getPolyline().isEmpty()) {
                                decoded.addAll(PolyUtil.decode(chunk.getPolyline()));
                            }
                            if (chunk.getChunkIndex() > lastChunkIndex) {
                                lastChunkIndex = chunk.getChunkIndex();
                            }
                        }
                    }

                    callback.onSuccess(new PartnerPathResult(
                            decoded,
                            lastChunkIndex,
                            dto.getLastChunkCreatedAtMs(),
                            dto.getPartnerStatus()));

                } else {
                    ApiError apiError = ErrorParser.extractApiError(
                            response, TrackingErrorCode.FETCH_PARTNER_PATH_FAILED);
                    String code = apiError.getCode();
                    Log.w(TAG, "fetchPartnerPath HTTP error: " + code);
                    if ("SESSION_NOT_ACTIVE".equals(code) || "SESSION_NOT_FOUND".equals(code)) {
                        callback.onError(new Exception("SESSION_TERMINAL|" + code));
                    } else {
                        callback.onError(new Exception(code));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "fetchPartnerPath network error", e);
                callback.onError(e);
            }
        });
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private String currentUserId() {
        String id = sessionManager.getUserId();
        return id != null ? id : "";
    }
}
