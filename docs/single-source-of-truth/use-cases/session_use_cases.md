# WalkMate — Session Lifecycle Use Cases

> Part of: [Use Cases Index](README.md)

**Domain:** Walk Session Lifecycle Management
**Last Updated:** 2026-04-12

---

## Table of Contents

| UC# | Use Case | API Endpoint |
|-----|----------|--------------|
| UC-16 | [View Active Sessions](#uc-16--view-active-sessions) | `GET /api/v1/sessions/active` |
| UC-17 | [Activate Session (Arrive at Hotspot)](#uc-17--activate-session-arrive-at-hotspot) | `POST /api/v1/sessions/{sessionId}/activate` |
| UC-18 | [Cancel a Pending Session](#uc-18--cancel-a-pending-session) | `POST /api/v1/sessions/{sessionId}/cancel` |
| UC-19 | [Complete Walk Session (User-initiated)](#uc-19--complete-walk-session-user-initiated) | `POST /api/v1/sessions/{sessionId}/complete` |
| UC-20 | [Abort Active Session (Emergency)](#uc-20--abort-active-session-emergency) | `POST /api/v1/sessions/{sessionId}/abort` |

---

### UC-16 — View Active Sessions

**Use Case Name:** View Active Sessions

**Initial assumption:** User is authenticated. User has at least one session in `PENDING` or `ACTIVE` status (navigated here after UC-13 success, or from bottom nav).

**Normal:**
1. UI calls `GET /api/v1/sessions/active`.
2. Backend returns list of sessions in `PENDING` or `ACTIVE` status.
3. For each `PENDING` session, UI shows:
   - Meeting point on a mini-map
   - `scheduled_start` countdown timer
   - Partner's name/avatar
   - Activation window: `[scheduledStart − 10 min, scheduledStart + 15 min]` (invariant **S-3**)
   - "I'm Here!" button (enabled only within activation window) → triggers UC-17
   - "Cancel Walk" button → triggers UC-18
4. For each `ACTIVE` session, UI shows:
   - Live map with partner's last known location
   - Walk duration timer (started at `started_at`)
   - "Complete Walk" button (enabled only after 5 minutes of walking, per **S-5**) → triggers UC-19
   - "Emergency Abort" button → triggers UC-20

**What can go wrong:**

| Condition | UI Reaction |
|-----------|------------|
| No active sessions | Show empty state: "No active walks. Create an intent to get started!" |
| Network failure | Show last cached session state. |

**Other activities:**
- Poll `GET /api/v1/sessions/active` every 30 seconds, OR listen for FCM `SESSION_ACTIVE` to detect when partner activates and session transitions to `ACTIVE`.
- When session is `ACTIVE`, start the background GPS sync task (UC-21).

**System state on completion:** UI reflects live session states. Chat icon is enabled for sessions in `PENDING` or `ACTIVE` status (invariant **S-7**).

---

### UC-17 — Activate Session (Arrive at Hotspot)

**Use Case Name:** Activate Session (Arrive at Hotspot)

**Initial assumption:** User is on the Session Detail screen. Session is `PENDING`. Current time is within the activation window: `[scheduledStart − 10 min, scheduledStart + 15 min]` (invariant **S-3**; defined by `WalkSession.ACTIVATION_WINDOW_BEFORE = 10 min` and `ACTIVATION_WINDOW_AFTER = 15 min`). At least one of `user_a_activated_at` / `user_b_activated_at` is `NULL`.

**Normal:**
1. User taps "I'm Here!" (the activate button).
2. UI disables the button immediately.
3. UI calls `POST /api/v1/sessions/{sessionId}/activate`.
4. **Case A — Partial Activation (200 OK, `status: "PENDING"`):** Only this user has activated.
   - UI shows: "You've arrived! Waiting for your partner..." with a spinner.
   - `user_a_activated_at` or `user_b_activated_at` is now set.
5. **Case B — Mutual Activation (200 OK, `status: "ACTIVE"`):** Both users have now activated.
   - `started_at` is set. Walk timer begins.
   - UI transitions to the Active Walk view (map, timer, abort/complete buttons).
   - GPS sync loop (UC-21) starts.
   - Chat is confirmed open (**S-7**).

**What can go wrong:**

| Condition | Error Code | Invariant | UI Reaction |
|-----------|-----------|-----------|-------------|
| Activation window has passed (no-show scenario) | `SESSION_ACTIVATION_WINDOW_CLOSED` | **S-3**, **S-4** | Show toast: "Activation window closed. Waiting for status update." Do **not** navigate away immediately. The session's terminal state (NO_SHOW or CANCELLED) is resolved server-side by the scheduler — poll `GET /api/v1/sessions/active` once after 5 seconds and navigate to History when the session disappears from the active list. |
| Session is not PENDING | `SESSION_NOT_PENDING` | — | Show toast: "This session is not waiting for activation." Refresh session state. |
| User not a participant | `SESSION_NOT_PARTICIPANT` | — | Show toast: "Permission denied." |
| Session not found | `SESSION_NOT_FOUND` | — | Show toast: "Session not found." Navigate to history. |
| User has overlapping active session | `SESSION_OVERLAPPING` | **I-1** | Show toast: "You already have an active walk session during this time." |

**Other activities:**
- After Case A, poll `GET /api/v1/sessions/active` every 15 seconds to detect when partner activates (or listen to FCM `SESSION_ACTIVE` notification).
- On Case B, start GPS sync (UC-21).

**System state on completion (Case B):** Session moves `PENDING` → `ACTIVE` (invariant **S-2**). `started_at` is set. 4-hour auto-close safety limit begins (**S-6**). Chat remains open (**S-7**).

---

### UC-18 — Cancel a Pending Session

**Use Case Name:** Cancel a Pending Session

**Initial assumption:** Session is `PENDING`. Walk has not started. User no longer wants to proceed.

**Normal:**
1. User taps "Cancel Walk".
2. UI shows a confirmation dialog with a required reason text input: "Why are you cancelling?"
3. User enters reason and confirms.
4. UI calls `POST /api/v1/sessions/{sessionId}/cancel`:
   ```json
   { "reason": "I can't make it today." }
   ```
5. Backend returns `200 OK`. Session moves to `CANCELLED` (terminal). Chat room is closed server-side.
6. UI navigates to Session History (UC-22). Chat input is locked.

**What can go wrong:**

| Condition | Error Code | Invariant | UI Reaction |
|-----------|-----------|-----------|-------------|
| Session is not PENDING (e.g., already ACTIVE) | `SESSION_CANCEL_NOT_PENDING` | — | Show toast: "Walk has already started. Use 'Abort' to stop an active walk." |
| Empty reason | `VALIDATION_ERROR` (422) | — | Show inline error: "Please provide a reason." |
| Session not found | `SESSION_NOT_FOUND` | — | Show toast: "Session not found." |
| User not a participant | `SESSION_NOT_PARTICIPANT` | — | Show toast: "Permission denied." |

**Other activities:** Partner receives a push notification that the session was cancelled.

**System state on completion:** Session is `CANCELLED` (terminal). Chat write access is revoked for `session_id` (**S-7**). User's trust score may be affected (**X-4**).

---

### UC-19 — Complete Walk Session (User-initiated)

**Use Case Name:** Complete Walk Session

**Initial assumption:** Session is `ACTIVE`. `started_at` is set. At least 5 minutes have elapsed since `started_at` (invariant **S-5**).

**Normal:**
1. User taps "Complete Walk".
2. UI shows a confirmation dialog: "End the walk now? Make sure you and your partner are ready to finish."
3. UI calls `POST /api/v1/sessions/{sessionId}/complete`.
4. Backend validates the 5-minute minimum (**S-5**) and returns `200 OK` with the final `WalkSessionResponse`.
5. UI navigates to a "Walk Completed!" summary screen showing total distance, duration, and partner's name.
6. UI prompts user to leave a review (navigates to UC-24 flow).
7. Chat input is locked (**S-7**).

**What can go wrong:**

| Condition | Error Code | Invariant | UI Reaction |
|-----------|-----------|-----------|-------------|
| Walk duration < 5 minutes | `SESSION_COMPLETE_TOO_EARLY` | **S-5** | Show toast: "You need to walk for at least 5 minutes before completing. Keep going!" Disable "Complete" button with a countdown to when it becomes enabled. |
| Session is not ACTIVE | `SESSION_NOT_ACTIVE` | — | Show toast: "This walk is not currently active." Refresh session state. |
| Session not found | `SESSION_NOT_FOUND` | — | Show toast: "Session not found." Navigate to history. |
| User not a participant | `SESSION_NOT_PARTICIPANT` | — | Show toast: "Permission denied." |

**Other activities:**
- GPS sync loop (UC-21) stops after completion.
- Gamification: `SessionCompletedEvent` is published server-side; badges may be awarded. Refresh profile stats after a short delay.
- Trust score update (**X-4**) is applied server-side.

**System state on completion:** Session is `COMPLETED` (terminal). Chat write access is revoked (**S-7**). User can now submit a review (UC-24) and/or report (UC-25, 72-hour window). GPS route data is available (UC-23).

---

### UC-20 — Abort Active Session (Emergency)

**Use Case Name:** Abort Active Session

**Initial assumption:** Session is `ACTIVE`. A safety issue or emergency has occurred.

**Normal:**
1. User taps the "Emergency Abort" button (should be visually distinct and require two-tap confirmation).
2. UI shows a dialog with reason selection:
   - "Safety Concern"
   - "Emergency"
   - "Partner Misconduct"
   - "Other"
3. User selects a reason and confirms.
4. UI calls `POST /api/v1/sessions/{sessionId}/abort`:
   ```json
   { "reason": "SAFETY_CONCERN" }
   ```
5. Backend returns `200 OK`. Session moves to `ABORTED` (terminal). `SessionAbortedEvent` is published.
6. UI navigates to a "Walk Aborted" screen with a safety message and option to submit a report (UC-25, 24-hour window).

**What can go wrong:**

| Condition | Error Code | Invariant | UI Reaction |
|-----------|-----------|-----------|-------------|
| Session is not ACTIVE | `SESSION_NOT_ACTIVE` | — | Show toast: "This walk is not active." Refresh session state. |
| Invalid reason enum | `VALIDATION_ERROR` (422) | — | Show inline error. (Client should enforce valid values from enum.) |
| Session not found | `SESSION_NOT_FOUND` | — | Show toast: "Session not found." Navigate to history. |
| User not a participant | `SESSION_NOT_PARTICIPANT` | — | Show toast: "Permission denied." |

**Other activities:**
- GPS sync loop (UC-21) stops.
- Partner receives push notification about abort.
- Gamification: `SessionAbortedEvent` published; trust/penalty scores updated (**X-4**).

**System state on completion:** Session is `ABORTED` (terminal). Chat write access is revoked (**S-7**). User can submit a report within 24 hours (UC-25).
