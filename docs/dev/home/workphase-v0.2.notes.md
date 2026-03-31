# Workphase Context Handoff — v0.2

**Current Version:** v0.2
**Phase Completed:** Phase B — Home Dashboard
**Date:** 2026-03-31
**Branch:** `improve/coordination-flow`

---

## Files Modified / Created

### Modified
| File | Change |
|---|---|
| `frontend/src/main/res/values/strings.xml` | Added 2 new entries: `home_greeting_format` ("Hi, %1$s! 👋") and `home_streak_title` ("Walking Streak") — required by HomeFragment but absent from Phase A |
| `frontend/src/main/java/com/walkmate/WalkMateApplication.java` | Added `walkSessionRepository` and `userRepository` singleton fields + their typed getters (`getWalkSessionRepository()`, `getUserRepository()`) |
| `frontend/src/main/java/com/walkmate/ui/main/MainActivity.java` | Re-pointed `tab_explore` from `ExploreFragment` to `HomeFragment`. Added `HomeFragment.TAG` to the hide-loop in `showTab()`. Added `HomeFragment` case to `createFragmentForTag()`. Default launch tab is now `HomeFragment`. |

### Created (Layouts)
| File | Notes |
|---|---|
| `res/layout/fragment_home.xml` | `NestedScrollView` root, `bg_cream` bg, `fillViewport="true"`, `paddingBottom="88dp"`. Sections: Top App Bar → Streak Widget → Hero Action → Upcoming Session → Quick Invite → Quick Stats. Upcoming Session body uses flat `ConstraintLayout` (no nested LinearLayout). |
| `res/layout/item_quick_invite.xml` | 56×56dp `FrameLayout` avatar + orange `+` badge, 56dp-wide name `TextView` below. |

### Created (Java — MVVM Stack)
| File | Notes |
|---|---|
| `ui/home/HomeDashboardUiState.java` | Immutable. Two inner classes: `UpcomingSessionSnapshot`, `QuickInviteUser`. Static `loading()` factory. |
| `ui/home/HomeViewModel.java` | Calls `sessionRepo.getActiveSessions()` for real (mock) session data. All other fields (greeting, streak, stats, invite list) use hardcoded mock values pending real API endpoints. `onCleared()` shuts down `ExecutorService`. |
| `ui/home/HomeViewModelFactory.java` | `ViewModelProvider.Factory` implementation. Receives `WalkSessionRepository` + `UserRepository` in constructor. |
| `ui/home/HomeFragment.java` | `TAG = "home"`. Inflates `fragment_home.xml`. Wires adapter + ViewModel in `onViewCreated()`. Single `renderState()` method as the only place Views are mutated. Loads avatars via Glide with `ic_user` placeholder. |
| `ui/home/quickinvite/QuickInviteAdapter.java` | `submitList()` API called from `renderState()`. Glide loads avatar URL; falls back to `ic_user` drawable when URL is null/empty. |

---

## Architectural State (Ready for Phase C)

| Resource | Status |
|---|---|
| `HomeFragment` wired as first tab in `MainActivity` | ✅ |
| `HomeDashboardUiState` immutable contract | ✅ |
| `HomeViewModel` + `HomeViewModelFactory` | ✅ |
| `QuickInviteAdapter` with Glide + placeholder | ✅ |
| `WalkMateApplication` exposes session + user repo singletons | ✅ |
| `fragment_home.xml` — all dynamic views use `tools:text`/`tools:src` | ✅ |
| `item_quick_invite.xml` — all dynamic views use `tools:src`/`tools:text` | ✅ |
| Zero hardcoded strings in XML layouts | ✅ |

---

## Known Mock Data (Replace When APIs Are Ready)

In `HomeViewModel.buildReadyState()`, the following fields are hardcoded mock values:

| Field | Mock Value | Replace With |
|---|---|---|
| `greetingName` | `"Alex"` | `userRepo.getProfile()` response |
| `locationName` | `"Ho Chi Minh City"` | Device location service |
| `hasUnreadNotification` | `true` | Notification repo |
| `streakDays` / `streakGoal` | `5` / `7` | Stats repo |
| `nearbyHotspotCount` | `5` | Hotspot repo |
| `weeklyDistanceKm` / `weeklySessionCount` | `12.5` / `3` | Stats repo |
| `quickInviteList` | 5 hardcoded users | Matching/social repo |

