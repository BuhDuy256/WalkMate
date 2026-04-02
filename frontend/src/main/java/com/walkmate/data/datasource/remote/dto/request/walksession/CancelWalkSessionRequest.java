package com.walkmate.data.datasource.remote.dto.request.walksession;

import com.google.gson.annotations.SerializedName;

public class CancelWalkSessionRequest {

    @SerializedName("reason")
    private final String reason;

    public CancelWalkSessionRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() { return reason; }
}
