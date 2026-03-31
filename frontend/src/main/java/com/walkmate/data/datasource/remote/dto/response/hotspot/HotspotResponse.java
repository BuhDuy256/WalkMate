package com.walkmate.data.datasource.remote.dto.response.hotspot;

import com.google.gson.annotations.SerializedName;

// Response payload for GET /api/v1/hotspots (inside ApiResponse<List<HotspotResponse>>)
public class HotspotResponse {

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("lat")
    private double lat;

    @SerializedName("lng")
    private double lng;

    @SerializedName(value = "activeWalkerCount", alternate = {"active_walker_count", "active_intent_count"})
    private int activeWalkerCount;

    public String getId() { return id; }
    public String getName() { return name; }
    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public int getActiveWalkerCount() { return activeWalkerCount; }
}
