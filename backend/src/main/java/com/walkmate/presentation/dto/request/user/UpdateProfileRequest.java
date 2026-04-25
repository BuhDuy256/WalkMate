package com.walkmate.presentation.dto.request.user;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdateProfileRequest(

        @Size(max = 100, message = "fullName must be at most 100 characters")
        String fullName,

        String gender,          // "MALE" | "FEMALE"

        String dateOfBirth,     // "YYYY-MM-DD"

        @Size(max = 500, message = "bio must be at most 500 characters")
        String bio,

        @Size(max = 10, message = "at most 10 tags allowed")
        List<UUID> tagIds
) {}
