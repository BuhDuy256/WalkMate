# Phase 9 Report — Session Lifecycle Feature
**Date:** 2026-04-09
**Branch:** `implement/realtime`
**Gaps closed:** 5.1 (activation flow), 5.2 (5-minute gate in ViewModel), 5.3 (polling cancelled in onPause), 5.4 (FINISHING loading state), 5.5 (Complete/Abort buttons in TrackingScreenActivity)

---

## Activation Flow — replaces direct TrackingScreenActivity launch

`SessionFragment` no longer launches `TrackingScreenActivity` on "Start Walk" click.
Instead:

1. `ActivationWindowButtonView` (in `item_session_card.xml`) shows the "I'm Here!" button and enforces the 10-min-before / 15-min-after window client-side.
2. Tapping "I'm Here!" calls `MatchesViewModel.activateSession(sessionId)` → `sessionRepository.activateSession()`.
3. Result is delivered via `activationResultEvent` LiveData:
   - **Case B** (session.status == ACTIVE): both users activated → `TrackingScreenActivity` launched with full session extras.
   - **Case A** (session non-null but still PENDING): only this user activated → Toast shown + 15-second polling started.
   - **Error** (`SESSION_ACTIVATION_WINDOW_CLOSED`): Toast shown.

---

## Polling — cancelled in onPause / resumed in onResume

`SessionFragment` uses a `Handler.postDelayed` loop:

| Trigger | Interval | Action |
|---|---|---|
| After Case A activation | 15 s | `matchesViewModel.loadAll()` |
| `onResume` (background refresh) | 30 s | `matchesViewModel.loadAll()` |
| `onPause` | — | `pollHandler.removeCallbacks(pollRunnable)` |
| `onDestroyView` | — | `pollHandler.removeCallbacks(pollRunnable)` |

---

## SessionAdapter Changes

### Listener interface
`OnStartWalkClickListener` replaced with `SessionActionListener`:
```java
interface SessionActionListener {
    void onArriveClicked(String sessionId);
    void onAbortClicked(String sessionId);
    void onCompleteClicked(String sessionId);
}
```

### ViewHolder — new fields
- `ActivationWindowButtonView activationBtn`
- `MaterialButton btnComplete`
- `MaterialButton btnAbort`

### `onBindViewHolder` — PENDING vs ACTIVE
```
PENDING → activationBtn visible, btnComplete/btnAbort gone
ACTIVE  → activationBtn gone, btnComplete (enabled=canComplete()), btnAbort visible
else    → all three gone
```

### `onViewRecycled`
```java
holder.activationBtn.release(); // stops Handler loop
```

---

## MatchesViewModel — new activateSession()

New field and methods:
```java
private final MutableLiveData<ActivationResult> activationResultEvent = new MutableLiveData<>(null);
public LiveData<ActivationResult> getActivationResultEvent() { ... }
public void consumeActivationResult() { ... }
public void activateSession(String sessionId) { ... }

static class ActivationResult {
    final WalkSession session;
    final String errorCode;
}
```

### New MatchesViewModel constructor signature (unchanged from Phase 8)
```java
public MatchesViewModel(
    WalkIntentRepository intentRepository,
    WalkProposalRepository proposalRepository,
    WalkSessionRepository sessionRepository,
    UserProfileRepository userProfileRepository)
```

---

## TrackingUiState — new fields

| Field | Type | Description |
|---|---|---|
| `completeTooEarlySeconds` | `long` | Seconds remaining before Complete is allowed; 0 when permitted. Computed dynamically from elapsed vs 5-min minimum. |
| `isSaving` | `boolean` | `true` while `walkState == FINISHING`. |

---

## TrackingViewModel — new constructor + methods

### New constructor signature
```java
public TrackingViewModel(
    @NonNull Application application,
    @NonNull WalkSessionRepository sessionRepository)
```
`TrackingViewModelFactory` now builds a `WalkSessionRepositoryImpl(application)` and passes it in.

### requestCompleteWalk()
- Guard: `walkState != ACTIVE` → no-op
- 5-minute gate: `elapsedSeconds < MINIMUM_WALK_DURATION_MINUTES * 60` → calls `rebuildUiState()` (UI already shows countdown via `completeTooEarlySeconds`), returns early
- Transitions: `ACTIVE → FINISHING` → calls `sessionRepository.completeSession()`
  - Success: `FINISHING → FINISHED`
  - Error: `FINISHING → ACTIVE` (restarts timer + GPS), posts error to `completionErrorLiveData`

### abortWalk(AbortReason reason)
- Transitions: any active state → `FINISHING` → calls `sessionRepository.abortSession()`
  - Success: `FINISHING → FINISHED`
  - Error: `FINISHING → ACTIVE` (restarts timer + GPS), posts error to `completionErrorLiveData`

### completionErrorLiveData
```java
private final MutableLiveData<String> completionErrorLiveData = new MutableLiveData<>();
public LiveData<String> getCompletionError() { return completionErrorLiveData; }
```

---

## TrackingScreenActivity — new buttons + states

### New buttons (added to `activity_tracking_screen.xml`)
- `btnComplete` — orange gradient pill, "Complete Walk", `visibility="gone"` by default
- `btnAbort` — red outlined pill, "Emergency Abort", `visibility="gone"` by default

### updateControls(TrackingUiState) — state table

| WalkState | btnStart | btnRowPauseStop | btnComplete | btnAbort |
|---|---|---|---|---|
| READY | visible | gone | gone | gone |
| ACTIVE | gone | visible (Pause) | visible, enabled=`completeTooEarlySeconds==0` | visible, enabled |
| PAUSED | gone | visible (Resume) | gone | gone |
| FINISHING | gone | gone | visible, disabled | visible, disabled |
| FINISHED | gone | gone | gone | gone |

When `completeTooEarlySeconds > 0` in ACTIVE state: `btnComplete` text shows `"Available in Xs"`.

### Click handlers
- `btnComplete` → AlertDialog confirmation → `viewModel.requestCompleteWalk()`
- `btnAbort` → `showAbortReasonDialog()` with `AbortReason` radio buttons → `viewModel.abortWalk(selectedReason)`

### Observations
- `completionErrorLiveData` → Toast on error
- `WalkState.FINISHED` → existing `showWalkCompletedDialog()` (PostSessionSummaryFragment deferred to Phase 13)

---

## Layout Changes

### `item_session_card.xml`
| Removed | Replaced with |
|---|---|
| `MaterialButton @id/btnStartWalk` | `ActivationWindowButtonView @id/activationBtn` |

New additions: `MaterialButton @id/btnComplete`, `MaterialButton @id/btnAbort`

### `activity_tracking_screen.xml`
Added after `btnRowPauseStop`:
- `MaterialButton @id/btnComplete`
- `MaterialButton @id/btnAbort`

### `strings.xml`
| Key | Value |
|---|---|
| `session_waiting_for_partner` | "Waiting for partner to arrive…" |
| `btn_complete_walk` | "Complete Walk" |
| `btn_abort_walk` | "Emergency Abort" |
| `tracking_complete_too_early_format` | "Available in %ds" |
| `complete_walk_confirm_title` | "Complete Walk?" |
| `complete_walk_confirm_message` | "Are you sure you want to finish the walk?" |
| `btn_complete_confirm` | "Complete" |
| `abort_walk_title` | "Abort Walk?" |
| `btn_abort_confirm` | "Abort" |
| `abort_reason_safety` | "Safety concern" |
| `abort_reason_emergency` | "Emergency" |
| `abort_reason_misconduct` | "Partner misconduct" |
| `abort_reason_other` | "Other" |
