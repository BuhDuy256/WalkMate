package com.walkmate.data.repository;

import android.util.Log;

import com.walkmate.core.util.ErrorParser;
import com.walkmate.data.datasource.local.GpsQueueStore;
import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.api.TrackingApiServiceV2;
import com.walkmate.data.datasource.remote.dto.request.tracking.SealStreamRequestDto;
import com.walkmate.data.datasource.remote.dto.request.tracking.TrackingBatchRequestDto;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.tracking.SealStreamResponseDto;
import com.walkmate.data.datasource.remote.dto.response.tracking.TrackingBatchResponseDto;
import com.walkmate.domain.tracking.GpsBatch;
import com.walkmate.domain.tracking.GpsPayloadHasher;
import com.walkmate.domain.tracking.GpsSyncState;
import com.walkmate.domain.tracking.TrackingErrorCode;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Drains the persistent GPS queue against the v2 endpoints.
 *
 * <p>This is the only place that performs GPS network I/O. It is deliberately
 * stateless and synchronous: the durable state lives in Room, so a crash between
 * two batches loses nothing — the next drain re-reads the same rows and resends
 * the identical payloads.</p>
 *
 * <p>Each attempt follows the same shape:</p>
 * <ol>
 *   <li>re-read the frozen batch from Room,</li>
 *   <li>take a lease (a second worker silently backs off),</li>
 *   <li>POST the payload,</li>
 *   <li>on 200, verify batch id + stream + range + hash before marking ACKED,</li>
 *   <li>on a transient failure, release with backoff; on a permanent refusal,
 *       park it as BLOCKED.</li>
 * </ol>
 */
public class GpsQueueDrainer {

    private static final String TAG = "GpsQueueDrainer";

    /** Result of one drain pass. */
    public static final class DrainResult {
        public final int uploaded;
        public final int remaining;
        public final int blocked;
        public final boolean terminal;

        DrainResult(int uploaded, int remaining, int blocked, boolean terminal) {
            this.uploaded = uploaded;
            this.remaining = remaining;
            this.blocked = blocked;
            this.terminal = terminal;
        }

        public boolean fullyDrained() {
            return remaining == 0 && blocked == 0;
        }
    }

    private final GpsQueueStore store;
    private final SessionManager sessionManager;

    public GpsQueueDrainer(GpsQueueStore store, SessionManager sessionManager) {
        this.store = store;
        this.sessionManager = sessionManager;
    }

    private TrackingApiServiceV2 api() {
        return ApiClient.buildAuthenticatedRetrofit(
                        sessionManager, ApiClient.getAuthApiService())
                .create(TrackingApiServiceV2.class);
    }

    /**
     * Uploads every drainable batch for this session, subject to each batch's
     * backoff window.
     *
     * @param stopOnFirstFailure when false the pass continues past a transient
     *                           failure; the first failure keeps its backoff and
     *                           the rest are still attempted (they are independent
     *                           ranges, so one bad batch need not block others)
     */
    public DrainResult drain(String sessionId, String userId, boolean stopOnFirstFailure) {
        if (userId == null || userId.isEmpty()) {
            // Never upload under an unknown identity — that would attribute the
            // route to the wrong account.
            return new DrainResult(0, 0, 0, true);
        }

        store.ensureStream(sessionId, userId);

        int uploaded = 0;
        boolean terminal = false;

        List<GpsBatch> drainable = store.findDrainable(sessionId, userId);
        for (GpsBatch batch : drainable) {
            if (!store.claim(batch.getBatchId())) {
                Log.d(TAG, "Batch " + batch.getBatchId() + " already leased — skipping");
                continue;
            }

            BatchOutcome outcome = pushOne(batch);
            switch (outcome) {
                case ACKED:
                    uploaded++;
                    break;
                case TRANSIENT:
                    store.release(batch);
                    if (stopOnFirstFailure) {
                        return new DrainResult(uploaded,
                                store.countOutstanding(sessionId, userId),
                                store.findBlocked(sessionId, userId).size(), false);
                    }
                    break;
                case BLOCKED:
                    break;
                case EXPIRED:
                    terminal = true;
                    store.setSyncState(sessionId, userId, GpsSyncState.EXPIRED);
                    return new DrainResult(uploaded,
                            store.countOutstanding(sessionId, userId),
                            store.findBlocked(sessionId, userId).size(), true);
                default:
                    break;
            }

            if (outcome == BatchOutcome.EXPIRED) {
                break;
            }
        }

        return new DrainResult(uploaded,
                store.countOutstanding(sessionId, userId),
                store.findBlocked(sessionId, userId).size(),
                terminal);
    }

