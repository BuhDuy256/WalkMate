# UI Gap Analysis: Frontend vs. Full Lifecycle

**Date:** 2026-04-07  
**Branch:** `implement/realtime`  
**Sources compared:**
- SSOT docs: `state-transitions.md`, `invariants.md`
- Backend gap analysis: `tasks/gap_analysis.md`
- UI: `ui/explore/**`, `ui/matches/**`
- Frontend domain models: `domain/walkintent`, `domain/walkproposal`, `domain/walksession`

---

## Legend

| Priority | Meaning |
|---|---|
| **P0 – Blocking** | Core lifecycle action is broken or calls the wrong API |
| **P1 – High** | Lifecycle state is not represented visually; user is confused or stuck |
| **P2 – Medium** | Feature from SSOT not surfaced; UX incomplete |
| **P3 – Low** | Polish, edge-case, future-proofing |

---

## P0 – Blocking: Core Actions Broken

### UI-GAP-1 · "Start Walk" launches tracking without activating the session (violates S-2)
- **SSOT S-2:** Session moves to `ACTIVE` only when both `user_a_activated_at` and `user_b_activated_at` are set — this requires `POST /sessions/{id}/activate`.
- **Code:** `SessionAdapter.bind()` → `btnStartWalk.setOnClickListener` directly starts `TrackingScreenActivity`. It skips the activate API call entirely.
- **Effect:** The session stays `PENDING` on the server forever. GPS tracking starts locally but the server never sees the session as `ACTIVE`. Partner's activation has no effect either.
- **`WalkSessionRepository`:** Has `activateSession()` — but it is never called from `SessionFragment` or `SessionAdapter`.
- **Fix needed:** SessionFragment must call `sessionRepository.activateSession()` first. Only on success (session → ACTIVE) should it launch `TrackingScreenActivity`. For a PENDING session waiting for partner, show a "Waiting for partner..." state instead.

### UI-GAP-2 · `createIntent()` passes `tags` parameter that backend doesn't accept (silent API mismatch)
- **Backend:** `CreateWalkIntentRequest` has no `tags` field. The endpoint ignores any extra JSON.
- **Frontend:** `WalkIntentRepository.createIntent()` signature includes `List<String> tags`. `CreateIntentViewModel.submit()` passes it. The ExploreFragment collects tag chip selections.
- **Effect:** Tags selected by the user are silently discarded. The `FindingAdapter` reads `intent.getTags()` and shows them, but since they are never persisted, they always come back empty.
- **Fix needed:** Remove `tags` from `createIntent()` signature OR align the backend API to accept tags. Either way, the mismatch must be resolved.

---

## P1 – High: Lifecycle State Not Represented in UI

### UI-GAP-3 · `MATCHING` status not handled in `FindingAdapter.bindStatusChip()`
- **SSOT:** After GAP-1 (backend) is fixed, intents will have `status = "MATCHING"` while a proposal is pending.
- **Code:** `bindStatusChip()` handles only `"CONSUMED"` (grey) and a generic fallback (orange). `"MATCHING"` falls into the orange default — looks identical to `"OPEN"`.
- **Worse:** The **Cancel button is always visible** regardless of status. If the user cancels during `MATCHING`, the backend must also cancel the proposal. There is no warning dialog for this.
- **Fix needed:**
  1. Add `"MATCHING"` case to `bindStatusChip()` with a distinct color (e.g. blue/pulsing).
  2. On cancel during `MATCHING`, show a dialog: "Cancelling will also withdraw your current proposal match. Continue?"

