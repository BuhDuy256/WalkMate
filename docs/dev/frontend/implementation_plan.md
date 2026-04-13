# WalkMate Frontend — Implementation Plan
**Purpose:** Eliminate all 21 identified gaps between the current Java Android frontend and the backend use cases defined in `docs/single-source-of-truth/use-cases/backend_use_cases.md`.
**Source:** Approved gap analysis (`docs/dev/frontend/gap_analysis.md`, 2026-04-13).
**Target branch:** `merge/oauth` → feature branches per phase.

---

## Dependency Graph

```
Phase 0 (Cleanup + Error Handling)
    └─► Phase 1 (Social Domain Rebuild)
            └─► Phase 2 (Public Profile Screen)
                    └─► Phase 3 (Friends & Social Screens)
                            └─► Phase 4 (Private Intent Flow)
Phase 0
    └─► Phase 5 (Proposal Enhancements)
    └─► Phase 6 (Session Lifecycle Enhancements)
    └─► Phase 7 (FCM & Notification Routing)  ← requires Phase 2, Phase 3 destinations
Phase 6
    └─► Phase 7 (FCM destinations must exist)
Phase 0 + Phase 3
    └─► Phase 8 (Gamification & Discovery Polish)
```

---

## Phase 0 — Pre-flight Cleanup & Foundation Hardening

**Addresses:** GAP-6, GAP-21
**Goal:** Remove the `triggerMatch` UI action (UC-18 protocol violation) and harden the global HTTP error-parsing layer so all subsequent feature work builds on a correct foundation.

### Task 0.1 — Remove `triggerMatch` User Action (GAP-6)

**Files to modify:**
- `ui/matches/finding/FindingFragment.java`
- `ui/matches/MatchesViewModel.java`
- Layout: `fragment_finding.xml` (remove "Find Match" button view)

**Steps:**
1. In `fragment_finding.xml`, delete the "Find Match" / "Trigger Match" button declaration.
2. In `FindingFragment`, delete `onFindMatchClicked()` method and any click-listener wiring for that button.
3. In `MatchesViewModel`, delete `triggerMatch()` method (lines 235–260) and the associated `getNoMatchFoundEvent()` / `consumeNoMatchFoundEvent()` LiveData pair (lines 85–87).
4. In `WalkIntentRepository` interface, **retain** `findMatch()` method — it may still be called internally or in tests — but add a `@Deprecated` comment: *"Internal API only. Must not be called from UI layer."*
5. Verify no other Fragment or Activity references the deleted ViewModel method.

**Acceptance:** `FindingFragment` renders OPEN intent cards with only a "Cancel" button. No "Find Match" button exists anywhere in the Matches UI.

---

### Task 0.2 — Global Error Handling: `error.code`-First Parsing (GAP-21)

**Files to modify / verify:**
- `data/repository/*RepositoryImpl.java` (all implementations)
- Any shared `handleApiError()` utility if one exists

**Steps:**
1. Audit every `onResponse()` callback in the repository layer. Confirm that domain error classification reads `apiResponse.getError().getCode()` (the `error.code` string field), not the HTTP status integer.
2. Wherever a repository currently switches on HTTP status code (e.g., `if (code == 404)`) to determine domain error type, replace with a switch on `error.code` string.
3. For `VALIDATION_ERROR` (HTTP 422): confirm `error.message` is parsed by splitting on `", "` (comma-space) to produce a `Map<String, String>` of field → reason entries. Create a shared utility method `ValidationErrorParser.parse(String message): Map<String, String>` in `core/util/` if one does not already exist.
4. Verify `TokenRefreshAuthenticator` correctly triggers `AuthEventBus.FORCE_LOGOUT` on 401 responses and does not confuse 401 with a 400 domain error.

**Acceptance:** A `SESSION_NOT_FOUND` error (HTTP 400, `error.code = "SESSION_NOT_FOUND"`) is handled distinctly from a generic 400 bad-request. VALIDATION_ERROR messages produce field-level inline errors in the UI.

---

## Phase 1 — Social Domain Layer Rebuild

**Addresses:** GAP-7, GAP-2
**Goal:** Tear out the obsolete follow/follower model from the frontend social domain and replace it with the correct friend-request model that mirrors the backend `FriendsController` contract.

### Task 1.1 — Retire Follow/Follower Artifacts

**Files to modify:**
- `domain/social/SocialRepository.java` (interface)
- `data/datasource/remote/api/SocialApiService.java` (Retrofit interface)
- `data/repository/SocialRepositoryImpl.java`

**Steps:**
1. In `SocialRepository.java`, remove method declarations: `follow()`, `unfollow()`, `getFollowers()`, `getFollowing()`.
2. In `SocialApiService.java`, remove the corresponding `@POST`/`@DELETE`/`@GET` Retrofit annotations for the follow/follower endpoints.
3. In `SocialRepositoryImpl.java`, remove `follow()`, `unfollow()`, `getFollowers()`, `getFollowing()` implementations.
4. Search the entire codebase for any call sites referencing these deleted methods (primarily `HomeViewModel`, `ProfileViewModel`). Replace `getFollowing()` calls used for the Home Dashboard quick-invite candidate list with `getFriends()` (which already exists).
5. Remove `UserSummary.java` field `isFollowing` if present; replace with `friendshipStatus: String` (values: `"NONE"`, `"PENDING_SENT"`, `"PENDING_RECEIVED"`, `"FRIENDS"`).

