# Evaluate Strategy: Is the AI Matching Upgrade Fit for Current State?

This document evaluates every proposed fix from `chatgpt_solution_prevent_halluciate.md` (reviewed in `chatgpt_solution_evaluation.md`) against the **actual, traced codebase** as of 2026-05-08. Each item is graded on fit, effort, and whether it genuinely improves the system.

---

## Executive Summary

After tracing all relevant source files — `AiWeightedMatchingStrategy`, `AiTrainingService`, `MatchingPreference`, `MatchingCommandService`, `ReviewCommandService`, `ReportCommandService`, `AdminReportCommandService`, `WalkIntentJdbcRepository`, and the frontend ViewModels — the proposed 4-layer architecture is **highly coherent with the current DDD-lite structure**. However, several fixes are already partially implemented, and the effort estimates vary significantly.

**Key finding:** The current codebase is already more mature than the proposals assumed. The hard exclusion mechanism (`excluded_user_ids`), the proposal lifecycle guard with pessimistic locking, and the private-intent firewall are already in place. The remaining gaps are concentrated in the **scoring engine** and the **AI learning engine**.

---

## Category A: Already Implemented (No Action Needed)

### A-1. Private Intent Lock from Public Matching
**Proposed fix:** Ensure public matching only queries `PUBLIC + OPEN` intents.

**Verdict: ✅ Already Done.**

The SQL in `WalkIntentJdbcRepository.findOpenCandidates()` (line 270) contains:
```sql
AND (wi.is_private = false OR wi.invited_friend_id = :callerId)
```
Private intents are already excluded from the public pool. A private intent only appears for its invited friend. No action required.

---

### A-2. Prevent Re-match After Reject (Exclusion List)
**Proposed fix:** Add a `match_exclusion` table.

**Verdict: ✅ Already Done via `excluded_user_ids` on `walk_intent`.**

The `walk_intent` table already has an `excluded_user_ids UUID[]` column. `MatchingCommandService.transitionRejectedProposalIntents()` (line 420) already calls:
```java
callerIntent.excludeUser(UUID.fromString(partnerUserId));
```
And `findOpenCandidates` already enforces:
```sql
AND :callerId != ALL(wi.excluded_user_ids)
```
The re-match prevention is fully operational. No new table needed.

---

### A-3. Lifecycle Guard / Transaction Safety
**Proposed fix:** Wrap proposal creation in a transaction with optimistic locking.

**Verdict: ✅ Already Done with pessimistic locking.**

`MatchingCommandService.findOrCreateProposal()` already:
1. Verifies intent is `OPEN` before running the strategy.
2. Uses `findByIdForUpdate()` (SELECT FOR UPDATE) to re-lock both intents after scoring.
3. Re-verifies both are still `OPEN` under the lock before transitioning to `MATCHING`.

This is stricter than the optimistic locking proposed (which would throw on conflict) — pessimistic locking prevents the conflict from occurring in the first place.

---

### A-4. Proposal Timeout Job
**Proposed fix:** Add a `@Scheduled` sweep for expired proposals.

**Verdict: ✅ Already Done.**

`MatchingCommandService.sweepExpiredProposals()` exists and correctly handles both public (→ `OPEN`) and private (→ `CANCELLED`) lifecycle transitions. The `match_proposal.expires_at` column is already populated with `now + 5 minutes` on creation. Verify `@Scheduled` annotation wires it to a cron job in the scheduler config — the method body is correct.

---

## Category B: Trivial Fixes (1–5 lines of code, high impact)

### B-1. Clamp `S_trust` to [0, 100]
**File:** `AiWeightedMatchingStrategy.java:142`

**Problem:** `scoreTrust()` returns `trustScore / 10.0` with no upper bound. If `trust_score > 1000` (which can happen for active, frequently-praised users), `S_trust > 100.0`. Since `S_time` and `S_tags` are hard-capped at 100, this breaks the mathematical meaning of `MAX_WEIGHT_CAP = 0.70`.

**Current code:**
```java
private double scoreTrust(String userId) {
    return userRepository.findById(userId)
            .map(u -> u.getTrustScore() / 10.0)
            .orElse(0.0);
}
```

**Fix:**
```java
private double scoreTrust(String userId) {
    return userRepository.findById(userId)
            .map(u -> Math.min(u.getTrustScore() / 10.0, 100.0))
            .orElse(0.0);
}
```

**Effort:** 1 line. **Priority: CRITICAL.** No DB change. No test regression risk beyond re-checking trust-heavy edge cases.

---

### B-2. Lower Empty Profile Tag Score (50 → 20)
**File:** `AiWeightedMatchingStrategy.java:128`

**Problem:** `scoreTags()` returns `50.0` when both users have empty tag sets. This artificially rewards incomplete profiles and creates a "new user bubble" where tagless users match better against each other than against tagged users.

