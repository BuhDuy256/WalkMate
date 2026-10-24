package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.request.tracking.SealStreamRequestDto;
import com.walkmate.data.datasource.remote.dto.request.tracking.TrackingBatchRequestDto;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.tracking.SealStreamResponseDto;
import com.walkmate.data.datasource.remote.dto.response.tracking.TrackingBatchResponseDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Retrofit interface for the GPS v2 sync contract.
 *
 * <pre>
 *   POST /api/v2/tracking/batches                     → commit-or-proof batch write
 *   POST /api/v2/tracking/streams/{streamId}/seal     → declare the final sequence
 *   POST /api/v2/tracking/streams/{streamId}/expire   → close an uncovered stream
 * </pre>
 *
 * <p>The v1 endpoint ({@link RoutePointSyncApiService}) is left in place for old
 * clients during rollout; this app no longer calls it.</p>
 *
 * <p>Retry policy is driven by the HTTP status, not by a generic catch-all:</p>
 * <ul>
 *   <li>timeout / no network / 429 / 5xx → keep the batch and retry with backoff</li>
 *   <li>409 {@code TRACKING_SEQUENCE_OVERLAP} / {@code TRACKING_BATCH_ID_REUSED}
 *       or 422 → BLOCKED for diagnosis; never silently drop the points</li>
 *   <li>410 {@code TRACKING_STREAM_EXPIRED} → stop attempts for this stream</li>
 *   <li>401 → authentication must be refreshed before any further upload</li>
 * </ul>
 */
public interface TrackingApiServiceV2 {

    @POST("api/v2/tracking/batches")
    Call<ApiResponse<TrackingBatchResponseDto>> pushBatch(
            @Body TrackingBatchRequestDto request);

    @POST("api/v2/tracking/streams/{streamId}/seal")
    Call<ApiResponse<SealStreamResponseDto>> sealStream(
            @Path("streamId") String streamId,
            @Body SealStreamRequestDto request);

    @POST("api/v2/tracking/streams/{streamId}/expire")
    Call<ApiResponse<SealStreamResponseDto>> expireStream(
            @Path("streamId") String streamId);
}
