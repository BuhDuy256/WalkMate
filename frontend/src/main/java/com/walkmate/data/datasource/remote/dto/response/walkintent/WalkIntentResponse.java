package com.walkmate.data.datasource.remote.dto.response.walkintent;

import com.google.gson.annotations.SerializedName;

// Response payload for POST /api/v1/intents and GET /api/v1/intents/{id}/match
// (inside ApiResponse<WalkIntentResponse>)
public class WalkIntentResponse {

    @SerializedName("id")
    private String id;

    @SerializedName("hotspot_id")
    private String hotspotId;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("time_window_start")
    private String timeWindowStart;

    @SerializedName("time_window_end")
    private String timeWindowEnd;

    @SerializedName("age_min")
    private int ageMin;

    @SerializedName("age_max")
    private int ageMax;

    @SerializedName("status")
    private String status;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("expires_at")
    private String expiresAt;

    public String getId() { return id; }
    public String getHotspotId() { return hotspotId; }
    public String getUserId() { return userId; }
    public String getTimeWindowStart() { return timeWindowStart; }
    public String getTimeWindowEnd() { return timeWindowEnd; }
    public int getAgeMin() { return ageMin; }
    public int getAgeMax() { return ageMax; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public String getExpiresAt() { return expiresAt; }
}
