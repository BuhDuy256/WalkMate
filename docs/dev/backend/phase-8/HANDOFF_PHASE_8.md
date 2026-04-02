# WalkMate Handoff — End of Phase 8

## What Was Delivered

Phase 8 implements the social graph (Follow & Block) and wires block relationships into
the Matching Engine so blocked users are never presented to each other as walk candidates.

---

## Backend

### New Migration

| File | Purpose |
|------|---------|
| `V16__create_social_relations.sql` | Creates `follow_relation` and `block_relation` tables with composite PKs, self-relationship CHECK constraints, and four optimised indexes |

**follow_relation** schema:
```
follower_id   UUID  FK → user_account(id)  ─┐ PK
followee_id   UUID  FK → user_account(id)  ─┘
followed_at   TIMESTAMPTZ DEFAULT NOW()
CHECK follower_id <> followee_id
```

**block_relation** schema:
```
blocker_id   UUID  FK → user_account(id)  ─┐ PK
blocked_id   UUID  FK → user_account(id)  ─┘
blocked_at   TIMESTAMPTZ DEFAULT NOW()
CHECK blocker_id <> blocked_id
```

---

### New Domain (`domain/social/`)

| Class | Role |
|-------|------|
| `FollowRelation.java` | Immutable record: followerId, followeeId, followedAt |
| `BlockRelation.java`  | Immutable record: blockerId, blockedId, blockedAt |
| `SocialErrorCode.java`| `FOLLOW_ALREADY_FOLLOWING`, `FOLLOW_SELF_FOLLOW_FORBIDDEN`, `BLOCK_ALREADY_BLOCKED`, `BLOCK_SELF_BLOCK_FORBIDDEN`, `SOCIAL_USER_NOT_FOUND` |
| `SocialRepository.java` | Domain interface — all follow/block operations + `getBlockedAndBlockerIds(UUID)` |

---

### New Application Services (`application/social/`)

| Class | Key methods |
|-------|------------|
| `SocialCommandService` | `follow`, `unfollow`, `block`, `unblock` — guards self-ops, idempotent unfollow/unblock, tears down follow on block |
| `SocialQueryService`   | `getFollowers`, `getFollowing`, `isFollowing`, `isBlocked` |

**block() side-effect:** when A blocks B, any follow relationships in both directions (A→B and B→A) are silently removed before inserting the block row. This prevents blocked users from appearing in each other's follower/following lists.

---

### New Infrastructure (`infrastructure/repository/social/`)

| Class | Role |
|-------|------|
| `SocialJdbcRepository` | `JdbcClient`-based; all follow/block CRUD + the `getBlockedAndBlockerIds` UNION query |

---

### New Presentation (`presentation/controller/social/`)

| Endpoint | Auth | Description |
|----------|------|-------------|
| `POST /api/v1/users/{userId}/follow` | JWT | Follow a user |
| `DELETE /api/v1/users/{userId}/follow` | JWT | Unfollow a user (idempotent) |
| `GET /api/v1/users/{userId}/followers` | Public | List of followers with name + avatar |
| `GET /api/v1/users/{userId}/following` | Public | List of users this user follows |
| `POST /api/v1/users/{userId}/block` | JWT | Block a user |
| `DELETE /api/v1/users/{userId}/block` | JWT | Unblock a user (idempotent) |

All endpoints return `ApiResponse<T>`. Mutating endpoints return `ApiResponse<Void>` (data: null).

`UserSummaryResponse` shape (returned in follower/following lists):
```json
{
  "userId": "uuid-string",
  "fullName": "Nguyễn Bảo Duy",
  "avatarUrl": "http://localhost:8080/api/v1/files/avatars/xxx.jpg"
}
```

**New SecurityConfig rules (Phase 8 additions):**
```java
.requestMatchers(HttpMethod.GET, "/api/v1/users/*/followers").permitAll()
.requestMatchers(HttpMethod.GET, "/api/v1/users/*/following").permitAll()
// POST/DELETE on follow & block fall through to anyRequest().authenticated()
```

---

### Modified: Matching Engine Integration

**`RuleBasedMatchingStrategy.findCandidates(WalkIntent intent)`** now:
1. Executes the existing DB-level hard filter (hotspot, time window, age).
2. Calls `SocialRepository.getBlockedAndBlockerIds(callerId)` — one UNION query, O(1) DB round-trip.
3. In-memory filters out any candidate whose userId is in the exclusion set.

This keeps the critical path at **2 DB queries** regardless of how many blocks a user has. See `OPTIMIZATION_DECISION_LOG.md` for full analysis.

---

## Frontend

### New Domain (`domain/social/`)

| Class | Role |
|-------|------|
| `UserSummary.java` | Value object: userId, fullName, avatarUrl |
| `SocialRepository.java` | Interface with `DomainCallback<T>` callbacks: follow, unfollow, block, unblock, getFollowers, getFollowing |

### New Data Layer

| Class | Role |
|-------|------|
| `SocialApiService.java` | Retrofit interface — 6 endpoints |
| `UserSummaryResponse.java` | Gson DTO mirroring backend `UserSummaryResponse` |
| `SocialMapper.java` | `UserSummaryResponse → UserSummary` mapping |
| `SocialRepositoryImpl.java` | Background-thread Retrofit calls; error code extraction from `ApiResponse.error.code` |

### Modified: `WalkMateApplication.java`
- Added `SocialRepository socialRepository` singleton field.
- Added `getSocialRepository()` getter (lazy-initialised `SocialRepositoryImpl`).

---

## Canonical Error Codes (Social Domain)

| Code | Trigger |
|------|---------|
| `FOLLOW_ALREADY_FOLLOWING` | Second follow on same pair |
| `FOLLOW_SELF_FOLLOW_FORBIDDEN` | followerId == followeeId |
| `BLOCK_ALREADY_BLOCKED` | Second block on same pair |
| `BLOCK_SELF_BLOCK_FORBIDDEN` | blockerId == blockedId |
| `SOCIAL_USER_NOT_FOUND` | Target user UUID does not exist |

---

## Validated Test Cases

| Scenario | Expected result |
|----------|----------------|
| User A follows User B | 200 — `follow_relation` row created |
| User A follows User B again | 400 — `FOLLOW_ALREADY_FOLLOWING` |
| User A follows themselves | 400 — `FOLLOW_SELF_FOLLOW_FORBIDDEN` |
| User A blocks User B | 200 — `block_relation` row created; follow rows in both directions removed |
| User A blocks User B again | 400 — `BLOCK_ALREADY_BLOCKED` |
| User A creates intent overlapping User B's intent after A blocks B | `findMatch` returns 204 — B excluded |
| User B creates intent overlapping User A's intent (B did NOT block A) | `findMatch` returns 204 — A excluded (bidirectional) |
| `GET /api/v1/users/{id}/followers` (no JWT) | 200 — public list |
| Unfollow when not following | 200 — idempotent success |
| Unblock when not blocked | 200 — idempotent success |

---

## Ready for Phase 9

- `SocialRepository.isFollowing` is exposed for use by the scoring engine
  (`// TODO (AI Upgrade — Social): +50 for mutual followers` in `RuleBasedMatchingStrategy`).
- `getBlockedAndBlockerIds` is intentionally generic — can be reused by any feature
  that needs mutual-block awareness (e.g., chat, notifications).
- No scheduled cleanup needed: block/follow rows cascade-delete when either user is removed.
