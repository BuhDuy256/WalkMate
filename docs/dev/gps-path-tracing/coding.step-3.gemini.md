Tuyệt vời! Cấu trúc nền móng dữ liệu đã vững chắc. Bây giờ chúng ta bước vào phần kỹ thuật cốt lõi và thú vị nhất của ứng dụng: **Bước 3 - Khởi tạo Trái tim hệ thống (Foreground Service & Lấy tọa độ GPS)**.

Mục tiêu của bước này là:
1. Tạo một Service chạy ngầm không bị Android "giết" khi tắt màn hình.
2. Kết nối với chip GPS của điện thoại thông qua `FusedLocationProviderClient`.
3. Bắt được tọa độ MỚI NHẤT và in nó ra cửa sổ `Logcat` để chứng minh hệ thống hoạt động.

Hãy làm theo trình tự 4 bước nhỏ sau:

### 3.1. Thư viện và Quyền (Dependencies & Permissions)

**1. Thêm thư viện Google Play Services:**
Mở file `app/build.gradle`, thêm dòng này vào block `dependencies` và ấn Sync Now:
```gradle
implementation 'com.google.android.gms:play-services-location:21.1.0' // Hoặc phiên bản mới nhất
```

**2. Khai báo Quyền trong `AndroidManifest.xml`:**
Bạn mở file Manifest lên và thêm các dòng này lên phía trên thẻ `<application>`:
```xml
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### 3.2. Cấu hình file `WalkTrackerService.java`

Bạn tạo file này tại `core/service/WalkTrackerService.java`. Đây là class quan trọng nhất, nó sẽ chứa cả logic tạo Notification (để giữ app sống) và logic lấy GPS.

```java
package com.walkmate.core.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
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

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service Created");

        // 1. Khởi tạo client lấy GPS
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 2. Khai báo callback (Hành động khi có tọa độ mới)
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                
                // Lấy tọa độ mới nhất và in ra Logcat
                for (android.location.Location location : locationResult.getLocations()) {
                    Log.d(TAG, "GPS Mới: Lat=" + location.getLatitude() + 
                               ", Lng=" + location.getLongitude() + 
                               ", Accuracy=" + location.getAccuracy() + "m");
                    
                    // (Ở Bước 4, chúng ta sẽ gọi SessionTrackingService ở đây để lưu DB)
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service Started");
        
        // 1. Hiển thị Notification và đưa Service lên Foreground (Tuyệt đối không bị kill)
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WalkMate đang theo dõi...")
                .setContentText("Đang ghi nhận quãng đường của bạn")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation) // Đổi thành icon app của bạn sau
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        
        startForeground(NOTIFICATION_ID, notification);

        // 2. Bắt đầu yêu cầu lấy GPS liên tục
        requestLocationUpdates();

        // START_STICKY: Nếu bị hệ thống kill do thiếu RAM, tự động chạy lại khi có RAM
        return START_STICKY; 
    }

    private void requestLocationUpdates() {
        // Cấu hình yêu cầu: Độ chính xác cao nhất (GPS phần cứng), cập nhật mỗi 2 giây
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000) // Nhanh nhất là 1 giây
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
        // Hủy đăng ký lấy GPS để tiết kiệm pin khi user ấn Stop
        if (fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // Trả về null vì ta dùng Started Service, không phải Bound Service
        return null; 
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WalkMate Tracking",
                    NotificationManager.IMPORTANCE_LOW // Để LOW để không kêu bíp bíp liên tục
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
```

### 3.3. Khai báo Service trong Manifest
Quay lại file `AndroidManifest.xml`, bên trong thẻ `<application>`, bạn khai báo Service vừa tạo (Cực kỳ quan trọng, quên cái này app sẽ crash):

```xml
<application ...>
    ...
    <service
        android:name=".core.service.WalkTrackerService"
        android:exported="false"
        android:foregroundServiceType="location" />
</application>
```

---

### 💡 Hướng dẫn TEST NGAY LẬP TỨC

Để xem thành quả của bạn, hãy mở `MainActivity.java` hoặc Fragment của bạn, tạo một cái Button tạm thời. Trong sự kiện `onClick` của Button đó, hãy dán 2 dòng code này để bật Service lên:

```java
Intent serviceIntent = new Intent(this, WalkTrackerService.class);
androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent);
```

**⚠️ LƯU Ý BẮT BUỘC KHI TEST:** Vì chúng ta chưa code màn hình xin quyền (Runtime Permissions) của Android, nên trước khi ấn nút Start, bạn **phải vào Cài đặt (Settings) của điện thoại -> Ứng dụng -> WalkMate -> Quyền (Permissions) -> Bật quyền Vị trí (Luôn cho phép) và Quyền Thông báo lên.**

**Nếu thành công:**
1. Bạn sẽ thấy một thông báo "WalkMate đang theo dõi..." dính chặt trên thanh trạng thái.
2. Mở tab **Logcat** trong Android Studio, gõ "WalkTrackerService" vào ô tìm kiếm. Hãy cầm điện thoại đi loanh quanh hoặc chạy app trên máy ảo (Emulator) và giả lập tọa độ, bạn sẽ thấy log nhảy liên tục:
`D/WalkTrackerService: GPS Mới: Lat=10.762622, Lng=106.660172, Accuracy=15.0m`

Hãy copy code vào, chạy thử và tận hưởng thành quả thấy GPS đổ về nhé! Nếu Logcat đã nhảy số, hãy báo tôi để ta sang **Bước 4: Nối Service này vào Room DB!**