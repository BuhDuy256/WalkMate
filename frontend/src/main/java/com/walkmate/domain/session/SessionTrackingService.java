package com.walkmate.domain.session;

import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionTrackingService {

    private static final int BATCH_SIZE_THRESHOLD = 50;

    private final SessionRepository sessionRepository;
    private final LocationFilterPolicy filterPolicy;
    
    // Bộ nhớ đệm giữ tọa độ hợp lệ Cuối Cùng để tính độ dời
    private RoutePoint lastAcceptedPoint = null;
    
    // Đẩy ExecutorService lên tầng này để đảm bảo Sync Flow và tránh MainThread exception
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public SessionTrackingService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
        this.filterPolicy = new LocationFilterPolicy();
    }

    public void processNewLocation(RoutePoint point) {
        // Ném toàn bộ công việc tính toán, lưu, và đếm sang một luồng phụ duy nhất
        executorService.execute(() -> {
            // 1. Kiểm tra bằng Policy (Lọc nhiễu bán kính và micro-movement)
            if (!filterPolicy.isValid(point, lastAcceptedPoint)) {
                Log.w("SessionTracking", "⏭️ Bỏ qua điểm tĩnh/nhiễu (Sai số=" + point.getAccuracy() + "m)");
                return;
            }

            // Gọi thành công -> Cập nhật mốc điểm mới
            lastAcceptedPoint = point;

            // 2. Điểm hợp lệ -> Lưu xuống Database đồng bộ
            sessionRepository.saveRoutePoint(point);
            Log.d("SessionTracking", "✅ Đã lưu 1 điểm hợp lệ vào Local DB. Tiền trình ID: " + point.getSessionId());

            // 3. Logic gom nhóm (Batching) sử dụng DB làm Single Source of Truth
            int count = sessionRepository.getUnsyncedCount();
            if (count >= BATCH_SIZE_THRESHOLD) {
                triggerBatchSync();
            }
        });
    }

    private void triggerBatchSync() {
        Log.e("SessionTracking", "💥 ĐÃ ĐỦ " + BATCH_SIZE_THRESHOLD + " ĐIỂM! Chuẩn bị đồng bộ...");
        // Logic gọi API lên Server ở Bước 5 sẽ được đặt ở đây
    }
}
