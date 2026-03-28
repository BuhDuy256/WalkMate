# DOMAIN_CONTRACTS.md

WalkMate Backend | Aggregate Contracts & State Machine Spec

This is the **single source of truth** for all domain behavior.
When a test fails, compare the actual behavior against this document — not against memory or assumption.
When prompting Claude to generate code, paste the relevant section from here as context.

---

# 1. WalkIntent Aggregate

## 1.1 States

| State       | Meaning                                                       |
| ----------- | ------------------------------------------------------------- |
| `OPEN`      | Created, available for matching                               |
| `CONSUMED`  | Locked after MatchProposal is CONFIRMED. No further matching. |
| `EXPIRED`   | No match found within the scheduling window. Terminal.        |
| `CANCELLED` | Cancelled by the user before matching. Terminal.              |

Terminal states: `EXPIRED`, `CANCELLED`. A terminal WalkIntent must reject all mutations.

## 1.2 Valid Transitions

| From        | To          | Trigger                                 | Guard                                              |
| ----------- | ----------- | --------------------------------------- | -------------------------------------------------- |
| `OPEN`      | `CONSUMED`  | MatchProposal confirmed by both parties | Intent must still be `OPEN`. No schedule conflict. |
| `OPEN`      | `EXPIRED`   | System auto-expire job                  | Scheduling window has passed with no confirmation  |
| `OPEN`      | `CANCELLED` | User cancels                            | User must own this intent                          |
| `CONSUMED`  | *(none)*    | —                                       | Terminal. Immutable.                               |
| `EXPIRED`   | *(none)*    | —                                       | Terminal. Immutable.                               |
| `CANCELLED` | *(none)*    | —                                       | Terminal. Immutable.                               |

Forbidden transitions (must throw `DomainException`):
- `CONSUMED` → anything
- `EXPIRED` → anything
- `CANCELLED` → anything
- `OPEN` → `ACTIVE` directly (no shortcut past MatchProposal)

## 1.3 Invariants (must always hold)

```
1. startTime must be <= endTime
2. endTime must be in the future at creation time
3. A WalkIntent in CONSUMED/EXPIRED/CANCELLED state must never accept mutation calls
4. snapshotLocation must not be null
5. walkPurpose must not be null
6. An OPEN WalkIntent cannot be CONSUMED unless an associated MatchProposal is CONFIRMED
```

## 1.4 Error Codes (WalkIntentErrorCode)

| Code                        | Thrown When                                           |
| --------------------------- | ----------------------------------------------------- |
| `INTENT_NOT_FOUND`          | findById returns empty in application layer           |
| `INTENT_ALREADY_TERMINAL`   | Any mutation called on CONSUMED / EXPIRED / CANCELLED |
| `INTENT_INVALID_TIME_RANGE` | startTime >= endTime at creation                      |
| `INTENT_TIME_IN_PAST`       | endTime is in the past at creation                    |
| `INTENT_OWNER_MISMATCH`     | User attempts to cancel an intent they don't own      |
| `INTENT_NOT_OPEN`           | Attempt to CONSUME an intent that is not OPEN         |

## 1.5 Method Contracts

### `WalkIntent.create(userId, scheduledStart, scheduledEnd, purpose, location, constraints)`
- Validates: `scheduledStart < scheduledEnd`
- Validates: `scheduledEnd` is after current time
- Sets status to `OPEN`
- Throws `INTENT_INVALID_TIME_RANGE` or `INTENT_TIME_IN_PAST` on violation

### `WalkIntent.consume()`
- Validates: current status is `OPEN`
- Sets status to `CONSUMED`
- Throws `INTENT_NOT_OPEN` if called on any non-OPEN state

### `WalkIntent.cancel(requestingUserId)`
- Validates: current status is `OPEN`
- Validates: `requestingUserId == this.ownerId`
- Sets status to `CANCELLED`
- Throws `INTENT_ALREADY_TERMINAL` if called on terminal state
- Throws `INTENT_OWNER_MISMATCH` if user doesn't own the intent

---

# 2. MatchProposal Aggregate

## 2.1 States

