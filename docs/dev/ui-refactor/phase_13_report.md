# Phase 13 Report — Post-Session Features
**Date:** 2026-04-09
**Branch:** `implement/realtime`
**Gaps closed:** 7.1 (history screen), 7.2 (route replay), 7.3 (incident report), 7.4 (post-session summary), 7.5 (review screen)

---

## Repository Method Verification

All three required methods were already present in `WalkSessionRepositoryImpl` (from Phase 6):

| Method | Status |
|---|---|
| `getSessionHistory(callback)` | Present ✓ |
| `getSessionRoute(sessionId, callback)` | Present ✓ |
| `reportSession(sessionId, reportedUserId, reason, evidenceUrl, callback)` | Present ✓ |

No repository changes were needed.

---

## Files Created

### Session History (Gap 7.1)

| File | Package |
|---|---|
| `SessionHistoryUiState.java` | `com.walkmate.ui.history` |
| `SessionHistoryViewModel.java` | `com.walkmate.ui.history` |
| `SessionHistoryViewModelFactory.java` | `com.walkmate.ui.history` |
| `SessionHistoryAdapter.java` | `com.walkmate.ui.history` |
| `SessionHistoryFragment.java` | `com.walkmate.ui.history` |

**Adapter behavior:** Shows formatted date (YYYY-MM-DD from ISO string), partner ID placeholder, status label, distance, duration. Tap → `OnSessionSelectedListener.onSessionSelected(sessionId)`.

### Route Replay (Gap 7.2)

| File | Package |
|---|---|
| `RouteReplayUiState.java` | `com.walkmate.ui.history.routereplay` |
| `RouteReplayViewModel.java` | `com.walkmate.ui.history.routereplay` |
| `RouteReplayViewModelFactory.java` | `com.walkmate.ui.history.routereplay` |
| `RouteReplayActivity.java` | `com.walkmate.ui.history.routereplay` |

**Polyline decoding:** Uses `PolyUtil.decode(String)` from `com.google.maps.android`. User A paths drawn in `Color.BLUE`, user B paths in `Color.RED`. Camera is moved to encompass all points via `LatLngBounds.Builder`. Decoding errors are caught and the segment is skipped non-fatally.

### Post-Session Summary (Gap 7.4)

| File | Package |
|---|---|
| `PostSessionSummaryUiState.java` | `com.walkmate.ui.gamification` |
| `PostSessionSummaryFragment.java` | `com.walkmate.ui.gamification` |
| `PostSessionSummaryViewModelFactory.java` | `com.walkmate.ui.gamification` |

**Existing `PostSessionSummaryViewModel` modified:**
- Constructor changed from `(GamificationRepository)` → `(GamificationRepository, WalkSessionRepository)`
- `repository` field split into `gamificationRepo` and `sessionRepo`
- Added `loadSummary(String sessionId)` — fetches history, finds matching `SessionSummary`, posts to `sessionSummary: MutableLiveData<SessionSummary>`
- Added `getSessionSummary()` getter

### Submit Review (Gap 7.5)

| File | Package |
|---|---|
| `ReviewUiState.java` | `com.walkmate.ui.review` |
| `SubmitReviewFragment.java` | `com.walkmate.ui.review` |
| `ReviewViewModelFactory.java` | `com.walkmate.ui.review` |

**Existing `ReviewViewModel` modified:**
- Constructor changed from `(ReviewRepository)` → `(ReviewRepository, WalkSessionRepository)`
- Added `reviewUiState: MutableLiveData<ReviewUiState>` and `getReviewUiState()` getter
- Added `loadReviewState(String sessionId)` — fetches history, finds matching entry, posts `alreadyReviewed()` or `idle()`

**`ReviewUiState` kinds:** `IDLE`, `LOADING`, `SUCCESS`, `ALREADY_REVIEWED`, `ERROR`

### Incident Report (Gap 7.3)

| File | Package |
|---|---|
| `ReportIncidentUiState.java` | `com.walkmate.ui.report` |
| `ReportIncidentViewModel.java` | `com.walkmate.ui.report` |
| `ReportIncidentViewModelFactory.java` | `com.walkmate.ui.report` |
| `ReportIncidentFragment.java` | `com.walkmate.ui.report` |

**Reason mapping:** RadioGroup labels → `AbortReason.toApiValue()` strings (`SAFETY_CONCERN`, `EMERGENCY`, `PARTNER_MISCONDUCT`, `OTHER`). Optional evidence URL passed through to `reportSession()`.

---

## Entry Point Wiring Summary

```
ProfileFragment ──[menuWalkHistory click]──► ProfileViewModel.onWalkHistoryClicked()
                                                  │ navigateToHistoryEvent
                                                  ▼
                                        SessionHistoryFragment
                                                  │ SessionHistoryAdapter.onSessionSelected(sessionId)
                                                  ▼
                                        RouteReplayActivity (Intent + EXTRA_SESSION_ID)

TrackingScreenActivity ──[WalkState.FINISHED]──► showPostSessionSummary()
                                                      │ getSupportFragmentManager().add(android.R.id.content, …)
                                                      ▼
                                           PostSessionSummaryFragment
                                             │                   │
                                    [Leave Review]          [Report Incident]
                                             │                   │ (only if isAborted=true)
                                             ▼                   ▼
                                  SubmitReviewFragment   ReportIncidentFragment
```

---

## TrackingScreenActivity — FINISHED Handling

**Old behavior:** `showWalkCompletedDialog()` — AlertDialog with stats, dismiss → `finish()`.

**New behavior:** `showPostSessionSummary()` adds `PostSessionSummaryFragment` over `android.R.id.content`. The Fragment handles its own back-stack (pops → returns to tracking screen which the user can dismiss).

The `wasLastActionAbort()` flag was added to `TrackingViewModel`:
- Set to `true` inside `abortWalk()` before the API call.
- Defaults to `false` (normal completion path).
- Read by `TrackingScreenActivity` to set `isAborted` in the Fragment's Bundle args.

---

## PostSessionSummaryViewModel Constructor Signature (for Phase 14)

```java
public PostSessionSummaryViewModel(
    GamificationRepository gamificationRepo,
    WalkSessionRepository  sessionRepo)
```

## ReviewViewModel Constructor Signature (for Phase 14)

```java
public ReviewViewModel(
    ReviewRepository      reviewRepository,
    WalkSessionRepository sessionRepository)
```

---

## Files Modified

| File | Change |
|---|---|
| `ui/profile/ProfileViewModel.java` | Added `navigateToHistoryEvent` + `onWalkHistoryClicked()` → fires event; `consumeNavigateToHistory()` |
| `ui/profile/ProfileFragment.java` | Imported `SessionHistoryFragment`; observes `navigateToHistoryEvent` → launches `SessionHistoryFragment` |
| `ui/gamification/PostSessionSummaryViewModel.java` | Added `WalkSessionRepository` param + `loadSummary()` + `sessionSummary` LiveData |
| `ui/gamification/PostSessionSummaryViewModelFactory.java` | **New file** (was missing from original) |
| `ui/review/ReviewViewModel.java` | Added `WalkSessionRepository` param + `loadReviewState()` + `reviewUiState` LiveData |
| `ui/tracking/TrackingViewModel.java` | Added `lastActionWasAbort` flag + `wasLastActionAbort()` getter; set in `abortWalk()` |
| `ui/tracking/TrackingScreenActivity.java` | Replaced `showWalkCompletedDialog()` with `showPostSessionSummary()`; imports `PostSessionSummaryFragment` |
