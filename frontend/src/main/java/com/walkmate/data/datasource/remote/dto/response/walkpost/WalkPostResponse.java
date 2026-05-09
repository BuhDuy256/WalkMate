package com.walkmate.data.datasource.remote.dto.response.walkpost;

import com.google.gson.annotations.SerializedName;

public class WalkPostResponse {

    @SerializedName("post_id")           private String postId;
    @SerializedName("session_id")        private String sessionId;
    @SerializedName("author_id")         private String authorId;
    @SerializedName("author_name")       private String authorName;
    @SerializedName("author_avatar_url") private String authorAvatarUrl;
    @SerializedName("caption")           private String caption;
    @SerializedName("visibility")        private String visibility;
    @SerializedName("hotspot_name")      private String hotspotName;
    @SerializedName("distance_km")       private double distanceKm;
    @SerializedName("duration_seconds")  private long durationSeconds;
    @SerializedName("points_earned")     private int pointsEarned;
    @SerializedName("show_companion")    private boolean showCompanion;
    @SerializedName("show_route_map")    private boolean showRouteMap;
    @SerializedName("show_stats")        private boolean showStats;
    @SerializedName("companion_name")    private String companionName;
    @SerializedName("route_preview_url")    private String routePreviewUrl;
    @SerializedName("route_preview_status") private String routePreviewStatus;
    @SerializedName("created_at")           private String createdAt;

    public String getPostId()              { return postId; }
    public String getSessionId()           { return sessionId; }
    public String getAuthorId()            { return authorId; }
    public String getAuthorName()          { return authorName; }
    public String getAuthorAvatarUrl()     { return authorAvatarUrl; }
    public String getCaption()             { return caption; }
    public String getVisibility()          { return visibility; }
    public String getHotspotName()         { return hotspotName; }
    public double getDistanceKm()          { return distanceKm; }
    public long getDurationSeconds()       { return durationSeconds; }
    public int getPointsEarned()           { return pointsEarned; }
    public boolean isShowCompanion()       { return showCompanion; }
    public boolean isShowRouteMap()        { return showRouteMap; }
    public boolean isShowStats()           { return showStats; }
    public String getCompanionName()       { return companionName; }
    public String getRoutePreviewUrl()     { return routePreviewUrl; }
    public String getRoutePreviewStatus()  { return routePreviewStatus; }
    public String getCreatedAt()           { return createdAt; }
}