| State       | Meaning                                                         |
| ----------- | --------------------------------------------------------------- |
| `PENDING`   | Created by matching engine. Awaiting responses from both users. |
| `CONFIRMED` | Both users accepted. Triggers WalkSession creation.             |
| `REJECTED`  | At least one user rejected. Terminal.                           |
| `EXPIRED`   | Neither user responded within the response window. Terminal.    |

Terminal states: `REJECTED`, `EXPIRED`.

## 2.2 Valid Transitions

| From        | To          | Trigger                                          | Guard                                      |
| ----------- | ----------- | ------------------------------------------------ | ------------------------------------------ |
| `PENDING`   | `CONFIRMED` | Both acceptedByA == true AND acceptedByB == true | Both parties must have explicitly accepted |
| `PENDING`   | `REJECTED`  | Either user rejects                              | —                                          |
| `PENDING`   | `EXPIRED`   | System timeout                                   | Response window elapsed                    |
| `CONFIRMED` | *(none)*    | —                                                | Terminal. Immutable.                       |
| `REJECTED`  | *(none)*    | —                                                | Terminal. Immutable.                       |
| `EXPIRED`   | *(none)*    | —                                                | Terminal. Immutable.                       |

Forbidden: WalkSession creation without `MatchProposal.status == CONFIRMED`. No exceptions.

## 2.3 Invariants

```
1. A MatchProposal must reference exactly two distinct WalkIntents (intentA ≠ intentB)
2. CONFIRMED state requires acceptedByA == true AND acceptedByB == true (both, not either)
3. A CONFIRMED MatchProposal must have triggered WalkSession creation before becoming immutable
4. No two PENDING MatchProposals may reference the same WalkIntent
```

## 2.4 Error Codes (MatchProposalErrorCode)

| Code                         | Thrown When                                           |
| ---------------------------- | ----------------------------------------------------- |
| `PROPOSAL_NOT_FOUND`         | findById returns empty                                |
| `PROPOSAL_ALREADY_TERMINAL`  | Mutation on REJECTED or EXPIRED                       |
| `PROPOSAL_ALREADY_CONFIRMED` | Accept called but already CONFIRMED                   |
| `PROPOSAL_BLOCK_EXISTS`      | A block relation exists between the two users         |
| `PROPOSAL_DUPLICATE_INTENT`  | A PENDING proposal already references the same intent |

---

# 3. WalkSession Aggregate

## 3.1 States

| State       | Meaning                                                                | Terminal |
| ----------- | ---------------------------------------------------------------------- | -------- |
| `PENDING`   | Created after mutual confirmation. Waiting for both users to activate. | No       |
| `ACTIVE`    | Both users activated within the activation window. GPS tracking on.    | No       |
| `COMPLETED` | Walk finished successfully. Minimum duration was met.                  | ✅ Yes    |
| `NO_SHOW`   | Activation window elapsed without both users activating.               | ✅ Yes    |
| `CANCELLED` | Cancelled by a user from PENDING state only.                           | ✅ Yes    |

## 3.2 Valid Transitions

| From        | To          | Trigger                                                     | Guard                                                    |
| ----------- | ----------- | ----------------------------------------------------------- | -------------------------------------------------------- |
| `PENDING`   | `ACTIVE`    | Both users call `activateByUser()` within activation window | Both activations must occur. Time must be within window. |
| `PENDING`   | `CANCELLED` | User cancels                                                | Must be called from `PENDING` only                       |
| `PENDING`   | `NO_SHOW`   | System auto-job                                             | Activation window elapsed without full activation        |
| `ACTIVE`    | `COMPLETED` | User calls `complete()` or auto-complete trigger            | Elapsed time >= 5 minutes (minimum duration)             |
| `COMPLETED` | *(none)*    | —                                                           | Terminal. Immutable.                                     |
| `NO_SHOW`   | *(none)*    | —                                                           | Terminal. Immutable.                                     |
| `CANCELLED` | *(none)*    | —                                                           | Terminal. Immutable.                                     |

Forbidden transitions (must throw `DomainException`):
- `ACTIVE` → `NO_SHOW` (by any mechanism — NO_SHOW only comes from PENDING)
- `ACTIVE` → `CANCELLED` (cannot cancel an active walk)
- `PENDING` → `COMPLETED` (cannot skip ACTIVE)
- Any terminal state → any other state

