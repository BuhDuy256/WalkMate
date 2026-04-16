# WalkMate — Social Use Cases

> Part of: [Use Cases Index](README.md)

**Domain:** Friendship for Private Walk Invites + Blocking
**Last Updated:** 2026-04-13

---

## Table of Contents

| UC# | Use Case | API Endpoint |
|-----|----------|--------------|
| UC-33 | [View a Public User Profile](#uc-33--view-a-public-user-profile) | `GET /api/v1/users/{userId}` |
| UC-34 | [Send a Friend Request](#uc-34--send-a-friend-request) | `POST /api/v1/friends/{userId}/request` |
| UC-35 | [Respond to a Friend Request (Accept/Decline)](#uc-35--respond-to-a-friend-request-acceptdecline) | `POST /api/v1/friends/requests/{requestId}/accept` / `decline` |
| UC-36 | [View Friends and Friend Requests](#uc-36--view-friends-and-friend-requests) | `GET /api/v1/friends` + request lists + `DELETE /api/v1/friends/{userId}` |
| UC-37 | [Block a User](#uc-37--block-a-user) | `POST /api/v1/users/{userId}/block` |
| UC-38 | [Unblock a User](#uc-38--unblock-a-user) | `DELETE /api/v1/users/{userId}/block` |

---

### UC-33 — View a Public User Profile

**Use Case Name:** View a Public User Profile

**Initial assumption:** User taps on another user's name/avatar anywhere in the app (from session history, proposal, friends list, etc.). Authentication is not required to view profile data. Authentication is required for friendship actions and blocking.

**Normal:**
1. UI calls `GET /api/v1/users/{userId}`.
2. Backend returns `200 OK` with public profile data.
3. UI calls `GET /api/v1/users/{userId}/badges` and `GET /api/v1/users/{userId}/stats` in parallel.
4. UI calls `GET /api/v1/users/{userId}/reviews` to show review feed.
5. UI renders full public profile page: avatar, name, bio, tags, trust score, stats, badges, reviews.
6. If viewing another user's profile (not self), show friendship actions based on relationship state:
   - Not connected: `Add Friend`
   - Outgoing request pending: `Request Sent`
   - Incoming request pending: `Accept` / `Decline`
   - Already friends: `Invite Walk` and `Remove Friend`
   - Always available in overflow: `Block`
7. **Unauthenticated guard:** If user is not logged in and taps `Add Friend`, `Accept`, `Decline`, `Remove Friend`, or `Block`, do not call API. Navigate to Login and show: "Log in to manage friendships."

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| User not found | `USER_NOT_FOUND` | Show toast: "User not found." Navigate back. |

**Other activities:** None.

**System state on completion:** Read-only profile data is visible; friendship actions are available only for authenticated users.

---

### UC-34 — Send a Friend Request

**Use Case Name:** Send a Friend Request

**Initial assumption:** User is authenticated, viewing another user's profile, and both users are not already friends.

**Normal:**
1. User taps `Add Friend`.
2. UI optimistically changes action to `Request Sent`.
3. UI calls `POST /api/v1/friends/{userId}/request`.
4. Backend returns `200 OK`. Friend request moves to `PENDING`.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Target user not found | `SOCIAL_USER_NOT_FOUND` | Revert UI state. Show toast: "User not found." |
| Trying to add self | `FRIEND_REQUEST_SELF_FORBIDDEN` | Hide `Add Friend` on own profile. |
| Already friends | `FRIEND_REQUEST_ALREADY_FRIENDS` | Show state `Already friends`. |
| Request already pending | `FRIEND_REQUEST_ALREADY_PENDING` | Keep `Request Sent` state. |
| Either side is blocked | `FRIEND_REQUEST_BLOCKED` | Show toast: "Cannot send friend request." |

**Other activities:** Target user receives a notification for incoming friend request.

**System state on completion:** Request is `PENDING`; users are not friends yet, so private walk invite is still unavailable.

---

### UC-35 — Respond to a Friend Request (Accept/Decline)

**Use Case Name:** Respond to a Friend Request

**Initial assumption:** User is authenticated and has at least one incoming friend request in `PENDING` status.

**Normal:**
1. User opens incoming friend requests list.
2. For each request, user chooses `Accept` or `Decline`.
3. UI calls:
   - Accept: `POST /api/v1/friends/requests/{requestId}/accept`
   - Decline: `POST /api/v1/friends/requests/{requestId}/decline`
4. Backend returns `200 OK`.
5. On Accept:
   - Friendship becomes `ACCEPTED`.
   - Both users appear in each other's friends list.
   - Both users can use private walk invite flow in UC-15 (`invited_friend_id`).
6. On Decline:
   - Request moves to terminal declined state.
   - No friendship is created.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Request not found | `FRIEND_REQUEST_NOT_FOUND` | Show toast: "Request no longer exists." Refresh list. |
| User not part of request | `FRIEND_REQUEST_NOT_PARTICIPANT` | Show toast: "Permission denied." |
| Request already resolved | `FRIEND_REQUEST_ALREADY_RESOLVED` | Refresh list and remove stale item. |

**Other activities:** Request sender receives status notification (accepted/declined).

**System state on completion:** Friendship is either established (`ACCEPTED`) or request is closed (`DECLINED`).

---

### UC-36 — View Friends and Friend Requests

**Use Case Name:** View Friends and Friend Requests

**Initial assumption:** User is authenticated and opens social section from Profile.

**Normal:**
1. UI opens tabs:
   - `Friends`
   - `Incoming Requests`
   - `Sent Requests`
2. UI calls:
   - `GET /api/v1/friends`
   - `GET /api/v1/friends/requests/incoming`
   - `GET /api/v1/friends/requests/outgoing`
3. Backend returns lists with basic user card fields (`userId`, `fullName`, `avatarUrl`).
4. `Friends` list items provide quick actions:
   - `Invite Walk` (deep-link to UC-15 Create Intent with `is_private=true` and preselected `invited_friend_id`)
   - `Remove Friend` (confirmation, then `DELETE /api/v1/friends/{userId}`)
   - `View Profile` (UC-33)
5. If user confirms `Remove Friend`:
   - Backend returns `200 OK`.
   - Both users are removed from each other's friends lists.
   - Private invite is no longer available until a new friend request is accepted.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Network failure | `N/A` | Show cached list (if available) and retry option. |
| Remove target is not an accepted friend | `FRIEND_REMOVE_NOT_FRIENDS` | Refresh friends list and hide `Remove Friend` for that user. |
| Target user not found | `SOCIAL_USER_NOT_FOUND` | Show toast: "User not found." Refresh list. |

**Other activities:** None.

**System state on completion:** User can manage friendship network, remove existing friends, and quickly pick a friend for private invite flow.

---

### UC-37 — Block a User

**Use Case Name:** Block a User

**Initial assumption:** User is authenticated and viewing another user's profile.

**Normal:**
1. User selects `Block User` from overflow menu.
2. UI shows confirmation dialog: "Block [name]? You cannot invite each other to private walks while blocked."
3. User confirms.
4. UI calls `POST /api/v1/users/{userId}/block`.
5. Backend returns `200 OK`.
6. System side effects:
   - Existing friendship is removed if present.
   - Any pending friend requests between the two users are closed.
7. UI navigates back and removes blocked user from visible friendship lists.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Target user not found | `SOCIAL_USER_NOT_FOUND` | Show toast: "User not found." |
| Trying to block self | `BLOCK_SELF_BLOCK_FORBIDDEN` | Hide `Block` on own profile. |
| Already blocked | `BLOCK_ALREADY_BLOCKED` | Keep `Blocked` state; ignore duplicate action. |

**Other activities:** None.

**System state on completion:** Block relationship exists. Friendship/request links are severed. Users cannot invite each other while blocked.

---

### UC-38 — Unblock a User

**Use Case Name:** Unblock a User

**Initial assumption:** User is authenticated. The blocked user appears in a `Blocked Users` settings list.

**Normal:**
1. User taps `Unblock`.
2. UI calls `DELETE /api/v1/users/{userId}/block`.
3. Backend returns `200 OK`. Block relationship is removed.
4. UI removes user from blocked list.

**What can go wrong:**

| Condition | UI Reaction |
|-----------|------------|
| Network failure | Show toast: "Could not unblock. Try again." |

**Other activities:** None.

**System state on completion:** Block is removed. Friendship is not auto-restored; users must send a new friend request to reconnect.
