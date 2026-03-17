# WalkSession Implementation Proposal v2 (Code Cụ Thể với Room + Google Maps)

Tài liệu này cung cấp blueprint có code cụ thể để triển khai WalkSession với:

- Local DB bằng Room
- Batch sync points lên backend
- Màn hình trace GPS bằng com.google.android.gms.maps
- Đủ use case: activate, cancel, abort, complete, append points

## 1) Nâng cấp dependencies

## 1.1 Cập nhật gradle/libs.versions.toml

```toml
[versions]
lifecycle = "2.9.2"
retrofit = "2.11.0"
gson = "2.13.1"
room = "2.7.2"
work = "2.10.3"
playServicesMaps = "19.2.0"
playServicesLocation = "21.3.0"

[libraries]
androidx-lifecycle-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel", version.ref = "lifecycle" }
androidx-lifecycle-livedata = { group = "androidx.lifecycle", name = "lifecycle-livedata", version.ref = "lifecycle" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
work-runtime = { group = "androidx.work", name = "work-runtime", version.ref = "work" }
play-services-maps = { group = "com.google.android.gms", name = "play-services-maps", version.ref = "playServicesMaps" }
play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "playServicesLocation" }
```

## 1.2 Cập nhật frontend/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.walkmate"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.walkmate"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.gson)

    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    implementation(libs.work.runtime)

    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
```

## 2) AndroidManifest

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.WalkMate">

        <meta-data
            android:name="com.google.android.geo.API_KEY"
            android:value="${MAPS_API_KEY}" />

        <service
            android:name=".tracking.LocationTrackingService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="location" />

    </application>
</manifest>
```

## 3) Cấu trúc package đề xuất

```text
com.walkmate
├── core
│   ├── Result.java
│   └── ResultCallback.java
├── data
│   ├── local
│   │   ├── db/WalkSessionDatabase.java
│   │   ├── dao/SessionLocalDao.java
│   │   ├── dao/SessionPointLocalDao.java
│   │   ├── entity/SessionLocalEntity.java
│   │   └── entity/SessionPointLocalEntity.java
│   ├── remote
│   │   ├── ApiClient.java
│   │   ├── SessionApi.java
│   │   └── dto/*
│   ├── repository
│   │   ├── SessionRepository.java
│   │   └── SessionRepositoryImpl.java
│   └── worker
│       └── SessionPointSyncWorker.java
├── domain
│   ├── model/RoutePoint.java
│   └── model/SessionScreenStatus.java
├── tracking
│   ├── TrackingCommand.java
│   ├── TrackingServiceContract.java
│   └── LocationTrackingService.java
└── ui
    └── session
        ├── SessionUiState.java
        ├── SessionViewModel.java
        ├── SessionActivity.java
        └── activity_session.xml
```

## 4) DTO và API client

## 4.1 ApiResponseDto.java

```java
package com.walkmate.data.remote.dto;

public class ApiResponseDto<T> {
    public boolean success;
    public T data;
    public ErrorDto error;
    public String timestamp;

    public static class ErrorDto {
        public String code;
        public String message;
    }
}
```

## 4.2 SessionApi.java

```java
package com.walkmate.data.remote;

import com.walkmate.data.remote.dto.ApiResponseDto;
import com.walkmate.data.remote.dto.AppendSessionPointsRequestDto;
import com.walkmate.data.remote.dto.CompleteSessionRequestDto;
import com.walkmate.data.remote.dto.SessionResponseDto;
import com.walkmate.data.remote.dto.SessionTrackingResponseDto;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SessionApi {
    @POST("api/v1/sessions/{id}/activate")
    Call<ApiResponseDto<SessionResponseDto>> activate(@Path("id") String sessionId);

    @POST("api/v1/sessions/{id}/cancel")
    Call<ApiResponseDto<SessionResponseDto>> cancel(@Path("id") String sessionId, @Body Map<String, String> body);

    @POST("api/v1/sessions/{id}/abort")
    Call<ApiResponseDto<SessionResponseDto>> abort(@Path("id") String sessionId, @Body Map<String, String> body);

    @POST("api/v1/sessions/{id}/complete")
    Call<ApiResponseDto<SessionResponseDto>> complete(
            @Path("id") String sessionId,
            @Body CompleteSessionRequestDto body
    );

    @POST("api/v1/sessions/{id}/points:append")
    Call<ApiResponseDto<SessionTrackingResponseDto>> appendPoints(
            @Path("id") String sessionId,
            @Body AppendSessionPointsRequestDto body
    );

    @GET("api/v1/sessions/{id}")
    Call<ApiResponseDto<SessionResponseDto>> getById(@Path("id") String sessionId);
}
```

