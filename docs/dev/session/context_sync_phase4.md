# Context Sync — Phases 1–4 Complete
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
| **Tech stack** | Java (no Kotlin), MVVM + DDD-lite, LiveData, ExecutorService, Room 2.7.0, Retrofit 3 + OkHttp 5, Google Maps + FusedLocationProviderClient 21.3.0, Manual DI via `WalkMateApplication`, minSdk 24 / targetSdk 36 |

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
- **`WalkMateApplication`** — Application-level Service Locator for `WalkMateDatabase` and `TrackingRepository`.
- **`AndroidManifest.xml`** — `android:name=".WalkMateApplication"` added; `TrackingScreenActivity` pre-registered.
- **`libs.versions.toml`** + **`build.gradle.kts`** — Room 2.7.0 added with `annotationProcessor`.

---

## Phase 2 — Completed

### What was implemented
- **`LocationFilterPolicy`** — pure Java stateful filter. Rejects fixes with accuracy > 25 m and movement < 3 m. Haversine-based, zero Android imports. `reset()` on pause ensures the first resume fix is always accepted.
- **`SessionTrackingService`** — domain-layer pipeline. Single-thread executor. `stopTracking()` calls `executor.shutdown()` for graceful drain.
- **`WalkTrackerService`** — Android Foreground Service. `FusedLocationProviderClient` with `PRIORITY_HIGH_ACCURACY`, 3 s / 1.5 s / 1 m params. `START_NOT_STICKY`. Stops location updates and drains executor in `onDestroy()`.
- **`TrackingScreenActivity`** — skeleton Activity. Reads all four Intent extras; finishes immediately if `SESSION_ID` is null. `onDestroy()` guards with `isFinishing()`.
- **`activity_tracking_screen.xml`** — placeholder layout (replaced in Phase 5).
- **`AndroidManifest.xml`** — `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`; `WalkTrackerService` with `foregroundServiceType="location"`.

### Intent extra key constants

| Constant on `WalkTrackerService` | Re-exported on `TrackingScreenActivity` | String value | Type |
|---|---|---|---|
| `EXTRA_SESSION_ID` | `EXTRA_SESSION_ID` | `"SESSION_ID"` | `String` |
| `EXTRA_PARTNER_NAME` | `EXTRA_PARTNER_NAME` | `"PARTNER_NAME"` | `String` |
| `EXTRA_MEETING_LAT` | `EXTRA_MEETING_LAT` | `"MEETING_POINT_LAT"` | `double` |
| `EXTRA_MEETING_LNG` | `EXTRA_MEETING_LNG` | `"MEETING_POINT_LNG"` | `double` |

---

## Phase 3 — Completed

### What was implemented
- **`item_session_card.xml`** — Added `btnStartWalk` (`MaterialButton`, full-width, `bg_gradient_orange_pill` background, `android:visibility="gone"` by default, placed below the Chat/Cancel row).
- **`SessionAdapter.java`** — Added `OnStartWalkClickListener` interface + `startWalkListener` field + setter. `ViewHolder` shows `btnStartWalk` only when `session.getStatus() == WalkSession.Status.PENDING_MEET`.
- **`SessionFragment.java`** — Wired `OnStartWalkClickListener`; builds an `Intent` to `TrackingScreenActivity` using `TrackingScreenActivity.EXTRA_*` constants (no magic strings) with all four required extras; calls `startActivity(intent)`.
- **`strings.xml`** — Added `<string name="btn_start_walk">Start Walk</string>`.

---

## Phase 4 — Completed

### What was implemented

#### `TrackingViewModel` (`ui/tracking/TrackingViewModel.java`) — NEW

Extends `AndroidViewModel` (needs `Application` to start/stop the foreground service via `ContextCompat.startForegroundService()`).

**Observable state exposed to the Activity:**
- `LiveData<TrackingUiState> getUiState()` — single `MediatorLiveData` snapshot; always non-null after construction.

