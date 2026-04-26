# Badge System — Architectural Analysis

**WalkMate · Full-Stack End-to-End Review**
**Date:** 2026-04-26 · **Author:** Architectural Analysis Agent

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Entity-Relationship Analysis](#2-entity-relationship-analysis)
3. [Backend Data Flow & Abstraction](#3-backend-data-flow--abstraction)
4. [Frontend Rendering Logic](#4-frontend-rendering-logic)
5. [Optimization Recommendations](#5-optimization-recommendations)

---

## 1. Executive Summary

The WalkMate badge system is a gamification mechanism that awards permanent, non-revocable achievement badges to users based on two orthogonal triggers: **session completion milestones** and **trust score thresholds** earned via peer reviews.

The system is architecturally clean. Badge logic is isolated in a dedicated `gamification` package spanning domain, application, and infrastructure layers. The backend exposes a read-only REST API (`GET /api/v1/users/{userId}/badges`) that the Android frontend polls on every profile screen visit.

**Eight badges** are defined. Six are session/distance-based (`FIRST_WALK`, `FIRST_FIVE`, `CENTURY_STEPS`, `FIRST_KM`, `TEN_KM_WALKER`, `FIFTY_KM_WALKER`); two are trust-score-based (`TRUSTED_WALKER`, `HIGHLY_TRUSTED`). The deduplication guarantee is enforced at the database level (`PRIMARY KEY(user_id, badge_name)` + `ON CONFLICT DO NOTHING`), making the evaluation pipeline fully idempotent.

**Key strengths:** clear separation of concerns, event-driven decoupling from the session lifecycle, idempotent award writes, and a non-fatal gamification contract that can never crash the core session commit.

**Key gaps:** icon assets are not yet wired (Phase 14 placeholder), the own-profile screen caps badge display at 3 (hardcoded slots), and there is no push notification triggered on badge award.

---

## 2. Entity-Relationship Analysis

### 2.1 Badge-Related Tables

| Table | Role in Badge System |
|---|---|
| `user_account` | Source of truth for the denormalized stats used to evaluate badge criteria (`completed_sessions`, `total_distance_km`, `trust_score`, `total_points`) |
| `user_badge` | Stores awarded badges — one row per `(user_id, badge_name)` pair |
| `walk_session` | Completion events that drive session/distance-based badge triggers |
| `walk_review` | Review submissions that update `trust_score` and drive trust-based badge triggers |
| `session_point_chunks` | GPS polyline chunks read by `GamificationCommandService` to calculate per-participant distance and duration at session completion |

### 2.2 `user_badge` Schema

```sql
CREATE TABLE public.user_badge (
  user_id    uuid      NOT NULL,
  badge_name varchar   NOT NULL,         -- Enum name, e.g. 'FIRST_WALK'
  awarded_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT user_badge_pkey PRIMARY KEY (user_id, badge_name),
  CONSTRAINT user_badge_user_id_fkey FOREIGN KEY (user_id)
    REFERENCES public.user_account(user_id)
);
```

- **Composite primary key** `(user_id, badge_name)` is the sole deduplication guard.
- `badge_name` stores the raw Java enum name — not a foreign key into a `badge` master table. (Note: a separate `badge` master table does exist in the schema for icon metadata, but the award pipeline writes directly to `user_badge.badge_name` as a plain `varchar`.)
- No `revoked_at` or `revoked` column — badges are permanent by design.

### 2.3 Entity Relationship Diagram

```
user_account (user_id PK)
  │  completed_sessions  ──────────────────────┐
  │  total_distance_km   ──────────────────────┤
  │  trust_score         ──────────────────────┤
  │  total_points        ──────────────────────┤
  │                                            │
  │  (1)                                       ▼
  ├──── user_badge (user_id FK, badge_name) ◄── BadgePolicy.evaluateEarned()
  │         awarded_at
  │
  │  (1)──────────(N)
  ├──── walk_session ──────────── session_point_chunks
  │         status = COMPLETED          polyline (GPS data)
  │         user_a_distance_km
  │         user_b_distance_km
  │
  │  (1)──────────(N)
  └──── walk_review
            rating_stars ──► trust_score delta ──► user_account.trust_score
```

### 2.4 The Orphaned `badge` Master Table

The schema includes a `badge` master table (`badge_id`, `name`, `description`, `icon_url`). However, the current award pipeline **does not reference it**. `UserBadgeJdbcRepository.saveAll()` inserts a raw enum name string; `findByUserId()` returns that same string. The master table is currently unused by the badge flow and serves no relational integrity role.

This is documented separately in [Section 5.1](#51-orphaned-badge-master-table).

---

## 3. Backend Data Flow & Abstraction

### 3.1 Trigger #1 — Session Completion Path

```
SessionController.completeSession(sessionId, callerId)
  └─► SessionCommandService.completeSession()
        ├─ Validates session state, records user-specific 'COMPLETED' timestamp
        ├─ When BOTH users reach terminal state → global status = COMPLETED
        └─ ApplicationEventPublisher.publishEvent(new SessionCompletedEvent(session))
                                        │
                              [Spring @TransactionalEventListener]
                              [phase = AFTER_COMMIT]
                              [propagation = REQUIRES_NEW]
                                        │
                                        ▼
              GamificationCommandService.onSessionCompleted(event)
                ├─ Reads GPS polylines from session_point_chunks
                ├─ Calculates distance (km) + duration (seconds) per participant
                ├─ rewardUser(userIdA, points, distanceKm)
                │    ├─ Fetches User entity from UserRepository
                │    ├─ Updates: totalPoints += points
                │    │           totalDistanceKm += distanceKm
                │    │           completedSessions += 1
                │    │           trustScore += 5  (Stage-1 completion bonus)
                │    ├─ Persists updated User
                │    └─ BadgeEvaluationService.evaluateAndAward(user)  ◄── BADGE GATE
                │              ├─ Constructs UserStats from user fields
                │              ├─ Fetches existingBadgeNames from user_badge
                │              ├─ BadgePolicy.evaluateEarned(stats, existing)
                │              │    Evaluates all 8 Badge enum criteria
                │              │    Returns only newly earned badges
                │              └─ UserBadgeRepository.saveAll(userId, newBadges)
                │                   INSERT INTO user_badge ... ON CONFLICT DO NOTHING
                └─ rewardUser(userIdB, points, distanceKm)
                     └─ [same flow as above]
```

**Session-based badge criteria evaluated at this trigger:**

| Badge | Criterion |
|---|---|
| `FIRST_WALK` | `completedSessions >= 1` |
| `FIRST_FIVE` | `completedSessions >= 5` |
| `CENTURY_STEPS` | `completedSessions >= 10` |
| `FIRST_KM` | `totalDistanceKm >= 1.0` |
| `TEN_KM_WALKER` | `totalDistanceKm >= 10.0` |
| `FIFTY_KM_WALKER` | `totalDistanceKm >= 50.0` |

**Scheduler path (auto-expiry):** `SessionCommandService.handleExpiredSessions()` is a `@Scheduled` task. It auto-completes sessions past their `scheduled_end` window and publishes the same `SessionCompletedEvent` — badge logic is identical for both user-initiated and scheduler-driven completions.

### 3.2 Trigger #2 — Review Submission Path

```
ReviewController.submitReview(sessionId, principal, request)
  └─► ReviewCommandService.submitReview(sessionId, reviewerId, stars, comment, tagIds)
        ├─ Validates session is COMPLETED
        ├─ Validates reviewer is a participant
        ├─ Duplicate review guard
        ├─ Creates WalkReview entity → persists to walk_review
        ├─ Calculates trust score delta:
        │     5★ → +10   4★ → +5   3★ → 0   2★ → -10   1★ → -20
        ├─ Fetches reviewee User entity
        ├─ reviewee.trustScore += delta
        ├─ Persists updated reviewee
        └─ BadgeEvaluationService.evaluateAndAward(reviewee)  ◄── BADGE GATE
                ├─ Constructs UserStats from reviewee fields
                ├─ Fetches existing badges
                ├─ BadgePolicy.evaluateEarned(stats, existing)
                └─ Saves any newly earned badges

```

**Trust-based badge criteria evaluated at this trigger:**

| Badge | Criterion |
|---|---|
| `TRUSTED_WALKER` | `trustScore >= 100` |
| `HIGHLY_TRUSTED` | `trustScore >= 500` |

**Note:** `TRUSTED_WALKER` / `HIGHLY_TRUSTED` can also be triggered via the Stage-1 completion bonus (+5 per session) that runs in Trigger #1. Both triggers call the same `BadgeEvaluationService.evaluateAndAward()` method, so trust-based badges are evaluated after every session completion as well.

### 3.3 `BadgePolicy` — Domain Logic

`BadgePolicy.evaluateEarned(UserStats stats, Set<String> existingBadgeNames)` is a pure static method with no dependencies. It iterates all `Badge` enum values and applies their criteria against the stats record, skipping any already present in `existingBadgeNames`. Returns a `List<Badge>` of net-new awards.

This placement in the **domain layer** is architecturally correct per DDD: the rule for "when does a user earn FIFTY_KM_WALKER" is a business invariant, not an infrastructure concern.

### 3.4 Coupling & Cohesion Assessment

| Concern | Assessment |
|---|---|
| **Cohesion — `BadgeEvaluationService`** | High. Single responsibility: read stats, evaluate, write awards. No unrelated operations. |
| **Cohesion — `GamificationCommandService`** | Moderate-high. Handles session rewards AND calls badge evaluation. The distance/polyline calculation is substantive logic that could be its own service, but current size is acceptable. |
| **Coupling — Session → Badge** | Low. `SessionCommandService` does not import or reference any gamification class. It only publishes a Spring `ApplicationEvent`. |
| **Coupling — Review → Badge** | Moderate. `ReviewCommandService` directly injects `BadgeEvaluationService`. This is a direct dependency across two domain concerns (review and gamification). Not a violation of the architecture, but it makes `ReviewCommandService` responsible for triggering a side effect from a different subdomain. |
| **Coupling — BadgePolicy → DB** | None. `BadgePolicy` is pure domain logic with no infrastructure dependency. |
| **Transaction safety** | Correct. Gamification runs in `AFTER_COMMIT + REQUIRES_NEW` — badge award failures cannot roll back session completion. Review badge trigger runs inside the review transaction, meaning a badge write failure would roll back the review. This asymmetry is worth noting (see Section 5). |

---

## 4. Frontend Rendering Logic

### 4.1 Architecture Overview

The frontend follows the project's MVVM pattern precisely. Badge data flows: `GamificationApiService` → `GamificationRepositoryImpl` → `GamificationRepository` (domain interface) → `ProfileViewModel` / `PublicProfileViewModel` → `LiveData<UiState>` → Fragment render method.

### 4.2 Network Layer

**`GamificationApiService`** (Retrofit interface):
```
GET /api/v1/users/{userId}/badges   → Call<ApiResponse<BadgeResponse[]>>
GET /api/v1/users/{userId}/stats    → Call<ApiResponse<UserStatsResponse>>
GET /api/v1/leaderboard             → Call<ApiResponse<LeaderboardEntryResponse[]>>
```

**`GamificationRepositoryImpl`** maps HTTP DTOs to domain objects:
- `BadgeResponse { badgeName, awardedAt }` → `UserBadge { badgeName, awardedAt }`
- All calls dispatched on `ExecutorService` background thread per the project threading contract.

### 4.3 Own Profile Screen (`ui/profile/`)

**Data fetch — `ProfileViewModel.loadProfile()`:**

```
loadProfile()
  ├─ POST loading state to LiveData
  ├─ profileRepo.getMyProfile(userId, callback)
  │    └─ ON SUCCESS → loadSupplementalData(profile)
  │         ├─ AtomicInteger counter = 3
  │         ├─ gamificationRepo.getBadges(userId, callback)   ──┐
  │         ├─ gamificationRepo.getStats(userId, callback)    ──┤─► Each decrements counter
  │         └─ reviewRepo.getReviewsForUser(userId, callback) ──┘   When counter == 0: POST UiState
  └─ ON FAILURE → POST error state
```

Supplemental failures (badges, stats, reviews) are **non-fatal** — the ViewModel posts state with whatever partial data arrived. The base profile failure is fatal and shows an error screen.

**Badge mapping — `mapBadges(List<UserBadge>)`:**
```
"FIRST_WALK"     → "First Walk"
"FIRST_FIVE"     → "First Five"
"CENTURY_STEPS"  → "Century Steps"
"TEN_KM_WALKER"  → "Ten Km Walker"
"FIFTY_KM_WALKER"→ "Fifty Km Walker"
"TRUSTED_WALKER" → "Trusted Walker"
"HIGHLY_TRUSTED" → "Highly Trusted"
```
The transformation replaces underscores with spaces and title-cases each word. `iconDrawableResId` is set to `0` (no-op placeholder — Phase 14 work).

**Badge rendering — `ProfileFragment.renderBadges()`:**
- Displays up to **3 badges** in hardcoded static view slots (`imgBadge1`, `imgBadge2`, `imgBadge3` + corresponding labels).
- If the list is empty, the badge container is hidden with `GONE`.
- Icons are skipped (`setImageDrawable(null)`) until Phase 14 assets are available.

### 4.4 Public Profile Screen (`ui/profile/publicprofile/`)

**Data fetch — `PublicProfileViewModel.loadProfile(userId)`:**

```
loadProfile(userId)
  ├─ POST loading state to LiveData
  ├─ AtomicInteger counter = 4
  ├─ socialRepo.getPublicProfile(userId, callback)   ──┐ Fatal on failure
  ├─ gamificationRepo.getBadges(userId, callback)    ──┤ Non-fatal
  ├─ gamificationRepo.getStats(userId, callback)     ──┤ Non-fatal
  └─ reviewRepo.getReviewsForUser(userId, callback)  ──┘ Non-fatal
       When counter == 0 → POST final PublicProfileUiState
```

**Badge rendering — `PublicProfileFragment.renderBadges()`:**
- Renders badges as **Material Chips** in a `ChipGroup` (`chipGroupBadges`).
- No cap on number displayed — all earned badges shown.
- Clears and repopulates chips on every state update.
- Shows a "No badges" placeholder text when list is empty.

### 4.5 Comparison: Own Profile vs. Public Profile Badge Display

| Aspect | Own Profile (`ProfileFragment`) | Public Profile (`PublicProfileFragment`) |
|---|---|---|
| Display limit | 3 (hardcoded slots) | Unlimited |
| UI component | Static `ImageView` + `TextView` per slot | Dynamic `Chip` in `ChipGroup` |
| Icon support | Placeholder (Phase 14) | Not applicable (text-only chips) |
| Empty state | Parent `View.GONE` | "No badges" text shown |
| Parallel fetch count | 3 calls | 4 calls |

---

## 5. Optimization Recommendations

### 5.1 Orphaned `badge` Master Table

**Issue:** The `badge` master table (`badge_id`, `name`, `description`, `icon_url`) exists in the database but is not referenced by `user_badge` or any part of the award pipeline. `user_badge.badge_name` is a plain `varchar` with no FK constraint into `badge`.

**Consequence:** The icon URL stored in `badge.icon_url` is never served to the frontend. Phase 14's icon wiring work will need to either (a) join `user_badge` to `badge` in the API response, or (b) maintain a parallel client-side icon map.

**Recommendation:** Add a FK constraint `user_badge.badge_name → badge.name` once all 8 badge rows exist in the `badge` table, and extend `BadgeResponse` to include the `icon_url` field from the join. This eliminates the client-side enum-to-drawable mapping entirely and makes icon management a backend concern.

### 5.2 Transactional Asymmetry Between the Two Triggers

**Issue:** The two badge triggers have different transactional semantics:

- **Session trigger:** `GamificationCommandService` runs in `AFTER_COMMIT + REQUIRES_NEW`. A badge write failure logs an error but does not affect the session commit.
- **Review trigger:** `ReviewCommandService.submitReview()` calls `badgeEvaluationService.evaluateAndAward()` synchronously inside the review transaction. A badge write failure will roll back the entire review submission.

**Consequence:** A spurious DB error during `user_badge` insert (e.g., transient connection timeout) silently voids the review for the user with no indication other than an HTTP 500.

**Recommendation:** Extract the badge evaluation call in `ReviewCommandService` into an `@TransactionalEventListener(phase = AFTER_COMMIT)` pattern consistent with the session trigger. Publish a `ReviewSubmittedEvent` after the review is persisted, and handle badge logic in `GamificationCommandService.onReviewSubmitted()`.

### 5.3 Hardcoded 3-Badge Limit on Own Profile

**Issue:** `ProfileFragment` has three fixed slot views (`imgBadge1`–`imgBadge3`, `lblBadge1`–`lblBadge3`). A user who earns more than 3 badges sees only the first 3 with no indication that additional badges exist.

**Recommendation:** Replace the static slots with a `RecyclerView` (or the same `ChipGroup` approach used in `PublicProfileFragment`). This is a layout-only change — no backend or ViewModel changes needed. Alternatively, add a "View all N badges" affordance if the design intent is to surface only highlights.

### 5.4 No Push Notification on Badge Award

**Issue:** Users receive no in-app notification when a badge is awarded. The only way to discover a new badge is to open the profile screen, which re-fetches badges on every visit.

**Recommendation:** In `BadgeEvaluationService.evaluateAndAward()`, after `saveAll()`, publish a `BadgesAwardedEvent` containing the `userId` and the list of new badges. A `NotificationCommandService` listener can then insert rows into the `notification` table (which already exists in the schema) with `type = 'BADGE_AWARDED'` and a `payload` containing badge names. The FCM push pipeline (via `user_account.fcm_token`) can then surface an in-app toast or notification card.

### 5.5 Dual Read on Badge Evaluation (N+1 Pattern)

**Issue:** Every call to `BadgeEvaluationService.evaluateAndAward()` performs two sequential database reads:
1. `SELECT badge_name FROM user_badge WHERE user_id = ?` (fetch existing)
2. *(conditional)* `INSERT INTO user_badge ...` (if new badges found)

For session completion this runs **twice** (once per participant) inside the same `GamificationCommandService.onSessionCompleted()` call. Since each call has its own `REQUIRES_NEW` transaction, the reads are not batched.

**Recommendation:** For the session-completion path specifically, consider batching both users' badge evaluations in a single transaction by restructuring `rewardBothParticipants()` to collect both users' updated entities first, then call a `badgeEvaluationService.evaluateAndAwardBatch(List<User>)` variant that performs a single `WHERE user_id IN (?, ?)` query. This halves the badge-related DB reads per session completion event.

### 5.6 Frontend: No Staleness Handling After Session Completion

**Issue:** The frontend profile screen fetches badges on every visit via a fresh network call. However, after the user completes a session, the profile screen that was open before the session will show stale badge data until it is re-entered. There is no post-session signal to the Profile ViewModel to re-fetch.

**Recommendation:** After a session is successfully completed via `SessionViewModel` (or equivalent), use a shared `LiveData` or event bus at the `Application` level to notify `ProfileViewModel` to invalidate its cache and re-fetch supplemental data. This is a small quality-of-life improvement but directly impacts the "badge reveal" moment that is high-value for user retention.

---

*End of Document*
