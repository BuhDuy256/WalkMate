# Implementation Plan: AI Matching Improvement

This plan details the concrete steps to improve the AI Matching engine in WalkMate. It is derived from the evaluation in `evaluate_strategy.md` and maps directly to current source files.

---

## Overview

The improvements are split into two phases:

| Phase | Focus | DB Change? | Risk |
|---|---|---|---|
| **Phase 1** | Fix scoring bugs in `AiWeightedMatchingStrategy` + `MatchingPreference` | None | Low |
| **Phase 2** | Fix AI learning bugs in `AiTrainingService` + `AdminReportCommandService` | Optional (audit log table) | Medium |

Phase 3 (bonuses, decay, top-N fallback) is documented as a roadmap item, not an immediate task.

---

## Phase 1 — Scoring Engine Fixes

All changes in this phase are **backend-only, no DB migration required**.

### Task 1.1 — Clamp `S_trust` to [0, 100]

**File:** `backend/src/main/java/com/walkmate/application/walkintent/AiWeightedMatchingStrategy.java`

**Change:** Update `scoreTrust()` at line 142.

```java
// Before
private double scoreTrust(String userId) {
    return userRepository.findById(userId)
            .map(u -> u.getTrustScore() / 10.0)
            .orElse(0.0);
}

// After
private double scoreTrust(String userId) {
    return userRepository.findById(userId)
            .map(u -> Math.min(u.getTrustScore() / 10.0, 100.0))
            .orElse(0.0);
}
```

**Why this matters:** Without the clamp, a user with `trust_score = 1200` produces `S_trust = 120`, which exceeds the `MAX_WEIGHT_CAP = 0.70` ceiling mathematically. All three score components must be in [0, 100] for the weight formula to be meaningful.

**Test:** Add a unit test where `trust_score = 1500` and assert `scoreTrust` returns `100.0`.

---

### Task 1.2 — Fix Time Overlap Score (Ratio-Based, Not 60-Min Cap)

**File:** `backend/src/main/java/com/walkmate/application/walkintent/AiWeightedMatchingStrategy.java`

**Change:** Update `scoreTime()` and its call site in `scoreCandidate()`.

```java
// In scoreCandidate() — pass both intents to scoreTime
private ScoredResult scoreCandidate(WalkIntent a, WalkIntent b,
                                     MatchingPreference pref, List<String> tagsA) {
    Instant overlapStart = a.getTimeWindowStart().isAfter(b.getTimeWindowStart())
            ? a.getTimeWindowStart() : b.getTimeWindowStart();
    Instant overlapEnd = a.getTimeWindowEnd().isBefore(b.getTimeWindowEnd())
            ? a.getTimeWindowEnd() : b.getTimeWindowEnd();

    double sTime  = scoreTime(a, b, overlapStart, overlapEnd);  // updated signature
    double sTags  = scoreTags(tagsA, b);
    double sTrust = scoreTrust(b.getUserId());

    double total = (pref.getWeightTimeOverlap() * sTime)
                 + (pref.getWeightInterest()    * sTags)
                 + (pref.getWeightBehavior()    * sTrust);

    return new ScoredResult(b, overlapStart, overlapEnd, total);
}

// Updated scoreTime — ratio-based against the shorter desired duration
private double scoreTime(WalkIntent a, WalkIntent b, Instant overlapStart, Instant overlapEnd) {
    long overlapMinutes  = Duration.between(overlapStart, overlapEnd).toMinutes();
    long durationA       = Duration.between(a.getTimeWindowStart(), a.getTimeWindowEnd()).toMinutes();
    long durationB       = Duration.between(b.getTimeWindowStart(), b.getTimeWindowEnd()).toMinutes();
    long desiredDuration = Math.min(durationA, durationB);
    if (desiredDuration <= 0) return 0.0;
    return Math.min((overlapMinutes / (double) desiredDuration) * 100.0, 100.0);
}
```

