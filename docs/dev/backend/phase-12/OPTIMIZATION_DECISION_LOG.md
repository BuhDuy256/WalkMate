# Phase 12 — Optimization Decision Log

## Decision 1: AFTER_COMMIT + REQUIRES_NEW instead of synchronous post-session logic

**Context:** The gamification reward needs to run after a session is marked
COMPLETED, but gamification failures (e.g. the user's record has a transient
DB issue) must never cause the session completion to roll back.

**Decision:** Use Spring's `@TransactionalEventListener(phase = AFTER_COMMIT)`
to decouple gamification from the session commit, and `@Transactional(propagation
= REQUIRES_NEW)` on the listener so it operates in an independent transaction.

**Trade-offs:**
- ✅ Session completion is durable before gamification starts — no risk of
  rolling back an already-completed walk.
- ✅ A gamification failure only affects gamification, not session state.
- ⚠️  Under-award is possible if the process crashes between session commit and
  event delivery. Accepted for MVP.
- ⚠️  Adds a second DB round-trip (the REQUIRES_NEW transaction) for every
  completed session. Negligible at current scale.

---

## Decision 2: Sum polyline chunks at award time rather than maintaining a running total

**Context:** GPS data is stored as Google Encoded Polyline chunks in the
`session_point_chunks` table. At session completion, the distance must be
calculated for the points award.

**Decision:** `GamificationCommandService` calls
`TrackingChunkRepository.findPolylinesBySessionId()`, decodes each chunk with
`PolylineDecoder`, and sums the Haversine distances. This is a read at
award time, not a running counter.

**Trade-offs:**
- ✅ Simple — no extra column to keep in sync during GPS sync calls.
- ✅ Correct — uses the canonical stored polylines, not in-memory state.
- ⚠️  For very long walks with many chunks, this triggers multiple DB reads +
  CPU-bound decoding at award time. At MVP scale (< 100 chunks/session) this
  is negligible. Future: store `total_distance_km` on the session record and
  update it incrementally during each sync call.

---

## Decision 3: Both participants receive identical points

**Context:** A completed walk involves two users. The points formula is:
`distanceKm × 10 + durationMin × 2`. There is no concept of a "winner".

**Decision:** Award the same calculated points to both `userIdA` and `userIdB`.

**Trade-offs:**
- ✅ Symmetric — promotes fairness and simplicity.
- ⚠️  Doesn't account for one user having poor GPS coverage and therefore a
  shorter recorded route. Accepted for MVP.

---

## Decision 4: Gamification errors are logged and swallowed at the service boundary

**Context:** The `@TransactionalEventListener` listener must not propagate
exceptions back to the scheduler or to the HTTP thread that triggered completion.

**Decision:** `GamificationCommandService.onSessionCompleted()` wraps
`rewardBothParticipants()` in a try-catch that logs the full stack trace and
returns normally on error.

**Trade-offs:**
- ✅ Session completion is never degraded by gamification failures.
- ✅ The Spring scheduler's thread (for S-9 auto-completions) stays alive.
- ⚠️  Failures are silent to the user. Operators must monitor logs.

---

## Decision 5: Public gamification endpoints (no auth required)

**Context:** Leaderboard, badges, and stats are social features — users benefit
from seeing others' progress without needing to log in.

**Decision:** `GET /api/v1/leaderboard`, `/users/{userId}/badges`, and
`/users/{userId}/stats` are configured as `permitAll()` in `SecurityConfig`.

**Trade-offs:**
- ✅ Reduces friction for non-logged-in browsing.
- ⚠️  User IDs are exposed in the leaderboard. Acceptable since user IDs are
  already present in session and review responses.

---

## Decision 6: Leaderboard ordered by total_points DESC, trust_score DESC

**Context:** For ties on points, a secondary sort is needed.

**Decision:** Use `trust_score` as the tiebreaker (higher trust = higher rank).

**Trade-offs:**
- ✅ Rewards quality of walks, not just quantity.
- Simple one-line `ORDER BY` clause with no computed columns.

---

## Decision 7: `PostSessionSummaryViewModel` loads stats and badges in parallel

**Context:** The summary screen needs both the user's updated stats (points,
distance) and their badge list. These are independent API calls.

**Decision:** `loadUserSummary()` dispatches both calls without waiting for
either — they update separate LiveData objects independently. The UI renders
partial results as they arrive.

**Trade-offs:**
- ✅ Faster perceived load time — stats may appear before badges or vice versa.
- ⚠️  The `error` LiveData may be overwritten if both calls fail; the second
  error replaces the first. Acceptable for MVP.
