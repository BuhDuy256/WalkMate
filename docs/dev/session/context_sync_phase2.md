# Context Sync — Phase 1 + Phase 2 Complete
> **Use this file to start a new chat session without losing context.**
> Copy the entire contents into your next prompt as background.

---

## Project State

| Field | Value |
|---|---|
| **Repository** | WalkMate Android (Java) |
| **Active branch** | `improve/coordination-flow` |
| **Overall goal** | Build a Strava-like GPS Walk Tracking UI for WalkMate |
| **Implementation plan** | `docs/dev/session/implementation_proposal.md` |
| **Architecture reference** | `docs/single-source-of-truth/architecture/Frontend_VI.md` |
| **Tech stack** | Java (no Kotlin), MVVM + DDD-lite, LiveData, ExecutorService, Room 2.7.0, Retrofit 3 + OkHttp 5, Google Maps + FusedLocationProviderClient 21.3.0, Manual DI via `WalkMateApplication` |

---

## Phase 1 — Completed

### What was implemented
- **`WalkState` enum** — four-state walk lifecycle (`READY`, `ACTIVE`, `PAUSED`, `FINISHED`) in `domain/tracking/`.
- **`RoutePoint` domain model** — pure Java POJO; no Room or Android imports.
- **`TrackingRepository` interface** — write ops use `DomainCallback<T>`; reactive read returns `LiveData<List<RoutePoint>>`.
- **`TrackingErrorCode`** — string constants for tracking-specific errors.
- **`TrackingUiState`** — immutable UI snapshot: `WalkState`, `List<LatLng>`, `distanceKm`, `elapsedSeconds`, `paceMinPerKm`, `partnerName`, `isCameraFollowingUser`.
- **Room DB** — `RoutePointEntity` (composite index on `sessionId+timestamp`), `RoutePointDao`, `WalkMateDatabase` (singleton, version 1, `fallbackToDestructiveMigration`).
- **Remote DTOs** — `PushRoutePointsRequest` + `PushRoutePointsResponse` + `RoutePointSyncApiService` (Retrofit interface for `POST /api/v1/tracking/sync`).
- **`RoutePointMapper`** — pure static: Entity ↔ Domain, Entity → remote payload.
- **`TrackingRepositoryImpl`** — mock backend push (logs payload + 800 ms sleep + `onSuccess`); auto batch-sync at 50 unsynced points.
- **`WalkMateApplication`** — first Application class in the project; Service Locator for `WalkMateDatabase` and `TrackingRepository`.
- **`AndroidManifest.xml`** — `android:name=".WalkMateApplication"` added; `TrackingScreenActivity` pre-registered.
- **`libs.versions.toml`** + **`build.gradle.kts`** — Room 2.7.0 added with `annotationProcessor`.

---

## Phase 2 — Completed

### What was implemented

- **`LocationFilterPolicy`** — pure Java stateful filter in `domain/tracking/`. Rejects fixes with accuracy > 25 m and movement < 3 m from last accepted point. Uses Haversine (no Android imports). Call `reset()` on pause so the first resume fix is always accepted.

- **`SessionTrackingService`** — domain-layer processing pipeline in `domain/tracking/` (no Android imports). Owns a `newSingleThreadExecutor` to hand off location processing from the main thread. Calls `LocationFilterPolicy.shouldAccept()` then `TrackingRepository.saveRoutePoint()`. **Phase 2 fix:** `stopTracking()` calls `executor.shutdown()` (graceful — in-flight Room writes complete before thread dies). Also exposes `pauseSession()` which calls `filterPolicy.reset()`.

- **`WalkTrackerService`** — Android Foreground Service in `com.walkmate.service`. Uses `FusedLocationProviderClient` with `LocationRequest.Builder` (modern API 21.x style: `Priority.PRIORITY_HIGH_ACCURACY`, 3 s interval, 1.5 s fastest, 1 m min distance). Three Phase 2 fixes baked in:
  1. **No fallback session ID** — reads `EXTRA_SESSION_ID` from Intent; calls `stopSelf()` if absent.
  2. **Location updates stopped in `onDestroy()`** — `fusedLocationClient.removeLocationUpdates(callback)` called before releasing the reference.
  3. **Executor drained in `onDestroy()`** — calls `sessionTrackingService.stopTracking()`.
  - Notification: `NotificationChannel` created in `onCreate()` (API 26+ guard); `startForeground()` uses 3-arg form with `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION` for API 29+ (required for `targetSdk = 36`).
  - Returns `START_NOT_STICKY` — if killed by OS, it should not auto-restart.

- **`TrackingScreenActivity`** — skeleton `AppCompatActivity` in `ui/tracking/`. Reads all four Intent extras (`SESSION_ID`, `PARTNER_NAME`, `MEETING_POINT_LAT`, `MEETING_POINT_LNG`); logs them. **Phase 2 fix 1:** finishes immediately if `SESSION_ID` is null. **Phase 2 fix 2:** `finishWalk()` calls `stopService(WalkTrackerService)` before `finish()`. **Phase 2 fix 3:** `onDestroy()` guards with `isFinishing()` before stopping service (prevents killing GPS during screen rotation).

- **`activity_tracking_screen.xml`** — minimal placeholder layout (`FrameLayout` + label); replaced entirely in Phase 5.

