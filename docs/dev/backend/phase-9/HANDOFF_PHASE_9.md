# Phase 9 Handoff — Post-Session Reviews + Trust Score

## What Was Built

Phase 9 implements the full review lifecycle: submitting a star-rated review for a completed walk session and atomically updating the reviewee's trust score using a bounded domain policy.

---

## Backend

### New Migrations

| File | Purpose |
|------|---------|
| `V12__add_trust_score_to_user_account.sql` | Adds `trust_score INT NOT NULL DEFAULT 0` to `user_account` |
| `V13__create_walk_review.sql` | Creates `walk_review` table with unique constraint on `(session_id, reviewer_id)` |

### New Domain Classes

| Class | Role |
|-------|------|
| `domain/review/WalkReview` | Rich entity. `WalkReview.create(sessionId, reviewerId, revieweeId, ratingStars, comment)` validates rating in constructor. |
| `domain/review/ReviewErrorCode` | `REVIEW_SESSION_NOT_COMPLETED`, `REVIEW_NOT_PARTICIPANT`, `REVIEW_ALREADY_SUBMITTED`, `REVIEW_INVALID_RATING`, `REVIEW_NOT_FOUND` |
| `domain/review/WalkReviewRepository` | `save`, `existsBySessionAndReviewer`, `findByRevieweeId` |
| `domain/review/SessionOutcome` | Enum mapping terminal status → trust-score delta: `COMPLETED +5`, `NO_SHOW -20`, `ABORTED -10`, `CANCELLED -5` |
| `domain/review/TrustScorePolicy` | Pure function `apply(currentScore, outcome)` → bounded `[0, 1000]`. Single source of truth for all score mutations. |

### Modified Domain

**`domain/user/User`**:
- Added `trustScore` field (default `0`).
- Updated rehydration constructor with `trustScore` parameter.
- Added `applyTrustScore(int boundedScore)` — accepts a pre-bounded value from `TrustScorePolicy`, keeping the bound logic out of the entity.

**`domain/user/UserRepository`**:
- Added `Optional<User> findById(String userId)`.

### New Infrastructure

| Class | Role |
|-------|------|
| `infrastructure/repository/review/WalkReviewJdbcRepository` | `JdbcClient`-based. INSERT, duplicate check via COUNT, SELECT by revieweeId. |
| `infrastructure/repository/user/UserJdbcRepository` | Updated SELECT/INSERT/UPDATE to include `trust_score`. Added `findById`. |

### New Application Layer

**`application/review/ReviewCommandService`**:

| Method | Steps |
|--------|-------|
| `submitReview(sessionId, reviewerId, ratingStars, comment)` | ① Verify COMPLETED ② Verify participant ③ Duplicate guard ④ Determine reviewee ⑤ Create + save review ⑥ Load reviewee User ⑦ Apply TrustScorePolicy ⑧ Save User — all in one `@Transactional` |
| `getReviewsForUser(userId)` | `@Transactional(readOnly=true)` query |

### New Presentation Layer

| Class | Endpoint |
|-------|---------|
| `ReviewController` | `POST /api/v1/sessions/{sessionId}/review`, `GET /api/v1/users/{userId}/reviews` |
| `SubmitReviewRequest` | `{ "rating_stars": 1-5, "comment": "..." }` |
| `ReviewResponse` | `{ "review_id", "session_id", "reviewer_id", "reviewee_id", "rating_stars", "comment", "created_at" }` |

### Modified Configuration

- `SecurityConfig` — `POST /api/v1/sessions/*/review` requires auth; `GET /api/v1/users/*/reviews` is public.

---

## Frontend

### New Files

| File | Role |
|------|------|
| `domain/review/WalkReview.java` | Domain model |
| `domain/review/ReviewRepository.java` | Interface: `submitReview`, `getReviewsForUser` |
| `data/datasource/remote/api/ReviewApiService.java` | Retrofit: `POST sessions/{id}/review`, `GET users/{id}/reviews` |
| `data/datasource/remote/dto/request/review/SubmitReviewRequest.java` | Request DTO |
| `data/datasource/remote/dto/response/review/ReviewResponse.java` | Response DTO |
| `data/repository/ReviewRepositoryImpl.java` | Real Retrofit calls, inline mapping |
| `ui/review/ReviewViewModel.java` | `SubmitState` enum, `submitReview()`, `loadReviewsForUser()`, LiveData streams |

---

## API Contract Summary

```
POST /api/v1/sessions/{sessionId}/review   → 200 ReviewResponse
  Body: { "rating_stars": 1-5, "comment": "..." }
  Errors: REVIEW_SESSION_NOT_COMPLETED | REVIEW_NOT_PARTICIPANT | REVIEW_ALREADY_SUBMITTED

GET  /api/v1/users/{userId}/reviews        → 200 List<ReviewResponse> (public)
```

---

## Trust Score Logic

| Session Outcome | Delta | New Score Range |
|-----------------|-------|-----------------|
| COMPLETED | +5 | capped at 1000 |
| NO_SHOW | −20 | floored at 0 |
| ABORTED | −10 | floored at 0 |
| CANCELLED | −5 | floored at 0 |

In Phase 9, only COMPLETED sessions are reviewable, so the delta is always +5 per review. The other deltas are available for future scheduler-based score updates (e.g., penalising no-shows automatically without requiring the other party to submit a review).

---

## Known Gaps / Phase 12 Considerations

1. **Total session count / distance fields** — `user_account` has no `total_distance_km` or `completed_sessions` column yet; needed for the Phase 12 gamification leaderboard.
2. **Rating average** — `WalkReview.ratingStars` is stored but no aggregate (average, distribution) is computed yet.
3. **Review editing** — not supported; a user can submit exactly one review per session.
