package com.walkmate.controller.dto;

import com.walkmate.domain.intent.WalkPurpose;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Maps the frontend form parameters based on Figma UI:
 * "When do you want to walk?"
 * "Location Radius"
 * "Walk Type"
 * "Extra preferences"
 */
@Data
public class CreateIntentRequest {
    
    // Derived from UI "When do you want to walk?" + Duration
    private LocalDateTime timeWindowStart;
    private LocalDateTime timeWindowEnd;
    
    // GPS Coordinates for center of "Location Radius"
    private Double locationLat;
    private Double locationLng;
    
    // From UI slider "900m", mapped to match_filter -> search_radius in DB
    private Integer radiusMeters;
    
    // From UI buttons "Casual / Talk & Walk / Park Stroll / Pet Walk"
    private WalkPurpose walkType; 
    
    // From UI "Solo-friendly / Group OK / Quiet routes"
    // Mapped to tag_preferences inside match_filter
    private List<String> extraPreferences; 
}
