# WalkMate Frontend — Gap Analysis
**Date:** 2026-04-08
**Branch:** `implement/realtime`
**Reference Spec:** `docs/dev/ui-refactor/backend_use_cases.md`
**Scope:** Intent, Proposal, Session, GPS Path Tracking, Home Page (Hotspot Map), Profile Page.
**Out of Scope:** UC-01 (Register), UC-02 (Login), UC-06 (FCM Token), Social (UC-26–31), Notifications (UC-32–33), Gamification (UC-34–36).

---

## Legend

| Severity | Meaning |
|---|---|
| 🔴 CRITICAL | Blocks correct data flow; the feature cannot function without a fix. |
| 🟠 HIGH | Missing required behaviour or invariant; spec mandates it explicitly. |
| 🟡 MEDIUM | Wrong/stubbed data that will mislead the user; deviates from spec. |
| 🟢 LOW | Minor mismatch or hardcoded value that should be wired to real data. |

---

## Feature 1 — Home Page / Hotspot Map (UC-07)

### 1.1 🟡 Hotspot data never fetched; count is hardcoded
**File:** `ui/home/HomeViewModel.java:171`
`HomeViewModel.buildReadyState()` passes `nearbyHotspotCount = 5` as a hardcoded integer literal. `HotspotRepository` is not injected into `HomeViewModel` and `GET /api/v1/hotspots` is never called from the Home screen. The spec (UC-07) requires the pin count and the live `openIntentCount` per hotspot to drive visual weight on the map.

### 1.2 🟡 Home dashboard stats are entirely mocked
**File:** `ui/home/HomeViewModel.java:169–176`
The following fields are hardcoded:
- `streakDays = 5`, `streakGoal = 7` — no backend endpoint for streaks yet, but comment should reflect this explicitly.
- `weeklyDistanceKm = 12.5`, `weeklySessionCount = 3` — should come from `GET /api/v1/users/{userId}/stats` (UC-35 data). `GamificationRepository` is not used here.
- `locationName = "Ho Chi Minh City"` — should be resolved from the device's last known location.

### 1.3 🟡 Quick-invite list is mock data
**File:** `ui/home/HomeViewModel.java:179–186`
`buildMockInviteList()` returns five hardcoded `QuickInviteUser` objects ("Minh", "Sarah", etc.) instead of calling `GET /api/v1/users/me/friends` via `SocialRepository`. The API surface (`SocialApiService.getFriends()`) already exists.

### 1.4 🟢 No refresh-on-focus for hotspot list
**Spec (UC-07):** "Refresh hotspot list every time the screen gains focus or the user pulls to refresh."
`HomeFragment` and `ExploreFragment` do not re-fetch hotspots on `onResume()`.

### 1.5 🟢 Pin visual weight not implemented
**Spec (UC-07):** "Each pin's visual weight (size or color) reflects `openIntentCount`."
`Hotspot` domain model has `openIntentCount` and `HotspotRepository` returns it, but there is no map-rendering code that adjusts pin size/color based on this field.

---

## Feature 2 — Profile Page (UC-03, 04, 05)

### 2.1 🟠 Badges and stats never loaded on profile screen
**File:** `ui/profile/ProfileViewModel.java:124`
`toUiState()` passes `Collections.emptyList()` for badges and `0` for `currentStreak`. The spec (UC-03) states: "Optionally trigger parallel fetch of `GET /api/v1/users/{userId}/badges` and `GET /api/v1/users/{userId}/stats`." `GamificationRepository` is not injected into `ProfileViewModel`; the two calls are never made.

### 2.2 🟡 `isOnline` hardcoded to `true`
**File:** `ui/profile/ProfileViewModel.java:118`
Online/presence status is hardcoded. No presence endpoint exists yet, but the hardcoded `true` is misleading for all offline users.

### 2.3 🟠 Edit Profile screen does not exist (UC-04)
`ProfileViewModel.saveProfile()` is implemented and calls `PUT /api/v1/profile/me`, but there is no Edit Profile Fragment or Activity. Navigation signal `onSettingsClicked()` / `onMyBadgesClicked()` / `onWalkHistoryClicked()` are no-ops in the VM. The edit flow cannot be reached from the UI.

