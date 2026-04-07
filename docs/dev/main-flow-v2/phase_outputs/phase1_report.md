# Phase 1 Completion Report

**Date:** 2026-04-07
**Branch:** `implement/realtime`

---

## Files Modified

```
backend/src/main/java/com/walkmate/application/proposal/MatchingCommandService.java
backend/src/main/java/com/walkmate/domain/session/WalkSession.java
backend/src/main/java/com/walkmate/infrastructure/repository/session/WalkSessionJdbcRepository.java
backend/src/main/java/com/walkmate/presentation/controller/session/SessionController.java
backend/src/main/java/com/walkmate/domain/walkintent/WalkIntent.java
backend/src/main/java/com/walkmate/domain/walkintent/WalkIntentErrorCode.java
backend/src/main/java/com/walkmate/application/walkintent/CreateWalkIntentCommand.java
backend/src/main/java/com/walkmate/presentation/dto/request/walkintent/CreateWalkIntentRequest.java
backend/src/main/java/com/walkmate/presentation/controller/walkintent/WalkIntentController.java
backend/src/main/java/com/walkmate/application/walkintent/WalkIntentCommandService.java
backend/src/main/java/com/walkmate/domain/social/SocialRepository.java
backend/src/main/java/com/walkmate/infrastructure/repository/social/SocialJdbcRepository.java
backend/src/main/java/com/walkmate/infrastructure/repository/walkintent/WalkIntentJdbcRepository.java
backend/src/main/java/com/walkmate/presentation/dto/response/walkintent/WalkIntentResponse.java
backend/src/main/java/com/walkmate/presentation/mapper/walkintent/WalkIntentMapper.java
docs/dev/main-flow-v2/implementation_plan.md
```

---

## Verification Results (Grapuco)

All five pre-implementation checks were performed against the live code graph.

### 1. `WalkSession` — activation window constants
**Found:** `backend/src/main/java/com/walkmate/domain/session/WalkSession.java`
**Before:** `ACTIVATION_WINDOW_BEFORE = 15 min`, `ACTIVATION_WINDOW_AFTER = 30 min`
**Verdict: GAP-6 confirmed — values are wrong, not yet fixed. Proceed.**

### 2. `MatchingCommandService` — proposal TTL
**Found:** `backend/src/main/java/com/walkmate/application/proposal/MatchingCommandService.java`
**Before:** `PROPOSAL_TTL_MINUTES = 30`
**Verdict: GAP-5 confirmed — TTL is wrong, not yet fixed. Proceed.**

### 3. `SessionController` — complete endpoint
**Found:** `backend/src/main/java/com/walkmate/presentation/controller/session/SessionController.java`
**Endpoints present:** `/active`, `/activate`, `/cancel`, `/abort`
**`/complete` absent — GAP-9 confirmed. Proceed.**

### 4. `WalkIntentJdbcRepository` — overlap check status set
**Found:** `backend/.../repository/walkintent/WalkIntentJdbcRepository.java`
**`hasOverlappingActiveIntent()` SQL:** `AND status IN ('OPEN', 'CONSUMED')`
**Verdict: GAP-12 confirmed — CONSUMED must be replaced with MATCHING. Proceed.**

### 5. `CreateWalkIntentCommand` — private intent fields
**Found:** `backend/src/main/java/com/walkmate/application/walkintent/CreateWalkIntentCommand.java`
**Fields present:** `hotspotId`, `userId`, `timeWindowStart`, `timeWindowEnd`, `ageMin`, `ageMax`
**`isPrivate`, `invitedFriendId`, `description` — all absent. GAP-10 confirmed. Proceed.**

**Cross-check vs phase0_report.md:** All five findings are consistent with Phase 0's scope (Phase 0 addressed only P0 gaps; none of the above were touched). No discrepancies.

---

## Deviations from Plan

### 1. `WalkSessionJdbcRepository.java` touched (Step 1.2)
**Plan said:** "Update SQL if needed." File was not listed in the Files Touched Summary for Phase 1.
**Actual:** `findSessionsPastActivationWindow()` hard-coded `INTERVAL '30 minutes'` instead of referencing the domain constant. After changing `ACTIVATION_WINDOW_AFTER` from 30 → 15 min, the SQL was inconsistent. Updated the literal to `INTERVAL '15 minutes'`.
**Reason:** The domain constant and SQL literal must agree; leaving them diverged would silently break the NO_SHOW sweep.

### 2. `SocialRepository.java` and `SocialJdbcRepository.java` touched (Step 1.4e)
**Plan said:** Validate ACCEPTED friendship in `WalkIntentCommandService`. Neither file was listed in the Files Touched Summary.
**Actual:** `SocialRepository` had no friendship query method — only follow/block. Added `boolean areAcceptedFriends(UUID userId1, UUID userId2)` to the interface and implemented it against the `friendship` table (V104 migration). Without this, the I-7 private-intent validation cannot be performed by the service without violating layering.
**Reason:** `SocialRepository` is missing this method; it is a necessary extension for the plan to compile and be correct.

