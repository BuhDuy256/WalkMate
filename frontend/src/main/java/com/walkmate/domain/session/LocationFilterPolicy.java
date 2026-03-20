package com.walkmate.domain.session;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.SphericalUtil;

public class LocationFilterPolicy {
    // Chỉ chấp nhận các điểm có bán kính sai số dưới 20 mét
    private static final float MAX_ACCEPTABLE_ACCURACY_METERS = 20.0f;
    // Bắt buộc bước đi cách điểm cũ tối thiểu 3.0m để triệt tiêu vòng lặp nhiễu GPS khi đứng yên
    private static final float MIN_DISPLACEMENT_METERS = 3.0f;

    public boolean isValid(RoutePoint currentPoint, RoutePoint lastAcceptedPoint) {
        // 1. Loại bỏ tín hiệu nhòe nặng
        if (currentPoint.getAccuracy() > MAX_ACCEPTABLE_ACCURACY_METERS) {
            return false;
        }

        // 2. Chống nhiễu khi người dùng đứng im (Spatial Filter)
        if (lastAcceptedPoint != null) {
            double distance = SphericalUtil.computeDistanceBetween(
                    new LatLng(lastAcceptedPoint.getLat(), lastAcceptedPoint.getLng()),
                    new LatLng(currentPoint.getLat(), currentPoint.getLng())
            );
            if (distance < MIN_DISPLACEMENT_METERS) {
                return false; // Chưa đi đủ xa, vẫn bị coi là trôi nhiễu
            }
        }

        return true;
    }
}
