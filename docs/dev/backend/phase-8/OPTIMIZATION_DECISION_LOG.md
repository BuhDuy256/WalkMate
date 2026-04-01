# Phase 8 — Optimization Decision Log
## "Exclude Blocked Users" in the Matching Engine

---

## Problem Statement

Every call to `POST /api/v1/intents/{id}/find-match` runs `RuleBasedMatchingStrategy.findCandidates()`,
which queries the database for open walk intents that overlap with the caller's time window.
Phase 8 adds the requirement that **users who have a block relationship in either direction
must never be presented to each other as match candidates**.

The naive implementation calls `socialRepository.isBlocked(A, C)` and `socialRepository.isBlocked(C, A)`
for every candidate `C` in the result set — 2N SQL queries per match request where N is the
number of candidates. At 50 candidates, that is 100 round-trips before a single proposal is
created. This is unacceptable.

---

## Options Considered

### Option 1 — Per-Candidate isBlocked() calls (rejected)

```java
// ✗ REJECTED — O(2N) DB round-trips
candidates.stream()
    .filter(c -> !socialRepo.isBlocked(callerId, c.getUserId()))
    .filter(c -> !socialRepo.isBlocked(c.getUserId(), callerId))
    .collect(toList());
```

**Cost:** 2N sequential SQL queries.
**Verdict:** Rejected. Latency scales linearly with candidate count.

---

### Option 2 — SQL NOT IN subquery inside findOpenCandidates (rejected)

Extend `WalkIntentRepository.findOpenCandidates()` to accept a `Set<UUID> excludedUserIds`
and embed a `WHERE wi.user_id NOT IN (...)` clause directly in the candidates query.

```sql
-- Inside findOpenCandidates
AND wi.user_id NOT IN (
    SELECT blocked_id FROM block_relation WHERE blocker_id = :callerId
    UNION
    SELECT blocker_id FROM block_relation WHERE blocked_id = :callerId
)
```

**Cost:** Single DB round-trip, subquery executes against two indexed columns.
**Problem:** This couples the `walkintent` domain (and its repository interface) to the
`social` domain — a DDD boundary violation. The walk-intent domain must not know that
block relations exist.
**Verdict:** Rejected. The performance gain does not justify the architectural coupling.

---

### Option 3 — Batch-load exclusion set, then in-memory filter (CHOSEN)

```java
// In RuleBasedMatchingStrategy.findCandidates()

// 1. DB query 1: standard hard filter (existing)
List<WalkIntent> candidates = walkIntentRepository.findOpenCandidates(...);

// 2. DB query 2: load FULL exclusion set — 1 UNION query
Set<UUID> blocked = socialRepository.getBlockedAndBlockerIds(callerId);

// 3. In-memory O(n) filter — no additional DB queries
if (!blocked.isEmpty()) {
    candidates = candidates.stream()
            .filter(c -> !blocked.contains(UUID.fromString(c.getUserId())))
            .toList();
}
```

The `getBlockedAndBlockerIds` SQL:
```sql
SELECT blocked_id AS user_id FROM block_relation WHERE blocker_id = :userId
UNION
SELECT blocker_id AS user_id FROM block_relation WHERE blocked_id = :userId
```

**Cost breakdown:**
- DB round-trips: **2 total** (candidates query + UNION exclusion query), regardless of N.
- In-memory work: O(N) stream + O(k) HashSet.contains() — constant per element.
- Early-exit guard: if `blocked.isEmpty()` (most users have zero blocks), the filter
  is skipped entirely; overhead is one extra empty-result query.

**Verdict:** Chosen. O(1) DB round-trips, respects DDD boundaries, simple to read.

---

## The UNION Query — Index Usage

The `block_relation` table has two dedicated indexes:

```sql
CREATE INDEX idx_block_blocker ON block_relation (blocker_id);
CREATE INDEX idx_block_blocked ON block_relation (blocked_id);
```

PostgreSQL executes the UNION as:
1. Index scan on `idx_block_blocker` for `WHERE blocker_id = :userId` → rows user blocked.
2. Index scan on `idx_block_blocked` for `WHERE blocked_id = :userId` → rows that blocked user.
3. Hash-based deduplication of the UNION result.

Expected execution plan: two fast index scans + hash dedup. For a typical user with
< 100 block relationships, this executes in < 1 ms.

---

## Scale Analysis

| Users with blocks | Candidates | DB round-trips | In-memory ops |
|-------------------|-----------|----------------|---------------|
| 0 (most users)    | N         | 2              | 0 (short-circuit) |
| 10                | N         | 2              | N × O(1) HashSet |
| 1 000             | N         | 2              | N × O(1) HashSet |

The cost is flat at 2 DB round-trips. Only the in-memory pass scales with N, and
Java HashSet.contains() is O(1) average so the total remains O(N).

---

## Future Upgrade Path

If block counts reach tens of thousands (e.g., anti-spam use case), Option 2 can be
revisited: the `SocialRepository.getBlockedAndBlockerIds()` contract can be changed to
return a parameterised SQL fragment or a Postgres array literal, and `findOpenCandidates`
can be updated to incorporate it — without any changes to the application or domain layers
above.

Alternatively, if the matching query itself becomes a bottleneck, the block exclusion
can be pushed into a materialised view or a Bloom filter cache (Redis) keyed on userId.
The `getBlockedAndBlockerIds` method provides a clean cache-aside hook for this upgrade.