---

### Task 1.2 — Add Friend-Request Domain Model

**New files to create:**
- `domain/social/FriendRequest.java`

**`FriendRequest` fields:**
```java
String requestId;
String senderId;
String senderName;
String senderAvatarUrl;
String receiverId;
String status; // "PENDING" | "ACCEPTED" | "DECLINED"
String createdAt;
```

---

### Task 1.3 — Add Friend-Request API Endpoints

**Files to modify:**
- `data/datasource/remote/api/SocialApiService.java`
- `data/datasource/remote/dto/request/social/` (new request DTOs)
- `data/datasource/remote/dto/response/social/` (new response DTOs)

**New Retrofit endpoints to add to `SocialApiService`:**
```
@POST("friends/{userId}/request")         sendFriendRequest(@Path userId)
@POST("friends/requests/{id}/accept")     acceptFriendRequest(@Path id)
@POST("friends/requests/{id}/decline")    declineFriendRequest(@Path id)
@GET("friends/requests/incoming")         getIncomingRequests()
@GET("friends/requests/outgoing")         getOutgoingRequests()
@DELETE("friends/{userId}")               removeFriend(@Path userId)
@GET("users/{userId}")                    getPublicProfile(@Path userId)
```

**New DTOs:**
- `FriendRequestResponse.java` (in `dto/response/social/`) — mirrors `FriendRequest` domain model
- `PublicUserResponse.java` (in `dto/response/social/`) — `userId`, `fullName`, `avatarUrl`, `bio`, `tags`, `trustScore`, `gender`, `friendshipStatus`

---

### Task 1.4 — Add Friend-Request Repository Methods

**Files to modify:**
- `domain/social/SocialRepository.java` — add new method signatures
- `data/repository/SocialRepositoryImpl.java` — implement them
- `data/mapper/SocialMapper.java` — add `toFriendRequest(FriendRequestResponse)` and `toUserSummary(PublicUserResponse)` mappers

**New methods on `SocialRepository` interface:**
```java
void sendFriendRequest(String userId, DomainCallback<Void> cb);
void acceptFriendRequest(String requestId, DomainCallback<Void> cb);
void declineFriendRequest(String requestId, DomainCallback<Void> cb);
void getIncomingRequests(DomainCallback<List<FriendRequest>> cb);
void getOutgoingRequests(DomainCallback<List<FriendRequest>> cb);
void removeFriend(String userId, DomainCallback<Void> cb);
void getPublicProfile(String userId, DomainCallback<UserSummary> cb);
```

**Implementation in `SocialRepositoryImpl`:** Each method calls the corresponding Retrofit endpoint, maps the response through `SocialMapper`, and delivers via `DomainCallback` on the calling thread (consistent with existing repository pattern).

**Acceptance:** All new methods compile cleanly. Existing `block()`, `unblock()`, `getFriends()` are untouched.

---

## Phase 2 — Public User Profile Screen

**Addresses:** GAP-1
**Prerequisite:** Phase 1 complete.
**Goal:** Build a self-contained `PublicProfileFragment` that renders any user's public profile and exposes contextual friendship actions.

### Task 2.1 — Create `PublicProfileUiState`

**New file:** `ui/profile/public/PublicProfileUiState.java`

**Fields:**
```java
boolean isLoading;
String error;
UserSummary profile;         // from SocialRepository.getPublicProfile()
List<Badge> badges;          // from GamificationRepository.getBadges(userId)
UserStats stats;             // from GamificationRepository.getStats(userId)
List<Review> reviews;        // from ReviewRepository.getReviewsForUser(userId)
String friendshipStatus;     // "NONE" | "PENDING_SENT" | "PENDING_RECEIVED" | "FRIENDS"
boolean isSelf;              // true when viewing own profile — hides all friendship actions
```

---

### Task 2.2 — Create `PublicProfileViewModel`

**New file:** `ui/profile/public/PublicProfileViewModel.java`

**Constructor dependency:** `SocialRepository`, `GamificationRepository`, `ReviewRepository`

**Methods:**
- `loadProfile(String userId)` — fires parallel calls: `getPublicProfile()`, `getBadges()`, `getStats()`, `getReviewsForUser()`. Merges results into a single `PublicProfileUiState`.
- `sendFriendRequest(String userId)`
- `removeFriend(String userId)`
- `acceptIncomingRequest(String requestId)`
- `declineIncomingRequest(String requestId)`
- `blockUser(String userId)` — calls `SocialRepository.block()` then pops back

**Create:** `ui/profile/public/PublicProfileViewModelFactory.java` (manual DI, injects singletons from `WalkMateApplication`).

---

### Task 2.3 — Create `PublicProfileFragment`

**New file:** `ui/profile/public/PublicProfileFragment.java`
**New layout:** `res/layout/fragment_public_profile.xml`

