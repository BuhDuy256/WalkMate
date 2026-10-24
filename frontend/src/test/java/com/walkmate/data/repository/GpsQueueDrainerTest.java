package com.walkmate.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.google.gson.Gson;
import com.walkmate.data.datasource.local.GpsQueueStore;
import com.walkmate.data.datasource.local.WalkMateDatabase;
import com.walkmate.data.datasource.local.dao.GpsBatchDao;
import com.walkmate.data.datasource.local.dao.RoutePointDao;
import com.walkmate.data.datasource.local.entity.GpsBatchEntity;
import com.walkmate.data.datasource.local.entity.RoutePointEntity;
import com.walkmate.data.datasource.remote.dto.request.tracking.SealStreamRequestDto;
import com.walkmate.data.datasource.remote.dto.request.tracking.TrackingBatchRequestDto;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.tracking.SealStreamResponseDto;
import com.walkmate.data.datasource.remote.dto.response.tracking.TrackingBatchResponseDto;
import com.walkmate.domain.tracking.GpsBatch;
import com.walkmate.domain.tracking.GpsBatchState;
import com.walkmate.domain.tracking.GpsSyncState;
import com.walkmate.domain.tracking.TrackingErrorCode;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Retry and status-classification policy of the queue drainer.
 *
 * <p>This is where a route is either preserved or lost. Classifying an HTTP
 * outcome decides the fate of real rows: a transient failure must leave the batch
 * retryable, a permanent refusal must park it without deleting anything, and a 200
 * must never mark points synced unless the ACK actually describes the batch that
 * was sent.</p>
 *
 * <p>Responses are built from JSON with Gson rather than by reflection, so the
 * {@code @SerializedName} mapping is exercised too — a mismatch between the client
 * DTOs and the server contract fails here instead of on a device.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = android.app.Application.class)
public class GpsQueueDrainerTest {

    private static final String SESSION = "11111111-1111-1111-1111-111111111111";
    private static final String USER    = "22222222-2222-2222-2222-222222222222";
    private static final long   BASE_TS = 1_792_824_000_000L;

    private static final Gson GSON = new Gson();

