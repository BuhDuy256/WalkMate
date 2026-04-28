# GPS Path Tracing — Sync Strategy Report

**Scope:** Frontend (Android) ↔ Backend (Spring Boot) GPS route-point synchronisation  
**Date:** 2026-04-28  
**Author:** Technical Review

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        FRONTEND (Android)                       │
│                                                                 │
│  FusedLocation          WalkTrackerService (Foreground)         │
│  Provider ──5s──► onLocationReceived()                          │
│                          │                                      │
│                   SessionTrackingService (executor)             │
│                          │                                      │
│               LocationFilterPolicy.shouldAccept()               │
│               (accuracy ≤ 300m, distance ≥ 1m, warm-up 3pts)   │
│                          │ pass                                 │
│                    RoutePointDao.insertPoint()  ◄── Room DB     │
│                          │                                      │
│           ┌──────────────┴──────────────────┐                  │
│   after save:                          every 30s:               │
│   unsyncedCount ≥ 50 ?          triggerPeriodicSync()           │
│   → triggerBatchSync()                  │                       │
│           └──────────────┬──────────────┘                      │
│                   pushRoutePoints()                             │
│              (reads unsynced from Room)                         │
│                          │                                      │
│              POST /api/v1/tracking/sync  ──────────────────────►│
│                          │                                      │
│              ◄── acknowledged_ids                               │
│              dao.markAsSynced(ids)                              │
│                                                                 │
│  TrackingViewModel ◄── LiveData<List<RoutePoint>> (Room)        │
│       │                                                         │
│  TrackingScreenActivity → Polyline drawn on Google Map          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                         BACKEND (Spring Boot)                   │
│                                                                 │
│  TrackingController.syncRoutePoints()                           │
│       │                                                         │
│  TrackingCommandService                                         │
│       ├─ Validate: session ACTIVE + caller is participant       │
│       │           + caller user status == ACTIVE                │
│       ├─ Validate: lat/lng bounds + timestamp not in future     │
│       ├─ Encode: Google Encoded Polyline (1e5 precision)        │
│       ├─ Pack: timestamps → big-endian BYTEA                    │
│       ├─ nextChunkIndex(): SELECT MAX(chunk_index)+1            │
│       ├─ saveChunk(): INSERT INTO session_point_chunks          │
│       └─ Return: acknowledged_ids (echo of localId[])          │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Responsibilities

| Component | Layer | Responsibility |
|---|---|---|
| `WalkTrackerService` | Android Foreground Service | Requests GPS fix every 5 s via `FusedLocationProviderClient`. Forwards raw fixes to `SessionTrackingService`. Runs as foreground service with persistent notification. |
| `SessionTrackingService` | Domain | Owns the GPS processing pipeline. Serializes all filter+save ops on a single-thread executor. Schedules a periodic sync every 30 s via `syncScheduler`. |
| `LocationFilterPolicy` | Domain | Stateful GPS noise filter. Rejects accuracy > 300 m (after warm-up). Rejects movement < 1 m from last accepted point. First 3 points bypass accuracy check (warm-up). Resets on pause. |
| `TrackingRepositoryImpl` | Data | Saves points to Room. Triggers batch push when unsynced count ≥ 50. Handles Retrofit sync call, parses `acknowledged_ids`, marks Room rows. |
| `RoutePointDao` | Data / Room | CRUD for `route_points` table. LiveData subscription drives polyline redraws. |
| `TrackingViewModel` | UI | Observes Room LiveData. Computes distance (Haversine), pace, elapsed time. Manages walk state machine (READY→ACTIVE→PAUSED→FINISHING→FINISHED). Persists timer state for screen rotation. |
| `TrackingController` | Backend Presentation | Single endpoint `POST /api/v1/tracking/sync`. JWT-authenticated. |
| `TrackingCommandService` | Backend Application | Full validation + encoding + persistence transaction. |
| `TrackingChunkJdbcRepository` | Backend Infrastructure | Writes one chunk row per sync call. |

---

## 3. Detailed Sync Flow

### 3.1 GPS Collection (Frontend)

