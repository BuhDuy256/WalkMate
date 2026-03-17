Dưới đây là một skeleton hoàn chỉnh theo đúng MVVM-lite bạn đưa ra:

* `Activity` chỉ render + delegate action
* `ViewModel` giữ `LiveData<UiState>`
* `Repository` gọi Retrofit và trả `Result<T>`
* GPS chạy trong **Android Foreground Service**
* UI map update realtime mà **không reload Map layer**
* Backend chỉ call đúng các API bạn đã có:

  * `POST /sessions/{id}/activate`
  * `POST /sessions/{id}/points:append`
  * `POST /sessions/{id}/complete`
  * `POST /sessions/{id}/abort`
  * `POST /sessions/{id}/cancel`

Tôi chọn cách này để bám sát guideline của bạn và vẫn code được ngay.

---

# 1. Gradle dependencies

```gradle
dependencies {
    implementation "androidx.appcompat:appcompat:1.7.0"
    implementation "androidx.lifecycle:lifecycle-livedata:2.8.4"
    implementation "androidx.lifecycle:lifecycle-viewmodel:2.8.4"

    implementation "com.google.android.gms:play-services-maps:19.0.0"
    implementation "com.google.android.gms:play-services-location:21.3.0"

    implementation "com.squareup.retrofit2:retrofit:2.11.0"
    implementation "com.squareup.retrofit2:converter-gson:2.11.0"
    implementation "com.google.code.gson:gson:2.11.0"
}
```

---

# 2. AndroidManifest

```xml
<manifest ...>

    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

    <application ...>
        <service
            android:name=".core.tracking.LocationTrackingService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="location" />
    </application>
</manifest>
```

---

# 3. Folder structure

```text
com.walkmate
├── core
│   ├── common
│   │   ├── Result.java
│   │   └── ResultCallback.java
│   ├── tracking
│   │   ├── TrackingCommand.java
│   │   ├── TrackingEvent.java
│   │   ├── TrackingServiceContract.java
│   │   ├── TrackingMath.java
│   │   └── LocationTrackingService.java
│   └── util
│       └── PermissionUtil.java
├── data
│   ├── model
│   │   ├── ApiResponseDto.java
│   │   ├── SessionResponseDto.java
│   │   ├── ActivateResponseDto.java
│   │   ├── AppendPointItemDto.java
│   │   ├── AppendSessionPointsRequestDto.java
│   │   ├── CompleteSessionRequestDto.java
│   │   └── SessionTrackingResponseDto.java
│   ├── remote
│   │   ├── SessionApi.java
│   │   └── ApiClient.java
│   └── repository
│       └── SessionRepositoryImpl.java
├── domain
│   ├── model
│   │   ├── RoutePoint.java
│   │   └── SessionModel.java
│   └── repository
│       └── SessionRepository.java
└── ui
    └── session
        ├── SessionUiState.java
        ├── SessionViewModel.java
        └── SessionActivity.java
```

---

# 4. Core common

## `core/common/Result.java`

```java
package com.walkmate.core.common;

public class Result<T> {
    private final T data;
    private final Throwable error;

    private Result(T data, Throwable error) {
        this.data = data;
        this.error = error;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(data, null);
    }

    public static <T> Result<T> error(Throwable error) {
        return new Result<>(null, error);
    }

    public boolean isSuccess() {
        return error == null;
    }

    public T getData() {
        return data;
    }

    public Throwable getError() {
        return error;
    }
}
```

## `core/common/ResultCallback.java`

```java
package com.walkmate.core.common;

public interface ResultCallback<T> {
    void onResult(Result<T> result);
}
```

---

# 5. Domain models

## `domain/model/RoutePoint.java`

```java
package com.walkmate.domain.model;

public class RoutePoint {
    public final int pointOrder;
    public final double lat;
    public final double lng;
    public final long time;

    public RoutePoint(int pointOrder, double lat, double lng, long time) {
        this.pointOrder = pointOrder;
        this.lat = lat;
        this.lng = lng;
        this.time = time;
    }
}
```

## `domain/model/SessionModel.java`

```java
package com.walkmate.domain.model;

public class SessionModel {
    public final String id;
    public final String status;

    public SessionModel(String id, String status) {
        this.id = id;
        this.status = status;
    }
}
```

## `domain/repository/SessionRepository.java`

```java
package com.walkmate.domain.repository;

import com.walkmate.core.common.ResultCallback;
import com.walkmate.domain.model.RoutePoint;
import com.walkmate.domain.model.SessionModel;

import java.util.List;

public interface SessionRepository {
    void activateSession(String sessionId, ResultCallback<SessionModel> callback);

    void appendPoints(
            String sessionId,
            List<RoutePoint> points,
            double totalDistanceMeters,
            long totalDurationSeconds,
            ResultCallback<Integer> callback
    );

    void completeSession(
            String sessionId,
            double totalDistanceMeters,
            long totalDurationSeconds,
            ResultCallback<SessionModel> callback
    );

    void abortSession(
            String sessionId,
            String reason,
            ResultCallback<SessionModel> callback
    );

    void cancelSession(
            String sessionId,
            String reason,
            ResultCallback<SessionModel> callback
    );
}
```

