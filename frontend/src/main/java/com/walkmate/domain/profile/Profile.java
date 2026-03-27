package com.walkmate.domain.profile;

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
            String phone
    ) {
        this.userId = userId;
        this.fullName = fullName;
        this.city = city;
        this.avatarUrl = avatarUrl;
        this.bio = bio;
        this.profileMode = profileMode;
        this.infoVisibilityMode = infoVisibilityMode;
        this.tags = tags;
        this.email = email;
        this.phone = phone;
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
}
