# Phase 3 Completion Report

**Date:** 2026-04-07  
**Branch:** `implement/realtime`  
**Steps covered:** 3.1 (penalty events for NO_SHOW and ABORTED), 3.2 (listActiveIntents includes MATCHING)

---

## Files Modified / Created

```
backend/src/main/java/com/walkmate/application/gamification/SessionNoShowEvent.java   (new)
backend/src/main/java/com/walkmate/application/gamification/SessionAbortedEvent.java  (new)
backend/src/main/java/com/walkmate/application/gamification/GamificationCommandService.java (modified)
backend/src/main/java/com/walkmate/application/session/SessionCommandService.java      (modified)
backend/src/main/java/com/walkmate/application/walkintent/WalkIntentQueryService.java  (modified — Javadoc only)
backend/src/main/java/com/walkmate/infrastructure/repository/walkintent/WalkIntentJdbcRepository.java (modified)
docs/dev/main-flow-v2/implementation_plan.md                                            (checklists updated)
```

---

## Verification Results (Manual)

All five pre-implementation checks performed against the live codebase.

### 1. `SessionCommandService.handleExpiredSessions()` — no penalty event published
**File:** `application/session/SessionCommandService.java`  
**Before:** NO_SHOW branch called `session.markNoShow()` and saved. No `publishEvent()` call in either the NO_SHOW or CANCELLED path.  
**Verdict: GAP-15 confirmed for handleExpiredSessions(). Proceed.**

### 2. `SessionCommandService.abortSession()` — no penalty event published
**File:** `application/session/SessionCommandService.java`  
**Before:** Called `session.abort()`, saved, registered chat room close hook. No `publishEvent()`.  
**Verdict: GAP-15 confirmed for abortSession(). Proceed.**

### 3. `SessionNoShowEvent` / `SessionAbortedEvent` — neither class exists
**Search result:** Zero matches across the entire `backend/` tree.  
**Verdict: Confirmed absent. Proceed.**

### 4. `GamificationCommandService` — existing event pattern observed
**File:** `application/gamification/GamificationCommandService.java`  
**Pattern confirmed:**
- `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` + `@Transactional(propagation = Propagation.REQUIRES_NEW)` on `onSessionCompleted()`
- `SessionOutcome.NO_SHOW(-20)` and `SessionOutcome.ABORTED(-10)` already defined in `domain/review/SessionOutcome.java`
- `TrustScorePolicy.apply(currentScore, outcome)` already bounds the score to [0, 1000]
- `User.applyTrustScore(int)` already exists as the write path for trust score

### 5. `WalkIntentQueryService.listActiveIntents()` — single-status query confirmed
**File:** `application/walkintent/WalkIntentQueryService.java`  
**Before:** Delegates to `walkIntentRepository.findOpenByUserId(userId)`.  
**File:** `infrastructure/repository/walkintent/WalkIntentJdbcRepository.java` line 175  
**Before:** `AND status = 'OPEN'` — single-status filter.  
**Verdict: GAP-16 confirmed. Proceed.**

---

## Step 3.1 — Penalty Events

### Event classes created

**`SessionNoShowEvent`** (`application/gamification/SessionNoShowEvent.java`):
- Fields: `String sessionId`, `String penalizedUserId`
- Mirrors `SessionCompletedEvent` structure (plain class, no framework dependencies)

**`SessionAbortedEvent`** (`application/gamification/SessionAbortedEvent.java`):
- Fields: `String sessionId`, `String abortingUserId`
- Same structure

### `SessionCommandService` publish sites

**`abortSession()`** — publish after `logStateChange()`:
```java
eventPublisher.publishEvent(new SessionAbortedEvent(session.getSessionId(), callerId));
```

**`handleExpiredSessions()`** — NO_SHOW branch: capture penalized user ID, publish after save:
```java
String noShowUserId = null;
if (!aArrived && !bArrived) {
    session.cancel(...);
} else {
    session.markNoShow();
    noShowUserId = (session.getUserAActivatedAt() == null)
            ? session.getUserIdA() : session.getUserIdB();
}
sessionRepository.save(session);
// ...
if (noShowUserId != null) {
    eventPublisher.publishEvent(new SessionNoShowEvent(session.getSessionId(), noShowUserId));
}
```

Note: the local variable pattern avoids a second conditional check and keeps the publish after the save, matching the `completeSession()` flow.

### `GamificationCommandService` handlers added

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onSessionNoShow(SessionNoShowEvent event) { ... }

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onSessionAborted(SessionAbortedEvent event) { ... }

private void applyPenalty(String userId, SessionOutcome outcome) {
    // loads user, calls TrustScorePolicy.apply(), calls user.applyTrustScore(), saves
}
```

**Penalty deltas (from pre-existing `SessionOutcome` enum):**
- `NO_SHOW`: −20 points, bounded to [0, 1000]
- `ABORTED`: −10 points, bounded to [0, 1000]

Failure in either handler is caught and logged; it never rolls back the already-committed session record.

---

## Step 3.2 — listActiveIntents Includes MATCHING

**`WalkIntentJdbcRepository.findOpenByUserId()`** — SQL change:
```sql
-- Before
WHERE user_id = :userId
  AND status = 'OPEN'

