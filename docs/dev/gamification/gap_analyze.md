# Gap Analysis: Session, Gamification, Report & History Flows

**Date:** 2026-04-08
**Branch:** `implement/realtime`
**Scope:** GPS Tracking chunks, Badge evaluation, Report flow, History (dual-path) flow

---

## How to Read This Document

Each section follows the same structure:
- **Current State** — what the codebase does right now (verified against ACKG + live code).
- **Target State** — what it must do after the refactor.
- **Gap** — the delta between the two, labelled `G-N` for traceability in `implementation_plan.md`.

---

## 1. GPS Chunk Tracking

### 1.1 Race Condition on Chunk Index

#### Current State

`TrackingChunkJdbcRepository.nextChunkIndex(sessionId)`:
```sql
SELECT COALESCE(MAX(chunk_index) + 1, 0)
FROM session_point_chunks
WHERE session_id = :sessionId
```

The counter is **session-scoped** — shared between both participants. When User A and User B call `POST /tracking/sync` near-simultaneously:
1. Both execute `nextChunkIndex` and read the same `MAX` value (e.g. 4).
2. Both compute `chunkIndex = 5`.
3. Both call `saveChunk` with `chunk_index = 5`.
4. The second INSERT collides on `UNIQUE (session_id, chunk_index)` → one upload is lost or throws a constraint violation.

`session_point_chunks` schema (current):
```
chunk_id    uuid   PK
session_id  uuid   NOT NULL  FK → walk_session
chunk_index int    NOT NULL  CHECK >= 0
polyline    text   NOT NULL
timestamps  bytea
elevations  bytea
point_count int    NOT NULL
created_at  timestamp
UNIQUE (session_id, chunk_index)
```

No `user_id` column exists.

#### Target State

The chunk index must be **per-user** so that the two participants never compete for the same sequence number.

Schema change:
```
chunk_id    uuid   PK
session_id  uuid   NOT NULL  FK → walk_session
user_id     uuid   NOT NULL  FK → user_account
chunk_index int    NOT NULL  CHECK >= 0
...
UNIQUE (session_id, user_id, chunk_index)   -- replaces old constraint
INDEX  (session_id, user_id, chunk_index ASC) -- replaces old index
```

Repository contract change:
```java
// TrackingChunkRepository
int nextChunkIndex(String sessionId, String userId);                          // scoped counter
void saveChunk(String sessionId, String userId, int chunkIndex,              // owns the row
               String polyline, byte[] timestamps, int pointCount);
List<String> findPolylinesBySessionAndUser(String sessionId, String userId); // single-user path
```

`syncRoutePoints(sessionId, callerId, points)` already receives `callerId` — it passes it straight through to both repository calls.

**Gap G-1:** Add `user_id` column to `session_point_chunks`. Change unique constraint from `(session_id, chunk_index)` to `(session_id, user_id, chunk_index)`.

**Gap G-2:** Update `TrackingChunkRepository` interface and `TrackingChunkJdbcRepository` to scope `nextChunkIndex` and `saveChunk` by `userId`.

**Gap G-3:** Update `TrackingCommandService.syncRoutePoints` to pass `callerId` to both repository calls.

---

### 1.2 Double-Counted Distance in Gamification

#### Current State

`GamificationCommandService.calculateTotalDistanceKm(sessionId)`:
```java
List<String> polylines = trackingChunkRepository.findPolylinesBySessionId(sessionId);
return polylines.stream()
        .mapToDouble(PolylineDecoder::calculateDistanceKm)
        .sum();
```

`findPolylinesBySessionId` returns **all** chunks for the session regardless of uploader. Because both User A and User B independently upload their own GPS traces:
- The sum covers both full routes.
- A 3 km walk appears as 6 km.
- `total_distance_km` on the session row and both users' lifetime totals are inflated by 2×.
- Badge milestones (`FIRST_KM`, `TEN_KM_WALKER`, `FIFTY_KM_WALKER`) can be earned prematurely.

#### Target State

Calculate distance from the path of whichever participant submitted **more GPS chunks**, as that device is more likely to have had continuous GPS coverage during the walk. If both have equal chunk counts, prefer `user_id_a` as the tiebreaker.

```java
private double calculateTotalDistanceKm(WalkSession session) {
    int countA = chunkRepository.countChunks(session.getSessionId(), session.getUserIdA());
    int countB = chunkRepository.countChunks(session.getSessionId(), session.getUserIdB());
    String canonicalUserId = (countB > countA) ? session.getUserIdB() : session.getUserIdA();
    List<String> polylines = chunkRepository.findPolylinesBySessionAndUser(
            session.getSessionId(), canonicalUserId);
    return polylines.stream().mapToDouble(PolylineDecoder::calculateDistanceKm).sum();
}
```

New repository method required:
```java
int countChunks(String sessionId, String userId);
```

Note: `findPolylinesBySessionId(sessionId)` (the legacy no-user variant) is retained unchanged — it is still needed by the History flow to aggregate both paths for rendering.

**Gap G-4:** Add `countChunks(sessionId, userId)` to `TrackingChunkRepository` and `TrackingChunkJdbcRepository`.