**Why this matters:** A user wanting a 3-hour walk should prefer a 3-hour overlap over a 1-hour overlap. The old formula treated both identically (both scored 100). The new formula gives the 3-hour overlap `100.0` and the 1-hour overlap `33.3`, enabling meaningful differentiation for long-walk users.

**Behavioural note:** The hard filter already guarantees `overlapMinutes >= MIN_WALK_DURATION`, so all candidates reaching this scorer already have a valid overlap. The `desiredDuration <= 0` guard is defensive only.

**Test cases:**
- `durationA = 180 min`, `durationB = 60 min`, `overlap = 60 min` → `S_time = 100.0` (60/60 = 1.0)
- `durationA = 180 min`, `durationB = 180 min`, `overlap = 60 min` → `S_time = 33.3`
- `durationA = 60 min`, `durationB = 60 min`, `overlap = 60 min` → `S_time = 100.0`

---

### Task 1.3 — Lower Empty Profile Tag Score (50 → 20 / 10)

**File:** `backend/src/main/java/com/walkmate/application/walkintent/AiWeightedMatchingStrategy.java`

**Change:** Update `scoreTags()` at line 128.

```java
// Before
if (tagsA.isEmpty() && tagsB.isEmpty()) return 50.0;

// After
if (tagsA.isEmpty() && tagsB.isEmpty()) return 20.0;
if (tagsA.isEmpty() || tagsB.isEmpty()) return 10.0;
```

**Why this matters:** Returning `50.0` for two tagless users creates an unintended "empty profile bonus" — they score higher against each other than against tagged users with low Jaccard similarity. `20.0` still allows new users to match (avoids zero-score isolation) while signalling that completing the profile is beneficial.

**Note:** The one-sided empty case (`tagsA.isEmpty() || tagsB.isEmpty()`) currently falls through to Jaccard with an empty set which returns `0.0` (correct). Adding `return 10.0` gives a small base signal — keep or drop depending on product decision. The `0.0` behavior is acceptable but very harsh on new users matched against existing ones.

**Test:** Assert `scoreTags([], [])` returns `20.0`; assert `scoreTags([], ["hiking"])` returns `10.0`.

---

### Task 1.4 — Add `MIN_TIME_WEIGHT` Floor in `MatchingPreference.normalize()`

**File:** `backend/src/main/java/com/walkmate/domain/walkintent/MatchingPreference.java`

**Change:** Add a `MIN_TIME_WEIGHT` constant and enforce it after both normalization passes.

```java
// Add constant alongside MAX_WEIGHT_CAP
public static final double MAX_WEIGHT_CAP  = 0.70;
public static final double MIN_TIME_WEIGHT = 0.25;

// At the END of normalize(), after Pass 2
// Pass 3 — enforce minimum floor for time overlap
if (weightTimeOverlap < MIN_TIME_WEIGHT) {
    double deficit    = MIN_TIME_WEIGHT - weightTimeOverlap;
    weightTimeOverlap = MIN_TIME_WEIGHT;
    double subSum     = weightInterest + weightBehavior;
    if (subSum > 0) {
        weightInterest -= deficit * (weightInterest / subSum);
        weightBehavior -= deficit * (weightBehavior / subSum);
    } else {
        // Edge case: both are zero (should not happen after Pass 1)
        weightInterest = (1.0 - MIN_TIME_WEIGHT) / 2.0;
        weightBehavior = (1.0 - MIN_TIME_WEIGHT) / 2.0;
    }
}
```

**Why this matters:** `AiTrainingService` only increments `weightInterest` and `weightBehavior`. After repeated normalization cycles, `weightTimeOverlap` decays toward `0`. For a walking-appointment app where time scheduling is fundamental, letting the time weight collapse makes the matching engine forget its core purpose. The floor at `0.25` ensures time overlap always contributes at least 25% of the score.

