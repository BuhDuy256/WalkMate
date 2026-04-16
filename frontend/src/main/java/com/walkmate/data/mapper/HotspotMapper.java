package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.hotspot.HotspotResponse;
import com.walkmate.domain.hotspot.Hotspot;

import java.util.ArrayList;
import java.util.List;

public class HotspotMapper {

    public static Hotspot toDomain(HotspotResponse response) {
        return new Hotspot(
                response.getId(),
                response.getName(),
                response.getLat(),
                response.getLng(),
                response.getopenIntentCount());
    }

    public static List<Hotspot> toDomainList(List<HotspotResponse> responses) {
        List<Hotspot> result = new ArrayList<>(responses.size());
        for (HotspotResponse r : responses) {
            result.add(toDomain(r));
        }
        return result;
    }

    private HotspotMapper() {
    }
}
