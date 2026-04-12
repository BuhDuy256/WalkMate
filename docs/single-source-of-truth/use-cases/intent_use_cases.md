# WalkMate — Walk Intent Use Cases

> Part of: [Use Cases Index](README.md)

**Domain:** Walk Intent Creation and Management
**Last Updated:** 2026-04-12

---

## Table of Contents

| UC# | Use Case | API Endpoint |
|-----|----------|--------------|
| UC-08 | [Create Walk Intent](#uc-08--create-walk-intent) | `POST /api/v1/intents` |
| UC-09 | [View My Active Intents](#uc-09--view-my-active-intents) | `GET /api/v1/intents` |
| UC-10 | [Cancel Walk Intent](#uc-10--cancel-walk-intent) | `DELETE /api/v1/intents/{intentId}` |
| UC-11 | [Trigger Match](#uc-11--trigger-match) | `POST /api/v1/intents/{intentId}/match` |

---

### UC-08 — Create Walk Intent

**Use Case Name:** Create Walk Intent

**Initial assumption:** User is authenticated. User has selected a hotspot from UC-07. User is on the "Create Intent" form screen. The user currently has NO overlapping `OPEN`/`MATCHING` intent or `PENDING`/`ACTIVE` session in the chosen time window (invariant **I-1**).

**Normal:**
1. User fills in:
   - Date (`date`: YYYY-MM-DD)
   - Start time (`time_start`: fractional hours, e.g., 17.5 = 17:30)
   - End time (`time_end`: fractional hours)
   - Age preference range (`age_min`, `age_max`)
   - Optional: Description
   - Optional: Toggle "Private" (if private, must pick a friend via `invited_friend_id`)
2. UI validates client-side: `time_start < time_end`, `age_min <= age_max`, if private then `invited_friend_id` is set.
3. UI calls `POST /api/v1/intents`:
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
4. Backend returns `201 Created` with `WalkIntentResponse` including `id`, `status: "OPEN"`, `expires_at`.
5. UI navigates to the "My Intents" list (UC-09) and highlights the newly created intent.

**What can go wrong:**

| Condition | Error Code | Invariant | UI Reaction |
|-----------|-----------|-----------|-------------|
| Another OPEN/MATCHING intent overlaps the time window | `INTENT_OVERLAPPING` | **I-1** | Show blocking dialog: "You already have an active intent during this time. Cancel it first or choose a different time." |
| An ACTIVE/PENDING session overlaps the time window | `INTENT_OVERLAPPING_SESSION` | **I-1** | Show blocking dialog: "You already have a confirmed walk session during this time." |
| Hotspot no longer exists | `HOTSPOT_NOT_FOUND` | — | Show toast: "This hotspot is no longer available." Navigate back to map. |
| `time_start >= time_end` | `INVALID_TIME_RANGE` | — | Show inline error: "End time must be after start time." |
| `age_min > age_max` | `INVALID_AGE_RANGE` | — | Show inline error: "Minimum age cannot exceed maximum age." |
| Private intent but friendship not accepted | `INTENT_PRIVATE_FRIEND_NOT_ACCEPTED` | **I-7** | Show inline error: "You can only send a private invite to an accepted friend." |
| Validation errors | `VALIDATION_ERROR` (422) | — | Parse `error.message` (comma-separated `field: reason` string) and show field-level errors. |

**Other activities:** None.

**System state on completion:** A new `WalkIntent` exists in `OPEN` status. The overlap lock is now held (invariant **I-1**). The intent is eligible for matching. `expires_at` countdown begins.

---

### UC-09 — View My Active Intents

**Use Case Name:** View My Active Intents

**Initial assumption:** User is authenticated and on the "My Intents" screen.

**Normal:**
1. UI calls `GET /api/v1/intents`.
2. Backend returns `200 OK` with a list of intents in `OPEN` or `MATCHING` status.
3. UI renders each intent card showing: hotspot name, time window, age range, status badge, `expires_at` countdown timer.
4. For `OPEN` intents: show "Find Match" button (triggers UC-11) and "Cancel" button (triggers UC-10).
5. For `MATCHING` intents: show "View Proposal" button (navigates to UC-12) and disable "Cancel". Display a lock icon indicating the intent is soft-locked per invariant **I-4**.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Network failure | — | Show cached list with "Could not refresh" banner. |

**Other activities:**
- Show a local countdown timer for each intent's `expires_at`. When it hits 0, refresh the list — the intent may have moved to `EXPIRED`.
- If a push notification arrives for a new proposal, automatically refresh this list.

**System state on completion:** UI reflects live intent states. Expired intents disappear from the list after refresh.

---

### UC-10 — Cancel Walk Intent

**Use Case Name:** Cancel Walk Intent

**Initial assumption:** User is viewing an intent card in `OPEN` status (invariant **I-6**: only OPEN intents can be cancelled via this API; MATCHING intents require UC-15 via proposal flow).

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

### UC-11 — Trigger Match

**Use Case Name:** Trigger Match

**Initial assumption:** User has a `OPEN` intent. User is on the "My Intents" screen or the intent detail screen. The intent must be `OPEN` to trigger matching — `MATCHING` intents are already locked (invariant **I-4**).

**Normal:**
1. User taps "Find Match" button on an OPEN intent card.
2. UI shows a loading spinner and calls `POST /api/v1/intents/{intentId}/match` (no request body required).
3. **Case A — Match Found (200 OK):** Backend returns a `MatchProposalResponse` with `status: "PENDING"`.
   - Intent is now `MATCHING` (soft-locked per **I-4**).
   - UI navigates immediately to the Proposal Detail screen (UC-13/UC-14).
   - Show a push-like banner: "Match found! Respond within 5 minutes."
4. **Case B — No Match Yet (204 No Content):** Empty response.
   - UI shows a message: "No match found yet. We'll notify you when one is found!"
   - Intent remains `OPEN`.

**What can go wrong:**

| Condition | Error Code | Invariant | UI Reaction |
|-----------|-----------|-----------|-------------|
| Intent is not OPEN (e.g., user tapped too fast after match) | `INVALID_INTENT_DATA` | **I-4** | Show toast: "This intent is already being matched." Refresh list. |
| Intent not found | `INTENT_NOT_FOUND` | — | Show toast: "Intent not found." Refresh list. |
| Intent not owned by caller | `INTENT_NOT_OWNER` | — | Show toast: "Permission denied." |
| Network failure | — | — | Show toast: "Connection error. Please try again." |

**Other activities:**
- The user does NOT need to constantly tap "Find Match." The backend can also push a `PROPOSAL_RECEIVED` notification via FCM when the matching engine finds a compatible intent asynchronously. The UI should listen for that FCM notification and navigate to the proposal when it arrives.
- Implement a pull-to-refresh on the intents list to catch status changes.

**System state on completion (Case A):** Intent transitions from `OPEN` → `MATCHING`. A `MatchProposal` in `PENDING` status now exists. The 5-minute proposal timeout (**P-4**) has started.
