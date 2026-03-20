package com.walkmate.domain.session;

import androidx.lifecycle.LiveData;
import java.util.List;

// Đây là bản hợp đồng nghiệp vụ
public interface SessionRepository {

    // Dùng cho WalkTrackerService: Ném 1 điểm GPS vào DB
    void saveRoutePoint(RoutePoint point);

    // Dùng cho TrackingViewModel (UI): Lắng nghe liên tục các điểm trong DB để vẽ Polyline
    // (Đây chính là sự châm chước nguyên tắc DDD mà chúng ta đã thống nhất: cho phép dùng LiveData ở Domain)
    LiveData<List<RoutePoint>> getPointsOfCurrentSession();

    // Dùng cho Sync Logic (Bước 5): Lấy ra các điểm chưa được gửi lên Server
    List<RoutePoint> getUnsyncedPoints();

    // Dùng để kiểm tra Batching (Lấy đếm từ Database)
    int getUnsyncedCount();

    // Dùng cho Sync Logic (Bước 5): Đánh dấu các điểm đã gửi thành công
    void markPointsAsSynced(List<Long> pointIds);
}
