package com.walkmate.domain.walkintent;

import java.util.List;

public class WalkIntent {
    private final String id;
    private final String hotspotId;
    private final String userId;
    private final float timeStart;   // hour in 0.0–24.0, e.g. 16.5 = 16:30
    private final float timeEnd;
    private final int ageMin;
    private final int ageMax;
    private final String status;     // "OPEN" | "WAITLIST" | "MATCHED" | "EXPIRED"
    private final String createdAt;
    private final List<String> tags; // empty list until API contract includes it

    public WalkIntent(String id, String hotspotId, String userId,
                      float timeStart, float timeEnd,
                      int ageMin, int ageMax,
                      String status, String createdAt,
                      List<String> tags) {
        this.id = id;
        this.hotspotId = hotspotId;
        this.userId = userId;
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        this.ageMin = ageMin;
        this.ageMax = ageMax;
        this.status = status;
        this.createdAt = createdAt;
        this.tags = tags;
    }

    public String getId() { return id; }
    public String getHotspotId() { return hotspotId; }
    public String getUserId() { return userId; }
    public float getTimeStart() { return timeStart; }
    public float getTimeEnd() { return timeEnd; }
    public int getAgeMin() { return ageMin; }
    public int getAgeMax() { return ageMax; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public List<String> getTags() { return tags; }
}
