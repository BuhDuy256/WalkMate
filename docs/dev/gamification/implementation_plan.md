# Implementation Plan: Session, Gamification, Report & History Flows

**Date:** 2026-04-08
**Branch:** `implement/realtime`
**Gap source:** `docs/dev/gamification/gap_analyze.md`

---

## Guiding Principles

1. **One migration file.** All schema changes ship in a single `V107__tracking_report_schema.sql` to keep Flyway history clean and allow a single rollback point.
2. **Gaps close in dependency order.** Infrastructure (schema + repository) is always done before application layer; application layer before presentation layer.
3. **No phase breaks existing tests.** Each phase is a net-additive or isolated replacement — it must not break any previously passing code path.
4. **Verification before moving on.** Each phase ends with a checklist of manual verification steps. Do not start the next phase until all checks pass.

---

## Phase 0 — Schema Migration

**Closes:** G-1, G-8

### Files to create/modify

```
backend/src/main/resources/db/migration/V107__tracking_report_schema.sql  (new)
```

### Step 0.1 — Add `user_id` to `session_point_chunks`

```sql
-- Step 1: add the column. Use a safe default for existing rows (NULL temporarily).
ALTER TABLE public.session_point_chunks
    ADD COLUMN user_id uuid;

-- Step 2: If the DB is empty / dev-only, backfill is not required and we move directly to NOT NULL.
--         Document here that prod backfill must be done before the NOT NULL constraint is applied.
--         For the dev branch, enforce immediately:
ALTER TABLE public.session_point_chunks
    ALTER COLUMN user_id SET NOT NULL;

-- Step 3: Drop old unique constraint.
ALTER TABLE public.session_point_chunks
    DROP CONSTRAINT session_point_chunks_unique;

-- Step 4: Add per-user unique constraint.
ALTER TABLE public.session_point_chunks
    ADD CONSTRAINT session_point_chunks_unique
        UNIQUE (session_id, user_id, chunk_index);

-- Step 5: Replace covering index with user-scoped variant.
DROP INDEX IF EXISTS public.idx_chunks_session_order;
CREATE INDEX idx_chunks_session_user_order
    ON public.session_point_chunks (session_id, user_id, chunk_index ASC);
```

### Step 0.2 — Add unique constraint to `session_report`

```sql
-- Mirrors the walk_review pattern: one report per (session, reporter).
ALTER TABLE public.session_report
    ADD CONSTRAINT session_report_unique UNIQUE (session_id, reporter_id);
```

### Verification checklist

- [ ] `./gradlew :backend:flywayMigrate` completes with `BUILD SUCCESSFUL`
- [ ] `\d session_point_chunks` in psql shows `user_id uuid NOT NULL` and new UNIQUE/INDEX
- [ ] `\d session_report` shows `session_report_unique` constraint

---

## Phase 1 — GPS Chunk Repository & Service Layer

**Closes:** G-2, G-3, G-4, G-5

**Depends on:** Phase 0

### Files to modify

```
backend/src/main/java/com/walkmate/domain/tracking/TrackingChunkRepository.java
backend/src/main/java/com/walkmate/infrastructure/repository/tracking/TrackingChunkJdbcRepository.java
backend/src/main/java/com/walkmate/application/tracking/TrackingCommandService.java
backend/src/main/java/com/walkmate/application/gamification/GamificationCommandService.java
```

### Step 1.1 — Update `TrackingChunkRepository` interface (G-2, G-4)

Add three methods; keep `findPolylinesBySessionId` unchanged (still needed for legacy reads):

```java
/** Scoped per-user counter. Returns 0 when user has no chunks yet for this session. */
int nextChunkIndex(String sessionId, String userId);

/** Persists a chunk row tagged to the uploading user. */
void saveChunk(String sessionId, String userId, int chunkIndex,
               String polyline, byte[] timestamps, int pointCount);

/** Returns polylines for one participant only, ordered by chunk_index ASC. */
List<String> findPolylinesBySessionAndUser(String sessionId, String userId);

/** Returns the number of chunks a specific user has uploaded for a session. */
int countChunks(String sessionId, String userId);
```

