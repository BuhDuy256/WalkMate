package com.walkmate.presentation.dto.request;

import com.walkmate.domain.intent.WalkPurpose;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public record SubmitIntentRequest(
    @NotNull Double lat,
    @NotNull Double lng,
    @NotNull Instant startTime,
    @NotNull Instant endTime,
    @NotNull WalkPurpose purpose,
    Integer minAge,
    Integer maxAge,
    String genderPreference,
    List<String> tagsPreference
) {}