**Internal state:**
- `MutableLiveData<WalkState> walkStateLiveData` — defaults to `READY`.
- `MutableLiveData<Long> elapsedSecondsLiveData` — defaults to `0L`.
- `MediatorLiveData<TrackingUiState> uiStateLiveData` — three sources: `walkStateLiveData`, `elapsedSecondsLiveData`, `routePointsLiveData` (added lazily in `startTrackingSession`).

**Timer:**
- `ScheduledExecutorService timerExecutor` — single-thread; lives for the ViewModel's lifetime.
- `ScheduledFuture<?> timerFuture` — 1-second fixed-rate task; replaced on each `startTimer()` call.
- Time variables: `walkStartEpochMs`, `pausedAccumulatedMs`, `pauseStartEpochMs`.
- Tick formula: `activeMs = (now - walkStartEpochMs) - pausedAccumulatedMs`.
- Posts via `postValue` (background thread safe).

**Walk lifecycle commands:**

| Method | Guard | Side effects |
|---|---|---|
| `startTrackingSession(id, name, lat, lng)` | idempotent | stores metadata; lazily adds route LiveData source |
| `startWalk()` | only if `READY` | resets time vars; starts GPS service + timer; → `ACTIVE` |
| `pauseWalk()` | only if `ACTIVE` | records `pauseStartEpochMs`; stops timer + GPS service; → `PAUSED` |
| `resumeWalk()` | only if `PAUSED` | folds pause duration into `pausedAccumulatedMs`; starts GPS service + timer; → `ACTIVE` |
| `finishWalk()` | any state | stops timer + GPS service; → `FINISHED` |

**Pace calculation:**
```
if (distanceKm * 1000 < 50.0 || elapsedSeconds <= 0) → 0.0
else → (elapsedSeconds / 60.0) / distanceKm
```

**Distance calculation:**
Incremental Haversine sum over the ordered `List<RoutePoint>`. Returns kilometres.

**`isCameraFollowingUser`:**
`true` only while `WalkState == ACTIVE` — camera locks during pause/finish.

**`onCleared()`:**
Calls `stopTimer()` then `timerExecutor.shutdown()` to release the background thread.

---

#### `TrackingViewModelFactory` (`ui/tracking/TrackingViewModelFactory.java`) — NEW

Standard `ViewModelProvider.Factory` that passes `Application` to `TrackingViewModel`'s constructor. Throws `IllegalArgumentException` for unknown ViewModel classes.

---

#### `TrackingScreenActivity` (`ui/tracking/TrackingScreenActivity.java`) — MODIFIED

Changes in Phase 4:
1. Added `TrackingViewModel viewModel` field.
2. `onCreate()` — after `readAndLogIntentExtras()`, creates `TrackingViewModelFactory`, obtains the ViewModel, calls `viewModel.startTrackingSession(...)`, observes `uiState` via `renderState()`.
3. `finishWalk()` — now delegates to `viewModel.finishWalk()` before calling `finish()`. The `onDestroy()` `stopService()` safety net is retained for back-press exits where the Finish button was not tapped.
4. Added `renderState(TrackingUiState state)` stub — logs all stats fields; Phase 5 binds them to UI widgets.

---

## Key Technical Decisions (all phases)

### `AndroidViewModel` vs `ViewModel`
`TrackingViewModel` extends `AndroidViewModel` because it needs `Application` context to issue `startForegroundService` / `stopService` calls. This keeps the Activity a passive view with no service management code.

### GPS service on pause/resume
`pauseWalk()` stops the foreground service entirely (GPS off); `resumeWalk()` restarts it with the same session extras (GPS on). Route points are persisted in Room so the polyline is never lost across a pause.

### `ScheduledFuture.cancel(false)`
The timer is cancelled with `interrupt=false` so any in-flight `postValue` completes cleanly. A new `Future` is created on each `startTimer()` call after a `stopTimer()`.

