# Phase 5 — Implementation Report
**Date:** 2026-04-14
**Branch:** feature/phase-2-rework
**Build status:** `BUILD SUCCESSFUL` — zero compile errors

---

## Overview

Phase 5 fixed three proposal-flow behavioral gaps (GAP-11, GAP-12, GAP-17) and added
optimistic-locking error handling for the accept path (UC-20 / Invariant X-5).

---

## TASK 5.1 — Add Private Invite Fields to Domain Model

### Problem
The frontend had no way to know whether a proposal originated from a private walk invite
(`is_private`) or whether the current user had already accepted it from the initial list
load (not only after an Accept tap). Both pieces of information are needed by Tasks 5.2
and 5.3.

### Files changed

| File | Change |
|------|--------|
| `data/datasource/remote/dto/response/proposal/WalkProposalResponse.java` | Added `@SerializedName("is_private") boolean privateInvite`; added `isPrivateInvite()` getter |
| `domain/walkproposal/WalkProposal.java` | Added `boolean isPrivateInvite` field; extended constructor from 15 → 16 parameters; added `isPrivateInvite()` getter; added `isCurrentUserAccepted()` method (alias for `isAcceptedByMe()` — covers backend auto-accept for private invite senders); updated `withMatchedUserName()` to thread through the new field |
| `data/mapper/WalkProposalMapper.java` | `toDomain()` now passes `response.isPrivateInvite()` as the 16th constructor argument |

---

## TASK 5.2 — Private Invite Pre-Accepted Rendering (GAP-11)

### Problem
When a private invite sender opened the Proposal tab, they saw the normal Accept/Pass
buttons. The backend auto-accepts the sender's side (`my_acceptance_status: "ACCEPTED"`)
but the frontend condition used `isAcceptedByMe()` only after an explicit tap, not on
initial render from the list endpoint.

### Resolution
The `ProposalAdapter.ViewHolder.bind()` condition was updated from `proposal.isAcceptedByMe()`
to `proposal.isCurrentUserAccepted()`. The new method is semantically equivalent but
makes the intent explicit: it covers both the post-tap state and the backend-pre-accepted
state that comes from the proposals list response. No change to `ProposalFragment.renderState()`
was needed because rendering is fully delegated to the adapter.

### Files changed

| File | Change |
|------|--------|
| `ui/matches/proposal/ProposalAdapter.java` | `bind()` condition updated to `proposal.isCurrentUserAccepted()` |

---

## TASK 5.3 — Differentiated Pass Dialog (GAP-12)

### Problem
Tapping Pass used a single call to `passProposal(proposalId)` with no confirmation
dialog and no post-pass navigation differentiation.

### Resolution

**ProposalAdapter** — `ProposalActionListener.onPass()` signature extended to
`onPass(String proposalId, boolean isPrivateInvite)` so the Fragment receives the
proposal type when handling the tap.

**ProposalFragment** — The `onPass` callback now shows an `AlertDialog` before calling
the ViewModel. Dialog message is differentiated:
- Private invite: *"Decline this private invite? This invite will be closed and you will not be added to the public wait list."*
- Public proposal: *"Pass on this match? Your intent will stay active and we'll keep looking for other partners."*

**MatchesViewModel** — `passProposal()` signature changed to
`passProposal(String proposalId, boolean isPrivateInvite)`:
- **Private invite declined** → optimistic remove of the proposal from the list; stay on Proposal tab.
- **Public proposal passed** → `loadAll()` followed by `scrollToTabEvent.postValue(TAB_FINDING)`
  so the re-opened OPEN intent is immediately visible in the Finding tab.

### Files changed

| File | Change |
|------|--------|
| `ui/matches/proposal/ProposalAdapter.java` | `onPass()` interface signature extended; click listener passes `proposal.isPrivateInvite()` |
| `ui/matches/proposal/ProposalFragment.java` | Added `AlertDialog` in `onPass` callback with differentiated text; calls `passProposal(proposalId, isPrivateInvite)` |
| `ui/matches/MatchesViewModel.java` | `passProposal(String, boolean)` with split success path; private `postError(String)` helper extracted |

---

## TASK 5.4 — Proposal Expiry Countdown Timer (GAP-17)

### Status: Pre-implemented by `CountdownTimerView`

