package com.walkmate.domain.walkpost;

import android.util.Log;

public enum PostVisibility {
    PUBLIC, FRIENDS, PRIVATE;

    private static final String TAG = "PostVisibility";

    public static PostVisibility from(String raw) {
        if (raw == null) {
            Log.w(TAG, "Received null visibility from backend, falling back to PRIVATE");
            return PRIVATE;
        }
        try {
            return valueOf(raw);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Unknown visibility value from backend: '" + raw + "', falling back to PRIVATE");
            return PRIVATE;
        }
    }

    public String toDisplayLabel() {
        switch (this) {
            case PUBLIC:  return "Public";
            case FRIENDS: return "Friends";
            case PRIVATE: return "Only me";
            default:      return "Only me";
        }
    }
}