### 3. `WalkIntentErrorCode.java` touched (Step 1.4e)
**Plan said:** "Throw a domain error if not [friends]." The specific error code constant was not listed.
**Actual:** Added `INTENT_PRIVATE_FRIEND_NOT_ACCEPTED` enum constant to `WalkIntentErrorCode`.
**Reason:** Consistent with the project's pattern of domain-specific error codes; required for the service to throw a meaningful typed error.

### 4. `WalkIntentMapper.java` touched (Step 1.4h)
**Plan said:** Update `WalkIntentResponse.java` with new fields. Mapper was not listed in Files Touched Summary.
**Actual:** `WalkIntentMapper.toResponse()` constructs `WalkIntentResponse` via a positional record constructor. Adding two new fields to the record causes a compile error in the mapper. Updated `toResponse()` to pass `intent.isPrivate()` and `intent.getDescription()`.
**Reason:** Implicit dependency — any record field addition requires the mapper to be updated; the code would not compile otherwise.

### 5. `invitedFriendId` omitted from `WalkIntentResponse` (plan-compliant)
**Plan said:** "Expose `isPrivate`, `description` (omit `invitedFriendId` for privacy unless caller is the owner)."
**Actual:** `invitedFriendId` is not included in `WalkIntentResponse`. Only `isPrivate` and `description` are exposed.
**Note:** No deviation — this follows the plan exactly.

### 6. Session overlap check not called from `WalkIntentCommandService` (pre-existing gap)
**Plan checklist item:** "Confirm `createIntent()` calls both checks (intent overlap + session overlap)."
**Finding:** `WalkIntentCommandService.createIntent()` only calls `hasOverlappingActiveIntent()`. It does NOT call `hasOverlappingActiveSession()`. This is a pre-existing gap not in Phase 1 scope. Noted here; not fixed.

---

## New Invariant State

- **Proposal TTL:** 5 minutes (was 30)
- **Activation window:** `[scheduledStart − 10 min, scheduledStart + 15 min]` per S-3 (was −15/+30)
- **Scheduler SQL boundary:** `scheduled_start + INTERVAL '15 minutes' < now` (was 30)
- **POST /sessions/{id}/complete:** exposed; enforces 5-minute minimum walk guard (S-5)
- **WalkIntent:** `isPrivate`, `invitedFriendId`, `description` present in all layers (domain, command, request DTO, controller, service, JDBC, response DTO)
- **Private intent validation:** if `isPrivate = true`, service checks ACCEPTED friendship via `SocialRepository.areAcceptedFriends()` before persisting; throws `INTENT_PRIVATE_FRIEND_NOT_ACCEPTED` if not friends
- **`findOpenCandidates()` SQL:** includes `AND (is_private = false OR invited_friend_id = :callerId)` enforcing I-7 at the DB level
- **Overlap check:** `hasOverlappingActiveIntent()` now uses `OPEN + MATCHING` (was `OPEN + CONSUMED`), correctly implementing I-1

---

## Open Issues / Blockers

### 1. `SocialJdbcRepository` follow/block methods still reference dropped `follow_relation` table
V104 migration dropped `follow_relation` and replaced it with `friendship`. The existing `follow()`, `unfollow()`, `isFollowing()`, `getFollowerIds()`, `getFolloweeIds()` methods in `SocialJdbcRepository` still query `follow_relation` — they will fail at runtime. The new `areAcceptedFriends()` method correctly queries the `friendship` table. The follow-related methods are not in Phase 1 scope (no follow flow is exercised by any Phase 1 endpoint), but they must be fixed before any social feature is tested against a real DB.

### 2. `WalkIntentCommandService.createIntent()` does not call `hasOverlappingActiveSession()`
Only the intent-level overlap check is performed. A user who already has a `PENDING` or `ACTIVE` session in the same time window can still create a new `WalkIntent`. Full I-1 enforcement requires calling both checks. Pre-existing gap; not in Phase 1 scope.

### 3. No test coverage for Phase 1 changes
No unit or integration tests were written for Phase 1 (same as Phase 0). Phase 2 should include tests for:
- `PROPOSAL_TTL_MINUTES = 5`: proposal expires within 5 minutes in the sweep
- Activation window: `recordActivation()` rejects timestamps outside `[−10 min, +15 min]`
- `POST /sessions/{id}/complete`: happy path and `SESSION_COMPLETE_TOO_EARLY` guard
- `WalkIntent` private fields: round-trip through all layers
- `findOpenCandidates()` I-7 filter: private intents not visible to non-invited users
- `hasOverlappingActiveIntent()`: MATCHING intents block overlap; CONSUMED do not