### 2.4 🟠 Avatar upload screen does not exist (UC-05)
`ProfileViewModel.uploadAvatar()` is implemented and calls `POST /api/v1/profile/avatar`, but no image-picker UI or dedicated screen exists.

### 2.5 🟢 Reviews not shown on profile
**Spec (UC-03 / UC-26):** Profile should show a review feed via `GET /api/v1/users/{userId}/reviews`. `ReviewApiService.getReviewsForUser()` exists but is not wired to the Profile screen.

---

## Feature 3 — Walk Intent (UC-08, 09, 10, 11)

### 3.1 🟠 `CreateWalkIntentRequest` missing `description` field (UC-08)
**File:** `data/datasource/remote/dto/request/walkintent/CreateWalkIntentRequest.java`
The spec (UC-08 step 1) lists "Optional: Description" as a user-fillable field sent in the request body. The DTO has no `description` field; it cannot be submitted.

### 3.2 🟠 `WalkIntent` domain model missing `expiresAt` field (UC-09)
**File:** `domain/walkintent/WalkIntent.java`
The spec (UC-09 step 3) requires showing an `expires_at` countdown timer on each intent card. `WalkIntentResponse.getExpiresAt()` exists in the DTO but `WalkIntentMapper.toDomain()` silently drops it; the domain model has no `expiresAt` field.

### 3.3 🟠 `WalkIntent` domain model missing `description` field
**File:** `domain/walkintent/WalkIntent.java`
Parallel to gap 3.1 — the domain object cannot carry description text even if the DTO were fixed.

### 3.4 🟡 `WalkIntentMapper.toRequest()` hardcodes today's date
**File:** `data/mapper/WalkIntentMapper.java:46`
The static factory `toRequest()` calls `LocalDate.now().toString()`, ignoring the `date` parameter the user should provide (UC-08 step 1). Note: `CreateIntentViewModel.submit()` passes `date` through `WalkIntentRepository.createIntent()`, so the repository implementation must not use this mapper factory path — but the mapper still contains a broken convenience method that could cause future misuse.

### 3.5 🟠 MATCHING intent state not differentiated in Finding tab (UC-09)
**Spec (UC-09 step 5):** For `MATCHING` intents, show "View Proposal" button and disable "Cancel"; display a lock icon (invariant **I-4**). The `WalkIntent.status` field is a raw `String`, and there is no evidence in `FindingAdapter` or `FindingFragment` that MATCHING vs OPEN intents render differently.

### 3.6 🟠 `findMatch()` not exposed in `MatchesViewModel` (UC-11)
`WalkIntentRepository.findMatch()` exists and `WalkIntentApiService.findMatch()` exists, but `MatchesViewModel` does not expose a `triggerMatch(intentId)` method. There is no "Find Match" button wiring visible in the `MatchesViewModel` API. UC-11 requires a user-triggered match call with Case A / Case B response handling (200 vs 204 No Content).

### 3.7 🟠 204 No Content response not handled for `findMatch` (UC-11)
**File:** `data/datasource/remote/api/WalkIntentApiService.java:27`
`findMatch()` is declared as `Call<ApiResponse<WalkProposalResponse>>`. A 204 response has no body; Retrofit will return `null` body, not a typed error. The repository and VM must explicitly handle `response.code() == 204` as "No Match Yet" (Case B) rather than treating it as a failure.

---

## Feature 4 — Proposal Negotiation (UC-12, 13, 14, 15)

### 4.1 🔴 `WalkProposal` domain model missing critical fields (UC-12, 13)
**File:** `domain/walkproposal/WalkProposal.java`
The following fields present in `WalkProposalResponse` are dropped by `WalkProposalMapper.toDomain()` and not in the domain model:

| Missing Field | Spec Requirement |
|---|---|
| `expiresAt` | UC-12: 5-minute TTL countdown timer (invariant **P-4**) |
| `meetingLat` / `meetingLng` | UC-12: show proposed meeting coordinates |
| `myAcceptanceStatus` | UC-13: distinguish Case A (partial) from Case B (confirmed) |
| `partnerAvatarUrl` | UC-12: fetch/show partner avatar (via `GET /api/v1/users/{matchedUserId}`) |
| `hotspotName` | UC-12: show proposed walk context |

