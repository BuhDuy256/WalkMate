package com.walkmate.domain.profile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class Profile {
    private final UUID userId;
    private final String fullName;
    private final String city;
    private final String avatarUrl;
    private final String bio;
    private final ProfileMode profileMode;
    private final InfoVisibilityMode infoVisibilityMode;
    private final List<ProfileTag> tags;
    private final String email;
    private final String phone;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public Profile(
            UUID userId,
            String fullName,
            String city,
            String avatarUrl,
            String bio,
            ProfileMode profileMode,
            InfoVisibilityMode infoVisibilityMode,
            List<ProfileTag> tags
    ) {
        this(userId, fullName, city, avatarUrl, bio, profileMode, infoVisibilityMode, tags, null, null, null, null);
    }

    public Profile(
            UUID userId,
            String fullName,
            String city,
            String avatarUrl,
            String bio,
            ProfileMode profileMode,
            InfoVisibilityMode infoVisibilityMode,
            List<ProfileTag> tags,
            String email,
            String phone,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.userId = userId;
        this.fullName = normalizeRequired(fullName, 120, "Full name");
        this.city = normalizeOptional(city, 120);
        this.avatarUrl = normalizeOptional(avatarUrl, 1024);
        this.bio = normalizeOptional(bio, 500);
        this.profileMode = profileMode;
        this.infoVisibilityMode = infoVisibilityMode;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.email = normalizeOptional(email, 255);
        this.phone = normalizeOptional(phone, 20);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    private String normalizeRequired(String value, int maxLength, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds max length " + maxLength);
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Field exceeds max length " + maxLength);
        }
        return normalized;
    }

    public Profile withContactInfo(String email, String phone) {
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

    public UUID getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getCity() {
        return city;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getBio() {
        return bio;
    }

    public ProfileMode getProfileMode() {
        return profileMode;
    }

    public InfoVisibilityMode getInfoVisibilityMode() {
        return infoVisibilityMode;
    }

    public List<ProfileTag> getTags() {
        return tags;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
