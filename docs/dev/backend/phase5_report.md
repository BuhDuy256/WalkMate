# Phase 5 Report: Friends System — Domain & Repository

## Steps Completed

### Step 1: Read existing social layer
- Read `SocialRepository.java`, `SocialErrorCode.java`, `SocialJdbcRepository.java`
- Confirmed existing JdbcClient usage pattern (`.sql(...).param(...).query(...).list()` / `.optional()` / `.single()`)
- Confirmed `areAcceptedFriends` already present; `SOCIAL_USER_NOT_FOUND` in `SocialErrorCode`

### Step 2: Created `Friendship` domain entity
File: `domain/social/Friendship.java`

DB column → Java field mapping:
| DB Column | Java Field | Type |
|---|---|---|
| `friendship_id` | `friendshipId` | `String` |
| `requester_id` | `requesterId` | `UUID` |
| `addressee_id` | `addresseeId` | `UUID` |
| `status` | `status` | `String` |
| `version` | `version` | `long` |
| `created_at` | `createdAt` | `Instant` |
| `updated_at` | `updatedAt` | `Instant` |

- Static factory `create(requesterId, addresseeId)` sets status=PENDING, version=0
- `accept()` and `decline()` both call `guardPending()` before mutating state
- `guardPending()` throws `DomainException(FRIEND_REQUEST_ALREADY_RESOLVED)` if status != PENDING
- Helpers: `isPending()`, `isAccepted()`

### Step 3: Created `FriendshipErrorCode`
File: `domain/social/FriendshipErrorCode.java`

All 8 codes:
- `FRIEND_REQUEST_SELF_FORBIDDEN`
- `FRIEND_REQUEST_ALREADY_FRIENDS`
- `FRIEND_REQUEST_ALREADY_PENDING`
- `FRIEND_REQUEST_BLOCKED`
- `FRIEND_REQUEST_NOT_FOUND`
- `FRIEND_REQUEST_NOT_PARTICIPANT`
- `FRIEND_REQUEST_ALREADY_RESOLVED`
- `FRIEND_REMOVE_NOT_FRIENDS`

### Step 4: Extended `SocialRepository` interface
File: `domain/social/SocialRepository.java`

Added 7 new method signatures (all existing methods kept unchanged):
- `saveFriendship(Friendship friendship)`
- `findFriendshipById(String friendshipId)` → `Optional<Friendship>`
- `findPendingFriendship(UUID requesterId, UUID addresseeId)` → `Optional<Friendship>`
- `findIncomingPendingRequests(UUID addresseeId)` → `List<Friendship>`
- `findOutgoingPendingRequests(UUID requesterId)` → `List<Friendship>`
- `getAcceptedFriendIds(UUID userId)` → `List<UUID>`
- `removeFriendship(UUID userId1, UUID userId2)`

### Step 5: Implemented new methods in `SocialJdbcRepository`
File: `infrastructure/repository/social/SocialJdbcRepository.java`

**RowMapper pattern used:**
```java
private static final RowMapper<Friendship> FRIENDSHIP_MAPPER =
    (rs, rowNum) -> new Friendship(
        rs.getString("friendship_id"),
        UUID.fromString(rs.getString("requester_id")),
        UUID.fromString(rs.getString("addressee_id")),
        rs.getString("status"),
        rs.getLong("version"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant()
    );
```

Queries use `friendship_id::text`, `requester_id::text`, `addressee_id::text` casts so `rs.getString()` works correctly for UUID columns.

**CAST pattern for friend_status enum:**
All SQL literals use `CAST('PENDING' AS friend_status)` etc., matching the existing V104 pattern in `follow()`.

**saveFriendship uses ON CONFLICT (requester_id, addressee_id)** — requires a UNIQUE constraint on `(requester_id, addressee_id)` in the DB. This constraint already exists (evidenced by V104's `ON CONFLICT (requester_id, addressee_id) DO NOTHING` in `follow()`).

## Migration File
**NO migration file was created.** The `friendship` table already exists from V104. The UNIQUE constraint on `(requester_id, addressee_id)` is confirmed present by the existing `follow()` implementation which relies on it.
