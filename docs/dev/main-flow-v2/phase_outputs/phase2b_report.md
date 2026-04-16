# Phase 2B Completion Report

**Date:** 2026-04-07  
**Branch:** `implement/realtime`  
**Steps covered:** 2.4 (exclude list), 2.5 (intent expiry scheduler), 2.6a–2.6d (FCM dual-channel)

---

## Files Modified / Created

```
backend/src/main/resources/db/migration/V106__add_intent_exclude_list.sql          (new)
backend/src/main/java/com/walkmate/domain/walkintent/WalkIntent.java               (modified)
backend/src/main/java/com/walkmate/domain/walkintent/WalkIntentErrorCode.java      (modified)
backend/src/main/java/com/walkmate/domain/walkintent/WalkIntentRepository.java     (modified)
backend/src/main/java/com/walkmate/infrastructure/repository/walkintent/WalkIntentJdbcRepository.java  (modified)
backend/src/main/java/com/walkmate/application/proposal/MatchingCommandService.java (modified)
backend/src/main/java/com/walkmate/application/walkintent/IntentScheduler.java     (new)
backend/src/main/java/com/walkmate/application/notification/PushNotificationProvider.java  (modified)
backend/src/main/java/com/walkmate/infrastructure/notification/FcmNotificationProvider.java (modified)
backend/src/main/java/com/walkmate/infrastructure/notification/NotificationPublisherImpl.java (modified)
docs/dev/main-flow-v2/implementation_plan.md                                         (checklists updated)
```

---

## Verification Results (Manual)

All six pre-implementation checks were performed against the live codebase.

### 1. `PushNotificationProvider` — sendPush() absent
**File:** `application/notification/PushNotificationProvider.java`  
**Before:** Only `sendMatchFound(String, String, String)` declared. No `sendPush()`.  
**Verdict: GAP-19 confirmed. Proceed.**

### 2. `NotificationPublisherImpl` — FCM dispatch absent
**File:** `infrastructure/notification/NotificationPublisherImpl.java`  
**Before:** Single-channel; only `notificationRepository.save(notification)`. No FCM call.  
**Verdict: GAP-18 confirmed. Proceed.**

### 3. `MatchingCommandService` — manual sendMatchFound() present
**File:** `application/proposal/MatchingCommandService.java`  
**Before:** Manual `userRepository.findById()` → `pushNotificationProvider.sendMatchFound()` block present at lines 141–151.  
**Verdict: Redundancy confirmed. Will be removed in 2.6d.**

### 4. `SessionScheduler` — pattern observed
**File:** `application/session/SessionScheduler.java`  
**Pattern:** `@Scheduled(fixedDelay = 60_000)`, thin component, try-catch per delegated call, `@Slf4j`, `@RequiredArgsConstructor`. `IntentScheduler` follows this pattern exactly.

### 5. `WalkIntentJdbcRepository` — new query methods absent
**Before:** No `findIntentsExpiringSoon()` or `findOverdueOpenIntents()` methods.  
**Verdict: GAP-13, GAP-14 confirmed. Proceed.**

### 6. V106 migration and `excluded_user_ids` column absent
**Before:** Last migration is `V105_adjust_tag_review.sql`. No `excluded_user_ids` column in `walk_intent`.  
**Verdict: GAP-11 confirmed. Proceed.**

---

## FCM Dual-Channel State

| Check | Status |
|---|---|
| `sendPush()` added to `PushNotificationProvider` | **yes** |
| `sendPush()` implemented in `FcmNotificationProvider` | **yes** |
| `NotificationPublisherImpl` dual-dispatches (DB + FCM) | **yes** |
| Manual `sendMatchFound()` removed from `MatchingCommandService` | **yes** |
| `PushNotificationProvider` dependency removed from `MatchingCommandService` | **yes** |
| `UserRepository` dependency removed from `MatchingCommandService` | **yes** |
| `PROPOSAL_RECEIVED` auto-pushed via FCM | **yes** |
| `SESSION_CONFIRMED` auto-pushed via FCM | **yes** |
| `SESSION_ACTIVE` auto-pushed via FCM | **yes** |
| `REVIEW_REQUESTED` auto-pushed via FCM | **yes** |

---

## Scheduler State

