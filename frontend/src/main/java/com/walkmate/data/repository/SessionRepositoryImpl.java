package com.walkmate.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.walkmate.data.datasource.local.dao.RoutePointDao;
import com.walkmate.data.datasource.local.entity.RoutePointEntity;
import com.walkmate.domain.session.RoutePoint;
import com.walkmate.domain.session.SessionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionRepositoryImpl implements SessionRepository {

    private final RoutePointDao routePointDao;

    // Vì ta không dùng RxJava hay Coroutines, ta dùng ExecutorService (Java thuần)
    // để chạy các tác vụ thêm/sửa/xóa DB ở background, tránh làm đơ Main Thread (UI).
    private final ExecutorService executorService;

    // Constructor (Sau này nếu bạn dùng Dagger/Hilt thì sẽ thêm @Inject ở đây)
    public SessionRepositoryImpl(RoutePointDao routePointDao) {
        this.routePointDao = routePointDao;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public void saveRoutePoint(RoutePoint point) {
        // Chạy ngầm: Tránh lỗi "Cannot access database on the main thread" của Room
        executorService.execute(() -> {
            // Bước Mapper: Chuyển từ Domain Model sang Entity để Room hiểu
            RoutePointEntity entity = new RoutePointEntity(
                    point.getLat(),
                    point.getLng(),
                    point.getTimestamp(),
                    point.getAccuracy()
            );
            routePointDao.insertPoint(entity);
        });
    }

    @Override
    public LiveData<List<RoutePoint>> getPointsOfCurrentSession() {
        // Room trả về LiveData<List<RoutePointEntity>>.
        // Nhưng UI và Domain chỉ muốn nhận LiveData<List<RoutePoint>>.
        // Giải pháp: Dùng Transformations.map để "dịch" (map) dữ liệu ngay khi Room nhả ra.
        return Transformations.map(routePointDao.getAllPoints(), entities -> {
            List<RoutePoint> domainPoints = new ArrayList<>();
            for (RoutePointEntity entity : entities) {
                // Bước Mapper: Chuyển từ Entity ngược lại thành Domain Model
                domainPoints.add(new RoutePoint(
                        entity.id, // Nhớ thêm id vào class RoutePoint ở Bước 1 nhé
                        entity.lat,
                        entity.lng,
                        entity.timestamp,
                        entity.accuracy
                ));
            }
            return domainPoints;
        });
    }

    @Override
    public List<RoutePoint> getUnsyncedPoints() {
        // Lưu ý: Hàm này sẽ được gọi từ Background Thread trong logic Sync sau này,
        // nên ta gọi trực tiếp DB mà không cần ExecutorService bọc ngoài.
        List<RoutePointEntity> entities = routePointDao.getUnsyncedPoints();
        List<RoutePoint> domainPoints = new ArrayList<>();
        for (RoutePointEntity entity : entities) {
            domainPoints.add(new RoutePoint(
                    entity.id, 
                    entity.lat, 
                    entity.lng, 
                    entity.timestamp, 
                    entity.accuracy
            ));
        }
        return domainPoints;
    }

    @Override
    public void markPointsAsSynced(List<Long> pointIds) {
        executorService.execute(() -> {
            routePointDao.markAsSynced(pointIds);
        });
    }
}