Keep the old `nextChunkIndex(String sessionId)` and `saveChunk(String sessionId, int chunkIndex, ...)` signatures in the interface only if there are other callers. ACKG search confirmed no other callers — remove the old signatures from the interface to avoid confusion. The JDBC implementation will provide only the new signatures.

### Step 1.2 — Update `TrackingChunkJdbcRepository` (G-2, G-4)

```java
// nextChunkIndex — per-user
@Override
public int nextChunkIndex(String sessionId, String userId) {
    Integer next = jdbcClient.sql("""
            SELECT COALESCE(MAX(chunk_index) + 1, 0)
            FROM session_point_chunks
            WHERE session_id = :sessionId AND user_id = :userId
            """)
            .param("sessionId", UUID.fromString(sessionId))
            .param("userId",    UUID.fromString(userId))
            .query(Integer.class).single();
    return next != null ? next : 0;
}

// saveChunk — includes user_id
@Override
public void saveChunk(String sessionId, String userId, int chunkIndex,
                      String polyline, byte[] timestamps, int pointCount) {
    jdbcClient.sql("""
            INSERT INTO session_point_chunks
                (session_id, user_id, chunk_index, polyline, timestamps, point_count)
            VALUES
                (:sessionId, :userId, :chunkIndex, :polyline, :timestamps, :pointCount)
            """)
            .param("sessionId",  UUID.fromString(sessionId))
            .param("userId",     UUID.fromString(userId))
            .param("chunkIndex", chunkIndex)
            .param("polyline",   polyline)
            .param("timestamps", timestamps)
            .param("pointCount", pointCount)
            .update();
}

// findPolylinesBySessionAndUser
@Override
public List<String> findPolylinesBySessionAndUser(String sessionId, String userId) {
    return jdbcClient.sql("""
            SELECT polyline FROM session_point_chunks
            WHERE session_id = :sessionId AND user_id = :userId
            ORDER BY chunk_index ASC
            """)
            .param("sessionId", UUID.fromString(sessionId))
            .param("userId",    UUID.fromString(userId))
            .query(String.class).list();
}

// countChunks
@Override
public int countChunks(String sessionId, String userId) {
    return jdbcClient.sql("""
            SELECT COUNT(*) FROM session_point_chunks
            WHERE session_id = :sessionId AND user_id = :userId
            """)
            .param("sessionId", UUID.fromString(sessionId))
            .param("userId",    UUID.fromString(userId))
            .query(Integer.class).single();
}
```

Remove the old `nextChunkIndex(String sessionId)` and `saveChunk(..., int chunkIndex, ...)` methods.

### Step 1.3 — Update `TrackingCommandService.syncRoutePoints` (G-3)

Two lines change — `callerId` is already in scope:

```java
// Before
int chunkIndex = chunkRepository.nextChunkIndex(sessionId);
chunkRepository.saveChunk(sessionId, chunkIndex, polyline, timestampBytes, points.size());

// After
int chunkIndex = chunkRepository.nextChunkIndex(sessionId, callerId);
chunkRepository.saveChunk(sessionId, callerId, chunkIndex, polyline, timestampBytes, points.size());
```

### Step 1.4 — Fix `GamificationCommandService.calculateTotalDistanceKm` (G-5)

Change signature from `(String sessionId)` to `(WalkSession session)` and implement fallback-user selection:

```java
private double calculateTotalDistanceKm(WalkSession session) {
    String idA = session.getUserIdA();
    String idB = session.getUserIdB();
    String sid = session.getSessionId();

    int countA = trackingChunkRepository.countChunks(sid, idA);
    int countB = trackingChunkRepository.countChunks(sid, idB);
    // Use the user with more chunks (better GPS coverage). Tiebreak: user_id_a.
    String canonicalUserId = (countB > countA) ? idB : idA;

    List<String> polylines = trackingChunkRepository.findPolylinesBySessionAndUser(sid, canonicalUserId);
    if (polylines.isEmpty()) return 0.0;
    return polylines.stream().mapToDouble(PolylineDecoder::calculateDistanceKm).sum();
}
```

