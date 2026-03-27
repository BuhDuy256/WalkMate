package com.walkmate.presentation.mapper.hotspot;

import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.presentation.dto.response.hotspot.HotspotResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HotspotMapper {

    public HotspotResponse toResponse(Hotspot hotspot) {
        return new HotspotResponse(
                hotspot.getId(),
                hotspot.getName(),
                hotspot.getLat(),
                hotspot.getLng(),
                hotspot.getActiveWalkerCount()
        );
    }

    public List<HotspotResponse> toResponseList(List<Hotspot> hotspots) {
        return hotspots.stream().map(this::toResponse).toList();
    }
}
