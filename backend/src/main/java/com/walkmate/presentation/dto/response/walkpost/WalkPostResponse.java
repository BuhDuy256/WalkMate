package com.walkmate.presentation.dto.response.walkpost;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WalkPostResponse(
        @JsonProperty("post_id")           String postId,
        @JsonProperty("session_id")        String sessionId,
        @JsonProperty("author_id")         String authorId,
        @JsonProperty("author_name")       String authorName,
        @JsonProperty("author_avatar_url") String authorAvatarUrl,
        @JsonProperty("caption")           String caption,
        @JsonProperty("visibility")        String visibility,
        @JsonProperty("hotspot_name")      String hotspotName,
        @JsonProperty("distance_km")       double distanceKm,
        @JsonProperty("duration_seconds")  long durationSeconds,
        @JsonProperty("points_earned")     int pointsEarned,
        @JsonProperty("show_companion")    boolean showCompanion,
        @JsonProperty("show_route_map")    boolean showRouteMap,
        @JsonProperty("show_stats")        boolean showStats,
        @JsonProperty("companion_name")    String companionName,
        @JsonProperty("route_preview_url") String routePreviewUrl,
        @JsonProperty("created_at")        String createdAt
) {}
