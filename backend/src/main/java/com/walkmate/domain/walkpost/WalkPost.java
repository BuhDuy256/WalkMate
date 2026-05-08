package com.walkmate.domain.walkpost;

import com.walkmate.domain.shared.exception.DomainException;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class WalkPost {

    private String         postId;
    private String         sessionId;
    private String         authorId;
    private String         caption;
    private PostVisibility visibility;
    private boolean        showCompanion;
    private boolean        showRouteMap;
    private boolean        showStats;
    private double         distanceKm;
    private long           durationSeconds;
    private int            pointsEarned;
    private String         routePreviewUrl;
    private Instant        createdAt;
    private Instant        updatedAt;

    /** Transient — populated by repository JOIN with user_profile. */
    private String authorName;
    /** Transient — populated by repository JOIN with user_profile. */
    private String authorAvatarUrl;
    /** Transient — populated by repository JOIN with walk_session + hotspot. */
    private String hotspotName;
    /** Transient — populated by repository JOIN with walk_session + user_profile. */
    private String companionName;

    protected WalkPost() {}

    /** Rehydration constructor — used by the repository when loading from DB (with JOINs). */
    public WalkPost(String postId, String sessionId, String authorId,
                    String caption, PostVisibility visibility,
                    boolean showCompanion, boolean showRouteMap, boolean showStats,
                    double distanceKm, long durationSeconds, int pointsEarned,
                    String routePreviewUrl, Instant createdAt, Instant updatedAt,
                    String authorName, String authorAvatarUrl,
                    String hotspotName, String companionName) {
        this.postId          = postId;
        this.sessionId       = sessionId;
        this.authorId        = authorId;
        this.caption         = caption;
        this.visibility      = visibility;
        this.showCompanion   = showCompanion;
        this.showRouteMap    = showRouteMap;
        this.showStats       = showStats;
        this.distanceKm      = distanceKm;
        this.durationSeconds = durationSeconds;
        this.pointsEarned    = pointsEarned;
        this.routePreviewUrl = routePreviewUrl;
        this.createdAt       = createdAt;
        this.updatedAt       = updatedAt;
        this.authorName      = authorName;
        this.authorAvatarUrl = authorAvatarUrl;
        this.hotspotName     = hotspotName;
        this.companionName   = companionName;
    }

    private WalkPost(String sessionId, String authorId,
                     String caption, PostVisibility visibility,
                     boolean showCompanion, boolean showRouteMap, boolean showStats,
                     double distanceKm, long durationSeconds) {
        String trimmed = caption != null ? caption.trim() : null;
        if (trimmed != null && trimmed.length() > 150) {
            throw new DomainException(WalkPostErrorCode.WALK_POST_CAPTION_TOO_LONG);
        }
        this.postId          = UUID.randomUUID().toString();
        this.sessionId       = sessionId;
        this.authorId        = authorId;
        this.caption         = (trimmed == null || trimmed.isEmpty()) ? null : trimmed;
        this.visibility      = visibility;
        this.showCompanion   = showCompanion;
        this.showRouteMap    = showRouteMap;
        this.showStats       = showStats;
        this.distanceKm      = distanceKm;
        this.durationSeconds = durationSeconds;
        this.pointsEarned    = 0;
        this.routePreviewUrl = null;
        this.createdAt       = Instant.now();
        this.updatedAt       = this.createdAt;
    }

    public static WalkPost create(String sessionId, String authorId,
                                  String caption, PostVisibility visibility,
                                  boolean showCompanion, boolean showRouteMap, boolean showStats,
                                  double distanceKm, long durationSeconds) {
        return new WalkPost(sessionId, authorId, caption, visibility,
                showCompanion, showRouteMap, showStats, distanceKm, durationSeconds);
    }

    public void changeVisibility(PostVisibility newVisibility, String requesterId) {
        if (!this.authorId.equals(requesterId)) {
            throw new DomainException(WalkPostErrorCode.WALK_POST_FORBIDDEN);
        }
        this.visibility = newVisibility;
        this.updatedAt  = Instant.now();
    }
}