**Gap G-5:** Change `GamificationCommandService.calculateTotalDistanceKm` signature to accept `WalkSession` (not just `sessionId`) and implement the fallback-user selection logic described above. Update the call site in `rewardBothParticipants`.

---

## 2. Badge Evaluation Logic

### 2.1 Trust Badges Awarded Late (or Never) After Reviews

#### Current State

`BadgePolicy.evaluateEarned()` is called only inside `GamificationCommandService.rewardUser()`, which fires exclusively from the `onSessionCompleted` event handler.

`ReviewCommandService.submitReview()` calls `TrustScorePolicy.apply()` and saves the updated `trustScore` to the user — but **never evaluates badges**. If a review pushes a user's trust score across the 100 or 500 threshold, `TRUSTED_WALKER` or `HIGHLY_TRUSTED` will not be awarded until that user completes their next session.

Call chain today:
```
onSessionCompleted → rewardBothParticipants → rewardUser → evaluateEarned ✓
submitReview       → applyTrustScore                     → [nothing]       ✗
```

#### Target State

Badge evaluation must run at every point where user stats change. A shared `BadgeEvaluationService` encapsulates the load-evaluate-save pattern and is called from both sites:

```
onSessionCompleted → rewardBothParticipants → rewardUser → BadgeEvaluationService.evaluateAndAward ✓
submitReview       → applyTrustScore        →             BadgeEvaluationService.evaluateAndAward ✓
```

`BadgeEvaluationService`:
```java
// application/gamification/BadgeEvaluationService.java
@Service
@RequiredArgsConstructor
public class BadgeEvaluationService {
    private final UserBadgeRepository badgeRepository;

    /**
     * Evaluates which badges the user has newly earned given their current stats
     * and persists any new ones. Idempotent — safe to call from any flow that
     * modifies user stats (session completion, review submission, etc.).
     *
     * @param user the user whose stats have just been updated and saved
     */
    public void evaluateAndAward(User user) {
        UserStats stats = UserStats.from(user);
        Set<String>  existing  = badgeRepository.findBadgeNamesByUserId(user.getUserId());
        List<Badge>  newBadges = BadgePolicy.evaluateEarned(stats, existing);
        if (!newBadges.isEmpty()) {
            badgeRepository.saveAll(user.getUserId(), newBadges);
        }
    }
}
```

No schema migration required. `user_badge` with `UNIQUE(user_id, badge_name)` and `ON CONFLICT DO NOTHING` already guarantees idempotency at the DB level.

**Gap G-6:** Create `BadgeEvaluationService` and extract the inline badge logic from `GamificationCommandService.rewardUser` into it.

**Gap G-7:** Inject `BadgeEvaluationService` into `ReviewCommandService` and call `evaluateAndAward(reviewee)` after `reviewee.applyTrustScore(newScore)`.

---

### 2.2 Unused `badge` Metadata Table

#### Current State

`V1__init.sql` defines a `badge` table (badge metadata for display purposes). No Java code references it. `UserBadgeJdbcRepository` stores and queries only `badge_name varchar`, sourced from the `Badge` Java enum.

The `trust_score` table also exists as a separate table but is not used by any Java code — trust score is stored directly on `user_account.trust_score`.

#### Target State

No action. Both tables are pre-existing schema artifacts noted in `problems.md` as "not a runtime bug." They are kept as-is to avoid a destructive migration. The `Badge` enum remains the single source of truth for badge definitions.

---

## 3. Report Flow

### 3.1 No Java Layer for `session_report`

#### Current State

`session_report` table exists in `V1__init.sql`:
```
report_id        uuid   PK
session_id       uuid   FK → walk_session (ON DELETE SET NULL)
reporter_id      uuid   FK → user_account
reported_user_id uuid   FK → user_account
reason           varchar NOT NULL
evidence_url     text
status           report_status  DEFAULT 'OPEN'  (OPEN | RESOLVED | DISMISSED)
created_at       timestamp
```

No Java domain class, no repository, no application service, and no controller exist for this table.

`abortSession()` in `SessionCommandService` handles ACTIVE→ABORTED transitions. Report and Abort are currently merged in user intent but are separate concerns in the data model.

#### Target State

A minimal Report flow that allows users to flag incidents. Report and Abort are decoupled: a user may report a partner without aborting the session, or abort and report simultaneously through two separate calls.

**Domain:**
```java
// domain/report/SessionReport.java  — immutable value object, no state machine
// domain/report/SessionReportRepository.java
//   void save(SessionReport report)
//   boolean existsBySessionAndReporter(String sessionId, String reporterId)
```

**Application — `ReportCommandService.submitReport`:**

Validation rules by session status at time of report:
- `ACTIVE` → allowed. The session is ongoing; the reporter may simultaneously call `abortSession` as a separate request.
- `COMPLETED` → allowed only if `now` is within 72 hours of `session.getEndedAt()`. After that window, reject with `REPORT_WINDOW_EXPIRED`.
- `NO_SHOW` → always allowed (supplementary context for moderation).
- `PENDING` → not allowed. No interaction has occurred yet.
- All terminal states (`CANCELLED`, `ABORTED`) → allowed within 24 hours of `session.getEndedAt()` for post-incident reports.
- Duplicate (same reporter, same session) → rejected (`REPORT_ALREADY_SUBMITTED`).