**Current code:**
```java
if (tagsA.isEmpty() && tagsB.isEmpty()) return 50.0;
```

**Fix:**
```java
if (tagsA.isEmpty() && tagsB.isEmpty()) return 20.0;
if (tagsA.isEmpty() || tagsB.isEmpty()) return 10.0;
```

The partial-empty case (one has tags, one does not) currently falls through to a Jaccard with an empty set, which correctly returns 0. The proposed `10.0` for one-sided empty is therefore optional but adds positive signal differentiation.

**Effort:** 2 lines. **Priority: HIGH.** No DB change. Affects all new-user matching immediately.

---

## Category C: Low-Effort Logic Improvements

### C-1. Time Overlap Ratio vs. Fixed 60-Minute Cap
**File:** `AiWeightedMatchingStrategy.java:119`

**Problem:** `scoreTime()` caps the score at 60 minutes. A user who wants a 3-hour walk gets the same perfect score for a 60-minute candidate as for a 3-hour candidate. This is a meaningful distortion for "long walk" users.

**Current code:**
```java
private double scoreTime(Instant start, Instant end) {
    long overlapMinutes = Duration.between(start, end).toMinutes();
    return Math.min((overlapMinutes / 60.0) * 100.0, 100.0);
}
```

**Fix:** `scoreCandidate()` already has access to both `WalkIntent a` (caller) and `WalkIntent b` (candidate). Pass both time windows to `scoreTime`:

```java
private double scoreTime(WalkIntent a, WalkIntent b, Instant overlapStart, Instant overlapEnd) {
    long overlapMinutes  = Duration.between(overlapStart, overlapEnd).toMinutes();
    long durationA       = Duration.between(a.getTimeWindowStart(), a.getTimeWindowEnd()).toMinutes();
    long durationB       = Duration.between(b.getTimeWindowStart(), b.getTimeWindowEnd()).toMinutes();
    long desiredDuration = Math.min(durationA, durationB);
    if (desiredDuration <= 0) return 0.0;
    return Math.min((overlapMinutes / (double) desiredDuration) * 100.0, 100.0);
}
```

The hard filter already guarantees `overlapMinutes >= MIN_WALK_DURATION`, so the 0-denominator path is only a safety guard.

**Effort:** ~10 lines, one method signature change. **Priority: HIGH.** No DB change.

---

### C-2. Prevent Review Spam / Click-Through Bias
**File:** `AiTrainingService.java:41`

**Problem:** `trainWeightsFromReview()` loops over all selected tags and adds `+0.05` per tag per type. A user selecting 10 INTEREST tags gets `+0.50` to `weightInterest` in one review. A user who taps all tags to dismiss the review screen rapidly corrupts their own matching model.

**Current code:**
```java
for (ReviewTag tag : selectedTags) {
    String type = tag.tagType();
    if (type == null) continue;
    if (type.contains("INTEREST")) {
        pref.adjustWeightInterest(0.05);
    } else if (type.contains("BEHAVIOR")) {
        pref.adjustWeightBehavior(0.05);
    }
}
```

**Fix:** Use boolean gates — one signal per category per review:
```java
boolean hasInterestSignal = false;
boolean hasBehaviorSignal = false;
for (ReviewTag tag : selectedTags) {
    String type = tag.tagType();
    if (type == null) continue;
    if (type.contains("INTEREST")) hasInterestSignal = true;
    else if (type.contains("BEHAVIOR")) hasBehaviorSignal = true;
}
if (hasInterestSignal)  pref.adjustWeightInterest(0.05);
if (hasBehaviorSignal)  pref.adjustWeightBehavior(0.05);
```

**Effort:** ~10 lines. **Priority: HIGH.** No DB change. Immediate noise reduction.

---

### C-3. Floor for `weightTimeOverlap` (MIN_TIME_WEIGHT)
**File:** `MatchingPreference.java:72`

**Problem:** `normalize()` only applies `MAX_WEIGHT_CAP`. Since only `weightInterest` and `weightBehavior` are ever incremented, `weightTimeOverlap` decays systematically toward zero for active users. WalkMate is a time-scheduled app — letting the time overlap weight collapse breaks the core matching premise.

**Fix:** Add a `MIN_TIME_WEIGHT = 0.25` floor in `normalize()`, applied after the cap pass:

```java
public static final double MIN_TIME_WEIGHT  = 0.25;

// After both normalization passes, enforce the floor:
if (weightTimeOverlap < MIN_TIME_WEIGHT) {
    double deficit    = MIN_TIME_WEIGHT - weightTimeOverlap;
    weightTimeOverlap = MIN_TIME_WEIGHT;
    // Distribute deficit proportionally from interest and behavior
    double remaining  = weightInterest + weightBehavior;
    if (remaining > 0) {
        weightInterest -= deficit * (weightInterest / remaining);
        weightBehavior -= deficit * (weightBehavior / remaining);
    }
}
```