---

# 6. Retrofit DTOs

## `data/model/ApiResponseDto.java`

```java
package com.walkmate.data.model;

public class ApiResponseDto<T> {
    public T data;
    public String message;
}
```

## `data/model/SessionResponseDto.java`

```java
package com.walkmate.data.model;

public class SessionResponseDto {
    public String id;
    public String status;
}
```

## `data/model/AppendPointItemDto.java`

```java
package com.walkmate.data.model;

public class AppendPointItemDto {
    public int pointOrder;
    public double lat;
    public double lng;
    public long time;

    public AppendPointItemDto(int pointOrder, double lat, double lng, long time) {
        this.pointOrder = pointOrder;
        this.lat = lat;
        this.lng = lng;
        this.time = time;
    }
}
```

## `data/model/AppendSessionPointsRequestDto.java`

```java
package com.walkmate.data.model;

import java.util.List;

public class AppendSessionPointsRequestDto {
    public List<AppendPointItemDto> points;
    public double totalDistance;
    public long totalDuration;

    public AppendSessionPointsRequestDto(
            List<AppendPointItemDto> points,
            double totalDistance,
            long totalDuration
    ) {
        this.points = points;
        this.totalDistance = totalDistance;
        this.totalDuration = totalDuration;
    }
}
```

## `data/model/CompleteSessionRequestDto.java`

```java
package com.walkmate.data.model;

public class CompleteSessionRequestDto {
    public double distance;
    public long duration;

    public CompleteSessionRequestDto(double distance, long duration) {
        this.distance = distance;
        this.duration = duration;
    }
}
```

## `data/model/SessionTrackingResponseDto.java`

```java
package com.walkmate.data.model;

public class SessionTrackingResponseDto {
    public String sessionId;
    public int appendedCount;
    public String message;
}
```

---

# 7. Retrofit API

## `data/remote/SessionApi.java`

```java
package com.walkmate.data.remote;

import com.walkmate.data.model.ApiResponseDto;
import com.walkmate.data.model.AppendSessionPointsRequestDto;
import com.walkmate.data.model.CompleteSessionRequestDto;
import com.walkmate.data.model.SessionResponseDto;
import com.walkmate.data.model.SessionTrackingResponseDto;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SessionApi {

    @POST("api/v1/sessions/{id}/activate")
    Call<ApiResponseDto<SessionResponseDto>> activate(@Path("id") String sessionId);

    @POST("api/v1/sessions/{id}/points:append")
    Call<ApiResponseDto<SessionTrackingResponseDto>> appendPoints(
            @Path("id") String sessionId,
            @Body AppendSessionPointsRequestDto request
    );

    @POST("api/v1/sessions/{id}/complete")
    Call<ApiResponseDto<SessionResponseDto>> complete(
            @Path("id") String sessionId,
            @Body CompleteSessionRequestDto request
    );

    @POST("api/v1/sessions/{id}/abort")
    Call<ApiResponseDto<SessionResponseDto>> abort(
            @Path("id") String sessionId,
            @Body Map<String, String> request
    );

    @POST("api/v1/sessions/{id}/cancel")
    Call<ApiResponseDto<SessionResponseDto>> cancel(
            @Path("id") String sessionId,
            @Body Map<String, String> request
    );

    @GET("api/v1/sessions/{id}")
    Call<ApiResponseDto<SessionResponseDto>> getById(@Path("id") String sessionId);
}
```

## `data/remote/ApiClient.java`

```java
package com.walkmate.data.remote;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static Retrofit retrofit;

    public static SessionApi sessionApi(String baseUrl) {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(SessionApi.class);
    }
}
```

---

# 8. Repository implementation

## `data/repository/SessionRepositoryImpl.java`

