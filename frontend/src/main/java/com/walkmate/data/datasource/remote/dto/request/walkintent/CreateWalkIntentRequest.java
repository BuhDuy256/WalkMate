package com.walkmate.data.datasource.remote.dto.request.walkintent;

import com.google.gson.annotations.SerializedName;

// Request body for POST /api/v1/intents
public class CreateWalkIntentRequest {

    @SerializedName("hotspot_id")
    private final String hotspotId;

    @SerializedName("user_id")
    private final String userId;

    @SerializedName("time_start")
    private final float timeStart;

    @SerializedName("time_end")
    private final float timeEnd;

    @SerializedName("age_min")
    private final int ageMin;

    @SerializedName("age_max")
    private final int ageMax;

    public CreateWalkIntentRequest(String hotspotId, String userId,
                                   float timeStart, float timeEnd,
                                   int ageMin, int ageMax) {
        this.hotspotId = hotspotId;
        this.userId = userId;
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        this.ageMin = ageMin;
        this.ageMax = ageMax;
    }

    public String getHotspotId() { return hotspotId; }
    public String getUserId() { return userId; }
    public float getTimeStart() { return timeStart; }
    public float getTimeEnd() { return timeEnd; }
    public int getAgeMin() { return ageMin; }
    public int getAgeMax() { return ageMax; }
}
