package com.walkmate.presentation.dto.request.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdateProfileRequest(

        @Size(max = 100, message = "fullName must be at most 100 characters")
        String fullName,

        String gender,          // "MALE" | "FEMALE" | "OTHER" | "PREFER_NOT_TO_SAY"

        String dateOfBirth,     // "YYYY-MM-DD"

        @Size(max = 500, message = "bio must be at most 500 characters")
        String bio,

        @Min(value = 1, message = "searchRadius must be at least 1 metre")
        @Max(value = 50000, message = "searchRadius must be at most 50 000 metres")
        int searchRadius,

        @Size(max = 10, message = "at most 10 tags allowed")
        List<UUID> tagIds
) {}
