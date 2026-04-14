# WalkMate Frontend — Execution Playbook
**Source plan:** `docs/dev/frontend/implementation_plan.md`
**Date:** 2026-04-13

This playbook contains the verbatim AI prompts to execute each phase of the implementation plan. Every prompt is self-contained and follows a strict protocol to guarantee correctness, traceability, and clean context handoff between phases.

---

## Mandatory Protocols (Apply to Every Phase)

### Index Refresh Command
Run this command **before starting any phase** to ensure the ACKG MCP symbol index reflects the current codebase state:
```
node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"
```

### ACKG Search Protocol
Before writing any code, use the ACKG MCP tools to locate all relevant files:
- `mcp__ackg-walkmate__search_symbols` — find classes and methods by name substring
- `mcp__ackg-walkmate__get_file_outline` — list all methods in a file before editing it
- `mcp__ackg-walkmate__find_usages` — find every call site before deleting a method

### Phase Report (End of Every Phase)
Every phase must conclude with a **Phase Completion Report** written as a final message containing:
1. **Files Modified** — list every file changed with a one-line summary of the change
2. **Files Created** — list every new file with its package path
3. **Broken Call Sites Fixed** — any other files that were updated to accommodate the changes
4. **Known Risks / Follow-ups** — anything the next phase must be aware of
5. **Verification** — confirmation that the project compiles without errors

This report is the sole context document passed to the next phase prompt.

---

## Phase 0 — Pre-flight Cleanup & Foundation Hardening

### Objective
Remove the banned `triggerMatch` UI action (UC-18 violation) and harden global HTTP error parsing to use `error.code` instead of HTTP status codes.

---

### Phase 0 Prompt

```
You are a Senior Java Android Developer working on the WalkMate project.

## Setup
First, run the ACKG index refresh:
  node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Context
The backend use cases (docs/single-source-of-truth/use-cases/backend_use_cases.md) explicitly state that the
triggerMatch endpoint (POST /api/v1/intents/{intentId}/match) is an INTERNAL API and must never be exposed as
a user-triggerable button. A gap analysis confirmed that FindingFragment currently exposes this action. This
phase removes it and fixes global error handling.

## ACKG Search Protocol — Run Before Coding
Use the ACKG MCP tools to locate the following before making any changes:
1. search_symbols("triggerMatch") — find all definitions and call sites
2. search_symbols("onFindMatchClicked") — find button handler in FindingFragment
3. get_file_outline("frontend/src/main/java/com/walkmate/ui/matches/finding/FindingFragment.java")
4. get_file_outline("frontend/src/main/java/com/walkmate/ui/matches/MatchesViewModel.java")
5. search_symbols("noMatchFoundEvent") — find the associated LiveData pair
6. search_symbols("handleApiError") or search_symbols("onResponse") in all RepositoryImpl files

## Task 0.1 — Remove triggerMatch User Action
### Target files:
- frontend/src/main/java/com/walkmate/ui/matches/finding/FindingFragment.java
- frontend/src/main/java/com/walkmate/ui/matches/MatchesViewModel.java
- frontend/src/main/res/layout/fragment_finding.xml (remove the "Find Match" button view)

### Steps:
1. In fragment_finding.xml: delete the "Find Match" / "Trigger Match" button declaration.
2. In FindingFragment: delete onFindMatchClicked() and any click-listener wiring for it.
3. In MatchesViewModel: delete the triggerMatch() method and the getNoMatchFoundEvent() /
   consumeNoMatchFoundEvent() LiveData pair.
4. In WalkIntentRepository.java (domain interface): add a comment to findMatch():
   "Internal API only — must not be called from the UI layer."
   Do NOT delete the method — it may be needed for tests or internal callers.
5. Search for any remaining call sites to triggerMatch() or noMatchFoundEvent and remove them.

## Task 0.2 — Fix Global Error Handling (error.code-first parsing)
### Target files:
- All files matching: frontend/src/main/java/com/walkmate/data/repository/*RepositoryImpl.java
- frontend/src/main/java/com/walkmate/core/util/ (create ValidationErrorParser.java here if needed)

### Steps:
1. Audit every onResponse() callback across all RepositoryImpl files.
   Confirm that domain error classification reads apiResponse.getError().getCode() (the error.code string),
   not the HTTP status integer. Flag every location that switches on HTTP status for domain error type.
2. Replace HTTP-status-based error switches with error.code string switches.
3. For VALIDATION_ERROR handling: confirm error.message is parsed by splitting on ", " (comma-space) to
   produce field-level error entries. If no shared utility exists, create:
     core/util/ValidationErrorParser.java
   with a single static method:
     public static Map<String, String> parse(String message)
   that splits the comma-separated string into a Map<fieldName, reason>.
4. Verify TokenRefreshAuthenticator triggers AuthEventBus.FORCE_LOGOUT on 401 responses and does not
   confuse 401 with a 400 domain error.

## Output
- Implement all changes above.
- Do not refactor unrelated code.
- Ensure the project compiles without errors after your changes.

## End of Phase Report
Conclude with a Phase 0 Completion Report containing:
1. Files Modified (each file + one-line summary)
2. Files Created
3. Broken Call Sites Fixed
4. Known Risks / Follow-ups for Phase 1
5. Verification: project compiles cleanly (yes/no + any notes)
```

---

## Phase 1 — Social Domain Layer Rebuild

### Objective
Remove the obsolete follow/follower model and implement the friend-request domain model to match the backend `FriendsController` contract.

### Inputs
- Phase 0 Completion Report (paste in full at the start of the prompt)

---

### Phase 1 Prompt

