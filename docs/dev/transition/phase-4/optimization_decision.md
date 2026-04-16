# Optimization Decisions — Phase 4 / 5

---

## Phase 4 · Scanning Flow

### Decision: `dismissTimeout()` called *before* showing the dialog, not in the positive button callback

The plan called `viewModel.dismissTimeout()` inside both dialog button callbacks. The risk: if the
device rotates *while* the dialog is open, `onViewCreated` re-runs and `renderState` is called
again. At that point `scanTimedOut` is still `true`, so the dialog would re-appear immediately.

By calling `viewModel.dismissTimeout()` as the very first thing in `showScanTimeoutDialog()` —
before `MaterialAlertDialogBuilder.show()` — the LiveData is reset synchronously on the main
thread. A rotation during the dialog dismisses the dialog naturally and never re-triggers it.

**Why:** Prevents duplicate dialogs on config change without needing a `dialogShowing` boolean.

---

### Decision: `observeForever` in `ExploreViewModel` (not `observe(lifecycleOwner, ...)`)

`AppEventBus.get().observe().observeForever(appEventObserver)` is used deliberately because
`ExploreViewModel` has no `LifecycleOwner`. Using `observeForever` from a `ViewModel` is the
standard pattern; the observer is removed in `onCleared()` to prevent leaks.

The alternative — passing `getViewLifecycleOwner()` to the bus observation from `ExploreFragment`
— would stop receiving FCM events when the fragment is paused or when another fragment is on top,
causing missed matches. The ViewModel layer is the correct place for this observation.

**Why:** FCM can fire at any time; the match correlation logic lives in the ViewModel and must
remain active for the lifetime of the ViewModel, not the Fragment view.

---

### Decision: `consumeEvent()` on `AppEventBus` called inside `ExploreViewModel`, not in `MainActivity`

Phase 3 set up `MainActivity` to observe `AppEventBus` and navigate to `matchesFragment`. Phase 4
adds a *second* consumer in `ExploreViewModel`. To avoid double-handling:

- `ExploreViewModel.appEventObserver` calls `AppEventBus.get().consumeEvent()` immediately after
  matching on `activeIntentId`, clearing the sticky event.
- `MainActivity`'s observer therefore receives `null` for that event and takes no action.

This means the ExploreViewModel "wins" the event when a scan is active. If no scan is active
(user is not in SCANNING state), `activeIntentId` is `null` and the match fails, so `MainActivity`
handles the FCM event normally (navigates to Matches).

**Why:** Prevents the user from being navigated away from ExploreFragment twice for the same event.

---

### Decision: `stopSearching()` uses a local copy of `activeIntentId` before nulling it

```java
final String idToCancel = activeIntentId;
activeIntentId = null;
intentRepository.cancelIntent(idToCancel, ...);
```

`activeIntentId` is set to `null` before the async call returns. This prevents a race where the
user taps "Stop Searching" twice (or the timeout fires simultaneously) from sending two cancel
requests for the same intent. The repository call uses the captured local copy.

**Why:** Simple null-guard against double-cancel without adding synchronization overhead.

---

### Decision: `WalkIntentRepository` injected into `ExploreViewModel` (not accessed via Application)

The plan called `intentRepository.cancelIntent(...)` without specifying the injection path. Two
options were considered:

1. Retrieve from `WalkMateApplication` inside `ExploreViewModel` (same pattern as `MainActivity`).
2. Inject via `ExploreViewModelFactory` constructor parameter.

Option 2 was chosen because `ExploreViewModelFactory` already handles `HotspotRepository`
injection. Adding `WalkIntentRepositoryImpl` here keeps the ViewModel's dependencies explicit,
testable, and consistent with the existing factory pattern. `WalkMateApplication` is not modified.

---

## Phase 5 · Matches Auto-Navigation

### Decision: `MutableLiveData<Integer>` with consume pattern instead of a `SingleLiveEvent`

There is no `SingleLiveEvent` class in this codebase. The project uses a consistent
`consumeXxx()` pattern (see `consumeError()` in every UiState/ViewModel). A
`MutableLiveData<Integer> scrollToTabEvent` with a `consumeScrollToTab()` null-reset was used to
follow that convention exactly. No new utility class was introduced.

**Why:** Consistency with existing patterns; avoids importing an external helper for a
one-off use case.

---

### Decision: `hasAutoScrolledToProposal` flag is per-Fragment-instance, not persisted in ViewModel

The auto-scroll to Proposal tab (Phase 5b) should fire once when proposals are first loaded.
Persisting this flag in the ViewModel would suppress auto-scroll for the user's entire session
even if they navigate away and return. A per-instance boolean in `MatchesFragment` means the
scroll fires once per Fragment creation — which is the desired UX (each time the user opens
Matches fresh, the non-empty proposal list scrolls them to Proposal tab).

**Why:** Scoping the flag to the Fragment avoids over-persistence while still preventing multiple
scrolls within a single `onViewCreated` session.

---

### Decision: `subTabPager.post(() -> scrollToSubTab(...))` for argument-driven scroll

The scroll from navigation arguments is deferred via `subTabPager.post(...)`. This is required
because `ViewPager2` sets up its internal state asynchronously after `setAdapter()`. Calling
`setCurrentItem()` synchronously in `onViewCreated` before the first layout pass is a no-op on
some API levels. The `post()` defers to the next frame when the pager is ready.

The `scrollToTabEvent` observer does not need this deferral because it fires after `loadAll()`
completes (async), by which time the pager is fully initialized.
