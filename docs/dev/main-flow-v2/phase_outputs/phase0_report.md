# Phase 0 Completion Report

**Date:** 2026-04-07
**Branch:** `implement/realtime`

---

## Files Modified

```
backend/src/main/java/com/walkmate/domain/walkintent/IntentStatus.java
backend/src/main/java/com/walkmate/domain/walkintent/WalkIntentErrorCode.java
backend/src/main/java/com/walkmate/domain/walkintent/WalkIntent.java
backend/src/main/java/com/walkmate/domain/proposal/ProposalErrorCode.java
backend/src/main/java/com/walkmate/domain/proposal/MatchProposal.java
backend/src/main/java/com/walkmate/domain/proposal/MatchProposalRepository.java
backend/src/main/java/com/walkmate/infrastructure/repository/proposal/MatchProposalJdbcRepository.java
backend/src/main/java/com/walkmate/application/proposal/MatchingCommandService.java
backend/src/main/java/com/walkmate/application/session/SessionScheduler.java
docs/dev/main-flow-v2/implementation_plan.md
```

---

## Verification Results (Grapuco)

All five verification searches were performed against the Grapuco code graph before any code was written.

### 1. `IntentStatus` search
**Found:** `IntentStatus.java` at `backend/src/main/java/com/walkmate/domain/walkintent/IntentStatus.java`
**Current enum values (before Phase 0):** `OPEN, CONSUMED, CANCELLED, EXPIRED`
**Verdict: MATCHING is absent — GAP-1 confirmed.**

### 2. `WalkIntent` search
**Found:** `WalkIntent.java` at `backend/src/main/java/com/walkmate/domain/walkintent/WalkIntent.java`
**Methods present:** `create()`, `cancel()`, `consume()`
**Missing:** `lock()`, `unlock()` — GAP-1/GAP-3 confirmed.
**`consume()` behaviour:** negative guards only (blocks CANCELLED/CONSUMED) — no positive MATCHING guard — GAP-1 confirmed.
**`cancel()` behaviour:** blocks CANCELLED/CONSUMED only; OPEN and MATCHING are implicitly allowed — no change needed to the guard logic.

### 3. `MatchProposal` search
**Found:** `MatchProposal.java` at `backend/src/main/java/com/walkmate/domain/proposal/MatchProposal.java`
**Fields:** `proposalId`, `intentIdA`, `intentIdB`, `userIdA`, `userIdB`, `proposedLocationLat`, `proposedLocationLng`, `proposedStartTime`, `proposedEndTime`, `acceptedByA`, `acceptedByB`, `status`, `createdAt`, `expiresAt`, `confirmedAt`
**`version` field: ABSENT — GAP-4 confirmed.**

### 4. `MatchingCommandService` search
**Found:** `MatchingCommandService.java` at `backend/src/main/java/com/walkmate/application/proposal/MatchingCommandService.java`
**`acceptProposal()` guard (line 184):** `if (first.getStatus() != IntentStatus.OPEN || second.getStatus() != IntentStatus.OPEN)` — checks **OPEN**, not MATCHING — **GAP-7 confirmed.**
**`findOrCreateProposal()`:** saves proposal but never calls `lock()` on either intent — **GAP-2 confirmed.**
**`passProposal()`:** calls `proposal.reject()` and saves, but never unlocks intents — **GAP-3 confirmed.**
**`cancelProposal()`:** cancels caller's intent but never unlocks partner's intent — **GAP-3 confirmed.**

### 5. `MatchProposalJdbcRepository` search
**Found:** `MatchProposalJdbcRepository.java` at `backend/.../repository/proposal/MatchProposalJdbcRepository.java`
**`save()` method:** uses `ON CONFLICT (proposal_id) DO UPDATE SET ...` — **no `AND version = :expectedVersion` guard — GAP-4 confirmed.**
**SELECT:** does not include `mp.version` column — **GAP-4 confirmed.**

---

## Deviations from Plan

### 1. `save()` implementation strategy (Step 0.5)
**Plan said:** A single UPDATE SQL with `AND version = :expectedVersion`.
**Actual:** Branched `save()` into INSERT (version == 0) and UPDATE (version > 0) paths to preserve the ability to distinguish fresh entities from mutated ones. The UPDATE SQL exactly matches the plan's specification (`version = version + 1 WHERE version = :expectedVersion`), with `expectedVersion = proposal.getVersion() - 1` since domain methods already increment version before save.
**Reason:** The plan's stated UPDATE SQL cannot handle the initial INSERT case. Branching is the correct OCC pattern and the UPDATE path matches the plan exactly.

### 2. Additional files touched beyond plan scope (Step 0.4 scheduler path)
**Plan said:** "Add an `expireProposal(proposalId)` method or handle in the existing expired-session sweep."
**Actual:**
- Added `findExpiredPending()` to `MatchProposalRepository` interface and `MatchProposalJdbcRepository` implementation.
- Added `sweepExpiredProposals()` to `MatchingCommandService`.
- Added `MatchingCommandService` injection to `SessionScheduler` and wired the sweep call.
**Additional files:** `MatchProposalRepository.java`, `SessionScheduler.java`
**Reason:** The scheduled expiry checklist item mandated a complete, working path. A stub method that nobody calls would not satisfy the invariant.