## 5) Room local model

## 5.1 SessionPointLocalEntity.java

```java
package com.walkmate.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "session_point_local",
        indices = {
                @Index(value = {"sessionId", "syncStatus", "pointOrder"}),
                @Index(value = {"sessionId", "pointOrder"}, unique = true)
        }
)
public class SessionPointLocalEntity {
    @PrimaryKey(autoGenerate = true)
    public long localId;

    @NonNull
    public String sessionId;

    public int pointOrder;
    public double lat;
    public double lng;
    public long time;

    @NonNull
    public String syncStatus; // PENDING, SYNCING, SYNCED, FAILED

    public int retryCount;
    public String batchToken;
    public long createdAt;
    public long updatedAt;
}
```

## 5.2 SessionLocalEntity.java

```java
package com.walkmate.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "session_local")
public class SessionLocalEntity {
    @PrimaryKey
    @NonNull
    public String sessionId;

    @NonNull
    public String backendState; // PENDING, ACTIVE, COMPLETED, CANCELLED, NO_SHOW, ABORTED

    @NonNull
    public String uiState; // IDLE, ACTIVATING, TRACKING_ACTIVE, TRACKING_PAUSED...

    public double totalDistanceMeters;
    public long totalDurationSeconds;

    @NonNull
    public String localTrackerState; // RUNNING, PAUSED, STOPPED

    public boolean hasPendingSync;
    public int lastSyncedPointOrder;

    public String lastErrorCode;
    public String lastErrorMessage;

    public long updatedAt;
}
```

## 5.3 DAO

```java
package com.walkmate.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.walkmate.data.local.entity.SessionPointLocalEntity;

import java.util.List;

@Dao
public interface SessionPointLocalDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(SessionPointLocalEntity entity);

    @Query("SELECT * FROM session_point_local WHERE sessionId = :sessionId ORDER BY pointOrder ASC")
    List<SessionPointLocalEntity> getAllBySession(String sessionId);

    @Query("SELECT * FROM session_point_local WHERE sessionId = :sessionId AND syncStatus = 'PENDING' ORDER BY pointOrder ASC LIMIT :limit")
    List<SessionPointLocalEntity> getPendingBatch(String sessionId, int limit);

    @Query("UPDATE session_point_local SET syncStatus = :syncStatus, batchToken = :batchToken, updatedAt = :updatedAt WHERE localId IN (:ids)")
    void updateBatchStatus(List<Long> ids, String syncStatus, String batchToken, long updatedAt);

    @Query("UPDATE session_point_local SET syncStatus = 'SYNCED', updatedAt = :updatedAt WHERE batchToken = :batchToken")
    void markSyncedByBatch(String batchToken, long updatedAt);

    @Query("UPDATE session_point_local SET syncStatus = 'PENDING', retryCount = retryCount + 1, batchToken = NULL, updatedAt = :updatedAt WHERE batchToken = :batchToken")
    void requeueBatch(String batchToken, long updatedAt);

    @Query("SELECT COUNT(*) FROM session_point_local WHERE sessionId = :sessionId AND syncStatus IN ('PENDING','FAILED','SYNCING')")
    int countUnsynced(String sessionId);
}
```

## 5.4 Database

```java
package com.walkmate.data.local.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.walkmate.data.local.dao.SessionLocalDao;
import com.walkmate.data.local.dao.SessionPointLocalDao;
import com.walkmate.data.local.entity.SessionLocalEntity;
import com.walkmate.data.local.entity.SessionPointLocalEntity;

@Database(entities = {SessionLocalEntity.class, SessionPointLocalEntity.class}, version = 1, exportSchema = false)
public abstract class WalkSessionDatabase extends RoomDatabase {
    public abstract SessionLocalDao sessionLocalDao();
    public abstract SessionPointLocalDao sessionPointLocalDao();
}
```

## 6) Foreground service ghi point vào Room

