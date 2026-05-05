package com.walkmate.data.datasource.remote.dto.request.report;

import com.google.gson.annotations.SerializedName;

public class ResolveReportRequest {
    @SerializedName("resolution")     private final String resolution;
    @SerializedName("resolutionNote") private final String resolutionNote;

    public ResolveReportRequest(String resolution, String resolutionNote) {
        this.resolution     = resolution;
        this.resolutionNote = resolutionNote;
    }
}
