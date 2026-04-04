package com.walkmate.data.datasource.remote.dto;

import java.util.List;
import java.util.UUID;

public class SetupProfileRequestDto {
    private UUID userId;
    private String fullName;
    private String city;
    private String avatarUrl;
    private String bio;
    private String dateOfBirth;
    private String gender;
    private String profileMode;
    private List<String> tags;

    public SetupProfileRequestDto(
            UUID userId,
            String fullName,
            String city,
            String avatarUrl,
            String bio,
                String dateOfBirth,
                String gender,
            String profileMode,
            List<String> tags
    ) {
        this.userId = userId;
        this.fullName = fullName;
        this.city = city;
        this.avatarUrl = avatarUrl;
        this.bio = bio;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.profileMode = profileMode;
        this.tags = tags;
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

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public String getProfileMode() {
        return profileMode;
    }

    public List<String> getTags() {
        return tags;
    }
}
