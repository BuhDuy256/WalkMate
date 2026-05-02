# 03 — Proposal: How "Report" Should Affect AI Matching

## Problem Statement

Today a submitted report has zero impact on the AI matching algorithm. A user who triggers a `NO_SHOW` loses 100 trust points through the gamification system, but a user who receives an **explicit misconduct report** from their partner suffers no matching penalty until a human admin intervenes. The gap is large.

The "Review" use case provides a clean precedent: after a review is saved, two side-effects run — a synchronous trust-score update and an async preference-weight training. The same two-channel architecture can carry Report signals with meaningful adjustments.

---

## Design Principles

1. **Mirror the Review architecture** — no new patterns, same `@Async` + `TrustScorePolicy` path.
2. **Reports are stronger signals than ratings** — a misconduct report should penalize more than a 1-star review (which gives −20).
3. **Reports need a confidence guard** — a single unverified report must not be as powerful as a confirmed pattern, so score changes are staged.
4. **No double-penalization** — if a session is already marked `NO_SHOW` (−100), the report penalty should not stack blindly.
5. **Reviewer weight training on submit** — the reporter is explicitly revealing what matters to them (safety, behavior), so their own preference weights should be updated too.

---

## Proposed Changes

### Channel 1: Trust Score Penalty (Reported User)

Apply a trust-score penalty to the **reported user** analogous to the review path.

#### Penalty Scale by Reason

| Reason | Penalty | Rationale |
|---|---|---|
| `PARTNER_MISCONDUCT` | −30 | Strong behavioral signal |
| `SAFETY_CONCERN` | −50 | Highest severity |
| `EMERGENCY` | 0 | Ambiguous — no penalty (reporter may have caused it) |
| `OTHER` | −10 | Weak/unspecified signal |

#### Duplicate-Report Guard

Because `session_report` enforces `UNIQUE (session_id, reporter_id)`, only one penalty per session per reporter fires. No race conditions.

#### No-Show Double-Penalty Guard

Before applying, check whether `GamificationCommandService` already penalized this session as `NO_SHOW`. If yes, skip the trust deduction (or apply only the delta above −100 if the report reason warrants more than that).

```java
// ReportCommandService.submitReport() — after existing save
if (!gamificationService.wasNoShowPenaltyApplied(session)) {
    int delta = TrustScorePolicy.deltaForReason(request.reason());
    if (delta != 0) {
        User reported = userRepository.findById(reportedUserId);
        int newScore = TrustScorePolicy.apply(reported.getTrustScore(), delta);
        reported.applyTrustScore(newScore);
        userRepository.save(reported);
    }
}
```

**Files to modify:**
- `backend/.../application/report/ReportCommandService.java` — add penalty block inside existing `@Transactional` method
- `backend/.../domain/review/TrustScorePolicy.java` — add `deltaForReason(String reason)` static helper

---

### Channel 2: Reporter's Preference Weight Training (Async)

When a user files a report, they are explicitly signaling that **behavioral safety** matters to them. Train their `matching_preference_model` accordingly.

#### Weight Adjustment

| Condition | Adjustment |
|---|---|
| Any report submitted | `weightBehavior += 0.10` (2× the review tag increment) |
| Safety-concern or misconduct report | additional `weightBehavior += 0.05` |

After adjustment: call `pref.normalize()` → weights re-sum to 1.0.

This makes future searches by the reporter rank candidates with higher trust scores more prominently.

```java
// AiTrainingService.java — new method (async)
@Async
public void trainWeightsFromReport(UUID reporterId, String reason) {
    MatchingPreference pref = matchingPreferenceRepository.findByUserId(reporterId)
            .orElseGet(() -> MatchingPreference.defaultFor(reporterId));

    pref.adjustWeightBehavior(0.10);
    if ("SAFETY_CONCERN".equals(reason) || "PARTNER_MISCONDUCT".equals(reason)) {
        pref.adjustWeightBehavior(0.05);
    }

    pref.normalize();
    pref.updateLastTrainedAt(Instant.now());
    matchingPreferenceRepository.save(pref);
}
```