```java
package com.walkmate.tracking;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import com.walkmate.data.local.entity.SessionPointLocalEntity;

public class LocationTrackingService extends Service {

    private static final String CHANNEL_ID = "walk_tracking";
    private static final int NOTI_ID = 2001;

    private FusedLocationProviderClient fusedClient;
    private LocationCallback callback;

    private String sessionId;
    private int currentOrder;
    private boolean paused;

    private SessionPointLocalDao pointDao;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        createChannel();

        callback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null || paused) return;
                if (result.getLastLocation() == null) return;

                SessionPointLocalEntity entity = new SessionPointLocalEntity();
                entity.sessionId = sessionId;
                entity.pointOrder = currentOrder++;
                entity.lat = result.getLastLocation().getLatitude();
                entity.lng = result.getLastLocation().getLongitude();
                entity.time = System.currentTimeMillis();
                entity.syncStatus = "PENDING";
                entity.retryCount = 0;
                entity.createdAt = System.currentTimeMillis();
                entity.updatedAt = entity.createdAt;

                // pointDao lấy từ singleton DB provider
                pointDao.insert(entity);

                Intent i = new Intent(TrackingServiceContract.ACTION_POINT_LOCAL_WRITTEN);
                i.putExtra(TrackingServiceContract.EXTRA_SESSION_ID, sessionId);
                sendBroadcast(i);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();

        if (TrackingServiceContract.ACTION_START.equals(action)) {
            sessionId = intent.getStringExtra(TrackingServiceContract.EXTRA_SESSION_ID);
            currentOrder = intent.getIntExtra(TrackingServiceContract.EXTRA_NEXT_ORDER, 0);
            paused = false;
            startForeground(NOTI_ID, buildNotification("Walking in progress"));
            requestLocation();
        } else if (TrackingServiceContract.ACTION_PAUSE.equals(action)) {
            paused = true;
        } else if (TrackingServiceContract.ACTION_RESUME.equals(action)) {
            paused = false;
        } else if (TrackingServiceContract.ACTION_STOP.equals(action)) {
            fusedClient.removeLocationUpdates(callback);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }

        return START_STICKY;
    }

    private void requestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(2000L)
                .setWaitForAccurateLocation(false)
                .build();

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper());
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("WalkMate Tracking")
                .setContentText(text)
                .setOngoing(true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "Walk Tracking", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(c);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
```

## 7) Worker sync points theo batch

```java
package com.walkmate.data.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.walkmate.data.local.dao.SessionPointLocalDao;
import com.walkmate.data.local.entity.SessionPointLocalEntity;
import com.walkmate.data.remote.SessionApi;
import com.walkmate.data.remote.dto.AppendPointItemDto;
import com.walkmate.data.remote.dto.AppendSessionPointsRequestDto;
import com.walkmate.data.remote.dto.ApiResponseDto;
import com.walkmate.data.remote.dto.SessionTrackingResponseDto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import retrofit2.Response;

public class SessionPointSyncWorker extends Worker {

    public SessionPointSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String sessionId = getInputData().getString("session_id");
        if (sessionId == null) return Result.failure();

        SessionPointLocalDao dao = null; // lấy từ singleton database
        SessionApi api = null; // lấy từ ApiClient

        List<SessionPointLocalEntity> batch = dao.getPendingBatch(sessionId, 30);
        if (batch.isEmpty()) return Result.success();

        String batchToken = UUID.randomUUID().toString();
        List<Long> ids = new ArrayList<>();
        List<AppendPointItemDto> points = new ArrayList<>();

        for (SessionPointLocalEntity e : batch) {
            ids.add(e.localId);
            points.add(new AppendPointItemDto(e.pointOrder, e.lat, e.lng, e.time));
        }

        dao.updateBatchStatus(ids, "SYNCING", batchToken, System.currentTimeMillis());

        AppendSessionPointsRequestDto req = new AppendSessionPointsRequestDto(points, 0.0, 0L);

        try {
            Response<ApiResponseDto<SessionTrackingResponseDto>> response = api.appendPoints(sessionId, req).execute();
            if (response.isSuccessful() && response.body() != null && response.body().success) {
                dao.markSyncedByBatch(batchToken, System.currentTimeMillis());
                return Result.success();
            }

            dao.requeueBatch(batchToken, System.currentTimeMillis());
            return Result.retry();
        } catch (Exception ex) {
            dao.requeueBatch(batchToken, System.currentTimeMillis());
            return Result.retry();
        }
    }
}
```

## 8) ViewModel cho đủ 5 use case