Update call site in `rewardBothParticipants`:
```java
// Before
double distanceKm = calculateTotalDistanceKm(session.getSessionId());

// After
double distanceKm = calculateTotalDistanceKm(session);
```

### Verification checklist

- [ ] `./gradlew :backend:compileJava` — `BUILD SUCCESSFUL`
- [ ] Manual: two simultaneous `syncRoutePoints` calls from different users on the same ACTIVE session — both succeed, each user gets independent `chunk_index` starting at 0.
- [ ] Manual: `SELECT user_id, chunk_index FROM session_point_chunks WHERE session_id = '<id>' ORDER BY user_id, chunk_index` shows two separate sequences.
- [ ] Manual: session completion — `total_distance_km` on the session row reflects a single user's path distance, not the sum of both.

---

## Phase 2 — Badge Evaluation Service

**Closes:** G-6, G-7

**Depends on:** Phase 0 (no schema dependency; can run in parallel with Phase 1 logically, but keep sequential for safety)

### Files to create/modify

```
backend/src/main/java/com/walkmate/application/gamification/BadgeEvaluationService.java  (new)
backend/src/main/java/com/walkmate/application/gamification/GamificationCommandService.java  (modified)
backend/src/main/java/com/walkmate/application/review/ReviewCommandService.java              (modified)
```

### Step 2.1 — Create `BadgeEvaluationService` (G-6)

```java
package com.walkmate.application.gamification;

import com.walkmate.domain.gamification.Badge;
import com.walkmate.domain.gamification.BadgePolicy;
import com.walkmate.domain.gamification.UserBadgeRepository;
import com.walkmate.domain.gamification.UserStats;
import com.walkmate.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Evaluates and awards badges for a user whose stats have just changed.
 *
 * <p>Safe to call from any flow that modifies user stats (session completion,
 * review submission, etc.). Idempotent — the DB constraint
 * {@code UNIQUE(user_id, badge_name)} and the {@code ON CONFLICT DO NOTHING}
 * clause on {@link UserBadgeRepository#saveAll} guarantee no duplicate awards.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeEvaluationService {

    private final UserBadgeRepository badgeRepository;

    /**
     * Evaluates which badges {@code user} has newly earned and persists them.
     *
     * @param user the user whose stats have already been updated and saved
     */
    public void evaluateAndAward(User user) {
        UserStats   stats     = new UserStats(
                user.getUserId(),
                user.getCompletedSessions(),
                user.getTotalDistanceKm(),
                user.getTotalPoints(),
                user.getTrustScore()
        );
        Set<String>  existing  = badgeRepository.findBadgeNamesByUserId(user.getUserId());
        List<Badge>  newBadges = BadgePolicy.evaluateEarned(stats, existing);

        if (!newBadges.isEmpty()) {
            badgeRepository.saveAll(user.getUserId(), newBadges);
            log.info("BadgeEvaluation: awarded {} badge(s) to user {}: {}",
                    newBadges.size(), user.getUserId(), newBadges);
        }
    }
}
```

### Step 2.2 — Refactor `GamificationCommandService.rewardUser` (G-6)

Inject `BadgeEvaluationService`. Replace inline badge logic with a delegation call:

```java
// Before (in rewardUser)
UserStats stats = new UserStats(...);
Set<String>  existingBadges = badgeRepository.findBadgeNamesByUserId(userId);
List<Badge>  newBadges      = BadgePolicy.evaluateEarned(stats, existingBadges);
if (!newBadges.isEmpty()) {
    badgeRepository.saveAll(userId, newBadges);
    log.info(...);
}

// After
badgeEvaluationService.evaluateAndAward(user);
```

Remove `UserBadgeRepository` injection from `GamificationCommandService` (it is now owned by `BadgeEvaluationService`).

### Step 2.3 — Wire `BadgeEvaluationService` into `ReviewCommandService` (G-7)

```java
// Inject
private final BadgeEvaluationService badgeEvaluationService;

// In submitReview(), after saving reviewee's new trust score:
reviewee.applyTrustScore(newScore);
userRepository.save(reviewee);
badgeEvaluationService.evaluateAndAward(reviewee);   // ← add this line
```

