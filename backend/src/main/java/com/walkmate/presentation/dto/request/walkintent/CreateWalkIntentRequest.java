package com.walkmate.presentation.dto.request.walkintent;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Presentation-layer DTO for creating a walk intent.
 *
 * The frontend supplies a calendar date and fractional-hour floats
 * (e.g. 17.5 = 17:30) instead of ISO-8601 timestamps.
 * The controller converts date + float → Instant using Asia/Ho_Chi_Minh timezone.
 */
public record CreateWalkIntentRequest(

        @NotBlank(message = "Hotspot ID is required")
        @JsonProperty("hotspot_id")
        String hotspotId,

        @NotBlank(message = "Date is required")
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must be in yyyy-MM-dd format")
        String date,

        @DecimalMin(value = "0.0", message = "timeStart must be >= 0.0")
        @DecimalMax(value = "23.99", message = "timeStart must be < 24.0")
        @JsonProperty("time_start")
        float timeStart,

        @DecimalMin(value = "0.0", message = "timeEnd must be >= 0.0")
        @DecimalMax(value = "24.0", message = "timeEnd must be <= 24.0")
        @JsonProperty("time_end")
        float timeEnd,

        @Min(value = 0, message = "Age min must be >= 0")
        @JsonProperty("age_min")
        int ageMin,

        @Min(value = 0, message = "Age max must be >= 0")
        @JsonProperty("age_max")
        int ageMax,

        @JsonProperty("is_private")
        boolean isPrivate,

        @JsonProperty("invited_friend_id")
        String invitedFriendId,

        String description
) {
    @AssertTrue(message = "invited_friend_id is required when is_private is true")
    public boolean isInvitedFriendIdValidWhenPrivate() {
        return !isPrivate || (invitedFriendId != null && !invitedFriendId.isBlank());
    }
}
