# WalkMate — Post-Session Use Cases

> Part of: [Use Cases Index](README.md)

**Domain:** Session History, Route Replay, Reviews, and Incident Reports
**Last Updated:** 2026-04-13

---

## Table of Contents

| UC# | Use Case | API Endpoint |
|-----|----------|--------------|
| UC-29 | [View Session History](#uc-29--view-session-history) | `GET /api/v1/sessions/history` |
| UC-30 | [View Session Route Replay](#uc-30--view-session-route-replay) | `GET /api/v1/sessions/{sessionId}/route` |
| UC-31 | [Submit a Review](#uc-31--submit-a-review) | `POST /api/v1/sessions/{sessionId}/review` |
| UC-32 | [Submit an Incident Report](#uc-32--submit-an-incident-report) | `POST /api/v1/sessions/{sessionId}/report` |

---

### UC-29 — View Session History

**Use Case Name:** View Session History

**Initial assumption:** User is authenticated and on the History screen. Session is in a terminal state.

**Normal:**
1. UI calls `GET /api/v1/sessions/history`.
2. Backend returns list of terminal sessions (newest first) with `status`, `partner_id`, `scheduled_start`, `total_distance_km`, `duration_minutes`.
3. UI renders a history list. Each card shows:
   - Session date and time
   - Partner name (fetch from `GET /api/v1/users/{partner_id}` or cache)
   - Status badge (COMPLETED, NO_SHOW, CANCELLED, ABORTED)
   - Total distance and duration (shown for COMPLETED; "—" for others)
4. Tapping a card navigates to session detail, with options for route replay (UC-30), review (UC-31), or report (UC-32) depending on status and time window.

**What can go wrong:**

| Condition | UI Reaction |
|-----------|------------|
| Network failure | Show cached history with "Could not refresh" banner. |

**Other activities:** None.

**System state on completion:** User sees full history. COMPLETED sessions show review prompt if not yet reviewed.

---

### UC-30 — View Session Route Replay

**Use Case Name:** View Session Route Replay

**Initial assumption:** User is on the Session History detail screen. Session is `COMPLETED`.

**Normal:**
1. User taps "View Route" on a completed session card.
2. UI calls `GET /api/v1/sessions/{sessionId}/route`.
3. Backend returns `200 OK` with:
   - `user_a_polylines`: Google Encoded Polyline strings (array of segments)
   - `user_b_polylines`: Google Encoded Polyline strings (array of segments)
   - `total_distance_km`, `duration_minutes`
4. UI decodes polylines and renders dual-path route on a map (different colors per user).
5. UI shows stats panel: total distance, total duration.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Session is not COMPLETED | `SESSION_NOT_FINISHED` | Show toast: "Route replay is only available for completed walks." |
| User not a participant | `SESSION_NOT_PARTICIPANT` | Show toast: "Permission denied." |
| Session not found | `SESSION_NOT_FOUND` | Show toast: "Session not found." Navigate back. |
| No GPS data (tracking unavailable) | (200 OK, empty polylines) | Show message: "No route data recorded for this session." |

**Other activities:** None.

**System state on completion:** Map shows historical route. Read-only display.

---

### UC-31 — Submit a Review

**Use Case Name:** Submit a Review

**Initial assumption:** Session is in `COMPLETED` status (only completed sessions can be reviewed). User has not yet reviewed this session. User is on the post-session screen or history detail.

**Normal:**
1. UI shows a star rating (1–5) and an optional comment field.
2. User selects a star rating (required).
3. User calls `POST /api/v1/sessions/{sessionId}/review`:
   ```json
   { "rating_stars": 4, "comment": "Great walk! Very friendly." }
   ```
4. Backend returns `200 OK` with the `ReviewResponse`.
5. UI shows a confirmation: "Review submitted! Thank you." Hides the review prompt for this session.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Session is not COMPLETED | `REVIEW_SESSION_NOT_COMPLETED` | Hide the "Review" button for non-COMPLETED sessions. If called anyway, show toast: "Reviews are only for completed walks." |
| User was not a participant | `REVIEW_NOT_PARTICIPANT` | Show toast: "You were not part of this session." |
| Already reviewed | `REVIEW_ALREADY_SUBMITTED` | Hide the "Review" button after successful submission. If called again, show toast: "You've already reviewed this session." |
| Rating not 1–5 | `REVIEW_INVALID_RATING` | Enforce client-side with star widget. |

**Other activities:**
- Server stores review and applies review-based trust adjustment, then recalculates `trustScore`.
- Optionally refresh the partner's public profile page to show the new trust score.

**System state on completion:** Review exists in DB. Reviewee's trust score is recalculated from session-outcome baseline + review adjustment (**X-4**). Review appears in `GET /api/v1/users/{revieweeId}/reviews`.

---

### UC-32 — Submit an Incident Report

**Use Case Name:** Submit an Incident Report

**Initial assumption:** Session is in `ACTIVE`, `NO_SHOW`, `COMPLETED`, or `ABORTED` status. Reporting window is open (72h for COMPLETED, 24h for ABORTED/NO_SHOW). For `ACTIVE`, report can be submitted immediately from the live session detail. User has not yet submitted a report for this session.

**Normal:**
1. User taps "Report an Issue" on the session detail or post-abort screen.
2. UI shows a form with:
   - Reason text field (required)
   - Optional: Evidence URL field
   - The `reportedUserId` is pre-filled from the session's partner ID.
3. User fills in reason and taps "Submit Report".
4. UI calls `POST /api/v1/sessions/{sessionId}/report`:
   ```json
   {
     "reportedUserId": "...",
     "reason": "Partner was aggressive and threatening.",
     "evidenceUrl": null
   }
   ```
5. Backend returns `201 Created` with `{ "data": { "reportId": "...", "createdAt": "..." } }`.
6. UI shows confirmation: "Your report has been submitted. Our team will review it."

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Session status not reportable | `REPORT_SESSION_INVALID_STATUS` | Hide "Report" button for non-reportable sessions (PENDING, CANCELLED). |
| Reporting window has expired | `REPORT_WINDOW_EXPIRED` | Show toast: "The reporting window for this session has closed." Hide "Report" button. |
| Already reported | `REPORT_ALREADY_SUBMITTED` | Show toast: "You've already submitted a report for this session." Hide button. |
| User trying to report themselves | `REPORT_SELF_NOT_ALLOWED` | Should never happen in UI — `reportedUserId` is always the partner. |
| User not a participant | `SESSION_NOT_PARTICIPANT` | Show toast: "Permission denied." |
| Session not found | `SESSION_NOT_FOUND` | Show toast: "Session not found." |

**Other activities:** None. Report is reviewed by admins asynchronously.

**System state on completion:** Report is stored. Moderators are notified asynchronously.
