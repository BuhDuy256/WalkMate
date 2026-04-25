package com.walkmate.data.datasource.remote.dto.request.walkintent;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class CreateWalkIntentRequest {

    @SerializedName("hotspot_id")
    private String hotspotId;

    private String date;

    @SerializedName("time_start")
    private float timeStart;

    @SerializedName("time_end")
    private float timeEnd;

    @SerializedName("age_min")
    private int ageMin;

    @SerializedName("age_max")
    private int ageMax;

    @SerializedName("preferred_gender")
    private String preferredGender;

    private List<String> tags;

    @SerializedName("is_private")
    private boolean isPrivate;

    @SerializedName("invited_friend_id")
    private String invitedFriendId;

    @SerializedName("description")
    private String description;

    public CreateWalkIntentRequest() {
        // Empty constructor for Gson
    }

    public CreateWalkIntentRequest(String hotspotId,
                                   String date,
                                   float timeStart,
                                   float timeEnd,
                                   int ageMin,
                                   int ageMax,
                                   String preferredGender,
                                   List<String> tags,
                                   boolean isPrivate,
                                   String invitedFriendId,
                                   String description) {
        this.hotspotId       = hotspotId;
        this.date            = date;
        this.timeStart       = timeStart;
        this.timeEnd         = timeEnd;
        this.ageMin          = ageMin;
        this.ageMax          = ageMax;
        this.preferredGender = preferredGender;
        this.tags            = tags;
        this.isPrivate       = isPrivate;
        this.invitedFriendId = invitedFriendId;
        this.description     = description;
    }

    public String getHotspotId()      { return hotspotId; }
    public String getDate()           { return date; }
    public float getTimeStart()       { return timeStart; }
    public float getTimeEnd()         { return timeEnd; }
    public int getAgeMin()            { return ageMin; }
    public int getAgeMax()            { return ageMax; }
    public String getPreferredGender(){ return preferredGender; }
    public List<String> getTags()     { return tags; }
    public boolean isPrivate()        { return isPrivate; }
    public String getInvitedFriendId(){ return invitedFriendId; }
    public String getDescription()    { return description; }
}