### `timerExecutor.shutdown()` in `onCleared()`
`shutdown()` (not `shutdownNow()`) lets the last in-flight tick complete before releasing the thread. Consistent with the pattern used in `SessionTrackingService` and `TrackingRepositoryImpl`.

### Haversine duplication
The ViewModel's `haversineMeters()` is intentionally a copy of the one in `LocationFilterPolicy`. The domain layer must not import UI classes; the UI layer should not depend on domain internals. Both are pure math — no abstraction needed.

### `ContextCompat.startForegroundService()`
Used instead of `context.startForegroundService()` directly because `minSdk = 24` (< 26). `ContextCompat` transparently falls back to `startService()` on pre-26 devices.

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
| `ui/tracking/TrackingScreenActivity.java` | UI (skeleton → Phase 4 wired) |
| `res/layout/activity_tracking_screen.xml` | UI (placeholder → Phase 5) |

### Phase 2 — MODIFIED files
| File | What changed |
|---|---|
| `AndroidManifest.xml` | 3 permissions + `WalkTrackerService` with `foregroundServiceType="location"` |

### Phase 3 — MODIFIED files
| File | What changed |
|---|---|
| `res/layout/item_session_card.xml` | Added `btnStartWalk` (orange pill, `gone` by default) |
| `ui/matches/session/SessionAdapter.java` | `OnStartWalkClickListener` + `PENDING_MEET` visibility logic |
| `ui/matches/session/SessionFragment.java` | Wires listener → launches `TrackingScreenActivity` with 4 extras |
| `res/values/strings.xml` | Added `btn_start_walk` |

### Phase 4 — NEW files
| File | Layer |
|---|---|
| `ui/tracking/TrackingViewModel.java` | UI — ViewModel |
| `ui/tracking/TrackingViewModelFactory.java` | UI — ViewModel |

### Phase 4 — MODIFIED files
| File | What changed |
|---|---|
| `ui/tracking/TrackingScreenActivity.java` | ViewModel wired; `renderState()` stub added; `finishWalk()` delegates to ViewModel |

All paths are relative to `frontend/src/main/java/com/walkmate/` (Java) or `frontend/src/main/` (resources/manifest).

---

## Next Steps

### Phase 5 — UI Layer: Stats sheet + Lifecycle controls + Design system

We are ready to implement the final UI phase. The full data pipeline is complete:

```
GPS hardware
  → WalkTrackerService (Foreground Service)
    → SessionTrackingService (domain filter + executor)
      → TrackingRepositoryImpl (Room insert)
        → RoutePoint LiveData
          → TrackingViewModel (MediatorLiveData: timer + pace + state)
            → TrackingScreenActivity.renderState(TrackingUiState)
              → [Phase 5: bind to Views]
```

**Target files (MODIFIED):**
- `res/layout/activity_tracking_screen.xml` — Replace placeholder with full layout:
  - `SupportMapFragment` (full-screen, behind the stats sheet)
  - `BottomSheetBehavior` stats sheet containing:
    - Partner name header
    - Distance / elapsed time / pace stat tiles
    - Start / Pause / Resume / Finish `MaterialButton` row
- `ui/tracking/TrackingScreenActivity.java` — Expand `renderState()` to bind all `TrackingUiState` fields; wire button clicks to ViewModel commands (`startWalk`, `pauseWalk`, `resumeWalk`, `finishWalk`); control button visibility based on `WalkState`.

**Key design constraints for Phase 5:**
- The Start button is only visible in `READY` state.
- The Pause button is only visible in `ACTIVE` state.
- The Resume + Finish buttons are only visible in `PAUSED` state.
- Camera follows the user only while `isCameraFollowingUser == true` (i.e., `ACTIVE`).
- Elapsed time format: `MM:SS` (pad with leading zero).
- Pace format: `X'YY"/km"` — show `--'--"` until the 50 m threshold is crossed.
- Distance format: `0.000 km` (3 decimal places).
- All UI colours and gradients must use the existing design system (`bg_gradient_orange_pill`, `color_danger`, `text_dark`, `text_muted`, etc.).
