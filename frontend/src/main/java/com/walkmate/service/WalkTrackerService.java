package com.walkmate.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.domain.tracking.SessionTrackingService;
import com.walkmate.ui.tracking.TrackingScreenActivity;

/**
 * Android Foreground Service that acquires GPS fixes and feeds them to the
 * domain-layer {@link SessionTrackingService} for filtering and persistence.
 *
 * ── How to start ─────────────────────────────────────────────────────────────
 * <pre>
 *   Intent intent = new Intent(context, WalkTrackerService.class);
 *   intent.putExtra(EXTRA_SESSION_ID, sessionId);
 *   ContextCompat.startForegroundService(context, intent);
 * </pre>
 *
 * ── PHASE 2 FIX 1: No hardcoded session ID ───────────────────────────────────
 * {@code SESSION_ID} is mandatory in the launch Intent. If it is absent,
 * the service logs an error and calls {@link #stopSelf()} immediately — the
 * caller is responsible for providing a valid ID.
 *
 * ── PHASE 2 FIX 2: Executor shutdown on destroy ───────────────────────────────
 * {@code onDestroy()} calls {@link SessionTrackingService#stopTracking()} which
 * gracefully shuts down the domain service's executor, allowing any pending
 * Room writes to complete before the thread dies.
 *
 * ── PHASE 2 FIX 3: Location updates stopped on destroy ───────────────────────
 * {@code onDestroy()} removes the {@link LocationCallback} from
 * {@link FusedLocationProviderClient} before releasing the reference,
 * preventing a callback on a dead service instance.
 */
public class WalkTrackerService extends Service {

    // ── Intent extras ─────────────────────────────────────────────────────────

    public static final String EXTRA_SESSION_ID    = "SESSION_ID";
    public static final String EXTRA_PARTNER_NAME  = "PARTNER_NAME";
    public static final String EXTRA_MEETING_LAT   = "MEETING_POINT_LAT";
    public static final String EXTRA_MEETING_LNG   = "MEETING_POINT_LNG";

    // ── Notification ──────────────────────────────────────────────────────────

    private static final String CHANNEL_ID      = "walk_tracker_channel";
    private static final int    NOTIFICATION_ID = 1001;

    // ── Location config ───────────────────────────────────────────────────────

    /** How often to request a new GPS fix (milliseconds). */
    private static final long LOCATION_INTERVAL_MS     = 5_000L;
    /** Fastest interval — ignored if no other app is requesting faster. */
    private static final long LOCATION_FASTEST_MS      = 1_500L;
    /** Minimum movement between fixes delivered to the callback. */
    private static final float LOCATION_MIN_DISTANCE_M = 1.0f;

    private static final String TAG = "WalkTrackerService";

    // ── Instance state ────────────────────────────────────────────────────────

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private SessionTrackingService sessionTrackingService;
    private String sessionId;

    // ── Service lifecycle ─────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        Log.d(TAG, "Service created");
    }

    /**
     * Entry point when the Activity starts this service.
     *
     * ── PHASE 2 FIX: No fallback session ID ───────────────────────────────────
     * If {@code EXTRA_SESSION_ID} is missing or empty the service stops itself
     * immediately. This prevents the "phantom session" bug where location points
     * were silently written under a throwaway ID that never matched any real
     * WalkSession in the database.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // ── Validate session ID ────────────────────────────────────────────────
        if (intent == null || intent.getStringExtra(EXTRA_SESSION_ID) == null
                || intent.getStringExtra(EXTRA_SESSION_ID).trim().isEmpty()) {
            Log.e(TAG, "EXTRA_SESSION_ID is missing — refusing to track without a real session ID.");
            stopSelf();
            return START_NOT_STICKY;
        }

        sessionId = intent.getStringExtra(EXTRA_SESSION_ID);
        String partnerName = intent.getStringExtra(EXTRA_PARTNER_NAME);
        Log.d(TAG, "Tracking started for session=" + sessionId
                + ", partner=" + partnerName);

        // ── Wire domain service ────────────────────────────────────────────────
        WalkMateApplication app = (WalkMateApplication) getApplicationContext();
        sessionTrackingService = new SessionTrackingService(app.getTrackingRepository());
        sessionTrackingService.startSession(sessionId);

        // ── Move to foreground immediately ─────────────────────────────────────
        startForegroundWithNotification(partnerName);

        // ── Start GPS ─────────────────────────────────────────────────────────
        startLocationUpdates();

        return START_NOT_STICKY;
    }

    /**
     * ── PHASE 2 FIX: Full teardown on destroy ─────────────────────────────────
     * Called when the Activity calls {@code stopService()} or the OS reclaims
     * resources. Always removes the location callback first (avoids a late
     * delivery on a dead instance), then gracefully drains the domain executor.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        // 1. Stop GPS hardware — must happen before releasing the callback reference.
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            Log.d(TAG, "Location updates removed");
        }

        // 2. Drain the domain executor so in-flight Room writes can complete.
        if (sessionTrackingService != null) {
            sessionTrackingService.stopTracking();
        }

        Log.d(TAG, "Service destroyed — session=" + sessionId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Started service — no binding needed
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
                .setMinUpdateIntervalMillis(LOCATION_FASTEST_MS)
                .setMinUpdateDistanceMeters(LOCATION_MIN_DISTANCE_M)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null || sessionTrackingService == null) return;

                // FusedLocationProviderClient delivers on the Looper we provided
                // (main thread). Pass straight to the domain service which hands
                // the work off to its own executor thread.
                android.location.Location loc = result.getLastLocation();
                if (loc != null) {
                    sessionTrackingService.onLocationReceived(
                            loc.getLatitude(),
                            loc.getLongitude(),
                            loc.getAccuracy());
                }
            }
        };

        try {
            fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper());
            Log.d(TAG, "Location updates requested");
        } catch (SecurityException e) {
            // ACCESS_FINE_LOCATION not granted — the Activity is responsible for
            // checking and requesting this permission before starting the service.
            Log.e(TAG, "Location permission not granted: " + e.getMessage());
            stopSelf();
        }
    }

    private void startForegroundWithNotification(@Nullable String partnerName) {
        // Build a tap-to-return PendingIntent so the user can get back to the
        // tracking screen from the notification shade.
        Intent resumeIntent = new Intent(this, TrackingScreenActivity.class);
        resumeIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int pendingFlags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, resumeIntent, pendingFlags);

        String contentText = (partnerName != null && !partnerName.isEmpty())
                ? "Walking with " + partnerName
                : "Tracking your route…";

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Walk in progress")
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)               // cannot be dismissed by swipe
                .setPriority(NotificationCompat.PRIORITY_LOW) // quiet — no sound
                .build();

        // startForeground with serviceType is required for targetSdk >= 34
        // and available since API 29.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Walk Tracker",
                    NotificationManager.IMPORTANCE_LOW // silent
            );
            channel.setDescription("Shows while a walk session is active");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