Session data (`UpcomingSessionSnapshot`) is real — pulled from `WalkSessionRepositoryImpl.getActiveSessions()`.

---

## What Was NOT Done (Deferred to Phase C+)

- `res/layout/fragment_profile.xml` — Full profile layout (Phase C)
- `ui/profile/ProfileUiState.java`, `ProfileViewModel.java`, `ProfileViewModelFactory.java`, `ProfileFragment.java` (Phase C)
- Navigation from `HomeFragment` → `ExploreFragment` when "Find a WalkMate Now" is tapped (Phase D)
- Real data for greeting, location, streak, hotspot count, weekly stats (pending backend endpoints)

---

## Next Immediate Step: Phase C — Profile Rebuild

### Phase C Checklist
- [ ] **C1** Rebuild `res/layout/fragment_profile.xml`
  - Root: `NestedScrollView`, `bg_cream`, `fillViewport="true"`, `paddingBottom="88dp"`
  - Single `LinearLayout` child, `orientation="vertical"`
  - Sections: Top App Bar → Identity → Milestones → Menu List
  - Identity: 88dp avatar `FrameLayout` + `bg_green_dot` status dot; name; public-profile link; trust `Chip`; personality `ChipGroup`
  - Milestones stats row: **flat `ConstraintLayout`** with 2 `Guideline`s at 33.3%/66.7% (see `implementation_proposal.md` §4 for full XML)
  - Menu rows: `LinearLayout` with `bg_warm_circle` icon + label + `ic_chevron_right`; each row `clickable="true"` with `selectableItemBackground`
  - All dynamic views (`imgProfileAvatar`, `txtProfileName`, `chipTrustScore`, stat values, badge images) use `tools:src`/`tools:text`
- [ ] **C2** Create `ui/profile/ProfileUiState.java`
  - Inner class `Badge { int labelStringResId; int iconDrawableResId; }`
  - Static `ProfileUiState.loading()` factory
- [ ] **C3** Create `ui/profile/ProfileViewModel.java`
  - Uses `UserRepository` for profile; mock data for milestones/badges pending real API
  - `onCleared()` shuts down executor
- [ ] **C4** Create `ui/profile/ProfileViewModelFactory.java`
- [ ] **C5** Rebuild `ui/profile/ProfileFragment.java` from the current stub

---

## Handoff Instructions (Paste into New Chat)

> We are building WalkMate V3.0 on branch `improve/coordination-flow`.
>
> **Phases A and B are complete.** The Home Dashboard is fully wired:
> - All foundation resources exist (colors, strings, drawables) — see `workphase-v0.1.notes.md`
> - `fragment_home.xml`, `item_quick_invite.xml`, and the full MVVM stack (`HomeFragment`, `HomeViewModel`, `HomeViewModelFactory`, `HomeDashboardUiState`, `QuickInviteAdapter`) are created
> - `WalkMateApplication` now exposes `getWalkSessionRepository()` and `getUserRepository()`
> - `MainActivity` re-pointed to `HomeFragment` as the first tab
>
> **Now begin Phase C — Profile Rebuild.** The spec is in `docs/dev/home/implementation_proposal.md` (Phase C section). Key rules:
> - 3-column stats row MUST be a flat `ConstraintLayout` with `Guideline`s — never `LinearLayout` + `layout_weight`
> - All dynamic views use `tools:text`/`tools:src`; all static labels use `android:text="@string/..."`
> - `ProfileUiState.Badge` inner class must carry `labelStringResId` and `iconDrawableResId` (not strings)
> - Architecture: MVVM, `LiveData<ProfileUiState>`, `ExecutorService`, manual DI via `WalkMateApplication`
> - `UserRepository` current interface only has: `login`, `register`, `saveAccessToken`, `getAccessToken` — no `getMyProfile()`. Use mock data for profile fields until the API is added.
> - Read `docs/single-source-of-truth/architecture/Frontend_VI.md` for hard constraints before writing any Java
