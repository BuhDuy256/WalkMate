package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.tracking.PartnerPathResponseDto;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Retrofit interface for the incremental partner-path fetch endpoint.
 *
 * Contract: GET /api/v1/tracking/partner-path
 *   Params → session_id (required), after_chunk_index (default -1)
 *   200    → {@link ApiResponse}<{@link PartnerPathResponseDto}>
 *
 * The endpoint always returns HTTP 200 even when the partner has not
 * started yet — callers must inspect {@code partner_status} rather than
 * treating an empty {@code chunks} list as an error.
 */
public interface PartnerPathApiService {

    @GET("api/v1/tracking/partner-path")
    Call<ApiResponse<PartnerPathResponseDto>> getPartnerPath(
            @Query("session_id")        String sessionId,
            @Query("after_chunk_index") int    afterChunkIndex);
}
