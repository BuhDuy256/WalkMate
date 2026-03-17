package com.walkmate.data.remote.dto;

public class AppendPointItemDto {
    public int pointOrder;
    public double lat;
    public double lng;
    public long time;

    public AppendPointItemDto(int pointOrder, double lat, double lng, long time) {
        this.pointOrder = pointOrder;
        this.lat = lat;
        this.lng = lng;
        this.time = time;
    }
}
