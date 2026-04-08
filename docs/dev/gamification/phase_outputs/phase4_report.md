# Phase 4 Output Report — History Flow (Dual GPS Path Rendering)

**Date:** 2026-04-08
**Branch:** `implement/realtime`
**Implemented by:** Claude (Sonnet 4.6)

---

## 1. Pre-flight Verification Results

### 1.1 `TrackingChunkRepository` — per-user methods (Phase 1 confirmation)

Verified by direct file read (`TrackingChunkRepository.java`):

| Method | Signature | Status |
|--------|-----------|--------|
| `findPolylinesBySessionAndUser` | `List<String> findPolylinesBySessionAndUser(String sessionId, String userId)` | **PRESENT** — matches phase1_report.md exactly |
| `countChunks` | `int countChunks(String sessionId, String userId)` | **PRESENT** — matches phase1_report.md exactly |
| `nextChunkIndex` | `int nextChunkIndex(String sessionId, String userId)` | **PRESENT** (per-user scoped, Phase 1) |
| `saveChunk` | `void saveChunk(String sessionId, String userId, int chunkIndex, ...)` | **PRESENT** (per-user scoped, Phase 1) |
| `findPolylinesBySessionId` | `List<String> findPolylinesBySessionId(String sessionId)` | **PRESENT** (legacy, retained for dual-path) |

**No discrepancy with phase1_report.md.**

### 1.2 `WalkSessionRepository` — history method pre-check

Verified by direct file read: `findCompletedByUserId` did **NOT** exist prior to this phase.
Existing `find*` methods were: `findById`, `findByProposalId`, `findActiveForUser`,
`findSessionsPastActivationWindow`, `findSessionsPastEndTime`.

### 1.3 `WalkSessionJdbcRepository` — history implementation pre-check

No `findCompletedByUserId` implementation existed. Confirmed via direct file read.

### 1.4 Terminal statuses used in SQL `IN` clause

From `SessionStatus.java`:
```java
PENDING, ACTIVE, COMPLETED, NO_SHOW, CANCELLED, ABORTED
```
Terminal statuses included in history query: `COMPLETED`, `NO_SHOW`, `ABORTED`, `CANCELLED`.
(PENDING and ACTIVE are excluded — they are live sessions.)

### 1.5 `WalkSession` domain field names

Confirmed from `WalkSession.java`:
- `totalDistanceKm` (double) — getter: `getTotalDistanceKm()`
- `totalDurationSeconds` (long) — getter: `getTotalDurationSeconds()`
- `userIdA` / `userIdB` — getters: `getUserIdA()` / `getUserIdB()`
- `scheduledStart` / `scheduledEnd` — Instant fields

### 1.6 Pre-existence checks for new classes

- `TrackingQueryService` — did NOT exist (confirmed: no file at `application/tracking/TrackingQueryService.java`)
- `SessionHistoryQueryService` — did NOT exist
- `SessionHistoryController` — did NOT exist

---

## 2. Files Modified

| File | Change |
|------|--------|
| `backend/src/main/java/com/walkmate/domain/session/WalkSessionRepository.java` | Added `List<WalkSession> findCompletedByUserId(String userId)` |
| `backend/src/main/java/com/walkmate/infrastructure/repository/session/WalkSessionJdbcRepository.java` | Implemented `findCompletedByUserId` — added before the audit log section |
| `backend/src/main/java/com/walkmate/domain/session/SessionErrorCode.java` | Added `SESSION_NOT_FINISHED` error code |

---

## 3. Files Created

| File | Purpose |
|------|---------|
| `backend/src/main/java/com/walkmate/application/tracking/TrackingQueryService.java` | G-14 — Route query service |
| `backend/src/main/java/com/walkmate/application/session/SessionHistoryQueryService.java` | G-15 — Session history query service |
| `backend/src/main/java/com/walkmate/presentation/controller/session/SessionHistoryController.java` | G-16 — History and route endpoints |
| `backend/src/main/java/com/walkmate/presentation/dto/response/session/SessionRouteResponse.java` | G-16 — Route response DTO |
| `backend/src/main/java/com/walkmate/presentation/dto/response/session/SessionSummaryResponse.java` | G-16 — History list entry DTO |

---

## 4. History SQL

```sql
SELECT session_id::text, proposal_id::text,
       user_id_a::text, user_id_b::text,
       meeting_point_lat, meeting_point_lng,
       scheduled_start, scheduled_end,
       status, created_at, started_at, ended_at,
       user_a_activated_at, user_b_activated_at,
       cancellation_reason, cancelled_by::text,
       abort_reason, version,
       total_distance_km, total_duration_seconds
FROM walk_session
WHERE (user_id_a = :userId OR user_id_b = :userId)
  AND status IN ('COMPLETED', 'NO_SHOW', 'ABORTED', 'CANCELLED')
ORDER BY COALESCE(ended_at, created_at) DESC
LIMIT 50
```

