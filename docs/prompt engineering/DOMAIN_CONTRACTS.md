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

| Code                        | Thrown When                                           | HTTP |
| --------------------------- | ----------------------------------------------------- | ---- |
| `INTENT_NOT_FOUND`          | findById returns empty in application layer           | 404  |
| `INTENT_ALREADY_TERMINAL`   | Any mutation called on CONSUMED / EXPIRED / CANCELLED | 409  |
| `INTENT_INVALID_TIME_RANGE` | startTime >= endTime at creation                      | 400  |
| `INTENT_TIME_IN_PAST`       | endTime is in the past at creation                    | 400  |
| `INTENT_OWNER_MISMATCH`     | User attempts to cancel an intent they don't own      | 403  |
| `INTENT_NOT_OPEN`           | Attempt to CONSUME an intent that is not OPEN         | 409  |

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
- **Cross-aggregate side effect (§6):** Domain Service must call `expire()` on all `PENDING` MatchProposals that reference this intent

### `WalkIntent.expire()`
- Called only by system job (no userId check)
- Guards: current status must be `OPEN` → throws `INTENT_ALREADY_TERMINAL` if already terminal
- Sets status to `EXPIRED`
- **Cross-aggregate side effect (§6):** Domain Service must call `expire()` on all `PENDING` MatchProposals that reference this intent

---

# 2. MatchProposal Aggregate

## 2.1 States

| State       | Meaning                                                         |
| ----------- | --------------------------------------------------------------- |
| `PENDING`   | Created by matching engine. Awaiting responses from both users. |
| `CONFIRMED` | Both users accepted. Triggers WalkSession creation.             |
| `REJECTED`  | At least one user rejected. Terminal.                           |
| `EXPIRED`   | Neither user responded within the response window. Terminal.    |

Terminal states: `CONFIRMED`, `REJECTED`, `EXPIRED`.

