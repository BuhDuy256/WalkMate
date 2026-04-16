# Phase 4 Report — Private Invite Flow
**Date:** 2026-04-13
**Branch:** `merge/oauth`
**Gap Closed:** 3.2 — UC-15 Case B private invite: receiver intent, proposal, auto-accept, notifications

---

## Summary

Phase 4 implements the atomic private invite transaction required by UC-15 Case B, invariants I-2, I-7, and P-1(b). All 7 steps (sender intent, receiver overlap check, receiver intent, proposal creation, auto-accept, notifications, return) execute inside a single `TransactionTemplate` boundary. Any failure rolls back all DB changes — no orphaned intents or ghost proposals can be committed.

---

## Steps Implemented

### Step 1 — `NotificationType.INVITE_SENT` added (`NotificationType.java`)

`INVITE_SENT` did not exist in the enum. Added it to support the sender-side push notification in step 7 of the private invite flow. The existing `PROPOSAL_RECEIVED` type is reused for the receiver.

**File:** `domain/notification/NotificationType.java`

---

### Step 2 — `findOpenByUserId()` now filters `is_private = false` (`WalkIntentJdbcRepository.java`)

**Invariant I-7:** Private intents must never appear in public results.

The `findOpenByUserId()` SQL (used by UC-16 `GET /api/v1/intents`) returned all `OPEN`/`MATCHING` intents including private ones. Added `AND is_private = false` to the WHERE clause.

```sql
-- Before
WHERE user_id = :userId
  AND status IN ('OPEN', 'MATCHING')

-- After
WHERE user_id = :userId
  AND status IN ('OPEN', 'MATCHING')
  AND is_private = false
```

The matching engine query (`findOpenCandidates()`) already contained `AND (wi.is_private = false OR wi.invited_friend_id = :callerId)` — no change required. Private intents also enter `MATCHING` immediately (never `OPEN`), so the `status = 'OPEN'` filter in `findOpenCandidates()` already excluded them from the match pool.

**File:** `infrastructure/repository/walkintent/WalkIntentJdbcRepository.java`

---

### Step 3 — `createPrivateInviteIntent()` implemented (`WalkIntentCommandService.java`)

**New fields injected:**
- `MatchProposalRepository matchProposalRepository` — to persist the proposal created in step 5
- `NotificationPublisher notificationPublisher` — to fire push notifications in step 7

**Routing change in `createIntent()`:**

The private-path branch now routes immediately to `createPrivateInviteIntent()` after friendship validation, running inside `transactionTemplate.execute()`:

```java
if (command.isPrivate() && command.invitedFriendId() != null) {
    CreateIntentResult result = transactionTemplate.execute(status ->
            createPrivateInviteIntent(command));
    ...
    return result;
}
```

**`createPrivateInviteIntent()` — 7-step atomic flow:**

| Step | Action | Invariant |
|---|---|---|
| 1 | Validate hotspot, sender overlap, friendship | I-1, I-7 |
| 2 | Create sender intent (OPEN → MATCHING via `lock()`) | I-4 |
| 3 | Check receiver overlap (intent + session) | I-1 |
| 4 | Create system-generated receiver intent (MATCHING, `is_private=true`) | I-7, I-4 |
| 5 | Create `MatchProposal` via `MatchProposal.create()` (5-min TTL) | P-1(b) |
| 6 | Auto-accept sender: `matchingCommandService.acceptProposal(proposalId, senderId)` | P-2 |
| 7 | Publish `INVITE_SENT` to sender, `PROPOSAL_RECEIVED` to receiver | UC-15 Case B |

**File:** `application/walkintent/WalkIntentCommandService.java`

---

## `MatchProposal.createPrivate()` Decision

`MatchProposal.createPrivate()` was not implemented — it is unnecessary. The existing `MatchProposal.create()` factory accepts all required parameters (`intentIdA`, `intentIdB`, `userIdA`, `userIdB`, `lat`, `lng`, `startTime`, `endTime`, `expiresAt`) and does not embed a privacy flag on the proposal entity itself. Privacy is a property of the `WalkIntent` rows (`is_private = true`), not of `MatchProposal`. Using `create()` directly avoids duplication.

---

## Transaction Boundary

`acceptProposal()` is annotated `@Transactional(propagation = REQUIRED)`. When invoked from inside `transactionTemplate.execute()`, it joins the existing transaction rather than opening a new one. Since the receiver has not yet accepted, `bothAccepted = false` — no session is created during the auto-accept step. The returned proposal reflects `acceptedByA = true` and is used as the response payload.

---

## Verification Checklist

- [x] `WalkIntentCommandService.createIntent()` routes `isPrivate=true` to `createPrivateInviteIntent()`
- [x] `createPrivateInviteIntent()` implements all 7 steps
- [x] Sender intent: `is_private=true`, status `MATCHING` after `lock()`
- [x] Receiver intent: `is_private=true`, `invitedFriendId=senderId`, status `MATCHING`
- [x] Proposal created with 5-minute TTL matching `MatchingCommandService.PROPOSAL_TTL_MINUTES`
- [x] Auto-accept via `matchingCommandService.acceptProposal()` (no logic duplication)
- [x] `NotificationType.INVITE_SENT` published to sender
- [x] `NotificationType.PROPOSAL_RECEIVED` published to receiver with `proposalId`, `intentId`, `senderUserId`
- [x] `findOpenByUserId()` SQL excludes `is_private = true` intents (UC-16 / I-7)
- [x] `findOpenCandidates()` SQL already excluded private intents — confirmed, no change needed
- [x] `./gradlew :backend:compileJava` — BUILD SUCCESSFUL
- [x] `./gradlew :backend:test --tests "com.walkmate.domain.*" --tests "com.walkmate.application.*"` — BUILD SUCCESSFUL
- [x] MCP index updated (3 files re-indexed)