    private WalkMateDatabase db;
    private GpsQueueStore store;
    private GpsBatchDao batchDao;
    private RoutePointDao pointDao;
    private ScriptedTransport transport;
    private GpsQueueDrainer drainer;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, WalkMateDatabase.class)
                .allowMainThreadQueries()
                .build();
        store = new GpsQueueStore(db);
        batchDao = db.gpsBatchDao();
        pointDao = db.routePointDao();
        transport = new ScriptedTransport();
        // SessionManager is only used by the production transport, which is
        // bypassed here; passing null keeps the dependency honest.
        drainer = new GpsQueueDrainer(store, null, transport);
    }

    @After
    public void tearDown() {
        db.close();
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /** Seeds points and seals one batch; returns the sealed batch. */
    private GpsBatch seedSealedBatch(int points) {
        String streamId = store.ensureStream(SESSION, USER);
        for (int i = 0; i < points; i++) {
            store.saveAcceptedPoint(SESSION, USER, streamId,
                    10.0 + i * 0.001, 106.0 + i * 0.0005, BASE_TS + i * 1000L, 5f);
        }
        GpsBatch batch = store.sealNextBatch(SESSION, USER);
        assertNotNull(batch);
        return batch;
    }

    /** The same ACK the server returns for the batch that was just posted. */
    private static Response<ApiResponse<TrackingBatchResponseDto>> committedAck(
            TrackingBatchRequestDto sent) {
        String json = "{"
                + "\"batch_id\":\"" + sent.getBatchId() + "\","
                + "\"stream_id\":\"" + sent.getStreamId() + "\","
                + "\"seq_start\":" + sent.getSeqStart() + ","
                + "\"seq_end\":" + sent.getSeqEnd() + ","
                + "\"payload_hash\":\"" + sent.getPayloadHash() + "\","
                + "\"accepted_point_count\":" + sent.getPoints().size() + ","
                + "\"status\":\"COMMITTED\"}";
        return parsedEnvelope(json);
    }

    /** An ACK that is well-formed but describes a different payload. */
    private static Response<ApiResponse<TrackingBatchResponseDto>> ackWithWrongHash(
            TrackingBatchRequestDto sent) {
        String wrongHash = "deadbeef".repeat(8);
        String json = "{"
                + "\"batch_id\":\"" + sent.getBatchId() + "\","
                + "\"stream_id\":\"" + sent.getStreamId() + "\","
                + "\"seq_start\":" + sent.getSeqStart() + ","
                + "\"seq_end\":" + sent.getSeqEnd() + ","
                + "\"payload_hash\":\"" + wrongHash + "\","
                + "\"accepted_point_count\":" + sent.getPoints().size() + ","
                + "\"status\":\"COMMITTED\"}";
        return parsedEnvelope(json);
    }

    /** A non-2xx response carrying the backend's error envelope in the error body. */
    private static <T> Response<ApiResponse<T>> httpError(int httpStatus, String errorCode) {
        String json = "{\"success\":false,\"data\":null,"
                + "\"error\":{\"code\":\"" + errorCode + "\",\"message\":\""
                + errorCode + "\"}}";
        return Response.<ApiResponse<T>>error(httpStatus,
                ResponseBody.create(json, MediaType.get("application/json")));
    }

    /** Gson needs the concrete generic type; the fields are private by design. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Response<ApiResponse<TrackingBatchResponseDto>> parsedEnvelope(String json) {
        ApiResponse raw = GSON.fromJson("{\"success\":true,\"data\":" + json + "}", ApiResponse.class);
        String wrapped = GSON.toJson(raw);
        ApiResponse<TrackingBatchResponseDto> typed =
                GSON.fromJson(wrapped, new com.google.gson.reflect.TypeToken<
                        ApiResponse<TrackingBatchResponseDto>>() {}.getType());
        return Response.<ApiResponse<TrackingBatchResponseDto>>success(typed);
    }

    private GpsBatchEntity batchRow(String batchId) {
        return batchDao.find(batchId);
    }

    private void assertNothingSynced(String batchId) {
        for (RoutePointEntity p : pointDao.getPointsOfBatch(batchId)) {
            assertFalse("no point may be marked synced by an unverified/refused upload", p.isSynced);
        }
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    public void aVerifiedAckDrainsTheBatchAndMarksItsPointsSynced() {
        GpsBatch sealed = seedSealedBatch(50);
        transport.pushBehaviour = GpsQueueDrainerTest::committedAck;

        GpsQueueDrainer.DrainResult result = drainer.drain(SESSION, USER, false);

        assertEquals(1, result.uploaded);
        assertEquals(0, result.remaining);
        assertEquals(0, result.blocked);
        assertTrue(result.fullyDrained());

        assertEquals(GpsBatchState.ACKED, batchRow(sealed.getBatchId()).state);
        for (RoutePointEntity p : pointDao.getPointsOfBatch(sealed.getBatchId())) {
            assertTrue("points become synced only after the verified ACK", p.isSynced);
        }
    }

    @Test
    public void theAckPayloadIsWhatTheServerReceivesNotWhatIsUnsyncedNow() {
        GpsBatch sealed = seedSealedBatch(50);
        // Fixes keep arriving after the batch was sealed.
        store.saveAcceptedPoint(SESSION, USER, sealed.getStreamId(), 11.0, 107.0,
                BASE_TS + 900_000L, 5f);

        final TrackingBatchRequestDto[] captured = new TrackingBatchRequestDto[1];
        transport.pushBehaviour = sent -> {
            captured[0] = sent;
            return committedAck(sent);
        };

        drainer.drain(SESSION, USER, false);

        assertNotNull(captured[0]);
        assertEquals(sealed.getBatchId(), captured[0].getBatchId());
        assertEquals(sealed.getPayloadHash(), captured[0].getPayloadHash());
        assertEquals(50, captured[0].getPoints().size());
        assertEquals(1L, captured[0].getSeqStart());
        assertEquals(50L, captured[0].getSeqEnd());
    }

    // ── Transient failures keep the batch ─────────────────────────────────────

    @Test
    public void networkErrorLeavesTheBatchPendingForRetry() {
        GpsBatch sealed = seedSealedBatch(50);
        transport.pushBehaviour = sent -> {
            throw new IOException("connection reset");
        };

        GpsQueueDrainer.DrainResult result = drainer.drain(SESSION, USER, false);

        assertEquals(0, result.uploaded);
        assertEquals("a network failure must not lose the batch", 1, result.remaining);
        assertEquals(0, result.blocked);

        GpsBatchEntity row = batchRow(sealed.getBatchId());
        assertEquals(GpsBatchState.PENDING, row.state);
        assertEquals(1, row.retryCount);
        assertTrue("retry is deferred with backoff",
                row.nextAttemptAt > System.currentTimeMillis());
        assertNothingSynced(sealed.getBatchId());
        assertTrue("a backoff batch is not retried immediately",
                store.findDrainable(SESSION, USER).isEmpty());
    }

    @Test
    public void serverErrorIsTransient() {
        GpsBatch sealed = seedSealedBatch(1);
        transport.pushBehaviour = sent -> httpError(500, "INTERNAL_ERROR");

        GpsQueueDrainer.DrainResult result = drainer.drain(SESSION, USER, false);

        assertEquals(1, result.remaining);
        assertEquals(0, result.blocked);
        assertEquals(GpsBatchState.PENDING, batchRow(sealed.getBatchId()).state);
    }

    @Test
    public void unauthorizedIsTransientSoTheBatchIsNotBurnedOnAnExpiredSession() {
        GpsBatch sealed = seedSealedBatch(1);
        transport.pushBehaviour = sent -> httpError(401, "UNAUTHORIZED");

        GpsQueueDrainer.DrainResult result = drainer.drain(SESSION, USER, false);

        assertEquals("401 must be retried after re-auth, not blocked", 1, result.remaining);
        assertEquals(0, result.blocked);
        assertEquals(GpsBatchState.PENDING, batchRow(sealed.getBatchId()).state);
    }

    // ── Permanent refusals park the batch, never delete it ────────────────────

    @Test
    public void permanentRefusalBlocksTheBatchAndKeepsEveryPoint() {
        GpsBatch sealed = seedSealedBatch(50);
        transport.pushBehaviour = sent -> httpError(409, TrackingErrorCode.BATCH_ID_REUSED);

        GpsQueueDrainer.DrainResult result = drainer.drain(SESSION, USER, false);

        assertEquals(1, result.blocked);
        assertEquals(0, result.uploaded);
        assertEquals(GpsBatchState.BLOCKED, batchRow(sealed.getBatchId()).state);

        List<GpsBatch> blocked = store.findBlocked(SESSION, USER);
        assertEquals(1, blocked.size());
        assertEquals("the parked batch keeps all of its points",
                50, blocked.get(0).getPointCount());
        assertNothingSynced(sealed.getBatchId());
    }

    @Test
    public void everyPermanentRefusalCodeParksTheBatch() {
        String[] permanent = {
                TrackingErrorCode.SEQUENCE_OVERLAP,
                TrackingErrorCode.HASH_MISMATCH,
                TrackingErrorCode.STREAM_CLOSED,
                TrackingErrorCode.POINT_INVALID,
                TrackingErrorCode.BATCH_TOO_LARGE,
                TrackingErrorCode.RECORDED_AT_INVALID,
        };
        for (String code : permanent) {
            // Fresh state per code: a previous iteration's BLOCKED row would
            // otherwise still be counted.
            tearDown();
            setUp();

            GpsBatch sealed = seedSealedBatch(1);
            transport.pushBehaviour = sent -> httpError(409, code);

            drainer.drain(SESSION, USER, false);

            assertEquals(code + " must park the batch",
                    1, store.findBlocked(SESSION, USER).size());
            assertEquals(code + " must not be retried",
                    GpsBatchState.BLOCKED, batchRow(sealed.getBatchId()).state);
        }
    }

    @Test
    public void expiredStreamIsTerminalAndStopsFurtherAttempts() {
        GpsBatch sealed = seedSealedBatch(50);
        transport.pushBehaviour = sent -> httpError(410, TrackingErrorCode.STREAM_EXPIRED);

        GpsQueueDrainer.DrainResult result = drainer.drain(SESSION, USER, false);

        assertTrue("410 ends the stream", result.terminal);
        assertEquals(GpsSyncState.EXPIRED, store.syncState(SESSION, USER));
        // The batch is neither ACKed nor blocked — it simply cannot be uploaded.
        assertEquals(GpsBatchState.IN_FLIGHT, batchRow(sealed.getBatchId()).state);
        assertNothingSynced(sealed.getBatchId());
    }

    // ── A 200 that does not describe our batch is not an ACK ──────────────────

    @Test
    public void a200WithTheWrongHashIsNotTreatedAsAnAck() {
        GpsBatch sealed = seedSealedBatch(50);
        transport.pushBehaviour = GpsQueueDrainerTest::ackWithWrongHash;

        GpsQueueDrainer.DrainResult result = drainer.drain(SESSION, USER, false);

        assertEquals("a mismatched ACK must not count as uploaded", 0, result.uploaded);
        assertEquals(1, result.blocked);
        assertEquals(GpsBatchState.BLOCKED, batchRow(sealed.getBatchId()).state);
        assertNothingSynced(sealed.getBatchId());
    }

    // ── Multiple batches ──────────────────────────────────────────────────────

    @Test
    public void aFailureOnOneBatchDoesNotStopLaterBatches() {
        GpsBatch first = seedSealedBatch(50);
        GpsBatch second = seedSealedBatch(50);
        assertEquals(2, batchDao.findOutstanding(SESSION, USER).size());

        final int[] calls = {0};
        transport.pushBehaviour = sent -> {
            calls[0]++;
            if (sent.getBatchId().equals(first.getBatchId())) {
                throw new IOException("first batch fails");
            }
            return committedAck(sent);
        };

        GpsQueueDrainer.DrainResult result = drainer.drain(SESSION, USER, false);

        assertEquals("the second batch still uploads", 1, result.uploaded);
        assertEquals(1, result.remaining);
        assertEquals(2, calls[0]);
        assertEquals(GpsBatchState.ACKED, batchRow(second.getBatchId()).state);
        assertEquals(GpsBatchState.PENDING, batchRow(first.getBatchId()).state);
    }

    @Test
    public void stopOnFirstFailureStopsThePassAfterATransientError() {
        GpsBatch first = seedSealedBatch(50);
        seedSealedBatch(50);

        transport.pushBehaviour = sent -> {
            throw new IOException("network down");
        };

        GpsQueueDrainer.DrainResult result = drainer.drain(SESSION, USER, true);

        assertEquals(0, result.uploaded);
        assertEquals("remaining work is still reported", 2, result.remaining);
        assertEquals(GpsBatchState.PENDING, batchRow(first.getBatchId()).state);
    }

    // ── Identity guard ────────────────────────────────────────────────────────

    @Test
    public void nothingIsUploadedWithoutAUserId() {
        seedSealedBatch(10);

        GpsQueueDrainer.DrainResult result = drainer.drain(SESSION, "", false);

        assertEquals(0, result.uploaded);
        assertEquals("an empty identity must not drain another account's queue",
                0, transport.pushCount);
        assertEquals(1, store.countOutstanding(SESSION, USER));
    }

    // ── Scripted transport ────────────────────────────────────────────────────

    interface PushBehaviour {
        Response<ApiResponse<TrackingBatchResponseDto>> apply(TrackingBatchRequestDto sent)
                throws IOException;
    }

    static class ScriptedTransport implements GpsQueueDrainer.Transport {
        PushBehaviour pushBehaviour = sent -> {
            throw new IOException("no behaviour set");
        };
        int pushCount = 0;

        @Override
        public Response<ApiResponse<TrackingBatchResponseDto>> pushBatch(
                TrackingBatchRequestDto request) throws IOException {
            pushCount++;
            return pushBehaviour.apply(request);
        }

        @Override
        public Response<ApiResponse<SealStreamResponseDto>> sealStream(
                String streamId, SealStreamRequestDto request) {
            String json = "{\"streamId\":\"" + streamId
                    + "\",\"syncState\":\"SEALED\",\"finalSequence\":1}";
            return parsedSeal(json);
        }

        @Override
        public Response<ApiResponse<SealStreamResponseDto>> expireStream(String streamId) {
            String json = "{\"streamId\":\"" + streamId
                    + "\",\"syncState\":\"EXPIRED\",\"finalSequence\":1}";
            return parsedSeal(json);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Response<ApiResponse<SealStreamResponseDto>> parsedSeal(String json) {
            ApiResponse raw = GSON.fromJson(
                    "{\"success\":true,\"data\":" + json + "}", ApiResponse.class);
            ApiResponse<SealStreamResponseDto> typed = GSON.fromJson(GSON.toJson(raw),
                    new com.google.gson.reflect.TypeToken<
                            ApiResponse<SealStreamResponseDto>>() {}.getType());
            return Response.<ApiResponse<SealStreamResponseDto>>success(typed);
        }
    }
}
