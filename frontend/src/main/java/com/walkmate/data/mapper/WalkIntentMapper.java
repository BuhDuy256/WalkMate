package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.walkintent.WalkIntentResponse;
import com.walkmate.domain.walkintent.WalkIntent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WalkIntentMapper {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public static WalkIntent toDomain(WalkIntentResponse response) {
        return new WalkIntent(
                response.getId(),
                response.getHotspotId(),
                response.getHotspotName(),
                response.getUserId(),
                toHourFloat(response.getTimeWindowStart()),
                toHourFloat(response.getTimeWindowEnd()),
                response.getAgeMin(),
                response.getAgeMax(),
                response.getPreferredGender(),
                response.getStatus(),
                response.getCreatedAt(),
                toDateString(response.getTimeWindowStart()),
                Collections.emptyList(),
                response.getExpiresAt(),
                null,
                response.getProposalId()
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
        if (instantString == null || instantString.trim().isEmpty()) return 0f;
        try {
            LocalTime localTime = Instant.parse(instantString)
                    .atZone(VN_ZONE)
                    .toLocalTime();
            return localTime.getHour() + (localTime.getMinute() / 60f);
        } catch (Exception ignored) {
            return 0f;
        }
    }

    private static String toDateString(String instantString) {
        if (instantString == null || instantString.trim().isEmpty()) return null;
        try {
            LocalDate date = Instant.parse(instantString)
                    .atZone(VN_ZONE)
                    .toLocalDate();
            return date.toString(); // "yyyy-MM-dd"
        } catch (Exception ignored) {
            return null;
        }
    }

    private WalkIntentMapper() {}
}