- **`AndroidManifest.xml`** — Added:
  - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS` permissions.
  - `<service android:name="...WalkTrackerService" android:foregroundServiceType="location" android:exported="false"/>`.

---

## Key Technical Decisions

### Service architecture — two-layer separation
`WalkTrackerService` (Android layer) only handles GPS hardware and the foreground notification. All business logic — filtering, saving, sync triggering — lives in `SessionTrackingService` (domain layer, zero Android imports). This separation means the domain logic is unit-testable without an Android emulator.

### Executor ownership
Each layer that does background work owns its own `ExecutorService`:
- `SessionTrackingService` — single-thread executor for filter + repo dispatch (receives from main-thread location callback).
- `TrackingRepositoryImpl` — single-thread executor for Room I/O.
Both are shut down gracefully via `shutdown()` (not `shutdownNow()`) so pending tasks always complete.

### `START_NOT_STICKY` rationale
If the OS kills `WalkTrackerService`, the walk is already interrupted. Auto-restarting with a null Intent would trigger the "missing SESSION_ID" guard and call `stopSelf()` again anyway. The user must explicitly resume from the tracking screen.

### `isFinishing()` guard in `onDestroy()`
`onDestroy()` is called for both screen rotation (Activity recreated) and true exit. Only stopping the GPS service on `isFinishing() == true` prevents the walk from being silently cancelled when the user rotates their phone.

---

## Complete File Manifest

### Phase 1 — NEW files
| File | Layer |
|---|---|
| `domain/tracking/WalkState.java` | Domain |
| `domain/tracking/RoutePoint.java` | Domain |
| `domain/tracking/TrackingRepository.java` | Domain |
| `domain/tracking/TrackingErrorCode.java` | Domain |
| `ui/tracking/TrackingUiState.java` | UI |
| `data/datasource/local/entity/RoutePointEntity.java` | Data — Local |
| `data/datasource/local/dao/RoutePointDao.java` | Data — Local |
| `data/datasource/local/WalkMateDatabase.java` | Data — Local |
| `data/datasource/remote/dto/request/tracking/PushRoutePointsRequest.java` | Data — Remote |
| `data/datasource/remote/dto/response/tracking/PushRoutePointsResponse.java` | Data — Remote |
| `data/datasource/remote/api/RoutePointSyncApiService.java` | Data — Remote |
| `data/mapper/RoutePointMapper.java` | Data — Mapper |
| `data/repository/TrackingRepositoryImpl.java` | Data — Repository |
| `WalkMateApplication.java` | App |

### Phase 1 — MODIFIED files
| File | What changed |
|---|---|
| `AndroidManifest.xml` | `android:name=".WalkMateApplication"`; `TrackingScreenActivity` pre-registered |
| `gradle/libs.versions.toml` | Room 2.7.0 added |
| `frontend/build.gradle.kts` | Room runtime + annotationProcessor added |

### Phase 2 — NEW files
| File | Layer |
|---|---|
| `domain/tracking/LocationFilterPolicy.java` | Domain |
| `domain/tracking/SessionTrackingService.java` | Domain |
| `service/WalkTrackerService.java` | Android Service |
| `ui/tracking/TrackingScreenActivity.java` | UI (skeleton) |
| `res/layout/activity_tracking_screen.xml` | UI (placeholder) |

### Phase 2 — MODIFIED files
| File | What changed |
|---|---|
| `AndroidManifest.xml` | Added 3 permissions + `WalkTrackerService` service declaration with `foregroundServiceType="location"` |

All paths are relative to `frontend/src/main/java/com/walkmate/` (Java) or `frontend/src/main/` (resources/manifest).

---

## Next Steps

### Phase 3 — Entry-Point Wiring (`ui/matches/session/`)

**Target files:**
- `SessionAdapter.java` ← add `OnStartWalkClickListener` interface; show "Start Walk" button when `session.getStatus() == PENDING_MEET`
- `SessionFragment.java` ← wire the listener; build Intent with 4 extras and call `startActivity(TrackingScreenActivity)`
- `item_session_card.xml` ← add the Start Walk `MaterialButton` (orange gradient pill) to the card layout

**Key constraint:** The Start Walk button must only be visible for sessions with `WalkSession.Status.PENDING_MEET`. Cards with other statuses should not show it.

### Phase 4 — ViewModel: Timer, Pace, Lifecycle Commands (`ui/tracking/`)

**Target files:**
- `TrackingViewModel.java` ← NEW; `MediatorLiveData` merging route LiveData + timer + WalkState
- `TrackingViewModelFactory.java` ← NEW; reads `TrackingRepository` from `WalkMateApplication`

**Key work:**
1. Expose `startWalk()`, `pauseWalk()`, `resumeWalk()`, `finishWalk()` commands that transition `WalkState` and start/stop `WalkTrackerService`.
2. Add a `ScheduledExecutorService`-based stopwatch that posts `elapsedSeconds` every second (paused time excluded).
3. Compute `paceMinPerKm` from distance and elapsed time once `distanceKm > 0.05`.
4. Wire `TrackingScreenActivity.onCreate()` to `TrackingViewModel` and observe `uiState`.
5. Move `startForegroundService()` call from `onCreate` to the `startWalk()` command so GPS only starts when the user explicitly taps Start.