> Note: `CONFIRMED` is terminal for the MatchProposal domain object. WalkSession creation happens atomically in the same DB transaction as the `CONFIRMED` transition — orchestrated by the Domain Service. There is never a moment where `CONFIRMED` exists without an associated WalkSession.

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
3. WalkSession creation must occur atomically within the same transaction as the CONFIRMED transition — there is no moment where CONFIRMED exists in DB without an associated WalkSession
4. No two PENDING MatchProposals may reference the same WalkIntent
```

## 2.4 Error Codes (MatchProposalErrorCode)

| Code                           | Thrown When                                           | HTTP |
| ------------------------------ | ----------------------------------------------------- | ---- |
| `PROPOSAL_NOT_FOUND`           | findById returns empty                                | 404  |
| `PROPOSAL_ALREADY_TERMINAL`    | Mutation on REJECTED or EXPIRED                       | 409  |
| `PROPOSAL_ALREADY_CONFIRMED`   | Accept called but already CONFIRMED                   | 409  |
| `PROPOSAL_BLOCK_EXISTS`        | A block relation exists between the two users         | 403  |
| `PROPOSAL_DUPLICATE_INTENT`    | A PENDING proposal already references the same intent | 409  |
| `PROPOSAL_USER_NOT_PARTICIPANT`| userId is neither intentOwnerA nor intentOwnerB       | 403  |

## 2.5 Method Contracts

### `MatchProposal.acceptByUser(userId)`
- Guards: status is `REJECTED` or `EXPIRED` → throws `PROPOSAL_ALREADY_TERMINAL`
- Guards: status is `CONFIRMED` → throws `PROPOSAL_ALREADY_CONFIRMED`
- Guards: `userId` is not `intentOwnerA` or `intentOwnerB` → throws `PROPOSAL_USER_NOT_PARTICIPANT`
- Records acceptance for that user (sets `acceptedByA` or `acceptedByB` to `true`)
- If both users have now accepted → transitions to `CONFIRMED`
- A CONFIRMED proposal must NOT trigger WalkSession creation inside this method — that is the responsibility of the Domain Service after it observes the CONFIRMED state

### `MatchProposal.rejectByUser(userId)`
- Guards: status is not `PENDING` → throws `PROPOSAL_ALREADY_TERMINAL`
- Guards: `userId` is not `intentOwnerA` or `intentOwnerB` → throws `PROPOSAL_USER_NOT_PARTICIPANT`
- Transitions to `REJECTED` immediately — a single rejection from either party is sufficient

### `MatchProposal.expire()`
- Called only by system job (no userId check)
- Guards: status is not `PENDING` → throws `PROPOSAL_ALREADY_TERMINAL`
- Transitions to `EXPIRED`

---

# 3. WalkSession Aggregate

## 3.1 States

| State       | Meaning                                                                | Terminal |
| ----------- | ---------------------------------------------------------------------- | -------- |
| `PENDING`   | Created after mutual confirmation. Waiting for both users to activate. | No       |
| `ACTIVE`    | Both users activated within the activation window. GPS tracking on.    | No       |
| `COMPLETED` | Walk finished successfully. Minimum duration was met.                  | ✅ Yes    |
| `NO_SHOW`   | Exactly one participant activated; the other did not act before window closed. | ✅ Yes    |
| `CANCELLED` | Cancelled by a user from PENDING state, or neither participant activated before window closed. | ✅ Yes    |
| `ABORTED`   | Walk stopped mid-session by a participant due to a safety/medical/environmental emergency. | ✅ Yes    |

## 3.2 Valid Transitions

| From        | To          | Trigger                                                     | Guard                                                    |
| ----------- | ----------- | ----------------------------------------------------------- | -------------------------------------------------------- |
| `PENDING`   | `ACTIVE`    | Both users call `activateByUser()` within activation window | Both activations must occur. Time must be within window. |
| `PENDING`   | `CANCELLED` | User cancels                                                | Must be called from `PENDING` only                       |
| `PENDING`   | `NO_SHOW`   | System auto-job                                             | Activation window elapsed without full activation        |
| `ACTIVE`    | `COMPLETED` | User calls `complete()` or auto-complete trigger            | Elapsed time >= 5 minutes (minimum duration)             |
| `ACTIVE`    | `ABORTED`   | User calls `abort()` with an emergency reason               | abortReason ∈ {INJURY, SAFETY, ENVIRONMENT, OTHER}       |
| `COMPLETED` | *(none)*    | —                                                           | Terminal. Immutable.                                     |
| `NO_SHOW`   | *(none)*    | —                                                           | Terminal. Immutable.                                     |
| `CANCELLED` | *(none)*    | —                                                           | Terminal. Immutable.                                     |
| `ABORTED`   | *(none)*    | —                                                           | Terminal. Immutable.                                     |

Forbidden transitions (must throw `DomainException`):
- `ACTIVE` → `NO_SHOW` (by any mechanism — NO_SHOW only comes from PENDING)
- `ACTIVE` → `CANCELLED` (cannot cancel an active walk — use abort() for emergencies)
- `PENDING` → `COMPLETED` (cannot skip ACTIVE)
- Any terminal state → any other state

## 3.3 Activation Window Rules

```
Window opens:  scheduledStart - 15 minutes
Window closes: scheduledStart + 30 minutes

Both userA and userB must call activateByUser() within this window.
If only one user activates and the window closes → NO_SHOW (system job).
If neither user activates and the window closes → system calls cancel() → CANCELLED (system job).
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

| Code                                 | Thrown When                                              | HTTP |
| ------------------------------------ | -------------------------------------------------------- | ---- |
| `SESSION_NOT_FOUND`                  | findById returns empty in application layer              | 404  |
| `SESSION_ALREADY_TERMINAL`           | Any mutation on COMPLETED / NO_SHOW / CANCELLED          | 409  |
| `SESSION_ACTIVATION_WINDOW_EXPIRED`  | activateByUser() called after window close time          | 400  |
| `SESSION_ACTIVATION_WINDOW_NOT_OPEN` | activateByUser() called before window open time          | 400  |
| `SESSION_ALREADY_ACTIVATED_BY_USER`  | Same user calls activateByUser() twice                   | 409  |
| `SESSION_MINIMUM_DURATION_NOT_MET`   | complete() called before 5 minutes have elapsed          | 400  |
| `SESSION_NOT_ACTIVE`                 | complete() or abort() called when state is not ACTIVE    | 409  |
| `SESSION_NOT_PENDING`                | cancel() called when state is not PENDING                | 409  |
| `SESSION_USER_NOT_PARTICIPANT`       | Action by a user who is not participantA or participantB | 403  |
| `SESSION_INVALID_ABORT_REASON`       | abort() called with a reason not in the allowed enum     | 400  |

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