    private enum BatchOutcome { ACKED, TRANSIENT, BLOCKED, EXPIRED }

    private BatchOutcome pushOne(GpsBatch batch) {
        TrackingBatchRequestDto request = toRequest(batch);
        try {
            Response<ApiResponse<TrackingBatchResponseDto>> response =
                    api().pushBatch(request).execute();

            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                TrackingBatchResponseDto ack = response.body().getData();
                boolean verified = store.ackVerified(batch,
                        ack.getStreamId(), ack.getSeqStart(), ack.getSeqEnd(),
                        ack.getPayloadHash());
                if (!verified) {
                    // The server says it committed something that is not what we
                    // sent. Do not mark points synced; park it for investigation.
                    store.block(batch.getBatchId(), TrackingErrorCode.BATCH_ID_REUSED);
                    return BatchOutcome.BLOCKED;
                }
                Log.d(TAG, "Batch " + batch.getBatchId() + " ACKed ["
                        + batch.getSeqStart() + "," + batch.getSeqEnd() + "]");
                return BatchOutcome.ACKED;
            }

            ApiError error = ErrorParser.extractApiError(response, TrackingErrorCode.SYNC_FAILED);
            String code = error.getCode();
            Log.w(TAG, "Batch " + batch.getBatchId() + " refused: " + code);

            if (TrackingErrorCode.STREAM_EXPIRED.equals(code)) {
                return BatchOutcome.EXPIRED;
            }
            if (response.code() == 401) {
                // Authentication must be refreshed before any further upload.
                return BatchOutcome.TRANSIENT;
            }
            if (TrackingErrorCode.isPermanent(code) || response.code() == 422
                    || response.code() == 413 || response.code() == 403) {
                store.block(batch.getBatchId(), code);
                return BatchOutcome.BLOCKED;
            }
            return BatchOutcome.TRANSIENT;

        } catch (Exception e) {
            Log.e(TAG, "Batch " + batch.getBatchId() + " network error", e);
            return BatchOutcome.TRANSIENT;
        }
    }

    private static TrackingBatchRequestDto toRequest(GpsBatch batch) {
        List<TrackingBatchRequestDto.PointDto> points =
                new ArrayList<>(batch.getPoints().size());
        for (GpsPayloadHasher.Point p : batch.getPoints()) {
            points.add(new TrackingBatchRequestDto.PointDto(
                    p.sequence, p.recordedAt, p.lat, p.lng, p.accuracy));
        }
        return new TrackingBatchRequestDto(
                batch.getSessionId(), batch.getStreamId(), batch.getBatchId(),
                batch.getSeqStart(), batch.getSeqEnd(), batch.getPayloadHash(), points);
    }

    /**
     * Declares the final sequence and, when the backlog is already uploaded,
     * lets the server converge the stream to COMPLETE in the same round-trip.
     *
     * @return the server-side sync state, or null when the call failed
     */
    public String seal(String sessionId, String userId) {
        String streamId = store.ensureStream(sessionId, userId);
        Long finalSeq = store.finalSequence(sessionId, userId);
        if (finalSeq == null) {
            finalSeq = store.lastAllocatedSequence(sessionId, userId);
        }

        try {
            Response<ApiResponse<SealStreamResponseDto>> response =
                    api().sealStream(streamId, new SealStreamRequestDto(finalSeq, null)).execute();

            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                SealStreamResponseDto data = response.body().getData();
                store.setSyncState(sessionId, userId, data.getSyncState());
                Log.d(TAG, "Sealed stream " + streamId + " final=" + finalSeq
                        + " state=" + data.getSyncState());
                return data.getSyncState();
            }

            ApiError error = ErrorParser.extractApiError(response, TrackingErrorCode.SYNC_FAILED);
            Log.w(TAG, "Seal refused: " + error.getCode());
        } catch (Exception e) {
            Log.e(TAG, "Seal network error", e);
        }
        return null;
    }

    /**
     * Closes an uncovered stream after its deadline. Safe to call speculatively:
     * the server leaves a stream that is still within its window untouched.
     */
    public String expire(String sessionId, String userId) {
        String streamId = store.ensureStream(sessionId, userId);
        try {
            Call<ApiResponse<SealStreamResponseDto>> call = api().expireStream(streamId);
            Response<ApiResponse<SealStreamResponseDto>> response = call.execute();
            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                String state = response.body().getData().getSyncState();
                store.setSyncState(sessionId, userId, state);
                return state;
            }
        } catch (Exception e) {
            Log.e(TAG, "Expire network error", e);
        }
        return null;
    }
}
