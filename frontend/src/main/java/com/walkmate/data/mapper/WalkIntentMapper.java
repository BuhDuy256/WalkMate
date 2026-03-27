package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.request.walkintent.CreateWalkIntentRequest;
import com.walkmate.data.datasource.remote.dto.response.walkintent.WalkIntentResponse;
import com.walkmate.domain.walkintent.WalkIntent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

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
                response.getCreatedAt()
        );
    }

    public static CreateWalkIntentRequest toRequest(String hotspotId,
                                                     float timeStart, float timeEnd,
                                                     int ageMin, int ageMax) {
        return new CreateWalkIntentRequest(
                hotspotId,
                toInstantString(timeStart),
                toInstantString(timeEnd),
                ageMin,
                ageMax);
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

    private static String toInstantString(float hourFloat) {
        int hour = (int) Math.floor(hourFloat);
        int minute = Math.round((hourFloat - hour) * 60f);

        if (minute >= 60) {
            hour += 1;
            minute -= 60;
        }

        if (hour >= 24) {
            hour = 23;
            minute = 59;
        }
        if (hour < 0) {
            hour = 0;
            minute = 0;
        }

        return LocalDate.now(ZoneOffset.UTC)
                .atTime(hour, minute)
                .toInstant(ZoneOffset.UTC)
                .toString();
    }

    private WalkIntentMapper() {}
}
