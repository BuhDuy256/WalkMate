# 01 — How "Review" Affects AI Matching

## Overview

Submitting a review triggers **two independent side-effects** that feed back into the AI matching pipeline:

| Side-Effect | Target | Direction |
|---|---|---|
| Trust-score update | **Reviewee's** `user_account.trust_score` | Global — visible to all future callers |
| Preference weight training | **Reviewer's** `matching_preference_model` | Personal — affects only the reviewer's scoring weights |

---

## Full Data Flow

```
User submits review (stars + tags)
  └─ ReviewViewModel.submitReview()                          [Frontend]
       └─ ReviewRepositoryImpl → POST /api/v1/sessions/{id}/review
            └─ ReviewController.submitReview()               [Presentation]
                 └─ ReviewCommandService.submitReview()      [@Transactional, Application]
                      ├─ [1] Persist WalkReview → walk_review table
                      ├─ [2] Update reviewee trust score ← MATCHING INPUT
                      │        TrustScorePolicy.apply(current, ratingDelta(stars))
                      │        UPDATE user_account.trust_score
                      ├─ [3] Evaluate badges
                      └─ [4] @Async → AiTrainingService.trainWeightsFromReview()
                                  ├─ Load reviewer's MatchingPreference
                                  ├─ INTEREST tags → weightInterest += 0.05
                                  ├─ BEHAVIOR tags → weightBehavior += 0.05
                                  ├─ normalize() → all 3 weights sum to 1.0
                                  └─ UPSERT matching_preference_model ← MATCHING INPUT
```

---

## Side-Effect 1: Trust Score Update (Reviewee)

**File:** `backend/src/main/java/com/walkmate/application/review/ReviewCommandService.java`

After saving the review, the command service reads the delta from a non-linear curve, applies `TrustScorePolicy`, and persists the new score to `user_account`.

### Rating → Trust-Score Delta

| Stars | Delta |
|---|---|
| 5 ★ | +10 |
| 4 ★ | +5 |
| 3 ★ | 0 |
| 2 ★ | −10 |
| 1 ★ | −20 |

**Policy bounds:** `TrustScorePolicy.apply()` clamps the result to `[0, 1000]`.

**File:** `backend/src/main/java/com/walkmate/domain/review/TrustScorePolicy.java`

```java
public static int apply(int currentScore, int delta) {
    return Math.max(0, Math.min(1000, currentScore + delta));
}
```

### How It Enters Matching

At match time, `AiWeightedMatchingStrategy.scoreTrust()` reads the candidate's live `trust_score` and maps it to a `[0, 100]` score component:

```java
// AiWeightedMatchingStrategy.java
private double scoreTrust(String userId) {
    return userRepository.findById(userId)
            .map(u -> u.getTrustScore() / 10.0)
            .orElse(0.0);
}
// S_trust = trustScore / 10  →  range [0, 100]
```

**File:** `backend/src/main/java/com/walkmate/application/walkintent/AiWeightedMatchingStrategy.java`

A reviewee whose trust score rises from 500 → 510 gains `+1.0` to `S_trust`. When multiplied by the caller's `weight_behavior`, this increments the final composite score.

---

## Side-Effect 2: Preference Weight Training (Reviewer)

**File:** `backend/src/main/java/com/walkmate/application/walkintent/AiTrainingService.java`

Runs `@Async` so it never blocks the HTTP response. Adjusts the **reviewer's own** matching weights based on the tags they selected.

### Tag Type → Weight Adjusted

| Tag type prefix | Weight adjusted |
|---|---|
| `POSITIVE_INTEREST` / `NEGATIVE_INTEREST` | `weightInterest += 0.05` |
| `POSITIVE_BEHAVIOR` / `NEGATIVE_BEHAVIOR` | `weightBehavior += 0.05` |
| Other | ignored |

After adjustment, `MatchingPreference.normalize()` re-scales all three weights to sum to `1.0`.

**Note:** `weightTimeOverlap` is never directly incremented; it changes only through normalization when the other two grow.

### Default Weights (new user)

```
weightTimeOverlap = 1/3 ≈ 0.333
weightInterest    = 1/3 ≈ 0.333
weightBehavior    = 1/3 ≈ 0.333
```

### How It Enters Matching

`AiWeightedMatchingStrategy.match()` loads the caller's personalized weights before scoring:

```java
// AiWeightedMatchingStrategy.java
MatchingPreference pref = matchingPreferenceRepository.findByUserId(callerUuid)
        .orElseGet(() -> MatchingPreference.defaultFor(callerUuid));
```

The three weights feed directly into the composite scoring formula.

---

## The Scoring Formula (at Match Time)

```
TotalScore = (W_time    × S_time)
           + (W_interest × S_tags)
           + (W_behavior × S_trust)

S_time    = min(overlapMinutes / 60 × 100, 100)        [0–100]
S_tags    = Jaccard(callerTags, candidateTags) × 100   [0–100]
S_trust   = candidateTrustScore / 10                   [0–100]

W_time + W_interest + W_behavior = 1.0  (always normalized)
```

**File:** `backend/src/main/java/com/walkmate/application/walkintent/AiWeightedMatchingStrategy.java`

---

## Two-Level Personalization Summary

| Dimension | Who is affected | Mechanism | Updated by |
|---|---|---|---|
| `S_trust` | **Candidate** being evaluated | Raw trust score in `user_account` | ReviewCommandService (synchronous, in-transaction) |
| `W_behavior` / `W_interest` | **Caller** doing the search | Personal weights in `matching_preference_model` | AiTrainingService (async, post-commit) |

A high-rated user becomes a better candidate for **everyone** (higher `S_trust`). A reviewer who repeatedly selects BEHAVIOR tags trains their own model to value behavior more when they search.

---

## Key Files Reference

| Layer | File |
|---|---|
| Frontend ViewModel | `frontend/.../ui/review/ReviewViewModel.java` |
| Frontend Repository | `frontend/.../data/repository/ReviewRepositoryImpl.java` |
| Backend Controller | `backend/.../presentation/controller/review/ReviewController.java` |
| Application Service | `backend/.../application/review/ReviewCommandService.java` |
| Trust Policy | `backend/.../domain/review/TrustScorePolicy.java` |
| Async Training | `backend/.../application/walkintent/AiTrainingService.java` |
| Preference Model | `backend/.../domain/walkintent/MatchingPreference.java` |
| Preference Repo Impl | `backend/.../infrastructure/repository/walkintent/MatchingPreferenceJdbcRepository.java` |
| AI Matching Strategy | `backend/.../application/walkintent/AiWeightedMatchingStrategy.java` |
