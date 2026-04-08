# Phase 3 Report — Data Layer: Mappers
**Date:** 2026-04-09
**Branch:** `implement/realtime`
**Gaps closed:** 3.2 (intent expiresAt), 4.1 (proposal fields), 6.1 (session timestamps), 6.4 (acknowledged_ids)

---

## Files Modified

### 1. `WalkIntentMapper.java`

**Change:** Deleted `toRequest()` factory method; added Phase 4 TODO comments for dropped fields.

| DTO Field | Before | After |
|---|---|---|
| `id` | mapped | mapped |
| `hotspotId` | mapped | mapped |
| `userId` | mapped | mapped |
| `timeWindowStart` | mapped | mapped |
| `timeWindowEnd` | mapped | mapped |
| `ageMin` | mapped | mapped |
| `ageMax` | mapped | mapped |
| `status` | mapped | mapped |
| `createdAt` | mapped | mapped |
| `expiresAt` | **dropped** | **TODO Phase 4 comment** |
| `description` | not in DTO | **TODO Phase 4 comment** (pass null) |

`toRequest()` — deleted. Repositories build `CreateWalkIntentRequest` directly.

---

### 2. `WalkProposalMapper.java`

**Change:** Added Phase 4 TODO comments for all currently-dropped fields in `toDomain()`.

| DTO Field | Before | After |
|---|---|---|
| `proposalId` | mapped | mapped |
| `callersIntentId` | mapped | mapped |
| `matchedUserId` | mapped | mapped |
| `proposedTimeStart` | mapped (→ overlappingTimeStart) | mapped |
| `proposedTimeEnd` | mapped (→ overlappingTimeEnd) | mapped |
| `status` | mapped | mapped |
| `expiresAt` | **dropped** | **TODO Phase 4 comment** |
| `proposedLat` | **dropped** | **TODO Phase 4 comment** (→ meetingLat) |
| `proposedLng` | **dropped** | **TODO Phase 4 comment** (→ meetingLng) |
| `myAcceptanceStatus` | **dropped** | **TODO Phase 4 comment** |
| `sessionId` | **dropped** | **TODO Phase 4 comment** |

`toSession()` — unchanged (builds `WalkSession` from a confirmed proposal; already maps proposedLat/Lng).

---

### 3. `WalkSessionMapper.java`

**Change:** Added `isCallerUserA` local variable (already computed via `callerId` comparison) with Phase 4 TODO to wire into domain model; added TODO comments for all dropped timestamp/flag fields.

#### New `toDomain()` Signature
```java
public static WalkSession toDomain(WalkSessionResponse response, String callerId)
```
**This signature was already present before Phase 3.** No change required.

| DTO Field | Before | After |
|---|---|---|
| `sessionId` | mapped | mapped |
| `proposalId` | mapped | mapped |
| `userIdA` / `userIdB` | mapped (partner computed) | mapped |
| `meetingPointLat` | mapped | mapped |
| `meetingPointLng` | mapped | mapped |
| `scheduledStart` | mapped | mapped |
| `status` | mapped | mapped |
| `scheduledEnd` | **dropped** | **TODO Phase 4 comment** |
| `startedAt` | **dropped** | **TODO Phase 4 comment** |
| `endedAt` | **dropped** | **TODO Phase 4 comment** |
| `userAActivatedAt` | **dropped** | **TODO Phase 4 comment** |
| `userBActivatedAt` | **dropped** | **TODO Phase 4 comment** |
| `isReviewed` | **dropped** | **TODO Phase 4 comment** |
| `isCallerUserA` (computed) | implicit | **TODO Phase 4 comment** |

#### Call Sites — No Changes Needed
Both call sites already pass `callerId`:

| File | Line | Call |
|---|---|---|
| `WalkSessionRepositoryImpl.java` | 54 | `WalkSessionMapper.toDomainList(data, callerId)` |
| `WalkSessionRepositoryImpl.java` | 81 | `WalkSessionMapper.toDomain(data, callerId)` |

---

### 4. `TrackingRepositoryImpl.java`

**Change:** `pushRoutePoints()` now reads `acknowledgedIds` from the server response and marks them synced immediately via `dao.markAsSynced(acked)`. `triggerBatchSync()` callback no longer double-marks.

**Before (`pushRoutePoints` success block):**
```java
Log.d(TAG, "Sync succeeded — " + points.size() + " points acknowledged");
callback.onSuccess(null);
```

**After:**
```java
List<Long> acked = response.body().getData().getAcknowledgedIds();
if (acked != null && !acked.isEmpty()) {
    dao.markAsSynced(acked);
}
Log.d(TAG, "Sync succeeded — " + points.size() + " points acknowledged");
callback.onSuccess(null);
```

`triggerBatchSync` `onSuccess` now only logs — the DAO write is handled inside `pushRoutePoints`.

---

## New Files Created

### 5. `SessionSummaryMapper.java`
**Path:** `data/mapper/SessionSummaryMapper.java`

Maps `WalkSessionResponse` → `SessionSummary` (Phase 4 domain model).

Signature:
```java
public static SessionSummary toDomain(WalkSessionResponse response, String callerId)
public static List<SessionSummary> toDomainList(List<WalkSessionResponse> responses, String callerId)
```

| Field | Source |
|---|---|
| `sessionId` | `response.getSessionId()` |
| `status` | `response.getStatus()` |
| `partnerId` | computed from `callerId` vs `userIdA`/`userIdB` |
| `scheduledStart` | `response.getScheduledStart()` |
| `totalDistanceKm` | `0.0` (not in `WalkSessionResponse`) |
| `durationMinutes` | `0` (not in `WalkSessionResponse`) |
| `isReviewed` | `response.isReviewed()` |

### 6. `SessionRouteMapper.java`
**Path:** `data/mapper/SessionRouteMapper.java`

Maps `SessionRouteResponse` → `SessionRoute` (Phase 4 domain model). Simple field copy.

| Field | Source |
|---|---|
| `userAPolylines` | `response.getUserAPolylines()` |
| `userBPolylines` | `response.getUserBPolylines()` |
| `totalDistanceKm` | `response.getTotalDistanceKm()` |
| `durationMinutes` | `response.getDurationMinutes()` |

---

## Fields Waiting on Phase 4 Domain Model Additions

| Domain Model | Fields to Add |
|---|---|
| `WalkIntent` | `expiresAt` (String), `description` (String, nullable) |
| `WalkProposal` | `expiresAt`, `meetingLat`, `meetingLng`, `myAcceptanceStatus`, `sessionId` |
| `WalkSession` | `isCallerUserA`, `scheduledEnd`, `startedAt`, `endedAt`, `userAActivatedAt`, `userBActivatedAt`, `isReviewed` |
| `SessionSummary` | new class — 7 fields listed above |
| `SessionRoute` | new class — 4 fields listed above |

`SessionSummaryMapper` and `SessionRouteMapper` will not compile until Phase 4 creates `SessionSummary` and `SessionRoute`.