**Invariant check:** After Pass 3, `weightTimeOverlap + weightInterest + weightBehavior` must still equal `1.0`. The redistribution from interest/behavior preserves this.

**Test:** Simulate 100 reviews with INTEREST tags only → assert `getWeightTimeOverlap()` never drops below `0.25`.

---

## Phase 2 — AI Learning Engine Fixes

### Task 2.1 — Anti-Spam: One Signal Per Tag Category Per Review

**File:** `backend/src/main/java/com/walkmate/application/walkintent/AiTrainingService.java`

**Change:** Replace the per-tag loop in `trainWeightsFromReview()` with a per-category boolean gate.

```java
@Async
public void trainWeightsFromReview(UUID reviewerId, List<ReviewTag> selectedTags) {
    if (selectedTags == null || selectedTags.isEmpty()) return;

    try {
        MatchingPreference pref = matchingPreferenceRepository.findByUserId(reviewerId)
                .orElseGet(() -> MatchingPreference.defaultFor(reviewerId));

        boolean hasInterestSignal = false;
        boolean hasBehaviorSignal = false;

        for (ReviewTag tag : selectedTags) {
            String type = tag.tagType();
            if (type == null) continue;
            if (type.contains("INTEREST"))  hasInterestSignal = true;
            else if (type.contains("BEHAVIOR")) hasBehaviorSignal = true;
        }

        if (hasInterestSignal)  pref.adjustWeightInterest(0.05);
        if (hasBehaviorSignal)  pref.adjustWeightBehavior(0.05);

        pref.normalize();
        pref.updateLastTrainedAt(Instant.now());
        matchingPreferenceRepository.save(pref);

        log.debug("AI weights updated from review: reviewer={} timeOverlap={:.3f} interest={:.3f} behavior={:.3f}",
                reviewerId, pref.getWeightTimeOverlap(), pref.getWeightInterest(), pref.getWeightBehavior());
    } catch (Exception e) {
        log.warn("AI weight training failed for reviewer={}: {}", reviewerId, e.getMessage());
    }
}
```

**Why this matters:** The old loop gave `+0.50` for 10 INTEREST tags, `+0.35` for 7. The new logic gives a flat `+0.05` regardless of how many tags were selected. A user dismissing the review by selecting all tags will still train `+0.05`, not `+0.50`.

---

### Task 2.2 — Provisional Report Signal: Move AI Training to Admin Resolution

**Files:**
- `backend/src/main/java/com/walkmate/application/report/ReportCommandService.java`
- `backend/src/main/java/com/walkmate/application/report/AdminReportCommandService.java`
- `backend/src/main/java/com/walkmate/application/walkintent/AiTrainingService.java`

**Problem:** `ReportCommandService.submitReport()` (line 121) fires `aiTrainingService.trainWeightsFromReport()` immediately. When an Admin later rejects the report in `AdminReportCommandService.resolveReport()`, the trust delta on the *reported user* is correctly reversed, but the *reporter's* AI weight bump is never rolled back.

**Step 1 — Remove AI training from `submitReport`:**

In `ReportCommandService.java`, remove or comment out line 121:
```java
// Remove:
aiTrainingService.trainWeightsFromReport(UUID.fromString(reporterId), reason);
```

**Step 2 — Inject `AiTrainingService` into `AdminReportCommandService` and fire on resolution:**

