package com.walkmate.application.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walkmate.domain.profile.ProfileErrorCode;
import com.walkmate.domain.profile.ProfileTag;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.presentation.dto.request.profile.SetupProfileRequest;
import com.walkmate.presentation.dto.response.ProfileResponse;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProfileSetupPersistenceService {
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public ProfileSetupPersistenceService(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    public void saveProfile(UUID userId, SetupProfileRequest request) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            ensureUserAccountExists(conn, userId);
            upsertUserProfile(conn, userId, request);

            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save profile", e);
        }
    }

    public ProfileResponse getProfile(UUID userId) {
        String sql = """
                SELECT
                    user_id,
                    full_name,
                    gender,
                    date_of_birth,
                    avatar_url,
                    bio,
                    search_radius,
                    interests,
                    walk_vibes,
                    best_time_to_walk,
                    profile_visibility,
                    created_at,
                    updated_at
                FROM user_profile
                WHERE user_id = ?
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                throw new DomainException(ProfileErrorCode.PROFILE_NOT_FOUND.name(), "Profile not found");
            }

            return new ProfileResponse(
                    (UUID) rs.getObject("user_id"),
                    rs.getString("full_name"),
                    rs.getString("gender"),
                    getLocalDate(rs, "date_of_birth"),
                    rs.getString("avatar_url"),
                    rs.getString("bio"),
                    (Integer) rs.getObject("search_radius"),
                    parseJsonArray(rs.getString("interests")),
                    parseJsonArray(rs.getString("walk_vibes")),
                    parseJsonArray(rs.getString("best_time_to_walk")),
                    rs.getString("profile_visibility"),
                    getLocalDateTime(rs, "created_at"),
                    getLocalDateTime(rs, "updated_at")
            );
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

    private void upsertUserProfile(Connection conn, UUID userId, SetupProfileRequest request) throws SQLException {
        String resolvedName = resolveName(request);
        if (resolvedName == null || resolvedName.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        validateDateOfBirth(request.getDateOfBirth());

        String upsertSql = """
                INSERT INTO user_profile (
                    user_id,
                    full_name,
                    gender,
                    date_of_birth,
                    avatar_url,
                    bio,
                    interests,
                    walk_vibes,
                    best_time_to_walk,
                    profile_visibility,
                    updated_at,
                    created_at
                )
                VALUES (?, ?, ?::gender, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id)
                DO UPDATE SET
                    full_name = EXCLUDED.full_name,
                    gender = EXCLUDED.gender,
                    date_of_birth = EXCLUDED.date_of_birth,
                    avatar_url = EXCLUDED.avatar_url,
                    bio = EXCLUDED.bio,
                    interests = EXCLUDED.interests,
                    walk_vibes = EXCLUDED.walk_vibes,
                    best_time_to_walk = EXCLUDED.best_time_to_walk,
                    profile_visibility = EXCLUDED.profile_visibility,
                    updated_at = CURRENT_TIMESTAMP
                """;

        try (PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
            stmt.setObject(1, userId);
            stmt.setString(2, resolvedName);

            if (request.getGender() == null || request.getGender().isBlank()) {
                stmt.setNull(3, Types.VARCHAR);
            } else {
                stmt.setString(3, request.getGender().trim().toUpperCase());
            }

            if (request.getDateOfBirth() == null) {
                stmt.setNull(4, Types.DATE);
            } else {
                stmt.setDate(4, Date.valueOf(request.getDateOfBirth()));
            }

            stmt.setString(5, resolveAvatar(request));
            stmt.setString(6, resolveBio(request));
            stmt.setString(7, toJsonArray(resolveInterests(request)));
            stmt.setString(8, toJsonArray(resolveWalkVibes(request)));
            stmt.setString(9, toJsonArray(resolveBestTimeToWalk(request)));
            stmt.setString(10, resolveProfileVisibility(request));
            stmt.executeUpdate();
        }
    }

    private String resolveName(SetupProfileRequest request) {
        if (request.getName() != null && !request.getName().isBlank()) {
            return request.getName().trim();
        }
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            return request.getFullName().trim();
        }
        return null;
    }

    private String resolveAvatar(SetupProfileRequest request) {
        if (request.getAvatar() != null && !request.getAvatar().isBlank()) {
            return request.getAvatar();
        }
        return request.getAvatarUrl();
    }

    private String resolveBio(SetupProfileRequest request) {
        if (request.getWalkBio() != null && !request.getWalkBio().isBlank()) {
            return request.getWalkBio();
        }
        return request.getBio();
    }

    private List<String> resolveInterests(SetupProfileRequest request) {
        if (request.getInterests() != null) {
            return request.getInterests();
        }
        return filterLegacyTagsByCategory(request.getTags(), "INTERESTS");
    }

    private List<String> resolveWalkVibes(SetupProfileRequest request) {
        if (request.getWalkVibes() != null) {
            return request.getWalkVibes();
        }
        return filterLegacyTagsByCategory(request.getTags(), "WALK_VIBES");
    }

    private List<String> resolveBestTimeToWalk(SetupProfileRequest request) {
        if (request.getBestTimeToWalk() != null) {
            return request.getBestTimeToWalk();
        }
        return filterLegacyTagsByCategory(request.getTags(), "BEST_TIME");
    }

    private List<String> filterLegacyTagsByCategory(List<String> legacyTags, String category) {
        if (legacyTags == null || legacyTags.isEmpty()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (String tagCode : legacyTags) {
            if (tagCode == null || tagCode.isBlank()) {
                continue;
            }
            try {
                ProfileTag tag = ProfileTag.fromCode(tagCode);
                if (category.equals(tag.getCategory())) {
                    result.add(tag.toCode());
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown legacy tag values.
            }
        }
        return result;
    }

    private String resolveProfileVisibility(SetupProfileRequest request) {
        if (request.getProfileVisibility() != null && !request.getProfileVisibility().isBlank()) {
            return request.getProfileVisibility().trim().toUpperCase();
        }
        if (request.getProfileMode() != null && !request.getProfileMode().isBlank()) {
            return request.getProfileMode().trim().toUpperCase();
        }
        return Boolean.TRUE.equals(request.getPublicProfile()) ? "PUBLIC" : "PRIVATE";
    }

    private String toJsonArray(List<String> values) {
        List<String> safeValues = values == null ? List.of() : values;
        try {
            return objectMapper.writeValueAsString(safeValues);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid list payload", e);
        }
    }

    private List<String> parseJsonArray(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private LocalDate getLocalDate(ResultSet rs, String columnName) throws SQLException {
        Date date = rs.getDate(columnName);
        return date == null ? null : date.toLocalDate();
    }

    private java.time.LocalDateTime getLocalDateTime(ResultSet rs, String columnName) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private void validateDateOfBirth(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return;
        }
        LocalDate maxAllowedDob = LocalDate.now().minusYears(13);
        if (!dateOfBirth.isBefore(maxAllowedDob)) {
            throw new IllegalArgumentException("Date of birth must be at least 13 years before today");
        }
    }
}