```java
package com.walkmate.data.repository;

import com.walkmate.core.common.Result;
import com.walkmate.core.common.ResultCallback;
import com.walkmate.data.model.ApiResponseDto;
import com.walkmate.data.model.AppendPointItemDto;
import com.walkmate.data.model.AppendSessionPointsRequestDto;
import com.walkmate.data.model.CompleteSessionRequestDto;
import com.walkmate.data.model.SessionResponseDto;
import com.walkmate.data.model.SessionTrackingResponseDto;
import com.walkmate.data.remote.SessionApi;
import com.walkmate.domain.model.RoutePoint;
import com.walkmate.domain.model.SessionModel;
import com.walkmate.domain.repository.SessionRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SessionRepositoryImpl implements SessionRepository {

    private final SessionApi api;

    public SessionRepositoryImpl(SessionApi api) {
        this.api = api;
    }

    @Override
    public void activateSession(String sessionId, ResultCallback<SessionModel> callback) {
        api.activate(sessionId).enqueue(new Callback<ApiResponseDto<SessionResponseDto>>() {
            @Override
            public void onResponse(Call<ApiResponseDto<SessionResponseDto>> call,
                                   Response<ApiResponseDto<SessionResponseDto>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                    SessionResponseDto dto = response.body().data;
                    callback.onResult(Result.success(new SessionModel(dto.id, dto.status)));
                } else {
                    callback.onResult(Result.error(new Exception("Activate session failed")));
                }
            }

            @Override
            public void onFailure(Call<ApiResponseDto<SessionResponseDto>> call, Throwable t) {
                callback.onResult(Result.error(t));
            }
        });
    }

    @Override
    public void appendPoints(String sessionId,
                             List<RoutePoint> points,
                             double totalDistanceMeters,
                             long totalDurationSeconds,
                             ResultCallback<Integer> callback) {

        List<AppendPointItemDto> dtoPoints = new ArrayList<>();
        for (RoutePoint point : points) {
            dtoPoints.add(new AppendPointItemDto(
                    point.pointOrder,
                    point.lat,
                    point.lng,
                    point.time
            ));
        }

        AppendSessionPointsRequestDto request = new AppendSessionPointsRequestDto(
                dtoPoints,
                totalDistanceMeters,
                totalDurationSeconds
        );

        api.appendPoints(sessionId, request).enqueue(new Callback<ApiResponseDto<SessionTrackingResponseDto>>() {
            @Override
            public void onResponse(Call<ApiResponseDto<SessionTrackingResponseDto>> call,
                                   Response<ApiResponseDto<SessionTrackingResponseDto>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                    callback.onResult(Result.success(response.body().data.appendedCount));
                } else {
                    callback.onResult(Result.error(new Exception("Append points failed")));
                }
            }

            @Override
            public void onFailure(Call<ApiResponseDto<SessionTrackingResponseDto>> call, Throwable t) {
                callback.onResult(Result.error(t));
            }
        });
    }

    @Override
    public void completeSession(String sessionId,
                                double totalDistanceMeters,
                                long totalDurationSeconds,
                                ResultCallback<SessionModel> callback) {
        CompleteSessionRequestDto request = new CompleteSessionRequestDto(
                totalDistanceMeters,
                totalDurationSeconds
        );

        api.complete(sessionId, request).enqueue(new Callback<ApiResponseDto<SessionResponseDto>>() {
            @Override
            public void onResponse(Call<ApiResponseDto<SessionResponseDto>> call,
                                   Response<ApiResponseDto<SessionResponseDto>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                    SessionResponseDto dto = response.body().data;
                    callback.onResult(Result.success(new SessionModel(dto.id, dto.status)));
                } else {
                    callback.onResult(Result.error(new Exception("Complete session failed")));
                }
            }

            @Override
            public void onFailure(Call<ApiResponseDto<SessionResponseDto>> call, Throwable t) {
                callback.onResult(Result.error(t));
            }
        });
    }

    @Override
    public void abortSession(String sessionId, String reason, ResultCallback<SessionModel> callback) {
        Map<String, String> request = new HashMap<>();
        request.put("reason", reason);

        api.abort(sessionId, request).enqueue(new Callback<ApiResponseDto<SessionResponseDto>>() {
            @Override
            public void onResponse(Call<ApiResponseDto<SessionResponseDto>> call,
                                   Response<ApiResponseDto<SessionResponseDto>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                    SessionResponseDto dto = response.body().data;
                    callback.onResult(Result.success(new SessionModel(dto.id, dto.status)));
                } else {
                    callback.onResult(Result.error(new Exception("Abort session failed")));
                }
            }

            @Override
            public void onFailure(Call<ApiResponseDto<SessionResponseDto>> call, Throwable t) {
                callback.onResult(Result.error(t));
            }
        });
    }

    @Override
    public void cancelSession(String sessionId, String reason, ResultCallback<SessionModel> callback) {
        Map<String, String> request = new HashMap<>();
        request.put("reason", reason);

        api.cancel(sessionId, request).enqueue(new Callback<ApiResponseDto<SessionResponseDto>>() {
            @Override
            public void onResponse(Call<ApiResponseDto<SessionResponseDto>> call,
                                   Response<ApiResponseDto<SessionResponseDto>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                    SessionResponseDto dto = response.body().data;
                    callback.onResult(Result.success(new SessionModel(dto.id, dto.status)));
                } else {
                    callback.onResult(Result.error(new Exception("Cancel session failed")));
                }
            }

            @Override
            public void onFailure(Call<ApiResponseDto<SessionResponseDto>> call, Throwable t) {
                callback.onResult(Result.error(t));
            }
        });
    }
}
```

---

# 9. Tracking contracts

