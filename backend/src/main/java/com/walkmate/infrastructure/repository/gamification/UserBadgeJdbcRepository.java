package com.walkmate.infrastructure.repository.gamification;

import com.walkmate.domain.gamification.Badge;
import com.walkmate.domain.gamification.UserBadgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserBadgeJdbcRepository implements UserBadgeRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Set<String> findBadgeNamesByUserId(String userId) {
        List<String> names = jdbcClient.sql("""
                SELECT badge_name FROM user_badge WHERE user_id = :userId
                """)
                .param("userId", UUID.fromString(userId))
                .query(String.class)
                .list();
        return new HashSet<>(names);
    }

    @Override
    public void saveAll(String userId, List<Badge> badges) {
        if (badges.isEmpty())
            return;
        UUID userUuid = UUID.fromString(userId);
        for (Badge badge : badges) {
            jdbcClient.sql("""
                    INSERT INTO user_badge (user_id, badge_name)
                    VALUES (:userId, :badgeName)
                    ON CONFLICT (user_id, badge_name) DO NOTHING
                    """)
                    .param("userId", userUuid)
                    .param("badgeName", badge.name())
                    .update();
        }
    }

    @Override
    public List<UserBadgeRecord> findByUserId(String userId) {
        return jdbcClient.sql("""
                SELECT ub.badge_name,
                       b.display_name,
                       b.description,
                       ub.awarded_at::text,
                       b.rarity,
                       b.category
                FROM user_badge ub
                JOIN badge b ON b.name = ub.badge_name
                WHERE ub.user_id = :userId
                ORDER BY ub.awarded_at ASC
                """)
                .param("userId", UUID.fromString(userId))
                .query((rs, rowNum) -> new UserBadgeRecord(
                        rs.getString("badge_name"),
                        rs.getString("display_name"),
                        rs.getString("description"),
                        rs.getString("awarded_at"),
                        rs.getString("rarity"),
                        rs.getString("category")))
                .list();
    }

    @Override
    public List<BadgeCatalogRecord> findAllActive() {
        return jdbcClient.sql("""
                SELECT name, display_name, description, rarity, category
                FROM badge
                WHERE is_active = true
                ORDER BY category ASC, name ASC
                """)
                .query((rs, rowNum) -> new BadgeCatalogRecord(
                        rs.getString("name"),
                        rs.getString("display_name"),
                        rs.getString("description"),
                        rs.getString("rarity"),
                        rs.getString("category")))
                .list();
    }
}