| Check | Status |
|---|---|
| `IntentScheduler` created at | `application/walkintent/IntentScheduler.java` |
| `@Scheduled` interval | `fixedDelay = 60_000` (60 seconds) |
| `findIntentsExpiringSoon()` query | `time_window_start <= :cutoff AND time_window_end > :now AND status IN ('OPEN', 'MATCHING')` |
| `findOverdueOpenIntents()` query | `time_window_end <= :now AND status IN ('OPEN', 'MATCHING')` |
| V106 migration applied | **yes** (file created — will run on next startup) |

---

## Deviations from Plan

### 1. `MatchProposal.expire()` was already present
**Plan said:** "New domain method `MatchProposal.expire()`."  
**Actual:** `MatchProposal.expire()` was implemented in Phase 0 (`sweepExpiredProposals` already used it). No change needed.  
**Verdict:** No deviation — pre-existing correct implementation. Noted in checklist.

### 2. `UserRepository` removed from `MatchingCommandService` entirely
**Plan said:** Remove `PushNotificationProvider` from `MatchingCommandService` dependencies.  
**Actual:** `userRepository` was also only used by the removed manual FCM block. Removed both dependencies and their imports, leaving the class dependency-minimal.  
**Reason:** Dead dependency; removing it prevents IDE warnings and clarifies intent.

### 3. `IntentScheduler` uses `TransactionTemplate` per-item, not `@Transactional` on method
**Plan said:** Code snippet showed `handleExpiredIntents()` with inline logic.  
**Actual:** Per-item isolation via `transactionTemplate.execute()` inside `expireIntentSafely()`, matching the `sweepExpiredProposals()` pattern in `MatchingCommandService`. A failure on one intent rolls back only that item.  
**Reason:** `@Transactional` on a single method would commit all expirations together — one OCC conflict would roll back the entire sweep. Per-item isolation is more robust.

### 4. `findIntentsExpiringSoon()` adds `time_window_end > :now` guard
**Plan said:** `time_window_start <= :now + :buffer AND status IN ('OPEN', 'MATCHING')`.  
**Actual:** Added `AND time_window_end > :now` to prevent overlap with `findOverdueOpenIntents()`. Without this guard, an intent whose window has fully passed could be returned by both queries, causing a second `expireIntentSafely()` call that would fail with `INTENT_ALREADY_TERMINAL`.  
**Reason:** Prevents noisy error logs for expected double-processing; cleaner separation of the two sweep paths.

### 5. `excludeUser()` deduplicates before adding
**Plan said:** `this.excludedUserIds.add(userId)`.  
**Actual:** Added `if (!this.excludedUserIds.contains(userId))` guard before adding. `version` is still incremented to ensure optimistic lock is bumped (signals a write occurred).  
**Reason:** The DB column is `uuid[] NOT NULL DEFAULT '{}'` with no UNIQUE constraint per element. Calling `passProposal()` twice for the same pair would otherwise insert duplicates. The dedup is O(n) on what will be a very small list in practice.

---

## Open Issues / Blockers

### 1. No test coverage for Phase 2B changes
No unit or integration tests written (same pattern as prior phases). Candidates for Phase 3 test pass:
- `excludeUser()` dedup guard and version increment
- `findOpenCandidates()` exclude filter: candidate with matching userId in `excluded_user_ids` is filtered out
- `IntentScheduler.handleExpiredIntents()`: pre-start path, overdue path, cascade to proposal + partner unlock
- `NotificationPublisherImpl`: DB channel failure does not block FCM; FCM failure does not block DB
- `sendPush()` in `FcmNotificationProvider`: FCM exception swallowed, `log.error` called

### 2. `PROPOSAL_RECEIVED` FCM payload does not include `intentId`
~~The `notificationPublisher.publish()` payload contains `proposalId` and `senderUserId` but NOT the recipient's `intentId`.~~  
**Fixed (post-phase technical debt pass):** `intentId` (the recipient's own intent ID) added to the `PROPOSAL_RECEIVED` payload in `MatchingCommandService.findOrCreateProposal()`.

### 3. `SocialJdbcRepository` follow/block methods reference dropped `follow_relation` table
~~Inherited open issue from Phase 1 report.~~  
**Fixed (post-phase technical debt pass):** All five follow methods (`follow`, `unfollow`, `isFollowing`, `getFollowerIds`, `getFolloweeIds`) reimplemented against the `friendship` table (V104). See fix details below.