**Effort:** ~15 lines within the existing `normalize()` method. **Priority: HIGH.** No DB change.

---

## Category D: Medium-Effort Improvements

### D-1. Provisional vs. Confirmed Report Signal for AI Training
**Files:** `ReportCommandService.java`, `AdminReportCommandService.java`, `AiTrainingService.java`

**Problem:** `ReportCommandService.submitReport()` calls `aiTrainingService.trainWeightsFromReport()` immediately on every report submission. If an Admin later rejects the report in `AdminReportCommandService.resolveReport()`, the trust delta on the **reported user** is correctly reversed, but the **reporter's** AI matching weight bump (`+0.10` to `weightBehavior`) is never rolled back.

**Current flow:**
```
submitReport() → trainWeightsFromReport() [immediate, permanent]
resolveReport(REJECTED) → reverse trust on reported user [but no AI weight rollback]
```

**Proposed fix:** Move the AI weight training from `submitReport` to `resolveReport`:

- On `submitReport`: apply only a small provisional bump (`+0.03`) or skip AI training entirely and only add the user to the reporter's exclusion list.
- On `resolveReport(APPROVED)`: apply the full `+0.10` / `+0.15` signal.
- On `resolveReport(REJECTED)`: reverse the provisional bump if one was applied.

This requires:
1. Adding a `preference_training_event` log table (optional but recommended for auditability and rollback).
2. Injecting `AiTrainingService` into `AdminReportCommandService`.
3. Modifying `AiTrainingService` to support `rollbackWeightsFromReport()`.

**Effort:** Medium. Touches 3 classes. Optional new table. **Priority: HIGH** (correctness issue — false reports silently inflate `weightBehavior`).

---

### D-2. Return Top-N Ranked Candidates (Fallback Matching)
**Files:** `AiWeightedMatchingStrategy.java`, `MatchingStrategy.java`, `MatchingCommandService.java`

**Problem:** `match()` returns only the single best candidate. If that candidate's intent is cancelled or locked by a concurrent transaction between scoring and proposal creation, the entire match attempt fails with no fallback. The current pessimistic lock check at line 122 throws a `DomainException` rather than trying the next candidate.

**Proposed fix:** Change `match()` to return `List<MatchResult>` ordered by score descending. `MatchingCommandService.findOrCreateProposal()` iterates the ranked list and attempts proposal creation for each, skipping candidates that fail the re-verification check.

**Effort:** Medium. Interface change cascades to both the strategy implementation and the orchestrator. **Priority: MEDIUM** — the pessimistic lock already prevents corrupt state; this only improves the no-match rate under concurrent load.

---

## Category E: Low Priority / Out of Scope for Now

### E-1. Weight Decay Toward Default
**Proposed:** Periodic decay `weight = weight * 0.95 + default * 0.05`.

**Verdict:** Deferred. The current system is too new to have accumulated enough training events to cause "personality lock-in". Implement after Phase 2 when the model is being actively trained. Risk of overcomplicating before the core bugs are fixed.

---

### E-2. Advanced Bonuses and Penalties (Friend Bonus, Past Walk Bonus, No-Show Penalty)
**Verdict:** Deferred to Phase 3. The base scoring model must be correct before adding bonuses that could mask or amplify existing errors.

---

## Final Assessment Table

| # | Proposed Fix | Status | Effort | Priority |
|---|---|---|---|---|
| A-1 | Private intent lock from public pool | ✅ Already done | — | — |
| A-2 | Re-match exclusion after reject | ✅ Already done | — | — |
| A-3 | Lifecycle guard / pessimistic locking | ✅ Already done | — | — |
| A-4 | Proposal timeout sweep | ✅ Already done | — | — |
| B-1 | Clamp `S_trust` to [0, 100] | ❌ Missing | 1 line | CRITICAL |
| B-2 | Empty profile tag score 50 → 20 | ❌ Missing | 2 lines | HIGH |
| C-1 | Time overlap ratio vs fixed 60 min | ❌ Missing | ~10 lines | HIGH |
| C-2 | Anti-spam: 1 signal per tag category | ❌ Missing | ~10 lines | HIGH |
| C-3 | MIN_TIME_WEIGHT = 0.25 floor | ❌ Missing | ~15 lines | HIGH |
| D-1 | Provisional report signal + rollback | ❌ Missing | Medium | HIGH |
| D-2 | Top-N ranked candidates with fallback | ❌ Missing | Medium | MEDIUM |
| E-1 | Weight decay toward default | Deferred | — | LOW |
| E-2 | Bonus/penalty scoring extensions | Deferred | — | LOW |

**Bottom line:** 4 of 13 proposed fixes are already in production code. The remaining critical/high-priority fixes are concentrated in `AiWeightedMatchingStrategy` (3 methods) and `AiTrainingService` (2 methods) with no DB schema changes for Phase 1. The upgrade is **highly compatible** with the current architecture and can be shipped incrementally.