### 4.2 🔴 `MatchesViewModel.acceptProposal()` does not distinguish Case A vs Case B (UC-13)
**File:** `ui/matches/MatchesViewModel.java:261`
`acceptProposal()` calls `proposalRepository.acceptProposal()` and on any success immediately calls `loadAll()` then scrolls to the Session tab. The spec (UC-13) defines two distinct outcomes:
- **Case A (status: "PENDING"):** Partner has not yet accepted — UI must show waiting state, keep countdown visible, disable both buttons.
- **Case B (status: "CONFIRMED", `session_id` non-null):** Both accepted — show celebration animation, navigate to Session Detail.

The current implementation always proceeds to Case B behaviour, causing premature navigation to the Session tab even on a partial acceptance.

### 4.3 🔴 Session navigation before `session_id` is confirmed (UC-13)
**Spec (UC-13 Required UI navigation):** "Do not navigate to the Session Detail screen before `session_id` is non-null in the response."
The current flow navigates unconditionally. On Case A, `session_id` is `null` in the `WalkProposalResponse`; navigating to a Session screen at this point will fail or show stale data.

### 4.4 🟠 No 5-minute countdown timer on proposal cards (UC-12)
`WalkProposal.expiresAt` does not exist (gap 4.1), so no countdown can be rendered. The spec (UC-12 step 3, invariant **P-4**) requires a live countdown; hitting zero must trigger a list refresh.

### 4.5 🟠 Partner profile not fetched for proposal display (UC-12)
**Spec (UC-12 step 3):** "Partner's name and avatar (fetch from `GET /api/v1/users/{matchedUserId}`)."
`WalkProposalMapper` uses `matchedUserId` as a name placeholder and passes `null` for avatar. `UserProfileApiService.getPublicProfile()` is available but never called in the proposal flow.

### 4.6 🟠 Chat integration absent after proposal confirmation (UC-13, S-7)
**File:** `ui/matches/session/SessionFragment.java:53–56`
The chat click listener shows a `Toast` with `R.string.session_chat_coming_soon`. The spec (UC-13 Case B step 3) requires a **Chat button** that opens a WebSocket/Chat UI scoped to `session_id`, enabled from CONFIRMED through any terminal state. No WebSocket client, no chat Fragment, and no chat Activity exist.

### 4.7 🟡 No celebration animation on mutual acceptance (UC-13 Case B)
**Spec (UC-13 Required UI navigation step 1):** "Show a brief celebration animation (e.g., confetti overlay)."
Not implemented.

---

## Feature 5 — Session Lifecycle (UC-16, 17, 18, 19, 20)

### 5.1 🔴 Activation endpoint bypassed entirely (UC-17, invariant S-3)
**File:** `ui/matches/session/SessionFragment.java:59–65`
The "Start Walk" click handler directly launches `TrackingScreenActivity` without calling `POST /api/v1/sessions/{sessionId}/activate`. This means:
1. The backend never records `user_a_activated_at` / `user_b_activated_at`.
2. The session never transitions `PENDING → ACTIVE` on the server.
3. GPS tracking begins against a session that is still `PENDING`, causing every `POST /api/v1/tracking/sync` call to potentially return `SESSION_NOT_ACTIVE`.

### 5.2 🔴 Activation window not enforced (UC-17, invariant S-3)
**Spec (S-3):** `"I'm Here!" button enabled only within [scheduledStart − 10 min, scheduledStart + 15 min]`.
There are no constants `ACTIVATION_WINDOW_BEFORE` / `ACTIVATION_WINDOW_AFTER` defined anywhere in the codebase. The button is not conditionally enabled. The `WalkSession` domain model has only `scheduledTime` (single field) — it lacks `scheduledEnd` — and neither field is used to compute or enforce the window.