```
GPS fix (every 5 s, min 1 m displacement)
   ↓
WalkTrackerService.onLocationResult()
   ↓ main thread hand-off
SessionTrackingService.onLocationReceived()
   ↓ submit to single-thread executor
processPoint()
   ↓
LocationFilterPolicy.shouldAccept(lat, lng, accuracy)
   ├── REJECT if accuracy > 300 m (after 3 warm-up points)
   ├── REJECT if distance from last accepted < 1 m
   └── ACCEPT
         ↓
RoutePoint { id=0, sessionId, lat, lng, timestamp=System.currentTimeMillis(), accuracy, isSynced=false }
         ↓
repository.saveRoutePoint()  →  Room INSERT (background thread)
         ↓
callback.onSuccess(rowId)
   ├── filterPolicy.accept(lat, lng)   ← update "last good" position
   └── unsyncedCount = dao.getUnsyncedCount()
         └── if count ≥ 50 → triggerBatchSync()
```

### 3.2 Dual Sync Trigger

Two independent paths can initiate a backend push:

| Trigger | Condition | Latency |
|---|---|---|
| **Batch trigger** | Accumulates 50 unsynced Room rows | ~250 s at 5 s GPS interval (varies with filter rejections) |
| **Periodic timer** | Every 30 seconds unconditionally | 30 s max lag for any accepted point |

Both paths converge at `pushRoutePoints()`, which:
1. Reads all unsynced rows from Room (`isSynced = 0`)
2. Maps to `PushRoutePointsRequest.RoutePointPayload` (localId, lat, lng, timestamp, accuracy)
3. Calls `POST /api/v1/tracking/sync` synchronously (Retrofit `.execute()`)
4. On HTTP 200: reads `acknowledged_ids` → `dao.markAsSynced(ids)`
5. On `SESSION_NOT_ACTIVE` / `SESSION_NOT_FOUND`: fires `SessionEndedListener` → foreground service stops

### 3.3 Backend Processing

```
POST /api/v1/tracking/sync
  { session_id, points: [{ local_id, lat, lng, timestamp, accuracy }] }

TrackingCommandService.syncRoutePoints()
  1. sessionRepository.findById(sessionId)          // 404 if missing
  2. caller is participant check                     // 403 if not
  3. callerStatus == ACTIVE check                   // 400 SESSION_NOT_ACTIVE
  4. per-point: lat∈[-90,90], lng∈[-180,180], timestamp ≤ now
  5. encode lats+lngs → Google Polyline string
  6. pack timestamps → ByteBuffer big-endian BYTEA
  7. SELECT MAX(chunk_index)+1 FROM session_point_chunks WHERE session_id AND user_id
  8. INSERT INTO session_point_chunks (chunk_id, session_id, user_id, chunk_index,
                                       polyline, timestamps, point_count)
  9. return { acknowledged_ids: [localId, ...] }
```

### 3.4 UI Path (Real-time Polyline)

```
Room INSERT
   ↓  (Room notifies on main thread)
LiveData<List<RoutePointEntity>> emits
   ↓
TrackingViewModel.latestRoutePoints updated
   ↓
rebuildUiState() → TrackingUiState { mapPoints, distanceKm, pace, ... }
   ↓
TrackingScreenActivity.renderState()
   ↓
polyline.setPoints(latLngList)  /  googleMap.animateCamera()
```

The UI polyline is driven **entirely from Room**, not from the server response. The user sees new segments drawn as soon as a point passes the filter and is persisted locally — there is no visible network round-trip delay on the map.

### 3.5 Walk State Machine

```
READY ──[startWalk()]──► ACTIVE ──[pauseWalk()]──► PAUSED
                           ▲                           │
                           └──────[resumeWalk()]───────┘
                           │
                   [requestCompleteWalk()]
                           │
                        FINISHING ──(API call)──► FINISHED
                                  ◄─(on error)── (reverts to prev state)
```

On `pauseWalk()`: GPS service stops, `LocationFilterPolicy.reset()` is called so the next resume fix is always accepted regardless of distance.

On `requestCompleteWalk()`: minimum walk guard (10 s in code, `WalkSession.MINIMUM_WALK_DURATION_SECONDS`) before API is called. Session is completed via `sessionRepository.completeSession(sessionId)`.

### 3.6 State Persistence (Screen Rotation Safety)

Timer epoch values (`walkStartEpochMs`, `pausedAccumulatedMs`, `pauseStartEpochMs`) and `WalkState` are persisted to Room via `TrackingStateRepository` on every state transition. On `startTrackingSession()`, the ViewModel restores state asynchronously so closing and re-opening the screen never resets an in-progress walk.