**Schema addition:**
```sql
-- Mirrors the walk_review uniqueness pattern
ALTER TABLE session_report
    ADD CONSTRAINT session_report_unique UNIQUE (session_id, reporter_id);
```

**Presentation:**
```
POST /api/v1/sessions/{sessionId}/report
Body: { reportedUserId, reason, evidenceUrl? }
```

**Gap G-8:** Add `UNIQUE(session_id, reporter_id)` constraint to `session_report` (migration).

**Gap G-9:** Create `SessionReport` domain class and `SessionReportRepository` interface.

**Gap G-10:** Create `ReportCommandService.submitReport` with the status-window validation rules above.

**Gap G-11:** Create `ReportController` — `POST /api/v1/sessions/{sessionId}/report`.

---

## 4. History Flow — Dual GPS Path Rendering

### 4.1 No Per-User Path Retrieval and No History Endpoint

#### Current State

`TrackingChunkRepository.findPolylinesBySessionId(sessionId)` returns all chunks for a session as a flat `List<String>`, with no ability to separate by uploader. Because `user_id` does not exist on the `session_point_chunks` table, it is physically impossible to reconstruct individual paths.

`WalkSessionJdbcRepository` has no `findByUserId`-style method for listing a user's session history. No history query service or controller exists.

#### Target State

**New repository methods (after G-1 adds `user_id`):**
```java
// TrackingChunkRepository — new additions
List<String> findPolylinesBySessionAndUser(String sessionId, String userId); // G-2 above
int          countChunks(String sessionId, String userId);                   // G-4 above
```

**New session history repository method:**
```java
// WalkSessionRepository
List<WalkSession> findCompletedByUserId(String userId); // ordered by ended_at DESC
```

**New `TrackingQueryService`:**
```java
// application/tracking/TrackingQueryService.java
public SessionRouteResponse getSessionRoute(String sessionId, String callerId) {
    // 1. Load session, verify callerId is participant, verify status is COMPLETED or ABORTED
    // 2. Fetch User A chunks and User B chunks separately (two indexed scans)
    // 3. Return both polyline lists + pre-computed stats from the session row
}
```

Response shape:
```java
record SessionRouteResponse(
    String       sessionId,
    List<String> userAPolylines,      // ordered by chunk_index ASC
    List<String> userBPolylines,      // ordered by chunk_index ASC
    double       totalDistanceKm,     // from walk_session.total_distance_km (no re-aggregation)
    int          durationMinutes      // derived from walk_session.total_duration_seconds
)
```

**New `SessionHistoryQueryService`:**
```java
// application/session/SessionHistoryQueryService.java
public List<SessionSummaryResponse> getSessionHistory(String callerId)
```

Response shape:
```java
record SessionSummaryResponse(
    String sessionId,
    String status,
    String partnerId,
    String partnerDisplayName,
    String scheduledStart,
    String scheduledEnd,
    double totalDistanceKm,
    int    durationMinutes
)
```

**Presentation:**
```
GET /api/sessions/history              → list of SessionSummaryResponse (for current user)
GET /api/sessions/{sessionId}/route   → SessionRouteResponse (dual path map data)
```

**Gap G-12:** Add `findPolylinesBySessionAndUser(sessionId, userId)` to `TrackingChunkRepository` and `TrackingChunkJdbcRepository`.

**Gap G-13:** Add `findCompletedByUserId(userId)` to `WalkSessionRepository` and `WalkSessionJdbcRepository`.

**Gap G-14:** Create `TrackingQueryService.getSessionRoute`.

**Gap G-15:** Create `SessionHistoryQueryService.getSessionHistory`.

**Gap G-16:** Create `SessionHistoryController` — `GET /api/sessions/history` and `GET /api/sessions/{sessionId}/route`.

---

## Gap Summary Table

| Gap | Area | Kind | Priority |
|-----|------|------|----------|
| G-1 | GPS Chunk | Schema migration | HIGH |
| G-2 | GPS Chunk | Repository contract | HIGH |
| G-3 | GPS Chunk | Application service | HIGH |
| G-4 | GPS Chunk / Gamification | Repository method | HIGH |
| G-5 | Gamification | Distance calculation logic | HIGH |
| G-6 | Badge | Extract BadgeEvaluationService | MEDIUM |
| G-7 | Badge | Wire into ReviewCommandService | MEDIUM |
| G-8 | Report | Schema migration | MEDIUM |
| G-9 | Report | Domain + Repository | MEDIUM |
| G-10 | Report | Application service | MEDIUM |
| G-11 | Report | Controller | MEDIUM |
| G-12 | History | Repository method | LOW |
| G-13 | History | Session history repository | LOW |
| G-14 | History | TrackingQueryService | LOW |
| G-15 | History | SessionHistoryQueryService | LOW |
| G-16 | History | Controller | LOW |
