package com.walkmate.core.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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

public class WalkTrackerService extends Service {

    private static final String TAG = "WalkTrackerService";
    private static final String CHANNEL_ID = "WalkTrackingChannel";
    private static final int NOTIFICATION_ID = 1;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    
    private String sessionId;
    private com.walkmate.domain.session.SessionTrackingService sessionTrackingService;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service Created");

        // Lấy Repository từ Application (Service Locator)
        com.walkmate.domain.session.SessionRepository repository = 
                ((com.walkmate.WalkMateApplication) getApplication()).getSessionRepository();
        sessionTrackingService = new com.walkmate.domain.session.SessionTrackingService(repository);

        // 1. Khởi tạo client lấy GPS
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 2. Khai báo callback (Hành động khi có tọa độ mới)
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                
                // Lấy tọa độ mới nhất và đẩy vào Service xử lý
                for (android.location.Location location : locationResult.getLocations()) {
                    // LOG THỰC TẾ: Báo cho bạn biết thiết bị VỪA BẮT ĐƯỢC 1 tọa độ thô (bất chấp sai số)
                    Log.d("SessionTracking", "📍 [RAW] Bắt được GPS thật: Lat=" + location.getLatitude() +
                            ", Lng=" + location.getLongitude() + ", Sai số=" + location.getAccuracy() + "m");

                    if (sessionId != null) {
                        com.walkmate.domain.session.RoutePoint domainPoint = new com.walkmate.domain.session.RoutePoint(
                                sessionId,
                                location.getLatitude(),
                                location.getLongitude(),
                                location.getTime(),
                                location.getAccuracy()
                        );
                        sessionTrackingService.processNewLocation(domainPoint);
                    } else {
                        Log.e(TAG, "Tracking failed: sessionId is null");
                    }
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service Started");
        
        if (intent != null && intent.hasExtra("SESSION_ID")) {
            sessionId = intent.getStringExtra("SESSION_ID");
        }
        
        // Cứu cánh: Nếu nhấn nút Start trên MainActivity mà quên truyền Intent
        if (sessionId == null) {
            sessionId = "test-session-" + System.currentTimeMillis();
            Log.d(TAG, "Tự động tạo SESSION_ID giả định: " + sessionId);
        }
        
        // 1. Hiển thị Notification và đưa Service lên Foreground
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WalkMate đang theo dõi...")
                .setContentText("Đang ghi nhận quãng đường của bạn")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        // 2. Bắt đầu yêu cầu lấy GPS liên tục
        requestLocationUpdates();

        return START_STICKY; 
    }

    private void requestLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000)
                .build();

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            Log.d(TAG, "Đã đăng ký nhận GPS thành công");
        } catch (SecurityException e) {
            Log.e(TAG, "Mất quyền GPS: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service Destroyed");
        if (fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; 
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WalkMate Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