**Layout sections:**
- Avatar (`AvatarInitialView`), full name, bio, `TagChipGroup` for tags
- Stats row using `WalkMateStatColumn` (Total Distance, Sessions, Trust Score)
- Badges section (horizontal chip list, empty state: "No badges yet.")
- Reviews `RecyclerView` (reuse existing review adapter)
- Friendship action area (dynamic, driven by `friendshipStatus`):
  - `NONE` → `WalkMateButton` "Add Friend" (FILLED)
  - `PENDING_SENT` → `WalkMateButton` "Request Sent" (OUTLINED, disabled)
  - `PENDING_RECEIVED` → `WalkMateButton` "Accept" (FILLED) + `WalkMateButton` "Decline" (OUTLINED)
  - `FRIENDS` → `WalkMateButton` "Invite Walk" (FILLED) + `WalkMateButton` "Remove Friend" (OUTLINED)
  - Overflow menu (always, when not `isSelf`): "Block User" action
- Unauthenticated guard: if not logged in and any action tapped, navigate to `AuthActivity` with toast "Log in to manage friendships."

**Navigation args:** `userId: String` (passed via `NavController` Safe Args or Bundle).

**Entry points to wire up:**
- `SessionHistoryFragment` — partner name tap
- `PostSessionSummaryFragment` — partner name tap
- `ProposalFragment` — partner name/avatar tap on proposal card
- `HomeFragment` — quick-invite candidate card tap
- `LeaderboardFragment` (Phase 8) — row tap

---

### Task 2.4 — Add Navigation Route

**File to modify:** `res/navigation/nav_graph.xml`

Add a `<fragment>` destination for `PublicProfileFragment` with `userId` as a `<argument>`. Connect it from every entry point listed in Task 2.3.

---

## Phase 3 — Friends & Social Management Screens

**Addresses:** GAP-3, GAP-4
**Prerequisite:** Phase 1 complete. Phase 2 desirable (View Profile action on Friends list taps Phase 2).
**Goal:** Build the multi-tab Friends screen and the Blocked Users settings screen.

### Task 3.1 — Create `FriendsUiState`

**New file:** `ui/social/friends/FriendsUiState.java`

**Fields:**
```java
boolean isLoading;
String error;
List<UserSummary> friends;
List<FriendRequest> incomingRequests;
List<FriendRequest> outgoingRequests;
int incomingBadgeCount; // drives badge on tab header
```

---

### Task 3.2 — Create `FriendsViewModel`

**New file:** `ui/social/friends/FriendsViewModel.java`

**Constructor dependency:** `SocialRepository`

**Methods:**
- `loadAll()` — fires `getFriends()`, `getIncomingRequests()`, `getOutgoingRequests()` in parallel; merges into `FriendsUiState`
- `acceptRequest(String requestId)`
- `declineRequest(String requestId)`
- `removeFriend(String userId)` — shows confirmation dialog from Fragment side, then calls this
- `navigateToInviteWalk(String friendId)` — posts a navigation event so the container can deep-link to ExploreFragment with `friendId` pre-filled

**Create:** `ui/social/friends/FriendsViewModelFactory.java`

---

### Task 3.3 — Create `FriendsFragment` (Tabbed Container)

**New files:**
- `ui/social/friends/FriendsFragment.java` (ViewPager2 container with TabLayout)
- `ui/social/friends/FriendListFragment.java` (Friends tab)
- `ui/social/friends/IncomingRequestsFragment.java`
- `ui/social/friends/OutgoingRequestsFragment.java`
- `ui/social/friends/FriendsPagerAdapter.java`
- Layouts: `fragment_friends.xml`, `fragment_friend_list.xml`, `fragment_incoming_requests.xml`, `fragment_outgoing_requests.xml`
- RecyclerView adapters: `FriendsAdapter.java`, `FriendRequestsAdapter.java`

**Tabs:**
- **Friends** — list of `UserSummary`. Each card: `AvatarInitialView`, name, quick-action buttons: "Invite Walk" (calls `FriendsViewModel.navigateToInviteWalk()`), "View Profile" (navigates to `PublicProfileFragment`), "Remove Friend" (confirmation dialog → `FriendsViewModel.removeFriend()`).
- **Incoming Requests** — list of `FriendRequest` in `PENDING`. Each card: sender avatar + name, "Accept" + "Decline" buttons.
- **Sent Requests** — list of `FriendRequest` sent by current user in `PENDING`. Each card: receiver avatar + name, "Pending" status label, no actions.

**Entry point:** `ProfileFragment` → "Friends" button/row navigates here.

**Navigation route:** Add destination in `nav_graph.xml`.

---

### Task 3.4 — Create Blocked Users Screen

**New files:**
- `ui/social/blocked/BlockedUsersFragment.java`
- `ui/social/blocked/BlockedUsersViewModel.java`
- `ui/social/blocked/BlockedUsersViewModelFactory.java`
- `ui/social/blocked/BlockedUsersUiState.java`
- Layout: `fragment_blocked_users.xml`

**`BlockedUsersViewModel` methods:**
- `loadBlocked()` — calls `SocialRepository.getBlockedUsers()` (add this method to domain interface + `SocialApiService` as `GET /api/v1/users/me/blocked`)
- `unblock(String userId)` — calls `SocialRepository.unblock()`, removes from list on success

**Entry point:** `ProfileFragment` overflow menu or Settings sub-section → navigates to `BlockedUsersFragment`.

**Navigation route:** Add destination in `nav_graph.xml`.

---

## Phase 4 — Private Intent Flow

