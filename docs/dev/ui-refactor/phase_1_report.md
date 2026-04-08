# Phase 1 Report — Data Layer: DTOs
**Date:** 2026-04-09
**Branch:** `implement/realtime`
**Gaps closed:** 3.1, 3.3 (partial), 6.2, 5.3 (request side), 7.2 (response side), 7.3 (request side)

---

## Files Modified

### 1. `CreateWalkIntentRequest.java`
**Path:** `frontend/src/main/java/com/walkmate/data/datasource/remote/dto/request/walkintent/CreateWalkIntentRequest.java`

**Changes:**
- Added field: `@SerializedName("description") private String description`
- Added `description` as the last parameter in the full-arg constructor
- Added getter: `public String getDescription()`
- No-arg constructor and all existing fields/getters left intact

**Gaps closed:** 3.1 (missing `description` in request DTO), 3.3 (prerequisite — DTO can now carry the value to the domain layer in Phase 2)

---

### 2. `PushRoutePointsResponse.java`
**Path:** `frontend/src/main/java/com/walkmate/data/datasource/remote/dto/response/tracking/PushRoutePointsResponse.java`

**Changes (BREAKING):**
- **REMOVED** field `private int syncedCount` and getter `getSyncedCount()`
- **ADDED** field: `@SerializedName("acknowledged_ids") private List<Long> acknowledgedIds`
- **ADDED** getter: `public List<Long> getAcknowledgedIds()`
- Added `import com.google.gson.annotations.SerializedName` and `import java.util.List`

**Gaps closed:** 6.2 (response now carries per-ID acknowledgement so mapper can do selective confirmation)

> ⚠️ **Breaking change for Phase 3:** `TrackingRepositoryImpl` currently calls `response.getSyncedCount()` to decide how many points to mark as synced. This call will no longer compile. Phase 3 must update `TrackingRepositoryImpl` to iterate `getAcknowledgedIds()` and mark only those specific point IDs as synced in the local Room database.

---

## Files Created

### 3. `CompleteWalkSessionRequest.java`
**Path:** `frontend/src/main/java/com/walkmate/data/datasource/remote/dto/request/walksession/CompleteWalkSessionRequest.java`
**Package:** `com.walkmate.data.datasource.remote.dto.request.walksession`

Empty POJO with a single no-arg constructor. Backend requires a POST with an empty body for `POST /api/v1/sessions/{sessionId}/complete`. Retrofit serialises this as `{}`.

**Gaps closed:** 5.3 (request side — `SessionApiService` can now declare the endpoint in Phase 3)

---

### 4. `ReportSessionRequest.java`
**Path:** `frontend/src/main/java/com/walkmate/data/datasource/remote/dto/request/walksession/ReportSessionRequest.java`
**Package:** `com.walkmate.data.datasource.remote.dto.request.walksession`

| Field | Serialised Name | Type | Nullable |
|---|---|---|---|
| `reportedUserId` | `reported_user_id` | `String` | No |
| `reason` | `reason` | `String` | No |
| `evidenceUrl` | `evidence_url` | `String` | Yes |

Full-arg constructor + getters for all three fields.

**Gaps closed:** 7.3 (request side — `SessionApiService` can now declare `POST /api/v1/sessions/{sessionId}/report` in Phase 3)

---

### 5. `SessionRouteResponse.java`
**Path:** `frontend/src/main/java/com/walkmate/data/datasource/remote/dto/response/session/SessionRouteResponse.java`
**Package:** `com.walkmate.data.datasource.remote.dto.response.session`

| Field | Serialised Name | Type |
|---|---|---|
| `userAPolylines` | `user_a_polylines` | `List<String>` |
| `userBPolylines` | `user_b_polylines` | `List<String>` |
| `totalDistanceKm` | `total_distance_km` | `double` |
| `durationMinutes` | `duration_minutes` | `int` |

Full-arg constructor + getters for all four fields.

**Gaps closed:** 7.2 (response side — `SessionApiService` can now declare `GET /api/v1/sessions/{sessionId}/route` in Phase 3)

---

## DTOs Read But Not Modified

| File | Finding |
|---|---|
| `WalkProposalResponse.java` | Already contains `expiresAt`, `proposedLat/Lng`, `myAcceptanceStatus`, `sessionId`. Missing `partnerAvatarUrl` and `hotspotName` — these are deferred to Phase 2 (domain model) since the domain layer is the authoritative consumer and fetching partner avatar requires a secondary API call. |
| `WalkSessionResponse.java` | Already contains all fields listed in gap 5.4 (`scheduledEnd`, `startedAt`, `endedAt`, `userAActivatedAt`, `userBActivatedAt`, `isReviewed`). Gap 5.4 is a mapper bug, not a DTO bug — no changes needed here. |

---

## Notes for Phase 2 (Domain Layer)

- `WalkIntent` domain model needs `expiresAt` and `description` fields wired from the DTO (gaps 3.2, 3.3).
- `WalkProposal` domain model needs `expiresAt`, `meetingLat/Lng`, `myAcceptanceStatus`, `partnerAvatarUrl`, `hotspotName` (gap 4.1).
- `WalkSession` domain model needs `scheduledEnd`, `startedAt`, `endedAt`, `userAActivatedAt`, `userBActivatedAt`, `isReviewed` mapped from the already-correct DTO (gap 5.4).

## Notes for Phase 3 (API Service + Repository Layer)

- `TrackingRepositoryImpl.triggerBatchSync()` **will not compile** until it is updated to use `getAcknowledgedIds()` instead of the removed `getSyncedCount()`.
- `SessionApiService` needs new endpoint declarations: `complete`, `report`, `route` (using the new request/response DTOs created here).
