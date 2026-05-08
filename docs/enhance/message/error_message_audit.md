# Error Message Audit — WalkMate Android Frontend

**Date:** 2026-05-08
**Scope:** Full frontend Java source (`frontend/src/main/java/com/walkmate/`)
**Goal:** Identify all places where raw backend error codes reach the user, and propose a centralised resolution strategy.

---

## 1. Backend API Error Shape

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "OVERLAP_INTENT",
    "message": "Human-readable description from backend."
  }
}
```

**Parsed by:** `ErrorParser.extractApiError(Response<?>, String fallbackCode)`
→ Returns `ApiError` with `.getCode()` and `.getMessage()`.

**Key field used by repositories:** `apiError.getCode()` — the raw backend error code string.

---

## 2. Universal Repository Error Pattern

Every `*RepositoryImpl` follows the same pattern:

```java
ApiError apiError = ErrorParser.extractApiError(resp, "MY_FALLBACK_CODE");
// For VALIDATION_ERROR:
callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
// For all other errors:
callback.onError(new Exception(apiError.getCode()));
```

**Result:** `Exception.getMessage()` that arrives at the ViewModel is always the raw backend
error code (e.g., `OVERLAP_INTENT`, `SESSION_NOT_FOUND`). For network failures, it is the
`IOException` message (e.g., `Unable to resolve host`).

---

## 3. What Is Already Protected

| Scope | Mechanism | File |
|-------|-----------|------|
| Auth domain (login / register / OTP) | `UserErrorMessageMapper.map(code)` → `R.string.*` resource | `LoginViewModel`, `RegisterViewModel`, `OtpVerifyViewModel` |
| QR verification | Local `friendlyMessage()` switch inside ViewModel | `ScanQrViewModel` |
| Password change / set | Local `resolveErrorMessage()` switch inside ViewModel | `SecurityViewModel` |
| Proposal accept — known codes | Partial inline `switch` with English strings | `MatchesViewModel.acceptProposal()` |
| Partner status notices | `mapPartnerStatusTransitionToNotice()` — friendly strings | `TrackingViewModel` |

`UserErrorMessageMapper` is the only centralised mapper. It covers **user/auth domain codes only**
(17 codes total). No equivalent exists for intent, proposal, session, or review domains.

---

## 4. Risky Locations — Full Table

| # | File | Class / Method | Current behaviour | Example raw value that could be shown | Risk |
|---|------|----------------|-------------------|---------------------------------------|------|
| R-01 | `ui/matches/session/SessionFragment.java:160` | `activationResultEvent` observer | `Toast.makeText(ctx, result.errorCode, SHORT)` — **public field read directly by Fragment** | `SESSION_ACTIVATE_FAILED`, `SESSION_NOT_ACTIVE`, `QR_VERIFY_FAILED` | **HIGH** |
| R-02 | `ui/matches/MatchesViewModel.java:87` | `loadIntents() onError` | `postError(error.getMessage())` | `INTENT_FETCH_FAILED`, `INTENT_NOT_FOUND` | **HIGH** |
| R-03 | `ui/matches/MatchesViewModel.java:103` | `loadProposals() onError` | `postError(error.getMessage())` | `PROPOSALS_FETCH_FAILED` | **HIGH** |
| R-04 | `ui/matches/MatchesViewModel.java:131` | `loadSessions() onError` | `postError(error.getMessage())` | `SESSIONS_FETCH_FAILED`, `SESSION_NOT_FOUND` | **HIGH** |
| R-05 | `ui/matches/MatchesViewModel.java:149` | `cancelIntent() onError` | `postError(error.getMessage())` | `INTENT_CANCEL_FAILED`, `INTENT_NOT_FOUND` | **HIGH** |
| R-06 | `ui/matches/MatchesViewModel.java:171` | `passProposal() onError` | `postError(error.getMessage())` | `PROPOSAL_PASS_FAILED`, `MATCH_PROPOSAL_EXPIRED` | **HIGH** |
| R-07 | `ui/matches/MatchesViewModel.java:181` | `cancelProposal() onError` | `postError(error.getMessage())` | `PROPOSAL_CANCEL_FAILED` | **HIGH** |
| R-08 | `ui/matches/MatchesViewModel.java:226` | `acceptProposal() onError` — **default branch** | `postError(code)` where code = raw value not in switch | Any unlisted backend code, e.g., `MATCH_PROPOSAL_EXPIRED` | **HIGH** |
| R-09 | `ui/matches/MatchesViewModel.java:255` | `cancelSession() onError` — **default branch** | `postError(code)` for non-VALIDATION_ERROR cases | `SESSION_CANCEL_FAILED`, `SESSION_ALREADY_COMPLETED` | **HIGH** |
| R-10 | `ui/matches/MatchesViewModel.java:265` | `activateSession() onError` | `activationResultEvent.postValue(new ActivationResult(null, e.getMessage()))` — raw code stored in event | `SESSION_ACTIVATE_FAILED`, `SESSION_ACTIVATION_WINDOW_CLOSED` | **HIGH** |
| R-11 | `ui/explore/createintent/CreateIntentViewModel.java:120` | `submit() onError` — else branch | `withError(msg)` — raw code in UiState | `OVERLAP_INTENT`, `WALK_INTENT_OVERLAP`, `PROFILE_INCOMPLETE_FOR_MATCHING` (already special-cased but others not) | **HIGH** |
| R-12 | `ui/tracking/TrackingScreenActivity.java:150` | `getCompletionError()` observer | `Toast.makeText(this, error, SHORT)` | `SESSION_COMPLETE_FAILED`, `SESSION_ALREADY_COMPLETED` | **HIGH** |
| R-13 | `ui/review/ReviewViewModel.java:98` | `submitReview() onError` | `error.postValue(e.getMessage())` | `REVIEW_ALREADY_EXISTS`, `SESSION_NOT_FOUND` | **HIGH** |
| R-14 | `ui/review/ReviewViewModel.java:117` | `loadReviewsForUser() onError` | `error.postValue(e.getMessage())` | `REVIEW_FETCH_FAILED`, `USER_NOT_FOUND` | **HIGH** |
| R-15 | `ui/review/SubmitReviewFragment.java:175` | `reviewUiState` observer — `ERROR` case | `Toast.makeText(ctx, state.error, SHORT)` | Raw code from ReviewViewModel | **HIGH** |
| R-16 | `ui/review/SubmitReviewFragment.java:204` | `submitState` observer — `ERROR` case | `Toast.makeText(ctx, errMsg, SHORT)` where `errMsg = viewModel.getError().getValue()` | Same | **HIGH** |
| R-17 | `ui/report/ReportIncidentViewModel.java:99` | `submitReport() onError` | `ReportIncidentUiState.error(msg)` — raw code in state | `SESSION_REPORT_FAILED`, `SESSION_NOT_FOUND` | **HIGH** |
| R-18 | `ui/report/ReportIncidentFragment.java:168` | `renderState()` observer | `Toast.makeText(ctx, state.error, SHORT)` | Same as above | **HIGH** |
| R-19 | `ui/profile/ProfileViewModel.java:318` | `friendlyError(Exception)` helper | Returns `e.getMessage()` as the "friendly" string | `USER_NOT_FOUND`, `PROFILE_FETCH_FAILED` | **MEDIUM** |
| R-20 | `ui/profile/ProfileFragment.java:245` | `renderState()` | `Toast.makeText(ctx, state.getError(), SHORT)` | Same as above | **MEDIUM** |
| R-21 | `ui/explore/ExploreViewModel.java:116` | `loadHotspots() onError` | `withError(error.getMessage())` | `HOTSPOT_FETCH_FAILED` | **MEDIUM** |
| R-22 | `ui/explore/ExploreFragment.java:895` | ExploreUiState error observer | `Toast.makeText(ctx, state.getError(), SHORT)` | Same as ExploreViewModel | **MEDIUM** |
| R-23 | `ui/social/friends/FriendListFragment.java:75` | `renderState()` | `Toast.makeText(ctx, state.getError(), SHORT)` | `FRIENDS_FETCH_FAILED` | **MEDIUM** |
| R-24 | `ui/social/blocked/BlockedUsersFragment.java:101` | `renderState()` | `Toast.makeText(ctx, state.getError(), SHORT)` | `BLOCKED_USERS_FETCH_FAILED` | **MEDIUM** |
| R-25 | `ui/badge/BadgeFragment.java:159` | `renderState()` | `Toast.makeText(ctx, state.getError(), SHORT)` | `BADGE_FETCH_FAILED` | **LOW** |
| R-26 | `ui/admin/reports/AdminReportsListFragment.java:154` | `renderState()` | `Toast.makeText(ctx, state.getError(), SHORT)` | `REPORTS_FETCH_FAILED` | **LOW** |
| R-27 | `ui/admin/reports/AdminReportDetailFragment.java:146` | `renderState()` | `Toast.makeText(ctx, state.getError(), SHORT)` | `REPORT_FETCH_FAILED` | **LOW** |

### Notes on Already-Safe Locations

| File | Why it is safe |
|------|----------------|
| `AuthActivity.java:121/127` | `state.getError()` comes from `LoginViewModel` which routes through `UserErrorMessageMapper` |
| `RegisterActivity.java:109/128` | Same — `RegisterViewModel` uses `UserErrorMessageMapper` |
| `OtpVerifyFragment.java:78` | Same — `OtpVerifyViewModel` uses `UserErrorMessageMapper` |
| `EmailInputFragment.java:54` | Same — `EmailInputViewModel` uses `UserErrorMessageMapper` |
| `NewPasswordFragment.java:58/62` | `NewPasswordViewModel` uses `UserErrorMessageMapper` |
| `SecurityFragment.java:134` | `SecurityViewModel.resolveErrorMessage()` returns English strings |
| `ScanQrFragment` — `txtErrorMessage.setText(state.getError())` | `ScanQrViewModel.friendlyMessage()` maps QR-specific codes to English |
| `TrackingScreenActivity.java:369` | `notice` from `mapPartnerStatusTransitionToNotice()` — pre-mapped friendly strings |
| `TrackingScreenActivity.java:329` | `R.string.tracking_permission_denied` — hardcoded string resource |
| `SessionFragment.java:151-154` | `SESSION_ACTIVATION_WINDOW_CLOSED` is explicitly handled with a friendly string |

---

## 5. Root Cause Analysis

The architecture has **two levels of inconsistency**:

### Level 1 — ViewModel does not translate
Most ViewModels store `error.getMessage()` (= raw code) directly into `UiState.error`.
The Fragment shows whatever is in `UiState.error`. Example chain:

```
WalkIntentRepositoryImpl.createIntent()
  → callback.onError(new Exception("OVERLAP_INTENT"))          ← raw code set here