### Verification checklist

- [ ] `./gradlew :backend:compileJava` — `BUILD SUCCESSFUL`
- [ ] Manual: submit a review that pushes reviewee's trust score above 100 for the first time → `user_badge` table gains `TRUSTED_WALKER` row immediately, without waiting for another session.
- [ ] Manual: call `evaluateAndAward` twice for the same user in the same session → no duplicate badge rows (idempotency via `ON CONFLICT DO NOTHING`).

---

## Phase 3 — Report Flow

**Closes:** G-8 (schema, done in Phase 0), G-9, G-10, G-11

**Depends on:** Phase 0 for the unique constraint; no dependency on Phases 1–2.

### Files to create

```
backend/src/main/java/com/walkmate/domain/report/SessionReport.java              (new)
backend/src/main/java/com/walkmate/domain/report/SessionReportRepository.java    (new)
backend/src/main/java/com/walkmate/domain/report/ReportErrorCode.java            (new)
backend/src/main/java/com/walkmate/infrastructure/repository/report/SessionReportJdbcRepository.java  (new)
backend/src/main/java/com/walkmate/application/report/ReportCommandService.java   (new)
backend/src/main/java/com/walkmate/presentation/controller/ReportController.java  (new)
backend/src/main/java/com/walkmate/presentation/dto/request/report/SubmitReportRequest.java (new)
```

### Step 3.1 — Domain layer (G-9)

`SessionReport.java` — plain value object, no state machine (moderation is handled externally):
```java
// Fields: reportId, sessionId, reporterId, reportedUserId, reason, evidenceUrl, createdAt
// Static factory: SessionReport.create(sessionId, reporterId, reportedUserId, reason, evidenceUrl)
```

`SessionReportRepository.java`:
```java
void save(SessionReport report);
boolean existsBySessionAndReporter(String sessionId, String reporterId);
```

`ReportErrorCode.java` — mirrors existing ErrorCode pattern:
```java
REPORT_SESSION_INVALID_STATUS,   // session in a state that does not allow reporting
REPORT_WINDOW_EXPIRED,           // post-session report submitted outside the allowed time window
REPORT_ALREADY_SUBMITTED,        // (session, reporter) pair already exists
REPORT_SELF_NOT_ALLOWED          // reporter == reportedUser
```

### Step 3.2 — Infrastructure layer (G-9)

`SessionReportJdbcRepository`:
```java
// save() — INSERT INTO session_report (report_id, session_id, reporter_id, reported_user_id, reason, evidence_url)
// existsBySessionAndReporter() — SELECT COUNT(*) > 0 WHERE session_id = :sid AND reporter_id = :rid
```

### Step 3.3 — Application layer (G-10)

`ReportCommandService.submitReport(sessionId, reporterId, reportedUserId, reason, evidenceUrl)`:

```
1. Load session; throw SESSION_NOT_FOUND if absent.
2. Verify reporterId is a participant; throw REVIEW_NOT_PARTICIPANT if not.
3. Verify reporterId ≠ reportedUserId; throw REPORT_SELF_NOT_ALLOWED if equal.
4. Switch on session.getStatus():
   - PENDING    → throw REPORT_SESSION_INVALID_STATUS
   - ACTIVE     → allow (no time check needed)
   - COMPLETED  → allow only if Instant.now() < session.getEndedAt() + 72h; else REPORT_WINDOW_EXPIRED
   - NO_SHOW    → always allow
   - ABORTED    → allow only if Instant.now() < session.getEndedAt() + 24h; else REPORT_WINDOW_EXPIRED
   - CANCELLED  → throw REPORT_SESSION_INVALID_STATUS
5. Guard duplicate: existsBySessionAndReporter → throw REPORT_ALREADY_SUBMITTED if true.
6. Persist SessionReport.create(...) and return it.
```

### Step 3.4 — Presentation layer (G-11)

