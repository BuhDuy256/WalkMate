# GPS Path Tracing — Feature Completion Summary

> **Branch:** `improve/coordination-flow`
> **Status:** ✅ All 5 phases implemented
> **Date completed:** 2026-03-30

---

## 1. Feature Overview

WalkMate's GPS Path Tracing feature lets two matched walkers start a live GPS-tracked walk session directly from the session card in the Matches tab. It records a real-time polyline on a Google Map, displays an active stopwatch, incremental distance (km), and live walking pace (min/km) on a stats bottom sheet — a Strava-like experience in a social walking context.

---

## 2. End-to-End Data Flow

```
SessionFragment
  └─ OnStartWalkClickListener
       └─ Intent → TrackingScreenActivity
                    │  (reads 4 extras from Intent)
                    │
                    ├─ TrackingViewModel.startTrackingSession()
                    │       └─ TrackingRepository.getPointsForSession()  ←┐
                    │                                                       │ Room LiveData
                    │  [user taps Start]                                    │
                    │                                                       │
                    ├─ TrackingViewModel.startWalk()                       │
                    │   ├─ ContextCompat.startForegroundService()          │
                    │   │       └─ WalkTrackerService (foreground service) │
                    │   │           ├─ FusedLocationProviderClient         │
                    │   │           │   (GPS fixes every 3 s / 1 m)        │
                    │   │           └─ SessionTrackingService              │
                    │   │               ├─ LocationFilterPolicy            │
                    │   │               │   (accuracy > 25 m → reject)     │
                    │   │               │   (distance < 3 m → reject)      │
                    │   │               └─ TrackingRepository.save() ──────┘
                    │   └─ timerExecutor (1 s tick → elapsedSecondsLiveData)
                    │
                    └─ MediatorLiveData<TrackingUiState>
                         (merges: routePoints + elapsed + walkState)
                              └─ TrackingScreenActivity.renderState()
                                   ├─ updatePolyline (setPoints, no re-create)
                                   ├─ animateCamera  (follows user in ACTIVE)
                                   ├─ updateStats    (distance / duration / pace)
                                   └─ updateControls (Start | Pause+Stop | hidden)
```

---

## 3. Architecture Layers

### 3.1 Domain Layer (`com.walkmate.domain.tracking`)

| Class | Role |
|---|---|
| `WalkState` | Enum — `READY → ACTIVE ⇄ PAUSED → FINISHED` state machine |
| `RoutePoint` | Immutable POJO — single GPS fix (lat, lng, timestamp, accuracy, synced flag) |
| `TrackingRepository` (interface) | Contract for read (LiveData) and write (DomainCallback) GPS data |
| `TrackingErrorCode` | String constants for domain-level errors |
| `LocationFilterPolicy` | Stateful Haversine filter — rejects noisy/stationary GPS fixes |
| `SessionTrackingService` | Coordinates filtering + repo dispatch on a single-thread executor |

**Key invariant:** Zero Android imports. All domain classes are pure Java — unit-testable without an emulator.

### 3.2 Data Layer (`com.walkmate.data`)

| Class | Role |
|---|---|
| `RoutePointEntity` | Room entity — composite index on `(sessionId, timestamp)` |
| `RoutePointDao` | Room DAO — `insert`, `getPointsForSession` (LiveData), `getUnsyncedCount`, `markSynced` |
| `WalkMateDatabase` | Room singleton (version 1, `fallbackToDestructiveMigration`) |
| `RoutePointMapper` | Pure static mappers: Entity ↔ Domain, Entity → remote DTO |
| `TrackingRepositoryImpl` | Implements `TrackingRepository`; Room I/O on its own single-thread executor; mock remote sync at 50-point batches |
| `RoutePointSyncApiService` | Retrofit interface — `POST /api/v1/tracking/sync` |
| `PushRoutePointsRequest/Response` | Remote DTOs |

### 3.3 Android Service Layer (`com.walkmate.service`)

| Class | Role |
|---|---|
| `WalkTrackerService` | Foreground Service — owns `FusedLocationProviderClient`; creates the persistent tracking notification; feeds fixes to `SessionTrackingService`; `START_NOT_STICKY` |

**Notification:** `NotificationChannel` created with API 26 guard; `startForeground()` uses 3-arg form with `FOREGROUND_SERVICE_TYPE_LOCATION` (required for `targetSdk = 36`).

### 3.4 UI Layer (`com.walkmate.ui.tracking`)