### `WalkSession.cancel(requestingUserId, cancellationTime)`
> Full contract defined in §3.9 (includes penalty tier computation and `CancellationResult` return type).

### `WalkSession.markNoShow()`
- Called only by system job (no userId check)
- Guards: status must be `PENDING` → throws `SESSION_NOT_PENDING`
- Sets status to `NO_SHOW`

### `WalkSession.abort(requestingUserId, abortReason)`
- Guards: status must be `ACTIVE` → throws `SESSION_NOT_ACTIVE`
- Guards: `requestingUserId` must be a participant → throws `SESSION_USER_NOT_PARTICIPANT`
- Guards: `abortReason` must be one of: `INJURY` | `SAFETY` | `ENVIRONMENT` | `OTHER` → throws `SESSION_INVALID_ABORT_REASON`
- Sets status to `ABORTED`, records the abort reason
- Returns void. No penalty tier — ABORTED is treated as a non-penalised emergency stop.

### `WalkSession.isChatWritable()`
- Returns `true` if status is `PENDING` or `ACTIVE`
- Returns `false` for all other states
- No side effects. Never throws.

## 3.9 Cancellation Penalty Policy

Penalty is determined at the moment `cancel()` is called, based on how far the cancellation is from `scheduledStart`.

```
Tier 0 – No penalty:
  (scheduledStart - cancellationTime) > 2 hours

Tier 1 – Light penalty:
  30 minutes < (scheduledStart - cancellationTime) <= 2 hours

Tier 2 – Heavy penalty:
  (scheduledStart - cancellationTime) <= 30 minutes
```

Rules:
- `WalkSession.cancel(requestingUserId, cancellationTime)` computes and **returns** the penalty tier (0, 1, or 2). It does NOT apply TrustScore changes directly.
- The penalty tier is included in the `SessionCancelled` domain event payload.
- The TrustScore aggregate applies the appropriate deduction when it handles the `SessionCancelled` event, keyed on the tier value.
- `WalkSession` has no dependency on `TrustScore` — separation is enforced by the event boundary.

### Updated `WalkSession.cancel(requestingUserId, cancellationTime)` contract
- Guards: status must be `PENDING` → throws `SESSION_NOT_PENDING`
- Guards: `requestingUserId` must be a participant → throws `SESSION_USER_NOT_PARTICIPANT`
- Computes penalty tier from `(scheduledStart - cancellationTime)`
- Sets status to `CANCELLED`
- Returns `CancellationResult` containing the `requestingUserId` and resolved `penaltyTier`

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

| Code                          | Thrown When                                      | HTTP |
| ----------------------------- | ------------------------------------------------ | ---- |
| `USER_NOT_FOUND`              | findById returns empty                           | 404  |
| `USER_INVALID_CREDENTIALS`    | Password does not match during login             | 401  |
| `USER_EMAIL_ALREADY_EXISTS`   | Registration with a duplicate email              | 409  |
| `USER_PHONE_ALREADY_EXISTS`   | Registration with a duplicate phone number       | 409  |
| `USER_DISPLAY_NAME_BLANK`     | displayName is null or empty                     | 400  |
| `USER_ALREADY_PRIVATE`        | Attempt to set PRIVATE mode when already PRIVATE | 409  |
| `USER_ALREADY_PUBLIC`         | Attempt to set PUBLIC mode when already PUBLIC   | 409  |
| `USER_INVALID_EMAIL_FORMAT`   | Email provided but fails format validation       | 400  |

## 4.4 Method Contracts

