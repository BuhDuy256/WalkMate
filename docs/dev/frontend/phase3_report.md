# Phase 3 — Friends & Social Management Screens: Completion Report

**Branch:** `feature/phase-2-rework`
**Date:** 2026-04-14
**Addresses:** GAP-3, GAP-4

---

## 1. Files Created

### Java — Friends screen

| File | Purpose |
|------|---------|
| `ui/social/friends/FriendsUiState.java` | Immutable state: isLoading, error, friends, incomingRequests, outgoingRequests, incomingBadgeCount |
| `ui/social/friends/FriendsViewModel.java` | 3-way parallel load with AtomicInteger barrier; acceptRequest, declineRequest, removeFriend, navigateToInviteWalk |
| `ui/social/friends/FriendsViewModelFactory.java` | Manual DI factory — injects SocialRepository from WalkMateApplication |
| `ui/social/friends/FriendsPagerAdapter.java` | FragmentStateAdapter for ViewPager2 (Friends / Incoming / Sent tabs) |
| `ui/social/friends/FriendsFragment.java` | ViewPager2 container; scopes FriendsViewModel; handles tab badge and inviteWalkEvent |
| `ui/social/friends/FriendListFragment.java` | Friends tab — RecyclerView + Invite Walk / View Profile / Remove Friend actions |
| `ui/social/friends/IncomingRequestsFragment.java` | Incoming tab — Accept / Decline buttons |
| `ui/social/friends/OutgoingRequestsFragment.java` | Sent tab — status label only, no action buttons |
| `ui/social/friends/FriendsAdapter.java` | ListAdapter for UserSummary; three-button action listener |
| `ui/social/friends/FriendRequestsAdapter.java` | ListAdapter for FriendRequest; `showActions` flag toggles incoming vs. outgoing mode |

### Java — Blocked Users screen

| File | Purpose |
|------|---------|
| `ui/social/blocked/BlockedUsersUiState.java` | Immutable state: isLoading, error, blockedUsers |
| `ui/social/blocked/BlockedUsersViewModel.java` | loadBlocked(), unblock() with optimistic list removal + unblockSuccessEvent |
| `ui/social/blocked/BlockedUsersViewModelFactory.java` | Manual DI factory — injects SocialRepository |
| `ui/social/blocked/BlockedUsersFragment.java` | Full-screen list with ProgressBar, empty state, unblock toast |
| `ui/social/blocked/BlockedUsersAdapter.java` | ListAdapter for UserSummary with "Unblock" button |

### Layouts

| File | Purpose |
|------|---------|
| `res/layout/fragment_friends.xml` | Top bar + TabLayout + ViewPager2 |
| `res/layout/fragment_friend_list.xml` | RecyclerView + empty state ("You have no friends yet.") |
| `res/layout/fragment_incoming_requests.xml` | RecyclerView + empty state ("No incoming friend requests.") |
| `res/layout/fragment_outgoing_requests.xml` | RecyclerView + empty state ("No sent requests.") |
| `res/layout/item_friend_card.xml` | AvatarInitialView + name + Invite Walk (FILLED) / View Profile (OUTLINED) / Remove (OUTLINED) |
| `res/layout/item_friend_request_card.xml` | AvatarInitialView + name + Accept (FILLED) / Decline (OUTLINED) / Pending label (outgoing mode) |
| `res/layout/fragment_blocked_users.xml` | Top bar + ProgressBar + RecyclerView + empty state |
| `res/layout/item_blocked_user_card.xml` | AvatarInitialView + name + Unblock (OUTLINED) |

---

## 2. Files Modified

