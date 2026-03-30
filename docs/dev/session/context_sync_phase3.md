# Context Sync — Phase 1 + Phase 2 + Phase 3 Complete
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

- **`SessionTrackingService`** — domain-layer processing pipeline in `domain/tracking/` (no Android imports). Owns a `newSingleThreadExecutor` to hand off location processing from the main thread. Calls `LocationFilterPolicy.shouldAccept()` then `TrackingRepository.saveRoutePoint()`. `stopTracking()` calls `executor.shutdown()` (graceful — in-flight Room writes complete before thread dies). Also exposes `pauseSession()` which calls `filterPolicy.reset()`.

- **`WalkTrackerService`** — Android Foreground Service in `com.walkmate.service`. Uses `FusedLocationProviderClient` with `LocationRequest.Builder` (modern API 21.x style: `Priority.PRIORITY_HIGH_ACCURACY`, 3 s interval, 1.5 s fastest, 1 m min distance). Key behaviours:
  1. Reads `EXTRA_SESSION_ID` from Intent; calls `stopSelf()` if absent.
  2. `fusedLocationClient.removeLocationUpdates(callback)` called in `onDestroy()`.
  3. `sessionTrackingService.stopTracking()` called in `onDestroy()` to drain the executor.
  - Returns `START_NOT_STICKY`.

- **`TrackingScreenActivity`** — skeleton `AppCompatActivity` in `ui/tracking/`. Reads all four Intent extras; finishes immediately if `SESSION_ID` is null. `finishWalk()` calls `stopService(WalkTrackerService)`. `onDestroy()` guards with `isFinishing()`.

- **`activity_tracking_screen.xml`** — minimal placeholder layout.

- **`AndroidManifest.xml`** — `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS` permissions; `WalkTrackerService` declared with `foregroundServiceType="location"`.

### Intent extra key constants (defined in `WalkTrackerService`, re-exported by `TrackingScreenActivity`)

| Constant | String value | Type |
|---|---|---|
| `EXTRA_SESSION_ID` | `"SESSION_ID"` | `String` |
| `EXTRA_PARTNER_NAME` | `"PARTNER_NAME"` | `String` |
| `EXTRA_MEETING_LAT` | `"MEETING_POINT_LAT"` | `double` |
| `EXTRA_MEETING_LNG` | `"MEETING_POINT_LNG"` | `double` |

---

## Phase 3 — Completed

### What was implemented

- **`item_session_card.xml`** — Added `btnStartWalk` (`MaterialButton`, full-width, `bg_gradient_orange_pill` background, white bold text, `android:visibility="gone"` by default). Placed below the Chat / Cancel row inside the card's content `LinearLayout`.

- **`SessionAdapter.java`** — Added `OnStartWalkClickListener` interface with single method `onStartWalkClick(WalkSession session)`. Added `startWalkListener` field + `setOnStartWalkClickListener()` setter. `ViewHolder` now holds a `btnStartWalk` reference. In `bind()`: button visibility is `VISIBLE` only when `session.getStatus() == WalkSession.Status.PENDING_MEET`; click fires `startWalkListener.onStartWalkClick(session)`.

- **`SessionFragment.java`** — Wired `OnStartWalkClickListener` in `onViewCreated()`. Listener builds an `Intent` to `TrackingScreenActivity` with the four required extras using `TrackingScreenActivity.EXTRA_*` constants (no magic strings), then calls `startActivity(intent)`. Added `import android.content.Intent` and `import com.walkmate.ui.tracking.TrackingScreenActivity`.

- **`strings.xml`** — Added `<string name="btn_start_walk">Start Walk</string>`.

### Key Technical Decisions

#### Visibility vs. Gone
The Start Walk button uses `android:visibility="gone"` in XML (not `invisible`) so the card does not reserve vertical space for it on non-PENDING_MEET sessions. The adapter toggles `VISIBLE`/`GONE` on every `bind()` call to handle RecyclerView view recycling correctly.

#### Using `TrackingScreenActivity.EXTRA_*` constants
`SessionFragment` references the public constants from `TrackingScreenActivity` rather than duplicating string literals. This makes any future key rename a single-file change and keeps the caller/callee contract explicit.

#### No `startActivityForResult`
Phase 3 uses plain `startActivity`. Phase 4/5 may upgrade to `ActivityResultLauncher` if the tracking screen needs to return a result (e.g. walk summary) to the session card.

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

### Phase 3 — NEW files
*(none)*

### Phase 3 — MODIFIED files
| File | What changed |
|---|---|
| `res/layout/item_session_card.xml` | Added `btnStartWalk` (full-width orange pill, `visibility="gone"`) |
| `ui/matches/session/SessionAdapter.java` | Added `OnStartWalkClickListener`; `btnStartWalk` visibility logic in `bind()` |
| `ui/matches/session/SessionFragment.java` | Wired `OnStartWalkClickListener`; launches `TrackingScreenActivity` with 4 extras |
| `res/values/strings.xml` | Added `btn_start_walk` string |

All paths are relative to `frontend/src/main/java/com/walkmate/` (Java) or `frontend/src/main/` (resources/manifest).

---

## Key Technical Decisions (all phases)

### Service architecture — two-layer separation
`WalkTrackerService` (Android layer) only handles GPS hardware and the foreground notification. All business logic — filtering, saving, sync triggering — lives in `SessionTrackingService` (domain layer, zero Android imports).

### Executor ownership
Each layer that does background work owns its own `ExecutorService`:
- `SessionTrackingService` — single-thread executor for filter + repo dispatch.
- `TrackingRepositoryImpl` — single-thread executor for Room I/O.
Both are shut down gracefully via `shutdown()`.

### `START_NOT_STICKY` rationale
If the OS kills `WalkTrackerService`, the walk is already interrupted. Auto-restarting with a null Intent would trigger the "missing SESSION_ID" guard and `stopSelf()` anyway.

### `isFinishing()` guard in `onDestroy()`
Only stops the GPS service when the Activity is truly closing, not on configuration changes like screen rotation.

---

## Next Steps

### Phase 4 — ViewModel: Timer, Pace, Lifecycle Commands (`ui/tracking/`)

We are ready to implement Phase 4. The entry-point wiring is complete: `SessionFragment` can now launch `TrackingScreenActivity` with a valid session contract. Phase 4 adds the ViewModel layer that drives the tracking screen's live UI.

**Target files (NEW):**
- `ui/tracking/TrackingViewModel.java` — `MediatorLiveData` merging route LiveData + timer + `WalkState`
- `ui/tracking/TrackingViewModelFactory.java` — reads `TrackingRepository` from `WalkMateApplication`

**Key work:**
1. Expose `startWalk()`, `pauseWalk()`, `resumeWalk()`, `finishWalk()` commands that transition `WalkState` and start/stop `WalkTrackerService` via the Activity's `Context`.
2. Add a `ScheduledExecutorService`-based stopwatch that posts `elapsedSeconds` every second (paused time excluded).
3. Compute `paceMinPerKm` from `distanceKm` and `elapsedSeconds` once `distanceKm > 0.05`.
4. Wire `TrackingScreenActivity.onCreate()` to `TrackingViewModel` and observe `uiState`.
5. Move `startForegroundService()` call from `onCreate` into `TrackingViewModel.startWalk()` so GPS only starts when the user explicitly taps Start.

**Target files (MODIFIED):**
- `ui/tracking/TrackingScreenActivity.java` — inject `TrackingViewModelFactory`; observe `uiState`; delegate button clicks to ViewModel commands.
