package com.walkmate.presentation.mapper.walkintent;

import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.presentation.dto.response.walkintent.WalkIntentResponse;
import org.springframework.stereotype.Component;

@Component
public class WalkIntentMapper {

    public WalkIntentResponse toResponse(WalkIntent intent) {
        return new WalkIntentResponse(
                intent.getId(),
                intent.getHotspotId(),
                intent.getUserId(),
                intent.getTimeWindowStart().toString(),
                intent.getTimeWindowEnd().toString(),
                intent.getMatchingConstraints().ageMin(),
                intent.getMatchingConstraints().ageMax(),
                intent.getStatus().name(),
                intent.getCreatedAt().toString(),
                intent.getExpiresAt().toString(),
                intent.isPrivate(),
                intent.getDescription()
        );
    }
}