### `User.register(email, phone, displayName, passwordHash)`
- Validates: email format if provided → throws `USER_INVALID_EMAIL_FORMAT`
- Validates: displayName not blank → throws `USER_DISPLAY_NAME_BLANK`
- Sets `visibilityMode` to `PUBLIC` by default
- Receives an already-hashed password — does NOT call the password hasher itself

### `User.validateCredentials(rawPassword, passwordHasher)`
- Compares `rawPassword` against the stored hash via the `PasswordHasher` interface
- Throws `USER_INVALID_CREDENTIALS` if mismatch
- Does NOT return the hash — only succeeds (returns void) or throws

### `User.setVisibilityMode(mode)`
- Guards: mode is `PUBLIC` and current is `PUBLIC` → throws `USER_ALREADY_PUBLIC`
- Guards: mode is `PRIVATE` and current is `PRIVATE` → throws `USER_ALREADY_PRIVATE`
- Sets `visibilityMode` to the given mode

### `User.updateProfile(displayName, tags)`
- Validates: displayName not blank → throws `USER_DISPLAY_NAME_BLANK`
- Updates allowed public profile fields only (`displayName`, `tags`)
- Does not change email, phone, password, or `visibilityMode`

---

# 5. TrustScore Aggregate

## 5.1 Behavior

TrustScore is updated by domain events, not by direct user action.

| Trigger Event                      | Effect                                                  |
| ---------------------------------- | ------------------------------------------------------- |
| `WalkSessionCompleted`             | Positive contribution                                   |
| `WalkReviewCreated` (5 stars)      | Positive contribution                                   |
| `PartnerNoShowReported`            | Negative contribution                                   |
| `SessionCancelled` (Tier 0)        | No penalty — cancellation was > 2 hours before start    |
| `SessionCancelled` (Tier 1)        | Light negative contribution — 30 min – 2 hours before   |
| `SessionCancelled` (Tier 2)        | Heavy negative contribution — within 30 min of start    |

Penalty tier is carried in the `SessionCancelled` event payload (see §3.9). TrustScore reads the tier and applies the corresponding deduction. Floor at 0 is always enforced.

## 5.2 Invariants

```
1. TrustScore value must never go below 0
2. TrustScore value must never exceed 1000 (ceiling enforced by DB constraint and domain method)
3. TrustScore is never mutated directly by user action — only by events
4. Score changes are applied by TrustScore aggregate methods, not inline in services
```

## 5.3 Error Codes (TrustScoreErrorCode)

| Code                     | Thrown When                                                       | HTTP |
| ------------------------ | ----------------------------------------------------------------- | ---- |
| `TRUST_SCORE_NOT_FOUND`  | findByUserId returns empty                                        | 404  |
| `TRUST_SCORE_BELOW_ZERO` | Direct mutation bypasses `applyNegative()` and would produce < 0 | 400  |

## 5.4 Method Contracts

### Delta Policy Table

| Reason                    | Delta |
| ------------------------- | ----- |
| `SESSION_COMPLETED`       | +10   |
| `FIVE_STAR_REVIEW`        | +5    |
| `NO_SHOW`                 | -20   |
| `LATE_CANCELLATION_TIER1` | -5    |
| `LATE_CANCELLATION_TIER2` | -15   |

Adjust values to product decision. This table is the single source of truth for scoring magnitudes.

### `TrustScore.applyPositive(reason)`
- `reason`: enum — `SESSION_COMPLETED` | `FIVE_STAR_REVIEW`
- Adds the delta defined in the policy table above
- Ceiling enforcement: if `(score + delta) > 1000` → set score to `1000`. Clamps silently.

### `TrustScore.applyNegative(reason)`
- `reason`: enum — `NO_SHOW` | `LATE_CANCELLATION_TIER1` | `LATE_CANCELLATION_TIER2`
- Subtracts the delta defined in the policy table above
- Floor enforcement: if `(score - delta) < 0` → set score to `0`. Never throws for a floor hit — clamps silently.
- `TRUST_SCORE_BELOW_ZERO` is a guard against direct mutation bypassing this method, not thrown here.

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

# 7. UserEmbedding Aggregate

UserEmbedding is a read-model aggregate. It is built from domain events and used exclusively by the AI ranking layer. It never mutates any other aggregate.