---

## 4. Database Schema Mapping

### Frontend Room (`route_points`)
```
route_points
  id            LONG PK (autoGenerate)
  sessionId     TEXT
  userId        TEXT         ← per-user scoping on shared device
  lat           REAL
  lng           REAL
  timestamp     INTEGER      ← Unix epoch ms
  accuracy      REAL         ← metres
  isSynced      INTEGER      ← 0 = pending, 1 = acknowledged by backend
```
Index: `(sessionId, userId, timestamp)` — scopes all queries to specific user.

### Backend PostgreSQL (`session_point_chunks`)
```sql
session_point_chunks
  chunk_id      UUID PK
  session_id    UUID FK → walk_session
  user_id       UUID FK → user_account
  chunk_index   INTEGER  ← sequential per (session_id, user_id)
  polyline      TEXT     ← Google Encoded Polyline
  timestamps    BYTEA    ← big-endian int64 array, 8 bytes × point_count
  elevations    BYTEA    ← NULL (not collected by frontend)
  point_count   INTEGER
  created_at    TIMESTAMP
```

**Compression format:**
- Coordinates: Google Encoded Polyline (5 decimal places ≈ 1.1 m precision)
- Timestamps: raw big-endian `long[]` packed into BYTEA

---

## 5. Identified Problems

### CRITICAL

---

#### P1 — Race Condition in `chunk_index` Assignment

**Location:** `TrackingChunkJdbcRepository.nextChunkIndex()` + `saveChunk()`

```java
// backend
int chunkIndex = chunkRepository.nextChunkIndex(sessionId, callerId);  // SELECT MAX+1
chunkRepository.saveChunk(..., chunkIndex, ...);                        // INSERT
// These two are NOT atomic — they are separate statements inside @Transactional
// but the SELECT MAX is not locked
```

**Problem:** Two concurrent sync requests for the same `(sessionId, userId)` — e.g., a retry while the first request is still processing — both execute `SELECT MAX(chunk_index) + 1` before either INSERT commits. Both return the same index. One INSERT will succeed; the other will store a duplicate chunk at the same index (no UNIQUE constraint on `(session_id, user_id, chunk_index)`).

**Impact:** Chunk data duplicated at reconstruction time. Route replay shows doubled segments.

---

#### P2 — Unsynced Points Lost on Session Complete

**Location:** `TrackingViewModel.requestCompleteWalk()`

```java
stopTimer();
stopGpsService();                          // GPS stops; no new points
walkStateLiveData.setValue(WalkState.FINISHING);
sessionRepository.completeSession(sessionId, callback);  // ← backend marks session COMPLETED
```

**Problem:** There is no final flush of unsynced Room points before `completeSession()` is called. Once the backend transitions the session to `COMPLETED`, all subsequent `POST /api/v1/tracking/sync` calls return `SESSION_NOT_ACTIVE`. Any points that had not yet been pushed to the backend (could be up to 49 unsent points from batch, plus up to 30 seconds of points from periodic timer) are permanently lost from server storage.

**Impact:** Last leg of every walk is missing from the server-side route. Distance calculations on the backend under-count total walk distance.

---

#### P3 — Double Push of the Same Batch (Concurrent Sync Paths)

**Location:** `TrackingRepositoryImpl.saveRoutePoint()` + `triggerPeriodicSync()`

**Problem:** Both the batch trigger (from `saveRoutePoint()` executor task) and the periodic timer (from `syncScheduler`) independently call `pushRoutePoints()`, which reads **all currently unsynced rows**. If a batch push is in-flight (HTTP request sent, awaiting response) when the 30 s periodic timer fires:

1. The first push reads 50 unsynced rows and sends them.
2. Before the HTTP response is received (so `markAsSynced` has not yet run), the periodic timer fires and reads the same 50 rows (still `isSynced = 0`).
3. A second HTTP request sends the same 50 points again.
4. The backend has no deduplication — it inserts a second chunk.

**Impact:** Duplicate GPS data on the server. Route reconstruction produces doubled/overlapping segments. Server storage bloat.

---

#### P4 — No Retry Strategy for Failed Syncs

**Location:** `TrackingRepositoryImpl.triggerBatchSync()` and `triggerPeriodicSync()`