## `core/tracking/TrackingCommand.java`

```java
package com.walkmate.core.tracking;

public enum TrackingCommand {
    NONE,
    START,
    PAUSE,
    RESUME,
    STOP
}
```

## `core/tracking/TrackingEvent.java`

```java
package com.walkmate.core.tracking;

import com.walkmate.domain.model.RoutePoint;

public class TrackingEvent {
    public final RoutePoint point;

    public TrackingEvent(RoutePoint point) {
        this.point = point;
    }
}
```

## `core/tracking/TrackingServiceContract.java`

```java
package com.walkmate.core.tracking;

public final class TrackingServiceContract {
    private TrackingServiceContract() {}

    public static final String ACTION_START = "com.walkmate.tracking.START";
    public static final String ACTION_PAUSE = "com.walkmate.tracking.PAUSE";
    public static final String ACTION_RESUME = "com.walkmate.tracking.RESUME";
    public static final String ACTION_STOP = "com.walkmate.tracking.STOP";
    public static final String ACTION_POINT = "com.walkmate.tracking.POINT";

    public static final String EXTRA_SESSION_ID = "extra_session_id";
    public static final String EXTRA_POINT_ORDER = "extra_point_order";
    public static final String EXTRA_LAT = "extra_lat";
    public static final String EXTRA_LNG = "extra_lng";
    public static final String EXTRA_TIME = "extra_time";
}
```

## `core/tracking/TrackingMath.java`

```java
package com.walkmate.core.tracking;

import com.walkmate.domain.model.RoutePoint;

public final class TrackingMath {
    private static final double EARTH_RADIUS_M = 6371000.0;

    private TrackingMath() {}

    public static double distanceMeters(RoutePoint a, RoutePoint b) {
        double lat1 = Math.toRadians(a.lat);
        double lng1 = Math.toRadians(a.lng);
        double lat2 = Math.toRadians(b.lat);
        double lng2 = Math.toRadians(b.lng);

        double dLat = lat2 - lat1;
        double dLng = lng2 - lng1;

        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
        return EARTH_RADIUS_M * c;
    }
}
```

---

# 10. Foreground GPS Service

## `core/tracking/LocationTrackingService.java`

```java
package com.walkmate.core.tracking;

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

import com.google.android.gms.location.*;
import com.walkmate.domain.model.RoutePoint;

public class LocationTrackingService extends Service {

    private static final String CHANNEL_ID = "walk_tracking_channel";
    private static final int NOTIFICATION_ID = 1101;
    private static final float MIN_ACCEPT_ACCURACY_METERS = 25f;
    private static final float MIN_DISTANCE_METERS = 3f;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private boolean isPaused = false;
    private int currentOrder = 0;
    private RoutePoint lastAcceptedPoint;
    private String currentSessionId;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null || isPaused) return;

                android.location.Location location = locationResult.getLastLocation();
                if (location == null) return;

                if (location.getAccuracy() > MIN_ACCEPT_ACCURACY_METERS) return;

                RoutePoint candidate = new RoutePoint(
                        ++currentOrder,
                        location.getLatitude(),
                        location.getLongitude(),
                        System.currentTimeMillis()
                );

                if (lastAcceptedPoint != null) {
                    double delta = TrackingMath.distanceMeters(lastAcceptedPoint, candidate);
                    if (delta < MIN_DISTANCE_METERS) {
                        return;
                    }
                }

                lastAcceptedPoint = candidate;
                emitPoint(candidate);
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
            currentSessionId = intent.getStringExtra(TrackingServiceContract.EXTRA_SESSION_ID);
            isPaused = false;
            currentOrder = 0;
            lastAcceptedPoint = null;
            startForeground(NOTIFICATION_ID, buildNotification("Walking in progress"));
            startLocationUpdates();
        } else if (TrackingServiceContract.ACTION_PAUSE.equals(action)) {
            isPaused = true;
        } else if (TrackingServiceContract.ACTION_RESUME.equals(action)) {
            isPaused = false;
        } else if (TrackingServiceContract.ACTION_STOP.equals(action)) {
            stopLocationUpdates();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }

        return START_STICKY;
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 3000L
        ).setMinUpdateIntervalMillis(2000L)
         .setWaitForAccurateLocation(false)
         .build();

        fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
        );
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void emitPoint(RoutePoint point) {
        Intent intent = new Intent(TrackingServiceContract.ACTION_POINT);
        intent.setPackage(getPackageName());
        intent.putExtra(TrackingServiceContract.EXTRA_POINT_ORDER, point.pointOrder);
        intent.putExtra(TrackingServiceContract.EXTRA_LAT, point.lat);
        intent.putExtra(TrackingServiceContract.EXTRA_LNG, point.lng);
        intent.putExtra(TrackingServiceContract.EXTRA_TIME, point.time);
        sendBroadcast(intent);
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Walk tracking")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Walk Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
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
}
```

---

