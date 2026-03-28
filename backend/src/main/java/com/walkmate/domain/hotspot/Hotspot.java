package com.walkmate.domain.hotspot;

import lombok.Getter;

@Getter
public class Hotspot {

    private String id;
    private String name;
    private double lat;
    private double lng;
    private int activeIntentCount;

    protected Hotspot() {
    }

    // Rehydration constructor
    public Hotspot(String id, String name, double lat, double lng, int activeIntentCount) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.activeIntentCount = activeIntentCount;
    }
}
