# Phase 6 Report: Friends System — Application & Presentation

## Steps Completed

### Step 1: Read existing code
- Read `SocialCommandService`, `SocialQueryService`, `SocialController`, `NotificationType`
- Confirmed `NotificationPublisher` domain interface at `domain/shared/NotificationPublisher.java`
- Confirmed `Notification.create(userId, type, payload)` factory pattern

### Step 2: Added notification types
File: `domain/notification/NotificationType.java`

Added:
- `FRIEND_REQUEST_RECEIVED` — sent to addressee when friend request arrives (UC-34)
- `FRIEND_REQUEST_ACCEPTED` — sent to requester when request is accepted (UC-35)
- `FRIEND_REQUEST_DECLINED` — sent to requester when request is declined (UC-35)

### Step 3: Created `FriendCommandService`
File: `application/social/FriendCommandService.java`

4 methods with full guard chains:

**`sendFriendRequest(callerId, targetId)`**
1. Self-request guard → `FRIEND_REQUEST_SELF_FORBIDDEN`
2. Target user existence → `SOCIAL_USER_NOT_FOUND` (UC-34 requirement)
3. Block check (both directions) → `FRIEND_REQUEST_BLOCKED`
4. Already friends → `FRIEND_REQUEST_ALREADY_FRIENDS`
5. Existing pending check (bidirectional):
   - B already requested A → auto-accept via `acceptFriendRequest`
   - A already requested B → `FRIEND_REQUEST_ALREADY_PENDING`
5. Creates new `Friendship`, saves, publishes `FRIEND_REQUEST_RECEIVED` to addressee

**`acceptFriendRequest(callerId, friendshipId)`**
- Guards: not found, not participant, already resolved (guarded by entity)
- Publishes `FRIEND_REQUEST_ACCEPTED` to requester

**`declineFriendRequest(callerId, friendshipId)`**
- Same guards as accept; publishes `FRIEND_REQUEST_DECLINED` to requester (per UC-35 SSOT)

**`removeFriend(callerId, targetId)`**
- Target user existence → `SOCIAL_USER_NOT_FOUND` (UC-36 requirement)
- Guard: not accepted friends → `FRIEND_REMOVE_NOT_FRIENDS`
- Calls `removeFriendship` which soft-deletes to DECLINED status

### Step 4: Created `FriendQueryService`
File: `application/social/FriendQueryService.java`

3 read-only methods:
- `getFriends(callerId)` → `List<UUID>` (accepted friend IDs)
- `getIncomingRequests(callerId)` → `List<Friendship>`
- `getOutgoingRequests(callerId)` → `List<Friendship>`

### Step 5: Fixed `SocialQueryService.getFriends()`
File: `application/social/SocialQueryService.java`

- Injected `FriendQueryService` via `@RequiredArgsConstructor`
- `getFriends()` now delegates to `friendQueryService.getFriends(callerId)`
- Removed stale comment "There is no dedicated Friendship table yet"
- Old implementation (`getFolloweeIds`) is no longer used for friends

### Step 6: Created `FriendshipResponse` DTO
File: `presentation/dto/response/social/FriendshipResponse.java`

```json
{
  "friendship_id": "...",
  "requester_id":  "...",
  "addressee_id":  "...",
  "status":        "PENDING|ACCEPTED|DECLINED",
  "created_at":    "2026-04-13T..."
}
```

### Step 7: Created `FriendsController`
File: `presentation/controller/social/FriendsController.java`

All 7 endpoints:

| Method | Path | UC | Response |
|---|---|---|---|
| `POST` | `/api/v1/friends/{userId}/request` | UC-34 | 201 `FriendshipResponse` |
| `POST` | `/api/v1/friends/requests/{requestId}/accept` | UC-35 | 200 `FriendshipResponse` |
| `POST` | `/api/v1/friends/requests/{requestId}/decline` | UC-35 | 200 `null` |
| `GET` | `/api/v1/friends` | UC-36 | 200 `List<UserSummaryResponse>` |
| `GET` | `/api/v1/friends/requests/incoming` | UC-36 | 200 `List<FriendshipResponse>` |
| `GET` | `/api/v1/friends/requests/outgoing` | UC-36 | 200 `List<FriendshipResponse>` |
| `DELETE` | `/api/v1/friends/{userId}` | UC-36 | 200 `null` |

### Step 8: Removed follow/unfollow endpoints from `SocialController`
File: `presentation/controller/social/SocialController.java`

Removed 4 endpoint handlers:
- `POST /api/v1/users/{userId}/follow` (`follow()`)
- `DELETE /api/v1/users/{userId}/follow` (`unfollow()`)
- `GET /api/v1/users/{userId}/followers` (`getFollowers()`)
- `GET /api/v1/users/{userId}/following` (`getFollowing()`)

Kept:
- `GET /api/v1/users/me/friends` (`getFriends()`) — still in spec
- `POST /api/v1/users/{userId}/block` (`block()`)
- `DELETE /api/v1/users/{userId}/block` (`unblock()`)

The underlying `SocialCommandService.unfollow` method was NOT removed — it is still called internally by the block flow (both directions) in `SocialCommandService.block()`. The `follow` method remains available but is no longer exposed via HTTP.

## Edge Cases

**friend_status enum casting:** All SQL string literals cast explicitly: `CAST('PENDING' AS friend_status)`, `CAST('ACCEPTED' AS friend_status)`, `CAST('DECLINED' AS friend_status)`. The `saveFriendship` INSERT uses `CAST(:status AS friend_status)` for the named parameter.

**Bidirectional pending detection:** `findPendingFriendship` uses OR condition to catch both A→B and B→A in one query. The service layer inspects `requesterId` to determine direction and auto-accept if B already requested A.

**`SocialQueryService.getFriends()` fix confirmed:** Now delegates to `FriendQueryService.getFriends()` → `getAcceptedFriendIds()` instead of the old `getFolloweeIds()`.