| File | Change |
|------|--------|
| `res/navigation/nav_graph.xml` | Added `friendsFragment` destination with `action_friendsFragment_to_publicProfileFragment`; added `blockedUsersFragment` destination; added `action_profile_to_friendsFragment` and `action_profile_to_blockedUsersFragment` on `profileFragment` |
| `res/layout/fragment_profile.xml` | Added "Friends" menu row (Row 4) and "Blocked Users" menu row (Row 5) to `cardMenu` with row dividers |
| `ui/profile/ProfileViewModel.java` | Added `navigateToFriendsEvent` + `onFriendsClicked()` + `consumeNavigateToFriends()`; added `navigateToBlockedUsersEvent` + `onBlockedUsersClicked()` + `consumeNavigateToBlockedUsers()` |
| `ui/profile/ProfileFragment.java` | Bound `menuFriends` and `menuBlockedUsers` views; wired click listeners to ViewModel; added observers for both new navigation events → NavController navigate |

---

## 3. Navigation Routes Added

| Source | Action ID | Destination |
|--------|-----------|-------------|
| `profileFragment` | `action_profile_to_friendsFragment` | `friendsFragment` |
| `profileFragment` | `action_profile_to_blockedUsersFragment` | `blockedUsersFragment` |
| `friendsFragment` | `action_friendsFragment_to_publicProfileFragment` | `publicProfileFragment` |

Back-stack after Friends → View Profile:
```
profileFragment → friendsFragment → publicProfileFragment
```

Back-stack after Profile → Blocked Users:
```
profileFragment → blockedUsersFragment
```

---

## 4. Known Risks / Follow-ups for Phase 4

### 4.1 Invite Walk deep-link (medium risk)
`FriendsFragment.inviteWalkEvent` handler shows a "coming soon" toast. Phase 5 will navigate to `ExploreFragment` with `friendId` pre-filled once `CreateIntentViewModel` supports the `invitedFriendId` field (Task 4.2 in the implementation plan).

### 4.2 OutgoingRequestsFragment receiver name
`FriendRequestsAdapter` reads `getSenderName()` for both incoming and outgoing tabs. For outgoing requests, the relevant party is the receiver, not the sender. If `FriendRequestResponse` from the backend carries a `receiverName` field, `FriendRequest` domain model will need a `receiverName` getter and `OutgoingRequestsFragment` will need a dedicated adapter configuration. Deferred to Phase 4 when the DTO is confirmed.

### 4.3 FriendListFragment: "View Profile" navigates via action on friendsFragment
The nav action `action_friendsFragment_to_publicProfileFragment` is defined on the `friendsFragment` NavDestination. `FriendListFragment` is a child fragment inside ViewPager2, so its `NavHostFragment.findNavController(this)` traverses up to the main NavHost. This mirrors the pattern used by `ProposalFragment` (child of `MatchesFragment`) calling `action_matches_to_publicProfileFragment` and is expected to compile and run correctly. Verify with a device test.

### 4.4 Carry-overs from Phase 2 (unchanged)
See `phase2_report.md` sections 4.1 – 4.4 for deferred items that remain open.

---

## 5. Verification

- **Package convention:** `com.walkmate.ui.social.friends` and `com.walkmate.ui.social.blocked` (avoids `public` reserved keyword conflict used in earlier phase).
- **DomainCallback import:** All new ViewModels use `com.walkmate.domain.shared.DomainCallback` — the canonical single source at that path. Note: `PublicProfileViewModel` from Phase 2 imports `com.walkmate.core.domain.DomainCallback` which does not exist as a file; this is a pre-existing deviation not introduced here.
- **ViewModel scoping:** FriendsViewModel is scoped to `FriendsFragment` (not Activity), consistent with it being a NavDestination that can be popped. Sub-fragments access it via `ViewModelProvider(requireParentFragment())`.
- **Drawable references:** All drawables referenced in new layouts (`ic_back`, `ic_badge_social`, `ic_more_vert`, `ic_chevron_right`) confirmed to exist in `res/drawable/`.
- **No data/domain layer modifications:** All new code is UI-layer only; `SocialRepository` and `FriendRequest`/`UserSummary` domain models were already provided by Phase 1 and are used as-is.
- **Backward-compatible changes:** `ProfileFragment` additions are additive only — existing behaviour of Walk History, My Badges, Settings, Visibility, and Logout All rows is unmodified.
