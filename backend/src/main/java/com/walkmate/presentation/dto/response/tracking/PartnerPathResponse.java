package com.walkmate.presentation.dto.response.tracking;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for GET /api/v1/tracking/partner-path.
 *
 * <p>Always returns HTTP 200 with {@code chunks = []} when the partner
 * has not yet started (PENDING) — the caller should never treat an empty
 * chunks list as an error.</p>
 *
 * <p>Each chunk may carry {@code stream_id}/{@code seq_start}/{@code seq_end}.
 * These are optional so an older client keeps working: a client that ignores
 * them simply falls back to arrival order. A client that understands them can
 * place a batch that arrived out of order at its real route position instead of
 * appending it to the end.</p>
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
    /**
     * One arrival-ordered chunk.
     *
     * @param chunkIndex arrival cursor — the pagination key, deliberately not the
     *                   route order
     * @param seqStart   first source sequence covered, or null for a legacy chunk
     * @param seqEnd     last source sequence covered, or null for a legacy chunk
     */
    public record PartnerChunk(
            @JsonProperty("chunk_index") int    chunkIndex,
            @JsonProperty("polyline")    String polyline,
            @JsonProperty("stream_id")   String streamId,
            @JsonProperty("seq_start")   Long   seqStart,
            @JsonProperty("seq_end")     Long   seqEnd
    ) {}
}
