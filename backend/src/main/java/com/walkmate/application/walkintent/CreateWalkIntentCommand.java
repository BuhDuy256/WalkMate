package com.walkmate.application.walkintent;

import java.time.Instant;

public record CreateWalkIntentCommand(
        String hotspotId,
        String userId,
        Instant timeWindowStart,
        Instant timeWindowEnd,
        int ageMin,
        int ageMax,
        String preferredGender,  // "ANY", "MALE", or "FEMALE"
        boolean isPrivate,
        String invitedFriendId,
        String description
) {
}