## 3.3 Activation Window Rules

```
Window opens:  scheduledStart - 15 minutes
Window closes: scheduledStart + 30 minutes

Both userA and userB must call activateByUser() within this window.
If only one user activates and the window closes → NO_SHOW (system job).
If neither user activates and the window closes → NO_SHOW (system job).
```

## 3.4 Completion Rules

```
Minimum duration: 5 minutes
  → complete() must throw SESSION_MINIMUM_DURATION_NOT_MET if elapsed < 5 min

Safety auto-complete limit: 4 hours
  → System job calls complete() automatically after 4 hours in ACTIVE state

Manual complete: user calls complete() after >= 5 minutes in ACTIVE state
```

## 3.5 Chat Access Rules

| Session State | Chat Access         |
| ------------- | ------------------- |
| `PENDING`     | Open (read + write) |
| `ACTIVE`      | Open (read + write) |
| `COMPLETED`   | Read-only           |
| `CANCELLED`   | Closed              |
| `NO_SHOW`     | Closed              |

`isChatWritable()` must return true only for PENDING and ACTIVE.

## 3.6 Invariants

```
1. A WalkSession in a terminal state must reject all mutation calls
2. ACTIVE state is only reachable if both activationByA and activationByB are recorded
3. complete() is only valid from ACTIVE state
4. cancel() is only valid from PENDING state
5. Activation timestamps must be within the activation window
6. The session must reference exactly two distinct userIds (participantA ≠ participantB)
7. scheduledStart must be before scheduledEnd
```

## 3.7 Error Codes (WalkSessionErrorCode)

| Code                                 | Thrown When                                              |
| ------------------------------------ | -------------------------------------------------------- |
| `SESSION_NOT_FOUND`                  | findById returns empty in application layer              |
| `SESSION_ALREADY_TERMINAL`           | Any mutation on COMPLETED / NO_SHOW / CANCELLED          |
| `SESSION_ACTIVATION_WINDOW_EXPIRED`  | activateByUser() called after window close time          |
| `SESSION_ACTIVATION_WINDOW_NOT_OPEN` | activateByUser() called before window open time          |
| `SESSION_ALREADY_ACTIVATED_BY_USER`  | Same user calls activateByUser() twice                   |
| `SESSION_MINIMUM_DURATION_NOT_MET`   | complete() called before 5 minutes have elapsed          |
| `SESSION_NOT_ACTIVE`                 | complete() called when state is not ACTIVE               |
| `SESSION_NOT_PENDING`                | cancel() called when state is not PENDING                |
| `SESSION_USER_NOT_PARTICIPANT`       | Action by a user who is not participantA or participantB |

## 3.8 Method Contracts

### `WalkSession.activateByUser(userId, activationTime)`
- Guards: status must not be terminal → throws `SESSION_ALREADY_TERMINAL`
- Guards: `userId` must be participantA or participantB → throws `SESSION_USER_NOT_PARTICIPANT`
- Guards: `activationTime` must be within the activation window → throws `SESSION_ACTIVATION_WINDOW_EXPIRED` or `SESSION_ACTIVATION_WINDOW_NOT_OPEN`
- Guards: user has not already activated → throws `SESSION_ALREADY_ACTIVATED_BY_USER`
- Records the activation for that user
- If both users have now activated → transitions to `ACTIVE`

### `WalkSession.complete(completionTime)`
- Guards: status must be `ACTIVE` → throws `SESSION_NOT_ACTIVE`
- Guards: `completionTime - activationTime >= 5 minutes` → throws `SESSION_MINIMUM_DURATION_NOT_MET`
- Sets status to `COMPLETED`, records end time

### `WalkSession.cancel(requestingUserId)`
- Guards: status must be `PENDING` → throws `SESSION_NOT_PENDING`
- Guards: `requestingUserId` must be a participant → throws `SESSION_USER_NOT_PARTICIPANT`
- Sets status to `CANCELLED`