```java
package com.walkmate.ui.session;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SessionViewModel extends ViewModel {

    private final MutableLiveData<SessionUiState> uiState = new MutableLiveData<>(SessionUiState.idle());

    public LiveData<SessionUiState> getUiState() {
        return uiState;
    }

    public void activate(String sessionId) {
        // 1) set ACTIVATING
        // 2) gọi API activate
        // 3) success -> TRACKING_ACTIVE + start service + schedule sync
        // 4) fail -> ERROR_RETRYABLE hoặc ERROR_BLOCKING + reconcile
    }

    public void cancel(String sessionId, String reason) {
        // gọi API cancel
        // success -> COMPLETED_VIEW (cancelled)
    }

    public void abort(String sessionId, String reason) {
        // gọi API abort
        // success -> COMPLETED_VIEW (aborted)
    }

    public void complete(String sessionId) {
        // flush pending points local trước
        // sau đó gọi API complete
        // success -> COMPLETED_VIEW
    }

    public void pauseTracking() {
        // local UI state TRACKING_PAUSED
    }

    public void resumeTracking() {
        // local UI state TRACKING_ACTIVE
    }
}
```

## 9) Activity dùng com.google.android.gms.maps để trace GPS

```java
package com.walkmate.ui.session;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.walkmate.R;

import java.util.ArrayList;
import java.util.List;

public class SessionActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap googleMap;
    private Polyline routePolyline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Observe Room stream points và gọi renderRouteFromLocal(points)
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
    }

    private void renderRouteFromLocal(List<LatLng> points) {
        if (googleMap == null) return;

        if (routePolyline == null) {
            routePolyline = googleMap.addPolyline(new PolylineOptions()
                    .width(10f)
                    .color(0xFF34C759)
                    .addAll(points));
        } else {
            routePolyline.setPoints(points);
        }

        if (!points.isEmpty()) {
            LatLng last = points.get(points.size() - 1);
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(last, 17f));
        }
    }

    private List<LatLng> toLatLngList(List<com.walkmate.data.local.entity.SessionPointLocalEntity> entities) {
        List<LatLng> out = new ArrayList<>();
        for (com.walkmate.data.local.entity.SessionPointLocalEntity e : entities) {
            out.add(new LatLng(e.lat, e.lng));
        }
        return out;
    }
}
```

## 10) Layout activity_session.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <fragment
        android:id="@+id/mapFragment"
        android:name="com.google.android.gms.maps.SupportMapFragment"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <LinearLayout
        android:id="@+id/bottomPanel"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:orientation="vertical"
        android:padding="16dp"
        android:background="#EFFFFFFF">

        <!-- Partner card + stats card + CTA bám mock Init/Progress/Paused -->

    </LinearLayout>

</FrameLayout>
```

## 11) Mapping use case với code điểm chạm

- activate:
  - SessionViewModel.activate
  - SessionApi.activate
  - thành công thì start service + enqueue worker

- append points:
  - LocationTrackingService ghi Room
  - SessionPointSyncWorker gửi batch points:append

- complete:
  - SessionViewModel.complete
  - flush unsynced -> SessionApi.complete

- cancel:
  - SessionViewModel.cancel
  - SessionApi.cancel

- abort:
  - SessionViewModel.abort
  - SessionApi.abort

## 12) Ràng buộc DDD và eventual consistency

- Backend luôn là source of truth cho lifecycle state.
- FE chỉ giữ replica local để render realtime và chống mất dữ liệu.
- Mỗi lỗi invalid transition phải reconcile bằng GET /sessions/{id}.
- Application service backend không chứa business rule, domain mới là nơi enforce invariant.

## 13) Checklist chạy được end-to-end

1. Thêm dependencies Maps/Location/Room/WorkManager/Retrofit.
2. Tạo SessionActivity + activity_session.xml với SupportMapFragment.
3. Tạo Room entities + DAO + database singleton.
4. Tạo service ghi points local.
5. Tạo worker sync batch.
6. Nối ViewModel use cases activate/cancel/abort/complete.
7. Wire UI buttons theo 3 trạng thái Init/Progress/Paused.
8. Test mất mạng, kill app, resume sync, complete sau flush.

Với bộ code skeleton này, bạn có thể triển khai trực tiếp màn trace GPS dùng com.google.android.gms.maps và vẫn đáp ứng đầy đủ use case WalkSession theo thiết kế v2.
