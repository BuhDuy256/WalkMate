# GPS Path Tracing — Architectural Snapshot

> **Branch:** `feature/fe/gps-path-tracing`
> **Date captured:** 2026-03-30
> **Scope:** Strictly factual description of the current implementation. No recommendations or future plans are included.

---

## 1. Overview

The GPS Path Tracing feature operates as a pipeline of four decoupled components:

```
Android OS GPS → WalkTrackerService → Room DB → TrackingViewModel → TrackingScreenActivity (Map)
```

1. **`WalkTrackerService`** (Foreground Service) receives raw GPS fixes from the Android Fused Location Provider and forwards them to the domain layer.
2. **`SessionTrackingService`** (Domain) applies a two-stage noise filter (`LocationFilterPolicy`) and, for every accepted point, persists it to Room via `SessionRepository`. It also monitors the count of unsynced points to detect when a batch threshold is crossed.
3. **`TrackingViewModel`** observes the Room table via `LiveData`. Each emission from the database triggers a recalculation of the screen state (`TrackingUiState`), including the list of `LatLng` points and the cumulative distance.
4. **`TrackingScreenActivity`** renders the polyline on a Google Map and updates the distance `TextView` whenever `TrackingUiState` changes.

The session is identified by a plain `String` session ID that is currently hardcoded to `"test-session-123"` in the Activity and passed to the Service via `Intent` extra `SESSION_ID`.

---

## 2. Libraries & Dependencies

| Library | Role |
|---|---|
| **Google Maps SDK for Android** (`com.google.android.gms.maps`) | Map display, polyline rendering, camera control |
| **Google Play Services — Location** (`com.google.android.gms.location`) | `FusedLocationProviderClient`, `LocationRequest`, `LocationCallback`, `LocationResult` |
| **Maps SDK Utility Library** (`com.google.maps.android.SphericalUtil`) | Geodesic distance computation (`computeDistanceBetween`, `computeLength`) used in both the spatial filter and the distance-accumulation algorithm |
| **AndroidX Room** (`androidx.room`) | Local SQLite persistence via `@Entity`, `@Dao`, `@Database` abstractions |
| **AndroidX Lifecycle — LiveData / ViewModel** (`androidx.lifecycle`) | Reactive UI updates; `Transformations.switchMap` and `Transformations.map` bridge Room's `LiveData` to the UI |
| **AndroidX ConstraintLayout** | Root layout of `activity_tracking_screen.xml` |
| **Material Components** (`com.google.android.material`) | `FloatingActionButton` (re-center camera button) |
| **AndroidX CardView** | Container for the distance `TextView` overlay |

---

## 3. Path Rendering Algorithm

### 3.1 Data Source

`TrackingViewModel` uses `Transformations.switchMap` keyed on `sessionTrigger` (a `MutableLiveData<String>` holding the session ID). When the session ID is set, it subscribes to `SessionRepository.getPointsOfCurrentSession(sessionId)`, which delegates to:

```sql
SELECT * FROM route_points WHERE sessionId = :sessionId ORDER BY timestamp ASC
```

Room delivers this as a `LiveData<List<RoutePointEntity>>` that fires every time a new row is inserted into the table.

### 3.2 Domain → LatLng Mapping

Inside the `Transformations.map` lambda in `TrackingViewModel`, each `RoutePoint` domain object is mapped to a `com.google.android.gms.maps.model.LatLng`:

```
RoutePoint(lat, lng) → LatLng(lat, lng)
```

The resulting `List<LatLng>` is packed into a `TrackingUiState` object alongside the computed total distance.

### 3.3 Polyline Rendering

`TrackingScreenActivity.drawPolyline(List<LatLng>)` manages a single `Polyline` instance (`currentPolyline`):

- **First render:** A new `Polyline` is created via `mMap.addPolyline(PolylineOptions)`.
- **Subsequent updates:** `currentPolyline.setPoints(points)` replaces the point list in-place, avoiding repeated object allocation on the map.

Polyline styling:

| Property | Value |
|---|---|
| Color | `#4285F4` (Google Blue) |
| Width | `16f` pixels |
| Geodesic | `true` (great-circle arcs between points) |

### 3.4 Distance Accumulation (O(1) incremental algorithm)

`TrackingViewModel` maintains three instance variables to avoid recomputing the total path length over the full point list on every DB emission:

| Variable | Purpose |
|---|---|
| `cachedTotalDistance` | Running sum of accepted segments in meters |
| `lastProcessedListSize` | Size of the list when last processed |
| `lastProcessedPoint` | Last domain point included in the sum |

