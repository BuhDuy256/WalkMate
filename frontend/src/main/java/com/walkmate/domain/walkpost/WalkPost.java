package com.walkmate.domain.walkpost;

public class WalkPost {

    private final String postId;
    private final String sessionId;
    private final String authorId;
    private final String authorName;
    private final String authorAvatarUrl;
    private final String caption;
    private final PostVisibility visibility;
    private final String hotspotName;
    private final double distanceKm;
    private final long durationSeconds;
    private final int pointsEarned;
    private final boolean showCompanion;
    private final boolean showRouteMap;
    private final boolean showStats;
    private final String companionName;
    private final String routePreviewUrl;
    /** PENDING | READY | NO_ROUTE | FAILED — matches backend RoutePreviewStatus enum names. */
    private final String routePreviewStatus;
    private final String createdAt;

    public WalkPost(String postId, String sessionId, String authorId,
                    String authorName, String authorAvatarUrl,
                    String caption, PostVisibility visibility,
                    String hotspotName,
                    double distanceKm, long durationSeconds, int pointsEarned,
                    boolean showCompanion, boolean showRouteMap, boolean showStats,
                    String companionName, String routePreviewUrl, String routePreviewStatus,
                    String createdAt) {
        this.postId             = postId;
        this.sessionId          = sessionId;
        this.authorId           = authorId;
        this.authorName         = authorName;
        this.authorAvatarUrl    = authorAvatarUrl;
        this.caption            = caption;
        this.visibility         = visibility;
        this.hotspotName        = hotspotName;
        this.distanceKm         = distanceKm;
        this.durationSeconds    = durationSeconds;
        this.pointsEarned       = pointsEarned;
        this.showCompanion      = showCompanion;
        this.showRouteMap       = showRouteMap;
        this.showStats          = showStats;
        this.companionName      = companionName;
        this.routePreviewUrl    = routePreviewUrl;
        this.routePreviewStatus = routePreviewStatus != null ? routePreviewStatus : "PENDING";
        this.createdAt          = createdAt;
    }

    public String getPostId()             { return postId; }
    public String getSessionId()          { return sessionId; }
    public String getAuthorId()           { return authorId; }
    public String getAuthorName()         { return authorName; }
    public String getAuthorAvatarUrl()    { return authorAvatarUrl; }
    public String getCaption()            { return caption; }
    public PostVisibility getVisibility() { return visibility; }
    public String getHotspotName()        { return hotspotName; }
    public double getDistanceKm()         { return distanceKm; }
    public long getDurationSeconds()      { return durationSeconds; }
    public int getPointsEarned()          { return pointsEarned; }
    public boolean isShowCompanion()      { return showCompanion; }
    public boolean isShowRouteMap()       { return showRouteMap; }
    public boolean isShowStats()          { return showStats; }
    public String getCompanionName()      { return companionName; }
    public String getRoutePreviewUrl()    { return routePreviewUrl; }
    public String getRoutePreviewStatus() { return routePreviewStatus; }
    public String getCreatedAt()          { return createdAt; }
}