### 5.3 🔴 `SessionApiService` missing `complete` endpoint (UC-19)
**File:** `data/datasource/remote/api/SessionApiService.java`
`POST /api/v1/sessions/{sessionId}/complete` is not declared. The method is also absent from `WalkSessionRepository` interface and `WalkSessionRepositoryImpl`. `TrackingViewModel.finishWalk()` fires locally without calling the backend.

### 5.4 🔴 `WalkSession` domain model missing backend-state fields (UC-16, 17, 19)
**File:** `domain/walksession/WalkSession.java`
`WalkSessionMapper.toDomain()` drops the following fields from `WalkSessionResponse`:

| Missing Field | Required For |
|---|---|
| `scheduledEnd` | Activation window calculation (S-3) |
| `startedAt` | Walk timer origin (UC-17 Case B, S-5 minimum check) |
| `endedAt` | Terminal-state display |
| `userAActivatedAt` / `userBActivatedAt` | Show partial activation state (UC-17 Case A) |
| `isReviewed` | Show/hide review prompt (UC-22, UC-24) |

### 5.5 🟠 5-minute minimum walk not enforced client-side (UC-19, invariant S-5)
**Spec (UC-19):** "Complete Walk button enabled only after 5 minutes of walking." There is no 5-minute gate on any "Complete" button in `TrackingViewModel` or `TrackingScreenActivity`. The spec also says: "Disable 'Complete' button with a countdown to when it becomes enabled."

### 5.6 🟠 Session polling not implemented (UC-16, UC-17)
**Spec (UC-16):** "Poll `GET /api/v1/sessions/active` every 30 seconds."
**Spec (UC-17 Case A):** "Poll every 15 seconds after partial activation to detect partner's arrival."
Neither `SessionFragment` nor any ViewModel schedules periodic polling.

### 5.7 🟠 Abort reason not constrained to enum (UC-20)
**Spec (UC-20 step 3):** reason must be one of `"SAFETY_CONCERN" | "EMERGENCY" | "PARTNER_MISCONDUCT" | "OTHER"`. The `AbortWalkSessionRequest` uses a free-text `reason` String. No enum or validation enforces the four allowed values.

### 5.8 🟡 `WalkSession.partnerName` always resolves to partner's `userId` string (UC-16)
**File:** `data/mapper/WalkSessionMapper.java:30`
The mapper comments: "name not yet in API — use partner userId as placeholder." `GET /api/v1/users/{partnerId}` must be called to resolve the display name. The spec (UC-16 step 3) requires showing "Partner's name/avatar."

### 5.9 🟢 FCM `SESSION_ACTIVE` event not handled to auto-navigate to active view (UC-16, 17)
**Spec (UC-16):** "Listen for FCM `SESSION_ACTIVE` to detect when partner activates."
`WalkMateFcmService.java` exists but session state transitions driven by FCM are not wired to UI navigation.

---

## Feature 6 — GPS Path Tracking (UC-21)

### 6.1 🟠 No time-based 30-second periodic sync (UC-21)
**File:** `data/repository/TrackingRepositoryImpl.java:49`
Sync is only triggered when `BATCH_SIZE_THRESHOLD = 50` unsynced points accumulate. At a 3-second fix interval, this means forced sync occurs at ~150 seconds minimum — well beyond the spec's 30-second requirement. There is no `ScheduledExecutorService` or `Handler` for a time-based flush in `WalkTrackerService` or `SessionTrackingService`.

### 6.2 🟠 `PushRoutePointsResponse` missing `acknowledged_ids` field (UC-21)
**File:** `data/datasource/remote/dto/response/tracking/PushRoutePointsResponse.java`
The spec (UC-21 step 4) states the backend returns `{ "acknowledged_ids": [1, 2, 3, ...] }`. The current DTO only has `syncedCount: int`. The repository cannot perform selective acknowledgement — it marks ALL submitted points as synced regardless of which IDs the server actually confirmed. If the server acknowledges a partial batch, unacknowledged points will be silently dropped.

### 6.3 🟠 GPS sync does not stop on session terminal state (UC-21)
**Spec (UC-21):** "The sync must stop when the session reaches any terminal state (`COMPLETED`, `ABORTED`, `CANCELLED`, `NO_SHOW`)."
`TrackingRepositoryImpl.triggerBatchSync()` and `WalkTrackerService` do not check session status. There is no handling of the `SESSION_NOT_ACTIVE` error code from `pushRoutePoints` to stop the sync loop and navigate to History (UC-21 error table).

