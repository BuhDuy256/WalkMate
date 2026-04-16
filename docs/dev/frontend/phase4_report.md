# Phase 4 — Implementation Report
**Date:** 2026-04-14
**Branch:** feature/phase-2-rework
**Build status:** `BUILD SUCCESSFUL` — zero compile errors

---

## Overview

Phase 4 resolved three pieces of technical debt from earlier phases and added two new feature areas: private walk invites and an authentication gate on hotspot selection.

---

## TASK 4.0.1 — Fix PostSessionSummaryFragment Partner ID

**Problem:** `WalkSession` had no `partnerId` field. The mapper was placing the backend user ID into the `partnerName` slot as a placeholder. `TrackingScreenActivity` never forwarded a partner ID to `PostSessionSummaryFragment`, so the 4-arg `newInstance` already in the fragment was unreachable.

**Files changed:**

| File | Change |
|------|--------|
| `domain/walksession/WalkSession.java` | Added `String partnerId` field and getter; constructor extended from 15 → 16 parameters |
| `data/mapper/WalkSessionMapper.java` | `partnerId` now maps to the new dedicated field; `partnerName` stays `null` until the API provides it |
| `data/mapper/WalkProposalMapper.java` | Fixed `toSession()` call site to pass `matchedUserId` as `partnerId` (16-arg constructor) |
| `service/WalkTrackerService.java` | Added `EXTRA_PARTNER_ID = "PARTNER_ID"` constant |
| `ui/tracking/TrackingScreenActivity.java` | Re-exports `EXTRA_PARTNER_ID`; added `partnerId` field; reads it in `readIntentExtras()`; passes it to 4-arg `PostSessionSummaryFragment.newInstance()` |
| `ui/tracking/TrackingViewModel.java` | Added `partnerId` field; updated `startTrackingSession()` signature (5 params); passes `EXTRA_PARTNER_ID` in `buildServiceIntent()` |
| `ui/matches/session/SessionFragment.java` | Passes `EXTRA_PARTNER_ID` from `result.session.getPartnerId()` when launching `TrackingScreenActivity` |

---

## TASK 4.0.2 — Fix UserSummary Missing Bio & Tags

**Problem:** `PublicProfileFragment.renderIdentity()` hardcoded `txtBio` and `chipGroupTags` to `GONE`. `UserSummary` had no `bio` or `tags` fields despite `PublicUserResponse` already carrying them.

**Files changed:**

| File | Change |
|------|--------|
| `domain/social/UserSummary.java` | Added `String bio` and `List<String> tags` fields; constructor extended from 4 → 7 parameters |
| `data/mapper/SocialMapper.java` | `toUserSummary(PublicUserResponse)` now maps `dto.bio` and `dto.tags`; `toDomain(UserSummaryResponse)` passes `null, null, null` for the new fields (lightweight DTO has none) |
| `ui/profile/publicprofile/PublicProfileFragment.java` | `renderIdentity()` shows `txtBio` and `chipGroupTags` when non-null/non-empty; builds tag chips programmatically |

---

## TASK 4.0.3 — Fix Accept/Decline pendingRequestId

**Problem:** `pendingRequestId` in `PublicProfileFragment` was always `null` because no code ever populated it. Accept/decline buttons were therefore no-ops.

**Files changed:**

| File | Change |
|------|--------|
| `data/datasource/remote/dto/response/social/PublicUserResponse.java` | Added `@SerializedName("pendingRequestId") String pendingRequestId` |
| `domain/social/UserSummary.java` | Added `String pendingRequestId` field and getter (same constructor extension as 4.0.2) |
| `data/mapper/SocialMapper.java` | `toUserSummary()` maps `dto.pendingRequestId` |
| `ui/profile/publicprofile/PublicProfileFragment.java` | `renderFriendshipActions()` signature extended to accept `UserSummary profile`; sets `this.pendingRequestId = profile.getPendingRequestId()` in the `PENDING_RECEIVED` branch |

**Pre-existing bug fixed:** `PublicProfileViewModel.java` had `import com.walkmate.core.domain.DomainCallback` (wrong package). Fixed to `com.walkmate.domain.shared.DomainCallback`.

---

## TASK 4.1 — Extend CreateIntentUiState

**File:** `ui/explore/createintent/CreateIntentUiState.java`

Rebuilt with 9 immutable fields and a full set of copy helpers:

| Field | Type | Purpose |
|-------|------|---------|
| `isPrivate` | `boolean` | Private walk toggle state |
| `invitedFriendId` | `String` | Selected friend's user ID |
| `invitedFriendName` | `String` | Selected friend's display name |
| `friendList` | `List<UserSummary>` | Friends available in the picker |
| `isFriendListLoading` | `boolean` | Loading indicator for friend list |
| `privateIntentError` | `String` | Field-level validation error for private config |

Copy helpers added: `withPrivate()`, `withFriend()`, `withFriendList()`, `withFriendListLoading()`, `withPrivateIntentError()`, `withLoading()`, `withError()`, `withSubmittedIntent()`.

`withPrivate(false)` clears `invitedFriendId` and `invitedFriendName` automatically.

---

## TASK 4.2 — Extend CreateIntentViewModel

**Files changed:**