```
You are a Senior Java Android Developer working on the WalkMate project.

## Setup
Run the ACKG index refresh:
  node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Context
[PASTE PHASE 0 COMPLETION REPORT HERE]

The backend social model is friend-request based (Friendship entity with PENDING/ACCEPTED/DECLINED states).
The frontend SocialRepository still exposes follow/unfollow/getFollowers/getFollowing — these map to
non-existent backend endpoints. This phase tears out the stale model and builds the correct one.

## ACKG Search Protocol — Run Before Coding
1. get_file_outline("frontend/src/main/java/com/walkmate/domain/social/SocialRepository.java")
2. get_file_outline("frontend/src/main/java/com/walkmate/data/datasource/remote/api/SocialApiService.java")
3. get_file_outline("frontend/src/main/java/com/walkmate/data/repository/SocialRepositoryImpl.java")
4. get_file_outline("frontend/src/main/java/com/walkmate/data/mapper/SocialMapper.java")
5. search_symbols("follow") — find all usages of follow/unfollow/getFollowers/getFollowing
6. search_symbols("getFriends") — find current usages to ensure they are preserved
7. search_symbols("UserSummary") — find the domain model to extend

## Task 1.1 — Remove Follow/Follower Artifacts
### Target files:
- domain/social/SocialRepository.java
- data/datasource/remote/api/SocialApiService.java
- data/repository/SocialRepositoryImpl.java

Remove: follow(), unfollow(), getFollowers(), getFollowing() from all three files.
Fix any call sites that reference these deleted methods (search with find_usages for each).
Replace any HomeViewModel or ProfileViewModel usages of getFollowing() with getFriends().

## Task 1.2 — Extend UserSummary Domain Model
### Target file: domain/social/UserSummary.java

Add field: String friendshipStatus  (values: "NONE", "PENDING_SENT", "PENDING_RECEIVED", "FRIENDS")
Remove field: isFollowing (if present)

## Task 1.3 — Create FriendRequest Domain Model
### New file: domain/social/FriendRequest.java

Fields:
  String requestId
  String senderId
  String senderName
  String senderAvatarUrl
  String receiverId
  String status  // "PENDING" | "ACCEPTED" | "DECLINED"
  String createdAt

## Task 1.4 — Add Friend-Request API Endpoints
### Target file: data/datasource/remote/api/SocialApiService.java

Add the following Retrofit endpoint declarations:
  @POST("friends/{userId}/request")         Call<ApiResponse<Void>> sendFriendRequest(@Path("userId") String userId)
  @POST("friends/requests/{id}/accept")     Call<ApiResponse<Void>> acceptFriendRequest(@Path("id") String id)
  @POST("friends/requests/{id}/decline")    Call<ApiResponse<Void>> declineFriendRequest(@Path("id") String id)
  @GET("friends/requests/incoming")         Call<ApiResponse<List<FriendRequestResponse>>> getIncomingRequests()
  @GET("friends/requests/outgoing")         Call<ApiResponse<List<FriendRequestResponse>>> getOutgoingRequests()
  @DELETE("friends/{userId}")               Call<ApiResponse<Void>> removeFriend(@Path("userId") String userId)
  @GET("users/{userId}")                    Call<ApiResponse<PublicUserResponse>> getPublicProfile(@Path("userId") String userId)

### New DTO files to create:
- data/datasource/remote/dto/response/social/FriendRequestResponse.java
  Fields: requestId, senderId, senderName, senderAvatarUrl, receiverId, status, createdAt
- data/datasource/remote/dto/response/social/PublicUserResponse.java
  Fields: userId, fullName, avatarUrl, bio, tags (List<String>), trustScore (float),
          gender (String), friendshipStatus (String)

## Task 1.5 — Add Friend-Request Repository Methods
### Target files:
- domain/social/SocialRepository.java (add method signatures)
- data/repository/SocialRepositoryImpl.java (implement them)
- data/mapper/SocialMapper.java (add toFriendRequest() and toUserSummary(PublicUserResponse) mappers)

New methods on SocialRepository interface:
  void sendFriendRequest(String userId, DomainCallback<Void> cb);
  void acceptFriendRequest(String requestId, DomainCallback<Void> cb);
  void declineFriendRequest(String requestId, DomainCallback<Void> cb);
  void getIncomingRequests(DomainCallback<List<FriendRequest>> cb);
  void getOutgoingRequests(DomainCallback<List<FriendRequest>> cb);
  void removeFriend(String userId, DomainCallback<Void> cb);
  void getPublicProfile(String userId, DomainCallback<UserSummary> cb);

Implement each in SocialRepositoryImpl using the ExecutorService pattern consistent with the
rest of the repository layer. Map responses using SocialMapper.

Also add to SocialApiService:
  @GET("users/me/blocked")  Call<ApiResponse<List<UserSummaryResponse>>> getBlockedUsers()
Add corresponding domain method:
  void getBlockedUsers(DomainCallback<List<UserSummary>> cb);

## Output
- Implement all changes.
- Do not modify any UI layer files.
- Ensure the project compiles without errors.

## End of Phase Report
Conclude with a Phase 1 Completion Report containing:
1. Files Modified
2. Files Created
3. Broken Call Sites Fixed
4. Known Risks / Follow-ups for Phase 2
5. Verification: project compiles cleanly
```

---

## Phase 2 — Public User Profile Screen

### Objective
Build the `PublicProfileFragment` and wire all entry points to it.

### Inputs
- Phase 1 Completion Report

---

### Phase 2 Prompt

```
You are a Senior Java Android Developer working on the WalkMate project.

## Setup
Run the ACKG index refresh:
  node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Context
[PASTE PHASE 1 COMPLETION REPORT HERE]

Users must be able to tap any partner's name/avatar anywhere in the app to view a public profile.
This phase builds the PublicProfileFragment and connects it to existing screens.

## ACKG Search Protocol — Run Before Coding
1. get_file_outline("frontend/src/main/java/com/walkmate/ui/profile/ProfileFragment.java")
2. get_file_outline("frontend/src/main/java/com/walkmate/ui/profile/ProfileViewModel.java")
3. get_file_outline("frontend/src/main/java/com/walkmate/domain/social/SocialRepository.java")
4. get_file_outline("frontend/src/main/java/com/walkmate/domain/gamification/GamificationRepository.java")
5. get_file_outline("frontend/src/main/java/com/walkmate/domain/review/ReviewRepository.java")
6. search_symbols("AvatarInitialView") — confirm custom view API
7. search_symbols("WalkMateStatColumn") — confirm custom view API
8. search_symbols("TagChipGroup") — confirm custom view API
9. search_symbols("nav_graph") — find the navigation graph file path
10. get_file_outline("frontend/src/main/java/com/walkmate/ui/history/SessionHistoryFragment.java")
11. get_file_outline("frontend/src/main/java/com/walkmate/ui/home/HomeFragment.java")
12. get_file_outline("frontend/src/main/java/com/walkmate/ui/matches/proposal/ProposalFragment.java")

## Task 2.1 — Create PublicProfileUiState
### New file: ui/profile/public/PublicProfileUiState.java

Immutable state class. Fields:
  boolean isLoading
  String error
  UserSummary profile
  List<Badge> badges
  UserStats stats
  List<Review> reviews
  String friendshipStatus  // "NONE" | "PENDING_SENT" | "PENDING_RECEIVED" | "FRIENDS"
  boolean isSelf           // true when viewing own profile

## Task 2.2 — Create PublicProfileViewModel
### New files:
- ui/profile/public/PublicProfileViewModel.java
- ui/profile/public/PublicProfileViewModelFactory.java

Constructor dependencies: SocialRepository, GamificationRepository, ReviewRepository

Methods:
  loadProfile(String userId) — fires parallel: getPublicProfile(), getBadges(userId),
    getStats(userId), getReviewsForUser(userId). Merges into PublicProfileUiState.
    Sets isSelf = true if userId matches the stored local user ID.
  sendFriendRequest(String userId)
  removeFriend(String userId)
  acceptIncomingRequest(String requestId)
  declineIncomingRequest(String requestId)
  blockUser(String userId) — calls SocialRepository.block(), then posts a navigateBackEvent

ViewModelFactory injects singletons from WalkMateApplication using the existing manual DI pattern.

## Task 2.3 — Create PublicProfileFragment and Layout
### New files:
- ui/profile/public/PublicProfileFragment.java
- res/layout/fragment_public_profile.xml

Layout sections (in order):
  - AvatarInitialView (large, top center)
  - Full name TextView, bio TextView
  - TagChipGroup (display mode, wm_chipStyle=display)
  - Stats row: three WalkMateStatColumn views (Total Distance, Sessions, Trust Score)
  - Badges section: horizontal ChipGroup or RecyclerView; empty state: "No badges yet."
  - Reviews RecyclerView (reuse existing review item layout)
  - Friendship action area (visibility driven by friendshipStatus):
      NONE           → WalkMateButton "Add Friend" (FILLED)
      PENDING_SENT   → WalkMateButton "Request Sent" (OUTLINED, disabled)
      PENDING_RECEIVED → WalkMateButton "Accept" (FILLED) + WalkMateButton "Decline" (OUTLINED)
      FRIENDS        → WalkMateButton "Invite Walk" (FILLED) + WalkMateButton "Remove Friend" (OUTLINED)
  - Overflow menu (when not isSelf): "Block User" item
  - When isSelf == true: hide ALL friendship action views

Unauthenticated guard: if not logged in and any action button tapped, show Toast
"Log in to manage friendships." and navigate to AuthActivity. Do not call any API.

Fragment receives userId via Bundle argument (key: "userId").
Calls viewModel.loadProfile(userId) in onViewCreated().

## Task 2.4 — Add Navigation Route
### Target file: res/navigation/nav_graph.xml (locate via ACKG or Glob)

Add <fragment> destination for PublicProfileFragment with argument:
  <argument android:name="userId" app:argType="string" />

Wire navigation actions from:
  - SessionHistoryFragment (partner name click)
  - PostSessionSummaryFragment (partner name click)
  - ProposalFragment (partner avatar/name click on each proposal card)
  - HomeFragment (quick-invite candidate card click)

For each entry point, add the navigation action in the Fragment's click handler:
  Bundle args = new Bundle(); args.putString("userId", partnerId);
  NavHostFragment.findNavController(this).navigate(R.id.action_X_to_publicProfileFragment, args);

## Output
- Implement all changes. Use existing Custom Views; do not create new ones.
- Ensure the project compiles without errors.

## End of Phase Report
Conclude with a Phase 2 Completion Report containing:
1. Files Modified
2. Files Created
3. Entry Points Wired
4. Known Risks / Follow-ups for Phase 3
5. Verification: project compiles cleanly
```