**Addresses:** GAP-8
**Prerequisite:** Phase 3 complete (friend picker sources from `FriendsViewModel`).
**Goal:** Add the `is_private` toggle and `invited_friend_id` friend-picker to the Create Intent form.

### Task 4.1 — Extend `CreateIntentUiState`

**File to modify:** `ui/explore/createintent/CreateIntentUiState.java`

**Add fields:**
```java
boolean isPrivate;
String invitedFriendId;     // null for public intents
String invitedFriendName;   // display name for picker label
List<UserSummary> friendList; // populated when toggle is enabled
boolean isFriendListLoading;
String privateIntentError;  // "INTENT_PRIVATE_FRIEND_NOT_ACCEPTED" etc.
```

---

### Task 4.2 — Extend `CreateIntentViewModel`

**File to modify:** `ui/explore/createintent/CreateIntentViewModel.java`

**Constructor dependency change:** Inject `SocialRepository` alongside `WalkIntentRepository`.

**New methods:**
- `togglePrivate(boolean isPrivate)` — if enabling, call `SocialRepository.getFriends()` and populate `friendList` in UiState
- `selectFriend(UserSummary friend)` — sets `invitedFriendId` + `invitedFriendName` in UiState
- Update `submit()` to pass `isPrivate` and `invitedFriendId` into `CreateWalkIntentRequest` (fields already exist in the DTO)
- Client-side validation in `submit()`: if `isPrivate == true` and `invitedFriendId == null`, post error "Please select a friend to invite."

**Update:** `CreateIntentViewModelFactory.java` to inject `SocialRepository` from `WalkMateApplication`.

---

### Task 4.3 — Update Create Intent Form UI

**File to modify:** `ExploreFragment.java` (owns the bottom-sheet form)
**Layout to modify:** The Create Intent form layout within the bottom-sheet

**Changes:**
1. Add a "Private Walk" toggle switch below the description field.
2. When toggled ON: reveal a friend-picker row showing `invitedFriendName` (or "Select a friend" placeholder). Tapping it opens a bottom-sheet list populated from `uiState.friendList`.
3. When `friendList` is empty and toggle is ON: show inline message "You have no friends yet. Add friends from your profile to use private invites."
4. Render `uiState.privateIntentError` as inline error text below the friend picker row.
5. On successful private intent creation where the response includes `proposal_id`: the existing `ExploreViewModel.onIntentCreated()` → SCANNING transition still fires, but the FCM/event-bus routing in Phase 7 will handle the navigation to Proposal tab. No change needed to the state machine for the happy path.

---

### Task 4.4 — Auth Guard on "Create Intent" CTA (GAP-10)

**File to modify:** `ExploreViewModel.java` (`selectHotspot()` method)

**Steps:**
1. Inject `UserRepository` into `ExploreViewModel` (add to `ExploreViewModelFactory`).
2. In `selectHotspot()`, before transitioning to `SETUP` state, call `userRepository.getAccessToken()`.
3. If token is null/empty: post a `navigateToLoginEvent` (new `MutableLiveData<Hotspot>` carrying the selected hotspot).
4. In `ExploreFragment`, observe `navigateToLoginEvent`: save the hotspot in a `SharedPreferences` key (`pending_hotspot_id`), then launch `AuthActivity`.
5. In `ExploreFragment.onResume()`, check for a `pending_hotspot_id` key. If present and user is now authenticated: clear the key and call `viewModel.selectHotspot(hotspot)` to resume the flow with the hotspot pre-filled.

---

## Phase 5 — Proposal Enhancements

**Addresses:** GAP-11, GAP-12, GAP-17 (proposals side)
**Prerequisite:** Phase 0 complete.
**Goal:** Correct proposal card behavior for private invites, differentiate pass dialogs, and add expiry countdown timers.

### Task 5.1 — Add `isPrivateInvite` and `iSenderAlreadyAccepted` to Proposal Domain Model

**File to modify:** `domain/walkproposal/WalkProposal.java` (or equivalent domain model)

Add fields:
```java
boolean isPrivateInvite;          // from backend response field is_private
boolean iCurrentUserAccepted;     // whether the calling user has already accepted
```
Update `WalkProposalRepository` response mapping and the relevant `Mapper` class.

---

### Task 5.2 — Private Invite Pre-Accepted State in `ProposalFragment` (GAP-11)

**File to modify:** `ui/matches/proposal/ProposalFragment.java` — `renderState()`
**File to modify:** `MatchesViewModel.java` — no change needed (data already flows in)

**Logic change in `renderState()`:**
- If `proposal.iCurrentUserAccepted == true` (sender of a private invite): render the waiting overlay ("You accepted! Waiting for your partner…") immediately, with Accept button disabled and Pass button enabled. Do **not** wait for an "Accept" tap.
- Add this check before any button-state logic to ensure it applies on the first render.

---

### Task 5.3 — Differentiated Pass Dialog (GAP-12)

**File to modify:** `ui/matches/proposal/ProposalFragment.java` — `onPass()` handler

**Logic change:**
```java
String message = proposal.isPrivateInvite
    ? "Decline this private invite? This invite will be closed and you will not be added to the public wait list."
    : "Pass on this match? Your intent will stay active and we'll keep looking for other partners.";
```

