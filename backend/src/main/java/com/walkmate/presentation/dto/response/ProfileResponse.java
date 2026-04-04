package com.walkmate.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {
    @JsonProperty("user_id")
    private UUID userId;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("date_of_birth")
    private LocalDate dateOfBirth;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    @JsonProperty("bio")
    private String bio;

    @JsonProperty("search_radius")
    private Integer searchRadius;

    @JsonProperty("interests")
    private List<String> interests;

    @JsonProperty("walk_vibes")
    private List<String> walkVibes;

    @JsonProperty("best_time_to_walk")
    private List<String> bestTimeToWalk;

    @JsonProperty("profile_visibility")
    private String profileVisibility;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