---

## Phase 3 — Friends & Social Management Screens

### Objective
Build the multi-tab Friends screen and the Blocked Users settings screen.

### Inputs
- Phase 2 Completion Report

---

### Phase 3 Prompt

```
You are a Senior Java Android Developer working on the WalkMate project.

## Setup
Run the ACKG index refresh:
  node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Context
[PASTE PHASE 2 COMPLETION REPORT HERE]

Users need to manage their social network: view accepted friends, respond to incoming friend requests,
view sent requests, and unblock users. This phase builds FriendsFragment (tabbed) and BlockedUsersFragment.

## ACKG Search Protocol — Run Before Coding
1. get_file_outline("frontend/src/main/java/com/walkmate/domain/social/SocialRepository.java")
2. get_file_outline("frontend/src/main/java/com/walkmate/ui/profile/ProfileFragment.java")
3. search_symbols("WalkMateButton") — confirm API (wm_buttonStyle, wm_text)
4. search_symbols("AvatarInitialView") — confirm API
5. search_symbols("PagerAdapter") — find an existing PagerAdapter for reference pattern
6. get_file_outline("frontend/src/main/java/com/walkmate/ui/auth/AuthActivity.java") — tab container pattern reference
7. search_symbols("WalkMateApplication") — find where singletons are registered

## Task 3.1 — Create FriendsUiState
### New file: ui/social/friends/FriendsUiState.java

Fields:
  boolean isLoading
  String error
  List<UserSummary> friends
  List<FriendRequest> incomingRequests
  List<FriendRequest> outgoingRequests
  int incomingBadgeCount  // = incomingRequests.size()

## Task 3.2 — Create FriendsViewModel and Factory
### New files:
- ui/social/friends/FriendsViewModel.java
- ui/social/friends/FriendsViewModelFactory.java

Constructor dependency: SocialRepository

Methods:
  loadAll() — fires getFriends(), getIncomingRequests(), getOutgoingRequests() in parallel;
    posts loading state immediately; merges all three results into FriendsUiState.
  acceptRequest(String requestId) — calls SocialRepository.acceptFriendRequest(), then loadAll().
  declineRequest(String requestId) — calls SocialRepository.declineFriendRequest(), then loadAll().
  removeFriend(String userId) — calls SocialRepository.removeFriend(), then loadAll().
  navigateToInviteWalk(String friendId) — posts a MutableLiveData<String> inviteWalkEvent with friendId.

## Task 3.3 — Create FriendsFragment (ViewPager2 Container)
### New files:
- ui/social/friends/FriendsFragment.java
- ui/social/friends/FriendsPagerAdapter.java
- ui/social/friends/FriendListFragment.java        (Friends tab)
- ui/social/friends/IncomingRequestsFragment.java  (Incoming tab)
- ui/social/friends/OutgoingRequestsFragment.java  (Sent tab)
- ui/social/friends/FriendsAdapter.java            (RecyclerView for friends)
- ui/social/friends/FriendRequestsAdapter.java     (RecyclerView for requests)
- Layouts:
    fragment_friends.xml               (TabLayout + ViewPager2)
    fragment_friend_list.xml           (RecyclerView + empty state)
    fragment_incoming_requests.xml     (RecyclerView + empty state)
    fragment_outgoing_requests.xml     (RecyclerView + empty state)
    item_friend_card.xml               (AvatarInitialView + name + action buttons)
    item_friend_request_card.xml       (AvatarInitialView + name + Accept/Decline buttons)

Sub-fragments share FriendsViewModel via ViewModelProvider(requireParentFragment()).

Friends tab (FriendListFragment):
  Each card: AvatarInitialView, name, three buttons:
    "Invite Walk" → calls viewModel.navigateToInviteWalk(userId), container observes and
                    navigates to ExploreFragment with friendId extra.
    "View Profile" → navigates to PublicProfileFragment with userId.
    "Remove Friend" → shows confirmation AlertDialog, on confirm calls viewModel.removeFriend(userId).
  Empty state: "You have no friends yet."

Incoming Requests tab (IncomingRequestsFragment):
  Each card: sender AvatarInitialView, sender name, "Accept" button, "Decline" button.
  Empty state: "No incoming friend requests."

Sent Requests tab (OutgoingRequestsFragment):
  Each card: receiver AvatarInitialView, receiver name, "Pending" status label (no action buttons).
  Empty state: "No sent requests."

FriendsFragment entry point: add a "Friends" button or row in ProfileFragment that navigates here.
Add nav_graph route and wire ProfileFragment click handler.

## Task 3.4 — Create BlockedUsersFragment
### New files:
- ui/social/blocked/BlockedUsersFragment.java
- ui/social/blocked/BlockedUsersViewModel.java
- ui/social/blocked/BlockedUsersViewModelFactory.java
- ui/social/blocked/BlockedUsersUiState.java
- ui/social/blocked/BlockedUsersAdapter.java
- res/layout/fragment_blocked_users.xml
- res/layout/item_blocked_user_card.xml

BlockedUsersUiState fields: boolean isLoading, String error, List<UserSummary> blockedUsers

BlockedUsersViewModel methods:
  loadBlocked() — calls SocialRepository.getBlockedUsers(), posts result to uiState.
  unblock(String userId) — calls SocialRepository.unblock(), removes user from list on success,
    shows Toast "User unblocked."

Entry point: Add an overflow menu item "Blocked Users" in ProfileFragment that navigates to
BlockedUsersFragment. Add nav_graph route.

## Output
- Implement all tasks. Follow the sub-fragment sharing ViewModel pattern from MatchesFragment as reference.
- Do not modify any data/domain layer files.
- Ensure the project compiles without errors.

## End of Phase Report
Conclude with a Phase 3 Completion Report containing:
1. Files Modified
2. Files Created
3. Navigation Routes Added
4. Known Risks / Follow-ups for Phase 4
5. Verification: project compiles cleanly
```

---

## Phase 4 — Private Intent Flow & Auth Gate

