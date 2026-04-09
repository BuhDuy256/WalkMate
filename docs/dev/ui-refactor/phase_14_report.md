# Phase 14 Report — DI & Factory Updates + Phase 13 Fixes
**Date:** 2026-04-09
**Branch:** `implement/realtime`
**Scope:** DI audit across all factories; two Phase 13 regression fixes (UX + enrichment).

---

## Phase 13 Regression Fixes

### Fix 1 — UX Flaw: Dead Tracking Screen on Back Press (FIXED)

**Problem:** `PostSessionSummaryFragment` was added over `android.R.id.content`. Pressing back popped the Fragment and left the user on the finished, dead `TrackingScreenActivity`.

**Fix in `PostSessionSummaryFragment.onViewCreated()`:**
```java
requireActivity().getOnBackPressedDispatcher().addCallback(
    getViewLifecycleOwner(),
    new OnBackPressedCallback(true) {
        @Override public void handleOnBackPressed() {
            requireActivity().finish();
        }
    });
```

A `btnDone` button (null-checked, wired to `requireActivity().finish()`) was also added as an explicit exit affordance.

**Behavior after fix:**
- System back from `PostSessionSummaryFragment` → `TrackingScreenActivity.finish()` → Home/History
- System back from `SubmitReviewFragment` or `ReportIncidentFragment` → pops back to `PostSessionSummaryFragment` (expected) → back again → `finish()`

---

### Fix 2 — Partner Name Enrichment in Session History (FIXED)

**Problem:** `SessionHistoryAdapter` showed raw `partnerId` strings as placeholder text.

**Pattern:** Follows `MatchesViewModel` Phase 8 partner enrichment (cache + parallel `getProfile()` calls).

**Changes:**

`SessionHistoryUiState` — added `Map<String, String> partnerNames` field + `withPartnerNames()` copy-mutator.

`SessionHistoryViewModel` — added `UserProfileRepository profileRepo` + `Map<String, String> profileCache`:
```java
private void enrichPartnerNames(List<SessionSummary> sessions) {
    for (SessionSummary session : sessions) {
        String partnerId = session.getPartnerId();
        if (profileCache.containsKey(partnerId)) { rebuildWithCache(); continue; }
        profileRepo.getProfile(partnerId, new DomainCallback<UserProfile>() {
            @Override public void onSuccess(UserProfile profile) {
                profileCache.put(partnerId, profile.getFullName());
                rebuildWithCache(); // re-posts READY state with updated names map
            }
            @Override public void onError(Exception e) { /* non-fatal */ }
        });
    }
}
```

`SessionHistoryAdapter` — added `Map<String, String> partnerNames` field + `setPartnerNames()`:
```java
String displayName = partnerNames.containsKey(partnerId)
    ? partnerNames.get(partnerId)
    : "Partner: " + partnerId;
```

`SessionHistoryViewModelFactory` — added `UserProfileRepository profileRepo` second parameter.

`SessionHistoryFragment.renderState(READY)` — calls `adapter.setPartnerNames(state.partnerNames)` before `submitList()`.

---

## DI Audit — All Factories

### Pre-existing factories (verified, no changes needed)

| Factory | ViewModel | Constructor params | Status |
|---|---|---|---|
| `HomeViewModelFactory` | `HomeViewModel` | 7 repos (session, user, profile, notification, hotspot, gamification, social) | ✅ Already correct |
| `HomeFragment.setupViewModel()` | — | Passes all 7 via `app.get*Repository()` | ✅ |
| `MatchesViewModelFactory` | `MatchesViewModel` | 4 repos (intent, proposal, session, userProfile) — via `app` | ✅ Already correct |
| `MatchesFragment` | — | Passes `requireActivity().getApplication()` to factory | ✅ |
| `TrackingViewModelFactory` | `TrackingViewModel` | `(Application, WalkSessionRepository)` | ✅ Already correct |
| `TrackingScreenActivity` | — | Passes `getApplication()` to factory | ✅ |
| `ProfileViewModelFactory` | `ProfileViewModel` | `(UserProfileRepository, GamificationRepository, ReviewRepository)` | ✅ Already correct |
| `ProfileFragment.setupViewModel()` | — | Passes all 3 via `app.get*Repository()` | ✅ |
| `NotificationViewModelFactory` | `NotificationViewModel` | `(NotificationRepository)` | ✅ Already correct |

### Phase 12–13 factories (verified/updated)

| Factory | Change |
|---|---|
| `EditProfileViewModelFactory` | `(UserProfileRepository)` — correct as written in Phase 12 ✅ |
| `PostSessionSummaryViewModelFactory` | `(GamificationRepository, WalkSessionRepository)` — correct as written in Phase 13 ✅ |
| `ReviewViewModelFactory` | `(ReviewRepository, WalkSessionRepository)` — correct as written in Phase 13 ✅ |
| `RouteReplayViewModelFactory` | `(WalkSessionRepository)` — correct as written in Phase 13 ✅ |
| `ReportIncidentViewModelFactory` | `(WalkSessionRepository)` — correct as written in Phase 13 ✅ |
| `SessionHistoryViewModelFactory` | **Updated**: added `UserProfileRepository` as second param (Phase 14 fix) |

---

## WalkMateApplication — Getter Audit

All needed getters were already present from Phase 11. No new getters required.

| Getter | Used by |
|---|---|
| `getWalkSessionRepository()` | TrackingViewModelFactory, MatchesViewModelFactory, SessionHistoryViewModelFactory, RouteReplayViewModelFactory, ReportIncidentViewModelFactory, ReviewViewModelFactory, PostSessionSummaryViewModelFactory |
| `getUserProfileRepository()` | MatchesViewModelFactory, HomeViewModelFactory, ProfileViewModelFactory, EditProfileViewModelFactory, SessionHistoryViewModelFactory |
| `getGamificationRepository()` | HomeViewModelFactory, ProfileViewModelFactory, PostSessionSummaryViewModelFactory |
| `getReviewRepository()` | ProfileViewModelFactory, ReviewViewModelFactory |
| `getSocialRepository()` | HomeViewModelFactory |
| `getHotspotRepository()` | HomeViewModelFactory |
| `getNotificationRepository()` | NotificationFragment (directly) |
| `getWalkIntentRepository()` | MatchesViewModelFactory |
| `getWalkProposalRepository()` | MatchesViewModelFactory |
| `getUserRepository()` | HomeViewModelFactory |

---

## Files Modified in Phase 14

| File | Change |
|---|---|
| `ui/gamification/PostSessionSummaryFragment.java` | Added `OnBackPressedCallback` → `finish()`; added `btnDone` → `finish()` |
| `ui/history/SessionHistoryUiState.java` | Added `partnerNames: Map<String, String>` + `withPartnerNames()` copy-mutator |
| `ui/history/SessionHistoryViewModel.java` | Added `UserProfileRepository` + `enrichPartnerNames()` + `rebuildWithCache()` + profile cache |
| `ui/history/SessionHistoryViewModelFactory.java` | Added `UserProfileRepository profileRepo` second constructor param |
| `ui/history/SessionHistoryAdapter.java` | Added `setPartnerNames()` + uses map in `bind()` for enriched display names |
| `ui/history/SessionHistoryFragment.java` | Updated factory call to pass `getUserProfileRepository()`; calls `adapter.setPartnerNames()` in `renderState()` |