### 6.4 🟠 `TrackingViewModel.finishWalk()` not wired to session completion (UC-19, UC-21)
**File:** `ui/tracking/TrackingViewModel.java:203`
`finishWalk()` only sets local state to `FINISHED` and stops the GPS service. It does not call `POST /api/v1/sessions/{sessionId}/complete`. The GPS sync loop is stopped by killing the service but no terminal acknowledgement is sent to the backend.

### 6.5 🟡 Local `WalkState` enum includes `PAUSED` — not in backend session lifecycle
**File:** `domain/tracking/WalkState.java`
The domain `WalkState` enum has `PAUSED`. This state has no equivalent in the backend session lifecycle (`PENDING / ACTIVE / COMPLETED / CANCELLED / ABORTED / NO_SHOW`). Pausing GPS locally while the backend session remains `ACTIVE` means the server may auto-complete the session after 4 hours (invariant **S-6**) even if the user "paused." The spec does not recognise PAUSED as a valid session state.

### 6.6 🟢 GPS fix interval is 3 seconds vs spec's 5 seconds
**File:** `service/WalkTrackerService.java:72`
`LOCATION_INTERVAL_MS = 3_000L`. Spec (UC-21 step 1) says "every 5 seconds." Minor mismatch; higher frequency drains battery faster.

---

## Feature 7 — Post-Session (UC-22, 23, 24, 25)

### 7.1 🔴 `SessionApiService` missing `GET /api/v1/sessions/history` (UC-22)
No method for session history exists in `SessionApiService`, `WalkSessionRepository`, or `WalkSessionRepositoryImpl`. No Session History Fragment or Activity exists. UC-22 is entirely unimplemented.

### 7.2 🔴 `SessionApiService` missing `GET /api/v1/sessions/{sessionId}/route` (UC-23)
No route-replay endpoint exists in any API service, repository interface, or repository implementation. No Route Replay screen exists. UC-23 is entirely unimplemented.

### 7.3 🔴 `SessionApiService` missing `POST /api/v1/sessions/{sessionId}/report` (UC-25)
No incident report endpoint exists. No Incident Report form or screen exists. UC-25 is entirely unimplemented.

### 7.4 🟠 Post-session summary screen does not exist (UC-19 step 5)
**Spec (UC-19 step 5):** "UI navigates to a 'Walk Completed!' summary screen showing total distance, duration, and partner's name." `PostSessionSummaryViewModel` class exists but has no associated Fragment or Activity. The summary screen for steps 5–7 of UC-19 (distance, duration, review prompt) cannot be reached.

### 7.5 🟠 `ReviewViewModel` not reachable from any active UI flow (UC-24)
`ReviewViewModel` and `ReviewApiService.submitReview()` exist and are correctly scoped to `POST /api/v1/sessions/{sessionId}/review`, but there is no Fragment, bottom sheet, or dialog that presents the star-rating form. The review prompt "navigates to UC-24 flow" in UC-19 step 6 has no destination.

### 7.6 🟠 `isReviewed` field not used to show/hide review prompt (UC-22, UC-24)
`WalkSessionResponse.isReviewed()` exists and is correctly mapped in the DTO, but it is dropped by `WalkSessionMapper` (gap 5.4). Even if the History screen were built, the "already reviewed" suppression rule (UC-24: "Hide the 'Review' button after successful submission") cannot be enforced.

---

## Summary Table

