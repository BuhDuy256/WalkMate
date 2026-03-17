package com.walkmate.domain.model;

public class RoutePoint {
    public final int pointOrder;
    public final double lat;
    public final double lng;
    public final long time;

    public RoutePoint(int pointOrder, double lat, double lng, long time) {
        this.pointOrder = pointOrder;
        this.lat = lat;
        this.lng = lng;
        this.time = time;
    }
}