## 7.1 States

| State        | Meaning                                          | Condition                    |
| ------------ | ------------------------------------------------ | ---------------------------- |
| `COLD_START` | Insufficient history for personalization         | < 3 completed sessions       |
| `ACTIVE`     | Enough history — vector scoring is enabled       | >= 3 completed sessions      |

## 7.2 Vector Structure

```
Vector(User) = [
  time_preference_pattern,
  average_walk_duration,
  average_speed,
  favorite_tags_distribution,
  reliability_score,
  acceptance_pattern,
  cancellation_pattern
]
```

## 7.3 Invariants

```
1. UserEmbedding never directly affects WalkIntent, MatchProposal, or WalkSession state
2. UserEmbedding is only written by projection/event handlers, never by direct user action
3. A COLD_START embedding must fall back to geo/time/purpose matching — no vector scoring applied
```

## 7.4 Update Triggers

| Domain Event              | Vector Dimension(s) Updated        |
| ------------------------- | ---------------------------------- |
| `WalkSessionCompleted`    | All dimensions                     |
| `WalkReviewCreated`       | `reliability_score`                |
| `FollowRelationCreated`   | `acceptance_pattern`               |
| `PartnerNoShowReported`   | `reliability_score`                |

## 7.5 Method Contracts

### `UserEmbedding.updateFromSessionCompleted(sessionData)`
- Updates all vector dimensions from the completed session metrics (duration, speed, time window, etc.)

### `UserEmbedding.updateFromReview(rating)`
- Updates `reliability_score` dimension based on the star rating value

### `UserEmbedding.updateFromFollow()`
- Updates `acceptance_pattern` dimension (positive signal)

### `UserEmbedding.updateFromNoShow()`
- Updates `reliability_score` dimension (negative signal)

### `UserEmbedding.isReadyForPersonalization()`
- Returns `true` only if status is `ACTIVE` (>= 3 completed sessions)
- No side effects. Never throws.

## 7.6 Error Codes (UserEmbeddingErrorCode)

| Code                  | Thrown When                | HTTP |
| --------------------- | -------------------------- | ---- |
| `EMBEDDING_NOT_FOUND` | findByUserId returns empty | 404  |

---

# 8. How to Use This Document in Vibe Coding

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

## When generating frontend domain code
Paste the relevant aggregate section (states + invariants + error codes + method contracts) into your prompt:
> "Implement `IntentService.createIntent()` on the Android domain layer according to these contracts: [paste section 1.3 invariants + 1.5 method contracts]"

## When generating frontend mappers
Paste the domain model fields alongside the DTO structure:
> "Write `IntentDtoToDomainMapper` using these domain invariants as validation rules: [paste relevant invariants from section 1.3]"

## When a frontend test fails
Same protocol as backend — compare the actual behavior against the relevant section here first. The contract is the referee for both platforms. The backend and Android domain layers must enforce the same invariants.

---

# 9. ChatRoom Aggregate

ChatRoom is created atomically when a WalkSession is created. It enables in-app messaging between the two session participants.

## 9.1 States

| State    | Meaning                                        | Terminal |
| -------- | ---------------------------------------------- | -------- |
| `OPEN`   | Messages can be sent and read.                 | No       |
| `CLOSED` | No new messages permitted. Read-only archive.  | ✅ Yes    |

## 9.2 Invariants

```
1. Exactly one ChatRoom exists per WalkSession — created atomically with the session
2. A ChatRoom transitions to CLOSED when its WalkSession reaches a terminal state
3. Only session participants (user1 and user2) may send messages to the ChatRoom
4. Message content must not be blank (enforced at domain + DB level)
```

## 9.3 Error Codes (ChatRoomErrorCode)

| Code                       | Thrown When                                         | HTTP |
| -------------------------- | --------------------------------------------------- | ---- |
| `CHAT_ROOM_NOT_FOUND`      | findBySessionId returns empty                       | 404  |
| `CHAT_ROOM_CLOSED`         | sendMessage() called on a CLOSED room               | 409  |
| `CHAT_NOT_PARTICIPANT`     | Sender is not a session participant                 | 403  |
| `CHAT_MESSAGE_BLANK`       | Message content is blank after trimming             | 400  |