```java
@Override
public void onError(Exception error) {
    Log.e(TAG, "Batch sync failed — will retry: " + error.getMessage());
    // No actual retry. Will wait for next periodic trigger (up to 30 s).
}
```

**Problem:** On transient network failure (tunnel, elevator, weak signal), a batch push fails silently. The next retry is the periodic sync 30 s later. If the failure is sustained (offline period), points accumulate in Room indefinitely. There is no WorkManager job, no exponential backoff, and no user-visible indication that syncing is failing.

**Impact:** Extended offline periods (>30 s) result in large queues. If the app process is killed while offline, the periodic sync scheduler is destroyed. Points survive in Room but will not be pushed until the app is re-opened.

---

### MEDIUM

---

#### P5 — `accuracy` Field Sent but Not Stored on Backend

**Location:** `PushRoutePointsRequest.RoutePointPayload.accuracy` → not written to any column

**Problem:** The frontend sends accuracy per point. The backend DTO declares the `accuracy` field but `TrackingCommandService` ignores it entirely — only `lat`, `lng`, and `timestamp` are encoded. The schema has an `elevations` BYTEA column but no `accuracies` column.

**Impact:** Accuracy data that passed the `LocationFilterPolicy` (and therefore may still be up to 300 m) is not persisted. Post-session route cleaning or trust score computation cannot use accuracy to weigh or discard GPS points.

---

#### P6 — Warm-up Points Inflate Distance Calculation

**Location:** `LocationFilterPolicy.WARM_UP_POINTS = 3`, `TrackingViewModel.computeDistanceKm()`

**Problem:** The first 3 accepted points bypass the accuracy filter. On devices with slow GPS cold-start, these points can have accuracy of 200–300 m, potentially placing them hundreds of metres from the true start position. The ViewModel's Haversine sum includes all Room points, so these inaccurate warm-up points inflate the displayed distance from the very beginning.

**Impact:** Distance and pace metrics shown to the user are incorrect for the first ~15 seconds. The backend receives and permanently stores these noisy coordinates.

---

#### P7 — Backend User Status Must Be ACTIVE Before First Sync

**Location:** `TrackingCommandService` — `callerStatus != SessionStatus.ACTIVE` check

**Problem:** The backend validates that the caller's individual status on the walk session (`user_a_status` / `user_b_status`) is `ACTIVE`. This status is set when the user "activates" the session (arrives at the meeting point). If the user starts the local walk (pressing Start) before the session is marked ACTIVE on the server — for instance due to a delayed activation HTTP response — the first sync batch will be rejected with `SESSION_NOT_ACTIVE`, and the `SessionEndedListener` will stop the foreground service entirely, ending the walk prematurely.

**Impact:** Walk terminated falsely if the activation API and the first sync race.

---

#### P8 — `chunk_index` Not Guaranteed to Be Temporally Ordered

**Location:** `TrackingChunkJdbcRepository.nextChunkIndex()` + backend `@Transactional`

**Problem:** `chunk_index` is assigned server-side based on arrival order, not on the `timestamp` of the earliest point in the batch. If network retransmission causes an older batch to arrive after a newer one (unlikely but possible), the chunk with the earlier GPS timestamps will have a higher `chunk_index`. Route reconstruction queries `ORDER BY chunk_index ASC`, which will present points out of chronological order.

**Impact:** Route replay shows GPS path doubling back incorrectly when chunks arrive out of order.

---

#### P9 — No Max Chunk Size Guard

**Location:** `TrackingRepositoryImpl.triggerPeriodicSync()`

**Problem:** `triggerPeriodicSync()` reads ALL unsynced rows and sends them in one HTTP request with no upper bound. If the device was offline for 10 minutes (120 unfiltered points, potentially 50+ accepted), a single request with 50+ points is sent. With JSON encoding, each point is ~80 bytes → manageable. But as offline duration grows, request size grows unboundedly.

**Impact:** Request body can grow very large during extended offline periods. Some mobile networks/proxies reject bodies above certain sizes. Backend validation has no chunk size limit either.

---

### MINOR

---

#### P10 — Duplicate Haversine Implementation

`TrackingViewModel` and `LocationFilterPolicy` each contain their own Haversine formula (identical math, different return types). This is a maintenance risk — a fix in one will not propagate to the other.

