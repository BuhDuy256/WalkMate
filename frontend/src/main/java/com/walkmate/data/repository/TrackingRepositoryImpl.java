package com.walkmate.data.repository;

import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.walkmate.core.util.ErrorParser;
import com.walkmate.data.datasource.local.GpsQueueStore;
import com.walkmate.data.datasource.local.WalkMateDatabase;
import com.walkmate.data.datasource.local.dao.RoutePointDao;
import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.PartnerPathApiService;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.tracking.PartnerPathResponseDto;
import com.walkmate.data.mapper.RoutePointMapper;
import com.walkmate.data.worker.GpsSyncScheduler;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.tracking.PartnerPathResult;
import com.walkmate.domain.tracking.RoutePoint;
import com.walkmate.domain.tracking.TrackingErrorCode;
import com.walkmate.domain.tracking.TrackingRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

/**
 * Concrete implementation of {@link TrackingRepository} for the v2 sync contract.
 *
 * <p><b>What changed from v1.</b> The old implementation built a request from
 * whatever happened to be unsynced at push time and derived a "deterministic"
 * batch id from the first/last Room row ids. That quietly broke idempotency: if a
 * new fix arrived between two attempts the derived id changed, so a retry after a
 * lost ACK was stored as a second, overlapping batch. v2 fixes the payload
 * <em>before</em> the first attempt:</p>
 *
 * <pre>
 *   accept point ─▶ reserve sequence ─▶ insert  (one Room transaction)
 *   seal batch   ─▶ freeze membership ─▶ hash   (one Room transaction)
 *   upload       ─▶ retry the SAME batch id/payload until the server ACKs
 * </pre>
 *
 * <p>The queue lives in Room, so an outage, a network drop or a process kill
 * simply leaves rows in {@code PENDING}. {@link GpsSyncScheduler} re-arms the
 * upload; {@link GpsQueueDrainer} performs it.</p>
 *
 * <p>All Room and network work runs on a single background thread, keeping local
 * writes ordered with respect to each other.</p>
 */
public class TrackingRepositoryImpl implements TrackingRepository {

    public interface SessionEndedListener {
        void onSessionEndedRemotely(String errorCode);
    }

    private static final String TAG = "TrackingRepo";

    /** Seal a batch as soon as this many points are unbatched. */
    private static final int BATCH_SIZE_THRESHOLD = GpsQueueStore.MAX_POINTS_PER_BATCH;

    private final RoutePointDao dao;
    private final GpsQueueStore queueStore;
    private final GpsQueueDrainer drainer;
    private final GpsSyncScheduler scheduler;
    private final SessionManager sessionManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SessionEndedListener sessionEndedListener;

    public TrackingRepositoryImpl(WalkMateDatabase database,
                                  GpsQueueStore queueStore,
                                  GpsQueueDrainer drainer,
                                  GpsSyncScheduler scheduler,
                                  SessionManager sessionManager) {
        this.dao = database.routePointDao();
        this.queueStore = queueStore;
        this.drainer = drainer;
        this.scheduler = scheduler;
        this.sessionManager = sessionManager;
    }

    public void setSessionEndedListener(SessionEndedListener l) {
        this.sessionEndedListener = l;
    }

    // ── Local writes ──────────────────────────────────────────────────────────

    /**
     * Persists one fix, assigning its stream sequence inside the same transaction.
     * The callback receives the assigned sequence (not the Room row id) so callers
     * can reason about route order.
     */
    @Override
    public void saveRoutePoint(RoutePoint point, DomainCallback<Long> callback) {
        executor.execute(() -> {
            try {
                String userId = currentUserId();
                if (userId.isEmpty()) {
                    callback.onError(new IllegalStateException("No signed-in user"));
                    return;
                }

                String streamId = queueStore.ensureStream(point.getSessionId(), userId);
                long sequence = queueStore.saveAcceptedPoint(
                        point.getSessionId(), userId, streamId,
                        point.getLat(), point.getLng(), point.getTimestamp(), point.getAccuracy());
                callback.onSuccess(sequence);

                // Seal + enqueue as soon as a full batch is available. Sealing is a
                // separate transaction from accepting, so a crash between them just
                // leaves unbatched points that the next seal picks up.
                if (queueStore.countUnbatched(point.getSessionId(), userId) >= BATCH_SIZE_THRESHOLD) {
                    queueStore.sealAllPending(point.getSessionId(), userId);
                    scheduler.requestDrain(userId, point.getSessionId(), false);
                }
            } catch (Exception e) {
                Log.e(TAG, "saveRoutePoint failed", e);
                callback.onError(e);
            }
        });
    }