**Post-pass navigation logic in `MatchesViewModel.passProposal()`:**
- If `isPrivateInvite == true`: after successful pass, navigate back to Proposal tab empty state (do **not** scroll to Intent/Finding tab, as no OPEN intent is created for the receiver).
- If `isPrivateInvite == false`: after successful pass, navigate to Intent/Finding tab (existing behavior, intents revert to OPEN).

Add `isPrivateInvite` parameter to `MatchesViewModel.passProposal()` signature to enable this routing.

---

### Task 5.4 — Proposal Expiry Countdown Timer (GAP-17)

**File to modify:** `ui/matches/proposal/ProposalFragment.java`

**Steps:**
1. In `renderState()`, for each rendered proposal card, compute `millisUntilExpiry = proposal.expiresAt - System.currentTimeMillis()`.
2. Start a `CountDownTimer` for `millisUntilExpiry`, updating a countdown `TextView` (e.g., "Expires in 4:32") on each tick.
3. On `onFinish()` of the timer: call `viewModel.loadAll()` to refresh the proposals list. The expired proposal will be absent from the server response, and the list re-renders cleanly.
4. Cancel the `CountDownTimer` in `onDestroyView()` to prevent leaks.

---

### Task 5.5 — Optimistic Locking Error Handling in `acceptProposal()` (UC-20 / Invariant X-5)

**File to modify:** `ui/matches/MatchesViewModel.java` — `acceptProposal()` `onError` callback

**Steps:**
1. In `MatchesViewModel.acceptProposal()`, after the existing success path, expand the `onError` dispatch to handle the following error codes from `error.code`:
   - `"PROPOSAL_CONCURRENT_MODIFICATION"` → show Toast "A conflict occurred while confirming the walk. The list has been refreshed." then call `loadAll()` to re-fetch proposals (Invariant X-5: optimistic locking violation).
   - `"PROPOSAL_INTENT_NO_LONGER_OPEN"` → show Toast "Could not confirm the walk — the intent is no longer available." then call `loadAll()`.
   - `"PROPOSAL_ALREADY_TERMINAL"` → show Toast "This proposal has already been decided." then post `navigateBackEvent` to exit the proposal detail.
   - `"PROPOSAL_NOT_PARTICIPANT"` → show Toast "Permission denied." (defensive; should not occur in normal flow).
   - `"PROPOSAL_NOT_FOUND"` → show Toast "Proposal not found." then post `navigateBackEvent`.
   - All other codes → generic error Toast.
2. Confirm that `loadAll()` is already a method on `MatchesViewModel` (or that a re-fetch mechanism exists) — reuse it; do not create a duplicate.
3. No ViewModel state changes are needed beyond the toast + refresh — the list LiveData will update automatically on `loadAll()` completion.

---

## Phase 6 — Session Lifecycle Enhancements

**Addresses:** GAP-13, GAP-14, GAP-15, GAP-18, GAP-19
**Prerequisite:** Phase 0 complete.
**Goal:** Enforce session invariants in the UI (activation window, 5-min minimum), wire the Chat button, broaden the Report Incident entry points, and add a celebration animation.

### Task 6.1 — Chat Button on Session Detail + Cancel Walk Reason Validation (GAP-13, UC-25)

**File to modify:** `ui/matches/session/SessionFragment.java`
**Layout to modify:** `fragment_session.xml`

**Steps:**
1. Add a speech-bubble `ImageButton` (or `WalkMateButton` OUTLINED) in the top-right of `fragment_session.xml`.
2. In `renderState()`:
   - Enable the button when `session.status == "PENDING" || session.status == "ACTIVE"`.
   - Disable and visually grey out when session is in a terminal state (`COMPLETED`, `ABORTED`, `CANCELLED`, `NO_SHOW`).
3. On click: navigate to `ChatFragment` passing `sessionId` as an argument. Wire `NavController` route from Session to Chat in `nav_graph.xml`.
4. `ChatViewModel` already exists — confirm it accepts a `sessionId` constructor argument and update `ChatViewModelFactory` if needed.
5. **Cancel Walk reason validation (UC-25, client-side guard):** In `SessionFragment.showCancelReasonDialog()`, inside the positive button `OnClickListener`:
   - Read the reason text from the `EditText` input field.
   - If the trimmed text is `null`, empty, or blank: call `reasonInput.setError("Please provide a reason.")` and **keep the dialog open** (do not dismiss or call the ViewModel).
   - Only call `viewModel.cancelSession(sessionId, reason.trim())` when the reason is non-empty after trimming.
6. **Defense-in-depth:** In `MatchesViewModel.cancelSession()` `onError` callback, if `error.code == "VALIDATION_ERROR"`, show a Toast with the parsed error message from `ValidationErrorParser.parse(error.message)` so that any server-side validation failure is surfaced to the user.

---

### Task 6.2 — Activation Window Enforcement (GAP-14)

**File to modify:** `ui/matches/session/SessionFragment.java`

**Steps:**
1. For each rendered `PENDING` session card, compute:
   - `windowOpenMs = session.scheduledStartMs - 10 * 60 * 1000`
   - `windowCloseMs = session.scheduledStartMs + 15 * 60 * 1000`
   - `nowMs = System.currentTimeMillis()`
