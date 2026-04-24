# WalkMate Trust Score Lifecycle - Technical Audit Report

---

## Section 1: Data Flow & Mutation Tracing

### 1.1 Initialization

`trustScore` is initialized to `0` in two places in the domain factory:

- `User.register()` - `User.java:70`
- `User.registerWithGoogle()` - `User.java:96`

The `user_account` DB column matches: `trust_score INTEGER NOT NULL DEFAULT 0` (`V1__init.sql:64`).

Critical divergence - orphaned table: The DB also has a completely separate `trust_score` table (`V1__init.sql:128-141`) initialized at `DEFAULT 100`, with columns for `completed_sessions`, `cancelled_sessions`, `no_show_sessions`, etc. No Java code in `UserJdbcRepository` (or anywhere else) ever reads from or writes to this table. It is a ghost schema.

`V102__adjust_trust_score.sql` even adds a `total_distance_km` column to it, compounding the orphaned state.

### 1.2 All Write Operations

There are three mutation paths. Only one is currently functional.

#### Path A - `GamificationCommandService.onSessionCompleted()` (ACTIVE, but incomplete)

Triggered by `SessionCompletedEvent`, published in `SessionCommandService.completeSession()` (line 156) and `handleExpiredSessions()` (line 222).

The listener calls:

`rewardBothParticipants()` -> `rewardUser()` -> `user.applySessionReward(points, distanceKm)`

Checking `User.applySessionReward()` (`User.java:159-163`):

```java
public void applySessionReward(int points, double distanceKm) {
    this.totalPoints       += points;
    this.totalDistanceKm   += distanceKm;
    this.completedSessions += 1;
}
```

`trustScore` is never touched here. The `+5` delta from `SessionOutcome.COMPLETED` exists in the enum but this path does not call `TrustScorePolicy.apply()`. The COMPLETED trust reward is entirely missing from the event-driven flow.

There is also a secondary data integrity problem here: `V102` dropped `total_distance_km` and `completed_sessions` from `user_account`, and `UserJdbcRepository.save()` does not include those columns in its `INSERT`/`UPDATE` (`UserJdbcRepository.java:83-106`). The in-memory increments from `applySessionReward()` are silently discarded on every persist. The stats API (`GET /users/{id}/stats`) will always return `0` for distance and completed sessions.

#### Path B - `GamificationCommandService.onSessionNoShow()` (DEAD CODE)

The listener at `GamificationCommandService.java:59` correctly calls `applyPenalty(userId, SessionOutcome.NO_SHOW)`, which calls `TrustScorePolicy.apply()` and `user.applyTrustScore()`.

However, `SessionNoShowEvent` is never published anywhere in the codebase. A global search for `publishEvent.*NoShow` returns zero matches. `SessionCommandService` handles the `NO_SHOW` status transition in the domain model (`WalkSession.markNoShow()` etc.) but never fires the event. The entire no-show penalty path is dead code.

#### Path C - `ReviewCommandService.submitReview()` (ACTIVE, but logically broken)

This is the only path that currently writes to `trustScore`. It runs in a single transaction:

1. Loads and verifies the session (step 1-3)
2. Determines the reviewee (step 4)
3. Saves the review row (step 5)
4. Step 6: Calls `TrustScorePolicy.apply(reviewee.getTrustScore(), toOutcome(session.getStatus()))` (`ReviewCommandService.java:84`)

`toOutcome()` maps:

- `NO_SHOW` -> `SessionOutcome.NO_SHOW` -> delta `-20`
- `CANCELLED` -> `SessionOutcome.CANCELLED` -> delta `-5`
- `default` -> `SessionOutcome.COMPLETED` -> delta `+5`

The `ratingStars` parameter (1-5) has absolutely zero effect on the trust score. A 1-star review on a completed session applies `+5`. A 5-star review applies `+5`. The star rating is persisted to the `walk_review` table but is never read back for scoring purposes. The trust score adjustment triggered by a review is purely a function of session terminal status, not the review's rating.

Additionally, there is a commented-out status guard on line 53:

```java
// if (session.getStatus() != SessionStatus.COMPLETED) { ... }
```

This means a review can currently be submitted on any session status including `CANCELLED` and `NO_SHOW`, which applies the penalty delta a second time if the no-show event path were ever un-stubbed.

### 1.3 Are the Events Orchestrating Real Changes?