    /**
     * Kept for interface compatibility. Under v2 the only verified way to mark
     * points synced is a matching batch ACK, which the drainer performs; marking
     * an arbitrary id list synced would defeat the lost-ACK guarantee.
     */
    @Override
    @Deprecated
    public void markPointsSynced(List<Long> ids, DomainCallback<Void> callback) {
        Log.w(TAG, "markPointsSynced(ids) is a no-op in v2 — points are synced by verified batch ACK");
        callback.onSuccess(null);
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
                callback.onSuccess(queueStore.countOutstanding(sessionId, currentUserId()));
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    // ── Remote push (public interface) ────────────────────────────────────────

    /**
     * Seals any remaining points and uploads the queue once.
     *
     * <p>The {@code points} argument is deliberately ignored: the payload of a
     * batch is the frozen snapshot in Room, never "whatever the caller happened to
     * pass". Keeping the signature avoids breaking existing callers.</p>
     */
    @Override
    public void pushRoutePoints(String sessionId, List<RoutePoint> points,
                                DomainCallback<Void> callback) {
        executor.execute(() -> {
            String userId = currentUserId();
            try {
                queueStore.sealAllPending(sessionId, userId);
                GpsQueueDrainer.DrainResult result = drainer.drain(sessionId, userId, false);
                if (result.remaining > 0 || result.blocked > 0) {
                    callback.onError(new Exception(result.blocked > 0
                            ? TrackingErrorCode.BATCH_BLOCKED
                            : TrackingErrorCode.SYNC_FAILED));
                    return;
                }
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * Periodic / in-walk sync trigger.
     *
     * <p>Seals the current tail into a batch (so the 30 s timer sends small
     * batches as well as full ones — plan scenario T02) and requests a drain. The
     * upload itself is handed to WorkManager so it survives this process being
     * killed mid-flight.</p>
     */
    @Override
    public void triggerPeriodicSync(String sessionId) {
        executor.execute(() -> {
            String userId = currentUserId();
            if (userId.isEmpty()) {
                return;
            }
            try {
                queueStore.sealAllPending(sessionId, userId);
                if (queueStore.hasOutstanding(sessionId, userId)) {
                    scheduler.requestDrain(userId, sessionId, false);
                }
            } catch (Exception e) {
                Log.e(TAG, "Periodic sync trigger failed", e);
            }
        });
    }

    // ── Finish: seal the stream and drain ─────────────────────────────────────

    /**
     * Called immediately before the session is marked complete.
     *
     * <p>v2 splits this into two independent things:</p>
     * <ol>
     *   <li><b>Seal</b> — freeze the final sequence so the server knows what
     *       "complete" means. This is durable locally and needs no network, so
     *       finishing a walk offline is not blocked by GPS upload.</li>
     *   <li><b>Drain</b> — try to upload what is queued. Failure is reported but
     *       must not stop the walk from completing; the queue keeps the data and a
     *       worker drains it later within the server's late-upload window.</li>
     * </ol>
     */
    @Override
    public void flushUnsyncedBeforeComplete(String sessionId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            String userId = currentUserId();
            try {
                // 1 — freeze membership of whatever is still unbatched.
                queueStore.sealAllPending(sessionId, userId);

                // 2 — persist the completion intent locally. This is the durable
                //     part: even if everything below fails, the next process knows
                //     the final sequence and can finish the job.
                long finalSequence = queueStore.lastAllocatedSequence(sessionId, userId);
                queueStore.persistFinalSequence(sessionId, userId, finalSequence,
                        System.currentTimeMillis());

                // 3 — one best-effort synchronous drain so a connected device
                //     usually converges before completeSession() is called.
                GpsQueueDrainer.DrainResult result = drainer.drain(sessionId, userId, true);
                if (result.remaining > 0) {
                    scheduler.requestDrain(userId, sessionId, true);
                    callback.onError(new Exception(
                            "GPS_SYNC_PENDING|" + result.remaining + " batches queued"));
                    return;
                }
                if (result.blocked > 0) {
                    callback.onError(new Exception(
                            TrackingErrorCode.BATCH_BLOCKED + "|" + result.blocked));
                    return;
                }
                callback.onSuccess(null);
            } catch (Exception e) {
                Log.e(TAG, "Pre-complete flush failed", e);
                callback.onError(e);
            }
        });
    }

    /**
     * Declares the final sequence to the server and reports the resulting sync
     * state. Called after the local seal so the server converges to
     * {@code COMPLETE} as soon as coverage allows.
     */
    @Override
    public void sealStream(String sessionId, DomainCallback<String> callback) {
        executor.execute(() -> {
            try {
                String state = drainer.seal(sessionId, currentUserId());
                if (state == null) {
                    callback.onError(new Exception(TrackingErrorCode.SYNC_FAILED));
                } else {
                    callback.onSuccess(state);
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /** Reports the local sync state ({@code PENDING} until the server confirms). */
    @Override
    public void getSyncState(String sessionId, DomainCallback<String> callback) {
        executor.execute(() -> {
            try {
                callback.onSuccess(queueStore.syncState(sessionId, currentUserId()));
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    // ── User cleanup ──────────────────────────────────────────────────────────

    @Override
    public void clearForUser(String userId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                if (userId != null && !userId.isEmpty()) {
                    // Removes batches, the stream cursor and the points in one
                    // transaction so no orphaned batch can reference deleted rows.
                    queueStore.clearForUser(userId);
                }
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    // ── Partner path fetch ────────────────────────────────────────────────────

    /**
     * Fetches partner GPS chunks since {@code afterChunkIndex}.
     *
     * <p>The cursor stays {@code chunk_index} (arrival order — what the server
     * paginates on), but each chunk may now carry its sequence range. The
     * ViewModel re-orders by sequence so a late batch lands at its correct route
     * position instead of being appended to the end.</p>
     */
    @Override
    public void fetchPartnerPath(String sessionId, int afterChunkIndex,
                                 DomainCallback<PartnerPathResult> callback) {
        executor.execute(() -> {
            try {
                PartnerPathApiService api =
                        ApiClient.buildAuthenticatedRetrofit(sessionManager, ApiClient.getAuthApiService())
                                .create(PartnerPathApiService.class);

                Response<ApiResponse<PartnerPathResponseDto>> response =
                        api.getPartnerPath(sessionId, afterChunkIndex).execute();

                if (response.isSuccessful()
                        && response.body() != null
                        && response.body().isSuccess()) {

                    PartnerPathResponseDto dto = response.body().getData();

                    List<PartnerPathResult.Chunk> chunks = new ArrayList<>();
                    List<LatLng> legacyPoints = new ArrayList<>();
                    int lastChunkIndex = afterChunkIndex;

                    if (dto.getChunks() != null) {
                        for (PartnerPathResponseDto.PartnerChunkDto chunk : dto.getChunks()) {
                            List<LatLng> decoded = (chunk.getPolyline() != null
                                    && !chunk.getPolyline().isEmpty())
                                    ? PolyUtil.decode(chunk.getPolyline())
                                    : new ArrayList<LatLng>();

                            if (chunk.hasSequenceRange()) {
                                chunks.add(new PartnerPathResult.Chunk(
                                        chunk.getChunkIndex(),
                                        chunk.getStreamId(),
                                        chunk.getSeqStart(),
                                        chunk.getSeqEnd(),
                                        decoded));
                            } else {
                                // Legacy (v1) chunk: no sequence information, so it
                                // can only be appended in arrival order.
                                legacyPoints.addAll(decoded);
                            }

                            if (chunk.getChunkIndex() > lastChunkIndex) {
                                lastChunkIndex = chunk.getChunkIndex();
                            }
                        }
                    }

                    callback.onSuccess(new PartnerPathResult(
                            legacyPoints,
                            chunks,
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