### Objective
Add the `is_private` toggle + friend-picker to the Create Intent form. Enforce the auth gate on the Explore hotspot tap.

### Inputs
- Phase 3 Completion Report

---

### Phase 4 Prompt

```
You are a Senior Java Android Developer working on the WalkMate project.

## Setup
Run the ACKG index refresh:
  node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Context
[PASTE PHASE 3 COMPLETION REPORT HERE]

UC-15 requires users to create private walk invites by toggling is_private and picking a friend.
The DTO already supports this (isPrivate, invitedFriendId fields exist) but the UI surface is absent.
Also, unauthenticated users who tap a hotspot must be redirected to login and returned afterward.

## ACKG Search Protocol — Run Before Coding
1. get_file_outline("frontend/src/main/java/com/walkmate/ui/explore/createintent/CreateIntentUiState.java")
2. get_file_outline("frontend/src/main/java/com/walkmate/ui/explore/createintent/CreateIntentViewModel.java")
3. get_file_outline("frontend/src/main/java/com/walkmate/ui/explore/createintent/CreateIntentViewModelFactory.java")
4. get_file_outline("frontend/src/main/java/com/walkmate/data/datasource/remote/dto/request/walkintent/CreateWalkIntentRequest.java")
5. get_file_outline("frontend/src/main/java/com/walkmate/ui/explore/ExploreViewModel.java")
6. get_file_outline("frontend/src/main/java/com/walkmate/ui/explore/ExploreViewModelFactory.java")
7. get_file_outline("frontend/src/main/java/com/walkmate/ui/explore/ExploreFragment.java")
8. get_file_outline("frontend/src/main/java/com/walkmate/domain/user/UserRepository.java")

## Task 4.1 — Extend CreateIntentUiState
### Target file: ui/explore/createintent/CreateIntentUiState.java

Add fields:
  boolean isPrivate
  String invitedFriendId      // null for public intents
  String invitedFriendName    // display label for the friend picker row
  List<UserSummary> friendList // populated when private toggle is enabled
  boolean isFriendListLoading
  String privateIntentError   // inline error for private-intent-specific failures

## Task 4.2 — Extend CreateIntentViewModel
### Target files:
- ui/explore/createintent/CreateIntentViewModel.java
- ui/explore/createintent/CreateIntentViewModelFactory.java

Constructor change: inject SocialRepository alongside existing WalkIntentRepository.
Update CreateIntentViewModelFactory to obtain SocialRepository from WalkMateApplication.

New methods:
  togglePrivate(boolean isPrivate) — if enabling private, call SocialRepository.getFriends()
    and set friendList in UiState. If disabling, clear invitedFriendId/Name and friendList.
  selectFriend(UserSummary friend) — sets invitedFriendId and invitedFriendName in UiState.

Update submit():
  - Client-side guard: if isPrivate == true and invitedFriendId == null,
    post error "Please select a friend to invite." and return early.
  - Pass isPrivate and invitedFriendId into CreateWalkIntentRequest (fields already exist).

Error handling in the submit() onError callback — parse error.code and react as follows:
  "INTENT_OVERLAPPING":
    Post a MutableLiveData<String> blockingDialogEvent with message:
    "You already have an active intent during this time. Cancel it first or choose a different time."
    The Fragment must render this as a blocking AlertDialog (not a Toast) — do NOT dismiss the form.
  "INTENT_OVERLAPPING_SESSION":
    Post blockingDialogEvent with message:
    "You already have a confirmed walk session during this time."
    Again, render as a blocking AlertDialog.
  "INTENT_PRIVATE_FRIEND_NOT_ACCEPTED":
    Post inline error to uiState.privateIntentError:
    "You can only send a private invite to an accepted friend."
  "INVALID_TIME_RANGE":
    Post inline error: "End time must be after start time."
  "INVALID_AGE_RANGE":
    Post inline error: "Minimum age cannot exceed maximum age."
  "HOTSPOT_NOT_FOUND":
    Post Toast error and navigate back to map (post a navigateBackToMapEvent).
  "VALIDATION_ERROR":
    Parse error.message using ValidationErrorParser.parse() and post field-level errors.
  All other errors:
    Post generic Toast: "Something went wrong. Please try again."

Add blockingDialogEvent as a MutableLiveData<String> on CreateIntentViewModel (null = no dialog).
Add consumeBlockingDialog() to clear it after the Fragment displays it.
In ExploreFragment, observe blockingDialogEvent: on non-null value, show an AlertDialog with the
message and a single "OK" button. Call viewModel.consumeBlockingDialog() in the button handler.

## Task 4.3 — Update Create Intent Form UI
### Target file: ui/explore/ExploreFragment.java
### Target layout: the Create Intent bottom-sheet layout within ExploreFragment

Changes to the layout:
  1. Add a SwitchCompat row labeled "Private Walk" below the description field.
  2. Below the switch: add a friend-picker row (initially GONE).
     The row shows invitedFriendName or "Select a friend" placeholder text.
  3. Add an error TextView below the friend-picker row for privateIntentError (initially GONE).

Changes to ExploreFragment:
  1. Observe uiState.isPrivate: toggle visibility of the friend-picker row.
  2. On friend-picker row tap: show a BottomSheetDialogFragment (new file: FriendPickerBottomSheet.java)
     that renders uiState.friendList in a RecyclerView.
     On item selection: call createIntentViewModel.selectFriend(friend) and dismiss sheet.
  3. Show "You have no friends yet." empty state in FriendPickerBottomSheet if friendList is empty.
  4. Render uiState.privateIntentError as inline error.
  5. Wire the switch onCheckedChangeListener to call createIntentViewModel.togglePrivate(checked).

New file:
  ui/explore/createintent/FriendPickerBottomSheet.java
  res/layout/bottom_sheet_friend_picker.xml

## Task 4.4 — Auth Gate on Hotspot Tap
### Target files:
- ui/explore/ExploreViewModel.java
- ui/explore/ExploreViewModelFactory.java

Steps:
1. Inject UserRepository into ExploreViewModel. Update ExploreViewModelFactory accordingly.
2. In selectHotspot(Hotspot hotspot):
   a. Call userRepository.getAccessToken().
   b. If token is null or empty: post a MutableLiveData<Hotspot> pendingHotspotEvent with the
      selected hotspot. Do NOT transition to SETUP state.
   c. If authenticated: proceed with existing transition to SETUP (no change).
3. In ExploreFragment:
   a. Observe pendingHotspotEvent. On fire:
      - Save hotspot.id to SharedPreferences under key "pending_hotspot_id".
      - Launch AuthActivity via Intent (finish = false, so ExploreFragment stays in back stack).
      - Call viewModel.consumePendingHotspot() to clear the event.
   b. In onResume(): check SharedPreferences for "pending_hotspot_id".
      If present AND userRepository.getAccessToken() is non-null:
        - Clear the key.
        - Fetch the hotspot by ID from the existing hotspots list in uiState.
        - Call viewModel.selectHotspot(hotspot) to resume the flow.

## Output
- Implement all tasks.
- Ensure the project compiles without errors.

## End of Phase Report
Conclude with a Phase 4 Completion Report containing:
1. Files Modified
2. Files Created
3. Known Risks / Follow-ups for Phase 5
4. Verification: project compiles cleanly
```

---

## Phase 5 — Proposal Enhancements

### Objective
Fix private invite pre-accepted state, differentiate the pass dialog, and add expiry countdown timers.

### Inputs
- Phase 4 Completion Report

---

