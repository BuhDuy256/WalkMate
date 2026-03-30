# Context Sync — Phase 1 Complete
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
| **Tech stack** | Java (no Kotlin), MVVM + DDD-lite, LiveData, ExecutorService, Room, Retrofit + OkHttp, Manual DI via `WalkMateApplication` |

---

## Completed in Phase 1

- **`WalkState` enum** — four-state walk lifecycle (`READY`, `ACTIVE`, `PAUSED`, `FINISHED`) defined in the domain layer.
- **`RoutePoint` domain model** — plain Java POJO in `domain/tracking/`; no Room or Android imports.
- **`TrackingRepository` interface** — declared in `domain/tracking/`; exposes both synchronous `DomainCallback`-based write ops and a reactive `LiveData<List<RoutePoint>>` read op for map polyline updates.
- **`TrackingErrorCode`** — string constants for tracking-specific errors.
- **`TrackingUiState`** — immutable UI snapshot in `ui/tracking/`; carries `WalkState`, `List<LatLng>`, `distanceKm`, `elapsedSeconds`, `paceMinPerKm`, `partnerName`, `isCameraFollowingUser`.
- **Room DB foundation** — `RoutePointEntity`, `RoutePointDao`, `WalkMateDatabase` (version 1, double-checked locking singleton, `fallbackToDestructiveMigration`).
- **Remote DTOs** — `PushRoutePointsRequest` (with nested `RoutePointPayload`), `PushRoutePointsResponse`, `RoutePointSyncApiService` (Retrofit interface for `POST /api/v1/tracking/sync`).
- **`RoutePointMapper`** — pure static mapper: Entity ↔ Domain, Entity → Remote payload.
- **`TrackingRepositoryImpl`** — concrete implementation with mock backend push (see below) and auto batch-sync trigger at 50 unsynced points.
- **`WalkMateApplication`** — Application class created as Service Locator; holds `WalkMateDatabase` and `TrackingRepository` singletons.
- **`AndroidManifest.xml`** — `android:name=".WalkMateApplication"` added; `TrackingScreenActivity` pre-registered (stub, wired in Phase 3).
- **`libs.versions.toml`** — Room `2.7.0` added (`roomRuntime`, `roomCompiler`).
- **`build.gradle.kts`** — `implementation(libs.roomRuntime)` + `annotationProcessor(libs.roomCompiler)` added.

---

## Key Technical Decisions

### 1. Room DB Setup
- Single `WalkMateDatabase` (`version = 1`) with one entity: `route_points`.
- Composite index on `(sessionId, timestamp)` for efficient per-session ordered reads.
- `exportSchema = false` for now — switch to `true` + explicit migration scripts before production.
- `fallbackToDestructiveMigration()` is intentional for the current development phase; must be removed for production.

### 2. Mock Backend Push Strategy
**Pattern used:** mock inside `TrackingRepositoryImpl.pushRoutePoints()` — no OkHttp interceptor, no separate mock class.

**Why:** Consistent with every other mock in the codebase (`HotspotRepositoryImpl`, `WalkSessionRepositoryImpl`) which simulate delay via `Thread.sleep()` on the executor. The mock logs the exact payload that *would* be sent (sessionId, point count), sleeps 800 ms, then calls `onSuccess`. The real Retrofit call is written out in a commented block directly below the mock body — swapping mock → real is a one-line diff.

**Auto batch-sync:** After each `saveRoutePoint`, the impl checks `getUnsyncedCount`. When ≥ 50 unsynced points exist, `triggerBatchSync()` fires automatically, calls `pushRoutePoints` (mock), then `markAsSynced` on success. This mirrors the architecture described in `gps-path-tracing-architecture.md`.

### 3. `WalkState` Design
- Pure Java `enum` in `domain/tracking/` — zero Android imports, fully unit-testable.
- Four states cover the complete Strava-like lifecycle: pre-walk (`READY`), active tracking (`ACTIVE`), temporary halt (`PAUSED`), and completion (`FINISHED`).
- `TrackingUiState` carries a `WalkState` field; the Activity's `renderState()` switch drives all control-button visibility from this single field (no separate boolean flags).

