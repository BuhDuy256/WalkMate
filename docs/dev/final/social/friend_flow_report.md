# Friend Flow — Comprehensive Technical Report

**Date:** 2026-04-26  
**Scope:** End-to-end Friend/Social flow — Android Frontend (Java) ↔ Spring Boot Backend

---

## Table of Contents

1. [Abstract Logic — Frontend](#1-abstract-logic--frontend)
2. [Abstract Logic — Backend](#2-abstract-logic--backend)
3. [Backend API ↔ Frontend Mapping](#3-backend-api--frontend-mapping)
4. [Use Cases and Trigger Scenes](#4-use-cases-and-trigger-scenes)
5. [Public Profile vs. Private Profile — Data & Access](#5-public-profile-vs-private-profile--data--access)
6. [UI Edge Cases](#6-ui-edge-cases)

---

## 1. Abstract Logic — Frontend

### 1.1 Package Structure

The social / friends feature follows the canonical MVVM + sub-feature layout defined in `Frontend_VI.md`:

```
ui/social/friends/
    FriendsFragment.java          ← container (ViewPager2 + TabLayout)
    FriendsPagerAdapter.java      ← wires 3 tab fragments
    FriendsViewModel.java         ← shared VM, scoped to FriendsFragment
    FriendsViewModelFactory.java
    FriendsUiState.java           ← immutable snapshot
    FriendListFragment.java       ← Tab 0: accepted friends
    IncomingRequestsFragment.java ← Tab 1: pending requests to me
    OutgoingRequestsFragment.java ← Tab 2: requests I sent (status only)
    FriendsAdapter.java           ← RecyclerView adapter (friends list)
    FriendRequestsAdapter.java    ← RecyclerView adapter (requests, dual-mode)

ui/profile/publicprofile/
    PublicProfileFragment.java    ← friend actions embedded in public profile
    PublicProfileViewModel.java   ← orchestrates 4 parallel loads + friendship mutations
    PublicProfileUiState.java
    PublicProfileViewModelFactory.java
    ReviewAdapter.java

ui/history/
    SessionHistoryFragment.java   ← entry point to partner's public profile
    SessionHistoryAdapter.java    ← partner name is clickable → PublicProfileFragment
```

### 1.2 FriendsViewModel — Parallel Load Pattern

`FriendsViewModel.loadAll()` is the single public load method. It fires **three repository calls in parallel** using an `AtomicInteger` barrier:

```
loadAll()
    ├── socialRepository.getFriends(...)         → friendsHolder
    ├── socialRepository.getIncomingRequests(...) → incomingHolder  (non-fatal)
    └── socialRepository.getOutgoingRequests(...) → outgoingHolder  (non-fatal)
                        ↓ all 3 complete (AtomicInteger.incrementAndGet() == 3)
    postValue(new FriendsUiState(friends, incoming, outgoing))
```

- **Incoming / outgoing failures are non-fatal** — those holders remain empty lists; the friend list error is fatal and propagates to `FriendsUiState.error(msg)`.
- After any mutation (`acceptRequest`, `declineRequest`, `removeFriend`), `loadAll()` is called again to refresh all three lists atomically.
- The badge count on the "Incoming" tab header is derived directly from `incomingRequests.size()` inside `FriendsUiState` — no separate counter call.

### 1.3 PublicProfileViewModel — Parallel Load + Friendship State Machine

`PublicProfileViewModel.loadProfile(userId)` fires **four parallel calls** with the same AtomicInteger barrier:

```
loadProfile(userId)
    ├── socialRepo.getPublicProfile(userId)       → profileHolder  (FATAL if fails)
    ├── gamificationRepo.getBadges(userId)        → badgesHolder   (non-fatal)
    ├── gamificationRepo.getStats(userId)         → statsHolder    (non-fatal)
    └── reviewRepo.getReviewsForUser(userId)      → reviewsHolder  (non-fatal)
                    ↓ all 4 complete
    postValue(PublicProfileUiState(profile, badges, stats, reviews, friendshipStatus, isSelf))
```

The `friendshipStatus` is read from `UserSummary.getFriendshipStatus()` returned by the profile call. It is a **server-resolved string** (`NONE | PENDING_SENT | PENDING_RECEIVED | FRIENDS`), meaning the backend computes this relative to the authenticated caller.

For the `PENDING_RECEIVED` case, `UserSummary.getPendingRequestId()` carries the `friendshipId` so the Fragment can pass it directly to `acceptIncomingRequest()` / `declineIncomingRequest()` without a separate lookup.

After **any** mutation (send, accept, decline, remove, block), the VM calls `reloadCurrent()` which re-runs `loadProfile(currentUserId)` — keeping the UI always consistent with server truth.

### 1.4 FriendRequestsAdapter — Dual-Mode Pattern

`FriendRequestsAdapter(boolean showActions)` is instantiated in two modes:

| Mode | Fragment | `showActions` | Rendered |
|------|----------|--------------|---------|
| Incoming | `IncomingRequestsFragment` | `true` | Accept + Decline buttons |
| Outgoing | `OutgoingRequestsFragment` | `false` | "Pending" label only, no buttons |

The adapter's `bind()` uses `request.getSenderName()` in **both** directions. For the outgoing tab the caller (Repository mapper) sets the receiver's name in the `senderName` field because the data model is generic — the adapter doesn't distinguish directionality.

### 1.5 Session History → Friend Request Trigger

`SessionHistoryFragment` is an entry point for discovering new walk partners:

```
SessionHistoryAdapter.onBindViewHolder()
    → holder.txtParticipant2Name.setOnClickListener
        → partnerClickListener.onPartnerClick(partnerId)
            → NavController.navigate(action_sessionHistory_to_publicProfileFragment, args)
                → PublicProfileFragment loads partner's profile with friendshipStatus
```

The user then interacts with the friendship action buttons directly on the Public Profile screen.

### 1.6 Frontend Domain Models

| Class | Layer | Purpose |
|-------|-------|---------|
| `UserSummary` | `domain.social` | Lightweight user representation: `userId`, `fullName`, `avatarUrl`, `friendshipStatus`, `bio`, `tags`, `pendingRequestId` |
| `FriendRequest` | `domain.social` | Friendship row: `requestId`, `senderId`, `senderName`, `senderAvatarUrl`, `receiverId`, `status`, `createdAt` |
| `SocialRepository` (interface) | `domain.social` | Contract for all friend + block operations via `DomainCallback<T>` |

---

## 2. Abstract Logic — Backend

### 2.1 Layered Architecture

```
FriendsController / SocialController
    ↓ extracts UUID from JWT (@AuthenticationPrincipal UserPrincipal)
    ↓ delegates to Application Service
FriendCommandService / FriendQueryService / SocialCommandService / SocialQueryService
    ↓ enforces business rules, calls domain entity state transitions
Friendship (Rich Domain Entity)
    ↓ friendship.accept() / friendship.decline() — self-guards via guardPending()
SocialRepository (domain interface)
    ↓ implemented by
SocialJdbcRepository (infrastructure, JdbcClient)
    ↓ reads/writes `friendship` + `block_relation` tables
```

### 2.2 FriendCommandService — Business Rules

#### `sendFriendRequest(callerId, targetId)`

1. Reject self-request → `FRIEND_REQUEST_SELF_FORBIDDEN`
2. Verify target user exists → `SOCIAL_USER_NOT_FOUND`
3. Reject if either party blocks the other → `FRIEND_REQUEST_BLOCKED`
4. Reject if already accepted friends → `FRIEND_REQUEST_ALREADY_FRIENDS`
5. Look up existing PENDING row (bidirectional query):
   - **If target is the requester of the existing row** (cross-request scenario) → auto-call `acceptFriendRequest()` — avoids duplicate rows, resolves the conflict silently.
   - **If caller is already the requester** → `FRIEND_REQUEST_ALREADY_PENDING`
6. If no existing row: `Friendship.create(callerId, targetId)` → INSERT → publish `FRIEND_REQUEST_RECEIVED` notification to target.

#### `acceptFriendRequest(callerId, friendshipId)`

1. Load row by `friendshipId` → `FRIEND_REQUEST_NOT_FOUND`
2. Verify `callerId == addresseeId` → `FRIEND_REQUEST_NOT_PARTICIPANT`
3. `friendship.accept()` — `guardPending()` throws `FRIEND_REQUEST_ALREADY_RESOLVED` if not PENDING.
4. UPSERT the updated row (status → ACCEPTED, version + 1).
5. Publish `FRIEND_REQUEST_ACCEPTED` notification to requester.

#### `declineFriendRequest(callerId, friendshipId)`

Same guards as accept but calls `friendship.decline()` → status → DECLINED. Publishes `FRIEND_REQUEST_DECLINED` notification.

#### `removeFriend(callerId, targetId)`

1. Verify target exists.
2. Verify `areAcceptedFriends` → `FRIEND_REMOVE_NOT_FRIENDS`
3. `removeFriendship(userId1, userId2)` → `UPDATE friendship SET status = 'DECLINED'` (soft delete, preserves row history).

### 2.3 Friendship Domain Entity — State Machine

```
         sendFriendRequest()
NONE ───────────────────────► PENDING
                                  │
               accept()           │  decline() / removeFriend()
                  ↓               │        ↓
              ACCEPTED ◄──────────┘     DECLINED
                  │
           removeFriend()
                  ↓
              DECLINED (soft update)
```

`guardPending()` is the invariant guard inside `Friendship.accept()` and `Friendship.decline()`. Calling either on a non-PENDING row throws `FRIEND_REQUEST_ALREADY_RESOLVED` — the entity protects its own state.

### 2.4 SocialJdbcRepository — Schema Design

The repository stores all friend / follow / block state in two tables:

**`friendship`** (`friendship_id`, `requester_id`, `addressee_id`, `status: friend_status`, `version`, `created_at`, `updated_at`)

- `status` is a PostgreSQL enum: `PENDING | ACCEPTED | DECLINED`
- Upsert uses `ON CONFLICT (requester_id, addressee_id) DO UPDATE SET status = EXCLUDED.status, version = version + 1`
- `findPendingFriendship` queries **bidirectionally** (both `(A→B)` and `(B→A)` directions) to detect cross-requests.
- `getAcceptedFriendIds` uses a `CASE WHEN requester_id = :userId THEN addressee_id ELSE requester_id END` to return the "other party" UUID regardless of who initiated.
- `removeFriendship` soft-deletes by setting `status = 'DECLINED'` — does not `DELETE` rows.

**`block_relation`** (`blocker_id`, `blocked_id`)

- `getBlockedAndBlockerIds` executes a `UNION` of both directions in one query — used by the matching engine to filter candidates.
- `block()` also silently un-follows both directions (`unfollow(A,B)` + `unfollow(B,A)`) before inserting the block row.

> **Note (V104 migration):** The legacy `follow_relation` table was replaced by remapping follow semantics onto the `friendship` table. `follow(A,B)` now inserts a `PENDING` friendship row; `isFollowing` checks for `PENDING | ACCEPTED` rows.

### 2.5 Notification Side-Effects

Every command publishes an in-app notification via `NotificationPublisher`:

| Event | Recipient | Payload keys |
|-------|-----------|--------------|
| `FRIEND_REQUEST_RECEIVED` | addressee | `friendshipId`, `requesterId` |
| `FRIEND_REQUEST_ACCEPTED` | requester | `friendshipId` |
| `FRIEND_REQUEST_DECLINED` | requester | `friendshipId` |

---

## 3. Backend API ↔ Frontend Mapping

All calls go through `SocialRepository` (frontend interface) → `SocialRepositoryImpl` (data layer) → HTTP via Retrofit → Spring REST controller.

| # | HTTP Method & Path | Controller | Application Service | Frontend Caller | Purpose |
|---|---|---|---|---|---|
| 1 | `POST /api/v1/friends/{userId}/request` | `FriendsController.sendFriendRequest` | `FriendCommandService.sendFriendRequest` | `PublicProfileViewModel.sendFriendRequest()` | UC-34: Send friend request |
| 2 | `POST /api/v1/friends/requests/{requestId}/accept` | `FriendsController.acceptFriendRequest` | `FriendCommandService.acceptFriendRequest` | `FriendsViewModel.acceptRequest()`, `PublicProfileViewModel.acceptIncomingRequest()` | UC-35: Accept request |
| 3 | `POST /api/v1/friends/requests/{requestId}/decline` | `FriendsController.declineFriendRequest` | `FriendCommandService.declineFriendRequest` | `FriendsViewModel.declineRequest()`, `PublicProfileViewModel.declineIncomingRequest()` | UC-35: Decline request |
| 4 | `DELETE /api/v1/friends/{userId}` | `FriendsController.removeFriend` | `FriendCommandService.removeFriend` | `FriendsViewModel.removeFriend()`, `PublicProfileViewModel.removeFriend()` | UC-36: Remove friend |
| 5 | `GET /api/v1/friends` | `FriendsController.getFriends` | `FriendQueryService.getFriends` | `FriendsViewModel.loadAll()` (branch: getFriends) | UC-37: Get accepted friends list |
| 6 | `GET /api/v1/friends/requests/incoming` | `FriendsController.getIncomingRequests` | `FriendQueryService.getIncomingRequests` | `FriendsViewModel.loadAll()` (branch: getIncomingRequests) | UC-38: View pending incoming requests |
| 7 | `GET /api/v1/friends/requests/outgoing` | `FriendsController.getOutgoingRequests` | `FriendQueryService.getOutgoingRequests` | `FriendsViewModel.loadAll()` (branch: getOutgoingRequests) | UC-38: View sent requests |
| 8 | `GET /api/v1/users/{userId}` | `UserProfileController.getPublicProfile` | `UserQueryService.getProfile` | `PublicProfileViewModel.loadProfile()` (via `SocialRepository.getPublicProfile`) | Load public profile + friendship status |
| 9 | `GET /api/v1/users/me/friends` | `SocialController.getFriends` | `SocialQueryService.getFriends → FriendQueryService` | Private walk intent friend-picker (UC-08) | UC-08 friend-picker for private intents |
| 10 | `POST /api/v1/users/{userId}/block` | `SocialController.block` | `SocialCommandService.block` | `PublicProfileViewModel.blockUser()` | Block a user from their public profile |
| 11 | `DELETE /api/v1/users/{userId}/block` | `SocialController.unblock` | `SocialCommandService.unblock` | `BlockedUsersViewModel.unblock()` | Unblock from Blocked Users screen |

**Response DTOs:**

- `FriendshipResponse` — `{ friendship_id, requester_id, addressee_id, status, created_at }`
- `UserSummaryResponse` — `{ userId, fullName, avatarUrl }` (used for friend-list entries)
- `UserProfileResponse` — full profile payload (name, bio, tags, avatarUrl, etc.) — used for public profile loads

---

## 4. Use Cases and Trigger Scenes

| UC | Name | Trigger Scene | Initiator |
|----|------|--------------|-----------|
| **UC-34** | Send Friend Request | User taps **"Add Friend"** button on `PublicProfileFragment` | `btnAddFriend.setOnClickListener` → `viewModel.sendFriendRequest(currentUserId)` |
| **UC-34b** | Auto-Accept Cross-Request | User A sends a request to User B; User B had already sent a request to User A | Backend: `FriendCommandService.sendFriendRequest` detects reverse PENDING row and auto-calls `acceptFriendRequest()` |
| **UC-35a** | Accept Friend Request | User taps **"Accept"** in `IncomingRequestsFragment` or `PublicProfileFragment` (PENDING_RECEIVED state) | `FriendsViewModel.acceptRequest(requestId)` or `PublicProfileViewModel.acceptIncomingRequest(pendingRequestId)` |
| **UC-35b** | Decline Friend Request | User taps **"Decline"** in `IncomingRequestsFragment` or `PublicProfileFragment` | `FriendsViewModel.declineRequest(requestId)` or `PublicProfileViewModel.declineIncomingRequest(pendingRequestId)` |
| **UC-36** | Remove Friend | User taps **"Remove Friend"** on `FriendListFragment` (shows confirmation dialog) or on `PublicProfileFragment` (FRIENDS state) | `FriendsViewModel.removeFriend(userId)` or `PublicProfileViewModel.removeFriend(currentUserId)` |
| **UC-37** | View Friends List | User navigates to `ProfileFragment → "Friends" menu row` → `FriendsFragment` loads, `loadAll()` fires | `ProfileFragment.menuFriends.setOnClickListener` → NavController → `FriendsFragment.onViewCreated` → `viewModel.loadAll()` |
| **UC-38** | View Pending Requests | `FriendsFragment` loads (same `loadAll()` call) — Incoming tab shows requests + badge count | Automatic on `FriendsFragment` creation. Also accessible via deep-link `Bundle{scrollToTab: 1}` (e.g., from FCM notification tap) |
| **UC-08** | Private Walk Intent Friend-Picker | User creates a **private** WalkIntent; app fetches `GET /api/v1/users/me/friends` to populate friend picker | Called from the WalkIntent creation flow (out of Friends scope) |
| **Session → Profile** | Discover Partner After Walk | User taps partner name in `SessionHistoryFragment` | `SessionHistoryAdapter.partnerClickListener.onPartnerClick(partnerId)` → NavController → `PublicProfileFragment` |
| **Proposal → Profile** | View Proposer During Matching | User taps proposer's avatar/name in `ProposalFragment` | NavController → `PublicProfileFragment` |
| **Block** | Block User | User taps overflow menu **"Block User"** on `PublicProfileFragment` | `PublicProfileViewModel.blockUser(currentUserId)` → `SocialRepository.block()` → `POST /api/v1/users/{userId}/block` → auto-navigates back |

---

## 5. Public Profile vs. Private Profile — Data & Access

### 5.1 Public Profile

**Screen:** `PublicProfileFragment`  
**API:** `GET /api/v1/users/{userId}` (unauthenticated endpoint — no auth header required by the controller signature, though the app always sends the token)  

**Data displayed:**

| Field | Source |
|-------|--------|
| Avatar | `UserProfileResponse.avatarUrl` → Glide loaded via `AvatarInitialView` |
| Full Name | `UserProfileResponse.fullName` |
| Bio | `UserProfileResponse.bio` |
| Personality Tags | `UserProfileResponse.tags` (chip group) |
| Total Distance (km) | `UserStats.totalDistanceKm` from `GET /api/v1/gamification/users/{userId}/stats` |
| Completed Sessions | `UserStats.completedSessions` |
| Trust Score | `UserStats.trustScore` |
| Badges | `UserBadge[]` from `GET /api/v1/gamification/users/{userId}/badges` |
| Walk Reviews | `WalkReview[]` from `GET /api/v1/reviews/users/{userId}` |
| Friendship Status | Embedded in `UserSummary.friendshipStatus` (server-resolved) |
| Friendship Action Buttons | Rendered per `friendshipStatus` (see §6) |

**What is NOT shown on Public Profile:**

- Email address
- Date of birth
- `visibilityMode` setting
- Session GPS route history

### 5.2 Private Profile (Own Profile)

**Screen:** `ProfileFragment` (bottom nav tab)  
**API:** `GET /api/v1/profile/me` (auth required — returns full `UserProfileResponse`)  

**Additional data visible only to the owner:**

| Field | Notes |
|-------|-------|
| Trust Tier label (Elite / Standard / Restricted) | Computed from trust score |
| Trust Score chip (numeric) | Shown prominently |
| Edit Profile entry point | `btnEditProfile` → `EditProfileFragment` |
| Walk History menu row | → `SessionHistoryFragment` |
| My Badges menu row | → badges detail |
| Leaderboard menu row | → Leaderboard |
| Settings menu row | → Settings |
| Friends menu row | → `FriendsFragment` |
| Blocked Users menu row | → `BlockedUsersFragment` |
| Logout All Devices button | Calls session invalidation API |

**Access mechanism to "Private Profile":**

The Private Profile (`ProfileFragment`) is the bottom-nav **Profile tab** and is only reachable when the user is authenticated. The `SessionManager.getUserId()` supplies the local user ID. When a user views their **own userId** via `PublicProfileFragment` (rare, e.g. from a notification deep-link), `isSelf == true` is set by comparing the passed `userId` argument against `localUserId`. This suppresses **all** friendship action buttons (`layoutFriendshipActions.setVisibility(GONE)`) and the overflow menu — the same Public Profile shell becomes a read-only view of themselves.

---

## 6. UI Edge Cases

### 6.1 Simultaneous Cross-Request ("You also sent the request!")

**Scenario:** User A is viewing User B's public profile. User B has already sent a request to User A (currently sitting in A's Incoming tab). User A taps "Add Friend".

**Frontend behavior:**

When `PublicProfileViewModel.loadProfile(B)` runs, `socialRepo.getPublicProfile(B)` returns a `UserSummary` with `friendshipStatus = "PENDING_RECEIVED"` (because there is already a PENDING row where B is the requester). The Fragment's `renderFriendshipActions()` method therefore renders:

```java
case "PENDING_RECEIVED":
    pendingRequestId = profile.getPendingRequestId();
    btnAcceptRequest.setVisibility(View.VISIBLE);
    btnDeclineRequest.setVisibility(View.VISIBLE);
    break;
```

The "Add Friend" button is **never shown** — it is replaced by Accept + Decline. The user cannot create a duplicate outgoing request because the button is hidden by the server-resolved `friendshipStatus`.

**Backend safety net (if frontend is bypassed):**

Even if `POST /api/v1/friends/{B}/request` is called directly, `FriendCommandService.sendFriendRequest` detects the cross-request:

```java
Optional<Friendship> existing = socialRepository.findPendingFriendship(callerId, targetId);
if (existing.isPresent()) {
    Friendship ex = existing.get();
    if (ex.getRequesterId().equals(targetId)) {
        // B already sent to A — auto-accept instead of duplicating
        return acceptFriendRequest(callerId, ex.getFriendshipId());
    } else {
        // A already sent to B
        throw new DomainException(FRIEND_REQUEST_ALREADY_PENDING);
    }
}
```

`findPendingFriendship` searches **bidirectionally** (`(A→B) OR (B→A)`), so this guard works regardless of direction. The result is an **automatic acceptance** — no error, no duplicate row — and both parties become friends immediately.

**UI resolution after auto-accept:** `sendFriendRequest` succeeds → `reloadCurrent()` → `loadProfile(B)` returns `friendshipStatus = "FRIENDS"` → Fragment renders `btnInviteWalk + btnRemoveFriend`. The user sees the profile flip to the "friends" state without any error message.

### 6.2 Request Rejected — "Add Friend" Button Reappears

**Scenario:** User A sent a request to User B. User B declined. User A revisits User B's public profile.

**Backend state after decline:**

`FriendCommandService.declineFriendRequest` calls `friendship.decline()` which sets `status = "DECLINED"`. The row is soft-stored (not deleted). `findPendingFriendship` filters on `status = 'PENDING'`, so the DECLINED row is invisible to subsequent queries.

**What the profile endpoint returns:**

`GET /api/v1/users/{B}` does NOT expose the `friendshipStatus` field directly — this field is computed by the frontend's `SocialRepository` implementation, which must perform a relationship lookup. When the existing row is DECLINED:

- `findPendingFriendship(A, B)` → `Optional.empty()` (no PENDING row)
- `areAcceptedFriends(A, B)` → `false` (no ACCEPTED row)
- No outgoing PENDING row → `findOutgoingPendingRequests(A)` will not contain B

The repository maps this to `friendshipStatus = "NONE"`.

**Frontend rendering:**

```java
case "NONE":
    btnAddFriend.setVisibility(View.VISIBLE);
    break;
```

The "Add Friend" button reappears. The prior DECLINED row does not prevent A from sending a new request — `sendFriendRequest` only blocks on PENDING or ACCEPTED rows. A new `Friendship.create(A, B)` row is inserted (the DECLINED row remains in history but does not conflict due to the UPSERT logic: `ON CONFLICT (requester_id, addressee_id) DO UPDATE`).

### 6.3 Removing a Friend — Confirmation Dialog

`FriendListFragment` shows a confirmation `AlertDialog` before calling `removeFriend`:

```java
new AlertDialog.Builder(requireContext())
    .setTitle("Remove Friend")
    .setMessage("Remove " + displayName + " from your friends?")
    .setPositiveButton("Remove", (d, w) -> viewModel.removeFriend(userId))
    .setNegativeButton("Cancel", null)
    .show();
```

After removal, `loadAll()` refreshes all three lists. The removed user disappears from the Friends tab and may be re-added via "Add Friend" on their public profile.

### 6.4 Outgoing Tab — No Action Buttons

`OutgoingRequestsFragment` instantiates `FriendRequestsAdapter(false)`. This means:

- `btnAccept` and `btnDecline` are `GONE`
- `txtPending` ("Pending…") is `VISIBLE`

There is intentionally **no "Cancel Request" button** in the current implementation. A user who wants to cancel an outgoing request must navigate to the recipient's public profile, where they would see `btnRequestSent` (read-only label, no tap action). The cancel flow is not yet implemented — the `btnRequestSent` button renders but has no `setOnClickListener` in the current codebase.

### 6.5 Block Navigates Away

When `PublicProfileViewModel.blockUser(userId)` succeeds, the VM posts `navigateBackEvent = true`:

```java
public void blockUser(String userId) {
    socialRepo.block(userId, new DomainCallback<Void>() {
        @Override public void onSuccess(Void v) {
            navigateBackEvent.postValue(true);   // ← triggers back navigation
        }
        ...
    });
}
```

The Fragment observes this and calls `navigateBack()`. The blocked user's profile is no longer accessible from the caller's perspective, and the SocialCommandService also silently tears down any follow relationships in both directions on the backend.

### 6.6 Badge Count on Incoming Tab

`FriendsFragment` observes `LiveData<FriendsUiState>` and updates the "Incoming" tab text dynamically:

```java
int count = state.getIncomingBadgeCount();  // == incomingRequests.size()
if (count > 0) {
    incomingTab.setText("Incoming (" + count + ")");
} else {
    incomingTab.setText("Incoming");
}
```

The badge is recalculated on every `loadAll()` completion — accepting or declining one request triggers a full refresh, and the badge count drops accordingly.

### 6.7 isSelf Guard on Public Profile

When a user navigates to their own profile via `PublicProfileFragment` (e.g., from a notification link):

```java
final boolean isSelf = userId != null && userId.equals(localUserId);
```

- `layoutFriendshipActions.setVisibility(View.GONE)` — all friend action buttons hidden
- `btnOverflowMenu.setVisibility(View.GONE)` — block/report menu hidden

This prevents self-friending, self-blocking, and confusing UI states without any extra API round-trip.

---

*End of Friend Flow Report*