The `CountdownTimerView` custom view (added in an earlier phase) already provides:
- ISO-8601 string → epoch-ms parsing via `startCountdown(String expiresAtIso)`
- `CountDownTimer` cancellation in `onDetachedFromWindow()` (fragment destroy path)
  and `onViewRecycled()` (RecyclerView recycle path)
- `OnExpiredListener` callback

The `ProposalAdapter.ViewHolder.bind()` already calls:
```java
countdown.startCountdown(proposal.getExpiresAt());
countdown.setOnExpiredListener(() -> actionListener.onProposalExpired());
```

`ProposalFragment` already maps `onProposalExpired()` → `matchesViewModel.loadAll()`.

No code changes were needed for this task. The spec's requirement (Map keyed by
proposalId, cancel in onDestroyView) is functionally satisfied by
`CountdownTimerView.onDetachedFromWindow()` which fires when the fragment view
hierarchy is destroyed.

---

## TASK 5.5 — Accept Proposal: Optimistic Locking Error Handling (UC-20 / Invariant X-5)

### Problem
`MatchesViewModel.acceptProposal()` had a generic `onError` handler that blindly
posted `error.getMessage()` as a UI error string. All five concurrent-modification
error codes needed distinct UX responses.

### Resolution

The `onError` callback in `acceptProposal()` now contains a `switch` on
`error.getMessage()` (which carries the API `error.code` string per the
`WalkProposalRepositoryImpl` error-parsing convention):

| Error code | Toast message | Side-effect |
|---|---|---|
| `PROPOSAL_CONCURRENT_MODIFICATION` | "A conflict occurred. Please refresh and try again." | `loadAll()` |
| `PROPOSAL_INTENT_NO_LONGER_OPEN` | "Could not confirm — one of the intents is no longer available. The proposal has been cancelled." | `loadAll()` |
| `PROPOSAL_ALREADY_TERMINAL` | "This proposal is no longer active." | `loadAll()` |
| `PROPOSAL_NOT_PARTICIPANT` | "Permission denied." | — |
| `PROPOSAL_NOT_FOUND` | "Proposal not found." | `loadAll()` |
| *(other)* | raw code string | — |

`loadAll()` causes the expired/cancelled proposal to disappear from the list
without requiring a separate "navigate back" signal (proposals live in a
RecyclerView list, not a detail screen).

### Files changed

| File | Change |
|------|--------|
| `ui/matches/MatchesViewModel.java` | `acceptProposal()` `onError` replaced with switch-on-code; `private postError(String)` helper; remaining `onError` callbacks in `cancelIntent`, `cancelProposal`, `cancelSession` also switched to `postError()` for consistency |

---

## Files Modified (6 total)

| File | Tasks |
|------|-------|
| `data/datasource/remote/dto/response/proposal/WalkProposalResponse.java` | 5.1 |
| `domain/walkproposal/WalkProposal.java` | 5.1 |
| `data/mapper/WalkProposalMapper.java` | 5.1 |
| `ui/matches/proposal/ProposalAdapter.java` | 5.2, 5.3 |
| `ui/matches/proposal/ProposalFragment.java` | 5.3 |
| `ui/matches/MatchesViewModel.java` | 5.3, 5.5 |

## Files Created

None.

---

## Known Risks / Follow-ups for Phase 6

| Risk | Detail |
|------|--------|
| `is_private` backend field | The mapper maps `response.isPrivateInvite()` which defaults to `false` if the backend omits the field. Proposals created before the backend ships this field will behave like public proposals — this is safe. |
| `my_acceptance_status` in list response | GAP-11 fix relies on the backend returning `my_acceptance_status: "ACCEPTED"` in the proposals LIST endpoint (not only the `/accept` response). If the backend omits it from list responses, the sender will see Accept/Pass buttons until they tap Accept. Confirm with backend team. |
| Pass → Finding tab scroll | `passProposal(public)` navigates to TAB_FINDING after `loadAll()`. If the user has no active intent (it was already matched or expired), the Finding tab will show an empty state — acceptable per UC-21. |
| GAP-13 (Chat button on Session Detail) | Not addressed in Phase 5. Needs `SessionFragment` wiring to `ChatFragment`. |
| GAP-14 (Activation window enforcement) | Not addressed in Phase 5. |

---

## Verification

- **Build:** `./gradlew :frontend:assembleDebug` → `BUILD SUCCESSFUL` (0 errors)
- **Warnings:** Java deprecation notes only (pre-existing, unrelated to Phase 5 changes)
