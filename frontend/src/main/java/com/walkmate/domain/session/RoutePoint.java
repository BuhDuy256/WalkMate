package com.walkmate.domain.session;

/**
 * Domain Model đại diện cho một điểm tọa độ GPS trong hành trình đi bộ.
 * Đây là thực thể thuần lõi không chứa framework annotations (như Room).
 */
public class RoutePoint {
    private final double lat;
    private final double lng;
    private final long timestamp;
    private final float accuracy;

    public RoutePoint(double lat, double lng, long timestamp, float accuracy) {
        this.lat = lat;
        this.lng = lng;
        this.timestamp = timestamp;
        this.accuracy = accuracy;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public float getAccuracy() {
        return accuracy;
    }
}