| ID | Feature | Severity | Gap |
|---|---|---|---|
| 1.1 | Home/Map | 🟡 | Hotspot count hardcoded; `HotspotRepository` not used |
| 1.2 | Home/Map | 🟡 | Stats/streak/location are mock values |
| 1.3 | Home/Map | 🟡 | Quick-invite list is hardcoded |
| 1.4 | Home/Map | 🟢 | No refresh-on-focus for hotspot list |
| 1.5 | Home/Map | 🟢 | Pin visual weight not implemented |
| 2.1 | Profile | 🟠 | Badges and stats not loaded on profile |
| 2.2 | Profile | 🟡 | `isOnline` hardcoded `true` |
| 2.3 | Profile | 🟠 | Edit Profile screen does not exist |
| 2.4 | Profile | 🟠 | Avatar upload screen does not exist |
| 2.5 | Profile | 🟢 | Reviews not shown on profile |
| 3.1 | Intent | 🟠 | `CreateWalkIntentRequest` missing `description` |
| 3.2 | Intent | 🟠 | `WalkIntent` missing `expiresAt`; countdown not possible |
| 3.3 | Intent | 🟠 | `WalkIntent` missing `description` field |
| 3.4 | Intent | 🟡 | Mapper `toRequest()` hardcodes today's date |
| 3.5 | Intent | 🟠 | MATCHING vs OPEN states not differentiated in UI |
| 3.6 | Intent | 🟠 | `findMatch()` not exposed in `MatchesViewModel` |
| 3.7 | Intent | 🟠 | 204 No Content not handled for findMatch |
| 4.1 | Proposal | 🔴 | `WalkProposal` missing `expiresAt`, `meetingLat/Lng`, `myAcceptanceStatus`, `partnerAvatarUrl` |
| 4.2 | Proposal | 🔴 | `acceptProposal()` doesn't distinguish Case A vs Case B |
| 4.3 | Proposal | 🔴 | Navigation to Session screen before `session_id` confirmed |
| 4.4 | Proposal | 🟠 | No 5-minute countdown timer on proposals |
| 4.5 | Proposal | 🟠 | Partner profile not fetched for proposal display |
| 4.6 | Proposal | 🟠 | Chat integration entirely absent |
| 4.7 | Proposal | 🟡 | No celebration animation on mutual acceptance |
| 5.1 | Session | 🔴 | `POST /activate` bypassed; `TrackingScreenActivity` launched directly |
| 5.2 | Session | 🔴 | Activation window (S-3) not enforced |
| 5.3 | Session | 🔴 | `SessionApiService` missing `complete` endpoint |
| 5.4 | Session | 🔴 | `WalkSession` missing `scheduledEnd`, `startedAt`, `endedAt`, activation timestamps, `isReviewed` |
| 5.5 | Session | 🟠 | 5-minute minimum walk not enforced (S-5) |
| 5.6 | Session | 🟠 | Session polling not implemented (S-3 window, UC-16 30s) |
| 5.7 | Session | 🟠 | Abort reason not constrained to enum |
| 5.8 | Session | 🟡 | `partnerName` always resolves to raw userId |
| 5.9 | Session | 🟢 | FCM `SESSION_ACTIVE` not handled for auto-navigation |
| 6.1 | GPS | 🟠 | No 30-second periodic sync; only 50-point threshold |
| 6.2 | GPS | 🟠 | `PushRoutePointsResponse` missing `acknowledged_ids`; all points blindly marked synced |
| 6.3 | GPS | 🟠 | Sync loop not stopped on terminal session state |
| 6.4 | GPS | 🟠 | `finishWalk()` not wired to `POST /complete` |
| 6.5 | GPS | 🟡 | `PAUSED` WalkState has no backend equivalent; may trigger S-6 auto-close |
| 6.6 | GPS | 🟢 | Fix interval 3s vs spec 5s |
| 7.1 | Post-Session | 🔴 | `GET /sessions/history` missing from all layers; UC-22 unimplemented |
| 7.2 | Post-Session | 🔴 | `GET /sessions/{id}/route` missing; UC-23 unimplemented |
| 7.3 | Post-Session | 🔴 | `POST /sessions/{id}/report` missing; UC-25 unimplemented |
| 7.4 | Post-Session | 🟠 | Post-session summary screen does not exist |
| 7.5 | Post-Session | 🟠 | `ReviewViewModel` not reachable from any UI |
| 7.6 | Post-Session | 🟠 | `isReviewed` dropped in mapper; review suppression impossible |

**Total: 8 🔴 CRITICAL, 22 🟠 HIGH, 9 🟡 MEDIUM, 5 🟢 LOW = 44 gaps**
