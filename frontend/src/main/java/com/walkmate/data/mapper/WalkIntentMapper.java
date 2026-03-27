package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.request.walkintent.CreateWalkIntentRequest;
import com.walkmate.data.datasource.remote.dto.response.walkintent.WalkIntentResponse;
import com.walkmate.domain.walkintent.WalkIntent;

public class WalkIntentMapper {

    public static WalkIntent toDomain(WalkIntentResponse response) {
        return new WalkIntent(
                response.getId(),
                response.getHotspotId(),
                response.getUserId(),
                response.getTimeStart(),
                response.getTimeEnd(),
                response.getAgeMin(),
                response.getAgeMax(),
                response.getStatus(),
                response.getCreatedAt()
        );
    }

    public static CreateWalkIntentRequest toRequest(String hotspotId, String userId,
                                                     float timeStart, float timeEnd,
                                                     int ageMin, int ageMax) {
        return new CreateWalkIntentRequest(hotspotId, userId, timeStart, timeEnd, ageMin, ageMax);
    }

    private WalkIntentMapper() {}
}