### `WalkSession.markNoShow()`
- Called only by system job (no userId check)
- Guards: status must be `PENDING` → throws `SESSION_NOT_PENDING`
- Sets status to `NO_SHOW`

### `WalkSession.isChatWritable()`
- Returns `true` if status is `PENDING` or `ACTIVE`
- Returns `false` for all other states
- No side effects. Never throws.

---

# 4. User Aggregate

## 4.1 States / Modes

| Attribute        | Value     | Meaning                               |
| ---------------- | --------- | ------------------------------------- |
| `visibilityMode` | `PUBLIC`  | User appears in coordination/matching |
| `visibilityMode` | `PRIVATE` | User does not appear in matching      |

No full state machine — User does not have lifecycle states like Session.

## 4.2 Invariants

```
1. email must be a valid format if provided
2. displayName must not be blank
3. A PRIVATE user must not appear as a candidate in any MatchProposal
4. A user in a block relation with another must never appear in their MatchProposal candidates
```

## 4.3 Error Codes (UserErrorCode)

| Code                        | Thrown When                                      |
| --------------------------- | ------------------------------------------------ |
| `USER_NOT_FOUND`            | findById returns empty                           |
| `USER_INVALID_CREDENTIALS`  | Password does not match during login             |
| `USER_EMAIL_ALREADY_EXISTS` | Registration with a duplicate email              |
| `USER_PHONE_ALREADY_EXISTS` | Registration with a duplicate phone number       |
| `USER_DISPLAY_NAME_BLANK`   | displayName is null or empty                     |
| `USER_ALREADY_PRIVATE`      | Attempt to set PRIVATE mode when already PRIVATE |
| `USER_ALREADY_PUBLIC`       | Attempt to set PUBLIC mode when already PUBLIC   |

---

# 5. TrustScore Aggregate

## 5.1 Behavior

TrustScore is updated by domain events, not by direct user action.

| Trigger Event                 | Effect                                   |
| ----------------------------- | ---------------------------------------- |
| `WalkSessionCompleted`        | Positive contribution                    |
| `WalkReviewCreated` (5 stars) | Positive contribution                    |
| `PartnerNoShowReported`       | Negative contribution                    |
| `SessionCancelled` (late)     | Negative contribution (tiered by timing) |

## 5.2 Invariants

```
1. TrustScore value must never go below 0
2. TrustScore is never mutated directly by user action — only by events
3. Score changes are applied by TrustScore aggregate methods, not inline in services
```

## 5.3 Error Codes (TrustScoreErrorCode)

| Code                     | Thrown When                                                         |
| ------------------------ | ------------------------------------------------------------------- |
| `TRUST_SCORE_NOT_FOUND`  | findByUserId returns empty                                          |
| `TRUST_SCORE_BELOW_ZERO` | A deduction would push the score below 0 (apply floor at 0 instead) |

---

# 6. Cross-Aggregate Rules (Domain Service Level)

These rules span multiple aggregates and are enforced by Domain Services, not by individual entities.

```
1. WalkSession can only be created if:
   a. MatchProposal.status == CONFIRMED
   b. Both referenced WalkIntents are still OPEN at the moment of creation
   c. No schedule conflict exists between the two intents

2. A MatchProposal can only be created if:
   a. No BlockRelation exists between the two users
   b. Both WalkIntents have overlapping time windows
   c. WalkPurpose matches between the two intents
   d. No existing PENDING MatchProposal already references either intent

3. Following is only possible after at least one COMPLETED WalkSession between the two users

4. Rating is only possible once per COMPLETED WalkSession, per participant
```

---

# 7. How to Use This Document in Vibe Coding

## When generating domain entity code
Paste the relevant aggregate section (state table + invariants + method contracts) into your prompt:
> "Implement `WalkSession.activateByUser()` according to these contracts: [paste section 3.8]"

## When generating tests
Paste the method contract + error codes to give Claude the exact scenarios:
> "Write tests for `WalkSession.activateByUser()` covering all guards in section 3.8 and all error codes in section 3.7"

## When a test fails
Compare the actual behavior against the relevant section here. The contract is the referee.

## When adding a new feature
Update this document first, then generate code from it. The document leads the code, never the reverse.