package com.walkmate.presentation.dto.response.hotspot;

public record HotspotResponse(
        String id,
        String name,
        double lat,
        double lng,
        int openIntentCount) {
}