| Event | Published? | Listener exists? | Trust score mutated? |
|---|---|---|---|
| SessionCompletedEvent | Yes (2 sites) | Yes (`GamificationCommandService`) | No - listener only updates points/distance, not trust score |
| SessionNoShowEvent | Never published | Yes (`GamificationCommandService`) | N/A - dead code |

The events are not stubs in the traditional sense - the infrastructure is wired and the listener is real - but the business logic inside the COMPLETED listener omits the trust score adjustment, and the NO_SHOW event has no publisher.

---

## Section 2: Consumption & Coupling - The Matching Engine

`RuleBasedMatchingStrategy` is the active `@Primary` component. It implements a two-stage process:

- Stage 1 (`findCandidates`): Pure DB filter - hotspot, time window, age constraints, block list. No trust score column in the query.
- Stage 2 (`match + buildResult`): In-memory scoring. The only active signal is overlap duration (`WEIGHT_OVERLAP_PER_MINUTE = 1`).

```java
// TODO (AI Upgrade - Trust):   totalScore += scoreTrustLevel(b.getUserId());
// TODO (AI Upgrade - NoShow):  totalScore += penalizeNoShowHistory(b.getUserId());
```

Both trust-related scoring methods are stub comments with no implementation. `MatchingCommandService` delegates entirely to `MatchingStrategy` and adds no trust logic of its own.

Verdict: Trust score has zero effect on the matching engine today. It is recorded but never read by any matching path.

---

## Section 3: Frontend Integration

### 3.1 How `trustScore` Reaches the UI

There are two independent sources for `trustScore` on the Profile screen, which creates a subtle correctness issue:

Source 1 - Base profile API (`GET /api/v1/profile/me` or equivalent): Returns `UserProfileResponse` which includes `trustScore` from `user_account.trust_score`. This is the source actually used to populate `ProfileUiState.trustScore` (`ProfileViewModel.java:250`):

```java
(float) profile.getTrustScore(),  // from UserProfileResponse, not UserStats
```

Source 2 - Stats API (`GET /api/v1/users/{id}/stats`): Returns `UserStatsResponse` with `trustScore` from `GamificationController.getStats()` (`GamificationController.java:55-58`), which reads the same `user_account.trust_score` field. The `statsHolder` is loaded in parallel, but the ViewModel explicitly falls back to `profile.getTrustScore()` for the score displayed - `userStats.getTrustScore()` is available in `UserStats` but not forwarded to `ProfileUiState`.

`PostSessionSummaryViewModel` loads `UserStats` which does carry `trustScore` (`UserStats.java:9`), but `PostSessionSummaryUiState` has no dedicated `trustScoreDelta` field - the screen receives the full stats object and can display the current absolute score, but there is no infrastructure for a "you gained/lost N points" message.

### 3.2 Is the UI Reactive to Score Changes?

No. The flow is request-driven, not push-driven:

- `ProfileFragment` calls `loadProfile()` on resume/create. There is no WebSocket subscription, polling mechanism, or invalidation signal wired to trust score changes.
- After `ReviewCommandService.submitReview()` mutates the score on the backend, the frontend only sees the new value on the next full `loadProfile()` call - typically the next time the user navigates to the Profile tab.
- `PostSessionSummaryViewModel.loadUserSummary()` fetches a fresh `UserStats` snapshot, so it would reflect post-session score state, but only for the points/distance/sessions values (which are currently always `0` due to the `V102` DB migration issue).

---

## Section 4: Gap Analysis vs. Target Architecture

### 4.1 Stage 1 - System-Driven Deltas

| Scenario | Target Delta | Current Delta | Current Path | Status |
|---|---:|---:|---|---|
| Session COMPLETED | +5 | +5 (defined in enum) | Never applied via events | Missing publisher call in event listener |
| Session CANCELLED | -15 to -30 | -5 | Never applied (no event exists for CANCELLED) | Wrong value + no trigger |
| Session NO_SHOW | -100 | -20 | Event listener exists but event never published | Wrong value + dead publisher |

Components that need changes:

