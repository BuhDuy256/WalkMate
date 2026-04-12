# WalkMate — Proposal Negotiation Use Cases

> Part of: [Use Cases Index](README.md)

**Domain:** Match Proposal Negotiation
**Last Updated:** 2026-04-12

---

## Table of Contents

| UC# | Use Case | API Endpoint |
|-----|----------|--------------|
| UC-12 | [View Incoming Proposals](#uc-12--view-incoming-proposals) | `GET /api/v1/proposals` |
| UC-13 | [Accept a Proposal](#uc-13--accept-a-proposal) | `POST /api/v1/proposals/{proposalId}/accept` |
| UC-14 | [Pass (Reject) a Proposal](#uc-14--pass-reject-a-proposal) | `POST /api/v1/proposals/{proposalId}/pass` |
| UC-15 | [Cancel a Proposal (Withdraw Intent)](#uc-15--cancel-a-proposal-withdraw-intent) | `DELETE /api/v1/proposals/{proposalId}` |

---

### UC-12 — View Incoming Proposals

**Use Case Name:** View Incoming Proposals

**Initial assumption:** User is authenticated. User receives a FCM notification of type `PROPOSAL_RECEIVED`, or navigates to the Proposals screen.

**Normal:**
1. UI calls `GET /api/v1/proposals`.
2. Backend returns `200 OK` with list of `PENDING` proposals the user is involved in.
3. UI renders each proposal card showing:
   - Partner's name and avatar (fetch from `GET /api/v1/users/{matchedUserId}`)
   - Proposed time window and meeting lat/lng
   - Countdown timer to `expires_at` (5-minute TTL per invariant **P-4**)
   - "Accept" (UC-13) and "Pass" (UC-14) action buttons
4. If no proposals, show "No pending proposals" empty state.

**What can go wrong:**

| Condition | UI Reaction |
|-----------|------------|
| Network failure | Show cached proposals with a "Could not refresh" banner. |
| Proposal disappears between list and action (expired/rejected concurrently) | Handled at action time by UC-13/UC-14. |

**Other activities:**
- Show a live countdown timer for each proposal's `expires_at`. If it reaches 0, refresh the list. The proposal will be gone (expired); the intent reverts to `OPEN` (**P-4**).
- Listen to FCM events: `PROPOSAL_RECEIVED` to add proposals, `SESSION_ACTIVE` to clear proposals and navigate to session screen.

**System state on completion:** User sees all PENDING proposals. The intent associated with each proposal is in `MATCHING` state (invariant **I-4**).

---

### UC-13 — Accept a Proposal

**Use Case Name:** Accept a Proposal

**Initial assumption:** User is on the Proposal Detail screen. The proposal is in `PENDING` status. The user's intent is in `MATCHING` status (invariant **P-2**).

**Normal:**
1. User taps "Accept".
2. UI disables the "Accept" button immediately to prevent double-tap.
3. UI calls `POST /api/v1/proposals/{proposalId}/accept`.
4. **Case A — Partial Acceptance (200 OK, `status: "PENDING"`):**
   - Partner has not yet accepted.
   - UI shows a waiting state: "You accepted! Waiting for your partner to accept..." with the countdown timer still visible.
   - Disable both "Accept" and "Pass" buttons.
5. **Case B — Both Accepted (200 OK, `status: "CONFIRMED"`, `session_id` is populated):**
   - A `WalkSession` has been atomically created (invariant **P-3**).
   - Both intents are now `CONSUMED` (invariant **I-3**).
   - A MongoDB chat room has been created with `session_id` as key.
   - UI shows a celebration animation and navigates to the Session Detail screen (UC-16).

**What can go wrong:**

| Condition | Error Code | Invariant | UI Reaction |
|-----------|-----------|-----------|-------------|
| Proposal already expired/rejected | `PROPOSAL_ALREADY_TERMINAL` | **I-6** | Show toast: "This proposal is no longer active." Navigate back to intents list. |
| User is not a participant | `PROPOSAL_NOT_PARTICIPANT` | — | Show toast: "Permission denied." |
| One intent is no longer MATCHING (e.g., it expired concurrently) | `PROPOSAL_INTENT_NO_LONGER_OPEN` | **P-2** | Show toast: "Could not confirm — one of the intents is no longer available. The proposal has been cancelled." Refresh intents list. |
| Concurrent modification (two users accepted simultaneously, DB conflict) | `PROPOSAL_CONCURRENT_MODIFICATION` | **X-5** | Show toast: "A conflict occurred. Please refresh and try again." Refresh proposals. |
| Proposal not found | `PROPOSAL_NOT_FOUND` | — | Show toast: "Proposal not found." Navigate back. |

**Other activities:**
- Partner receives `PROPOSAL_ACCEPTED` FCM notification when this user accepts.
- When both accept, both users receive `SESSION_ACTIVE` FCM notification — navigate both to the Session screen automatically.

**System state on completion (Case B):** Proposal is `CONFIRMED`. Both intents are `CONSUMED` (terminal, **I-6**). A `WalkSession` in `PENDING` status exists. Chat room is open. The session's `scheduled_start`, `scheduled_end`, and `meeting_point` are immutable snapshots (**S-8**).

**Required UI navigation on Case B:**
1. Show a brief celebration animation (e.g., confetti overlay).
2. Navigate to the **Session Detail screen** for the newly created session (use `session_id` from the response).
3. On the Session Detail screen, render a **Chat button** (e.g., speech-bubble icon in the top-right corner). Tapping it opens the WebSocket/Chat UI scoped to `session_id`. The Chat button must remain enabled until the session reaches a terminal state (**S-7**).
4. Do **not** navigate to the Session Detail screen before `session_id` is non-null in the response — only Case B guarantees its presence.

---

### UC-14 — Pass (Reject) a Proposal

**Use Case Name:** Pass (Reject) a Proposal

**Initial assumption:** User is on the Proposal Detail screen. Proposal is `PENDING`.

**Normal:**
1. User taps "Pass" (not interested in this match).
2. UI shows confirmation dialog: "Pass on this match? Your intent will stay active and we'll keep looking for other partners."
3. User confirms.
4. UI calls `POST /api/v1/proposals/{proposalId}/pass`.
5. Backend returns `200 OK` with `{ "data": null }`. Proposal moves to `REJECTED`. Both intents revert to `OPEN` (per state-transition: `MATCHING → OPEN`). The partner's intent is also added to the exclude list per invariant **X-3**, so the matching engine won't pair them again on this intent.
6. UI navigates back to the "My Intents" list. The intent now shows as `OPEN` again with "Find Match" enabled.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Proposal no longer PENDING | `PROPOSAL_ALREADY_TERMINAL` | Show toast: "This proposal is already resolved." Navigate back to intents. |
| User not a participant | `PROPOSAL_NOT_PARTICIPANT` | Show toast: "Permission denied." |
| Proposal not found | `PROPOSAL_NOT_FOUND` | Show toast: "Proposal not found." Navigate back. |

**Other activities:** None.

**System state on completion:** Proposal is `REJECTED`. Both intents revert to `OPEN`. The exclude list is updated (**X-3**) — these two users won't be matched again on this intent run.

---

### UC-15 — Cancel a Proposal (Withdraw Intent)

**Use Case Name:** Cancel a Proposal (Withdraw Intent)

**Initial assumption:** User is on the Proposal Detail screen. The proposal is `PENDING`. The user wants to fully withdraw their intent (not just pass on this match).

**Normal:**
1. User taps "Withdraw My Intent".
2. UI shows a strong warning dialog: "This will permanently cancel your walk intent. You'll need to create a new one if you change your mind."
3. User confirms.
4. UI calls `DELETE /api/v1/proposals/{proposalId}`.
5. Backend returns `200 OK`. The caller's intent moves to `CANCELLED` (terminal, **I-6**). The partner's intent reverts to `OPEN`.
6. UI navigates to the "My Intents" list. The cancelled intent is no longer visible.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Proposal not PENDING | `PROPOSAL_ALREADY_TERMINAL` | Show toast: "This proposal is already resolved." Navigate back. |
| User not a participant | `PROPOSAL_NOT_PARTICIPANT` | Show toast: "Permission denied." |
| Proposal not found | `PROPOSAL_NOT_FOUND` | Show toast: "Proposal not found." Navigate back. |

**Other activities:** None.

**System state on completion:** Caller's intent is `CANCELLED` (terminal). Partner's intent reverts to `OPEN` and is eligible for matching again. Proposal is `REJECTED`.