### 4. `WalkMateApplication` (Service Locator)
- First Application class in the project — previously no Application subclass existed.
- Only holds tracking singletons for now; other repos still instantiate their dependencies inline in their ViewModelFactory (existing pattern — not broken).
- Other features should gradually migrate their repos here as they are touched.

---

## Created / Modified Files

### NEW files
| File | Layer |
|---|---|
| `frontend/src/main/java/com/walkmate/domain/tracking/WalkState.java` | Domain |
| `frontend/src/main/java/com/walkmate/domain/tracking/RoutePoint.java` | Domain |
| `frontend/src/main/java/com/walkmate/domain/tracking/TrackingRepository.java` | Domain |
| `frontend/src/main/java/com/walkmate/domain/tracking/TrackingErrorCode.java` | Domain |
| `frontend/src/main/java/com/walkmate/ui/tracking/TrackingUiState.java` | UI |
| `frontend/src/main/java/com/walkmate/data/datasource/local/entity/RoutePointEntity.java` | Data — Local |
| `frontend/src/main/java/com/walkmate/data/datasource/local/dao/RoutePointDao.java` | Data — Local |
| `frontend/src/main/java/com/walkmate/data/datasource/local/WalkMateDatabase.java` | Data — Local |
| `frontend/src/main/java/com/walkmate/data/datasource/remote/dto/request/tracking/PushRoutePointsRequest.java` | Data — Remote |
| `frontend/src/main/java/com/walkmate/data/datasource/remote/dto/response/tracking/PushRoutePointsResponse.java` | Data — Remote |
| `frontend/src/main/java/com/walkmate/data/datasource/remote/api/RoutePointSyncApiService.java` | Data — Remote |
| `frontend/src/main/java/com/walkmate/data/mapper/RoutePointMapper.java` | Data — Mapper |
| `frontend/src/main/java/com/walkmate/data/repository/TrackingRepositoryImpl.java` | Data — Repository |
| `frontend/src/main/java/com/walkmate/WalkMateApplication.java` | App |

### MODIFIED files
| File | What changed |
|---|---|
| `frontend/src/main/AndroidManifest.xml` | Added `android:name=".WalkMateApplication"`; pre-registered `TrackingScreenActivity` |
| `gradle/libs.versions.toml` | Added `room = "2.7.0"`, `roomRuntime`, `roomCompiler` library entries |
| `frontend/build.gradle.kts` | Added `implementation(libs.roomRuntime)` + `annotationProcessor(libs.roomCompiler)` |

---

## Next Steps

**The next prompt should cover Phase 2 and Phase 3 in sequence.**

### Phase 2 — Service Lifecycle Fixes
Target files: `WalkTrackerService.java`, `SessionTrackingService.java`, `TrackingScreenActivity.java` (partially).

Key tasks:
1. Create `WalkTrackerService` (Foreground Service) — GPS acquisition via `FusedLocationProviderClient`, calls `TrackingRepository.saveRoutePoint()`.
2. Add `stopTracking()` to shut down the executor gracefully in `onDestroy`.
3. Remove the hardcoded session ID; read `SESSION_ID` from the Intent extra.
4. Add `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, and `POST_NOTIFICATIONS` permissions to `AndroidManifest.xml`.
5. Register `WalkTrackerService` in the Manifest.

### Phase 3 — Entry-Point Wiring
Target files: `SessionAdapter.java`, `SessionFragment.java`, `TrackingScreenActivity.java` (stub).

Key tasks:
1. Add `OnStartWalkClickListener` to `SessionAdapter`; show "Start Walk" button only for `PENDING_MEET` sessions.
2. Wire the listener in `SessionFragment` to launch `TrackingScreenActivity` with Intent extras (`SESSION_ID`, `PARTNER_NAME`, `MEETING_POINT_LAT`, `MEETING_POINT_LNG`).
3. Create the stub `TrackingScreenActivity` that reads the extras, calls `viewModel.init(sessionId, partnerName, lat, lng)`, and displays them — no full ViewModel or map yet.

After Phases 2 & 3, the walk can be started from a real session card and the service lifecycle is sound. Phase 4 (ViewModel timer + MediatorLiveData) and Phase 5 (full UI) follow.