**Files to modify:**
- `backend/.../application/walkintent/AiTrainingService.java` — add `trainWeightsFromReport()`
- `backend/.../application/report/ReportCommandService.java` — call `aiTrainingService.trainWeightsFromReport()` after save

---

## Updated ReportCommandService Flow (After Proposal)

```
POST /api/v1/sessions/{sessionId}/report
  └─ ReportCommandService.submitReport()  [@Transactional]
       ├─ [existing] All validation guards
       ├─ [existing] SessionReport.create() → reportRepository.save()
       ├─ [NEW - sync] Apply trust penalty to reported user
       │    └─ TrustScorePolicy.deltaForReason(reason) → UPDATE user_account.trust_score
       └─ [NEW - async] aiTrainingService.trainWeightsFromReport(reporterId, reason)
            ├─ Load reporter's MatchingPreference
            ├─ weightBehavior += 0.10 (+ 0.05 if severe reason)
            ├─ normalize()
            └─ UPSERT matching_preference_model
```

---

## Database Impact

No schema changes required. The proposal writes to existing tables only:

| Table | What changes |
|---|---|
| `session_report` | Already written by current code |
| `user_account` | `trust_score` decremented for reported user |
| `matching_preference_model` | `weight_behavior` incremented + normalized for reporter |

---

## How It Flows Into Matching (End State)

With both channels active, the scoring formula stays identical but the inputs shift:

```
TotalScore = (W_time    × S_time)
           + (W_interest × S_tags)
           + (W_behavior × S_trust)

Reported user:   S_trust decreases  →  lower composite score for any caller
Reporter:        W_behavior increases →  S_trust component weighted more in their future searches
```

A repeat offender who collects multiple reports across sessions will have their `S_trust` progressively reduced, making them rank lower in all callers' match results — without requiring admin intervention.

---

## Comparison: Review vs. Report Channels

| Dimension | Review | Report (proposed) |
|---|---|---|
| Trust delta — reviewee | −20 to +10 (star-based) | −10 to −50 (reason-based, one direction) |
| Weight training — reviewer | +0.05 per INTEREST/BEHAVIOR tag | +0.10 to +0.15 flat (behavior only) |
| Execution model | Sync (trust) + Async (weights) | Sync (trust) + Async (weights) |
| Trigger | Session COMPLETED, both statuses OK | Session COMPLETED, partner's status = NO_SHOW |
| New files needed | None | Add `deltaForReason()` to TrustScorePolicy |
| New tables needed | None | None |

---

## Implementation Checklist

### Backend

- [ ] `TrustScorePolicy.java` — add `deltaForReason(String reason)` returning the penalty map above
- [ ] `ReportCommandService.java` — inside `@Transactional` block, after `reportRepository.save()`:
  - Call `TrustScorePolicy.deltaForReason(reason)`, fetch reported user, apply & save
  - Add no-show guard: skip if gamification already applied −100 for this session
- [ ] `ReportCommandService.java` — after transaction, call `aiTrainingService.trainWeightsFromReport(reporterId, reason)`
- [ ] `AiTrainingService.java` — add `@Async trainWeightsFromReport(UUID reporterId, String reason)` method
- [ ] Unit test: `ReportCommandService` — verify trust delta applied per reason, no double-penalty
- [ ] Unit test: `AiTrainingService.trainWeightsFromReport` — verify weight increment + normalization

### Frontend (no changes required)

The frontend already collects `reason` and sends it in `SubmitReportRequest`. The new backend behavior is transparent to the UI.

---

## Key Files Reference

| Action | File |
|---|---|
| Add `deltaForReason()` | `backend/.../domain/review/TrustScorePolicy.java` |
| Add trust penalty + async call | `backend/.../application/report/ReportCommandService.java` |
| Add `trainWeightsFromReport()` | `backend/.../application/walkintent/AiTrainingService.java` |
| Affected at match time | `backend/.../application/walkintent/AiWeightedMatchingStrategy.java` |
| Preference model persistence | `backend/.../infrastructure/repository/walkintent/MatchingPreferenceJdbcRepository.java` |
| User trust persistence | `backend/.../infrastructure/repository/user/UserJdbcRepository.java` |