```
POST /api/v1/sessions/{sessionId}/report
Authorization: Bearer <token>
Body: { "reportedUserId": "...", "reason": "...", "evidenceUrl": "..." (optional) }
Response 201: { "reportId": "...", "createdAt": "..." }
```

### Verification checklist

- [ ] `./gradlew :backend:compileJava` — `BUILD SUCCESSFUL`
- [ ] POST report on ACTIVE session → 201 Created, row in `session_report` table.
- [ ] POST report on COMPLETED session within 72h → 201 Created.
- [ ] POST report on COMPLETED session after 72h → 422 `REPORT_WINDOW_EXPIRED`.
- [ ] POST second report from same reporter on same session → 409 `REPORT_ALREADY_SUBMITTED`.
- [ ] POST report where reporter == reportedUser → 422 `REPORT_SELF_NOT_ALLOWED`.
- [ ] POST report on PENDING session → 422 `REPORT_SESSION_INVALID_STATUS`.

---

## Phase 4 — History Flow (Dual GPS Path Rendering)

**Closes:** G-12, G-13, G-14, G-15, G-16

**Depends on:** Phase 1 (requires `user_id` on chunks and per-user fetch methods)

### Files to create/modify

```
backend/src/main/java/com/walkmate/domain/tracking/TrackingChunkRepository.java    (already modified in P1)
backend/src/main/java/com/walkmate/infrastructure/repository/tracking/TrackingChunkJdbcRepository.java  (already modified in P1)
backend/src/main/java/com/walkmate/domain/session/WalkSessionRepository.java       (modified — add history query)
backend/src/main/java/com/walkmate/infrastructure/repository/session/WalkSessionJdbcRepository.java    (modified)
backend/src/main/java/com/walkmate/application/tracking/TrackingQueryService.java  (new)
backend/src/main/java/com/walkmate/application/session/SessionHistoryQueryService.java (new)
backend/src/main/java/com/walkmate/presentation/controller/SessionHistoryController.java (new)
backend/src/main/java/com/walkmate/presentation/dto/response/session/SessionRouteResponse.java (new)
backend/src/main/java/com/walkmate/presentation/dto/response/session/SessionSummaryResponse.java (new)
```

### Step 4.1 — Session history repository (G-13)

Add to `WalkSessionRepository`:
```java
/** Returns terminal sessions for a user, ordered by ended_at DESC. */
List<WalkSession> findCompletedByUserId(String userId);
```

`WalkSessionJdbcRepository` SQL:
```sql
SELECT <all columns> FROM walk_session
WHERE (user_id_a = :userId OR user_id_b = :userId)
  AND status IN ('COMPLETED', 'NO_SHOW', 'ABORTED', 'CANCELLED')
ORDER BY COALESCE(ended_at, created_at) DESC
LIMIT 50
```

Limit of 50 is an initial safeguard; pagination can be added as a follow-up.

### Step 4.2 — `TrackingQueryService.getSessionRoute` (G-14)

```java
@Transactional(readOnly = true)
public SessionRouteResponse getSessionRoute(String sessionId, String callerId) {
    WalkSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new DomainException(SessionErrorCode.SESSION_NOT_FOUND));

    // Caller must be a participant
    if (!callerId.equals(session.getUserIdA()) && !callerId.equals(session.getUserIdB())) {
        throw new DomainException(SessionErrorCode.SESSION_NOT_PARTICIPANT);
    }

    // Route data only available for terminal sessions
    if (session.getStatus() == SessionStatus.PENDING || session.getStatus() == SessionStatus.ACTIVE) {
        throw new DomainException(SessionErrorCode.SESSION_NOT_FINISHED);
    }

    List<String> pathA = chunkRepository.findPolylinesBySessionAndUser(sessionId, session.getUserIdA());
    List<String> pathB = chunkRepository.findPolylinesBySessionAndUser(sessionId, session.getUserIdB());

    int durationMinutes = (int) (session.getTotalDurationSeconds() / 60);

    return new SessionRouteResponse(
            sessionId,
            pathA,
            pathB,
            session.getTotalDistanceKm(),
            durationMinutes
    );
}
```

