package com.walkmate.infrastructure.repository.user;

import com.walkmate.domain.user.Gender;
import com.walkmate.domain.user.ProfileTagMaster;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserProfileJdbcRepository implements UserProfileRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Optional<UserProfile> findByUserId(UUID userId) {
        return jdbcClient.sql("""
                        SELECT user_id, full_name, gender, date_of_birth,
                               avatar_url, bio, search_radius
                        FROM user_profile
                        WHERE user_id = :userId
                        """)
                .param("userId", userId)
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Override
    public UserProfile save(UserProfile profile) {
        jdbcClient.sql("""
                        INSERT INTO user_profile
                            (user_id, full_name, gender, date_of_birth,
                             avatar_url, bio, search_radius, updated_at)
                        VALUES
                            (:userId, :fullName, CAST(:gender AS gender), :dateOfBirth,
                             :avatarUrl, :bio, :searchRadius, :updatedAt)
                        ON CONFLICT (user_id) DO UPDATE SET
                            full_name     = EXCLUDED.full_name,
                            gender        = EXCLUDED.gender,
                            date_of_birth = EXCLUDED.date_of_birth,
                            avatar_url    = EXCLUDED.avatar_url,
                            bio           = EXCLUDED.bio,
                            search_radius = EXCLUDED.search_radius,
                            updated_at    = EXCLUDED.updated_at
                        """)
                .param("userId",       profile.getUserId())
                .param("fullName",     profile.getFullName())
                .param("gender",       profile.getGender() != null ? profile.getGender().name() : null)
                .param("dateOfBirth",  profile.getDateOfBirth() != null
                        ? Date.valueOf(profile.getDateOfBirth()) : null)
                .param("avatarUrl",    profile.getAvatarUrl())
                .param("bio",          profile.getBio())
                .param("searchRadius", profile.getSearchRadius())
                .param("updatedAt",    java.sql.Timestamp.from(Instant.now()))
                .update();

        return profile;
    }

    @Override
    @Transactional
    public void replaceTagsByIds(UUID userId, List<UUID> tagIds) {
        jdbcClient.sql("DELETE FROM user_profile_tag_map WHERE user_id = :userId")
                .param("userId", userId)
                .update();

        for (UUID tagId : tagIds) {
            jdbcClient.sql("""
                    INSERT INTO user_profile_tag_map (user_id, tag_id)
                    VALUES (:userId, :tagId)
                    ON CONFLICT DO NOTHING
                    """)
                    .param("userId", userId)
                    .param("tagId",  tagId)
                    .update();
        }
    }

    @Override
    public List<String> findTagsByUserId(UUID userId) {
        return jdbcClient.sql("""
                        SELECT ptm.tag_name
                        FROM   user_profile_tag_map uptm
                        JOIN   profile_tag_master   ptm ON uptm.tag_id = ptm.tag_id
                        WHERE  uptm.user_id = :userId
                        ORDER  BY ptm.tag_name
                        """)
                .param("userId", userId)
                .query(String.class)
                .list();
    }

    @Override
    public List<ProfileTagMaster> findAllMasterTags() {
        return jdbcClient.sql("""
                        SELECT tag_id, tag_name
                        FROM   profile_tag_master
                        ORDER  BY tag_name
                        """)
                .query((rs, rowNum) -> new ProfileTagMaster(
                        rs.getObject("tag_id", UUID.class),
                        rs.getString("tag_name")))
                .list();
    }

    @Override
    public Map<UUID, String> findNamesByUserIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();
        return jdbcClient.sql("""
                        SELECT user_id, full_name FROM user_profile
                        WHERE user_id = ANY(:ids)
                        """)
                .param("ids", userIds.toArray(new UUID[0]))
                .query(rs -> {
                    Map<UUID, String> result = new HashMap<>();
                    while (rs.next()) {
                        UUID id   = rs.getObject("user_id", UUID.class);
                        String name = rs.getString("full_name");
                        result.put(id, name != null ? name : "");
                    }
                    return result;
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserProfile mapRow(ResultSet rs) throws SQLException {
        String genderStr = rs.getString("gender");
        Gender gender    = (genderStr != null) ? Gender.valueOf(genderStr) : null;

        Date dob = rs.getDate("date_of_birth");

        return new UserProfile(
                rs.getObject("user_id", UUID.class),
                rs.getString("full_name"),
                gender,
                dob != null ? dob.toLocalDate() : null,
                rs.getString("avatar_url"),
                rs.getString("bio"),
                rs.getInt("search_radius")
        );
    }
}