| File | Change |
|------|--------|
| `ui/explore/createintent/CreateIntentViewModel.java` | Injected `SocialRepository`; added `togglePrivate()`, `selectFriend()`, `loadFriends()`; `submit()` guards private walk without a selected friend; `onError()` parses `VALIDATION_ERROR\|…` via `ValidationErrorParser` and surfaces the first field error as a human-readable message; added `consumePrivateIntentError()` |
| `ui/explore/createintent/CreateIntentViewModelFactory.java` | Injects `app.getSocialRepository()` alongside `WalkIntentRepository` |

**togglePrivate() behaviour:** Flips `isPrivate`; triggers `loadFriends()` on first enable when the list is empty.

**Error handling chain:**
1. Guard: private + no friend → `withPrivateIntentError(PRIVATE_INTENT_FRIEND_REQUIRED)` (no network call)
2. Network error prefixed `VALIDATION_ERROR|` → parsed by `ValidationErrorParser`; first error surfaced via `withError()`
3. All other errors → `withError(message)`

---

## TASK 4.3 — Create Intent Form UI — Private Walk

**New files:**

| File | Purpose |
|------|---------|
| `res/layout/bottom_sheet_friend_picker.xml` | Bottom sheet with drag handle, progress bar, empty state, RecyclerView |
| `ui/explore/createintent/FriendPickerBottomSheet.java` | `BottomSheetDialogFragment`; accepts friend list + loading state; fires `OnFriendSelectedListener` and auto-dismisses on selection |
| `ui/explore/createintent/FriendPickerAdapter.java` | Simple `RecyclerView.Adapter` using `item_friend_card.xml`; delegates clicks to `OnFriendClickListener` |

**Layout changes — `res/layout/fragment_explore.xml`:**
Inserted between chipGroupTags and btnFindMatch:
- `rowPrivateWalk` (`LinearLayout`): label + `SwitchCompat#switchPrivateWalk`
- `rowFriendPicker` (`LinearLayout`, `visibility="gone"`): label + `TextView#txtSelectedFriend` with chevron
- `TextView#txtPrivateIntentError` (`visibility="gone"`, `textColor="@color/color_danger"`)

**String resources added (`res/values/strings.xml`):**
`private_walk`, `invite_friend`, `select_friend`, `no_friends_yet`

**`ExploreFragment.java` changes:**
- Bound `switchPrivateWalk`, `rowFriendPicker`, `txtSelectedFriend`, `txtPrivateIntentError`
- `setupCreateIntentListeners()`: wires switch → `togglePrivate()`; friend row tap → `showFriendPicker()`
- `showFriendPicker()`: creates `FriendPickerBottomSheet`, populates from current state, sets listener → `selectFriend()`
- `submitCreateIntent()`: reads `isPrivate` and `invitedFriendId` from `CreateIntentUiState` instead of hardcoded `false/null`
- `renderCreateIntentState()`: syncs switch (listener-safe), toggles friend picker row visibility, updates selected friend label, shows/hides `privateIntentError`, updates open picker sheet if visible

---

## TASK 4.4 — Auth Gate on Hotspot Tap

**Problem:** `ExploreViewModel.selectHotspot()` transitioned directly to `SETUP` regardless of auth state. Unauthenticated users could open the create-intent form.

**Files changed:**

| File | Change |
|------|--------|
| `ui/explore/ExploreUiState.java` | Added `pendingHotspotId` field; updated private constructor (9 params); all `with*()` helpers thread it through; added `withPendingHotspot(String)` and `withPendingHotspotConsumed()` |
| `ui/explore/ExploreViewModel.java` | Injected `UserRepository`; `selectHotspot()` checks `userRepository.getAccessToken()` — if null/blank, posts `withPendingHotspot(hotspotId)` and returns early; added `consumePendingHotspot()` |
| `ui/explore/ExploreViewModelFactory.java` | Passes `app.getUserRepository()` to `ExploreViewModel` (4-arg constructor) |
| `ui/explore/ExploreFragment.java` | `renderState()` checks `getPendingHotspotId()` first; calls `consumePendingHotspot()` then `startActivity(new Intent(..., AuthActivity.class))`; returns early so no further rendering runs |

**Auth gate flow:**
1. Unauthenticated user taps a hotspot marker
2. `selectHotspot()` detects empty token → `withPendingHotspot(hotspotId)`
3. `renderState()` fires → consumes event → launches `AuthActivity`
4. After login, user returns to `ExploreFragment`; can tap the hotspot again (now authenticated → transitions to SETUP normally)

---

## Files Created

| Path | Purpose |
|------|---------|
| `res/layout/bottom_sheet_friend_picker.xml` | Friend picker bottom sheet layout |
| `ui/explore/createintent/FriendPickerBottomSheet.java` | Bottom sheet Fragment |
| `ui/explore/createintent/FriendPickerAdapter.java` | RecyclerView adapter for friend list |

## Files Modified (20 total)

`WalkSession.java`, `WalkSessionMapper.java`, `WalkProposalMapper.java`, `WalkTrackerService.java`, `TrackingScreenActivity.java`, `TrackingViewModel.java`, `SessionFragment.java`, `UserSummary.java`, `PublicUserResponse.java`, `SocialMapper.java`, `PublicProfileFragment.java`, `PublicProfileViewModel.java` (pre-existing bug fix), `CreateIntentUiState.java`, `CreateIntentViewModel.java`, `CreateIntentViewModelFactory.java`, `ExploreUiState.java`, `ExploreViewModel.java`, `ExploreViewModelFactory.java`, `ExploreFragment.java`, `fragment_explore.xml`, `strings.xml`
