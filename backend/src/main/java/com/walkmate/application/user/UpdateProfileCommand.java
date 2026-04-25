package com.walkmate.application.user;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UpdateProfileCommand(
        UUID         callerId,
        String       fullName,
        String       gender,      // raw string; mapped to Gender enum in service
        LocalDate    dateOfBirth,
        String       bio,
        int          searchRadius,
        List<UUID>   tagIds
) {}
