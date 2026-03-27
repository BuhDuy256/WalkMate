package com.walkmate.data.datasource.remote.dto.request.walkintent;

import com.google.gson.annotations.SerializedName;

// Request body for POST /api/v1/intents
public class CreateWalkIntentRequest {

    @SerializedName("hotspot_id")
    private final String hotspotId;

    @SerializedName("time_window_start")
    private final String timeWindowStart;

    @SerializedName("time_window_end")
    private final String timeWindowEnd;

    @SerializedName("age_min")
    private final int ageMin;

    @SerializedName("age_max")
    private final int ageMax;

    public CreateWalkIntentRequest(String hotspotId,
                                   String timeWindowStart, String timeWindowEnd,
                                   int ageMin, int ageMax) {
        this.hotspotId = hotspotId;
        this.timeWindowStart = timeWindowStart;
        this.timeWindowEnd = timeWindowEnd;
        this.ageMin = ageMin;
        this.ageMax = ageMax;
    }

    public String getHotspotId() { return hotspotId; }
    public String getTimeWindowStart() { return timeWindowStart; }
    public String getTimeWindowEnd() { return timeWindowEnd; }
    public int getAgeMin() { return ageMin; }
    public int getAgeMax() { return ageMax; }
}