CreateIntentViewModel.submit().onError()
  → uiState.withError(msg)                                      ← NOT translated

ExploreFragment.renderState()
  → Toast.makeText(ctx, state.getError(), SHORT).show()         ← shown raw
```

### Level 2 — Fragment reads event field directly (worst case)
`SessionFragment` reads the public `ActivationResult.errorCode` field directly from
a LiveData event and shows it in a Toast without any translation:

```java
// SessionFragment.java:159-160
} else if (result.errorCode != null) {
    Toast.makeText(requireContext(), result.errorCode, Toast.LENGTH_SHORT).show();
}
```

### Level 3 — `default` branch falls through to raw code
`MatchesViewModel.acceptProposal()` has a good partial switch but falls through to:
```java
default:
    postError(code);   // code = raw error code string
```

---

## 6. Proposed Architecture: `ErrorMessageResolver`

### Principle
The **ViewModel** is the single place responsible for translating error codes.
Fragments/Activities must never know about error codes — they only display the string from UiState.

```
Repository  →  Exception(rawCode)
                    ↓
ViewModel   →  ErrorMessageResolver.resolve(rawCode)  →  English string
                    ↓
UiState.error = "English user-facing message"
                    ↓
Fragment    →  Toast.makeText(ctx, state.getError(), SHORT)
```

### Proposed `ErrorMessageResolver` Interface

```java
// core/util/ErrorMessageResolver.java
public final class ErrorMessageResolver {

