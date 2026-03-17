package com.walkmate.tracking;

public final class TrackingServiceContract {
    private TrackingServiceContract() {
    }

    public static final String ACTION_START = "com.walkmate.tracking.START";
    public static final String ACTION_PAUSE = "com.walkmate.tracking.PAUSE";
    public static final String ACTION_RESUME = "com.walkmate.tracking.RESUME";
    public static final String ACTION_STOP = "com.walkmate.tracking.STOP";
    public static final String ACTION_POINT_LOCAL_WRITTEN = "com.walkmate.tracking.POINT_LOCAL_WRITTEN";

    public static final String EXTRA_SESSION_ID = "extra_session_id";
    public static final String EXTRA_NEXT_ORDER = "extra_next_order";
}
