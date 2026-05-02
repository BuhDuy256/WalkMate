package com.walkmate.data.datasource.remote.dto.request.walksession;

import com.google.gson.annotations.SerializedName;

public class ReportSessionRequest {

    @SerializedName("reportedUserId")
    private String reportedUserId;

    @SerializedName("reason")
    private String reason;

    @SerializedName("evidenceUrl")
    private String evidenceUrl;  // nullable

    public ReportSessionRequest(String reportedUserId, String reason, String evidenceUrl) {
        this.reportedUserId = reportedUserId;
        this.reason = reason;
        this.evidenceUrl = evidenceUrl;
    }

    public String getReportedUserId() { return reportedUserId; }
    public String getReason() { return reason; }
    public String getEvidenceUrl() { return evidenceUrl; }
}