2. Enable "I'm Here!" button only when `nowMs >= windowOpenMs && nowMs <= windowCloseMs`.
3. Show a countdown `TextView`:
   - Before window opens: "Activation opens in HH:MM:SS"
   - Within window: "Activation closes in HH:MM:SS"
   - After window: hide button, show "Activation window closed."
4. Use `CountDownTimer` for live countdown. Cancel in `onDestroyView()`.
5. **`SESSION_ACTIVATION_WINDOW_CLOSED` error handling in `MatchesViewModel.activateSession()`:** When this error code is received, post an `ActivationResult` with `errorCode = "SESSION_ACTIVATION_WINDOW_CLOSED"`. In `SessionFragment`, on this result: show toast "Activation window closed. Waiting for status update." Then schedule a one-shot `Handler.postDelayed()` (5 seconds) that calls `viewModel.loadAll()`. If the session no longer appears in the active list on refresh, navigate to `SessionHistoryFragment`.

---

### Task 6.3 — Complete Walk 5-Minute Minimum Countdown (GAP-18)

**File to modify:** `ui/tracking/TrackingScreenActivity.java`
**File to modify:** `ui/tracking/TrackingViewModel.java`

**Steps:**
1. In `TrackingViewModel`, add a `LiveData<Long> secondsUntilCompleteEnabled` computed from `session.startedAtMs`:
   - `secondsRemaining = max(0, 300 - elapsedSeconds)` where `elapsedSeconds` increments each timer tick.
   - When `secondsRemaining == 0`, emit `0L` as the signal that the button is now enabled.
2. In `TrackingScreenActivity.renderState()`, observe `secondsUntilCompleteEnabled`:
   - While > 0: disable "Complete Walk" button; show label "Complete Walk (4:52)".
   - When 0: enable button; show label "Complete Walk".
3. On `SESSION_COMPLETE_TOO_EARLY` error from the backend (defense-in-depth): show toast "You need to walk for at least 5 minutes before completing." and re-disable the button.

---

### Task 6.4 — Report Incident Reachable from ACTIVE Session and History (GAP-15)

**Files to modify:**
- `ui/matches/session/SessionFragment.java` — add "Report an Issue" menu item / button
- `ui/history/SessionHistoryFragment.java` — add "Report" action on COMPLETED/NO_SHOW/ABORTED cards
- `ui/report/ReportIncidentFragment.java` — relax the docstring + ensure it accepts any reportable status

