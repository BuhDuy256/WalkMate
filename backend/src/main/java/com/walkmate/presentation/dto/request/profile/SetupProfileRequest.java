package com.walkmate.presentation.dto.request.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SetupProfileRequest {
    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotBlank(message = "Display name is required")
    @Size(max = 120, message = "Display name cannot exceed 120 characters")
    private String fullName;

    @Size(max = 120, message = "City cannot exceed 120 characters")
    private String city;

    @Size(max = 1024, message = "Avatar URL cannot exceed 1024 characters")
    private String avatarUrl;

    @Size(max = 500, message = "Bio cannot exceed 500 characters")
    private String bio;

    @NotBlank(message = "Profile mode is required")
    private String profileMode;

    @NotBlank(message = "Info visibility mode is required")
    private String infoVisibilityMode;

    @NotNull(message = "Tags are required")
    @Size(min = 3, message = "At least 3 tags are required")
    private List<String> tags;
}