    private ErrorMessageResolver() {}

    public static String resolve(String errorCode) {
        if (errorCode == null) return UNKNOWN;
        if (errorCode.startsWith("VALIDATION_ERROR|")) {
            // Backend validation: strip prefix, return backend message directly
            return errorCode.substring("VALIDATION_ERROR|".length());
        }
        // Check for IOException messages (network errors)
        if (errorCode.toLowerCase().contains("unable to resolve host")
                || errorCode.toLowerCase().contains("failed to connect")
                || errorCode.toLowerCase().contains("timeout")) {
            return NETWORK_ERROR;
        }
        String msg = CODE_MAP.get(errorCode);
        return msg != null ? msg : UNKNOWN;
    }
}
```

This replaces and **supersedes** the current `UserErrorMessageMapper` by covering all domains.
`UserErrorMessageMapper` can be deprecated and delegated to this resolver.

### Strategy Comparison

| Option | Description | Tradeoff |
|--------|-------------|----------|
| **A — Single `ErrorMessageResolver` (Recommended)** | One static `HashMap<String, String>` in `core/util/`. All ViewModels call `ErrorMessageResolver.resolve(e.getMessage())`. `UserErrorMessageMapper` delegates to it. | Single source of truth. No string resources needed for domain errors. Easy to update and test. |
| B — Extend `UserErrorMessageMapper` | Add all domain codes to the existing mapper, keep `R.string.*` pattern. | Requires adding string resources for every code. More verbose. Good for i18n future, overkill now. |
| C — Each ViewModel has its own local switch | Keep the current per-ViewModel approach but ensure completeness. | Fragile. Easy to miss a code. Messages scatter across the codebase. |

**Recommendation: Option A.** The app is English-only today. A single `HashMap` is far simpler,
testable in isolation, and has zero boilerplate. If i18n is needed later, the keys of this map
become the string resource names.

---

## 7. Proposed English Message Map

All messages follow these rules:
- English only.
- User-friendly, no technical jargon.
- ≤ 80 characters (fits a Toast/Snackbar on any screen width).
- Action-oriented when the user can do something.
- Do not expose: `intent`, `proposal`, `domain`, `invariant`, `overlap`, `JDBC`, `Repository`.

### 7.1 Intent / Walk Scheduling Domain

| Error Code | Proposed Message |
|------------|-----------------|
| `OVERLAP_INTENT` | You already have a walk scheduled at that time. |
| `WALK_INTENT_OVERLAP` | That time slot overlaps with an existing walk. Please choose a different time. |
| `INTENT_NOT_FOUND` | This walk request could not be found. It may have been cancelled. |
| `INTENT_ALREADY_MATCHING` | A match is already in progress for this walk. |
| `INTENT_EXPIRED` | This walk request has expired. Please create a new one. |
| `INTENT_CANCEL_FAILED` | Could not cancel the walk request. Please try again. |
| `INTENT_FETCH_FAILED` | Could not load your walks. Pull down to retry. |
| `INTENT_CREATE_FAILED` | Could not create your walk request. Please try again. |
| `PROFILE_INCOMPLETE_FOR_MATCHING` | Please complete your profile before scheduling a walk. |

### 7.2 Match Proposal Domain

| Error Code | Proposed Message |
|------------|-----------------|
| `MATCH_PROPOSAL_EXPIRED` | This match offer has expired. |
| `MATCH_PROPOSAL_REJECTED` | The match was not accepted. |
| `MATCH_NOT_FOUND` | No match found yet. Keep waiting! |
| `PROPOSALS_FETCH_FAILED` | Could not load your match offers. Pull down to retry. |
| `PROPOSAL_ACCEPT_FAILED` | Could not accept this match. Please try again. |
| `PROPOSAL_PASS_FAILED` | Could not pass on this match. Please try again. |
| `PROPOSAL_CANCEL_FAILED` | Could not cancel the match. Please try again. |
| `PROPOSAL_ALREADY_TERMINAL` | This match offer is no longer active. |
| `PROPOSAL_NOT_FOUND` | Match offer not found. |
| `PROPOSAL_NOT_PARTICIPANT` | You are not part of this match. |
| `PROPOSAL_CONCURRENT_MODIFICATION` | A conflict occurred. Please refresh and try again. |
| `PROPOSAL_INTENT_NO_LONGER_OPEN` | Could not confirm — one of the walks is no longer available. |

### 7.3 Walk Session Domain

| Error Code | Proposed Message |
|------------|-----------------|
| `SESSION_NOT_FOUND` | Walk session not found. It may have already ended. |
| `SESSION_ALREADY_COMPLETED` | This walk has already been completed. |
| `SESSION_NOT_ACTIVE` | This walk is not in an active state. |
| `SESSION_ACTIVATE_FAILED` | Could not confirm your arrival. Please try again. |
| `SESSION_ACTIVATION_WINDOW_CLOSED` | Activation window closed. Waiting for status update. |
| `SESSION_CANCEL_FAILED` | Could not cancel the walk. Please try again. |
| `SESSION_COMPLETE_FAILED` | Could not finish the walk. Please try again. |
| `SESSION_REPORT_FAILED` | Could not submit your report. Please try again. |
| `SESSIONS_FETCH_FAILED` | Could not load your sessions. Pull down to retry. |
| `SESSION_HISTORY_FAILED` | Could not load your walk history. |
| `SESSION_SUMMARY_FAILED` | Could not load the walk summary. |
| `SESSION_ROUTE_FAILED` | Could not load the route data. |
| `QR_TOKEN_FETCH_FAILED` | Could not generate the QR code. Please try again. |
| `QR_VERIFY_FAILED` | QR verification failed. Please try again. |
| `SESSION_TERMINAL` | This walk has already ended. |

### 7.4 User / Auth Domain (already in `UserErrorMessageMapper` — kept for completeness)

| Error Code | Proposed Message |
|------------|-----------------|
| `USER_NOT_FOUND` | Account not found. Please check your details. |
| `USER_INVALID_CREDENTIALS` | Incorrect email or password. |
| `INVALID_CREDENTIALS` | Incorrect credentials. Please try again. |
| `USER_INVALID_CREDENTIALS` | Incorrect email or password. |
| `GOOGLE_LOGIN_FAILED` | Google Sign-In failed. Please try again. |
| `INVALID_USER_DATA` | Invalid information provided. |

### 7.5 Review Domain

| Error Code | Proposed Message |
|------------|-----------------|
| `REVIEW_ALREADY_EXISTS` | You have already reviewed this walk. |
| `REVIEW_FETCH_FAILED` | Could not load reviews. |
| `REVIEW_SUBMIT_FAILED` | Could not submit your review. Please try again. |

### 7.6 Social / Profile Domain

| Error Code | Proposed Message |
|------------|-----------------|
| `FRIENDS_FETCH_FAILED` | Could not load your friends list. |
| `BLOCKED_USERS_FETCH_FAILED` | Could not load blocked users. |
| `PROFILE_FETCH_FAILED` | Could not load this profile. |
| `HOTSPOT_FETCH_FAILED` | Could not load nearby hotspots. |
| `BADGE_FETCH_FAILED` | Could not load your badges. |

### 7.7 Catch-All

| Error Code | Proposed Message |
|------------|-----------------|
| `NETWORK_ERROR` _(IOException)_ | No connection. Please check your internet and try again. |
| `UNKNOWN_ERROR` / any unrecognised code | Something went wrong. Please try again. |

---

## 8. Recommended Implementation Plan

### Phase 1 — Create `ErrorMessageResolver` (no UI changes)
1. Create `frontend/src/main/java/com/walkmate/core/util/ErrorMessageResolver.java`.
2. Populate the `HashMap` with all entries from Section 7.
3. Handle `VALIDATION_ERROR|...` prefix and `IOException` message patterns.
4. Write unit tests covering every entry in the map and the fallback.
5. Update `UserErrorMessageMapper.map()` to delegate to `ErrorMessageResolver` for unmapped codes,
   or deprecate it after migrating callers.

### Phase 2 — Fix HIGH-risk locations (direct raw code → user)

Fix in priority order:

| Priority | Fix |
|----------|-----|
| P1 | `SessionFragment.java:160` — replace `result.errorCode` with `ErrorMessageResolver.resolve(result.errorCode)`. |
| P2 | `TrackingScreenActivity.java:150` — wrap `completionErrorLiveData` in `TrackingViewModel.completeOnBackend()`: `completionErrorLiveData.postValue(ErrorMessageResolver.resolve(e.getMessage()))`. |
| P3 | `MatchesViewModel` — replace all `postError(error.getMessage())` with `postError(ErrorMessageResolver.resolve(error.getMessage()))`. Fix the `default` branches in `acceptProposal()` and `cancelSession()`. |
| P4 | `CreateIntentViewModel.submit()` else branch — replace `withError(msg)` with `withError(ErrorMessageResolver.resolve(msg))`. |
| P5 | `ReviewViewModel.submitReview()` and `loadReviewsForUser()` — replace `error.postValue(e.getMessage())` with `error.postValue(ErrorMessageResolver.resolve(e.getMessage()))`. |
| P6 | `ReportIncidentViewModel.submitReport()` — replace raw `msg` with `ErrorMessageResolver.resolve(msg)`. |

### Phase 3 — Fix MEDIUM-risk locations

| Location | Fix |
|----------|-----|
| `ProfileViewModel.friendlyError()` | Replace body with `return ErrorMessageResolver.resolve(e.getMessage())`. |
| `ExploreViewModel.loadHotspots() onError` | Replace `withError(error.getMessage())` with `withError(ErrorMessageResolver.resolve(...))`. |

### Phase 4 — Clean up social/badge ViewModels (LOW risk)
Apply `ErrorMessageResolver` to remaining ViewModels: `FriendsViewModel`, `BlockedUsersViewModel`, `BadgeViewModel`, `AdminReportsListViewModel`, `AdminReportDetailViewModel`.

---

## 9. Files Likely to Change

**New file:**
- `frontend/src/main/java/com/walkmate/core/util/ErrorMessageResolver.java`

**Modified files (ViewModel layer — Phase 2 & 3):**
- `ui/matches/MatchesViewModel.java`
- `ui/matches/session/SessionFragment.java` _(Phase 2 P1 — only line 160)_
- `ui/tracking/TrackingViewModel.java`
- `ui/tracking/TrackingScreenActivity.java` _(or TrackingViewModel — prefer ViewModel)_
- `ui/explore/createintent/CreateIntentViewModel.java`
- `ui/review/ReviewViewModel.java`
- `ui/report/ReportIncidentViewModel.java`
- `ui/profile/ProfileViewModel.java`
- `ui/explore/ExploreViewModel.java`

**Optional / Phase 4:**
- `ui/social/friends/FriendsViewModel.java`
- `ui/social/blocked/BlockedUsersViewModel.java`
- `ui/badge/BadgeViewModel.java`
- `ui/admin/reports/AdminReportsListViewModel.java`
- `ui/admin/reports/AdminReportDetailViewModel.java`
- `core/util/UserErrorMessageMapper.java` _(delegate or deprecate)_

**Not to change:**
- All `*RepositoryImpl.java` files — the raw-code-as-exception pattern is architecturally correct at the data layer.
- `Fragment`/`Activity` files (except `SessionFragment:160`) — they should remain thin observers.
- `strings.xml` — no new string resources needed for domain errors (inline map approach).

---

## 10. Open Questions Before Implementation

1. **Should `ErrorMessageResolver` use `R.string.*` resources or inline strings?**
   The app is English-only. Inline strings in a `HashMap` are simpler and equally testable.
   If multi-language support is planned, each map key should become a `@StringRes int` lookup —
   decide before starting Phase 1 to avoid rework.

2. **Should `UserErrorMessageMapper` be merged into `ErrorMessageResolver` or kept separate?**
   `UserErrorMessageMapper` also returns `ActionType` (TOAST / FIELD_ERROR / FORCE_LOGOUT / SILENT)
   which is used by `LoginViewModel` to decide between a Toast and a field-level error.
   Merging them is possible but requires `ErrorMessageResolver` to also return an `ActionType`.
   Simpler short-term path: `ErrorMessageResolver` returns only a String;
   `UserErrorMessageMapper` continues to handle action-type routing for the auth domain.

3. **Who owns the `ActivationResult.errorCode` field in `MatchesViewModel`?**
   Currently it is a `public final String` read directly by `SessionFragment`.
   For P1, should the translation happen in the ViewModel (when constructing `ActivationResult`)
   or in the Fragment (at the call site)? The architecture rule says ViewModel owns translation —
   recommend translating in the ViewModel so the Fragment never sees raw codes.

4. **Should network errors (`IOException`) be detected in `ErrorMessageResolver` or in Repositories?**
   Currently Repositories call `callback.onError(e)` for `IOException`, so `e.getMessage()` can be
   `"Unable to resolve host 'api.walkmate.com'"`. The resolver must pattern-match this.
   Alternatively, Repositories could normalise all `IOException` to a synthetic
   `new Exception("NETWORK_ERROR")` before calling `callback.onError`. This is cleaner.
   Decide before Phase 1.

5. **`VALIDATION_ERROR|<message>` — should the backend message be shown as-is?**
   Currently `CreateIntentViewModel` strips the prefix and passes the raw backend validation message
   to the user. Backend validation messages may be technical (e.g., `"timeStart: must be positive"`).
   Consider mapping the field names to friendly labels (already partially done via `ValidationErrorParser`),
   or accepting the current behaviour for validation paths.