```java
@Service
@RequiredArgsConstructor
public class AdminReportCommandService {

    private final SessionReportRepository reportRepository;
    private final UserRepository          userRepository;
    private final AiTrainingService       aiTrainingService;  // add injection

    @Transactional
    public SessionReport resolveReport(String reportId, String adminUserId,
                                       String resolution, String note) {
        SessionReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new DomainException(ReportErrorCode.REPORT_NOT_FOUND));

        if (!"APPROVED".equals(resolution) && !"REJECTED".equals(resolution)) {
            throw new DomainException(ReportErrorCode.REPORT_INVALID_RESOLUTION);
        }
        if (report.isResolved()) {
            throw new DomainException(ReportErrorCode.REPORT_ALREADY_RESOLVED);
        }

        if ("APPROVED".equals(resolution)) {
            report.approve(adminUserId, note);
            reportRepository.update(report);

            // ── AI training fires only on admin approval ──────────────────
            aiTrainingService.trainWeightsFromReport(
                    UUID.fromString(report.getReporterId()), report.getReason());

        } else {
            // REJECTED — reverse trust delta on reported user (existing logic)
            report.reject(adminUserId, note);
            reportRepository.update(report);

            if (report.getAppliedTrustDelta() < 0) {
                User reportedUser = userRepository.findById(report.getReportedUserId())
                        .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));
                int newScore = TrustScorePolicy.apply(
                        reportedUser.getTrustScore(), -report.getAppliedTrustDelta());
                reportedUser.applyTrustScore(newScore);
                userRepository.save(reportedUser);
            }
            // No AI weight change on rejection — reporter's model stays clean
        }

        return report;
    }
}
```

**Why this matters:** Currently a malicious user can file a false `SAFETY_CONCERN` report to force `weightBehavior += 0.15` on their own profile permanently. After this fix, only admin-approved reports train the model. The behavior is now symmetric: trust reversal on rejected reports was already implemented; AI weight training needs the same gating.

**Note on `AiTrainingService` injection in `AdminReportCommandService`:** Both are `@Service` beans — no circular dependency risk. `AiTrainingService` depends only on `MatchingPreferenceRepository`.

---

## Phase 3 — Roadmap (Deferred)

These items are architecturally sound but should not be implemented until Phase 1 and 2 are stable and the model is receiving real training signals from users.

| Item | Description | Prerequisite |
|---|---|---|
| Top-N ranked fallback | Change `match()` to return `List<MatchResult>`, orchestrator iterates | Phase 1 complete |
| Weight decay toward default | Periodic decay: `w = w * 0.95 + default * 0.05` | Active user base with training data |
| Friend bonus in scoring | `+5–10` if candidate is in friends list | Stable base formula |
| Past successful walk bonus | `+5–10` if prior completed session with candidate | Session history lookup added |
| `preference_training_event` audit log | Track all weight adjustments with status (APPLIED / REVERTED) | Phase 2 complete, for production debugging |
| Explainable match UI | Surface "Matched because: shared time, similar interests, high trust" | All scoring fixes complete |

---

## Implementation Checklist

### Phase 1 (All backend, no DB changes)

- [ ] **1.1** `AiWeightedMatchingStrategy.scoreTrust()` — add `Math.min(..., 100.0)` clamp
- [ ] **1.2** `AiWeightedMatchingStrategy.scoreTime()` — change to ratio-based formula; update call site in `scoreCandidate()`
- [ ] **1.3** `AiWeightedMatchingStrategy.scoreTags()` — change empty-both to `20.0`, add one-sided empty `10.0`
- [ ] **1.4** `MatchingPreference.normalize()` — add `MIN_TIME_WEIGHT = 0.25` floor as Pass 3
- [ ] Write/update unit tests for all four scoring methods
- [ ] Verify `AiWeightedMatchingStrategyTest` (if exists) still passes

### Phase 2

- [ ] **2.1** `AiTrainingService.trainWeightsFromReview()` — replace per-tag loop with boolean gate
- [ ] **2.2a** `ReportCommandService.submitReport()` — remove `aiTrainingService.trainWeightsFromReport()` call
- [ ] **2.2b** `AdminReportCommandService` — inject `AiTrainingService`, call `trainWeightsFromReport()` only on APPROVED resolution
- [ ] Write integration test: submit report → admin reject → assert reporter's `weightBehavior` unchanged
- [ ] Write integration test: submit report → admin approve → assert reporter's `weightBehavior` incremented
