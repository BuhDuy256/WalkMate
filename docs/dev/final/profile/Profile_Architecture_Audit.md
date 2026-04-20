# WalkMate: Profile Feature Architecture & "Pause" Audit

This document provides a high-level architectural breakdown of the Profile feature (`ProfileFragment` & `ProfileViewModel`) to explain the data flow and identify the root cause of the UI "pausing" bug.

---

## 1. The Render Logic (UI Layer)

- **Initial Display:** When `ProfileFragment` opens, it sets up its observers and calls `viewModel.loadProfile()`. The method `renderState(ProfileUiState state)` serves as the single source of truth for updating the views.
- **The "Pause" Illusion (Bug Identified):** The Fragment is **not** doing too much work on the Main Thread. The issue lies in how the loading state is handled. In `renderState()`, if `state.isLoading()` is true, the method simply `return`s.
- **Missing Feedback:** Unlike `PublicProfileFragment`, `ProfileFragment` **does not display a ProgressBar** or skeleton loading UI. Because the screen stays blank or unpopulated while waiting for the network, it creates the illusion that the app has "paused" or "frozen".

## 2. The State Manager (ViewModel Layer)

- **State Holding:** The UI state is held as a single, immutable snapshot using `MutableLiveData<ProfileUiState>`.
- **Parallel Data Fetching:** `loadProfile()` first fetches the base profile. Once successful, it triggers a second phase (`loadSupplementalData()`) that fires 3 parallel API calls (Gamification Badges, Stats, and Walk Reviews). It uses an `AtomicInteger` barrier to wait for all 3 to finish before updating the UI.
- **History Data:** "History" data are **not** requested in this ViewModel. The ViewModel handles these purely as _navigation events_ (e.g., `onWalkHistoryClicked()` emits a signal to navigate to the `SessionHistoryFragment`). Any complex fetching for AI matches or history is properly delegated to their respective isolated fragments.

## 3. The Data Flow (Repository Layer)

**Sequence Flow:**

1. User opens the Profile Tab → `ProfileFragment.onViewCreated()` calls `viewModel.loadProfile()`.
2. `ProfileViewModel` posts `ProfileUiState.loading()` to the UI (which the UI currently ignores).
3. `ProfileViewModel` calls `UserProfileRepositoryImpl.getMyProfile()`.
4. The Repository opens an `ExecutorService` background thread and makes a synchronous network call to the backend.
5. Upon success, the background thread fires `onSuccess(profile)`.
6. The ViewModel receives the profile and triggers the 3 parallel background calls (Badges, Stats, Reviews).
7. Once the last background call completes, the ViewModel constructs the final `ProfileUiState` and uses `.postValue()` to safely push it back to the Main Thread.
8. `ProfileFragment.renderState()` runs again, `isLoading` is false, and the UI populates.

- **Missing Local Cache:** The current Data Flow relies 100% on the remote network. Following the `Frontend_VI.md` "Offline-first" directive, a **Room Database Local Cache** or SharedPreferences should be inserted in `UserProfileRepositoryImpl`. This would allow the repository to instantly return a cached user profile to the UI (eliminating the wait) while fetching the latest data silently in the background.

## 4. The "Pause" Audit (Threading)

- **No Main Thread Blocking:** The audit confirms that the Main Thread is **not** being stopped or blocked. All repository logic correctly wraps the synchronous Retrofit `.execute()` calls inside an `ExecutorService.execute(() -> { ... })` block, safely offloading the heavy lifting to background worker threads.
- **The Real Culprit:** The "blocking" feel is purely a UX design flaw. The UI thread is idle and responsive, but because there is no `ProgressBar` shown during the network wait, the user is left staring at an unresponsive-looking screen.