### Phase 5 Prompt

```
You are a Senior Java Android Developer working on the WalkMate project.

## Setup
Run the ACKG index refresh:
  node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Context
[PASTE PHASE 4 COMPLETION REPORT HERE]

Three behavioral gaps in the proposal flow must be fixed:
1. Private invite senders must see a "waiting for partner" state immediately (no Accept tap required).
2. The Pass confirmation dialog must show different text for public vs. private invite proposals.
3. Proposal cards must show a live 5-minute expiry countdown that auto-refreshes on expiry.

## ACKG Search Protocol — Run Before Coding
1. get_file_outline("frontend/src/main/java/com/walkmate/domain/walkproposal/WalkProposal.java") or search_symbols("WalkProposal")
2. get_file_outline("frontend/src/main/java/com/walkmate/data/mapper/WalkProposalMapper.java") or search_symbols("WalkProposalMapper")
3. get_file_outline("frontend/src/main/java/com/walkmate/ui/matches/proposal/ProposalFragment.java")
4. get_file_outline("frontend/src/main/java/com/walkmate/ui/matches/MatchesViewModel.java") — focus on passProposal() and acceptProposal()
5. search_symbols("WalkProposalResponse") — find the response DTO

## Task 5.1 — Add Private Invite Fields to WalkProposal Domain Model
### Target file: domain/walkproposal/WalkProposal.java (or the relevant domain model)

Add fields:
  boolean isPrivateInvite        // from backend response field is_private
  boolean isCurrentUserAccepted  // whether the calling user has already accepted this proposal

Update the corresponding mapper (WalkProposalMapper or equivalent) to map these from the API response.
Update the WalkProposalResponse DTO if needed to include these fields from the server.

## Task 5.2 — Private Invite Pre-Accepted Rendering (GAP-11)
### Target file: ui/matches/proposal/ProposalFragment.java — renderState() method

In renderState(), BEFORE any button-state logic:
  if (proposal.isCurrentUserAccepted()) {
      // Show waiting overlay immediately
      // Disable Accept button
      // Keep Pass button enabled
      showWaitingForPartnerOverlay("You accepted! Waiting for your partner...");
      acceptButton.setEnabled(false);
      return; // skip further button logic for this card
  }

This ensures the sender of a private invite sees the waiting state on first render.

## Task 5.3 — Differentiated Pass Dialog (GAP-12)
### Target file: ui/matches/proposal/ProposalFragment.java — onPass() method

Replace the current single-message confirmation dialog with:
  String dialogMessage = proposal.isPrivateInvite()
      ? "Decline this private invite? This invite will be closed and you will not be added to the public wait list."
      : "Pass on this match? Your intent will stay active and we'll keep looking for other partners.";

### Target file: ui/matches/MatchesViewModel.java — passProposal() method

Add a boolean isPrivateInvite parameter to passProposal(String proposalId, boolean isPrivateInvite).

Post-pass navigation logic:
  - If isPrivateInvite == true: do NOT scroll to Intent/Finding tab after success.
    Just reload proposals and stay on Proposal tab.
  - If isPrivateInvite == false: existing behavior (scroll to Finding tab; intents revert to OPEN).

Update the call site in ProposalFragment.onPass() to pass proposal.isPrivateInvite().

## Task 5.4 — Proposal Expiry Countdown Timer (GAP-17)
### Target file: ui/matches/proposal/ProposalFragment.java

In renderState(), for each proposal card rendered:
  1. Compute millisUntilExpiry = proposal.expiresAtMs - System.currentTimeMillis().
  2. Start a CountDownTimer(millisUntilExpiry, 1000):
       onTick: update a TextView showing "Expires in M:SS".
       onFinish: call viewModel.loadAll() to refresh the list.
  3. Store active timers in a Map<String, CountDownTimer> keyed by proposalId.
  4. Cancel all timers in onDestroyView().
  5. When renderState() is called again, cancel any existing timer for a proposalId before
     starting a new one (handles re-renders without duplicate timers).

## Task 5.5 — Proposal Accept: Optimistic Locking Error Handling (UC-20 / Invariant X-5)
### Target file: ui/matches/MatchesViewModel.java — acceptProposal() method

In the onError callback of acceptProposal(), add domain-error-code handling for the
two concurrent-modification scenarios defined in UC-20:

  "PROPOSAL_CONCURRENT_MODIFICATION":
    - Post a Toast error message: "A conflict occurred. Please refresh and try again."
    - Call loadAll() to refresh the full proposals + sessions state.

  "PROPOSAL_INTENT_NO_LONGER_OPEN":
    - Post a Toast error message:
      "Could not confirm — one of the intents is no longer available. The proposal has been cancelled."
    - Call loadAll() to reflect the cancelled proposal.

  "PROPOSAL_ALREADY_TERMINAL":
    - Post Toast: "This proposal is no longer active." then navigate back (post navigateBackEvent).

  "PROPOSAL_NOT_PARTICIPANT":
    - Post Toast: "Permission denied."

  "PROPOSAL_NOT_FOUND":
    - Post Toast: "Proposal not found." then navigate back.

These errors must be read from error.code (not HTTP status), consistent with the Phase 0
error-handling foundation. Do not modify the Case A / Case B success-path logic.

## Output
- Implement all tasks.
- Ensure the project compiles without errors.

## End of Phase Report
Conclude with a Phase 5 Completion Report containing:
1. Files Modified
2. Files Created
3. Known Risks / Follow-ups for Phase 6
4. Verification: project compiles cleanly
```

---

## Phase 6 — Session Lifecycle Enhancements

### Objective
Wire the Chat button, enforce activation window, broaden Report Incident entry points, enforce 5-minute Complete Walk minimum, and add a celebration animation.

### Inputs
- Phase 5 Completion Report

---

### Phase 6 Prompt

