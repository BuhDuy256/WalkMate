package com.walkmate.domain.session;

public class LocationFilterPolicy {
    // Chỉ chấp nhận các điểm có bán kính sai số dưới 20 mét
    private static final float MAX_ACCEPTABLE_ACCURACY_METERS = 20.0f;

    public boolean isValid(RoutePoint point) {
        return point.getAccuracy() <= MAX_ACCEPTABLE_ACCURACY_METERS;
    }
}
