package com.walkmate.data.datasource.remote.dto.response.tracking;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * DTO for the response of GET /api/v1/tracking/partner-path.
 * Wrapped in {@link com.walkmate.data.datasource.remote.dto.response.ApiResponse}.
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

        @SerializedName("chunk_index")
        private int chunkIndex;

        @SerializedName("polyline")
        private String polyline;

        public int getChunkIndex()  { return chunkIndex; }
        public String getPolyline() { return polyline; }
    }
}