```
You are a Senior Java Android Developer working on the WalkMate project.

## Setup
Run the ACKG index refresh:
  node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Context
[PASTE PHASE 5 COMPLETION REPORT HERE]

Five session lifecycle gaps must be closed:
1. Chat button missing on Session Detail screen.
2. Activation window enforcement ("I'm Here!" must be disabled outside the window).
3. Report Incident must be accessible from ACTIVE sessions and from History (not just post-abort).
4. "Complete Walk" button must be disabled for the first 5 minutes.
5. A celebration animation must play when a proposal is double-accepted.

## ACKG Search Protocol — Run Before Coding
1. get_file_outline("frontend/src/main/java/com/walkmate/ui/matches/session/SessionFragment.java")
2. get_file_outline("frontend/src/main/java/com/walkmate/ui/matches/MatchesViewModel.java") — activateSession()
3. get_file_outline("frontend/src/main/java/com/walkmate/ui/tracking/TrackingViewModel.java")
4. get_file_outline("frontend/src/main/java/com/walkmate/ui/tracking/TrackingScreenActivity.java")
5. get_file_outline("frontend/src/main/java/com/walkmate/ui/report/ReportIncidentFragment.java")
6. get_file_outline("frontend/src/main/java/com/walkmate/ui/history/SessionHistoryFragment.java")
7. get_file_outline("frontend/src/main/java/com/walkmate/ui/gamification/PostSessionSummaryFragment.java")
8. get_file_outline("frontend/src/main/java/com/walkmate/ui/matches/proposal/ProposalFragment.java")
9. get_file_outline("frontend/src/main/java/com/walkmate/ui/chat/ChatFragment.java")
10. get_file_outline("frontend/src/main/java/com/walkmate/ui/chat/ChatViewModel.java")

## Task 6.1 — Chat Button on Session Detail + Cancel Walk Validation (GAP-13, UC-25)
### Target files: ui/matches/session/SessionFragment.java + fragment_session.xml

1. In fragment_session.xml, add an ImageButton (speech-bubble icon) in the toolbar/top-right corner.
2. In SessionFragment.renderState():
   - Enable the chat button when session.status is "PENDING" or "ACTIVE".
   - Disable (setEnabled(false), set alpha 0.4) when status is terminal (COMPLETED, ABORTED, CANCELLED, NO_SHOW).
3. On chat button click: navigate to ChatFragment passing sessionId as argument.
   Bundle args = new Bundle(); args.putString("sessionId", session.sessionId);
   NavController.navigate(R.id.action_sessionFragment_to_chatFragment, args);
4. Confirm ChatViewModel accepts sessionId and update ChatViewModelFactory if needed.
5. Add the navigation action in nav_graph.xml.

Cancel Walk reason validation (UC-25):
6. In SessionFragment.showCancelReasonDialog(), update the dialog's positive button listener:
   - Read the reason text from the input field.
   - If the reason is null, empty, or blank after trim(): do NOT call the ViewModel.
     Instead, show an inline error on the EditText: setError("Please provide a reason.")
     and return (keep the dialog open).
   - Only call viewModel.cancelSession(sessionId, reason.trim()) when reason is non-empty.
   This client-side guard prevents a guaranteed 422 VALIDATION_ERROR round-trip.
   The server-side VALIDATION_ERROR for reason must still be handled in MatchesViewModel.cancelSession()
   onError: parse error.code == "VALIDATION_ERROR", post Toast "Please provide a reason." as
   defense-in-depth in case the guard is bypassed.

## Task 6.2 — Activation Window Enforcement (GAP-14)
### Target file: ui/matches/session/SessionFragment.java

For each PENDING session card in renderState():
  1. Compute:
       long windowOpenMs  = session.scheduledStartMs - 10 * 60 * 1000L;
       long windowCloseMs = session.scheduledStartMs + 15 * 60 * 1000L;
       long nowMs = System.currentTimeMillis();
  2. Set "I'm Here!" button enabled only when nowMs >= windowOpenMs && nowMs <= windowCloseMs.
  3. Show countdown TextView:
       - Before window opens: "Activation opens in HH:MM:SS" (CountDownTimer until windowOpenMs)
       - Inside window: "Activation closes in HH:MM:SS" (CountDownTimer until windowCloseMs)
       - After window closes: hide button, show "Activation window closed."
  4. Store CountDownTimers in a Map<String, CountDownTimer> keyed by sessionId. Cancel in onDestroyView().

SESSION_ACTIVATION_WINDOW_CLOSED error handling in MatchesViewModel.activateSession():
  When the error code "SESSION_ACTIVATION_WINDOW_CLOSED" is received in the onError callback:
    - Post ActivationResult(errorCode = "SESSION_ACTIVATION_WINDOW_CLOSED").
  In SessionFragment, when this ActivationResult arrives:
    - Show Toast "Activation window closed. Waiting for status update."
    - new Handler(Looper.getMainLooper()).postDelayed(() -> viewModel.loadAll(), 5000);
    - In the loadAll() callback, if the session no longer appears in the active list,
      NavController.navigate(R.id.action_matches_to_sessionHistory).

## Task 6.3 — Complete Walk 5-Minute Minimum (GAP-18)
### Target files: ui/tracking/TrackingViewModel.java + ui/tracking/TrackingScreenActivity.java

In TrackingViewModel:
  Add LiveData<Long> secondsUntilCompleteEnabled.
  In the existing 1-second timer tick:
    long elapsedSeconds = (System.currentTimeMillis() - session.startedAtMs) / 1000L;
    long secondsRemaining = Math.max(0L, 300L - elapsedSeconds);
    postValue(secondsRemaining) to secondsUntilCompleteEnabled.

In TrackingScreenActivity.renderState():
  Observe secondsUntilCompleteEnabled:
    if (seconds > 0):
      completeWalkButton.setEnabled(false);
      completeWalkButton.setText("Complete Walk (" + formatMSS(seconds) + ")");
    else:
      completeWalkButton.setEnabled(true);
      completeWalkButton.setText("Complete Walk");

On SESSION_COMPLETE_TOO_EARLY error (defense-in-depth): show Toast and re-disable the button.

## Task 6.4 — Report Incident from ACTIVE Session and History (GAP-15)
### Target files:
- ui/matches/session/SessionFragment.java + fragment_session.xml
- ui/history/SessionHistoryFragment.java (and its RecyclerView adapter)
- ui/report/ReportIncidentFragment.java (update to accept arguments)

In fragment_session.xml: add a "Report an Issue" text button or overflow menu item.
In SessionFragment: show this action when session.status == "ACTIVE".
  On click: navigate to ReportIncidentFragment with args: sessionId, reportedUserId (partner ID).

In SessionHistoryFragment's adapter item layout: add overflow menu (three-dot) for COMPLETED,
ABORTED, NO_SHOW cards. Menu item: "Report".
  On click: navigate to ReportIncidentFragment with sessionId and reportedUserId.

In ReportIncidentFragment:
  - Change from receiving context from PostSessionSummaryFragment only to accepting
    sessionId and reportedUserId as standard Fragment arguments (Bundle keys: "sessionId", "reportedUserId").
  - Add a reporting-window guard in onViewCreated(). The session object must be passed as a
    Bundle argument (add "sessionStatus" and "sessionTerminalAtMs" alongside sessionId/reportedUserId).
    Apply the following tiered check:

      long nowMs = System.currentTimeMillis();
      long terminalAtMs = args.getLong("sessionTerminalAtMs");
      String status = args.getString("sessionStatus");

      boolean windowExpired = false;
      if ("COMPLETED".equals(status)) {
          windowExpired = (nowMs - terminalAtMs) > 72 * 60 * 60 * 1000L;  // 72-hour window
      } else if ("ABORTED".equals(status) || "NO_SHOW".equals(status)) {
          windowExpired = (nowMs - terminalAtMs) > 24 * 60 * 60 * 1000L;  // 24-hour window
      }
      // ACTIVE sessions have no time gate — form is always enabled.
      // PENDING / CANCELLED must never reach this screen (guard in History adapter).

      if (windowExpired) {
          showWindowClosedBanner("The reporting window for this session has closed.");
          disableForm();
          return;
      }

    showWindowClosedBanner() renders a non-dismissable info banner at the top of the fragment.
    disableForm() sets all input fields and the submit button to setEnabled(false).

  - Update all navigation actions in nav_graph.xml for the new entry points.
  - All call sites (PostSessionSummaryFragment, SessionFragment, SessionHistoryFragment adapter)
    must pass sessionStatus and sessionTerminalAtMs in the navigation Bundle.
  - Ensure PostSessionSummaryFragment still passes the same arguments (verify existing call).

## Task 6.5 — Celebration Animation on Double-Accept (GAP-19)
### Target file: ui/matches/proposal/ProposalFragment.java + fragment_proposal.xml

In fragment_proposal.xml:
  Add a ViewGroup overlay (FrameLayout, match_parent, GONE) containing a Lottie
  LottieAnimationView (or a simple ScaleAnimation on a celebratory TextView "🎉 Match Confirmed!")
  positioned centrally. Set visibility GONE initially.

In ProposalFragment.onViewCreated():
  Observe MatchesViewModel.getScrollToTabEvent():
    When event fires with TAB_SESSION:
      1. Show the celebration overlay (VISIBLE).
      2. Start the animation.
      3. new Handler(Looper.getMainLooper()).postDelayed(() -> {
             celebrationOverlay.setVisibility(View.GONE);
             // existing tab-scroll navigation executes here
         }, 1500);
      4. Call viewModel.consumeScrollToTab() as usual.

In onDestroyView(): cancel any pending animation and null the overlay reference.

## Output
- Implement all tasks.
- Do not add Lottie as a dependency if it is not already in build.gradle — use a simple
  scale + alpha animation via Android's built-in Animator API instead.
- Ensure the project compiles without errors.

## End of Phase Report
Conclude with a Phase 6 Completion Report containing:
1. Files Modified
2. Files Created
3. Navigation Routes Added
4. Known Risks / Follow-ups for Phase 7
5. Verification: project compiles cleanly
```