**Cold-start path** (first emission with > 1 point): `SphericalUtil.computeLength(mapPoints)` is called once on the full list.

**Incremental path** (subsequent emissions): For each new index from `lastProcessedListSize` to `domainPoints.size() - 1`, `SphericalUtil.computeDistanceBetween(lastProcessedPoint, newPoint)` is accumulated into `cachedTotalDistance`. Both `lastProcessedListSize` and `lastProcessedPoint` are updated at the end of each emission.

The final distance is formatted for display as `"%.2f km"` (meters ÷ 1000).

### 3.5 Camera Behavior

- On the first location rendered (`isFirstLocationRendered == false`), the camera is always animated to the latest point at zoom `17.5f`.
- While the camera-follow flag (`isCameraFollowingUser`) is `true`, the camera animates to the latest point on every subsequent DB emission.
- If the user initiates a map gesture (`REASON_GESTURE`), `viewModel.setCameraFollow(false)` disables automatic following.
- The FAB button calls `viewModel.setCameraFollow(true)` to re-enable following.
- The native My-Location button is hidden; the app uses its own FAB instead. `mMap.setMyLocationEnabled(true)` is still called (when permission is granted) to show the blue dot.

---

## 4. Data Persistence (Room DB)

### 4.1 Database

**Class:** `WalkMateDatabase extends RoomDatabase`
**File:** `walkmate_database` (SQLite)
**Version:** `2`
**Migration strategy:** `fallbackToDestructiveMigration()` — schema changes drop and recreate the database.
**Instantiation:** Double-checked locking singleton via `getInstance(Context)`.

### 4.2 Entity: `route_points`

| Column | Type | Notes |
|---|---|---|
| `id` | `LONG` | Auto-generated primary key |
| `sessionId` | `STRING` | Groups points by walking session |
| `lat` | `DOUBLE` | WGS-84 latitude |
| `lng` | `DOUBLE` | WGS-84 longitude |
| `timestamp` | `LONG` | Unix epoch milliseconds from `Location.getTime()` |
| `accuracy` | `FLOAT` | Horizontal accuracy radius in meters from `Location.getAccuracy()` |
| `isSynced` | `BOOLEAN` | Defaults to `false`; set to `true` after successful server sync |

**Index:** Composite index on `(sessionId, timestamp)`.

### 4.3 DAO: `RoutePointDao`

| Method | SQL / Room annotation | Caller |
|---|---|---|
| `insertPoint(entity)` | `@Insert` | `SessionRepositoryImpl.saveRoutePoint` |
| `getPointsBySessionId(sessionId)` | `SELECT * … WHERE sessionId = ? ORDER BY timestamp ASC` → `LiveData` | `SessionRepositoryImpl.getPointsOfCurrentSession` |
| `getUnsyncedPoints()` | `SELECT * … WHERE isSynced = 0 ORDER BY timestamp ASC` | `SessionRepositoryImpl.getUnsyncedPoints` |
| `getUnsyncedCount()` | `SELECT COUNT(id) … WHERE isSynced = 0` | `SessionRepositoryImpl.getUnsyncedCount` |
| `markAsSynced(List<Long>)` | `UPDATE … SET isSynced = 1 WHERE id IN (…)` | `SessionRepositoryImpl.markPointsAsSynced` |

### 4.4 Write Path (Service → DB)

```
WalkTrackerService.onLocationResult()
  └─ SessionTrackingService.processNewLocation()   [background thread via ExecutorService]
       └─ LocationFilterPolicy.isValid()            [filter check]
            └─ SessionRepositoryImpl.saveRoutePoint()
                 └─ RoutePointDao.insertPoint()      [Room, synchronous on executor thread]
```

All DB writes occur on a dedicated background thread managed by `SessionTrackingService` (see Section 5).

### 4.5 Read Path (DB → UI)

```
RoutePointDao.getPointsBySessionId()  [Room emits on Main Thread via LiveData]
  └─ SessionRepositoryImpl (Transformations.map → List<RoutePoint>)
       └─ TrackingViewModel (Transformations.map → TrackingUiState)
            └─ TrackingScreenActivity.observe() → drawPolyline() + tvDistance.setText()
```

---

## 5. Background Processing & Network

### 5.1 Threading Model

`SessionTrackingService` holds a single `ExecutorService` created via `Executors.newSingleThreadExecutor()`. Every call to `processNewLocation(RoutePoint)` submits a `Runnable` to this executor, serializing all filter checks, DB writes, and batch-count queries onto one background thread.

