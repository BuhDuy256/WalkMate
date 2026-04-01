# Phase 12 — Gamification Handoff

## Status: COMPLETE

## Overview

Phase 12 adds a full gamification layer on top of the session lifecycle. When a
walk session transitions to COMPLETED (either by a user calling the endpoint or
by the scheduler's S-9 sweep), the system awards points, distance credit, and
badges to both participants — without blocking or rolling back the session commit.

---

## Architecture

### Event-Driven Decoupling

```
SessionCommandService.completeSession()
  │
  ├─ session.complete(now)
  ├─ sessionRepository.save(session)          ← commits to DB
  ├─ logStateChange(...)
  └─ eventPublisher.publishEvent(SessionCompletedEvent)
                                │
                                │  AFTER_COMMIT (Spring @TransactionalEventListener)
                                ▼
               GamificationCommandService.onSessionCompleted()
                 │  REQUIRES_NEW transaction
                 ├─ calculateTotalDistanceKm (sum of stored polyline chunks)
                 ├─ calculateDurationMinutes (endedAt - startedAt)
                 ├─ points = distanceKm × 10 + durationMin × 2
                 ├─ user.applySessionReward(points, distanceKm)  [both users]
                 ├─ userRepository.save(user)
                 └─ BadgePolicy.evaluateEarned() → badgeRepository.saveAll()
```

**Why AFTER_COMMIT + REQUIRES_NEW?**
- `AFTER_COMMIT` ensures gamification only fires when the session record is
  durably committed. A crash between session save and event delivery would
  under-award, not corrupt session state.
- `REQUIRES_NEW` means a gamification failure (e.g. a user was deleted between
  the session commit and the event firing) opens and rolls back its own
  transaction, leaving the session COMPLETED and the calling transaction
  unaffected.

---

## New Files

### Backend

| File | Purpose |
|------|---------|
| `application/gamification/SessionCompletedEvent.java` | Spring event payload carrying the completed WalkSession |
| `application/gamification/GamificationCommandService.java` | AFTER_COMMIT listener; awards points + badges to both users |
| `domain/gamification/Badge.java` | Badge enum (8 badges) |
| `domain/gamification/BadgePolicy.java` | Pure domain policy — evaluates newly earned badges |
| `domain/gamification/UserStats.java` | Record aggregating a user's gamification state |
| `domain/gamification/UserBadgeRepository.java` | Port for badge persistence |
| `infrastructure/repository/gamification/UserBadgeJdbcRepository.java` | JdbcClient impl with ON CONFLICT DO NOTHING |
| `infrastructure/util/PolylineDecoder.java` | Google Encoded Polyline decoder + Haversine distance |
| `presentation/dto/response/gamification/BadgeResponse.java` | API response record |
| `presentation/dto/response/gamification/UserStatsResponse.java` | API response record |
| `presentation/dto/response/gamification/LeaderboardEntryResponse.java` | API response record |
| `presentation/controller/gamification/GamificationController.java` | 3 public GET endpoints |

### Modified Backend Files

| File | Change |
|------|--------|
| `application/session/SessionCommandService.java` | Injects `ApplicationEventPublisher`; publishes `SessionCompletedEvent` in `completeSession()` and `handleExpiredSessions()` (S-9 path) |
| `domain/user/User.java` | Added `totalPoints`, `totalDistanceKm`, `completedSessions` fields + `applySessionReward()` |
| `domain/user/UserRepository.java` | Added `findTopByPoints(int limit)` |
| `infrastructure/repository/user/UserJdbcRepository.java` | Full rewrite — all queries include new gamification columns |
| `domain/tracking/TrackingChunkRepository.java` | Added `findPolylinesBySessionId()` |
| `infrastructure/config/SecurityConfig.java` | `/api/v1/leaderboard`, `/users/*/badges`, `/users/*/stats` → permitAll |
| `db/migration/V14__create_gamification_tables.sql` | Adds gamification columns to user_account; creates user_badge table |

### Frontend

| File | Purpose |
|------|---------|
| `domain/gamification/UserBadge.java` | Domain model |
| `domain/gamification/UserStats.java` | Domain model |
| `domain/gamification/LeaderboardEntry.java` | Domain model |
| `domain/gamification/GamificationRepository.java` | Domain port |
| `data/datasource/remote/api/GamificationApiService.java` | Retrofit interface (3 GET calls) |
| `data/datasource/remote/dto/response/gamification/*.java` | Gson-annotated response DTOs |
| `data/repository/GamificationRepositoryImpl.java` | Retrofit-backed impl, background executor |
| `ui/gamification/PostSessionSummaryViewModel.java` | Loads stats + badges + leaderboard via LiveData |

---

## API Endpoints

All three endpoints are **public** (no JWT required).

```
GET /api/v1/users/{userId}/badges
→ ApiResponse<List<BadgeResponse>>

GET /api/v1/users/{userId}/stats
→ ApiResponse<UserStatsResponse>

GET /api/v1/leaderboard
→ ApiResponse<List<LeaderboardEntryResponse>>   (top 50, ordered by total_points DESC)
```

---

## Points Formula

```
points = floor(distanceKm × 10) + (durationMinutes × 2)
```

Distance is the Haversine sum across **all stored GPS polyline chunks** for the
session. If no tracking chunks exist (e.g. the user never moved), distance = 0
and only duration contributes.

---

## Badge Thresholds

| Badge | Criterion |
|-------|-----------|
| `FIRST_WALK` | completedSessions ≥ 1 |
| `FIRST_FIVE` | completedSessions ≥ 5 |
| `CENTURY_STEPS` | completedSessions ≥ 10 |
| `FIRST_KM` | totalDistanceKm ≥ 1.0 |
| `TEN_KM_WALKER` | totalDistanceKm ≥ 10.0 |
| `FIFTY_KM_WALKER` | totalDistanceKm ≥ 50.0 |
| `TRUSTED_WALKER` | trustScore ≥ 100 |
| `HIGHLY_TRUSTED` | trustScore ≥ 500 |

Badges are awarded at most once per user (enforced by `BadgePolicy` + DB
`UNIQUE(user_id, badge_name)` constraint).

---

## Database Migrations

| Version | File | Description |
|---------|------|-------------|
| V14 | `V14__create_gamification_tables.sql` | Adds `total_points`, `total_distance_km`, `completed_sessions` to `user_account`; creates `user_badge` |

---

## Known Limitations / Future Work

- **Under-award on crash**: If the process crashes between the session commit
  and `GamificationCommandService` executing, gamification for that session is
  silently lost. An idempotent retry queue (e.g. an outbox table) would close
  this gap.
- **Distance based on GPS chunks only**: If the user completed a walk without
  network (all GPS points stayed in Room), the synced polyline may be partial.
  This is acceptable for MVP — distance is a best-effort metric.
- **Leaderboard is live SQL**: For large user bases, consider a periodic
  materialized view refresh.
