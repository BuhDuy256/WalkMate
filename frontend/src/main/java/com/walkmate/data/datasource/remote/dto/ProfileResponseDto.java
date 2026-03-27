package com.walkmate.data.datasource.remote.dto;

import java.util.List;
import java.util.UUID;

public class ProfileResponseDto {
    private UUID userId;
    private String fullName;
    private String city;
    private String avatarUrl;
    private String bio;
    private String profileMode;
    private String infoVisibilityMode;
    private List<String> tags;
    private String email;
    private String phone;

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
}