| Class | Role |
|---|---|
| `TrackingUiState` | Immutable snapshot — `WalkState`, `List<LatLng>`, `distanceKm`, `elapsedSeconds`, `paceMinPerKm`, `partnerName`, `isCameraFollowingUser` |
| `TrackingViewModel` | Owns the stopwatch (`ScheduledExecutorService`), GPS service commands, and `MediatorLiveData<TrackingUiState>` |
| `TrackingViewModelFactory` | Passes `Application` to `AndroidViewModel` constructor |
| `TrackingScreenActivity` | Pure passive view — observes `uiState`; delegates all logic to ViewModel |

### 3.5 Entry-Point Wiring (`com.walkmate.ui.matches.session`)

| Change | What it does |
|---|---|
| `SessionAdapter.OnStartWalkClickListener` | Interface triggered when the user taps "Start Walk" on a card |
| `SessionAdapter.bind()` | Shows "Start Walk" button only for `WalkSession.Status.PENDING_MEET` |
| `SessionFragment` | Wires the listener; builds the Intent with 4 typed extras; calls `startActivity` |

---

## 4. Key Technical Decisions

### Two-layer service separation
`WalkTrackerService` handles GPS hardware and the foreground notification. `SessionTrackingService` handles all business logic (filter, persist, sync trigger). This keeps domain logic unit-testable with no Android emulator.

### Executor ownership model
Each background-capable component owns one `ExecutorService` and shuts it down gracefully with `shutdown()` (not `shutdownNow()`), so pending Room writes always complete.

| Component | Executor | Work |
|---|---|---|
| `SessionTrackingService` | `newSingleThreadExecutor` | Location filter + repo dispatch |
| `TrackingRepositoryImpl` | `newSingleThreadExecutor` | Room I/O |
| `TrackingViewModel` | `newSingleThreadScheduledExecutor` | 1-second timer ticks |

### `AndroidViewModel` for GPS service control
`TrackingViewModel` extends `AndroidViewModel` so it can call `ContextCompat.startForegroundService()` and `stopService()` without holding an `Activity` reference. The Activity is a passive observer.

### `MediatorLiveData` for the UI snapshot
Three sources are merged into one `TrackingUiState` snapshot:
1. **Route points** (`LiveData<List<RoutePoint>>` from Room) — drives polyline and distance.
2. **Elapsed seconds** (`MutableLiveData<Long>` from the timer) — drives the stopwatch.
3. **Walk state** (`MutableLiveData<WalkState>`) — drives button visibility and camera follow.

The Activity observes only a single `LiveData<TrackingUiState>` — no manual merging in the view layer.

### Polyline reuse
`Polyline.setPoints()` is called on each state update rather than removing and re-adding the polyline. This avoids creating a new GPU-mapped object every second and eliminates the visual flicker of a redraw.

### Camera follow logic
- **First GPS point:** `moveCamera()` (instant, no animation) — avoids disorienting the user with an animated fly-in on start.
- **ACTIVE state:** `animateCamera()` — smooth follow.
- **PAUSED / FINISHED:** No camera move — user can freely pan the map to review the route.

### `isCameraFollowingUser` flag
Computed in the ViewModel as `walkState == ACTIVE`. This keeps the camera policy in one place and makes it easily testable.

### `isFinishing()` guard in `onDestroy()`
Prevents `WalkTrackerService` from being killed on screen rotation (configuration change). Only stops the service when the Activity is truly closing.

### `ContextCompat.startForegroundService()`
Used instead of the raw `context.startForegroundService()` because `minSdk = 24` (below API 26). `ContextCompat` transparently falls back to `startService()` on pre-26 devices.

### `START_NOT_STICKY`
If the OS kills `WalkTrackerService`, the walk is already interrupted. Auto-restarting with a null Intent would hit the "missing SESSION_ID" guard and call `stopSelf()` immediately anyway.

### Haversine duplication
`LocationFilterPolicy` and `TrackingViewModel` each carry their own `haversineMeters()` method. This is intentional — the domain layer must not import UI classes; the UI layer should not depend on domain internals. Both are pure math with identical behaviour.

---

## 5. Permissions & Manifest

