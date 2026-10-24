package com.walkmate.data.datasource.remote.dto.response.tracking;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * DTO for {@code GET /api/v1/tracking/partner-path}.
 * Wrapped in {@link com.walkmate.data.datasource.remote.dto.response.ApiResponse}.
 *
 * <p>v2 chunks additionally carry {@code stream_id}/{@code seq_start}/{@code seq_end}.
 * They are optional so the client keeps working against a v1 server during
 * rollout: when they are absent the chunk is treated as legacy arrival-order
 * data rather than being misinterpreted as route position.</p>
 */
public class PartnerPathResponseDto {

    @SerializedName("chunks")
    private List<PartnerChunkDto> chunks;

    @SerializedName("last_chunk_at")
    private long lastChunkCreatedAtMs;

    @SerializedName("partner_status")
    private String partnerStatus;

    public List<PartnerChunkDto> getChunks()  { return chunks; }
    public long getLastChunkCreatedAtMs()      { return lastChunkCreatedAtMs; }
    public String getPartnerStatus()           { return partnerStatus; }

    public static class PartnerChunkDto {

        /** Arrival cursor — the pagination key, NOT the route order. */
        @SerializedName("chunk_index")
        private int chunkIndex;

        @SerializedName("polyline")
        private String polyline;

        // ── v2 route identity (optional for backward compatibility) ───────────
        @SerializedName("stream_id")
        private String streamId;

        @SerializedName("seq_start")
        private Long seqStart;

        @SerializedName("seq_end")
        private Long seqEnd;

        public int getChunkIndex()  { return chunkIndex; }
        public String getPolyline() { return polyline; }
        public String getStreamId() { return streamId; }
        public long getSeqStart()   { return seqStart != null ? seqStart : 0L; }
        public long getSeqEnd()     { return seqEnd != null ? seqEnd : 0L; }

        /** True only when the server supplied a usable sequence range. */
        public boolean hasSequenceRange() {
            return seqStart != null && seqEnd != null && seqEnd >= seqStart;
        }
    }
}
