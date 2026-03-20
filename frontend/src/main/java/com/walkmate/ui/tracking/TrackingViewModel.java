package com.walkmate.ui.tracking;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.SphericalUtil;
import com.walkmate.WalkMateApplication;
import com.walkmate.domain.session.RoutePoint;
import com.walkmate.domain.session.SessionRepository;

import java.util.ArrayList;
import java.util.List;

public class TrackingViewModel extends AndroidViewModel {

    private final SessionRepository repository;
    
    // BIẾN TRIGGER: Giữ Session ID hiện tại.
    private final MutableLiveData<String> sessionTrigger = new MutableLiveData<>();
    
    // BIẾN STATE: Được nối vĩnh viễn với View.
    private final LiveData<TrackingUiState> uiStateLiveData;

    // CAMERA FLAG
    private final MutableLiveData<Boolean> isCameraFollowingUser = new MutableLiveData<>(true);
    
    // BIẾN LƯU VẾT (CACHING) CHO THUẬT TOÁN TÍNH QUÃNG ĐƯỜNG O(1)
    private float cachedTotalDistance = 0f;
    private int lastProcessedListSize = 0;
    private RoutePoint lastProcessedPoint = null;

    public TrackingViewModel(@NonNull Application application) {
        super(application);
        this.repository = ((WalkMateApplication) application).getSessionRepository();

        // TUYỆT KỸ SWITCH_MAP
        uiStateLiveData = Transformations.switchMap(sessionTrigger, sessionId -> {
            return Transformations.map(repository.getPointsOfCurrentSession(sessionId), domainPoints -> {
                
                List<LatLng> mapPoints = new ArrayList<>(domainPoints.size()); 
                for (RoutePoint p : domainPoints) {
                    mapPoints.add(new LatLng(p.getLat(), p.getLng()));
                }

                // TỐI ƯU O(1) TÍNH QUÃNG ĐƯỜNG
                if (lastProcessedListSize == 0 && mapPoints.size() > 1) {
                    // Lần đầu tiên load từ DB lên
                    cachedTotalDistance = (float) SphericalUtil.computeLength(mapPoints);
                } 
                else if (domainPoints.size() > lastProcessedListSize && lastProcessedPoint != null) {
                    // Tính khoảng cách của những điểm MỚI thêm vào
                    for (int i = lastProcessedListSize; i < domainPoints.size(); i++) {
                        RoutePoint newPoint = domainPoints.get(i);
                        cachedTotalDistance += SphericalUtil.computeDistanceBetween(
                                new LatLng(lastProcessedPoint.getLat(), lastProcessedPoint.getLng()),
                                new LatLng(newPoint.getLat(), newPoint.getLng())
                        );
                        lastProcessedPoint = newPoint; 
                    }
                }
                
                // Cập nhật mốc size cho vòng lặp sau
                lastProcessedListSize = domainPoints.size();
                if (domainPoints.size() > 0) {
                    lastProcessedPoint = domainPoints.get(domainPoints.size() - 1);
                }

                return new TrackingUiState(true, mapPoints, cachedTotalDistance);
            });
        });
    }

    public void startTrackingSession(String sessionId) {
        cachedTotalDistance = 0f;
        lastProcessedListSize = 0;
        lastProcessedPoint = null;
        sessionTrigger.setValue(sessionId);
    }

    public LiveData<TrackingUiState> getUiState() {
        return uiStateLiveData;
    }

    public LiveData<Boolean> getCameraFollowState() {
        return isCameraFollowingUser;
    }

    public void setCameraFollow(boolean isFollowing) {
        isCameraFollowingUser.setValue(isFollowing);
    }

    public void stopTracking() {
        // Có thể lưu trạng thái end vào DB...
        // Tín hiệu tắt Service sẽ được gửi qua UiEffect thay vì intent trực tiếp ở đây
    }
}
