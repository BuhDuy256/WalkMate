package com.walkmate.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.walkmate.data.datasource.local.dao.RoutePointDao;
import com.walkmate.data.datasource.local.entity.RoutePointEntity;
import com.walkmate.domain.session.RoutePoint;
import com.walkmate.domain.session.SessionRepository;

import java.util.ArrayList;
import java.util.List;

public class SessionRepositoryImpl implements SessionRepository {

    private final RoutePointDao routePointDao;

    // Constructor (Sau này nếu bạn dùng Dagger/Hilt thì sẽ thêm @Inject ở đây)
    public SessionRepositoryImpl(RoutePointDao routePointDao) {
        this.routePointDao = routePointDao;
    }

    @Override
    public void saveRoutePoint(RoutePoint point) {
        // Hàm chạy đồng bộ (Synchronous), do tầng trên (Domain Service) quản lý luồng
        // Bước Mapper: Chuyển từ Domain Model sang Entity để Room hiểu
        RoutePointEntity entity = new RoutePointEntity(
                point.getSessionId(),
                point.getLat(),
                point.getLng(),
                point.getTimestamp(),
                point.getAccuracy()
        );
        routePointDao.insertPoint(entity);
    }

    @Override
    public LiveData<List<RoutePoint>> getPointsOfCurrentSession(String sessionId) {
        return Transformations.map(routePointDao.getPointsBySessionId(sessionId), entities -> {
            List<RoutePoint> domainPoints = new ArrayList<>();
            for (RoutePointEntity entity : entities) {
                // Bước Mapper: Chuyển từ Entity ngược lại thành Domain Model
                domainPoints.add(new RoutePoint(
                        entity.id,
                        entity.sessionId,
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
        List<RoutePointEntity> entities = routePointDao.getUnsyncedPoints();
        List<RoutePoint> domainPoints = new ArrayList<>();
        for (RoutePointEntity entity : entities) {
            domainPoints.add(new RoutePoint(
                    entity.id,
                    entity.sessionId,
                    entity.lat, 
                    entity.lng, 
                    entity.timestamp, 
                    entity.accuracy
            ));
        }
        return domainPoints;
    }

    @Override
    public int getUnsyncedCount() {
        return routePointDao.getUnsyncedCount();
    }

    @Override
    public void markPointsAsSynced(List<Long> pointIds) {
        routePointDao.markAsSynced(pointIds);
    }
}