**Steps:**
1. In `fragment_session.xml`, add a "Report an Issue" overflow menu item or secondary button. Show it when `session.status == "ACTIVE"`.
2. In `SessionFragment`, on this action: navigate to `ReportIncidentFragment` passing `sessionId` and `reportedUserId` (partner's ID).
3. In `SessionHistoryFragment`'s adapter, for each history card with status `COMPLETED`, `ABORTED`, or `NO_SHOW`: show a "Report" option (e.g., in an overflow menu). Navigate to `ReportIncidentFragment` with the same arguments.
4. Update `ReportIncidentFragment` to accept `sessionId`, `reportedUserId`, `sessionStatus` (String), and `sessionTerminalAtMs` (long) as navigation arguments. All three call sites (SessionFragment active button, SessionHistoryFragment card menu, PostSessionSummary deep-link) must pass these four values.
5. Apply a **tiered reporting-window guard** in `ReportIncidentFragment.onViewCreated()` (UC-32, Invariant R):
   ```java
   long nowMs = System.currentTimeMillis();
   boolean windowExpired = false;
   if ("COMPLETED".equals(sessionStatus)) {
       windowExpired = (nowMs - sessionTerminalAtMs) > 72 * 60 * 60 * 1000L;
   } else if ("ABORTED".equals(sessionStatus) || "NO_SHOW".equals(sessionStatus)) {
       windowExpired = (nowMs - sessionTerminalAtMs) > 24 * 60 * 60 * 1000L;
   }
   // ACTIVE sessions: windowExpired remains false — reporting always allowed
   if (windowExpired) {
       showClosedBanner("The reporting window for this session has closed.");
       disableForm();
   }
   ```
   - `COMPLETED` sessions: 72-hour window from terminal timestamp.
   - `ABORTED` / `NO_SHOW` sessions: 24-hour window from terminal timestamp.
   - `ACTIVE` sessions: no gate — reporting is always open.

---

### Task 6.5 — Celebration Animation on Proposal Double-Accept (GAP-19)

**File to modify:** `ui/matches/proposal/ProposalFragment.java`

**Steps:**
1. Add a `Lottie` animation overlay (or a simple `ConfettiView` using a lightweight library) to `fragment_proposal.xml`, initially `GONE`.
2. In `ProposalFragment`, observe `MatchesViewModel.getScrollToTabEvent()`. When the event fires with `TAB_SESSION`:
   - Set confetti overlay to `VISIBLE`.
   - Start the animation.
   - After 1.5 seconds (`Handler.postDelayed`), proceed with the existing tab-scroll navigation.
3. The animation must be cancelled/hidden in `onDestroyView()`.

---

## Phase 7 — FCM & Notification Routing

**Addresses:** GAP-9, GAP-16
**Prerequisite:** Phase 2 (PublicProfileFragment destination), Phase 3 (FriendsFragment destination), Phase 6 (SessionFragment chat wiring).
**Goal:** Expand `AppEventBus` to carry all backend-defined FCM event types and wire them to the correct navigation destinations in both foreground (MainActivity) and background (NotificationFragment tap).

### Task 7.1 — Expand `AppEvent` Type Enum / Constants

**File to modify:** `core/event/AppEventBus.java` (or the `AppEvent` class/enum it references)

**Add event types:**
```
MATCH_FOUND           (existing)
PROPOSAL_RECEIVED     (new) — payload: proposalId
INVITE_SENT           (new) — payload: proposalId
PROPOSAL_ACCEPTED     (new) — payload: proposalId
SESSION_CONFIRMED     (new) — payload: sessionId
SESSION_ACTIVE        (new) — payload: sessionId
FRIEND_REQUEST_RECEIVED (new) — payload: requestId, senderName
FRIEND_REQUEST_ACCEPTED (new) — payload: requestId
FRIEND_REQUEST_DECLINED (new) — payload: requestId
```

Each event type must carry a `payload: Map<String, String>` so destinations can extract relevant IDs.

---

### Task 7.2 — Update FCM Message Handler

**File to modify:** `WalkMateFcmService.java` (or equivalent `FirebaseMessagingService` subclass)

**Steps:**
1. In `onMessageReceived()`, read the `type` field from `remoteMessage.getData()`.
2. Map each type string to the corresponding `AppEvent` type added in Task 7.1.
3. Build the payload map from the FCM data fields and call `AppEventBus.get().post(new AppEvent(type, payload))`.
4. For background (app killed/not in foreground): use `remoteMessage.getNotification()` for system tray display. Deep-link intent must include the FCM `type` and relevant IDs as extras on the launcher `Intent` so `MainActivity.onCreate()` can route them.

---

### Task 7.3 — Expand `MainActivity.observeAppEventBus()` (GAP-9)

**File to modify:** `ui/main/MainActivity.java` — `observeAppEventBus()` method

**New routing table:**

| Event type | Navigation action |
|---|---|
| `MATCH_FOUND` | Navigate to Matches → Proposal tab (existing) |
| `PROPOSAL_RECEIVED` | Navigate to Matches → Proposal tab |
| `INVITE_SENT` | Navigate to Matches → Proposal tab |
| `PROPOSAL_ACCEPTED` | Navigate to Matches → Proposal tab |
| `SESSION_CONFIRMED` | Navigate to Matches → Session tab, pass `sessionId` |
| `SESSION_ACTIVE` | Navigate to Matches → Session tab, pass `sessionId` |
| `FRIEND_REQUEST_RECEIVED` | Navigate to `FriendsFragment` → Incoming tab |
| `FRIEND_REQUEST_ACCEPTED` | Navigate to `FriendsFragment` → Friends tab |
| `FRIEND_REQUEST_DECLINED` | Navigate to `FriendsFragment` → Sent Requests tab |

**Implementation:** Use a `switch` on `event.type` to select the `NavController.navigate()` destination. Always call `AppEventBus.get().consumeEvent()` after handling.

---

### Task 7.4 — Notification Tap Deep-Link Dispatch (GAP-16)

**File to modify:** `ui/notification/NotificationFragment.java`

**Steps:**
1. In the RecyclerView item click listener (currently calls `viewModel.markRead(notification.id)`), add a post-mark-read navigation step.
2. Build a `navigateFromNotification(Notification n)` helper method that switches on `n.type`:

| Type | Navigation target |
|---|---|
| `PROPOSAL_RECEIVED` / `INVITE_SENT` / `PROPOSAL_ACCEPTED` | Navigate to Matches → Proposal tab |
| `SESSION_CONFIRMED` / `SESSION_ACTIVE` | Navigate to Matches → Session tab |
| `FRIEND_REQUEST_RECEIVED` | Navigate to FriendsFragment → Incoming tab |
| `FRIEND_REQUEST_ACCEPTED` | Navigate to FriendsFragment → Friends tab |
| `FRIEND_REQUEST_DECLINED` | Navigate to FriendsFragment → Sent Requests tab |

3. Call `navigateFromNotification()` inside the `markRead()` success callback to ensure navigation happens after the read state is confirmed.

---

## Phase 8 — Gamification & Discovery Polish

**Addresses:** GAP-5, GAP-17 (intent side), GAP-20
**Prerequisite:** Phase 0 complete. Phase 2 required (Leaderboard rows tap into PublicProfileFragment).
**Goal:** Surface the Leaderboard as a standalone screen, enforce intent expiry countdowns, and implement hotspot pin visual weight.

### Task 8.1 — Standalone Leaderboard Screen (GAP-5)

**New files:**
- `ui/gamification/leaderboard/LeaderboardFragment.java`
- `ui/gamification/leaderboard/LeaderboardViewModel.java`
- `ui/gamification/leaderboard/LeaderboardViewModelFactory.java`
- `ui/gamification/leaderboard/LeaderboardUiState.java`
- `ui/gamification/leaderboard/LeaderboardAdapter.java`
- Layouts: `fragment_leaderboard.xml`, `item_leaderboard_row.xml`

**`LeaderboardViewModel` methods:**
- `loadLeaderboard()` — calls `GamificationRepository.getLeaderboard()`. Mirrors existing `PostSessionSummaryViewModel.loadLeaderboard()` but scoped independently.

**`LeaderboardAdapter`:**
- Renders rank, `AvatarInitialView`, name, totalPoints, totalDistanceKm, completedSessions, trustScore.
- Highlights the authenticated user's own row with a distinct background tint (compare `userId` against `UserRepository.getCurrentUserId()`).
- `onRowClicked(entry)` → navigate to `PublicProfileFragment` with `entry.userId`.

**`LeaderboardFragment`:**
- Calls `viewModel.loadLeaderboard()` in `onViewCreated()`.
- Pull-to-refresh triggers `loadLeaderboard()` again.
- Network failure: show "Last updated at …" banner with cached data.

**Entry point:** Add `LeaderboardFragment` as a navigation destination reachable from `HomeFragment` ("View Leaderboard" button or menu item) and from `ProfileFragment`. Add route in `nav_graph.xml`.

---

### Task 8.2 — Intent Expiry Countdown in `FindingFragment` (GAP-17)

**File to modify:** `ui/matches/finding/FindingFragment.java`
**Adapter to modify:** Intent card `RecyclerView` adapter (create or modify existing)

**Steps:**
1. In the intent card adapter's `onBindViewHolder()`, compute `millisUntilExpiry = intent.expiresAt - System.currentTimeMillis()`.
2. Start a `CountDownTimer` per card, updating an "Expires in HH:MM:SS" `TextView`.
3. On `onFinish()`: call `parentViewModel.loadAll()` to refresh. The expired intent will be absent from the server response.
4. Cancel all active timers in `onViewRecycled()` and `FindingFragment.onDestroyView()`.

---

### Task 8.3 — Hotspot Pin Visual Weight (GAP-20)

**File to modify:** `ui/explore/ExploreFragment.java` — `addHotspotsToMap()` or equivalent marker-building code

**Steps:**
1. For each hotspot, read `hotspot.openIntentCount`.
2. Map the count to one of three visual tiers using a helper:
   - `0` → standard orange pin (default `BitmapDescriptor`)
   - `1–4` → medium pin (scale factor 1.3×)
   - `5+` → large prominent pin (scale factor 1.6×, optional pulse animation)
3. Apply via `MarkerOptions.icon(BitmapDescriptorFactory.fromBitmap(scaledBitmap))`.
4. Re-apply visual weight on hotspot list refresh (each `loadHotspots()` call rebuilds markers).

---

## Cross-Cutting Concerns (All Phases)

### WalkMateApplication — Singleton Registration

After each phase that introduces a new `RepositoryImpl`, add the corresponding singleton getter to `WalkMateApplication.java` following the existing pattern. Phases that require updates:
- **Phase 1:** No new repos; `SocialRepositoryImpl` already registered — verify it is re-constructed after method additions.
- **Phase 2:** No new repos.
- **Phase 3:** Add `getBlockedUsersRepository()` if split out, or extend `SocialRepository`.
- **Phase 8:** No new repos.

### nav_graph.xml — Running Tally of New Destinations

| Phase | Destination |
|---|---|
| Phase 2 | `PublicProfileFragment` (args: `userId`) |
| Phase 3 | `FriendsFragment`, `BlockedUsersFragment` |
| Phase 6 | Ensure `ReportIncidentFragment` is reachable from `SessionFragment` + `SessionHistoryFragment` |
| Phase 7 | Ensure `FriendsFragment` has an `incomingTab` argument for tab pre-selection |
| Phase 8 | `LeaderboardFragment` |

### attrs.xml

No new Custom Views are defined in this plan. All new screens use standard layouts with existing Custom View components (`WalkMateButton`, `AvatarInitialView`, `WalkMateStatColumn`, `TagChipGroup`).

---

## Acceptance Criteria Summary

| Phase | Done when… |
|---|---|
| 0 | No "Find Match" button exists in Matches UI. All domain errors distinguished by `error.code`. |
| 1 | `SocialRepository` compiles with friend-request methods; follow/follower methods deleted; no broken call sites. |
| 2 | Tapping any partner name/avatar navigates to a fully rendered `PublicProfileFragment` with correct friendship action buttons. |
| 3 | `FriendsFragment` loads all three lists. Accept/Decline/Remove actions update state. `BlockedUsersFragment` renders and unblock works. |
| 4 | Private intent toggle appears on Create Intent form; friend picker populates from friends list; submission sends correct `is_private` + `invited_friend_id`. Auth gate redirects unauthenticated users to login and restores hotspot on return. |
| 5 | Pass dialog shows correct text for public vs. private. Private invite sender sees waiting state immediately. Proposal countdown timer visible and auto-refreshes on expiry. |
| 6 | Chat button present on Session Detail, navigates to ChatFragment. Activation window enforced with countdown. Report Incident accessible from ACTIVE session and History. Complete Walk countdown enforced. Confetti animation plays on double-accept. |
| 7 | All 8 FCM event types navigate to correct screens. Notification tap routes to correct destination after marking read. |
| 8 | `LeaderboardFragment` accessible from Home/Profile; rows tap to Public Profile; authenticated user row highlighted. Intent expiry timers visible in FindingFragment. Hotspot pins show visual weight proportional to `openIntentCount`. |
