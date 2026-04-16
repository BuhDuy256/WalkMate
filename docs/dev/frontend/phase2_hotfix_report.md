# Phase 2 Hotfix — Navigation & Auth Guard Fixes

**Branch:** `feature/phase-2-rework`
**Date:** 2026-04-14
**Fixes two critical deviations found during code review of Phase 2.**

---

## 1. Files Modified

| File | Change |
|------|--------|
| `frontend/src/main/res/navigation/nav_graph.xml` | Added `sessionHistoryFragment` as a NavDestination with `action_sessionHistory_to_publicProfileFragment`; added `action_profile_to_sessionHistoryFragment` inside `profileFragment` |
| `frontend/src/main/java/com/walkmate/ui/profile/ProfileFragment.java` | Replaced `FragmentManager.replace()` for SessionHistory with `NavHostFragment.findNavController(this).navigate(R.id.action_profile_to_sessionHistoryFragment)`; removed stale `SessionHistoryFragment` import |
| `frontend/src/main/java/com/walkmate/ui/history/SessionHistoryFragment.java` | Replaced `FragmentManager.beginTransaction().replace()` with `NavHostFragment.findNavController(this).navigate(R.id.action_sessionHistory_to_publicProfileFragment, args)`; added `NavHostFragment` import |
| `frontend/src/main/java/com/walkmate/ui/gamification/PostSessionSummaryFragment.java` | Replaced `FragmentManager.replace()` with `startActivity(Intent → MainActivity + EXTRA_NAVIGATE_USER_ID)` using `FLAG_ACTIVITY_CLEAR_TOP \| FLAG_ACTIVITY_SINGLE_TOP`; swapped `PublicProfileFragment` import for `MainActivity` |
| `frontend/src/main/java/com/walkmate/ui/main/MainActivity.java` | Added `EXTRA_NAVIGATE_USER_ID` public constant; overrode `onNewIntent`; added `handleNavigateIntent()` that reads the extra and calls `navController.navigate(R.id.publicProfileFragment, args)`, guarded by `savedInstanceState == null` in `onCreate` |
| `frontend/src/main/java/com/walkmate/ui/profile/publicprofile/PublicProfileFragment.java` | Added `if (!requiresAuth()) return true;` guard to the "Block User" overflow menu item — the only social action that was missing it; updated entry-points Javadoc |

---

## 2. Fix 1 — Standardize Navigation (FragmentManager → NavController)

### Root Cause

`SessionHistoryFragment` was not a NavDestination — it was placed into `R.id.fragment_container`, a manual overlay `FrameLayout` that sits **outside** the `NavHostFragment` in `activity_main.xml`. This meant:

- `NavHostFragment.findNavController(SessionHistoryFragment.this)` would have thrown at runtime.
- Pressing Back after visiting `PublicProfileFragment` (via the old `FragmentManager.replace()`) would corrupt the back-stack because two independent stacks — the NavController's and the FragmentManager's — were being mutated simultaneously.

`PostSessionSummaryFragment` lives inside `TrackingScreenActivity`, a completely separate `Activity` that has no `NavHostFragment` at all, so `findNavController()` is not available there.

### Fix Applied

**SessionHistoryFragment path:**

1. `sessionHistoryFragment` added to `nav_graph.xml` as a first-class NavDestination.
2. `action_profile_to_sessionHistoryFragment` added inside `profileFragment`.
3. `action_sessionHistory_to_publicProfileFragment` added inside `sessionHistoryFragment`.
4. `ProfileFragment` now uses `NavHostFragment.findNavController(this).navigate(R.id.action_profile_to_sessionHistoryFragment)` — this places `SessionHistoryFragment` inside the NavHost, giving it a clean back-stack entry and access to `findNavController()`.
5. `SessionHistoryFragment`'s partner-name tap chains forward via `NavHostFragment.findNavController(this).navigate(R.id.action_sessionHistory_to_publicProfileFragment, args)`.

Back-stack after the fix:
```
profileFragment → sessionHistoryFragment → publicProfileFragment
```
Back presses unwind cleanly through NavController.

**PostSessionSummaryFragment path:**

`TrackingScreenActivity` has no NavHostFragment. Introducing one just for this tap would be disproportionate. Instead, the partner-name tap launches `MainActivity` with a typed Intent extra:

```java
Intent intent = new Intent(requireContext(), MainActivity.class);
intent.putExtra(MainActivity.EXTRA_NAVIGATE_USER_ID, resolvedPartnerId);
intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
startActivity(intent);
```

- `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP` brings the already-running `MainActivity` to the front (rather than creating a new instance) and delivers the intent via `onNewIntent`.
- `MainActivity.handleNavigateIntent()` reads `EXTRA_NAVIGATE_USER_ID`, immediately removes the extra (so rotation does not re-trigger navigation), then calls `navController.navigate(R.id.publicProfileFragment, args)`.
- No `FragmentManager` is used at any point.

---

## 3. Fix 2 — Unauthenticated Guard on Block User

### Root Cause

`requiresAuth()` was correctly wired to all five friendship action buttons:
`btnAddFriend`, `btnAcceptRequest`, `btnDeclineRequest`, `btnRemoveFriend`, `btnInviteWalk`.

However, the **"Block User"** item in `showOverflowMenu()` called `viewModel.blockUser(currentUserId)` directly, with no auth check. A guest user could trigger a block API call.

### Fix Applied

```java
popup.setOnMenuItemClickListener(item -> {
    if (item.getItemId() == MENU_BLOCK_USER) {
        if (!requiresAuth()) return true;   // ← added guard
        viewModel.blockUser(currentUserId);
        return true;
    }
    return false;
});
```

### How the Auth Guard Works

`requiresAuth()` delegates to `SessionManager.hasUsableAccessToken()`, which returns `true` only when a non-blank, non-expired JWT is present in `EncryptedSharedPreferences`. If the token is absent:

1. Toast displayed: `"Log in to manage friendships."`
2. `AuthActivity` launched via `startActivity(Intent)`.
3. The calling listener returns early — no ViewModel API method is invoked.

All six social actions (`addFriend`, `acceptRequest`, `declineRequest`, `removeFriend`, `inviteWalk`, `blockUser`) are now uniformly guarded.

---

## 4. Known Follow-ups Carried Over to Phase 3

These were documented in `phase2_report.md` and are **unchanged** by this hotfix:

| # | Issue | Status |
|---|-------|--------|
| 4.1 | `PostSessionSummaryFragment` old 3-arg `newInstance()` does not pass `partnerId` → partner tap inactive for sessions opened from `TrackingScreenActivity.showPostSessionSummary()` | Deferred — requires `partnerId` on `WalkSession` domain model |
| 4.2 | `UserSummary` missing `bio` and `tags` — sections hidden (`GONE`) in `PublicProfileFragment` | Deferred to Phase 3 API extension |
| 4.3 | `pendingRequestId` not carried in `PublicProfileUiState` — Accept/Decline are no-ops | Deferred to Phase 3 `FriendsViewModel` |
| 4.4 | "Invite Walk" button shows "coming soon" toast | Deferred to Phase 5 |