## 9.4 Method Contracts

### `ChatRoom.sendMessage(senderId, content)`
- Guards: status must be `OPEN` → throws `CHAT_ROOM_CLOSED`
- Guards: `senderId` must be participantA or participantB → throws `CHAT_NOT_PARTICIPANT`
- Guards: `content.trim()` must not be empty → throws `CHAT_MESSAGE_BLANK`
- Creates and persists a new ChatMessage

### `ChatRoom.close()`
- Called only by Domain Service when the associated WalkSession reaches a terminal state
- Guards: status must be `OPEN` (idempotent — already CLOSED is a no-op)
- Sets status to `CLOSED`

---

# 10. WalkReview Aggregate

WalkReview captures post-session feedback. Each participant may leave one review for their partner after a COMPLETED session.

## 10.1 Invariants

```
1. A review can only be created for a WalkSession in COMPLETED state
2. Each participant may submit at most one review per session (one review per reviewer per session)
3. reviewer_id and reviewee_id must be the two distinct participants of the session
4. rating_stars must be between 1 and 5 (inclusive)
5. A 5-star review triggers a TrustScore positive delta for the reviewee (see §5.4)
```

## 10.2 Error Codes (WalkReviewErrorCode)

| Code                          | Thrown When                                                  | HTTP |
| ----------------------------- | ------------------------------------------------------------ | ---- |
| `REVIEW_SESSION_NOT_FOUND`    | Referenced session does not exist                            | 404  |
| `REVIEW_SESSION_NOT_COMPLETE` | Review attempted on a session that is not COMPLETED          | 409  |
| `REVIEW_ALREADY_SUBMITTED`    | This reviewer already submitted a review for this session    | 409  |
| `REVIEW_INVALID_PARTICIPANT`  | reviewer or reviewee is not a session participant            | 403  |
| `REVIEW_INVALID_RATING`       | rating_stars is outside the 1–5 range                       | 400  |

## 10.3 Method Contracts

### `WalkReview.create(sessionId, reviewerId, revieweeId, ratingStars, comment)`
- Guards: session must be `COMPLETED` → throws `REVIEW_SESSION_NOT_COMPLETE`
- Guards: no prior review by reviewerId for this session → throws `REVIEW_ALREADY_SUBMITTED`
- Guards: reviewerId and revieweeId must be the two session participants → throws `REVIEW_INVALID_PARTICIPANT`
- Guards: `ratingStars` ∈ {1, 2, 3, 4, 5} → throws `REVIEW_INVALID_RATING`
- Creates the review record
- **Cross-aggregate side effect (§6):** if ratingStars == 5, Domain Service must call `TrustScore.applyPositive(FIVE_STAR_REVIEW)` for the reviewee

---

# 11. BlockRelation Aggregate

BlockRelation represents a unidirectional block from one user to another. It prevents MatchProposal creation and existing proposals between the two users are invalidated.

## 11.1 Invariants

```
1. A user cannot block themselves
2. A BlockRelation is unidirectional: A blocks B does not imply B blocks A
3. A MatchProposal cannot be created if a BlockRelation exists in either direction between the two users (see §6)
4. When a BlockRelation is created, all PENDING MatchProposals between the two users must be REJECTED
5. A BlockRelation cannot be duplicated (blocker_id + blocked_id must be unique)
```

## 11.2 Error Codes (BlockRelationErrorCode)

| Code                       | Thrown When                                         | HTTP |
| -------------------------- | --------------------------------------------------- | ---- |
| `BLOCK_SELF`               | User attempts to block themselves                   | 400  |
| `BLOCK_ALREADY_EXISTS`     | A BlockRelation already exists for this pair        | 409  |
| `BLOCK_NOT_FOUND`          | Unblock attempted but no relation exists            | 404  |

## 11.3 Method Contracts

