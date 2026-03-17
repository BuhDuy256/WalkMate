package com.walkmate.tracking;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.walkmate.data.local.dao.SessionPointLocalDao;
import com.walkmate.data.local.db.WalkSessionDatabase;
import com.walkmate.data.local.entity.SessionPointLocalEntity;
import com.walkmate.data.worker.SessionPointSyncWorker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationTrackingService extends Service {

    private static final String CHANNEL_ID = "walk_tracking_channel";
    private static final int NOTIFICATION_ID = 1011;
    private static final float MIN_DISTANCE_METERS = 3.0f;

    private FusedLocationProviderClient fusedLocationClient;
    private SessionPointLocalDao pointLocalDao;
    private ExecutorService ioExecutor;

    private boolean paused;
    private String sessionId;
    private int nextPointOrder;
    private Location lastAccepted;

    private LocationCallback locationCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        pointLocalDao = WalkSessionDatabase.getInstance(this).sessionPointLocalDao();
        ioExecutor = Executors.newSingleThreadExecutor();
        createChannel();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null || paused) {
                    return;
                }
                Location location = locationResult.getLastLocation();
                if (location == null) {
                    return;
                }
                if (lastAccepted != null && location.distanceTo(lastAccepted) < MIN_DISTANCE_METERS) {
                    return;
                }
                lastAccepted = location;

                final int pointOrder = nextPointOrder++;
                final double lat = location.getLatitude();
                final double lng = location.getLongitude();
                final long time = System.currentTimeMillis();

                ioExecutor.execute(() -> {
                    SessionPointLocalEntity point = new SessionPointLocalEntity();
                    point.sessionId = sessionId;
                    point.pointOrder = pointOrder;
                    point.lat = lat;
                    point.lng = lng;
                    point.time = time;
                    point.syncStatus = "PENDING";
                    point.retryCount = 0;
                    point.batchToken = null;
                    point.createdAt = time;
                    point.updatedAt = time;
                    pointLocalDao.insert(point);

                    SessionPointSyncWorker.enqueueNow(getApplicationContext(), sessionId);

                    Intent updateIntent = new Intent(TrackingServiceContract.ACTION_POINT_LOCAL_WRITTEN);
                    updateIntent.putExtra(TrackingServiceContract.EXTRA_SESSION_ID, sessionId);
                    sendBroadcast(updateIntent);
                });
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }

        String action = intent.getAction();
        if (TrackingServiceContract.ACTION_START.equals(action)) {
            sessionId = intent.getStringExtra(TrackingServiceContract.EXTRA_SESSION_ID);
            nextPointOrder = intent.getIntExtra(TrackingServiceContract.EXTRA_NEXT_ORDER, 0);
            paused = false;
            startForeground(NOTIFICATION_ID, buildNotification("Walking in progress"));
            requestLocationUpdates();
        } else if (TrackingServiceContract.ACTION_PAUSE.equals(action)) {
            paused = true;
        } else if (TrackingServiceContract.ACTION_RESUME.equals(action)) {
            paused = false;
        } else if (TrackingServiceContract.ACTION_STOP.equals(action)) {
            stopLocationUpdates();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }

        return START_STICKY;
    }

    private void requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(2000L)
                .setWaitForAccurateLocation(false)
                .build();

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WalkMate")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Walk Tracking",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        ioExecutor.shutdown();
    }
}