# 11. UiState

## `ui/session/SessionUiState.java`

```java
package com.walkmate.ui.session;

import com.walkmate.core.tracking.TrackingCommand;
import com.walkmate.domain.model.RoutePoint;

import java.util.ArrayList;
import java.util.List;

public class SessionUiState {
    public final boolean isLoading;
    public final boolean isTracking;
    public final boolean isPaused;
    public final boolean isCompleted;
    public final String errorMessage;

    public final List<RoutePoint> route;
    public final double distanceMeters;
    public final long durationSeconds;

    public final TrackingCommand pendingCommand;
    public final long commandVersion;

    public SessionUiState(
            boolean isLoading,
            boolean isTracking,
            boolean isPaused,
            boolean isCompleted,
            String errorMessage,
            List<RoutePoint> route,
            double distanceMeters,
            long durationSeconds,
            TrackingCommand pendingCommand,
            long commandVersion
    ) {
        this.isLoading = isLoading;
        this.isTracking = isTracking;
        this.isPaused = isPaused;
        this.isCompleted = isCompleted;
        this.errorMessage = errorMessage;
        this.route = route != null ? route : new ArrayList<>();
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
        this.pendingCommand = pendingCommand;
        this.commandVersion = commandVersion;
    }

    public static SessionUiState idle() {
        return new SessionUiState(
                false, false, false, false,
                null, new ArrayList<>(), 0.0, 0L,
                TrackingCommand.NONE, 0L
        );
    }
}
```

---

# 12. ViewModel

## `ui/session/SessionViewModel.java`

