package com.walkmate.data.datasource.remote.dto.request.walkintent;

import java.util.List;

public class CreateWalkIntentRequest {

    private String hotspotId;
    private String date;
    private float timeStart;
    private float timeEnd;
    private int ageMin;
    private int ageMax;
    private List<String> tags;

    public CreateWalkIntentRequest() {
        // Empty constructor for Gson
    }

    public CreateWalkIntentRequest(String hotspotId,
                                   String date,
                                   float timeStart,
                                   float timeEnd,
                                   int ageMin,
                                   int ageMax,
                                   List<String> tags) {
        this.hotspotId = hotspotId;
        this.date = date;
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        this.ageMin = ageMin;
        this.ageMax = ageMax;
        this.tags = tags;
    }

    public String getHotspotId() { return hotspotId; }
    public String getDate() { return date; }
    public float getTimeStart() { return timeStart; }
    public float getTimeEnd() { return timeEnd; }
    public int getAgeMin() { return ageMin; }
    public int getAgeMax() { return ageMax; }
    public List<String> getTags() { return tags; }
}
