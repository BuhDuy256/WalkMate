package com.walkmate.data.datasource.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.UUID;

public class ProfileResponseDto {
    @SerializedName("user_id")
    private UUID userId;

    @SerializedName("full_name")
    private String fullName;

    // Legacy field still kept for backward compatibility.
    private String city;

    @SerializedName("avatar_url")
    private String avatarUrl;

    private String bio;

    // Legacy visibility contract.
    private String profileMode;

    // Legacy visibility contract.
    private String infoVisibilityMode;

    // Legacy tag contract.
    private List<String> tags;

    // Legacy contact contract.
    private String email;

    // Legacy contact contract.
    private String phone;

    @SerializedName("gender")
    private String gender;

    @SerializedName("date_of_birth")
    private String dateOfBirth;

    @SerializedName("search_radius")
    private Integer searchRadius;

    @SerializedName("interests")
    private List<String> interests;

    @SerializedName("walk_vibes")
    private List<String> walkVibes;

    @SerializedName("best_time_to_walk")
    private List<String> bestTimeToWalk;

    @SerializedName("profile_visibility")
    private String profileVisibility;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("updated_at")
    private String updatedAt;

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

    public String getProfileMode() {
        return profileMode;
    }

    public String getInfoVisibilityMode() {
        return infoVisibilityMode;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getGender() {
        return gender;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public Integer getSearchRadius() {
        return searchRadius;
    }

    public List<String> getInterests() {
        return interests;
    }

    public List<String> getWalkVibes() {
        return walkVibes;
    }

    public List<String> getBestTimeToWalk() {
        return bestTimeToWalk;
    }

    public String getProfileVisibility() {
        return profileVisibility;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
