package com.walkmate.presentation.dto.request.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class SetupProfileRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 120, message = "Name cannot exceed 120 characters")
    private String name;

    @Size(max = 120, message = "City cannot exceed 120 characters")
    private String city;

    @Size(max = 500, message = "Walk bio cannot exceed 500 characters")
    private String walkBio;

    @Size(max = 2048, message = "Avatar cannot exceed 2048 characters")
    private String avatar;

    private LocalDate dateOfBirth;

    @Size(max = 50, message = "Gender cannot exceed 50 characters")
    private String gender;

    private Boolean publicProfile;

    // Public info visibility flag from FE.
    private Boolean publicInfo;

    private List<String> interests;

    private List<String> walkVibes;

    private List<String> bestTimeToWalk;
}