```java
package com.walkmate.ui.session;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.common.Result;
import com.walkmate.core.common.ResultCallback;
import com.walkmate.core.tracking.TrackingCommand;
import com.walkmate.core.tracking.TrackingMath;
import com.walkmate.domain.model.RoutePoint;
import com.walkmate.domain.model.SessionModel;
import com.walkmate.domain.repository.SessionRepository;

import java.util.ArrayList;
import java.util.List;

public class SessionViewModel extends ViewModel {

    private static final int APPEND_BATCH_SIZE = 20;

    private final SessionRepository repository;
    private final MutableLiveData<SessionUiState> state = new MutableLiveData<>(SessionUiState.idle());

    private final List<RoutePoint> route = new ArrayList<>();
    private final List<RoutePoint> appendBuffer = new ArrayList<>();

    private String sessionId;
    private long commandVersion = 0L;
    private double distanceMeters = 0.0;
    private long durationSeconds = 0L;
    private RoutePoint lastPoint;

    public SessionViewModel(SessionRepository repository) {
        this.repository = repository;
    }

    public LiveData<SessionUiState> getState() {
        return state;
    }

    public void bindSession(String sessionId) {
        this.sessionId = sessionId;
    }

    public void activateSession() {
        SessionUiState current = safeState();
        state.setValue(new SessionUiState(
                true, current.isTracking, current.isPaused, false,
                null, new ArrayList<>(route),
                distanceMeters, durationSeconds,
                TrackingCommand.NONE, commandVersion
        ));

        repository.activateSession(sessionId, new ResultCallback<SessionModel>() {
            @Override
            public void onResult(Result<SessionModel> result) {
                if (result.isSuccess()) {
                    route.clear();
                    appendBuffer.clear();
                    lastPoint = null;
                    distanceMeters = 0.0;
                    durationSeconds = 0L;

                    commandVersion++;
                    state.postValue(new SessionUiState(
                            false, true, false, false,
                            null, new ArrayList<>(route),
                            distanceMeters, durationSeconds,
                            TrackingCommand.START, commandVersion
                    ));
                } else {
                    state.postValue(new SessionUiState(
                            false, false, false, false,
                            messageOf(result.getError()),
                            new ArrayList<>(route),
                            distanceMeters, durationSeconds,
                            TrackingCommand.NONE, commandVersion
                    ));
                }
            }
        });
    }

    public void pauseWalk() {
        SessionUiState current = safeState();
        commandVersion++;
        state.setValue(new SessionUiState(
                false, current.isTracking, true, false,
                null, new ArrayList<>(route),
                distanceMeters, durationSeconds,
                TrackingCommand.PAUSE, commandVersion
        ));
    }

    public void resumeWalk() {
        SessionUiState current = safeState();
        commandVersion++;
        state.setValue(new SessionUiState(
                false, current.isTracking, false, false,
                null, new ArrayList<>(route),
                distanceMeters, durationSeconds,
                TrackingCommand.RESUME, commandVersion
        ));
    }

    public void onTrackingPoint(RoutePoint point) {
        SessionUiState current = safeState();
        if (!current.isTracking || current.isPaused) return;

        if (lastPoint != null) {
            distanceMeters += TrackingMath.distanceMeters(lastPoint, point);
            durationSeconds = Math.max(0L, (point.time - route.get(0).time) / 1000L);
        }

        lastPoint = point;
        route.add(point);
        appendBuffer.add(point);

        state.postValue(new SessionUiState(
                false, true, false, false,
                null, new ArrayList<>(route),
                distanceMeters, durationSeconds,
                TrackingCommand.NONE, commandVersion
        ));

        if (appendBuffer.size() >= APPEND_BATCH_SIZE) {
            flushAppendBuffer(false);
        }
    }

    public void completeSession() {
        SessionUiState current = safeState();
        state.setValue(new SessionUiState(
                true, current.isTracking, current.isPaused, false,
                null, new ArrayList<>(route),
                distanceMeters, durationSeconds,
                TrackingCommand.NONE, commandVersion
        ));

        flushAppendBufferThenComplete();
    }

    public void abortSession(String reason) {
        state.setValue(new SessionUiState(
                true, false, false, false,
                null, new ArrayList<>(route),
                distanceMeters, durationSeconds,
                TrackingCommand.NONE, commandVersion
        ));

        repository.abortSession(sessionId, reason, result -> {
            if (result.isSuccess()) {
                commandVersion++;
                state.postValue(new SessionUiState(
                        false, false, false, false,
                        null, new ArrayList<>(route),
                        distanceMeters, durationSeconds,
                        TrackingCommand.STOP, commandVersion
                ));
            } else {
                state.postValue(new SessionUiState(
                        false, true, false, false,
                        messageOf(result.getError()),
                        new ArrayList<>(route),
                        distanceMeters, durationSeconds,
                        TrackingCommand.NONE, commandVersion
                ));
            }
        });
    }

    public void cancelSession(String reason) {
        state.setValue(new SessionUiState(
                true, false, false, false,
                null, new ArrayList<>(route),
                distanceMeters, durationSeconds,
                TrackingCommand.NONE, commandVersion
        ));

        repository.cancelSession(sessionId, reason, result -> {
            if (result.isSuccess()) {
                commandVersion++;
                state.postValue(new SessionUiState(
                        false, false, false, false,
                        null, new ArrayList<>(route),
                        distanceMeters, durationSeconds,
                        TrackingCommand.STOP, commandVersion
                ));
            } else {
                state.postValue(new SessionUiState(
                        false, true, false, false,
                        messageOf(result.getError()),
                        new ArrayList<>(route),
                        distanceMeters, durationSeconds,
                        TrackingCommand.NONE, commandVersion
                ));
            }
        });
    }

    private void flushAppendBuffer(boolean silent) {
        if (appendBuffer.isEmpty()) return;

        List<RoutePoint> batch = new ArrayList<>(appendBuffer);
        appendBuffer.clear();

        repository.appendPoints(sessionId, batch, distanceMeters, durationSeconds, result -> {
            if (!result.isSuccess() && !silent) {
                SessionUiState current = safeState();
                state.postValue(new SessionUiState(
                        false, current.isTracking, current.isPaused, false,
                        messageOf(result.getError()),
                        new ArrayList<>(route),
                        distanceMeters, durationSeconds,
                        TrackingCommand.NONE, commandVersion
                ));
            }
        });
    }

    private void flushAppendBufferThenComplete() {
        if (appendBuffer.isEmpty()) {
            callCompleteNow();
            return;
        }

        List<RoutePoint> batch = new ArrayList<>(appendBuffer);
        appendBuffer.clear();

        repository.appendPoints(sessionId, batch, distanceMeters, durationSeconds, appendResult -> {
            if (appendResult.isSuccess()) {
                callCompleteNow();
            } else {
                SessionUiState current = safeState();
                state.postValue(new SessionUiState(
                        false, current.isTracking, current.isPaused, false,
                        messageOf(appendResult.getError()),
                        new ArrayList<>(route),
                        distanceMeters, durationSeconds,
                        TrackingCommand.NONE, commandVersion
                ));
            }
        });
    }

    private void callCompleteNow() {
        repository.completeSession(sessionId, distanceMeters, durationSeconds, result -> {
            if (result.isSuccess()) {
                commandVersion++;
                state.postValue(new SessionUiState(
                        false, false, false, true,
                        null, new ArrayList<>(route),
                        distanceMeters, durationSeconds,
                        TrackingCommand.STOP, commandVersion
                ));
            } else {
                SessionUiState current = safeState();
                state.postValue(new SessionUiState(
                        false, current.isTracking, current.isPaused, false,
                        messageOf(result.getError()),
                        new ArrayList<>(route),
                        distanceMeters, durationSeconds,
                        TrackingCommand.NONE, commandVersion
                ));
            }
        });
    }

    private SessionUiState safeState() {
        SessionUiState current = state.getValue();
        return current != null ? current : SessionUiState.idle();
    }

    private String messageOf(Throwable t) {
        return t != null && t.getMessage() != null ? t.getMessage() : "Unknown error";
    }
}
```

---

