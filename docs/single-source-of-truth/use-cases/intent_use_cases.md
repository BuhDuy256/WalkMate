# WalkMate — Walk Intent Use Cases

> Part of: [Use Cases Index](README.md)

**Domain:** Walk Intent Creation and Management
**Last Updated:** 2026-04-13

---

## Table of Contents

| UC# | Use Case | API Endpoint |
|-----|----------|--------------|
| UC-15 | [Create Walk Intent](#uc-15--create-walk-intent) | `POST /api/v1/intents` |
| UC-16 | [View My Active Intents](#uc-16--view-my-active-intents) | `GET /api/v1/intents` |
| UC-17 | [Cancel Walk Intent](#uc-17--cancel-walk-intent) | `DELETE /api/v1/intents/{intentId}` |
| UC-18 | [Trigger Match (Internal API)](#uc-18--trigger-match) | `POST /api/v1/intents/{intentId}/match` |

---

### UC-15 — Create Walk Intent

**Use Case Name:** Create Walk Intent

**Initial assumption:** User is authenticated. User has selected a hotspot from UC-14. User is on the "Create Intent" form screen. The user currently has NO overlapping `OPEN`/`MATCHING` intent or `PENDING`/`ACTIVE` session in the chosen time window (invariant **I-1**). If token expired, app must force re-login before this flow.

**Normal:**
1. User fills in:
   - Date (`date`: YYYY-MM-DD)
   - Start time (`time_start`: fractional hours, e.g., 17.5 = 17:30)
   - End time (`time_end`: fractional hours)
   - Age preference range (`age_min`, `age_max`)
   - Optional: Description
   - Optional: Toggle "Private" (if private, must pick a friend via `invited_friend_id`, sourced from UC-36 Friends list)
2. UI validates client-side: `time_start < time_end`, `age_min <= age_max`, if private then `invited_friend_id` is set.
3. UI calls `POST /api/v1/intents` (single request).
   ```json
   {
     "hotspot_id": "...",
     "date": "2026-04-10",
     "time_start": 17.0,
     "time_end": 18.5,
     "age_min": 18,
     "age_max": 40,
     "is_private": false,
     "invited_friend_id": null,
     "description": "Looking for a morning jog partner!"
   }
   ```
4. Backend creates caller intent, then performs inline matching logic in the same use-case flow (no immediate follow-up call to UC-18 from UI).
5. **Case A — Public Intent (`is_private = false`):**
   - Backend tries to find a compatible partner during the create flow.
   - **A1 Match Found:** return `201 Created` with caller intent now in `MATCHING` and a `MatchProposalResponse` (`status: "PENDING"`, `proposal_id`).
   - **A2 No Match Found:** return `201 Created` with caller intent in `OPEN` and no proposal.
6. **Case B — Invite Friend (`is_private = true`):**
   - Backend validates invited friend eligibility and overlap constraints for both users.
   - Backend atomically creates sender + receiver intents, sets both to `MATCHING`, creates proposal in `PENDING`.
   - Receiver intent is a system-generated private intent (not user-authored) and must never appear in public OPEN wait list.
   - Backend auto-accepts sender side by calling `MatchingCommandService.acceptProposal(proposalId, senderId)`.
   - Backend sends push notifications:
     - Sender: invite sent successfully.
     - Receiver: sender invited them to a walk proposal.
7. UI shows loading spinner only while `POST /api/v1/intents` is pending (no fixed wait duration).
8. UI routing after response:
   - If response contains proposal (`proposal_id` present): switch to Proposal tab and open proposal detail.
   - If response has no proposal: stay on Intent tab; intent remains in OPEN list.

**What can go wrong:**

| Condition | Error Code | Invariant | UI Reaction |
|-----------|-----------|-----------|-------------|
| Another OPEN/MATCHING intent overlaps the time window | `INTENT_OVERLAPPING` | **I-1** | Show blocking dialog: "You already have an active intent during this time. Cancel it first or choose a different time." |
| An ACTIVE/PENDING session overlaps the time window | `INTENT_OVERLAPPING_SESSION` | **I-1** | Show blocking dialog: "You already have a confirmed walk session during this time." |
| Hotspot no longer exists | `HOTSPOT_NOT_FOUND` | — | Show toast: "This hotspot is no longer available." Navigate back to map. |
| `time_start >= time_end` | `INVALID_TIME_RANGE` | — | Show inline error: "End time must be after start time." |
| `age_min > age_max` | `INVALID_AGE_RANGE` | — | Show inline error: "Minimum age cannot exceed maximum age." |
| Private intent but friendship not accepted | `INTENT_PRIVATE_FRIEND_NOT_ACCEPTED` | **I-7** | Show inline error: "You can only send a private invite to an accepted friend." |
| Invited friend has overlapping intent/session | `INTENT_OVERLAPPING` / `INTENT_OVERLAPPING_SESSION` | **I-1** | Show blocking dialog: "Your friend is not available in this time window." |
| Validation errors | `VALIDATION_ERROR` (422) | — | Parse `error.message` (comma-separated `field: reason` string) and show field-level errors. |

**Other activities:**
- For public intent path, backend may continue asynchronous matching after create when no immediate match is found.

**System state on completion:**
- Public no-match: caller intent is `OPEN` and appears in Intent tab (wait list behavior).
- Public match-found: caller intent is `MATCHING`; proposal exists in `PENDING` and appears in Proposal tab.
- Private invite: sender and receiver intents are `MATCHING`; proposal exists in `PENDING`; sender is already accepted. If private proposal is later passed/expired, system-generated private intents are closed (not reopened to public `OPEN`).

---

### UC-16 — View My Active Intents

**Use Case Name:** View My Active Intents

**Initial assumption:** User is authenticated and on the "My Intents" screen.

**Normal:**
1. UI calls `GET /api/v1/intents`.
2. Backend returns `200 OK` with a list of intents in `OPEN` status for this screen.
3. UI renders each OPEN intent card showing: hotspot name, time window, age range, and `expires_at` countdown timer.
4. The Intent tab is the effective wait list: OPEN means "waiting for match".
5. If a proposal is created for an intent, that intent transitions to `MATCHING` and is removed from this tab; user sees it in Proposal tab (UC-19).
6. For OPEN intents on this tab: show only "Cancel" button (UC-17). Do not show any "Find Match"/"Trigger Match" action.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Network failure | — | Show cached list with "Could not refresh" banner. |

**Other activities:**
- Show a local countdown timer for each intent's `expires_at`. When it hits 0, refresh the list — the intent may have moved to `EXPIRED`.
- If a push notification arrives for a new proposal, automatically refresh this list so matched intents disappear from Intent tab.

**System state on completion:** UI reflects live intent states. Expired intents disappear from the list after refresh.

---

### UC-17 — Cancel Walk Intent

**Use Case Name:** Cancel Walk Intent

**Initial assumption:** User is viewing an intent card in `OPEN` status (invariant **I-6**: only OPEN intents can be cancelled via this API; MATCHING intents require UC-22 via proposal flow).

**Normal:**
1. User taps "Cancel Intent" on the intent card.
2. UI shows a confirmation dialog: "Are you sure you want to cancel this intent? You will need to create a new one."
3. User confirms.
4. UI calls `DELETE /api/v1/intents/{intentId}`.
5. Backend returns `200 OK` with `{ "data": null }`.
6. UI removes the intent card from the list with an animation.

**What can go wrong:**

| Condition | Error Code | Invariant | UI Reaction |
|-----------|-----------|-----------|-------------|
| Intent is not OPEN (e.g., became MATCHING concurrently) | `INTENT_NOT_OPEN` | **I-6** | Show toast: "This intent is no longer OPEN and cannot be cancelled here. Go to your proposals to manage it." Refresh list. |
| User is not the owner | `INTENT_NOT_OWNER` | — | Show toast: "Permission denied." (Should never happen in normal flow.) |
| Intent not found | `INTENT_NOT_FOUND` | — | Show toast: "Intent not found." Refresh list. |

**Other activities:** None.

**System state on completion:** Intent is in `CANCELLED` terminal state (invariant **I-6**). The time-window overlap lock is released. User can create a new intent in the same time slot.

---

### UC-18 — Trigger Match

**Use Case Name:** Trigger Match (Internal API / Non-UI)

**Initial assumption:** This endpoint applies only to non-invite (`is_private = false`) intents and is not exposed as a mobile UI action. Android product flow performs matching in UC-15 create flow (inline + async), and Intent screen has no retrigger button.

**Normal:**
1. Caller invokes `POST /api/v1/intents/{intentId}/match` for an OPEN non-invite intent.
2. **Case A — Match Found (200 OK):** Backend returns a `MatchProposalResponse` with `status: "PENDING"`.
   - Intent is now `MATCHING` (soft-locked per **I-4**).
   - Internal caller may notify client via existing push/channel flow.
3. **Case B — No Match Yet (204 No Content):** Empty response.
   - Intent remains `OPEN`.

**What can go wrong:**

| Condition | Error Code | Invariant | UI Reaction |
|-----------|-----------|-----------|-------------|
| Intent is not OPEN | `INVALID_INTENT_DATA` | **I-4** | Caller treats as no-op/invalid retry and refreshes state. |
| Intent not found | `INTENT_NOT_FOUND` | — | Caller refreshes state and aborts retry. |
| Intent not owned by caller | `INTENT_NOT_OWNER` | — | Caller logs and aborts action. |
| Network failure | — | — | Retry policy depends on internal caller. |

**Other activities:**
- Endpoint is retained for internal retry/ops/testing scenarios only.
- Android app must not expose this endpoint as a user-triggerable button.

**System state on completion (Case A):** Intent transitions from `OPEN` → `MATCHING`. A `MatchProposal` in `PENDING` status now exists. The 5-minute proposal timeout (**P-4**) has started.
