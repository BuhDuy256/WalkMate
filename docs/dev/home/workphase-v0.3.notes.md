# Workphase Context Handoff — v0.3

**Current Version:** v0.3  
**Phase Completed:** Phase C — Profile Rebuild  
**Date:** 2026-03-31  
**Branch:** `improve/coordination-flow`

---

## Files Modified / Created

### Modified
| File | Change |
|---|---|
| `frontend/src/main/res/layout/fragment_profile.xml` | Discarded `FrameLayout` stub. Full rebuild: `NestedScrollView` root → Top App Bar → Identity → Milestones → Menu List. 88dp bottom padding. |
| `frontend/src/main/java/com/walkmate/ui/profile/ProfileFragment.java` | Rebuilt from Coming Soon stub. Full MVVM wiring, `renderState()`, tag chip slots, badge slot binding. |

### Created
| File | Notes |
|---|---|
| `ui/profile/ProfileUiState.java` | Immutable. `Badge` inner class with `labelStringResId` + `iconDrawableResId`. Static `loading()` factory. |
| `ui/profile/ProfileViewModel.java` | Executor-based mock load with 600ms simulated delay. Badge list mapped to R.drawable/R.string constants. `onCleared()` shuts down executor. Navigation stubs for Phase D. |
| `ui/profile/ProfileViewModelFactory.java` | Standard factory; receives `UserRepository` from `WalkMateApplication`. |
| `docs/dev/home/optimization_decision_log.md` | 6-entry decision log covering: stats row flattening, deliberate weight exception, tools: namespace, resource-ID badge pattern, static tag slot binding, return-early loading guard. |

---

## Architectural Constraint Verification

| Constraint | Status | Detail |
|---|---|---|
| 3-column stats row is flat `ConstraintLayout` | ✅ | 2 `Guideline`s at 33.3%/66.7%, 3 vertical packed chains. Zero nesting. |
| No `LinearLayout` + `layout_weight` in stats row | ✅ | Explicitly avoided. Badge row uses weight (documented exception — static 3-slot, single measure). |
| All dynamic views use `tools:text`/`tools:src` | ✅ | `txtProfileName`, `chipTrustScore`, stat values, badge images, avatar |
| All static labels use `android:text="@string/"` | ✅ | Screen title, stat labels, menu row labels, chip labels |
| Zero hardcoded string literals in XML | ✅ | Every android:text is a @string/ reference |
| `ProfileUiState.Badge` uses resource IDs not Strings | ✅ | `labelStringResId: int`, `iconDrawableResId: int` |
| `DomainCallback.onError(Exception)` signature | ✅ | Not used (mock path has no error path in executor.execute), but constructor is correct |
| ViewModel holds zero Context references | ✅ | R.* IDs are compile-time int constants, not Context-dependent |
| `onCleared()` shuts down ExecutorService | ✅ | Prevents thread leaks across ViewModel lifecycle |

---

## Known Mock Data in ProfileViewModel (Replace When APIs Are Ready)

| Field | Mock Value | Replace With |
|---|---|---|
| `name` | `"Nguyễn Bảo Duy"` | `userRepo.getMyProfile().fullname` |
| `avatarUrl` | `null` (ic_user placeholder shown) | `userRepo.getMyProfile().avatarUrl` |
| `isOnline` | `true` | Presence/WebSocket service |
| `trustScore` | `4.9f` | Rating service |
| `personalityTags` | `["Chatty", "Dog Friendly"]` | `userRepo.getMyProfile().tags` |
| `totalDistanceKm` | `248.0` | Stats repo |
| `totalSessions` | `32` | Stats repo |
| `currentStreak` | `5` | Stats repo |
| `badges` | All 3 hardcoded | Badge repo → mapped to R.drawable/R.string |

---

## What Was NOT Done (Deferred to Phase D)

- `HomeFragment.btnFindWalkMate` → navigate to `ExploreFragment`
- `ProfileViewModel.onWalkHistoryClicked()` / `onMyBadgesClicked()` / `onSettingsClicked()` — navigation stubs are present but not wired to destinations
- `UserRepository.getMyProfile()` — interface does not yet have this method; all profile data is mocked
- `SingleLiveEvent<NavigationDestination>` pattern for ViewModel-driven navigation (Phase D)

---

## Phase D Checklist (Next Immediate Step)

Phase D finalizes all cross-screen navigation and removes remaining stubs.

### D1. `btnFindWalkMate` in `HomeFragment` → `ExploreFragment`

Currently `viewModel.onFindWalkMateClicked()` is a no-op. Phase D should:
1. Add `private final MutableLiveData<Void> navigateToExplore = new MutableLiveData<>()` to `HomeViewModel`
2. `onFindWalkMateClicked()` calls `navigateToExplore.postValue(null)`
3. `HomeFragment` observes it and calls `((MainActivity) requireActivity()).switchToExploreTab()`
4. Add `switchToExploreTab()` to `MainActivity` that calls `showTab(ExploreFragment.TAG)`

### D2. Profile menu row navigation stubs → real destinations

Currently `onWalkHistoryClicked()` etc. are no-ops. Connect via `SingleLiveEvent<NavDestination>` or a sealed class navigation command observed by `ProfileFragment`.

### D3. `UserRepository` interface extension (optional for Phase D, required for real data)

Add `void getMyProfile(DomainCallback<User> callback)` to `UserRepository` interface.  
Implement in `UserRepositoryImpl` — initially returning the same mock `User` object.  
`ProfileViewModel.loadProfile()` replaces `buildMockState()` with the real repo call.

### D4. Final lint sweep

- Verify no `android:text` with raw string values across all new layouts
- Verify no `android:src` on dynamic `ImageView`s (Glide sets these at runtime)
- Confirm `@SuppressWarnings("unchecked")` is present on all factory `create()` methods

---

## Handoff Instructions (Paste into New Chat)

> We are building WalkMate V3.0 on branch `improve/coordination-flow`.
>
> **Phases A, B, and C are complete:**
> - Foundation resources (colors, strings, drawables) — `workphase-v0.1.notes.md`
> - Home Dashboard (full MVVM stack) — `workphase-v0.2.notes.md`
> - Profile Rebuild (full MVVM stack, flat ConstraintLayout stats row) — this file
>
> **Now begin Phase D — Navigation Wiring & Final Cleanup.**  
> Read `docs/dev/home/implementation_proposal.md` (Phase D section) for full spec.
>
> **Key facts:**
> - `HomeFragment.TAG = "home"`, `ExploreFragment.TAG = "ExploreFragment"`, `ProfileFragment.TAG = "ProfileFragment"`
> - `MainActivity.showTab(String tag)` is the routing method — already handles HomeFragment, ExploreFragment, MatchesFragment, ProfileFragment
> - `HomeViewModel.onFindWalkMateClicked()` is currently a no-op; Phase D wires it to navigate to ExploreFragment
> - Profile menu click handlers (`onWalkHistoryClicked`, `onMyBadgesClicked`, `onSettingsClicked`) are no-ops — Phase D connects them
> - `UserRepository` interface: `login`, `register`, `saveAccessToken`, `getAccessToken` only — **no `getMyProfile()`**
> - All mock data is in `HomeViewModel.buildReadyState()` and `ProfileViewModel.buildMockState()`
> - Read `docs/single-source-of-truth/architecture/Frontend_VI.md` before writing any new Java