The `COALESCE(ended_at, created_at)` fallback handles `NO_SHOW` sessions where `ended_at` may be null (those sessions never started). `LIMIT 50` is an initial safeguard; pagination can be added as a follow-up.

---

## 5. Route Endpoint Logic

### Session-status guard in `getSessionRoute`

```java
SessionStatus status = session.getStatus();
if (status == SessionStatus.PENDING || status == SessionStatus.ACTIVE) {
    throw new DomainException(SessionErrorCode.SESSION_NOT_FINISHED);
}
```

PENDING and ACTIVE sessions are rejected with `SESSION_NOT_FINISHED`. All four terminal statuses (COMPLETED, NO_SHOW, ABORTED, CANCELLED) are allowed through — the client receives whatever chunks were uploaded before termination (possibly empty lists for NO_SHOW or CANCELLED).

### Distance source — no re-aggregation

Distance is read directly from the session domain object:
```java
session.getTotalDistanceKm()
```
No polyline re-aggregation occurs in this query path. The value was written to `walk_session.total_distance_km` by `GamificationCommandService` at session completion time.

---

## 6. `compileJava` Output

```
> Task :backend:compileJava

BUILD SUCCESSFUL in 18s
1 actionable task: 1 executed
```

---

## 7. Gaps Closed

### Phase 4 gaps

| Gap | Description | Status |
|-----|-------------|--------|
| G-12 | `findPolylinesBySessionAndUser` in `TrackingChunkRepository` | **CLOSED** (Phase 1) |
| G-13 | `findCompletedByUserId` in `WalkSessionRepository` + JDBC impl | **CLOSED** |
| G-14 | `TrackingQueryService.getSessionRoute` | **CLOSED** |
| G-15 | `SessionHistoryQueryService.getSessionHistory` | **CLOSED** |
| G-16 | `SessionHistoryController` — history + route endpoints | **CLOSED** |

### Full gap recap — all 16 gaps

| Gap | Area | Status |
|-----|------|--------|
| G-1 | Add `user_id` to `session_point_chunks`, update unique constraint | **CLOSED** (Phase 0) |
| G-2 | Update `TrackingChunkRepository` interface and JDBC impl to scope by `userId` | **CLOSED** (Phase 1) |
| G-3 | Pass `callerId` to both chunk repo calls in `TrackingCommandService.syncRoutePoints` | **CLOSED** (Phase 1) |
| G-4 | Add `countChunks(sessionId, userId)` to repository | **CLOSED** (Phase 1) |
| G-5 | Fix `calculateTotalDistanceKm` with fallback-user selection | **CLOSED** (Phase 1) |
| G-6 | Extract `BadgeEvaluationService` from inline badge logic | **CLOSED** (Phase 2) |
| G-7 | Wire `BadgeEvaluationService` into `ReviewCommandService` | **CLOSED** (Phase 2) |
| G-8 | `UNIQUE(session_id, reporter_id)` constraint on `session_report` | **CLOSED** (Phase 0) |
| G-9 | `SessionReport` domain class + `SessionReportRepository` interface | **CLOSED** (Phase 3) |
| G-10 | `ReportCommandService.submitReport` with status-window validation | **CLOSED** (Phase 3) |
| G-11 | `ReportController` — `POST /api/v1/sessions/{sessionId}/report` | **CLOSED** (Phase 3) |
| G-12 | `findPolylinesBySessionAndUser` in `TrackingChunkRepository` | **CLOSED** (Phase 1) |
| G-13 | `findCompletedByUserId` in `WalkSessionRepository` | **CLOSED** (Phase 4) |
| G-14 | `TrackingQueryService.getSessionRoute` | **CLOSED** (Phase 4) |
| G-15 | `SessionHistoryQueryService.getSessionHistory` | **CLOSED** (Phase 4) |
| G-16 | `SessionHistoryController` | **CLOSED** (Phase 4) |

**All 16 gaps are now closed. No gaps remain open.**

---

## 8. Open Issues / Deviations

### `SESSION_NOT_FINISHED` error code placement

Added to `com.walkmate.domain.session.SessionErrorCode` (the existing enum for all session-lifecycle error codes). This was the correct choice because:
- `SESSION_NOT_FINISHED` is a session-state guard, semantically consistent with `SESSION_NOT_ACTIVE`, `SESSION_NOT_PENDING`, etc., which all live in the same enum.
- No separate error code class existed for tracking/history errors; creating one for a single entry would be disproportionate.
- The plan specified adding the code to "the appropriate error code class" — `SessionErrorCode` is that class.

### Controller package placement

`SessionHistoryController` was placed in `presentation/controller/session/` (same package as `SessionController`) rather than a separate `history/` sub-package. Both controllers share the `/api/v1/sessions` base path and operate on the same aggregate, so co-location is more coherent.

### No deviations from plan logic

All query constraints, status guards, distance sourcing, and response shapes match the Phase 4 specification exactly.
