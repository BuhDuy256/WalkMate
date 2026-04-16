# Phase Summary — Phase 4 / 5

Use this file as the contextual input for the **next** chat session.

---

## Checklist — What Was Implemented

### Phase 4 · Scanning Flow: FCM Consumer + Timeout + Cancel Fix ✅

- [x] `ExploreUiState` — two new fields added: `scanTimedOut` (boolean) and `matchFoundProposalId` (String)
- [x] `ExploreUiState` — new copy helpers: `withScanTimedOut(boolean)`, `withMatchFound(String)`
- [x] `ExploreUiState` — full private constructor updated to carry the two new fields through `withFilteredHotspots()`
- [x] `ExploreViewModel` — `activeIntentId` field stores the ID of the currently active scan intent
- [x] `ExploreViewModel` — `WalkIntentRepository` injected via constructor (for `stopSearching()` cancel call)
- [x] `ExploreViewModel` — `Handler(Looper.getMainLooper())` + `timeoutRunnable` implement the 10-second timeout
- [x] `ExploreViewModel` — `onIntentCreated()` stores `activeIntentId` and starts the timeout
- [x] `ExploreViewModel` — `stopSearching()` cancels timeout, calls `intentRepository.cancelIntent()`, resets to WELCOME
- [x] `ExploreViewModel` — `dismissTimeout()` resets `scanTimedOut` to `false`
- [x] `ExploreViewModel` — `consumeMatchFound()` clears `matchFoundProposalId` signal
- [x] `ExploreViewModel` — `AppEventBus.observeForever(appEventObserver)` correlates FCM events with `activeIntentId`
- [x] `ExploreViewModel` — `onCleared()` cancels timeout + removes FCM observer (no memory leak)
- [x] `ExploreViewModelFactory` — passes `WalkIntentRepositoryImpl` to the updated constructor
- [x] `ExploreFragment` — imports `Navigation`, `MaterialAlertDialogBuilder`
- [x] `ExploreFragment` — `btnStopSearching` now calls `viewModel.stopSearching()` (was `resetToWelcome()`)
- [x] `ExploreFragment.renderState()` — handles `matchFoundProposalId`: navigates to `matchesFragment` with `scrollToTab=TAB_PROPOSAL`, then consumes
- [x] `ExploreFragment.renderState()` — handles `scanTimedOut`: calls `showScanTimeoutDialog()`
- [x] `ExploreFragment.showScanTimeoutDialog()` — "Still looking…" `MaterialAlertDialog` with:
  - Positive: "Keep Searching" (stays in SCANNING)
  - Negative: "Save to Finding List" → navigates to `matchesFragment` with `scrollToTab=TAB_FINDING`

### Phase 5 · Matches Auto-Navigation ✅

- [x] `MatchesViewModel` — `MutableLiveData<Integer> scrollToTabEvent` added
- [x] `MatchesViewModel` — `getScrollToTabEvent()` + `consumeScrollToTab()` added
- [x] `MatchesViewModel.acceptProposal()` — posts `TAB_SESSION` to `scrollToTabEvent` on success
- [x] `MatchesFragment` — `hasAutoScrolledToProposal` boolean flag (per-instance)
- [x] `MatchesFragment.onViewCreated()` — reads `scrollToTab` argument and calls `subTabPager.post(() -> scrollToSubTab(...))` (deferred for ViewPager2 readiness)
- [x] `MatchesFragment` — observes `uiState.getProposals()` and auto-scrolls to TAB_PROPOSAL on first non-empty load
- [x] `MatchesFragment` — observes `getScrollToTabEvent()` and scrolls then consumes

---

## Files Modified / Created

| File | Action |
|------|--------|
| `ui/explore/ExploreUiState.java` | Modified — `scanTimedOut`, `matchFoundProposalId` fields + copy helpers |
| `ui/explore/ExploreViewModel.java` | Modified — full rewrite: `activeIntentId`, `Handler` timeout, FCM observer, `WalkIntentRepository` |
| `ui/explore/ExploreViewModelFactory.java` | Modified — passes `WalkIntentRepositoryImpl` to updated constructor |
| `ui/explore/ExploreFragment.java` | Modified — imports added, `btnStopSearching` → `stopSearching()`, dialog + navigation in `renderState` |
| `ui/matches/MatchesViewModel.java` | Modified — `scrollToTabEvent` LiveData + `getScrollToTabEvent()` + `consumeScrollToTab()` + `acceptProposal` posts event |
| `ui/matches/MatchesFragment.java` | Modified — full rewrite: deferred scroll from args, auto-scroll on proposals, `scrollToTabEvent` observer |

---

## States, Variables & API Contracts the Next Phase Must Know

### ExploreUiState — new fields

| Field | Type | Meaning |
|-------|------|---------|
| `scanTimedOut` | `boolean` | `true` for one frame after the 10-second timeout; reset to `false` by `dismissTimeout()` before the dialog is shown |
| `matchFoundProposalId` | `String` (nullable) | Non-null when FCM delivers a MATCH_FOUND event matching `activeIntentId`; consumed by `consumeMatchFound()` after navigation |

### ExploreViewModel — key state

- `activeIntentId` — stores the `WalkIntent.getId()` of the current scan.  Set in `onIntentCreated()`, cleared in `stopSearching()` and implicitly by `resetToWelcome()` (state reset, no scan).
- The `appEventObserver` uses `observeForever` and is removed in `onCleared()`. It calls `AppEventBus.consumeEvent()` when it handles a MATCH_FOUND event, preventing `MainActivity`'s observer from also reacting.

### MatchesViewModel — new API

- `getScrollToTabEvent()` — `LiveData<Integer>` (nullable); holds a `MatchesPagerAdapter.TAB_*` constant.
- `consumeScrollToTab()` — must be called immediately after handling to prevent re-scroll on rotation.

### Navigation behaviour

- **Match found while scanning** → ExploreFragment navigates to `matchesFragment` with `args.scrollToTab = TAB_PROPOSAL`.
- **Scan timeout → "Save to Finding List"** → ExploreFragment navigates to `matchesFragment` with `args.scrollToTab = TAB_FINDING`.
- **Scan timeout → "Keep Searching"** → user stays in SCANNING; no navigation.
- **Accept proposal** → MatchesFragment auto-scrolls to `TAB_SESSION` via `scrollToTabEvent`.
- **Proposals loaded non-empty** → MatchesFragment auto-scrolls to `TAB_PROPOSAL` once per instance.

### Interaction between AppEventBus and ExploreViewModel

If a MATCH_FOUND FCM arrives **while the user is scanning** (ExploreViewModel has a non-null
`activeIntentId` matching the event), ExploreViewModel handles it and consumes the event.
`MainActivity`'s AppEventBus observer sees `null` and does nothing.

If a MATCH_FOUND FCM arrives **while the user is NOT scanning** (e.g. they stopped the scan
before the event arrived), `activeIntentId` is `null`, the ViewModel's match check fails, the
event is NOT consumed by ExploreViewModel, and `MainActivity` navigates to `matchesFragment`
as set up in Phase 3.