1. `SessionOutcome.java` - Wrong delta values for `NO_SHOW` (`-20 -> -100`) and `CANCELLED` (`-5 -> target range`). Slight modification.
2. `GamificationCommandService.onSessionCompleted()` - Must call `TrustScorePolicy.apply()` for both participants in `rewardBothParticipants()`. Currently `rewardUser()` calls `applySessionReward()` which skips trust score entirely. Slight modification to `rewardUser()`.
3. `SessionCommandService` - Must publish `SessionNoShowEvent` when `WalkSession.markNoShow()` transitions occur. There is no `markNoShow()` call-site in `SessionCommandService` today; the `NO_SHOW` state is set via the domain model but the controller/service that surfaces it needs to be identified and wired. Moderate addition.
4. `SessionCommandService.handleExpiredSessions()` / `cancelSession()` - Needs to publish a cancellation event (or directly call `GamificationCommandService`) when `PENDING` sessions auto-cancel or are user-cancelled. Currently no trust penalty fires for cancellations at all. New event + publisher call required.
5. `UserJdbcRepository.save()` - The orphaned in-memory increments to `completedSessions` and `totalDistanceKm` need to route to the correct table (either restore columns to `user_account` or wire the `trust_score` table). This is a prerequisite for meaningful stats display.

### 4.2 Stage 2 - Review-Driven, Non-Linear Adjustments

This stage requires a complete rewrite of the scoring logic inside `ReviewCommandService`.

What needs to happen:

1. New `RatingAdjustmentPolicy` (domain layer, analogous to `TrustScorePolicy`): A pure static class mapping `ratingStars` (1-5) -> delta with a non-linear curve. For example:
   - 5 stars -> +10
   - 4 stars -> +5
   - 3 stars -> 0
   - 2 stars -> -10
   - 1 star -> -20
   The exact curve is a product decision, but the policy class needs to exist.
2. `ReviewCommandService.submitReview()` - Step 6 must be replaced: instead of `toOutcome(session.getStatus())`, it should call `RatingAdjustmentPolicy.apply(reviewee.getTrustScore(), ratingStars)`. The current `toOutcome()` helper becomes unused and should be removed.
3. `TrustScorePolicy.apply()` - Currently takes a `SessionOutcome` (system event). For Stage 2, the signature needs a second entry point that accepts a raw delta integer, or `RatingAdjustmentPolicy` can call `TrustScorePolicy` internally using the same bounds-clamping logic. The bounds (`MIN=0`, `MAX=1000`) are correct and should be reused.
4. The commented-out status guard in `ReviewCommandService.java:53` must be uncommented to prevent reviews (and score mutations) on non-`COMPLETED` sessions.

### 4.3 The Orphaned `trust_score` Table

This table was intended as a normalized stats ledger but is architecturally abandoned. Decision required before implementation:

- Option A (Recommended): Drop the `trust_score` table entirely. Keep `trust_score` denormalized on `user_account` (current behavior). Restore `completed_sessions` and `total_distance_km` to `user_account` (reverse part of `V102`) so the reward path can persist them.
- Option B: Fully adopt the `trust_score` table as the canonical stats store. Rewrite `UserJdbcRepository` to JOIN against it, write a `TrustScoreJdbcRepository`, and update `GamificationCommandService` to write to both. This is the "correct" normalized design but requires significant plumbing.

Leaving the current state - where `V102` dropped columns that the Java code still tries to increment - causes silent data loss on every session completion reward.

### 4.4 Summary: Rewrite vs. Modify

| Component | Action Required |
|---|---|
| `SessionOutcome.java` | Modify - update delta values |
| `GamificationCommandService.onSessionCompleted()` | Modify - add `TrustScorePolicy.apply()` call for trust reward |
| `SessionCommandService.cancelSession()` / `handleExpiredSessions()` | Modify - publish cancellation trust event |
| `SessionCommandService` (no-show path) | Modify - find NO_SHOW trigger site, publish `SessionNoShowEvent` |
| `ReviewCommandService.submitReview()` | Rewrite step 6 - replace `toOutcome(status)` with `RatingAdjustmentPolicy.apply(stars)`, uncomment status guard |
| `RatingAdjustmentPolicy.java` | New file - non-linear rating-to-delta mapping |
| `RuleBasedMatchingStrategy.scoreTrustLevel()` | New method - implement the stubbed TODO |
| `trust_score` table / `V102` migration | Schema decision required - currently causes silent data loss |
| `PostSessionSummaryUiState` | Add `trustScoreDelta` field for before/after display |
| `Frontend ProfileViewModel` | No structural change, but score refresh needs trigger after review submit |