---

#### P11 — Pause Does Not Drain Unsynced Points

When `pauseWalk()` is called, `stopGpsService()` stops new points from arriving, but there is no sync triggered for points already in Room that have not yet been pushed. They wait up to 30 s for the next periodic sync (the `syncScheduler` is not stopped on pause, so this eventually fires).

---

#### P12 — JWT Expiry During Long Walks

If a walk lasts longer than the JWT lifetime, all sync calls will begin returning HTTP 401. The error handler in `pushRoutePoints()` logs it but does not attempt a token refresh. Points accumulate in Room unsynced. There is no session-level token refresh wired into the sync loop.

---

## 6. Proposed Improvements

### R1 — Final Flush Before `completeSession()` *(fixes P2, highest priority)*

Before calling `sessionRepository.completeSession()`, force-flush all unsynced Room points. Only proceed with completion after the flush succeeds (or after N attempts):

```java
// TrackingViewModel.requestCompleteWalk()
stopTimer();
stopGpsService();
walkStateLiveData.setValue(WalkState.FINISHING);

repository.flushUnsyncedPoints(sessionId, new DomainCallback<Void>() {
    @Override
    public void onSuccess(Void v) {
        sessionRepository.completeSession(sessionId, completionCallback);
    }
    @Override
    public void onError(Exception e) {
        // Log but still complete — don't block the user for sync failure
        sessionRepository.completeSession(sessionId, completionCallback);
    }
});
```

---

### R2 — Idempotent Chunk Insertion *(fixes P1, P3)*

#### Option A — Client-generated `sync_request_id` (Recommended)

Add a UUID generated by the client per push attempt. The backend stores it and uses it as a deduplication key:

```sql
ALTER TABLE session_point_chunks
  ADD COLUMN sync_request_id UUID,
  ADD CONSTRAINT uq_sync_request UNIQUE (sync_request_id);
```

Frontend generates one UUID per `pushRoutePoints()` call. On duplicate submission (network retry), the INSERT fails the unique constraint → the backend returns 200 with the same `acknowledged_ids` (idempotent response).

#### Option B — Database Sequence for `chunk_index`

```sql
CREATE SEQUENCE chunk_index_seq START 1;
-- or use a per-(session,user) sequence via a sequence table
```

This eliminates the `SELECT MAX + 1` race but does not solve the double-push problem.

---

### R3 — WorkManager for Durable Sync *(fixes P4)*

Replace the in-process `syncScheduler` with a `WorkManager` periodic task:

```java
PeriodicWorkRequest syncWork = new PeriodicWorkRequest.Builder(
        RouteSyncWorker.class, 15, TimeUnit.MINUTES)
    .setConstraints(new Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,
        WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
    .build();
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "route_sync", ExistingPeriodicWorkPolicy.KEEP, syncWork);
```

**Benefits:**
- Survives process death
- Constraint-aware (only syncs when connected)
- Built-in exponential backoff on failure
- Android OS manages scheduling across Doze/App Standby

Keep the in-session 30 s timer for real-time responsiveness; WorkManager is the safety net for persistent delivery.

---

### R4 — In-flight Guard to Prevent Double Push *(fixes P3)*

Add an atomic flag in `TrackingRepositoryImpl` to skip a new sync if one is already running:

```java
private final AtomicBoolean syncInFlight = new AtomicBoolean(false);

private void triggerBatchSync(String sessionId, String userId) {
    if (!syncInFlight.compareAndSet(false, true)) {
        return; // sync already in flight, skip
    }
    List<RoutePointEntity> unsyncedEntities = dao.getUnsyncedPoints(sessionId, userId);
    if (unsyncedEntities.isEmpty()) {
        syncInFlight.set(false);
        return;
    }
    pushRoutePoints(sessionId, ..., new DomainCallback<Void>() {
        @Override public void onSuccess(Void v) { syncInFlight.set(false); }
        @Override public void onError(Exception e) { syncInFlight.set(false); }
    });
}
```

---

### R5 — Server-side `chunk_index` Using Advisory Lock or UNIQUE Constraint *(fixes P1)*

Add a UNIQUE constraint on `(session_id, user_id, chunk_index)` so duplicate indices are rejected at DB level, and change `nextChunkIndex` to use a row-level lock:

