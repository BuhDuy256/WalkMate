package com.walkmate.infrastructure.repository.walkpost;

import com.walkmate.domain.walkpost.PostVisibility;
import com.walkmate.domain.walkpost.RoutePreviewStatus;
import com.walkmate.domain.walkpost.WalkPost;
import com.walkmate.domain.walkpost.WalkPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class WalkPostJdbcRepository implements WalkPostRepository {

    private final JdbcClient jdbcClient;

    @Override
    public WalkPost save(WalkPost post) {
        jdbcClient.sql("""
                INSERT INTO walk_post (
                    post_id, session_id, author_id,
                    caption, visibility,
                    show_companion, show_route_map, show_stats,
                    distance_km, duration_seconds, points_earned,
                    route_preview_url, route_preview_path, route_preview_status,
                    created_at, updated_at
                )
                VALUES (
                    :postId, :sessionId, :authorId,
                    :caption, :visibility,
                    :showCompanion, :showRouteMap, :showStats,
                    :distanceKm, :durationSeconds, :pointsEarned,
                    :routePreviewUrl, :routePreviewPath, :routePreviewStatus,
                    :createdAt, :updatedAt
                )
                ON CONFLICT (post_id) DO UPDATE SET
                    visibility          = EXCLUDED.visibility,
                    updated_at          = EXCLUDED.updated_at
                """)
                .param("postId",             UUID.fromString(post.getPostId()))
                .param("sessionId",          UUID.fromString(post.getSessionId()))
                .param("authorId",           UUID.fromString(post.getAuthorId()))
                .param("caption",            post.getCaption())
                .param("visibility",         post.getVisibility().name())
                .param("showCompanion",      post.isShowCompanion())
                .param("showRouteMap",       post.isShowRouteMap())
                .param("showStats",          post.isShowStats())
                .param("distanceKm",         post.getDistanceKm())
                .param("durationSeconds",    post.getDurationSeconds())
                .param("pointsEarned",       post.getPointsEarned())
                .param("routePreviewUrl",    post.getRoutePreviewUrl())
                .param("routePreviewPath",   post.getRoutePreviewPath())
                .param("routePreviewStatus", post.getRoutePreviewStatus().name())
                .param("createdAt",          Timestamp.from(post.getCreatedAt()))
                .param("updatedAt",          Timestamp.from(post.getUpdatedAt()))
                .update();
        return findById(post.getPostId()).orElse(post);
    }

    @Override
    public void updateRoutePreview(String postId, String routePreviewUrl,
                                   String routePreviewPath, String routePreviewStatus) {
        jdbcClient.sql("""
                UPDATE walk_post
                SET route_preview_url    = :routePreviewUrl,
                    route_preview_path   = :routePreviewPath,
                    route_preview_status = :routePreviewStatus,
                    updated_at           = NOW()
                WHERE post_id = :postId
                """)
                .param("postId",             UUID.fromString(postId))
                .param("routePreviewUrl",    routePreviewUrl)
                .param("routePreviewPath",   routePreviewPath)
                .param("routePreviewStatus", routePreviewStatus)
                .update();
    }

    @Override
    public Optional<WalkPost> findById(String postId) {
        return jdbcClient.sql(selectAllWithJoins() + "WHERE wp.post_id = :postId")
                .param("postId", UUID.fromString(postId))
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Override
    public boolean existsBySessionAndAuthor(String sessionId, String authorId) {
        Integer count = jdbcClient.sql("""
                SELECT COUNT(*) FROM walk_post
                WHERE session_id = :sessionId AND author_id = :authorId
                """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("authorId",  UUID.fromString(authorId))
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public Optional<String> findPostIdBySessionAndAuthor(String sessionId, String authorId) {
        return jdbcClient.sql("""
                SELECT post_id::text FROM walk_post
                WHERE session_id = :sessionId AND author_id = :authorId
                """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("authorId",  UUID.fromString(authorId))
                .query(String.class)
                .optional();
    }

    @Override
    public List<WalkPost> findByAuthor(String authorId) {
        return jdbcClient.sql(selectAllWithJoins() + "WHERE wp.author_id = :authorId ORDER BY wp.created_at DESC")
                .param("authorId", UUID.fromString(authorId))
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    @Override
    public List<WalkPost> findVisibleByAuthor(String authorId, boolean isFriend) {
        String visibilityFilter = isFriend
                ? "AND wp.visibility IN ('PUBLIC', 'FRIENDS')"
                : "AND wp.visibility = 'PUBLIC'";
        return jdbcClient.sql(selectAllWithJoins()
                + "WHERE wp.author_id = :authorId " + visibilityFilter + " ORDER BY wp.created_at DESC")
                .param("authorId", UUID.fromString(authorId))
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    @Override
    public Map<String, Boolean> findExistenceMapBySessionIdsAndAuthor(Set<String> sessionIds, String authorId) {
        if (sessionIds == null || sessionIds.isEmpty()) return Collections.emptyMap();
        UUID[] ids = sessionIds.stream().map(UUID::fromString).toArray(UUID[]::new);
        Map<String, Boolean> result = new HashMap<>();
        jdbcClient.sql("""
                SELECT session_id::text FROM walk_post
                WHERE session_id = ANY(:sessionIds) AND author_id = :authorId
                """)
                .param("sessionIds", ids)
                .param("authorId",   UUID.fromString(authorId))
                .query(rs -> {
                    while (rs.next()) {
                        result.put(rs.getString("session_id"), Boolean.TRUE);
                    }
                });
        return result;
    }

    @Override
    public void deleteById(String postId) {
        jdbcClient.sql("DELETE FROM walk_post WHERE post_id = :postId")
                .param("postId", UUID.fromString(postId))
                .update();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String selectAllWithJoins() {
        return """
                SELECT wp.post_id::text, wp.session_id::text, wp.author_id::text,
                       wp.caption, wp.visibility,
                       wp.show_companion, wp.show_route_map, wp.show_stats,
                       wp.distance_km, wp.duration_seconds, wp.points_earned,
                       wp.route_preview_url, wp.route_preview_path, wp.route_preview_status,
                       wp.created_at, wp.updated_at,
                       up_author.full_name AS author_name,
                       up_author.avatar_url AS author_avatar_url,
                       h.name AS hotspot_name,
                       up_companion.full_name AS companion_name
                FROM walk_post wp
                JOIN user_profile up_author ON up_author.user_id = wp.author_id
                JOIN walk_session ws ON ws.session_id = wp.session_id
                JOIN hotspot h ON h.id = ws.hotspot_id
                LEFT JOIN user_profile up_companion
                    ON up_companion.user_id = CASE
                        WHEN ws.user_id_a = wp.author_id THEN ws.user_id_b
                        ELSE ws.user_id_a
                    END
                """;
    }

    private WalkPost mapRow(ResultSet rs) throws SQLException {
        Timestamp updatedAt = rs.getTimestamp("updated_at");

        String statusRaw = rs.getString("route_preview_status");
        RoutePreviewStatus status;
        try {
            status = statusRaw != null ? RoutePreviewStatus.valueOf(statusRaw) : RoutePreviewStatus.PENDING;
        } catch (IllegalArgumentException e) {
            status = RoutePreviewStatus.PENDING;
        }

        return new WalkPost(
                rs.getString("post_id"),
                rs.getString("session_id"),
                rs.getString("author_id"),
                rs.getString("caption"),
                PostVisibility.from(rs.getString("visibility")),
                rs.getBoolean("show_companion"),
                rs.getBoolean("show_route_map"),
                rs.getBoolean("show_stats"),
                rs.getDouble("distance_km"),
                rs.getLong("duration_seconds"),
                rs.getInt("points_earned"),
                rs.getString("route_preview_url"),
                rs.getString("route_preview_path"),
                status,
                rs.getTimestamp("created_at").toInstant(),
                updatedAt != null ? updatedAt.toInstant() : null,
                rs.getString("author_name"),
                rs.getString("author_avatar_url"),
                rs.getString("hotspot_name"),
                rs.getString("companion_name")
        );
    }
}
