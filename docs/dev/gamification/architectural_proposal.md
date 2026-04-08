# Architectural Proposal

## ACKG Findings Summary

| Area | Key Observation |
| --- | --- |
| TrackingCommandService.syncRoutePoints | Calls `chunkRepository.nextChunkIndex(sessionId)` with no `user_id` scope. Two concurrent uploads race on the same counter and collide on `UNIQUE(session_id, chunk_index)`. |
| TrackingChunkJdbcRepository.nextChunkIndex | Uses `SELECT MAX(chunk_index) + 1 ... WHERE session_id = :sessionId`. This is a global counter per session, not per user. |
| TrackingChunkJdbcRepository.findPolylinesBySessionId | Returns all polylines in a session (both users interleaved). `GamificationCommandService.calculateTotalDistanceKm()` sums all of them, causing 2x distance. |
| GamificationCommandService.rewardUser | Calls `BadgePolicy.evaluateEarned()` after session completion. This path is not invoked from review flow, which also changes trust score. |
| ReviewCommandService.submitReview | Applies `TrustScorePolicy` to the reviewee but does not evaluate badges, so `TRUSTED_WALKER` and `HIGHLY_TRUSTED` can lag by one session. |
| session_report table | Table exists in DB (`report_id`, `session_id`, `reporter_id`, `reported_user_id`, `reason`, `evidence_url`, `status`, `created_at`) with `report_status` enum (`OPEN`, `RESOLVED`, `DISMISSED`). Java layer is missing. |
| ReportCommandService | Does not exist. |
| session_point_chunks.user_id | Column does not exist, so per-user GPS path reconstruction is not possible. |
| WalkSessionJdbcRepository | Has `findActiveForUser`, `hasOverlappingActiveSession`, `findSessionsPastActivationWindow`, `findSessionsPastEndTime`, `selectAll`, but no `findByUserId` for history queries. |

## 1. GPS Chunk Race Condition and Double Distance

### Root Cause
The chunk index is a session-level counter shared by two users. Both users upload GPS concurrently, race on `MAX(chunk_index)`, and collide on the unique constraint. Distance calculation also sums every polyline in the session.

### Proposed Fix: Per-User Chunk Index
Add `user_id uuid NOT NULL` to `session_point_chunks` and change unique key scope.

```sql
-- Migration V107
ALTER TABLE session_point_chunks ADD COLUMN user_id uuid NOT NULL;
ALTER TABLE session_point_chunks DROP CONSTRAINT session_point_chunks_unique;
ALTER TABLE session_point_chunks ADD CONSTRAINT session_point_chunks_unique
    UNIQUE (session_id, user_id, chunk_index);
CREATE INDEX idx_chunks_user_order
    ON session_point_chunks (session_id, user_id, chunk_index ASC);
```

Repository interface changes (additive):
- `nextChunkIndex(sessionId, userId)` for per-user counter.
- `saveChunk(sessionId, userId, chunkIndex, polyline, timestamps, pointCount)` to persist ownership.
- `findPolylinesBySessionAndUser(sessionId, userId)` for single-user path retrieval.

Gamification distance fix:
- Update `calculateTotalDistanceKm()` to use one canonical user path (`user_id_a` by convention, or user with more chunks).
- Both users still receive the same points because the walk route is shared.

Impact:
- `TrackingCommandService.syncRoutePoints` passes `callerId` downstream.
- `GamificationCommandService.calculateTotalDistanceKm` calls `findPolylinesBySessionAndUser(sessionId, session.getUserIdA())`.
- Keep `findPolylinesBySessionId` for legacy callers.

## 2. Badge Schema Design

### Current State
`user_badge(user_id, badge_name, awarded_at)` is already a correct many-to-many join to the Java `Badge` enum. `ON CONFLICT (user_id, badge_name) DO NOTHING` makes writes idempotent.

### Actual Problem
Two flows change trust score, but only one evaluates badges:

```text
onSessionCompleted -> rewardUser -> applySessionReward -> evaluateEarned  (yes)
submitReview       -> applyTrustScore                 -> evaluateEarned  (no)
```

### Proposed Fix
Extract a shared `BadgeEvaluationService`.

```java
// application/gamification/BadgeEvaluationService.java
@Service
public class BadgeEvaluationService {
    public void evaluateAndAward(User user) { ... }
}
```

Integration:
- `GamificationCommandService.rewardUser()` calls it (replacing inline badge logic).
- `ReviewCommandService.submitReview()` calls it after `reviewee.applyTrustScore(newScore)`.

No schema migration is required for this objective.

## 3. Report Flow

### Current State
DB schema is complete. Java implementation is absent.

### Proposed Architecture (Minimal)
Domain:
- `domain/report/SessionReport.java` (plain value object)
- `domain/report/SessionReportRepository.java`

Application:
- `application/report/ReportCommandService.java`
- `submitReport(sessionId, reporterId, reason, evidenceUrl) -> SessionReport`

Validation by session status:
- `ACTIVE`: allow report. Caller may separately call `abortSession`.
- `COMPLETED`: allow only within 72 hours from `endedAt`.
- `NO_SHOW`: allow unconditionally.
- `PENDING` or other terminal states: reject.

No new table is needed. Add one constraint for duplicate control:

```sql
-- Migration V107 (same file as chunk fix)
ALTER TABLE session_report
    ADD CONSTRAINT session_report_unique UNIQUE (session_id, reporter_id);
```

Presentation:
- `presentation/controller/ReportController.java`
- `POST /api/sessions/{sessionId}/report`

## 4. History Flow: Dual GPS Path Rendering

### Requirement
Given a `sessionId`, return two ordered polyline streams (one per participant) for map rendering.

After objective 1 (`user_id` on chunks):

New repository method:

```java
Map<String, List<String>> findPolylinesBySessionGroupedByUser(String sessionId);
```

Expected return:

```text
{
  "userIdA": [poly1, poly2, ...],
  "userIdB": [poly1, ...]
}
```

SQL pattern:

```sql
SELECT user_id::text, polyline
FROM session_point_chunks
WHERE session_id = :sessionId
ORDER BY user_id, chunk_index ASC;
```

New query service:

```java
// application/tracking/TrackingQueryService.java
SessionRouteResponse getSessionRoute(String sessionId, String callerId);
```

Response shape:

```java
record SessionRouteResponse(
    String sessionId,
    List<String> userAPolylines,
    List<String> userBPolylines,
    double totalDistanceKm,
    int durationMinutes
) {}
```

Flow:
- Load session.
- Verify `callerId` is a participant.
- Verify status is `COMPLETED` or `ABORTED`.
- Fetch all chunk rows for session once, ordered by `(user_id, chunk_index)`, then partition in Java.
- Return `total_distance_km` directly from `walk_session`.

## Implementation Order

| # | Work Item | Migration? |
| --- | --- | --- |
| 1 | V107: add `user_id` to chunks, update unique constraint, add report unique constraint | Yes |
| 2 | Update `TrackingChunkRepository` and `TrackingChunkJdbcRepository` for per-user methods | No |
| 3 | Update `TrackingCommandService.syncRoutePoints` to pass `callerId` | No |
| 4 | Fix `GamificationCommandService.calculateTotalDistanceKm` to use `user_id_a` path | No |
| 5 | Extract `BadgeEvaluationService` and wire into `ReviewCommandService` | No |
| 6 | Add `SessionReport` domain, `ReportCommandService`, and `ReportController` | No |
| 7 | Add `TrackingQueryService.getSessionRoute` and history endpoint | No |

## Conclusion
All four objectives can be delivered with one migration file and minimal changes to existing constraints (mainly chunk unique key scope). Badge consistency fix needs no schema migration.
