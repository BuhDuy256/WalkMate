# Friend Flow — Update Plan & Architectural Report

**Date:** 2026-04-26  
**Role:** Senior System Architect (Android Native Java + Spring Boot) + Database Expert  
**Scope:** 4 new expectations on the Social/Friend flow

---

## Table of Contents

1. [Gap Analysis](#1-gap-analysis)
2. [Implementation Steps](#2-implementation-steps)
3. [Database Schema Assessment](#3-database-schema-assessment)
4. [Flyway Migration](#4-flyway-migration)

---

## 1. Gap Analysis

### 1.1 Expectation 1 — Partner Avatar on History Card + Navigate to Public Profile

**What is expected:**  
Each History card shows the partner's `AvatarInitialView` at the left of the partner row. Tapping either the avatar or the name navigates to the partner's Public Profile, where the user can send a friend request.

**Current state:**  
- `item_session_history.xml`: No avatar view. Only `txtParticipant2Name` exists for the partner row. The name is already wired to navigate to `PublicProfileFragment` via `SessionHistoryAdapter.setOnPartnerClickListener`.
- `ParticipantSummary` (frontend domain model): has no `avatarUrl` field.
- `ParticipantResponse` (frontend DTO inner class inside `SessionSummaryResponse`): has no `avatar_url` field.
- `ParticipantSummaryResponse` (backend DTO record): has no `avatar_url` field.
- `SessionHistoryQueryService.getSessionHistory()`: resolves only names via `profileRepository.findNamesByUserIds()`.
- `UserProfileRepository.findNamesByUserIds()`: returns only `Map<UUID, String>` (name only, no avatar).

**Gap — what is missing:**

| Layer | Missing piece |
|-------|--------------|
| DB | Nothing — `user_profile.avatar_url` already exists |
| Backend DTO | `avatar_url` field in `ParticipantSummaryResponse` |
| Backend Service | Batch avatar-URL fetch in `SessionHistoryQueryService.toSummary()` |
| Backend Repository | `findProfilesByUserIds()` batch method returning both name + avatar |
| Frontend DTO | `avatar_url` field in `SessionSummaryResponse.ParticipantResponse` |
| Frontend Domain | `avatarUrl` field in `ParticipantSummary` |
| Frontend Layout | `AvatarInitialView` at the start of the partner row in `item_session_history.xml` |
| Frontend Adapter | Bind avatar in `SessionHistoryAdapter.ViewHolder.bind()` |

**No architectural collision.** This is a straight additive change — extend the existing pipeline with avatar data.

---

### 1.2 Expectation 2 — Remove "Friends" Title Text

**What is expected:**  
Remove the centred "Friends" `TextView` from the top app bar of `FriendsFragment`.

**Current state:**  
`fragment_friends.xml` contains an anonymous `TextView` (`android:text="Friends"`) inside the top `ConstraintLayout`. There is no `android:id` on it.

**Gap:**  
Single XML edit: remove the `<TextView>` node. No Java changes required. No backend or DB impact.

---

### 1.3 Expectation 3 — Show Private Profile for Friends

**What is expected:**  
When User A navigates to User B's profile from any entry point (Proposal card, Session History card, History card, or the Friends list tab), AND `areAcceptedFriends(A, B) == true`, the app MUST display a **richer, friend-level ("Private") profile view** rather than the standard public profile.

**Current state:**  
All navigation to any other user's profile goes to `PublicProfileFragment` unconditionally, regardless of friendship status. The `isSelf` flag suppresses friendship action buttons when A views their own profile, but there is zero differentiation between "viewing a stranger" and "viewing a friend."

**Gap:**

| Layer | Missing piece |
|-------|--------------|
| Backend API | `GET /api/v1/users/{userId}` returns the same `UserProfileResponse` regardless of who calls it. It does not detect the caller–subject friendship relationship, so no friend-only fields are returned. |
| Frontend UiState | `PublicProfileUiState` has no `isFriend` flag. |
| Frontend Fragment | `PublicProfileFragment.renderState()` has no "friend view" rendering branch. |
| Frontend Navigation | All callers hard-code `navigate(R.id.action_xxx_to_publicProfileFragment)`. `FriendListFragment.onViewProfile()` navigates to `publicProfileFragment` even though friendship is guaranteed at that point. |

**Architectural Collision — Critical Analysis:**

The user's phrasing "show the Private Profile" creates an apparent clash with the existing screen taxonomy:

- `ProfileFragment` = authenticated user's **own** profile (`GET /api/v1/profile/me`)
- `PublicProfileFragment` = any **other** user's profile (`GET /api/v1/users/{userId}`)

Navigating friends directly to `ProfileFragment` is architecturally incorrect — it fetches the **caller's own** data from `GET /api/v1/profile/me`, not the friend's data.

**Resolution — Single-Fragment, Dual-Mode Pattern:**  
Rather than creating a new `FriendProfileFragment` (which would duplicate the entire profile layout and all navigation graph edges), the correct architecture is to **extend `PublicProfileFragment` with a `viewMode`** concept. The Fragment already receives a `userId` Bundle argument; a second optional `String viewMode` argument (`"PUBLIC"` | `"FRIEND"`) lets callers declare intent upfront when friendship is known, while the fragment falls back to server-resolved `friendshipStatus` when it is not.

- The backend extends `GET /api/v1/users/{userId}` to return **friend-only fields** (`lastActiveAt`, full `trustScore` displayed as numeric, etc.) when the authenticated caller and the requested user are accepted friends.
- `PublicProfileUiState` gains an `isFriend` boolean.
- `PublicProfileFragment.renderState()` gains a conditional `renderFriendExtras()` section that renders the additional data.
- `FriendListFragment.onViewProfile()` passes `viewMode = "FRIEND"` in the Bundle.
- Entry points that cannot know friendship in advance (Proposal, SessionHistory) navigate without the argument; the fragment resolves it from the loaded profile's `friendshipStatus`.

This approach:
- Requires **zero new Fragments** and **zero new nav graph edges**.
- Keeps `data/dto/` boundary clean — the enriched response is a superset of the current `PublicUserResponse`.
- Maintains the existing parallel-load pattern in `PublicProfileViewModel`.

---

### 1.4 Expectation 4 — Cancel Outgoing Friend Request

**What is expected:**  
A "Cancel Request" button appears on each card in the Outgoing Requests tab. Tapping it withdraws the pending request and removes the card from the list.

**Current state:**  
- `OutgoingRequestsFragment` instantiates `FriendRequestsAdapter(false)` — no action buttons rendered.
- No `cancelFriendRequest` method exists anywhere: not in `FriendsViewModel`, `SocialRepository` (frontend domain interface), `SocialRepositoryImpl`, `SocialApiService`, `FriendsController`, nor `FriendCommandService`.
- The `friend_status` enum contains only `PENDING | ACCEPTED | DECLINED`. There is no semantic separation between "addressee rejected" and "requester withdrew".

**Gap:**

| Layer | Missing piece |
|-------|--------------|
| DB | `CANCELLED` value missing from `friend_status` enum |
| Backend Domain | `Friendship.cancel()` method + guard |
| Backend Domain | `FriendshipErrorCode.FRIEND_REQUEST_NOT_REQUESTER` error code |
| Backend Service | `FriendCommandService.cancelFriendRequest(callerId, friendshipId)` |
| Backend Controller | `DELETE /api/v1/friends/requests/{requestId}` endpoint |
| Frontend API | `SocialApiService.cancelFriendRequest(@DELETE)` |
| Frontend Domain | `SocialRepository.cancelFriendRequest(requestId, callback)` |
| Frontend Data | `SocialRepositoryImpl.cancelFriendRequest()` |
| Frontend VM | `FriendsViewModel.cancelRequest(requestId)` |
| Frontend Adapter | `FriendRequestsAdapter` outgoing mode — expose a "Cancel" button |
| Frontend Fragment | `OutgoingRequestsFragment` — wire cancel action to ViewModel |

**Why add `CANCELLED` instead of reusing `DECLINED`:**  
`DECLINED` semantically means the addressee rejected the request. `CANCELLED` means the requester withdrew it. This distinction is important for:
- Audit history and notification logic (a future notification should not say "X declined your request" when you cancelled it yourself).
- The `findPendingFriendship` bidirectional query filters on `status = 'PENDING'` — both `DECLINED` and `CANCELLED` are invisible to it, so either value allows re-sending. However, the semantic distinction must be encoded at the DB level now before both are in use.

---

## 2. Implementation Steps

### 2.1 Frontend Changes

#### E1 — Partner Avatar on History Card

**Step 1 — Extend `ParticipantSummary` domain model**  
Add `avatarUrl` field + constructor parameter + getter.

**Step 2 — Extend frontend DTO `SessionSummaryResponse.ParticipantResponse`**  
Add `@SerializedName("avatar_url") private String avatarUrl;` + getter.

**Step 3 — Update `WalkSessionMapper` / wherever `ParticipantSummary` is built**  
Pass `avatarUrl` from `ParticipantResponse.getAvatarUrl()` when mapping DTO → domain.

**Step 4 — Update `item_session_history.xml`**  
Insert `<com.walkmate.core.designsystem.view.AvatarInitialView>` with id `avatarPartner` at the start of the Participant 2 row (before the `LinearLayout` holding name + status). Use `android:layout_width="36dp"` and `android:layout_height="36dp"` to keep the card compact.

**Step 5 — Update `SessionHistoryAdapter.ViewHolder`**  
Add `AvatarInitialView avatarPartner` field.  
In `bind()`: call `avatarPartner.bind(p.getFullName(), p.getAvatarUrl())` for the partner participant.  
Also set `avatarPartner.setOnClickListener` → `partnerClickListener.onPartnerClick(partnerId)` (same destination as the name tap).

---

#### E2 — Remove "Friends" Title Text

**Step 1 — Edit `fragment_friends.xml`**  
Delete the anonymous `<TextView android:text="Friends" .../>` node from the top `ConstraintLayout`. The back button (`btnBackFriends`) remains. Adjust `ConstraintLayout` height to `wrap_content` if it now has only one child.

---

#### E3 — Friend-Level Private Profile View

**Step 1 — Extend `PublicUserResponse` DTO**  
Add `@SerializedName("last_active_at") public String lastActiveAt;`.  
(Other friend-only fields can be added here as needed in the future.)

**Step 2 — Extend `UserSummary` domain model**  
Add `lastActiveAt` field + constructor parameter + getter.

**Step 3 — Update `SocialMapper.toUserSummary()`**  
Pass `dto.lastActiveAt` to the `UserSummary` constructor.

**Step 4 — Update `PublicProfileUiState`**  
Add `private final boolean isFriend;` field.  
Derive it from `friendshipStatus.equals("FRIENDS")` in the constructor.  
Add `public boolean isFriend()` getter.

**Step 5 — Update `PublicProfileViewModel.loadProfile()`**  
After all 4 parallel calls complete, set `isFriend = "FRIENDS".equals(status)` when building the state.

**Step 6 — Update `PublicProfileFragment.renderState()`**  
Add a new `renderFriendExtras(UserSummary profile, boolean isFriend)` method.  
When `isFriend == true`, show a "Last Active" row below the stats section using the `lastActiveAt` value from `UserSummary`. Hide this row when `isFriend == false`.

**Step 7 — Update `FriendListFragment.onViewProfile()`**  
Pass `args.putString("viewMode", "FRIEND")` alongside `args.putString("userId", userId)` when navigating to `publicProfileFragment`. This allows the fragment to pre-render the friend view before the API call completes (avoids layout flicker where buttons flash NONE → FRIENDS).

**Step 8 — Update `SessionHistoryAdapter`**  
No navigation change needed — existing `partnerClickListener.onPartnerClick(partnerId)` already navigates to `publicProfileFragment`. The profile will resolve `friendshipStatus` from the server.

---

#### E4 — Cancel Outgoing Friend Request

**Step 1 — Extend `SocialRepository` (domain interface)**  
Add: `void cancelFriendRequest(String requestId, DomainCallback<Void> callback);`

**Step 2 — Extend `SocialApiService`**  
Add:
```java
@DELETE("api/v1/friends/requests/{id}")
Call<ApiResponse<Void>> cancelFriendRequest(@Path("id") String id);
```

**Step 3 — Implement in `SocialRepositoryImpl`**  
Add `cancelFriendRequest()` following the same `executor.execute()` + `handleVoidResponse()` pattern as `declineFriendRequest`.

**Step 4 — Add `cancelRequest(requestId)` to `FriendsViewModel`**  
```java
public void cancelRequest(String requestId) {
    socialRepository.cancelFriendRequest(requestId, new DomainCallback<Void>() {
        @Override public void onSuccess(Void v) { loadAll(); }
        @Override public void onError(Exception e) {
            uiState.postValue(FriendsUiState.error(friendlyError(e)));
        }
    });
}
```

**Step 5 — Extend `FriendRequestsAdapter.ActionListener`**  
Add `void onCancel(String requestId);`

**Step 6 — Update `FriendRequestsAdapter` outgoing mode binding**  
When `showActions == false` (outgoing tab):
- Replace the static `txtPending` label with a "Cancel" `WalkMateButton` (OUTLINED style, danger color).
- Wire: `btnCancel.setOnClickListener(v -> { if (listener != null) listener.onCancel(request.getRequestId()); });`
- Keep `txtPending` label visible alongside (or replace with button — UX decision).

**Step 7 — Wire cancel in `OutgoingRequestsFragment`**  
The adapter already has `adapter.setActionListener(...)` — add the `onCancel` implementation:
```java
@Override public void onCancel(String requestId) {
    viewModel.cancelRequest(requestId);
}
```

---

### 2.2 Backend Changes

#### E1 — Partner Avatar in Session History

**Step 1 — Extend `UserProfileRepository` interface**  
Add:
```java
/** Batch-fetches userId → (fullName, avatarUrl) pairs. */
Map<UUID, UserProfileSnapshot> findSnapshotsByUserIds(Collection<UUID> userIds);
```
Where `UserProfileSnapshot` is a simple value record: `record UserProfileSnapshot(String fullName, String avatarUrl) {}`.

**Step 2 — Implement in `UserProfileJdbcRepository`**  
Run a single `SELECT user_id, full_name, avatar_url FROM user_profile WHERE user_id = ANY(:ids)` query. Build the snapshot map.

**Step 3 — Extend `ParticipantSummaryResponse`**  
Add `@JsonProperty("avatar_url") String avatarUrl` to the record.

**Step 4 — Update `SessionHistoryQueryService.getSessionHistory()`**  
Replace `profileRepository.findNamesByUserIds()` call with `profileRepository.findSnapshotsByUserIds()`.  
In `toSummary()`, pass `snapshot.avatarUrl()` to each `ParticipantSummaryResponse`.

---

#### E3 — Friend-Level Profile Enrichment

**Step 1 — Extend `UserProfileResponse`**  
Add `String lastActiveAt` (ISO-8601 string, nullable).

**Step 2 — Update `UserProfileController.getPublicProfile()`**  
Inject `SocialRepository` (or `FriendQueryService`) to check `areAcceptedFriends(callerId, targetId)`.  
The endpoint already has no `@AuthenticationPrincipal` — add it (make it optional so unauthenticated callers still get the public view).  
When caller and target are friends, populate `lastActiveAt` from `userAccount.lastActiveAt`.

**Step 3 — Update `UserProfileMapper.toResponse()`**  
Accept an optional `lastActiveAt` parameter and populate the DTO field.

---

#### E4 — Cancel Outgoing Friend Request

**Step 1 — Add `CANCELLED` to `friend_status` enum** *(see DB migration below)*

**Step 2 — Add `Friendship.cancel()` method**  
```java
public void cancel() {
    if (!"PENDING".equals(status))
        throw new DomainException(FriendshipErrorCode.FRIEND_REQUEST_ALREADY_RESOLVED);
    this.status    = "CANCELLED";
    this.updatedAt = Instant.now();
}
```

**Step 3 — Add `FRIEND_REQUEST_NOT_REQUESTER` to `FriendshipErrorCode`**  
```java
FRIEND_REQUEST_NOT_REQUESTER("Only the requester can cancel a friend request"),
```

**Step 4 — Add `cancelFriendRequest(callerId, friendshipId)` to `FriendCommandService`**  
```java
@Transactional
public void cancelFriendRequest(UUID callerId, String friendshipId) {
    Friendship friendship = socialRepository.findFriendshipById(friendshipId)
            .orElseThrow(() -> new DomainException(FriendshipErrorCode.FRIEND_REQUEST_NOT_FOUND));

    if (!friendship.getRequesterId().equals(callerId))
        throw new DomainException(FriendshipErrorCode.FRIEND_REQUEST_NOT_REQUESTER);

    friendship.cancel();
    socialRepository.saveFriendship(friendship);
    // No notification published — the requester cancelled their own request.
}
```

**Step 5 — Add endpoint to `FriendsController`**  
```java
// DELETE /api/v1/friends/requests/{requestId}  (UC-39: Cancel outgoing request)
@DeleteMapping("/requests/{requestId}")
public ResponseEntity<ApiResponse<Void>> cancelFriendRequest(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable String requestId) {
    UUID callerId = UUID.fromString(principal.userId());
    friendCommandService.cancelFriendRequest(callerId, requestId);
    return ResponseEntity.ok(ApiResponse.success(null));
}
```

---

## 3. Database Schema Assessment

### 3.1 Relevant tables review

| Table | Column | Status for new expectations |
|-------|--------|-----------------------------|
| `user_profile` | `avatar_url text` | ✅ Exists — sufficient for E1 |
| `user_account` | `last_active_at timestamp` | ✅ Exists — sufficient for E3 |
| `friendship` | `status friend_status` | ⚠️ Enum missing `CANCELLED` — needed for E4 |
| `friendship` | `requester_id`, `addressee_id` | ✅ Existing UNIQUE constraint supports re-send after cancel |

### 3.2 `friend_status` enum — current vs. required

| Value | Current | Required | Semantic |
|-------|---------|----------|----------|
| `PENDING` | ✅ | ✅ | Request awaiting response |
| `ACCEPTED` | ✅ | ✅ | Both parties are friends |
| `DECLINED` | ✅ | ✅ | Addressee rejected |
| `CANCELLED` | ❌ | ✅ | **Requester withdrew** |

Adding `CANCELLED` is the **only schema change required** across all four expectations.

### 3.3 Index sufficiency

The existing index `idx_friendship_status ON friendship(status)` covers the filter in all query methods. No new indexes are needed for the cancel use case — `findFriendshipById` uses the primary key, and `findOutgoingPendingRequests` already filters on `status = 'PENDING'` which automatically excludes `CANCELLED` rows.

### 3.4 Constraint check for re-send after cancel

The UNIQUE constraint `friendship_unique_pair UNIQUE (requester_id, addressee_id)` combined with the UPSERT in `SocialJdbcRepository.saveFriendship()` (`ON CONFLICT (requester_id, addressee_id) DO UPDATE SET status = EXCLUDED.status`) means that after a cancel, A can send a new request to B. The `CANCELLED` row will be **overwritten** (status → `PENDING`, version +1) rather than a new row being inserted. This is correct behaviour and requires no schema change.

---

## 4. Flyway Migration

### ⚠️ CRITICAL: Version Number Conflict

> The task brief states the migration must start from **`V106`**.  
> **This is impossible.** Flyway migrations `V106` through `V114` already exist in  
> `backend/src/main/resources/db/migration/`. Using `V106` would trigger a  
> **Flyway checksum conflict** and crash the application on startup.
>
> The correct next sequential version is **`V115`**.  
> The migration below uses `V115__add_friend_status_cancelled.sql`.

---

### Migration: `V115__add_friend_status_cancelled.sql`

```sql
-- V115: Add CANCELLED value to friend_status enum.
--
-- Semantic distinction:
--   DECLINED  = addressee explicitly rejected the request.
--   CANCELLED = requester withdrew the pending request themselves.
--
-- PostgreSQL does not support removing enum values, but adding new ones is safe
-- (ALTER TYPE ... ADD VALUE is non-transactional and idempotent when guarded).
--
-- Impact on existing data: none — no rows carry CANCELLED today.
-- Impact on existing indexes: none — idx_friendship_status covers all enum values.
-- Impact on existing queries:
--   findPendingFriendship  → filters status = 'PENDING'  → CANCELLED rows invisible ✓
--   findOutgoingPending    → filters status = 'PENDING'  → CANCELLED rows invisible ✓
--   areAcceptedFriends     → filters status = 'ACCEPTED' → CANCELLED rows invisible ✓
--   saveFriendship (UPSERT)→ ON CONFLICT DO UPDATE       → re-send overwrites CANCELLED ✓

ALTER TYPE public.friend_status ADD VALUE IF NOT EXISTS 'CANCELLED' AFTER 'DECLINED';
```

> **Note:** `ADD VALUE IF NOT EXISTS` makes the migration idempotent — safe to re-run on environments where it may have been partially applied.  
> `AFTER 'DECLINED'` positions the value cleanly at the end of the enum for readability (Postgres enum ordering is cosmetic only).

---

### Migration placement

Save the file at:
```
backend/src/main/resources/db/migration/V115__add_friend_status_cancelled.sql
```

This is the **only** DB migration required for all four expectations. All other changes (avatar URL in session history, last_active_at exposure, cancel logic) operate on existing columns.

---

## Summary Table

| # | Expectation | DB change | Backend change | Frontend change | Architectural collision |
|---|-------------|-----------|----------------|-----------------|------------------------|
| E1 | Partner avatar on History card | None | `ParticipantSummaryResponse` + `SessionHistoryQueryService` + `UserProfileRepository` batch method | `ParticipantSummary`, `ParticipantResponse` DTO, `item_session_history.xml`, `SessionHistoryAdapter` | None |
| E2 | Remove "Friends" title | None | None | `fragment_friends.xml` one-line removal | None |
| E3 | Private profile for friends | None | `GET /api/v1/users/{userId}` enrichment + `UserProfileMapper` + optional auth | `PublicUserResponse`, `UserSummary`, `PublicProfileUiState`, `PublicProfileFragment` (dual-mode render) | **Yes — see §1.3. Resolved via single-fragment dual-mode pattern.** |
| E4 | Cancel outgoing request | `V115` — add `CANCELLED` to enum | `Friendship.cancel()`, `FriendCommandService.cancelFriendRequest()`, `DELETE /api/v1/friends/requests/{requestId}` | `SocialRepository`, `SocialApiService`, `SocialRepositoryImpl`, `FriendsViewModel`, `FriendRequestsAdapter`, `OutgoingRequestsFragment` | None |
