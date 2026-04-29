package com.walkmate.domain.walkintent;

import java.util.List;

public class WalkIntent {
    private final String id;
    private final String hotspotId;
    private final String hotspotName;     // nullable; populated when API returns hotspot_name
    private final String userId;
    private final float timeStart;        // hour in 0.0–24.0, e.g. 16.5 = 16:30
    private final float timeEnd;
    private final int ageMin;
    private final int ageMax;
    private final String genderPreference; // "ANY" | "MALE" | "FEMALE" | null
    private final String status;          // "OPEN" | "MATCHING" | "CONSUMED" | "CANCELLED" | "EXPIRED"
    private final String createdAt;       // ISO-8601
    private final String walkDate;        // "yyyy-MM-dd" derived from timeWindowStart
    private final List<String> tags;
    private final String expiresAt;       // ISO-8601, nullable
    private final String description;     // nullable
    private final String proposalId;      // nullable

    public WalkIntent(String id, String hotspotId, String hotspotName, String userId,
                      float timeStart, float timeEnd,
                      int ageMin, int ageMax, String genderPreference,
                      String status, String createdAt, String walkDate,
                      List<String> tags,
                      String expiresAt, String description, String proposalId) {
        this.id = id;
        this.hotspotId = hotspotId;
        this.hotspotName = hotspotName;
        this.userId = userId;
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        this.ageMin = ageMin;
        this.ageMax = ageMax;
        this.genderPreference = genderPreference;
        this.status = status;
        this.createdAt = createdAt;
        this.walkDate = walkDate;
        this.tags = tags;
        this.expiresAt = expiresAt;
        this.description = description;
        this.proposalId = proposalId;
    }

    public String getId()               { return id; }
    public String getHotspotId()        { return hotspotId; }
    public String getHotspotName()      { return hotspotName; }
    public String getUserId()           { return userId; }
    public float  getTimeStart()        { return timeStart; }
    public float  getTimeEnd()          { return timeEnd; }
    public int    getAgeMin()           { return ageMin; }
    public int    getAgeMax()           { return ageMax; }
    public String getGenderPreference() { return genderPreference; }
    public String getStatus()           { return status; }
    public String getCreatedAt()        { return createdAt; }
    public String getWalkDate()         { return walkDate; }
    public List<String> getTags()       { return tags; }
    public String getExpiresAt()        { return expiresAt; }
    public String getDescription()      { return description; }
    public String getProposalId()       { return proposalId; }

    public boolean isOpen()     { return "OPEN".equals(status); }
    public boolean isMatching() { return "MATCHING".equals(status); }
}