### Step 4.3 — `SessionHistoryQueryService.getSessionHistory` (G-15)

```java
@Transactional(readOnly = true)
public List<SessionSummaryResponse> getSessionHistory(String callerId) {
    List<WalkSession> sessions = sessionRepository.findCompletedByUserId(callerId);
    return sessions.stream()
            .map(s -> toSummary(s, callerId))
            .collect(Collectors.toList());
}

private SessionSummaryResponse toSummary(WalkSession s, String callerId) {
    String partnerId = callerId.equals(s.getUserIdA()) ? s.getUserIdB() : s.getUserIdA();
    // Partner display name can be enriched by a UserRepository lookup if needed
    return new SessionSummaryResponse(
            s.getSessionId(),
            s.getStatus().name(),
            partnerId,
            s.getScheduledStart().toString(),
            s.getScheduledEnd().toString(),
            s.getTotalDistanceKm(),
            (int) (s.getTotalDurationSeconds() / 60)
    );
}
```

### Step 4.4 — `SessionHistoryController` (G-16)

```
GET /api/sessions/history
Authorization: Bearer <token>
Response 200: [ SessionSummaryResponse, ... ]

GET /api/sessions/{sessionId}/route
Authorization: Bearer <token>
Response 200: SessionRouteResponse { sessionId, userAPolylines, userBPolylines, totalDistanceKm, durationMinutes }
```

### Verification checklist

- [ ] `./gradlew :backend:compileJava` — `BUILD SUCCESSFUL`
- [ ] `GET /api/sessions/history` returns a list for a user with completed sessions.
- [ ] `GET /api/sessions/{sessionId}/route` on a COMPLETED session returns two separate polyline lists.
- [ ] `GET /api/sessions/{sessionId}/route` called by a non-participant → 403/404.
- [ ] `GET /api/sessions/{sessionId}/route` on an ACTIVE session → 422 `SESSION_NOT_FINISHED`.
- [ ] When only one user uploaded GPS chunks (the other had GPS failure) → the user with 0 chunks returns an empty list, not an error.

---

## Execution Order & Dependency Graph

```
Phase 0 (migration V107)
    │
    ├──► Phase 1 (GPS chunk repo + service + gamification fix)
    │        │
    │        └──► Phase 4 (history flow — needs user_id on chunks)
    │
    ├──► Phase 2 (badge extraction — no schema dependency)
    │
    └──► Phase 3 (report flow — needs unique constraint from P0)
```

Phases 2 and 3 can be implemented in parallel after Phase 0 completes. Phase 4 must wait for Phase 1.

---

## File Change Summary

| Phase | File | Action |
|-------|------|--------|
| 0 | `V107__tracking_report_schema.sql` | New migration |
| 1 | `TrackingChunkRepository` | Add 4 methods, remove 2 old signatures |
| 1 | `TrackingChunkJdbcRepository` | Implement new methods, remove old |
| 1 | `TrackingCommandService` | Pass `callerId` to chunk repo calls |
| 1 | `GamificationCommandService` | Fix distance calc, inject `BadgeEvaluationService` |
| 2 | `BadgeEvaluationService` | New class |
| 2 | `GamificationCommandService` | Remove inline badge logic, delegate to service |
| 2 | `ReviewCommandService` | Inject + call `BadgeEvaluationService` |
| 3 | `SessionReport` | New domain class |
| 3 | `SessionReportRepository` | New domain interface |
| 3 | `ReportErrorCode` | New error code enum |
| 3 | `SessionReportJdbcRepository` | New infra class |
| 3 | `ReportCommandService` | New application service |
| 3 | `ReportController` | New controller |
| 3 | `SubmitReportRequest` | New DTO |
| 4 | `WalkSessionRepository` | Add `findCompletedByUserId` |
| 4 | `WalkSessionJdbcRepository` | Implement history query |
| 4 | `TrackingQueryService` | New query service |
| 4 | `SessionHistoryQueryService` | New query service |
| 4 | `SessionHistoryController` | New controller |
| 4 | `SessionRouteResponse` | New DTO |
| 4 | `SessionSummaryResponse` | New DTO |
