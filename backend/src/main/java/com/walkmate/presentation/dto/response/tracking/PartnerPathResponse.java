package com.walkmate.presentation.dto.response.tracking;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for GET /api/v1/tracking/partner-path.
 *
 * <p>Always returns HTTP 200 with {@code chunks = []} when the partner
 * has not yet started (PENDING) — the caller should never treat an empty
 * chunks list as an error.
 */
public record PartnerPathResponse(

        @JsonProperty("chunks")
        List<PartnerChunk> chunks,

        /** Epoch-ms of the most recent chunk's {@code created_at}; 0 when no chunks exist. */
        @JsonProperty("last_chunk_at")
        long lastChunkCreatedAtMs,

        /** Partner's personal walk-session status: PENDING / ACTIVE / COMPLETED / NO_SHOW. */
        @JsonProperty("partner_status")
        String partnerStatus

) {
    public record PartnerChunk(
            @JsonProperty("chunk_index") int    chunkIndex,
            @JsonProperty("polyline")    String polyline
    ) {}
}
