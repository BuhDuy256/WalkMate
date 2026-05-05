package com.walkmate.infrastructure.repository.review;

import com.walkmate.domain.review.ReviewTag;
import com.walkmate.domain.review.ReviewTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReviewTagJdbcRepository implements ReviewTagRepository {

    private final JdbcClient jdbcClient;

    @Override
    public List<ReviewTag> findAllActive() {
        return jdbcClient.sql("""
                        SELECT tag_id, tag_name, tag_type
                        FROM review_tag_master
                        WHERE is_active = true
                        ORDER BY tag_type, tag_name
                        """)
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    @Override
    public List<ReviewTag> findByIds(List<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return Collections.emptyList();
        return jdbcClient.sql("""
                        SELECT tag_id, tag_name, tag_type
                        FROM review_tag_master
                        WHERE tag_id = ANY(:ids)
                        ORDER BY tag_type, tag_name
                        """)
                .param("ids", tagIds.toArray(new UUID[0]))
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    @Override
    public void saveTagMappings(String reviewId, List<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return;

        UUID reviewUuid = UUID.fromString(reviewId);
        for (UUID tagId : tagIds) {
            jdbcClient.sql("""
                            INSERT INTO walk_review_tag_map (review_id, tag_id)
                            VALUES (:reviewId, :tagId)
                            ON CONFLICT DO NOTHING
                            """)
                    .param("reviewId", reviewUuid)
                    .param("tagId",    tagId)
                    .update();
        }
    }

    @Override
    public List<String> findTagIdsByReviewId(String reviewId) {
        return jdbcClient.sql("""
                        SELECT tag_id::text
                        FROM walk_review_tag_map
                        WHERE review_id = :reviewId
                        """)
                .param("reviewId", UUID.fromString(reviewId))
                .query(String.class)
                .list();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ReviewTag mapRow(ResultSet rs) throws SQLException {
        return new ReviewTag(
                rs.getObject("tag_id", UUID.class),
                rs.getString("tag_name"),
                rs.getString("tag_type")
        );
    }
}
