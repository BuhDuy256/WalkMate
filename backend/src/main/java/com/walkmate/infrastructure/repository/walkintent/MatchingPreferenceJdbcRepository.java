package com.walkmate.infrastructure.repository.walkintent;

import com.walkmate.domain.walkintent.MatchingPreference;
import com.walkmate.domain.walkintent.MatchingPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MatchingPreferenceJdbcRepository implements MatchingPreferenceRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Optional<MatchingPreference> findByUserId(UUID userId) {
        return jdbcClient.sql("""
                        SELECT user_id, weight_time_overlap, weight_interest,
                               weight_behavior, last_trained_at
                        FROM matching_preference_model
                        WHERE user_id = :userId
                        """)
                .param("userId", userId)
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Override
    public void save(MatchingPreference preference) {
        jdbcClient.sql("""
                        INSERT INTO matching_preference_model
                            (user_id, weight_time_overlap, weight_interest,
                             weight_behavior, last_trained_at)
                        VALUES
                            (:userId, :weightTimeOverlap, :weightInterest,
                             :weightBehavior, :lastTrainedAt)
                        ON CONFLICT (user_id) DO UPDATE SET
                            weight_time_overlap = EXCLUDED.weight_time_overlap,
                            weight_interest     = EXCLUDED.weight_interest,
                            weight_behavior     = EXCLUDED.weight_behavior,
                            last_trained_at     = EXCLUDED.last_trained_at
                        """)
                .param("userId",            preference.getUserId())
                .param("weightTimeOverlap", preference.getWeightTimeOverlap())
                .param("weightInterest",    preference.getWeightInterest())
                .param("weightBehavior",    preference.getWeightBehavior())
                .param("lastTrainedAt",     Timestamp.from(preference.getLastTrainedAt()))
                .update();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MatchingPreference mapRow(ResultSet rs) throws SQLException {
        return new MatchingPreference(
                rs.getObject("user_id", UUID.class),
                rs.getDouble("weight_time_overlap"),
                rs.getDouble("weight_interest"),
                rs.getDouble("weight_behavior"),
                rs.getTimestamp("last_trained_at").toInstant()
        );
    }
}
