package com.walkmate.data.datasource.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.walkmate.data.datasource.local.entity.RoutePointEntity;

import java.util.List;

@Dao
public interface RoutePointDao {

    // 1. Dành cho Service: Lưu 1 tọa độ lấy được từ hệ thống GPS vào DB.
    @Insert
    void insertPoint(RoutePointEntity point);

    // 2. Dành cho UI (ViewModel): Rút toàn bộ dữ liệu ra dưới dạng LiveData.
    // Room sẽ tự động trigger và cập nhật lên UI (Main Thread) bất cứ khi nào DB thay đổi.
    @Query("SELECT * FROM route_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    LiveData<List<RoutePointEntity>> getPointsBySessionId(String sessionId);

    // 3. Dành cho Service đồng bộ (Batching): Lấy thuần data dạng List (những điểm chưa đi)
    @Query("SELECT * FROM route_points WHERE isSynced = 0 ORDER BY timestamp ASC")
    List<RoutePointEntity> getUnsyncedPoints();

    // Khai thác từ DB như "Single Source of Truth" cho chiến lược Batching
    @Query("SELECT COUNT(id) FROM route_points WHERE isSynced = 0")
    int getUnsyncedCount();

    // 4. Dành cho Service đồng bộ: Cập nhật cờ những điểm đã đẩy server thành công.
    @Query("UPDATE route_points SET isSynced = 1 WHERE id IN (:pointIds)")
    void markAsSynced(List<Long> pointIds);
}
