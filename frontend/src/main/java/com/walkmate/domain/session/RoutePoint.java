package com.walkmate.domain.session;

/**
 * Domain Model đại diện cho một điểm tọa độ GPS trong hành trình đi bộ.
 * Đây là thực thể thuần lõi không chứa framework annotations (như Room).
 */
public class RoutePoint {
    private final long id;
    private final String sessionId;
    private final double lat;
    private final double lng;
    private final long timestamp;
    private final float accuracy;

    // Constructor 1: Dùng khi tạo mới từ GPS (chưa lưu DB nên chưa có ID)
    public RoutePoint(String sessionId, double lat, double lng, long timestamp, float accuracy) {
        this.id = 0; // Khởi tạo mặc định, sau khi lưu Room sẽ có ID auto-gen
        this.sessionId = sessionId;
        this.lat = lat;
        this.lng = lng;
        this.timestamp = timestamp;
        this.accuracy = accuracy;
    }

    // Constructor 2: Dùng khi lấy dữ liệu ngược từ phần Entity của Database lên
    public RoutePoint(long id, String sessionId, double lat, double lng, long timestamp, float accuracy) {
        this.id = id;
        this.sessionId = sessionId;
        this.lat = lat;
        this.lng = lng;
        this.timestamp = timestamp;
        this.accuracy = accuracy;
    }

    public long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
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
