package com.walkmate.infrastructure.repository.profile;

import com.walkmate.domain.profile.InfoVisibilityMode;
import com.walkmate.domain.profile.Profile;
import com.walkmate.domain.profile.ProfileRepository;
import com.walkmate.domain.profile.ProfileTag;
import com.walkmate.domain.profile.ProfileMode;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProfileJooqRepository implements ProfileRepository {
    private final DataSource dataSource;

    public ProfileJooqRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Profile upsert(Profile profile) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            ensureUserAccountExists(conn, profile.getUserId());
            upsertUserProfile(conn, profile);
            replaceProfileTags(conn, profile.getUserId(), profile.getTags());

            conn.commit();
            return findByUserId(profile.getUserId()).orElse(profile);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save profile", e);
        }
    }

    @Override
    public Optional<Profile> findByUserId(UUID userId) {
        String sql = """
                SELECT
                    up.user_id,
                    up.full_name,
                    up.city,
                    up.avatar_url,
                    up.bio,
                    up.profile_mode,
                    up.info_visibility,
                    up.created_at,
                    up.updated_at,
                    ua.email,
                    ua.phone
                FROM user_profile up
                LEFT JOIN user_account ua ON up.user_id = ua.user_id
                WHERE up.user_id = ?
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                return Optional.empty();
            }

            return Optional.of(mapProfile(conn, rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load profile", e);
        }
    }

    private void ensureUserAccountExists(Connection conn, UUID userId) throws SQLException {
        String existsSql = "SELECT EXISTS(SELECT 1 FROM user_account WHERE user_id = ?)";
        try (PreparedStatement stmt = conn.prepareStatement(existsSql)) {
            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getBoolean(1)) {
                return;
            }
        }

        String insertSql = """
                INSERT INTO user_account (user_id, email, password_hash, provider, status, created_at)
                VALUES (?, ?, ?, 'LOCAL', 'ACTIVE', CURRENT_TIMESTAMP)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            stmt.setObject(1, userId);
            stmt.setString(2, "walkmate+" + userId + "@example.com");
            stmt.setString(3, "TEMP_HASH");
            stmt.executeUpdate();
        }
    }

    private void upsertUserProfile(Connection conn, Profile profile) throws SQLException {
        String upsertSql = """
                INSERT INTO user_profile (
                    user_id,
                    full_name,
                    city,
                    avatar_url,
                    bio,
                    profile_mode,
                    info_visibility,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?::profile_mode, ?::info_visibility_mode, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id)
                DO UPDATE SET
                    full_name = EXCLUDED.full_name,
                    city = EXCLUDED.city,
                    avatar_url = EXCLUDED.avatar_url,
                    bio = EXCLUDED.bio,
                    profile_mode = EXCLUDED.profile_mode,
                    info_visibility = EXCLUDED.info_visibility,
                    updated_at = CURRENT_TIMESTAMP
                """;

        try (PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
            stmt.setObject(1, profile.getUserId());
            stmt.setString(2, profile.getFullName());
            stmt.setString(3, profile.getCity());
            stmt.setString(4, profile.getAvatarUrl());
            stmt.setString(5, profile.getBio());
            stmt.setString(6, profile.getProfileMode().name());
            stmt.setString(7, profile.getInfoVisibilityMode().name());
            stmt.executeUpdate();
        }
    }

    private void replaceProfileTags(Connection conn, UUID userId, List<ProfileTag> tags) throws SQLException {
        String deleteSql = "DELETE FROM profile_tag WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setObject(1, userId);
            stmt.executeUpdate();
        }

        if (tags == null || tags.isEmpty()) {
            return;
        }

        String insertSql = """
                INSERT INTO profile_tag (tag_id, user_id, tag_type, created_at)
                VALUES (?, ?, ?::tag_type, CURRENT_TIMESTAMP)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (ProfileTag tag : tags) {
                stmt.setObject(1, UUID.randomUUID());
                stmt.setObject(2, userId);
                stmt.setString(3, tag.toCode());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private Profile mapProfile(Connection conn, ResultSet rs) throws SQLException {
        UUID userId = (UUID) rs.getObject("user_id");
        String fullName = rs.getString("full_name");
        String city = rs.getString("city");
        String avatarUrl = rs.getString("avatar_url");
        String bio = rs.getString("bio");
        ProfileMode profileMode = ProfileMode.fromCode(rs.getString("profile_mode"));
        InfoVisibilityMode infoVisibilityMode = InfoVisibilityMode.fromCode(rs.getString("info_visibility"));
        String email = rs.getString("email");
        String phone = rs.getString("phone");
        LocalDateTime createdAt = getDateTime(rs, "created_at");
        LocalDateTime updatedAt = getDateTime(rs, "updated_at");
        List<ProfileTag> tags = loadTags(conn, userId);

        return new Profile(
                userId,
                fullName,
                city,
                avatarUrl,
                bio,
                profileMode,
                infoVisibilityMode,
                tags,
                email,
                phone,
                createdAt,
                updatedAt
        );
    }

    private List<ProfileTag> loadTags(Connection conn, UUID userId) throws SQLException {
        String sql = "SELECT tag_type FROM profile_tag WHERE user_id = ? ORDER BY created_at ASC";
        List<ProfileTag> tags = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String tagType = rs.getString("tag_type");
                try {
                    tags.add(ProfileTag.fromCode(tagType));
                } catch (IllegalArgumentException ignored) {
                    // Skip tags unknown to current app version.
                }
            }
        }

        return tags;
    }

    private LocalDateTime getDateTime(ResultSet rs, String columnName) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnName);
        return ts == null ? null : ts.toLocalDateTime();
    }
}