---

## Phase 7 — FCM & Notification Routing

### Objective
Expand `AppEventBus` to carry all backend FCM event types and wire them to the correct navigation destinations in both foreground (MainActivity) and in-app (NotificationFragment taps).

### Inputs
- Phase 6 Completion Report (Phase 2 and Phase 3 destinations must exist)

---

### Phase 7 Prompt

```
You are a Senior Java Android Developer working on the WalkMate project.

## Setup
Run the ACKG index refresh:
  node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Context
[PASTE PHASE 6 COMPLETION REPORT HERE]

The AppEventBus and MainActivity only handle the MATCH_FOUND FCM event type. The backend dispatches
8 additional event types (PROPOSAL_RECEIVED, INVITE_SENT, PROPOSAL_ACCEPTED, SESSION_CONFIRMED,
SESSION_ACTIVE, FRIEND_REQUEST_RECEIVED, FRIEND_REQUEST_ACCEPTED, FRIEND_REQUEST_DECLINED).
NotificationFragment also lacks a tap dispatch table. Both must be fixed.

## ACKG Search Protocol — Run Before Coding
1. get_file_outline("frontend/src/main/java/com/walkmate/core/event/AppEventBus.java")
2. search_symbols("AppEvent") — find the event type enum or constants class
3. get_file_outline("frontend/src/main/java/com/walkmate/ui/main/MainActivity.java")
4. search_symbols("WalkMateFcmService") or search_symbols("FirebaseMessagingService") — find FCM handler
5. get_file_outline("frontend/src/main/java/com/walkmate/ui/notification/NotificationFragment.java")
6. get_file_outline("frontend/src/main/java/com/walkmate/ui/notification/NotificationViewModel.java")
7. search_symbols("TAB_PROPOSAL") and search_symbols("TAB_SESSION") — find existing tab index constants

## Task 7.1 — Expand AppEvent Type Constants
### Target file: core/event/AppEventBus.java (or the AppEvent type class/enum)

Add event type constants (as String constants or enum values, matching the existing pattern):
  MATCH_FOUND             (existing — do not change)
  PROPOSAL_RECEIVED       (new) — expected FCM data key: "proposalId"
  INVITE_SENT             (new) — expected FCM data key: "proposalId"
  PROPOSAL_ACCEPTED       (new) — expected FCM data key: "proposalId"
  SESSION_CONFIRMED       (new) — expected FCM data key: "sessionId"
  SESSION_ACTIVE          (new) — expected FCM data key: "sessionId"
  FRIEND_REQUEST_RECEIVED (new) — expected FCM data key: "requestId", "senderName"
  FRIEND_REQUEST_ACCEPTED (new) — expected FCM data key: "requestId"
  FRIEND_REQUEST_DECLINED (new) — expected FCM data key: "requestId"

If AppEvent carries a payload, ensure its payload field is Map<String, String>.
If it does not, add one.

## Task 7.2 — Update FCM Message Handler
### Target file: WalkMateFcmService.java (or the FirebaseMessagingService subclass)

In onMessageReceived():
  1. Read String type = remoteMessage.getData().get("type");
  2. Build Map<String, String> payload = new HashMap<>(remoteMessage.getData());
  3. Map type to the corresponding AppEvent constant and post:
       AppEventBus.get().post(new AppEvent(type, payload));
  4. Handle unknown types gracefully (log and skip).

For background (app not in foreground):
  When building the system-tray notification PendingIntent, include the type and relevant ID
  (proposalId or sessionId or requestId) as Intent extras on the launcher Intent, so
  MainActivity.onCreate() can route the deep-link.

In MainActivity.onCreate(): check getIntent() for an FCM type extra and route it through
the same switch table as the foreground AppEventBus handler (Task 7.3).

## Task 7.3 — Expand MainActivity.observeAppEventBus()
### Target file: ui/main/MainActivity.java — observeAppEventBus() method

Replace the current single-case handler with a full switch on event.type:

  MATCH_FOUND / PROPOSAL_RECEIVED / INVITE_SENT / PROPOSAL_ACCEPTED:
    → Navigate to Matches destination, then scroll to Proposal sub-tab.
      Pass proposalId via Bundle if available.

  SESSION_CONFIRMED / SESSION_ACTIVE:
    → Navigate to Matches destination, then scroll to Session sub-tab.
      Pass sessionId via Bundle if available.

  FRIEND_REQUEST_RECEIVED:
    → Navigate to FriendsFragment with argument incomingTab = true
      (so FriendsFragment pre-selects the Incoming Requests tab).

  FRIEND_REQUEST_ACCEPTED:
    → Navigate to FriendsFragment with argument friendsTab = true.

  FRIEND_REQUEST_DECLINED:
    → Navigate to FriendsFragment with argument outgoingTab = true.

Always call AppEventBus.get().consumeEvent() after handling in ALL cases.

## Task 7.4 — Notification Tap Deep-Link Dispatch
### Target file: ui/notification/NotificationFragment.java

Add a private navigateFromNotification(Notification notification) helper method.

In the RecyclerView item click handler (currently calls viewModel.markRead()):
  1. Call viewModel.markRead(notification.id) as before.
  2. In the markRead() success path (observe a markReadSuccess event or use a callback):
     call navigateFromNotification(notification).

navigateFromNotification(Notification n) switch on n.type:
  "PROPOSAL_RECEIVED" / "INVITE_SENT" / "PROPOSAL_ACCEPTED":
    → NavController.navigate(R.id.matchesFragment); then scroll to Proposal tab.
  "SESSION_CONFIRMED" / "SESSION_ACTIVE":
    → NavController.navigate(R.id.matchesFragment); then scroll to Session tab.
  "FRIEND_REQUEST_RECEIVED":
    → NavController.navigate(R.id.friendsFragment) with incomingTab = true.
  "FRIEND_REQUEST_ACCEPTED":
    → NavController.navigate(R.id.friendsFragment) with friendsTab = true.
  "FRIEND_REQUEST_DECLINED":
    → NavController.navigate(R.id.friendsFragment) with outgoingTab = true.

Add markReadSuccess LiveData to NotificationViewModel if not already present, or use
the existing post-success reload callback to trigger navigation.

## Output
- Implement all tasks.
- Ensure all navigation destinations created in Phases 2 and 3 exist before referencing them.
- Ensure the project compiles without errors.

## End of Phase Report
Conclude with a Phase 7 Completion Report containing:
1. Files Modified
2. Files Created
3. FCM Event Types Now Handled (full list)
4. Known Risks / Follow-ups for Phase 8
5. Verification: project compiles cleanly
```