# 13. Activity

## `ui/session/SessionActivity.java`

```java
package com.walkmate.ui.session;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.walkmate.R;
import com.walkmate.core.tracking.LocationTrackingService;
import com.walkmate.core.tracking.TrackingCommand;
import com.walkmate.core.tracking.TrackingServiceContract;
import com.walkmate.data.remote.ApiClient;
import com.walkmate.data.remote.SessionApi;
import com.walkmate.data.repository.SessionRepositoryImpl;
import com.walkmate.domain.model.RoutePoint;
import com.walkmate.domain.repository.SessionRepository;

import java.util.ArrayList;
import java.util.List;

public class SessionActivity extends ComponentActivity implements OnMapReadyCallback {

    private static final int REQ_LOCATION = 1001;

    private SessionViewModel viewModel;

    private GoogleMap googleMap;
    private Polyline polyline;

    private Button btnStart;
    private Button btnPause;
    private Button btnResume;
    private Button btnEnd;
    private TextView tvDistance;
    private TextView tvDuration;

    private long handledCommandVersion = -1L;
    private String sessionId;

    private final BroadcastReceiver trackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!TrackingServiceContract.ACTION_POINT.equals(intent.getAction())) return;

            int order = intent.getIntExtra(TrackingServiceContract.EXTRA_POINT_ORDER, 0);
            double lat = intent.getDoubleExtra(TrackingServiceContract.EXTRA_LAT, 0.0);
            double lng = intent.getDoubleExtra(TrackingServiceContract.EXTRA_LNG, 0.0);
            long time = intent.getLongExtra(TrackingServiceContract.EXTRA_TIME, 0L);

            RoutePoint point = new RoutePoint(order, lat, lng, time);
            viewModel.onTrackingPoint(point);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);

        sessionId = getIntent().getStringExtra("session_id");

        btnStart = findViewById(R.id.btnStart);
        btnPause = findViewById(R.id.btnPause);
        btnResume = findViewById(R.id.btnResume);
        btnEnd = findViewById(R.id.btnEnd);
        tvDistance = findViewById(R.id.tvDistance);
        tvDuration = findViewById(R.id.tvDuration);

        SessionApi api = ApiClient.sessionApi("https://your-base-url/");
        SessionRepository repository = new SessionRepositoryImpl(api);

        viewModel = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return (T) new SessionViewModel(repository);
            }
        }).get(SessionViewModel.class);

        viewModel.bindSession(sessionId);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        btnStart.setOnClickListener(v -> {
            if (ensureLocationPermission()) {
                viewModel.activateSession();
            }
        });

        btnPause.setOnClickListener(v -> viewModel.pauseWalk());
        btnResume.setOnClickListener(v -> viewModel.resumeWalk());
        btnEnd.setOnClickListener(v -> viewModel.completeSession());

        viewModel.getState().observe(this, this::render);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(trackingReceiver, new IntentFilter(TrackingServiceContract.ACTION_POINT));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(trackingReceiver);
    }

    private void render(SessionUiState state) {
        btnStart.setVisibility(!state.isTracking ? View.VISIBLE : View.GONE);
        btnPause.setVisibility(state.isTracking && !state.isPaused ? View.VISIBLE : View.GONE);
        btnResume.setVisibility(state.isTracking && state.isPaused ? View.VISIBLE : View.GONE);
        btnEnd.setVisibility(state.isTracking ? View.VISIBLE : View.GONE);

        tvDistance.setText(String.format("%.1f m", state.distanceMeters));
        tvDuration.setText(state.durationSeconds + " s");

        if (state.errorMessage != null) {
            Toast.makeText(this, state.errorMessage, Toast.LENGTH_SHORT).show();
        }

        renderRoute(state.route);
        executeTrackingCommand(state);
    }

    private void renderRoute(List<RoutePoint> route) {
        if (googleMap == null) return;

        List<LatLng> latLngs = new ArrayList<>();
        for (RoutePoint point : route) {
            latLngs.add(new LatLng(point.lat, point.lng));
        }

        if (polyline == null) {
            polyline = googleMap.addPolyline(new PolylineOptions().width(10f).addAll(latLngs));
        } else {
            polyline.setPoints(latLngs);
        }

        if (!latLngs.isEmpty()) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    latLngs.get(latLngs.size() - 1), 17f
            ));
        }
    }

    private void executeTrackingCommand(SessionUiState state) {
        if (state.commandVersion == handledCommandVersion) return;
        handledCommandVersion = state.commandVersion;

        Intent intent = new Intent(this, LocationTrackingService.class);

        if (state.pendingCommand == TrackingCommand.START) {
            intent.setAction(TrackingServiceContract.ACTION_START);
            intent.putExtra(TrackingServiceContract.EXTRA_SESSION_ID, sessionId);
            ContextCompat.startForegroundService(this, intent);
        } else if (state.pendingCommand == TrackingCommand.PAUSE) {
            intent.setAction(TrackingServiceContract.ACTION_PAUSE);
            startService(intent);
        } else if (state.pendingCommand == TrackingCommand.RESUME) {
            intent.setAction(TrackingServiceContract.ACTION_RESUME);
            startService(intent);
        } else if (state.pendingCommand == TrackingCommand.STOP) {
            intent.setAction(TrackingServiceContract.ACTION_STOP);
            startService(intent);
        }
    }

    private boolean ensureLocationPermission() {
        boolean fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            boolean fgGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            if (!fineGranted || !fgGranted) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.FOREGROUND_SERVICE_LOCATION
                        },
                        REQ_LOCATION
                );
                return false;
            }
            return true;
        }

        if (!fineGranted) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_LOCATION
            );
            return false;
        }

        return true;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                viewModel.activateSession();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
```

