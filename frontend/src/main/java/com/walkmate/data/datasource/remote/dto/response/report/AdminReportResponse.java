package com.walkmate.data.datasource.remote.dto.response.report;

import com.google.gson.annotations.SerializedName;

public class AdminReportResponse {
    @SerializedName("reportId")          public String reportId;
    @SerializedName("sessionId")         public String sessionId;
    @SerializedName("reporterId")        public String reporterId;
    @SerializedName("reporterName")      public String reporterName;
    @SerializedName("reportedUserId")    public String reportedUserId;
    @SerializedName("reportedUserName")  public String reportedUserName;
    @SerializedName("reason")            public String reason;
    @SerializedName("evidenceUrl")       public String evidenceUrl;
    @SerializedName("status")            public String status;
    @SerializedName("appliedTrustDelta") public int    appliedTrustDelta;
    @SerializedName("createdAt")         public String createdAt;
    @SerializedName("resolvedBy")        public String resolvedBy;
    @SerializedName("resolvedAt")        public String resolvedAt;
    @SerializedName("resolutionNote")    public String resolutionNote;
}