```sql
ALTER TABLE session_point_chunks
  ADD CONSTRAINT uq_chunk_index UNIQUE (session_id, user_id, chunk_index);
```

```sql
-- nextChunkIndex via locked read
SELECT COALESCE(MAX(chunk_index) + 1, 0)
FROM session_point_chunks
WHERE session_id = :sessionId AND user_id = :userId
FOR UPDATE;  -- advisory row lock inside the same @Transactional
```

---

### R6 — Sort Chunks by Earliest Timestamp, Not `chunk_index` *(fixes P8)*

Store the earliest timestamp of the batch as a column and order by it on reconstruction:

```sql
ALTER TABLE session_point_chunks ADD COLUMN earliest_ts BIGINT;
-- Reconstruction query:
SELECT polyline, timestamps FROM session_point_chunks
WHERE session_id = :sessionId AND user_id = :userId
ORDER BY earliest_ts ASC;
```

---

### R7 — Persist Accuracy per Chunk *(fixes P5)*

Pack accuracy values into a BYTEA column alongside timestamps, using the same big-endian float32 packing:

```sql
ALTER TABLE session_point_chunks ADD COLUMN accuracies BYTEA;
```

This enables post-session route smoothing and backend-side point weighting for trust score computation.

---

### R8 — Cap Chunk Size *(fixes P9)*

In `triggerPeriodicSync()` and `triggerBatchSync()`, limit the batch to a maximum of 200 points per HTTP request. Submit multiple requests for larger queues:

```java
private static final int MAX_POINTS_PER_REQUEST = 200;

List<RoutePointEntity> all = dao.getUnsyncedPoints(sessionId, userId);
for (int i = 0; i < all.size(); i += MAX_POINTS_PER_REQUEST) {
    List<RoutePointEntity> slice = all.subList(i, Math.min(i + MAX_POINTS_PER_REQUEST, all.size()));
    pushRoutePoints(sessionId, toDomain(slice), callback);
}
```

---

## 7. Summary Table

| # | Severity | Problem | Recommended Fix |
|---|---|---|---|
| P1 | Critical | `chunk_index` race condition (non-atomic SELECT MAX + INSERT) | R5 — UNIQUE constraint + `FOR UPDATE` lock |
| P2 | Critical | Unsynced points lost on session complete (no final flush) | R1 — flush before `completeSession()` |
| P3 | Critical | Double push of same batch (batch + periodic sync overlap) | R4 — `AtomicBoolean` in-flight guard + R2 — idempotent insertion |
| P4 | Medium | No retry strategy for failed syncs (no backoff, no durability) | R3 — WorkManager with exponential backoff |
| P5 | Medium | `accuracy` field sent but not stored on backend | R7 — persist accuracy BYTEA column |
| P6 | Medium | Warm-up points inflate distance/pace display | Filter warm-up points from distance computation |
| P7 | Medium | First sync rejected if session status still PENDING | Retry sync or activate before GPS start |
| P8 | Medium | Chunk ordering by `chunk_index` ≠ temporal ordering | R6 — sort by `earliest_ts` |
| P9 | Minor | No max chunk size → unbounded request body | R8 — cap at 200 points per request |
| P10 | Minor | Haversine duplicated in ViewModel and FilterPolicy | Extract to shared utility class |
| P11 | Minor | Pause does not drain unsynced points | Trigger sync on `pauseWalk()` |
| P12 | Minor | JWT expiry during long walk breaks sync silently | Wire token refresh into `pushRoutePoints()` error path |

---

## 8. Alternative Sync Strategy (MQTT / WebSocket)

The current strategy is **HTTP pull-push**: the client decides when to push and the server is passive. An alternative for future consideration is **WebSocket streaming**:

```
Client ──WS connect on walk start──► Server
Client ──GPS fix (per point)───────► Server (immediate, no batching)
Server ──ACK (per point)───────────► Client
Client ──WS close on walk end──────► Server
```

**Pros:** Sub-second server-side route update, enables real-time partner location sharing, eliminates batch complexity.

**Cons:** Higher battery and data consumption, complex reconnect logic, server-side stateful connection management, not suitable for Doze mode. The current project constraint (offline-first, battery efficiency) makes HTTP the appropriate choice for V1.

The recommended path is: **keep HTTP batch sync** but address P1–P4 (critical issues) before production release.