-- After
WHERE user_id = :userId
  AND status IN ('OPEN', 'MATCHING')
```

**`WalkIntentQueryService.listActiveIntents()`** — Javadoc updated from "Returns all OPEN intents" to "Returns all OPEN or MATCHING intents". No logic change.

`WalkIntentResponse` already includes a `status` field, so MATCHING intents surface automatically with no DTO changes.

---

## Deviations from Plan

### 1. Penalty publish placed after save in handleExpiredSessions()
**Plan said:** Code snippet showed publish inline in the else branch, before the save.  
**Actual:** Captured `noShowUserId` in a local variable (set in the else branch), then published after `sessionRepository.save()` and `logStateChange()`.  
**Reason:** Matches the `completeSession()` pattern where the event is published after the save. Also avoids duplicating a conditional just to re-identify the penalized user after the save block.

### 2. `applyPenalty()` private helper extracted
**Plan said:** Add handlers to `GamificationCommandService`.  
**Actual:** Extracted a `private void applyPenalty(String userId, SessionOutcome outcome)` helper shared by both handlers, rather than duplicating the load-compute-save block twice.  
**Reason:** Avoids code duplication; both handlers differ only in outcome and event type.

---

## Open Issues / Blockers

None for Phase 3. All P3 gaps are now closed.

---

## Final Implementation Sign-Off

### All Gaps Closed

| Gap | Status |
|---|---|
| GAP-1: MATCHING missing from IntentStatus enum | CLOSED (Phase 0) |
| GAP-2: Proposal creation doesn't lock intents to MATCHING | CLOSED (Phase 0) |
| GAP-3: Proposal rejection doesn't restore intents to OPEN | CLOSED (Phase 0) |
| GAP-4: MatchProposal has no version field | CLOSED (Phase 0) |
| GAP-5: Proposal TTL = 30 min vs. 5 min | CLOSED (Phase 0) |
| GAP-6: Activation window 15/30 min vs. 10/15 min | CLOSED (Phase 1) |
| GAP-7: P-3 checks OPEN instead of MATCHING | CLOSED (Phase 0) |
| GAP-8: No MongoDB chat room on session creation | CLOSED (Phase 2A) |
| GAP-9: No complete session endpoint | CLOSED (Phase 1) |
| GAP-10: is_private/invited_friend_id/description not in code | CLOSED (Phase 1) |
| GAP-11: No per-intent exclude list on rejection | CLOSED (Phase 2B) |
| GAP-12: Overlap check uses OPEN+CONSUMED instead of OPEN+MATCHING | CLOSED (Phase 1) |
| GAP-13: Intent expiry doesn't cascade to proposals | CLOSED (Phase 2B) |
| GAP-14: No auto-expire intent at T−5 min | CLOSED (Phase 2B) |
| GAP-15: NO_SHOW/ABORTED emit no penalty event | CLOSED (Phase 3) |
| GAP-16: listActiveIntents excludes MATCHING | CLOSED (Phase 3) |
| GAP-17: No MongoDB Atlas dependency, config, or infrastructure | CLOSED (Phase 2A) |
| GAP-18: NotificationPublisherImpl is single-channel (DB only) | CLOSED (Phase 2B) |
| GAP-19: PushNotificationProvider has no generic sendPush() method | CLOSED (Phase 2B) |

**All 19 gaps: CLOSED.**

---

### implementation_plan.md Checklist Status

All checklist items across all phases are marked complete (`[x]`), with one pre-existing exception:

| Item | Status | Reason |
|---|---|---|
| Step 1.5: `WalkIntentCommandService.createIntent()` calls both overlap checks | Unchecked | Pre-existing gap explicitly noted in plan as "not in Phase 1 scope" — session overlap check is not called from intent creation. Not a regression introduced by this implementation. |

All other checklist items: `[x]`.

---

### Regression Risk Areas

| Area | Change | Risk notes |
|---|---|---|
| `SessionCommandService.abortSession()` | `SessionAbortedEvent` published after save | Low — event fires AFTER_COMMIT in a REQUIRES_NEW tx; any gamification failure is caught and logged |
| `SessionCommandService.handleExpiredSessions()` | `SessionNoShowEvent` published after save, only in the NO_SHOW branch | Low — guarded by `noShowUserId != null`; event fires AFTER_COMMIT; existing cancel path unchanged |
| `GamificationCommandService` | Two new `@TransactionalEventListener` handlers + `applyPenalty()` helper | Low — mirrors exact pattern of `onSessionCompleted()`; failures are caught and swallowed |
| `WalkIntentJdbcRepository.findOpenByUserId()` | SQL changed from `= 'OPEN'` to `IN ('OPEN', 'MATCHING')` | Low — additive change; MATCHING intents now visible to the owner in their active list; no other callers of `findOpenByUserId()` |