---

## Phase 8 — Gamification & Discovery Polish

### Objective
Build the standalone Leaderboard screen, add intent expiry countdown timers, and implement hotspot pin visual weight.

### Inputs
- Phase 7 Completion Report

---

### Phase 8 Prompt

```
You are a Senior Java Android Developer working on the WalkMate project.

## Setup
Run the ACKG index refresh:
  node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Context
[PASTE PHASE 7 COMPLETION REPORT HERE]

Three remaining gaps need to be closed:
1. The Leaderboard is only visible inside PostSessionSummaryFragment. It must be a standalone
   navigable screen.
2. OPEN intent cards in FindingFragment have no expiry countdown.
3. Hotspot pins do not reflect openIntentCount visually.

## ACKG Search Protocol — Run Before Coding
1. get_file_outline("frontend/src/main/java/com/walkmate/domain/gamification/GamificationRepository.java")
2. get_file_outline("frontend/src/main/java/com/walkmate/data/repository/GamificationRepositoryImpl.java")
3. get_file_outline("frontend/src/main/java/com/walkmate/ui/gamification/PostSessionSummaryViewModel.java")
4. get_file_outline("frontend/src/main/java/com/walkmate/domain/gamification/LeaderboardEntry.java")
5. get_file_outline("frontend/src/main/java/com/walkmate/ui/home/HomeFragment.java")
6. get_file_outline("frontend/src/main/java/com/walkmate/ui/profile/ProfileFragment.java")
7. get_file_outline("frontend/src/main/java/com/walkmate/ui/matches/finding/FindingFragment.java")
8. get_file_outline("frontend/src/main/java/com/walkmate/ui/explore/ExploreFragment.java") — find marker rendering
9. search_symbols("openIntentCount") — confirm the field name in Hotspot domain model
10. search_symbols("AvatarInitialView") — confirm API for the leaderboard adapter

## Task 8.1 — Standalone Leaderboard Screen (GAP-5)
### New files:
- ui/gamification/leaderboard/LeaderboardFragment.java
- ui/gamification/leaderboard/LeaderboardViewModel.java
- ui/gamification/leaderboard/LeaderboardViewModelFactory.java
- ui/gamification/leaderboard/LeaderboardUiState.java
- ui/gamification/leaderboard/LeaderboardAdapter.java
- res/layout/fragment_leaderboard.xml
- res/layout/item_leaderboard_row.xml

LeaderboardUiState fields: boolean isLoading, String error, List<LeaderboardEntry> entries

LeaderboardViewModel:
  Constructor dependency: GamificationRepository, UserRepository (to identify own userId for row highlight)
  Methods:
    loadLeaderboard() — calls GamificationRepository.getLeaderboard(), posts result.
    String getMyUserId() — returns stored user ID from UserRepository.

LeaderboardAdapter:
  onBindViewHolder: render rank, AvatarInitialView (wm_avatarName = entry.fullName),
  name, totalPoints, totalDistanceKm, completedSessions.
  Highlight current user's row: if entry.userId.equals(myUserId), apply a distinct
  background tint (e.g., colorSurface variant or a light orange tint matching app theme).
  onItemClicked: navigate to PublicProfileFragment with entry.userId.

LeaderboardFragment:
  loadLeaderboard() in onViewCreated().
  Pull-to-refresh (SwipeRefreshLayout wrapping RecyclerView) triggers loadLeaderboard().
  Network failure: show "Last updated at [timestamp]" banner with cached data if available.
  Empty state: "No leaderboard data yet."

LeaderboardViewModelFactory: manual DI from WalkMateApplication singletons.

Navigation entry points:
  - HomeFragment: add a "View Leaderboard" button or menu item → navigate to LeaderboardFragment.
  - ProfileFragment: add a "Leaderboard" row in the stats section → navigate to LeaderboardFragment.
  Add destinations and actions in nav_graph.xml.

## Task 8.2 — Intent Expiry Countdown in FindingFragment (GAP-17)
### Target file: ui/matches/finding/FindingFragment.java
### Target: the RecyclerView adapter for intent cards (create IntentCardAdapter.java if not yet extracted)

In the adapter's onBindViewHolder():
  1. Compute millisUntilExpiry = intent.expiresAtMs - System.currentTimeMillis().
  2. If millisUntilExpiry <= 0: show "Expired" label; hide Cancel button.
  3. If millisUntilExpiry > 0: start a CountDownTimer(millisUntilExpiry, 1000):
       onTick(ms): update a TextView showing "Expires in H:MM:SS".
       onFinish(): call parentViewModel.loadAll() to refresh the list.
  4. In onViewRecycled(): cancel the CountDownTimer for that view holder to prevent leaks.
  5. FindingFragment.onDestroyView(): call adapter.cancelAllTimers() — add this method to the adapter.

## Task 8.3 — Hotspot Pin Visual Weight (GAP-20)
### Target file: ui/explore/ExploreFragment.java — find the method that adds hotspot markers to the map

In the marker-building loop (where MarkerOptions is constructed per hotspot):
  1. Read int count = hotspot.getOpenIntentCount() (verify field name with ACKG search).
  2. Determine scale tier:
       float scale = count == 0 ? 1.0f : (count <= 4 ? 1.3f : 1.6f);
  3. Create a scaled BitmapDescriptor:
       BitmapDescriptor icon = BitmapDescriptorFactory.fromBitmap(
           scaleBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_hotspot_pin), scale));
  4. Apply: markerOptions.icon(icon);
  5. Add a scaleBitmap(Bitmap src, float scale) private helper to ExploreFragment that uses
     Bitmap.createScaledBitmap(src, (int)(src.getWidth()*scale), (int)(src.getHeight()*scale), true).
  6. Re-apply on every loadHotspots() refresh (clear and rebuild all markers).

## Output
- Implement all tasks.
- Ensure LeaderboardFragment is fully self-contained and does not depend on PostSessionSummaryFragment.
- Ensure the project compiles without errors.

## Final Phase Completion Report
Conclude with a complete Phase 8 Completion Report containing:
1. Files Modified
2. Files Created
3. Navigation Routes Added
4. Full list of all 21 gaps from the approved gap analysis — mark each RESOLVED or PARTIAL with notes
5. Verification: project compiles cleanly
6. Recommended follow-up items (if any PARTIAL gaps remain)
```

---

## Playbook Summary

| Phase | Gaps Closed | Key Deliverable |
|---|---|---|
| 0 | GAP-6, GAP-21 | Clean foundation; no banned UI actions |
| 1 | GAP-7, GAP-2 | Friend-request domain layer replaces follow/follower model |
| 2 | GAP-1 | PublicProfileFragment with contextual friendship actions |
| 3 | GAP-3, GAP-4 | FriendsFragment (tabbed) + BlockedUsersFragment |
| 4 | GAP-8, GAP-10 | Private intent UI + hotspot auth gate |
| 5 | GAP-11, GAP-12, GAP-17 (proposals) | Correct proposal behavior + expiry timers |
| 6 | GAP-13, GAP-14, GAP-15, GAP-18, GAP-19 | Session invariants enforced + Chat button + celebrations |
| 7 | GAP-9, GAP-16 | Full FCM routing + notification deep-links |
| 8 | GAP-5, GAP-17 (intents), GAP-20 | Standalone Leaderboard + intent timers + hotspot visual weight |
