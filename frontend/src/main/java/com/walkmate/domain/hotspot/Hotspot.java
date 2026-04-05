package com.walkmate.domain.hotspot;

public class Hotspot {
    private final String id;
    private final String name;
    private final double lat;
    private final double lng;
    private final int openIntentCount;

    public Hotspot(String id, String name, double lat, double lng, int openIntentCount) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.openIntentCount = openIntentCount;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public int getopenIntentCount() {
        return openIntentCount;
    }
}
