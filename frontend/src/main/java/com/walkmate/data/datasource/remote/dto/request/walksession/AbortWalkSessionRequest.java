package com.walkmate.data.datasource.remote.dto.request.walksession;

import com.google.gson.annotations.SerializedName;

public class AbortWalkSessionRequest {

    @SerializedName("reason")
    private final String reason;  // AbortReason enum name (e.g. "SAFETY_CONCERN")

    public AbortWalkSessionRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() { return reason; }
}
