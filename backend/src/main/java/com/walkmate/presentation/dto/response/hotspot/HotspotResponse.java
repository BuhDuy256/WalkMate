package com.walkmate.presentation.dto.response.hotspot;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HotspotResponse(
        String id,
        String name,
        double lat,
        double lng,

        @JsonProperty("active_intent_count")
        int activeIntentCount
) {
}
