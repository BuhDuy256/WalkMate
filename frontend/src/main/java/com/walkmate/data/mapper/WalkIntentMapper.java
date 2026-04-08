package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.walkintent.WalkIntentResponse;
import com.walkmate.domain.walkintent.WalkIntent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

public class WalkIntentMapper {

    public static WalkIntent toDomain(WalkIntentResponse response) {
        return new WalkIntent(
                response.getId(),
                response.getHotspotId(),
                response.getUserId(),
                toHourFloat(response.getTimeWindowStart()),
                toHourFloat(response.getTimeWindowEnd()),
                response.getAgeMin(),
                response.getAgeMax(),
                response.getStatus(),
                response.getCreatedAt(),
                Collections.emptyList(),  // tags not yet in API contract
                response.getExpiresAt(),
                null  // description not in API contract
        );
    }

    public static List<WalkIntent> toDomainList(List<WalkIntentResponse> responses) {
        List<WalkIntent> result = new ArrayList<>(responses.size());
        for (WalkIntentResponse r : responses) {
            result.add(toDomain(r));
        }
        return result;
    }

    private static float toHourFloat(String instantString) {
        if (instantString == null || instantString.trim().isEmpty()) {
            return 0f;
        }
        try {
            LocalTime localTime = Instant.parse(instantString)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime();
            return localTime.getHour() + (localTime.getMinute() / 60f);
        } catch (Exception ignored) {
            return 0f;
        }
    }

    private WalkIntentMapper() {}
}