### `BlockRelation.create(blockerId, blockedId)`
- Guards: `blockerId != blockedId` → throws `BLOCK_SELF`
- Guards: no existing BlockRelation for this pair → throws `BLOCK_ALREADY_EXISTS`
- Creates the block record
- **Cross-aggregate side effect (§6):** Domain Service must call `rejectByUser()` or `expire()` on all PENDING MatchProposals between the two users

### `BlockRelation.remove(blockerId, blockedId)`
- Guards: a BlockRelation for this pair must exist → throws `BLOCK_NOT_FOUND`
- Deletes the block record
- No cascade — existing WalkSessions are not affected

---

# 12. FollowRelation

FollowRelation represents a unidirectional follow from one user to another. It is a social signal that feeds UserEmbedding's `acceptance_pattern` dimension.

## 12.1 Invariants

```
1. A user cannot follow themselves
2. A FollowRelation is unidirectional: A follows B does not imply B follows A
3. A FollowRelation cannot be duplicated (follower_id + followee_id must be unique)
4. Following is only possible after at least one COMPLETED WalkSession between the two users (see §6 rule 3)
5. When a FollowRelation is created, a FollowRelationCreated event is emitted for UserEmbedding projection
```

## 12.2 Error Codes (FollowRelationErrorCode)

| Code                         | Thrown When                                          | HTTP |
| ---------------------------- | ---------------------------------------------------- | ---- |
| `FOLLOW_SELF`                | User attempts to follow themselves                   | 400  |
| `FOLLOW_ALREADY_EXISTS`      | A FollowRelation already exists for this pair        | 409  |
| `FOLLOW_NOT_FOUND`           | Unfollow attempted but no relation exists            | 404  |
| `FOLLOW_NO_SHARED_SESSION`   | No COMPLETED WalkSession exists between the two users| 403  |

---

# 13. UserPresence

UserPresence tracks a user's real-time availability status for matching. It is a mutable value object updated frequently by the client.

## 13.1 States (presence_status)

| Value      | Meaning                                     |
| ---------- | ------------------------------------------- |
| `ONLINE`   | User is actively in the app                 |
| `OFFLINE`  | User is not in the app or has gone inactive |

## 13.2 States (presence_availability)

| Value         | Meaning                                                |
| ------------- | ------------------------------------------------------ |
| `AVAILABLE`   | User is open to receiving MatchProposals               |
| `UNAVAILABLE` | User is not accepting proposals (busy or opted out)    |

## 13.3 Invariants

```
1. A user has exactly one UserPresence record (created on registration)
2. UserPresence is always updated by the user's own client — never by another user's action
3. expires_at defines when the presence record auto-degrades to OFFLINE/UNAVAILABLE
4. A user in UNAVAILABLE status must not receive new MatchProposals
5. quick_mode = true signals the user wants an immediate match (influences ranking weight)
```

## 13.4 Error Codes (UserPresenceErrorCode)

| Code                        | Thrown When                                  | HTTP |
| --------------------------- | -------------------------------------------- | ---- |
| `PRESENCE_NOT_FOUND`        | findByUserId returns empty                   | 404  |
| `PRESENCE_UPDATE_FORBIDDEN` | Another user attempts to update presence     | 403  |

---

# 14. Notification

Notification is an immutable, append-only record of a system-generated event delivered to a user.

## 14.1 States (notification_status)

| Value    | Meaning                                       |
| -------- | --------------------------------------------- |
| `PENDING`  | Notification created, not yet delivered     |
| `DELIVERED`| Successfully pushed to the user's device   |
| `READ`     | User opened or acknowledged the notification|

## 14.2 Invariants

```
1. Notifications are created only by system events — never by direct user action
2. Notifications are immutable after creation — content cannot be changed
3. Status transitions are one-way: PENDING → DELIVERED → READ
4. A notification payload (jsonb) must always include the triggering entity type and ID
```

## 14.3 Error Codes (NotificationErrorCode)

| Code                    | Thrown When                                     | HTTP |
| ----------------------- | ----------------------------------------------- | ---- |
| `NOTIFICATION_NOT_FOUND`| findById returns empty                          | 404  |
| `NOTIFICATION_ALREADY_READ` | markRead() called on an already-READ notification | 409  |