`WalkTrackerService.locationCallback.onLocationResult()` fires on the **main looper** (as passed to `requestLocationUpdates`). The heavy work is immediately handed off to the executor in `SessionTrackingService`, so the main thread is not blocked.

### 5.2 Foreground Service

`WalkTrackerService` runs as an Android **Foreground Service**. On API 29+, it is started with `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION`. The persistent notification is configured as:

| Property | Value |
|---|---|
| Channel ID | `WalkTrackingChannel` |
| Channel Importance | `IMPORTANCE_LOW` |
| Notification Priority | `PRIORITY_HIGH` |
| Content Title | `"WalkMate đang theo dõi..."` |
| Icon | `android.R.drawable.ic_menu_mylocation` |

The service returns `START_STICKY`, so Android restarts it automatically if it is killed.

### 5.3 GPS Request Parameters

`LocationRequest` is built with:

| Parameter | Value |
|---|---|
| Priority | `PRIORITY_HIGH_ACCURACY` |
| Interval | `2000 ms` |
| Min update interval | `1000 ms` |

### 5.4 Network / Batch Sync

The batch sync logic is **a stub**. After every accepted point is saved, `SessionTrackingService` calls `sessionRepository.getUnsyncedCount()`. If `count >= 50` (`BATCH_SIZE_THRESHOLD`), `triggerBatchSync()` is invoked. The body of `triggerBatchSync()` currently contains only a `Log.e` statement; no actual network call is implemented.

The infrastructure is in place:
- `SessionRepository.getUnsyncedPoints()` retrieves all rows with `isSynced = 0`.
- `SessionRepository.markPointsAsSynced(List<Long>)` bulk-updates the `isSynced` flag.

No Retrofit client, WorkManager job, or other network component exists in the analyzed files.

---

## 6. State & Lifecycle Management

### 6.1 ViewModel Lifecycle

`TrackingViewModel` extends `AndroidViewModel` and is scoped to `TrackingScreenActivity` via `new ViewModelProvider(this).get(TrackingViewModel.class)`. It survives configuration changes (screen rotation). The `LiveData` chain (`sessionTrigger → uiStateLiveData`) is wired once in the constructor and remains active for the ViewModel's lifetime.

The camera-follow flag (`isCameraFollowingUser`) is a `MutableLiveData<Boolean>` initialized to `true`. It is reset only through explicit user actions or the FAB; it is not reset across ViewModel recreation.

Distance-accumulation cache variables (`cachedTotalDistance`, `lastProcessedListSize`, `lastProcessedPoint`) are stored directly on the ViewModel and are explicitly reset to zero/null in `startTrackingSession()`.

### 6.2 Map Lifecycle

`TrackingScreenActivity` implements `OnMapReadyCallback`. The `GoogleMap` reference (`mMap`) is assigned in `onMapReady()`, which fires asynchronously after `mapFragment.getMapAsync(this)`. All map interactions — `setMyLocationEnabled`, `setOnCameraMoveStartedListener`, and `viewModel.getUiState().observe()` — are set up inside `onMapReady()` to guarantee that `mMap` is non-null.

The `isFirstLocationRendered` boolean flag (initialized to `false`) is an Activity-level field that ensures the camera performs an initial fly-to on the very first point received, independent of the follow flag.

### 6.3 Service Lifecycle

`WalkTrackerService` is started in `TrackingScreenActivity.onCreate()` via `startForegroundService()` (API 26+) or `startService()`. There is no corresponding `stopService()` call in `TrackingScreenActivity.onDestroy()` or any other lifecycle method in the analyzed files. The `ExecutorService` inside `SessionTrackingService` is not explicitly shut down.

`WalkTrackerService.onDestroy()` removes location updates via `fusedLocationClient.removeLocationUpdates(locationCallback)`, which stops GPS consumption when the OS destroys the service.

### 6.4 Session ID Flow

```
TrackingScreenActivity.onCreate()
  ├─ viewModel.startTrackingSession("test-session-123")   → sets sessionTrigger LiveData
  └─ startForegroundService(intent + SESSION_ID="test-session-123")
       └─ WalkTrackerService.onStartCommand()
            └─ sessionId = intent.getStringExtra("SESSION_ID")
```

If the `Intent` does not carry `SESSION_ID`, `WalkTrackerService` self-generates a fallback ID as `"test-session-" + System.currentTimeMillis()`. This produces a mismatch with the ViewModel's session ID, causing the UI to observe an empty point list.