### UI-GAP-4 · No "waiting for partner" state after partial proposal acceptance (violates SSOT Note #1)
- **SSOT Note #1:** When User A accepts but User B hasn't, User A is in a "partial acceptance" limbo — the proposal stays `PENDING` but the user has already committed.
- **Code:** `MatchesViewModel.acceptProposal()` always calls `loadAll()` after success, regardless of whether the backend returned a partial (`PENDING`) or full (`CONFIRMED`) acceptance. The `WalkProposalRepository.acceptProposal()` returns `WalkSession` — but a partial acceptance returns no session (null/empty), so the reload shows the same proposal card again with the same three buttons (Pass / Accept / Cancel).
- **`WalkProposal` domain model** has no `acceptedByMe` or `waitingForPartner` field.
- **Effect:** User taps Accept, sees a loading flash, then sees the same card with the same buttons. They don't know if their tap registered.
- **Fix needed:**
  1. Add `acceptedByMe: boolean` to `WalkProposal` domain model (populated from API response field `acceptedByA/B` resolved to caller's perspective).
  2. `ProposalAdapter`: when `acceptedByMe == true`, replace Accept/Pass/Cancel with a "Waiting for partner..." indicator and a single Cancel button.

### UI-GAP-5 · No proposal expiry countdown in `ProposalAdapter` (violates P-4)
- **SSOT P-4:** Proposal TTL is 5 minutes. After the backend fix, this is hard-enforced.
- **Frontend `WalkProposal`:** No `expiresAt` field.
- **`ProposalAdapter`:** No countdown timer displayed.
- **Effect:** User sees a proposal card, does nothing for >5 minutes. Next tap (Accept/Pass) returns an error. No graceful handling exists — the error string is toasted and the card remains.
- **Fix needed:**
  1. Add `expiresAt: String` (ISO-8601) to `WalkProposal` domain model.
  2. `ProposalAdapter`: show a countdown chip (e.g. "⏱ 4:32 left"). When it hits zero, grey out the card and show "Proposal expired — refreshing..." then call `loadAll()`.

### UI-GAP-6 · No "Abort Walk" action for ACTIVE sessions
- **SSOT:** `ACTIVE → ABORTED` is a valid transition triggered by safety concern or emergency.
- **`WalkSessionRepository`:** Has `abortSession(String sessionId, String reason, DomainCallback<Void>)`.
- **Code:** `SessionAdapter` shows `btnCancelSession` for all sessions (no status guard) and `btnStartWalk` only for `PENDING`. For an `ACTIVE` session, neither the correct button set nor the abort dialog exists.
- **Effect:** Once a walk starts, the user has no in-app way to abort it (short of force-stopping tracking). The cancel button shown for ACTIVE sessions will call `cancelSession()` — which the backend will reject because `SESSION_CANCEL_NOT_PENDING`.
- **Fix needed:**
  1. `SessionAdapter.bind()`: branch by `status`.
     - `PENDING`: show Cancel + Start Walk.
     - `ACTIVE`: show Abort (with reason dialog: Safety Concern / Emergency / Other) + Complete Walk.
  2. Add `abortSession()` call path in `MatchesViewModel`.

### UI-GAP-7 · No "Complete Walk" action in the session UI
- **SSOT S-5:** Walk must be user-initiated to complete (minimum 5 min enforced server-side).
- **Backend:** `POST /sessions/{id}/complete` endpoint (to be added per backend GAP-9).
- **`WalkSessionRepository`:** No `completeSession()` method.
- **`MatchesViewModel`:** No `completeSession()` method.
- **Effect:** Users have no way to end a walk from the Matches tab. The only completion path is the backend's auto-complete scheduler (after 4 hours). This is a dead lifecycle branch from the user's perspective.
- **Fix needed:**
  1. Add `completeSession(String sessionId, DomainCallback<WalkSession>)` to `WalkSessionRepository`.
  2. Add `completeSession(String sessionId)` to `MatchesViewModel` (call `reloadSessionsAndIntents()` on success).
  3. `SessionAdapter`: show "Finish Walk" button for `ACTIVE` sessions (next to Abort).

### UI-GAP-8 · `WalkSession` domain model missing critical lifecycle fields
- **Current fields:** `sessionId, proposalId, partnerName, partnerAvatar, meetingPointLat, meetingPointLng, scheduledTime (start only), status`
- **Missing fields that the backend `WalkSessionResponse` provides:**
  - `scheduledEnd` — needed to display walk duration and enforce S-6 (4-hour limit display).
  - `userAActivatedAt` / `userBActivatedAt` — needed to show "Partner has checked in ✓" / "Waiting for partner..." on the session card.
  - `startedAt` — needed to display "Walk started X min ago" and calculate minimum 5-min guard client-side.
  - `abortReason` — needed to display reason when session is ABORTED.
  - `cancellationReason` — needed to display reason when session is CANCELLED.
- **Effect:** The session card is impoverished. Users can't see partner activation state, can't know how long the walk has been going, can't see why a session ended abnormally.

### UI-GAP-9 · No visual differentiation between PENDING and ACTIVE sessions in `SessionAdapter`
- **Code:** `SessionAdapter.bind()` only uses `status` to toggle `btnStartWalk`. No status label, color coding, or structural difference between the two states.
- **Effect:** A user with both a PENDING (waiting at meetup) and an ACTIVE (walking) session will see identical cards. There is no way to tell which session is live.
- **Fix needed:** Add a status chip/badge to the session card. `PENDING` → yellow "Waiting to meet", `ACTIVE` → green "Walk in progress", terminal states → grey.

---

## P2 – Medium: Missing Features from SSOT

### UI-GAP-10 · ExploreViewModel stays in `SCANNING` when proposal expires (violates state-transition: MATCHING → OPEN)
- **SSOT:** If proposal expires, both intents return to `OPEN` (MATCHING → OPEN transition).
- **Code:** `ExploreViewModel` moves to `SCANNING` on `onIntentCreated()`. It only exits `SCANNING` on:
  - `MATCH_FOUND` FCM event.
  - 10-second UI timeout (shows "Still looking..." dialog).
  - User taps "Stop Searching".
- **No event exists for "proposal expired → back to OPEN"**. If backend expires the proposal and returns the intent to `OPEN`, the UI stays stuck in `SCANNING` indefinitely. The 10-second timeout fires, user taps "Keep searching" — but they are effectively back at `OPEN` matching without knowing.
- **Fix needed:** Define a new FCM/AppEvent type `PROPOSAL_EXPIRED`. `ExploreViewModel` should handle it by showing a brief "No match confirmed. Still searching..." message while staying in `SCANNING` (since the intent is still `OPEN`).

### UI-GAP-11 · `WalkIntent` domain model missing `isPrivate`, `invitedFriendId`, `description` (violates I-7)
- **DB V100** and **backend GAP-10** add these fields.
- **Frontend `WalkIntent.java`:** Only has `id, hotspotId, userId, timeStart, timeEnd, ageMin, ageMax, status, createdAt, tags`.
- **Effect:** Even after the backend implements private intents, the frontend cannot create or display them.
- **Fix needed:**
  1. Add `isPrivate: boolean`, `description: String` to `WalkIntent`.
  2. `FindingAdapter`: show a "Private" lock icon badge and the description text on the card.

### UI-GAP-12 · No private intent creation UI (violates I-7)
- **SSOT I-7:** Private intents are matched only via `invited_friend_id`.
- **`ExploreFragment`** create-intent bottom sheet has no private/public toggle and no friend picker.
- **`CreateIntentViewModel.submit()`** has no `isPrivate` or `invitedFriendId` parameters.
- **Fix needed:** Add to the create-intent form:
  1. A toggle switch "Private invite only".
  2. When toggled on, show a friend picker (backed by `SocialRepository.getFriends(ACCEPTED)`).
  3. Pass `isPrivate` and `invitedFriendId` through `CreateIntentViewModel.submit()`.

### UI-GAP-13 · `MatchesViewModel.cancelSession()` comment says it "may re-open the WalkIntent" — but this never actually happens
- **Comment in `MatchesViewModel`:** "cancelling a session may re-open the WalkIntent (returning it to the Finding sub-tab)".
- **Backend reality:** `SessionCommandService.cancelSession()` sets the session to CANCELLED. It does NOT create a new intent or un-consume the old one. The CONSUMED intent stays CONSUMED.
- **Effect:** `reloadSessionsAndIntents()` is called after cancel, fetching intents — but there will be no new OPEN intent. The comment is misleading and the reload of intents is unnecessary.
- **Fix needed:** Update the comment; change `reloadSessionsAndIntents()` to `reloadSessions()` (sessions only).

### UI-GAP-14 · No real-time partner activation update in `SessionFragment`
- **SSOT S-2:** Mutual activation makes session ACTIVE.
- **Code:** `SessionFragment` only updates via `swipeRefresh` or `matchesViewModel.loadAll()`. There is no polling, no FCM handler for session state changes.
- **Effect:** User A activates at the meeting point. From User A's perspective, their session card shows PENDING and "Waiting for partner" forever until they manually swipe to refresh. User B's activation (ACTIVE transition) is invisible.
- **Fix needed:** Add FCM/AppEvent handler `SESSION_ACTIVE` → call `loadAll()` or targeted session reload in `MatchesViewModel`.

---

## P3 – Low: Polish and Edge Cases

### UI-GAP-15 · No cancel-during-MATCHING warning in `FindingFragment`
- When intent is `MATCHING`, tapping Cancel in `FindingAdapter` calls `cancelIntent()` directly with no dialog.
- The backend will cancel the intent AND must cancel the linked proposal (per SSOT). The user has no warning.
- **Fix needed:** If `intent.getStatus().equals("MATCHING")`, show: "You have a pending proposal. Cancelling will also withdraw it. Continue?"

### UI-GAP-16 · `MatchesViewModel.acceptProposal()` always does a full `loadAll()` even for partial acceptance
- Partial acceptance: backend returns the same proposal with status=PENDING (no session created).
- Full acceptance: backend returns session.
- The current code can't tell the difference — it always calls `loadAll()` with scroll-to-Session signal.
- For partial acceptance, this causes:
  - Unnecessary 3-API reload.
  - Scroll to Session tab (empty).
- **Fix needed:** Check the callback result. If `WalkSession` returned is non-null → full acceptance → `loadAll()` + scroll to Session. If null → partial → only reload proposals.

### UI-GAP-17 · `ExploreViewModel` scanning timeout is 10 seconds but SSOT proposal TTL is 5 minutes
- `TIMEOUT_MS = 10_000` is the UI "still looking" signal (shown as dialog).
- This is separate from the proposal TTL — these are two different things and the constant name / value is confusing.
- 10 seconds is also probably too short in production (network latency alone can exceed this).
- **Fix needed:** Rename `TIMEOUT_MS` to `SCANNING_NUDGE_DELAY_MS`. Consider a longer value like 30–60 seconds for production.

---

## Summary Table

| Gap | SSOT Ref | Priority | Area |
|-----|----------|----------|------|
| UI-GAP-1: Start Walk skips activateSession() | S-2 | **P0** | SessionAdapter |
| UI-GAP-2: createIntent() sends tags that backend ignores | API contract | **P0** | CreateIntentViewModel / Repository |
| UI-GAP-3: MATCHING status not styled; cancel during MATCHING has no warning | I-4 | **P1** | FindingAdapter |
| UI-GAP-4: No "waiting for partner" after partial acceptance | SSOT Note #1 | **P1** | WalkProposal model, ProposalAdapter, MatchesViewModel |
| UI-GAP-5: No proposal expiry countdown | P-4 | **P1** | WalkProposal model, ProposalAdapter |
| UI-GAP-6: No Abort Walk action for ACTIVE sessions | S-3 (state machine) | **P1** | SessionAdapter, WalkSessionRepository, MatchesViewModel |
| UI-GAP-7: No Complete Walk action | S-5 | **P1** | WalkSessionRepository, MatchesViewModel, SessionAdapter |
| UI-GAP-8: WalkSession missing scheduledEnd, activatedAt, startedAt, reasons | S-2, S-4, S-6 | **P1** | WalkSession domain model |
| UI-GAP-9: No visual diff between PENDING/ACTIVE session cards | state-transitions | **P1** | SessionAdapter |
| UI-GAP-10: SCANNING state stuck when proposal expires | MATCHING → OPEN | **P2** | ExploreViewModel, AppEvent |
| UI-GAP-11: WalkIntent missing isPrivate, description fields | I-7 | **P2** | WalkIntent model, FindingAdapter |
| UI-GAP-12: No private intent creation UI | I-7 | **P2** | ExploreFragment, CreateIntentViewModel |
| UI-GAP-13: cancelSession reload comment/logic wrong | — | **P2** | MatchesViewModel |
| UI-GAP-14: No real-time partner activation update | S-2 | **P2** | SessionFragment, MatchesViewModel |
| UI-GAP-15: No cancel-during-MATCHING warning | I-4 | **P3** | FindingFragment, FindingAdapter |
| UI-GAP-16: acceptProposal() always full-reloads even for partial | SSOT Note #1 | **P3** | MatchesViewModel |
| UI-GAP-17: Scanning timeout constant is misleadingly named/valued | P-4 | **P3** | ExploreViewModel |

---

## Files That Need Changes

| File | Gaps |
|---|---|
| `domain/walksession/WalkSession.java` | UI-GAP-8 |
| `domain/walkproposal/WalkProposal.java` | UI-GAP-4, UI-GAP-5 |
| `domain/walkintent/WalkIntent.java` | UI-GAP-11 |
| `domain/walksession/WalkSessionRepository.java` | UI-GAP-1, UI-GAP-7 |
| `domain/walkintent/WalkIntentRepository.java` | UI-GAP-2 |
| `ui/matches/MatchesViewModel.java` | UI-GAP-6, UI-GAP-7, UI-GAP-13, UI-GAP-16 |
| `ui/matches/session/SessionAdapter.java` | UI-GAP-1, UI-GAP-6, UI-GAP-7, UI-GAP-9 |
| `ui/matches/session/SessionFragment.java` | UI-GAP-1, UI-GAP-14 |
| `ui/matches/proposal/ProposalAdapter.java` | UI-GAP-4, UI-GAP-5 |
| `ui/matches/finding/FindingAdapter.java` | UI-GAP-3, UI-GAP-15 |
| `ui/explore/ExploreViewModel.java` | UI-GAP-10, UI-GAP-17 |
| `ui/explore/createintent/CreateIntentViewModel.java` | UI-GAP-2, UI-GAP-12 |
| `ui/explore/ExploreFragment.java` | UI-GAP-12 |
| New: AppEvent type `PROPOSAL_EXPIRED` | UI-GAP-10 |