| Permission | When required |
|---|---|
| `ACCESS_FINE_LOCATION` | Runtime check before `startWalk()`; requested from `TrackingScreenActivity` |
| `ACCESS_COARSE_LOCATION` | Declared for fallback (coarse is a subset of fine) |
| `FOREGROUND_SERVICE` | Required to call `startForeground()` from a Service |
| `FOREGROUND_SERVICE_LOCATION` | Required for `foregroundServiceType="location"` on targetSdk ≥ 34 |
| `POST_NOTIFICATIONS` | Required to show the persistent tracking notification on API 33+ |

---

## 6. Complete File Manifest

### Domain
| File | Phase |
|---|---|
| `domain/tracking/WalkState.java` | 1 |
| `domain/tracking/RoutePoint.java` | 1 |
| `domain/tracking/TrackingRepository.java` (interface) | 1 |
| `domain/tracking/TrackingErrorCode.java` | 1 |
| `domain/tracking/LocationFilterPolicy.java` | 2 |
| `domain/tracking/SessionTrackingService.java` | 2 |

### Data
| File | Phase |
|---|---|
| `data/datasource/local/entity/RoutePointEntity.java` | 1 |
| `data/datasource/local/dao/RoutePointDao.java` | 1 |
| `data/datasource/local/WalkMateDatabase.java` | 1 |
| `data/datasource/remote/dto/request/tracking/PushRoutePointsRequest.java` | 1 |
| `data/datasource/remote/dto/response/tracking/PushRoutePointsResponse.java` | 1 |
| `data/datasource/remote/api/RoutePointSyncApiService.java` | 1 |
| `data/mapper/RoutePointMapper.java` | 1 |
| `data/repository/TrackingRepositoryImpl.java` | 1 |

### App / DI
| File | Phase |
|---|---|
| `WalkMateApplication.java` | 1 |

### Service
| File | Phase |
|---|---|
| `service/WalkTrackerService.java` | 2 |

### UI — Tracking
| File | Phase |
|---|---|
| `ui/tracking/TrackingUiState.java` | 1 |
| `ui/tracking/TrackingViewModel.java` | 4 |
| `ui/tracking/TrackingViewModelFactory.java` | 4 |
| `ui/tracking/TrackingScreenActivity.java` | 2 (skeleton) → 4 (VM wired) → 5 (full UI) |

### UI — Matches entry-point
| File | Phase |
|---|---|
| `ui/matches/session/SessionAdapter.java` | 3 — `OnStartWalkClickListener` + PENDING_MEET visibility |
| `ui/matches/session/SessionFragment.java` | 3 — builds Intent, calls `startActivity` |

### Resources
| File | Phase | Change |
|---|---|---|
| `res/layout/activity_tracking_screen.xml` | 2 (placeholder) → 5 (full layout) | CoordinatorLayout + MapFragment + bottom sheet + controls |
| `res/layout/item_session_card.xml` | 3 | Added `btnStartWalk` (orange pill, `gone` by default) |
| `res/drawable/ic_my_location.xml` | 5 | NEW — GPS crosshair icon for re-center FAB |
| `res/values/strings.xml` | 3, 5 | Tracking screen strings added |

### Already-existing drawables used
| Drawable | Used for |
|---|---|
| `bg_gradient_orange_pill` | Start and Finish buttons |
| `bg_sheet_top_rounded` | Bottom sheet background (48dp top corners) |
| `bg_white_circle` | Back button background |
| `bg_warm_circle` | Partner avatar circle background |
| `ic_back` | Back button icon |

---

## 7. Stat Formatting Reference

| Stat | Method | Example output |
|---|---|---|
| Distance | `formatDistance(double km)` | `"1.23"` (2 dp) |
| Duration | `formatDuration(long seconds)` | `"04:30"` / `"1:02:09"` |
| Pace | `formatPace(double minPerKm)` | `"--'--\""` (< 50 m) / `"6'45\""` |

---

## 8. Known Limitations & Future Work

| Area | Current state | Future improvement |
|---|---|---|
| Backend sync | Mock (800 ms sleep + `onSuccess`) | Wire `RoutePointSyncApiService` to real endpoint |
| Walk summary | AlertDialog with 3 stats | Dedicated summary screen / Room-persisted history |
| Partner location | Not tracked | Push partner's live location via WebSocket |
| Camera follow toggle | Auto (follows in ACTIVE, free in PAUSED) | FAB taps toggle camera follow on/off |
| Elevation profile | Not implemented | Use `RoutePoint.altitude` field and a chart view |
| Map style | Default Google Maps | Custom retro/warm map style matching app palette |
| Offline resilience | Room buffers GPS points | Retry logic in `TrackingRepositoryImpl` when network returns |