### 3. Added `expire()` to `MatchProposal` (Step 0.5 adjacent)
**Plan said:** `version++` in `recordAcceptance()`, `reject()`, `confirm()`.
**Actual:** Also added `expire()` domain method (sets status to EXPIRED, increments version) needed by the scheduler sweep.
**Reason:** `ProposalStatus.EXPIRED` already existed in the DB/enum but had no corresponding domain method. The scheduler path requires it.

### 4. Added `PROPOSAL_CONCURRENT_MODIFICATION` to `ProposalErrorCode`
**Plan said:** "Throw `OptimisticLockException` (or domain-specific error) when update returns 0 rows."
**Actual:** Added `PROPOSAL_CONCURRENT_MODIFICATION` enum constant to `ProposalErrorCode` and used that in the repository.
**Reason:** Consistent with the project's pattern of domain-specific error codes (no framework exception types in domain layer).

### 5. Updated `PROPOSAL_INTENT_NO_LONGER_OPEN` message text
**Original message:** `"One or both intents are no longer OPEN — matching conflict"`
**Updated message:** `"One or both intents are no longer MATCHING — matching conflict"`
**Reason:** The error code name is intentionally kept unchanged to avoid breaking any client-side error code checks; only the human-readable message was corrected to reflect the new MATCHING invariant.

---

## New Domain State

### `IntentStatus` enum
```
OPEN, MATCHING, CONSUMED, CANCELLED, EXPIRED
```

### `WalkIntent` domain methods
| Method | Source state | Target state | Guard |
|---|---|---|---|
| `lock()` | OPEN | MATCHING | throws `INTENT_NOT_OPEN` if not OPEN |
| `unlock()` | MATCHING | OPEN | throws `INTENT_NOT_MATCHING` if not MATCHING |
| `consume()` | MATCHING | CONSUMED | throws `INTENT_NOT_MATCHING` if not MATCHING |
| `cancel()` | OPEN or MATCHING | CANCELLED | throws if CANCELLED or CONSUMED |

### `MatchProposal` domain
- `version` field present, initialised to `0` in factory constructor.
- `version++` in `recordAcceptance()`, `reject()`, `expire()`, `confirm()`.
- New `expire()` method: PENDING → EXPIRED, version++.

### `MatchProposalJdbcRepository`
- INSERT: writes `version = 0`.
- UPDATE: `WHERE version = :expectedVersion` (OCC guard); throws `PROPOSAL_CONCURRENT_MODIFICATION` when `rowsUpdated == 0`.
- SELECT: reads `mp.version`.

### `MatchingCommandService`
- `findOrCreateProposal()`: after proposal save, calls `intent.lock()` and `matched.lock()`, saves both intents within the same `@Transactional` boundary.
- `passProposal()`: after `proposal.reject()`, calls `unlock()` on both intents.
- `cancelProposal()`: calls `cancel()` on caller's intent, `unlock()` on partner's intent.
- `sweepExpiredProposals()`: finds all PENDING proposals past `expires_at`, calls `expire()` on each, then unlocks both associated intents if still in MATCHING state.
- `acceptProposal()` critical section: re-verifies intents are **MATCHING** (was OPEN) before consuming.

### `SessionScheduler`
Calls `matchingCommandService.sweepExpiredProposals()` in the same 60-second sweep loop as `sessionCommandService.handleExpiredSessions()`.

---

## Open Issues / Blockers

### 1. `findOrCreateProposal()` uses non-locking intent reads for the lock() operation
In Step 0.3, `intent` and `matched` are loaded via `findById()` (no pessimistic lock), not `findByIdForUpdate()`. The lock calls (`intent.lock()` / `matched.lock()`) are therefore not protected by a DB row lock. Concurrent matching of the same intent by two threads calling `findOrCreateProposal` at the same millisecond could both see status=OPEN, both create proposals, and both try to `lock()`. The unique partial index on `match_proposal` (V7 migration) prevents duplicate PENDING proposals (one will fail at the DB level), but the `save(intent)` with `status=MATCHING` may still collide. This is a race condition that should be addressed in a follow-up by using `findByIdForUpdate` for the intent objects at the point of `lock()`. Phase 0 does not introduce a regression here — the pre-Phase-0 code had no locking at all.

### 2. `sweepExpiredProposals()` runs all proposals in one transaction
If the proposal sweep processes multiple expired proposals in a single `@Transactional`, a failure mid-sweep rolls back all changes. For production correctness, each proposal should be processed in its own transaction (via a transactional helper bean or `TransactionTemplate`). This is a robustness issue, not a correctness issue — a failed sweep retries in 60 seconds.

### 3. No test coverage for Phase 0 changes
No unit or integration tests were written as part of Phase 0. Phase 1 should include tests that verify:
- `WalkIntent.lock()` / `unlock()` state transitions and guards
- `MatchProposal.version` OCC: concurrent save throws `PROPOSAL_CONCURRENT_MODIFICATION`
- `MatchingCommandService.findOrCreateProposal()` sets both intents to MATCHING
- `passProposal()` / `cancelProposal()` correctly restore intents to OPEN
- `sweepExpiredProposals()` expires proposals and unlocks intents