---

# 14. Layout XML

## `res/layout/activity_session.xml`

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
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:orientation="vertical"
        android:padding="16dp"
        android:background="#AAFFFFFF">

        <TextView
            android:id="@+id/tvDistance"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="0.0 m"
            android:textSize="18sp" />

        <TextView
            android:id="@+id/tvDuration"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="0 s"
            android:textSize="18sp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal">

            <Button
                android:id="@+id/btnStart"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Start" />

            <Button
                android:id="@+id/btnPause"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Pause" />

            <Button
                android:id="@+id/btnResume"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Resume" />

            <Button
                android:id="@+id/btnEnd"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="End" />
        </LinearLayout>
    </LinearLayout>

</FrameLayout>
```

---

# 15. Flow hoạt động

## Khi bấm Start

1. `Activity` gọi `viewModel.activateSession()`
2. `ViewModel` call `POST /sessions/{id}/activate`
3. Success → `UiState.pendingCommand = START`
4. `Activity` thấy command mới → start `LocationTrackingService`
5. Service nhận GPS và broadcast từng point
6. `Activity` nhận broadcast → delegate `viewModel.onTrackingPoint(point)`
7. `ViewModel` update `UiState`
8. `Activity` observe state → update stats + polyline

## Khi bấm Pause

1. `Activity` gọi `viewModel.pauseWalk()`
2. `UiState.pendingCommand = PAUSE`
3. `Activity` gửi action pause cho service
4. Service ngừng accept point mới

## Khi bấm Resume

1. `Activity` gọi `viewModel.resumeWalk()`
2. `UiState.pendingCommand = RESUME`
3. `Activity` gửi action resume cho service

## Khi bấm End

1. `Activity` gọi `viewModel.completeSession()`
2. `ViewModel` flush batch còn lại bằng `POST /sessions/{id}/points:append`
3. Sau đó call `POST /sessions/{id}/complete`
4. Success → `UiState.pendingCommand = STOP`
5. `Activity` stop service

---

# 16. Các vấn đề GPS đã xử lý trong code

Code trên đã xử lý các điểm quan trọng sau:

* **Không lấy GPS trong Activity**
* **Map không bị recreate khi bấm button**
* **Location chỉ nhận khi accuracy đủ tốt**
* **Bỏ point quá gần** để giảm noise
* **Batch append** mỗi 20 point để giảm API call
* **Complete** luôn flush batch cuối trước khi complete session
* **Pause/Resume** không làm reset map layer
* `Activity` chỉ render state và thực thi side effect hệ thống

---

# 17. Những chỗ bạn nên nâng cấp tiếp khi đưa production

Skeleton trên chạy đúng flow, nhưng để production hơn thì bạn nên bổ sung:

* lưu point vào `Room` để không mất route khi process bị kill
* auth interceptor cho Retrofit
* parse error body chuẩn hơn trong repository
* smooth camera thay vì `moveCamera()` liên tục
* debounce toast error
* foreground notification hiển thị distance realtime
* xử lý `ACCESS_BACKGROUND_LOCATION` nếu cần tracking khi app ở background lâu

---

# 18. API nào frontend gọi

Phần frontend với flow này chỉ dùng:

* `POST /api/v1/sessions/{id}/activate`
* `POST /api/v1/sessions/{id}/points:append`
* `POST /api/v1/sessions/{id}/complete`

Tùy màn:

* `POST /api/v1/sessions/{id}/abort`
* `POST /api/v1/sessions/{id}/cancel`

---

# 19. Điểm quan trọng nhất trong design này

Map layer không reload vì:

* `SupportMapFragment` chỉ tạo một lần trong `onCreate`
* `GoogleMap` giữ nguyên instance
* mỗi lần state đổi chỉ `setPoints()` cho polyline
* button state và map state tách nhau

---

Nếu bạn muốn, tôi có thể viết tiếp bản **v2 production-ready** với `Room` local cache cho points, `ViewModelFactory` chuẩn hơn, và tách `MapController` ra khỏi `Activity` để code sạch hơn.
