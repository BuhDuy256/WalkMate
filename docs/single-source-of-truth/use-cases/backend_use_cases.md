# WalkMate — Backend Use Cases for UI Refactor

**Document Purpose:** Comprehensive mapping of all backend APIs to UI Use Cases, cross-referenced against `invariants.md` and `state-transitions.md`. This is the single source of truth for UI engineers implementing the frontend refactor.

**Last Updated:** 2026-04-08
**Backend Branch Analyzed:** `implement/realtime`

---

## Table of Contents

| # | Domain | Use Case |
|---|--------|----------|
| **AUTH & PROFILE** | | |
| UC-01 | Auth | Register Account |
| UC-02 | Auth | Login |
| UC-03 | Profile | View My Profile |
| UC-04 | Profile | Edit My Profile |
| UC-05 | Profile | Upload Avatar |
| UC-06 | Device | Register FCM Token |
| **DISCOVERY** | | |
| UC-07 | Hotspots | Browse Hotspot Map |
| **WALK INTENT** | | |
| UC-08 | Intent | Create Walk Intent |
| UC-09 | Intent | View My Active Intents |
| UC-10 | Intent | Cancel Walk Intent |
| UC-11 | Intent | Trigger Match (POST /api/v1/intents/{intentId}/match) |
| **PROPOSAL NEGOTIATION** | | |
| UC-12 | Proposal | View Incoming Proposals |
| UC-13 | Proposal | Accept a Proposal |
| UC-14 | Proposal | Pass (Reject) a Proposal |
| UC-15 | Proposal | Cancel a Proposal (Withdraw Intent) |
| **SESSION LIFECYCLE** | | |
| UC-16 | Session | View Active Sessions |
| UC-17 | Session | Activate Session (Arrive at Hotspot) |
| UC-18 | Session | Cancel a Pending Session |
| UC-19 | Session | Complete Walk Session (User-initiated) |
| UC-20 | Session | Abort Active Session (Emergency) |
| **GPS TRACKING** | | |
| UC-21 | Tracking | Background GPS Route Sync |
| **POST-SESSION** | | |
| UC-22 | History | View Session History |
| UC-23 | History | View Session Route Replay |
| UC-24 | Review | Submit a Review |
| UC-25 | Report | Submit an Incident Report |
| **SOCIAL** | | |
| UC-26 | Social | View a Public User Profile |
| UC-27 | Social | Follow a User |
| UC-28 | Social | Unfollow a User |
| UC-29 | Social | View Followers / Following Lists |
| UC-30 | Social | Block a User |
| UC-31 | Social | Unblock a User |
| **NOTIFICATIONS** | | |
| UC-32 | Notifications | View Notification Feed |
| UC-33 | Notifications | Mark Notification as Read |
| **GAMIFICATION** | | |
| UC-34 | Gamification | View User Badges |
| UC-35 | Gamification | View User Stats |
| UC-36 | Gamification | View Leaderboard |

---

## Auth & Profile

---

### UC-01 — Register Account

**Use Case Name:** Register Account

**Initial assumption:** User is unauthenticated and on the Registration screen. No JWT token exists in local storage.

**Normal:**
1. User fills in Full Name, Email, and Password fields.
2. UI performs client-side validation (email format, password 8–72 chars, name 1–100 chars).
3. UI calls `POST /api/v1/auth/register` with payload:
   ```json
   { "fullname": "...", "email": "...", "password": "..." }
   ```
4. Backend returns `201 Created` with `{ "data": { "email": "..." } }`.
5. UI shows a success banner: "Account created! Please log in." and navigates to Login screen.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Email already registered | `USER_ALREADY_EXISTS` | Show inline error under Email field: "This email is already registered." |
| Any field fails Jakarta validation | `VALIDATION_ERROR` (422) | Show field-level error messages from `error.message` (a comma-separated string of `field: reason` entries). |
| Network failure / 500 | `INTERNAL_ERROR` | Show generic toast: "Something went wrong. Please try again." |

**Other activities:** None.

**System state on completion:** User account exists in DB with no profile data. UI is on the Login screen.

---

### UC-02 — Login

**Use Case Name:** Login

**Initial assumption:** User is unauthenticated. Email was registered via UC-01.

**Normal:**
1. User enters Email and Password.
2. UI calls `POST /api/v1/auth/login` with payload:
   ```json
   { "email": "...", "password": "..." }
   ```
3. Backend returns `200 OK` with `{ "data": { "accessToken": "...", "tokenType": "Bearer", "expiresIn": 86400000 } }`.
4. UI stores `accessToken` securely (e.g., `SharedPreferences` encrypted store).
5. UI registers the FCM token immediately (see UC-06).
6. UI navigates to the Home / Hotspot Map screen.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Wrong email or password | `USER_INVALID_CREDENTIALS` | Show inline error: "Incorrect email or password." |
| Any field fails validation | `VALIDATION_ERROR` (422) | Show field-level errors. |
| Network / server failure | `INTERNAL_ERROR` | Show generic toast. |

**Other activities:** Immediately after login, trigger FCM token registration (UC-06) in the background.

**System state on completion:** `accessToken` persisted. User lands on Home screen. All subsequent requests include `Authorization: Bearer <token>` header.

---

### UC-03 — View My Profile

**Use Case Name:** View My Profile

**Initial assumption:** User is authenticated. Profile screen is opened.

**Normal:**
1. UI calls `GET /api/v1/profile/me` with Bearer token.
2. Backend returns `200 OK` with full profile DTO including `trustScore`, `totalDistanceKm`, `totalSessions`, `tags`, `avatarUrl`, etc.
3. UI renders all profile fields, stats, and tag chips.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Token invalid / expired | 401 (Spring Security) | Clear local token, redirect to Login screen. |
| User deleted mid-session | `USER_NOT_FOUND` | Show toast "Your account was not found." and log user out. |
| Network failure | — | Show last cached profile data with a "Could not refresh" banner. |

**Other activities:** Optionally trigger parallel fetch of `GET /api/v1/users/{userId}/badges` and `GET /api/v1/users/{userId}/stats` to populate badges section.

**System state on completion:** Profile screen shows live data. Edit button is enabled.

---

### UC-04 — Edit My Profile

**Use Case Name:** Edit My Profile

**Initial assumption:** User is on the Edit Profile screen, pre-filled with data from UC-03.

**Normal:**
1. User edits one or more fields: Full Name, Gender, Date of Birth, Bio (≤500 chars), Search Radius, Tags (≤10).
2. UI performs client-side validation before submit.
3. UI calls `PUT /api/v1/profile/me` with only the changed fields (all fields are nullable):
   ```json
   {
     "fullName": "...",
     "gender": "MALE | FEMALE | OTHER | PREFER_NOT_TO_SAY",
     "dateOfBirth": "YYYY-MM-DD",
     "bio": "...",
     "searchRadius": 5000,
     "tags": ["hiking", "morning walks"]
   }
   ```
4. Backend returns `200 OK` with updated full profile DTO.
5. UI navigates back to Profile screen and refreshes with returned data.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Invalid gender enum value | `INVALID_USER_DATA` | Show toast: "Invalid gender selection." |
| Validation error on any field | `VALIDATION_ERROR` (422) | Show field-level error messages parsed from `error.message` (comma-separated `field: reason` string). |
| Bio over 500 chars | `VALIDATION_ERROR` (422) | Enforce client-side char counter; also handle server `error.message` if validation slips through. |
| Tags over 10 items | `VALIDATION_ERROR` (422) | Enforce max-10 rule client-side; cap tag addition. |
| User not found | `USER_NOT_FOUND` | Log out and redirect to Login. |

**Other activities:** None.

**System state on completion:** Profile updated in DB. UI shows updated profile. `tags` list is replaced atomically (not merged).

---

### UC-05 — Upload Avatar

**Use Case Name:** Upload Avatar

**Initial assumption:** User is on the Edit Profile screen and selects a new photo from the device gallery or camera.

**Normal:**
1. User selects an image file.
2. UI shows a loading indicator.
3. UI calls `POST /api/v1/profile/avatar` as `multipart/form-data` with the file in a field named `file`.
4. Backend returns `200 OK` with `{ "data": { "avatarUrl": "/api/v1/files/avatars/..." } }`.
5. UI updates the avatar preview using the returned `avatarUrl`.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| File too large (server-side limit) | `INTERNAL_ERROR` or 413 | Show toast: "Image is too large. Please choose a smaller file." |
| Unsupported file type | `INTERNAL_ERROR` | Show toast: "Unsupported image format." |
| Network failure | — | Show toast: "Upload failed. Please try again." |

**Other activities:** None.

**System state on completion:** Avatar stored on server. `avatarUrl` is updated. Rendered via `GET /api/v1/files/avatars/{filename}`.

---

### UC-06 — Register FCM Token

**Use Case Name:** Register FCM Token

**Initial assumption:** User is logged in. Firebase SDK has provided a device FCM token.

**Normal:**
1. After login (or when Firebase calls `onNewToken`), UI calls `PATCH /api/v1/users/me/fcm-token`:
   ```json
   { "fcmToken": "..." }
   ```
2. Backend returns `200 OK` with `{ "data": null }`.
3. UI proceeds silently — no user-facing feedback needed.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Token too long (>512 chars) | `VALIDATION_ERROR` (422) | Log silently; do not show UI error (invisible to user). |
| Network failure | — | Retry silently on next app resume. |

**Other activities:** Firebase automatically calls `onNewToken` when token rotates. The app must call this endpoint again to keep the server's token current.

**System state on completion:** Server has the latest FCM token for this device. User will receive push notifications for new proposals, session events, etc.

---

## Discovery

---

### UC-07 — Browse Hotspot Map

**Use Case Name:** Browse Hotspot Map

**Initial assumption:** User is on the Home/Map screen. Authentication is optional (endpoint is public).

**Normal:**
1. UI calls `GET /api/v1/hotspots`.
2. Backend returns `200 OK` with list of hotspots, each including `id`, `name`, `lat`, `lng`, `openIntentCount`.
3. UI renders hotspot pins on the map. Each pin's visual weight (size or color) reflects `openIntentCount` — more intents = more prominent pin.
4. User taps a pin to see the hotspot's detail card (name, intent count, "Create Intent" CTA).
5. Optionally, user taps a hotspot to call `GET /api/v1/hotspots/{id}` for the detail view.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Specific hotspot not found | `HOTSPOT_NOT_FOUND` | Show toast: "This hotspot is no longer available." |
| Network failure | — | Show cached hotspot list with a "Refresh" banner. |

**Other activities:** Refresh hotspot list every time the screen gains focus or the user pulls to refresh.

**System state on completion:** Map is populated with live hotspot data. User can navigate to UC-08 to create an intent at a chosen hotspot.

---

## Walk Intent

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

---

## Proposal Negotiation

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

---

## Session Lifecycle

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

---

## GPS Tracking

---

### UC-21 — Background GPS Route Sync

**Use Case Name:** Background GPS Route Sync

**Initial assumption:** Session is in `ACTIVE` status. This task starts automatically when UC-17 (Case B) completes. It runs entirely in the background — the user should not need to interact with it.

**Normal:**
1. Android GPS service collects location fixes at regular intervals (e.g., every 5 seconds).
2. Points are buffered locally. Each point has a client-assigned `local_id` (auto-increment), `lat`, `lng`, `timestamp` (epoch ms), and `accuracy`.
3. Every 30 seconds (or when buffer reaches N points), UI calls `POST /api/v1/tracking/sync`:
   ```json
   {
     "session_id": "...",
     "points": [
       { "local_id": 1, "lat": 10.776, "lng": 106.700, "timestamp": 1744123456000, "accuracy": 5.2 },
       ...
     ]
   }
   ```
4. Backend returns `200 OK` with `{ "acknowledged_ids": [1, 2, 3, ...] }`.
5. UI marks all acknowledged `local_id`s as synced. Removes them from the local buffer.
6. Unacknowledged points remain in buffer and are retried on the next sync cycle.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Session is no longer ACTIVE (e.g., session was auto-completed after 4h) | `SESSION_NOT_ACTIVE` | Stop the sync loop silently. Show a notification: "Your walk session has ended." Navigate to history. |
| Future timestamp submitted | `INVALID_ARGUMENT` | Skip the offending point(s) client-side; log the error. |
| lat/lng out of bounds | `INVALID_ARGUMENT` | Skip the offending points client-side. |
| Session not found | `SESSION_NOT_FOUND` | Stop sync loop and navigate to history. |
| Network failure | — | Keep points in the local buffer and retry on the next cycle. Do not discard unsynced points. |

**Other activities:**
- This task must respect Android battery optimization — use `FusedLocationProviderClient` with `PRIORITY_HIGH_ACCURACY` while ACTIVE, then stop.
- The sync must stop when the session reaches any terminal state (`COMPLETED`, `ABORTED`, `CANCELLED`, `NO_SHOW`).

**System state on completion:** GPS polyline data is stored server-side and available for route replay (UC-23) after the session ends.

---

## Post-Session

---

### UC-22 — View Session History

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
4. Tapping a card navigates to session detail, with options for route replay (UC-23), review (UC-24), or report (UC-25) depending on status and time window.

**What can go wrong:**

| Condition | UI Reaction |
|-----------|------------|
| Network failure | Show cached history with "Could not refresh" banner. |

**Other activities:** None.

**System state on completion:** User sees full history. COMPLETED sessions show review prompt if not yet reviewed.

---

### UC-23 — View Session Route Replay

**Use Case Name:** View Session Route Replay

**Initial assumption:** User is on the Session History detail screen. Session is in a terminal state (`COMPLETED`, `NO_SHOW`, `CANCELLED`, or `ABORTED`).

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
| Session not in terminal state | `SESSION_NOT_FINISHED` | Show toast: "Route is only available after the walk has ended." |
| User not a participant | `SESSION_NOT_PARTICIPANT` | Show toast: "Permission denied." |
| Session not found | `SESSION_NOT_FOUND` | Show toast: "Session not found." Navigate back. |
| No GPS data (session was cancelled early) | (200 OK, empty polylines) | Show message: "No route data recorded for this session." |

**Other activities:** None.

**System state on completion:** Map shows historical route. Read-only display.

---

### UC-24 — Submit a Review

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
- Server atomically updates reviewee's `trustScore` and evaluates badge eligibility.
- Optionally refresh the partner's public profile page to show the new trust score.

**System state on completion:** Review exists in DB. Reviewee's trust score is updated (**X-4**). Review appears in `GET /api/v1/users/{revieweeId}/reviews`.

---

### UC-25 — Submit an Incident Report

**Use Case Name:** Submit an Incident Report

**Initial assumption:** Session is in `ACTIVE`, `NO_SHOW`, `COMPLETED`, or `ABORTED` status. Reporting window is open (72h for COMPLETED, 24h for ABORTED/NO_SHOW). User has not yet submitted a report for this session.

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
| Session status not reportable | `REPORT_SESSION_INVALID_STATUS` | Hide "Report" button for non-reportable sessions (PENDING, CONSUMED). |
| Reporting window has expired | `REPORT_WINDOW_EXPIRED` | Show toast: "The reporting window for this session has closed." Hide "Report" button. |
| Already reported | `REPORT_ALREADY_SUBMITTED` | Show toast: "You've already submitted a report for this session." Hide button. |
| User trying to report themselves | `REPORT_SELF_NOT_ALLOWED` | Should never happen in UI — `reportedUserId` is always the partner. |
| User not a participant | `SESSION_NOT_PARTICIPANT` | Show toast: "Permission denied." |
| Session not found | `SESSION_NOT_FOUND` | Show toast: "Session not found." |

**Other activities:** None. Report is reviewed by admins asynchronously.

**System state on completion:** Report is stored. Moderators are notified asynchronously.

---

## Social

---

### UC-26 — View a Public User Profile

**Use Case Name:** View a Public User Profile

**Initial assumption:** User taps on another user's name/avatar anywhere in the app (from session history, proposal, followers list, etc.). Authentication not required to **view** the profile; authentication is required to **act** (Follow/Block).

**Normal:**
1. UI calls `GET /api/v1/users/{userId}`.
2. Backend returns `200 OK` with public profile data.
3. UI calls `GET /api/v1/users/{userId}/badges` and `GET /api/v1/users/{userId}/stats` in parallel.
4. UI calls `GET /api/v1/users/{userId}/reviews` to show review feed.
5. UI renders full public profile page: avatar, name, bio, tags, trust score, stats, badges, reviews.
6. If viewing another user's profile (not self): show "Follow" / "Unfollow" toggle and "Block" option.
7. **Unauthenticated guard:** If the user is not logged in and taps "Follow" or "Block", do **not** call the API. Instead, navigate to the Login screen and show a prompt: "Log in to follow or block users." After successful login, return the user to this profile screen.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| User not found | `USER_NOT_FOUND` | Show toast: "User not found." Navigate back. |

**Other activities:** None.

**System state on completion:** Read-only profile view. Action buttons for follow/block are enabled.

---

### UC-27 — Follow a User

**Use Case Name:** Follow a User

**Initial assumption:** User is **authenticated**. Viewing another user's public profile (UC-26). Not currently following them. (If user is unauthenticated and taps "Follow", redirect to Login — see UC-26 step 7.)

**Normal:**
1. User taps "Follow".
2. UI optimistically updates the button to "Following".
3. UI calls `POST /api/v1/users/{userId}/follow`.
4. Backend returns `200 OK`. Follow relationship is created.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Target user not found | `SOCIAL_USER_NOT_FOUND` | Revert button. Show toast: "User not found." |
| Trying to follow self | `FOLLOW_SELF_FOLLOW_FORBIDDEN` | Hide "Follow" button for the user's own profile. |
| Already following | `FOLLOW_ALREADY_FOLLOWING` | Button should already show "Following" — this is a double-tap race. Ignore silently. |

**Other activities:** None.

**System state on completion:** Follow relationship exists. User appears in target's followers list (`GET /api/v1/users/{userId}/followers`).

---

### UC-28 — Unfollow a User

**Use Case Name:** Unfollow a User

**Initial assumption:** User is authenticated. Currently following the target user.

**Normal:**
1. User taps "Following" (toggle to unfollow).
2. UI shows confirmation: "Unfollow this user?"
3. UI optimistically updates button to "Follow".
4. UI calls `DELETE /api/v1/users/{userId}/follow`.
5. Backend returns `200 OK`. Follow relationship is removed.

**What can go wrong:**

| Condition | UI Reaction |
|-----------|------------|
| Network failure | Revert button state. Show toast: "Could not unfollow. Try again." |

**Other activities:** None.

**System state on completion:** Follow relationship removed. User no longer appears in target's followers list.

---

### UC-29 — View Followers / Following Lists

**Use Case Name:** View Followers / Following Lists

**Initial assumption:** User is on a public profile page (own or another user's).

**Normal:**
1. User taps "Followers" tab → UI calls `GET /api/v1/users/{userId}/followers`.
2. User taps "Following" tab → UI calls `GET /api/v1/users/{userId}/following`.
3. Each response returns a list of `{ userId, fullName, avatarUrl }`.
4. UI renders the list; each item is tappable and navigates to UC-26.

**What can go wrong:**

| Condition | UI Reaction |
|-----------|------------|
| Network failure | Show empty state with retry option. |

**Other activities:** None.

**System state on completion:** User sees the social graph. Read-only.

---

### UC-30 — Block a User

**Use Case Name:** Block a User

**Initial assumption:** User is **authenticated**. Viewing another user's public profile. Has a "Block" option available (via overflow menu). (If user is unauthenticated and taps "Block", redirect to Login — see UC-26 step 7.)

**Normal:**
1. User selects "Block User" from the overflow menu.
2. UI shows a strong confirmation dialog: "Block [name]? They won't be able to see your activity and you won't be matched together."
3. User confirms.
4. UI calls `POST /api/v1/users/{userId}/block`.
5. Backend returns `200 OK`. Block relationship is created. Any existing follow relationships in both directions are silently removed.
6. UI navigates back to the previous screen and removes the blocked user from visible lists.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Target user not found | `SOCIAL_USER_NOT_FOUND` | Show toast: "User not found." |
| Trying to block self | `BLOCK_SELF_BLOCK_FORBIDDEN` | Hide "Block" on own profile. |
| Already blocked | `BLOCK_ALREADY_BLOCKED` | Show "Blocked" state in UI already. Ignore. |

**Other activities:** None.

**System state on completion:** Block relationship exists. Follow relationships are torn down. Matching engine will not pair blocked users.

---

### UC-31 — Unblock a User

**Use Case Name:** Unblock a User

**Initial assumption:** User is authenticated. The blocked user appears in a "Blocked Users" settings list.

**Normal:**
1. User taps "Unblock" next to the blocked user.
2. UI calls `DELETE /api/v1/users/{userId}/block`.
3. Backend returns `200 OK`. Block relationship is removed.
4. UI removes the user from the "Blocked Users" list.

**What can go wrong:**

| Condition | UI Reaction |
|-----------|------------|
| Network failure | Show toast: "Could not unblock. Try again." |

**Other activities:** None.

**System state on completion:** Block relationship removed. Users may be matched again.

---

## Notifications

---

### UC-32 — View Notification Feed

**Use Case Name:** View Notification Feed

**Initial assumption:** User is authenticated and on the Notifications screen. Unread badge is shown on the tab icon.

**Normal:**
1. UI calls `GET /api/v1/notifications`.
2. Backend returns list of notifications (newest first), each with `type`, `payload`, `status` (`UNREAD`/`READ`), `createdAt`, `readAt`.
3. UI renders the list. `UNREAD` items are visually highlighted.
4. Key notification types and their navigation targets:

| Notification Type | Description | Action |
|---|---|---|
| `PROPOSAL_RECEIVED` | New match proposal exists | Navigate to Proposal Detail (UC-12/UC-13) |
| `PROPOSAL_ACCEPTED` | Partner accepted the proposal | Show status update; navigate to Proposal Detail |
| `SESSION_ACTIVE` | Both users activated; walk is live | Navigate to Active Session screen (UC-16) |

5. Tapping a notification marks it as read (UC-33) and navigates to the relevant screen.

**What can go wrong:**

| Condition | UI Reaction |
|-----------|------------|
| Network failure | Show cached notifications. |

**Other activities:**
- FCM push notifications should deep-link directly to the relevant screen when the app is in the background.
- Unread count badge on the Notifications tab icon should update on refresh.

**System state on completion:** Notification feed is rendered. Unread items are identifiable.

---

### UC-33 — Mark Notification as Read

**Use Case Name:** Mark Notification as Read

**Initial assumption:** User taps a notification in the feed. Notification is currently `UNREAD`.

**Normal:**
1. User taps a notification item.
2. UI optimistically marks it as `READ` (visual change).
3. UI calls `POST /api/v1/notifications/{notificationId}/read`.
4. Backend returns `200 OK`. `readAt` is set. Notification status is `READ`.
5. UI navigates to the action target (proposal, session, etc.) based on notification `type`.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Notification not found | `NOTIFICATION_NOT_FOUND` | Revert to UNREAD state. Show toast: "Could not mark as read." |
| User does not own notification | `NOTIFICATION_NOT_OWNER` | Show toast: "Permission denied." |

**Other activities:** None.

**System state on completion:** Notification status is `READ`. `readAt` is set. Unread count badge decrements.

---

## Gamification

---

### UC-34 — View User Badges

**Use Case Name:** View User Badges

**Initial assumption:** User is on any public profile page (own or another user's). Authentication not required.

**Normal:**
1. UI calls `GET /api/v1/users/{userId}/badges`.
2. Backend returns list of `{ badgeName, awardedAt }`.
3. UI renders badge chips/icons in the profile's "Achievements" section.
4. Each badge can have a tooltip explaining how it was earned.

**What can go wrong:**

| Condition | UI Reaction |
|-----------|------------|
| No badges | Show empty state: "No badges yet." |
| Network failure | Show cached badge list or hide section. |

**Other activities:** Badges are awarded server-side automatically when sessions complete. No explicit user action is needed.

**System state on completion:** Badges displayed. Read-only.

---

### UC-35 — View User Stats

**Use Case Name:** View User Stats

**Initial assumption:** User is on any public profile page. Authentication not required.

**Normal:**
1. UI calls `GET /api/v1/users/{userId}/stats`.
2. Backend returns `{ userId, totalPoints, totalDistanceKm, completedSessions, trustScore }`.
3. UI renders stats in the profile header or a stats section.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| User not found | `USER_NOT_FOUND` | Handle at the profile level (UC-26 already checks). |
| Network failure | Show cached or skeleton stats. |

**Other activities:** None.

**System state on completion:** Stats displayed. Read-only.

---

### UC-36 — View Leaderboard

**Use Case Name:** View Leaderboard

**Initial assumption:** User navigates to the Leaderboard tab. Authentication not required.

**Normal:**
1. UI calls `GET /api/v1/leaderboard`.
2. Backend returns top 50 users sorted by `totalPoints` descending, each entry including `rank`, `userId`, `totalPoints`, `totalDistanceKm`, `completedSessions`, `trustScore`.
3. UI renders a ranked list. Each row is tappable and navigates to UC-26 (public profile).
4. If the authenticated user is in the top 50, highlight their row.

**What can go wrong:**

| Condition | UI Reaction |
|-----------|------------|
| Network failure | Show cached leaderboard with "Last updated at..." label. |

**Other activities:** Refresh on screen focus or pull-to-refresh.

**System state on completion:** Leaderboard displayed. Read-only.

---

## Appendix A — Global Error Handling

All API responses follow the `ApiResponse<T>` envelope. This is the **only** error format returned by the backend — there is no `errors` array.

```json
// Success
{ "success": true, "data": { ... }, "error": null, "timestamp": "2026-04-08T10:00:00Z" }

// Failure
{ "success": false, "data": null, "error": { "code": "SOME_ERROR_CODE", "message": "Human-readable description." }, "timestamp": "2026-04-08T10:00:00Z" }
```

**Validation error format** (`VALIDATION_ERROR` / HTTP 422): `error.message` is a **single comma-separated string** of field violations, e.g. `"fullName: must not be blank, email: must be a valid email address"`. Parse this string by splitting on `", "` to render per-field inline errors.

### Critical: HTTP Status vs. Domain Error Code

> **Important for UI engineers:** `@GlobalExceptionHandler` maps **all** `DomainException`s to HTTP **400 Bad Request**, regardless of the semantic meaning of the error code. A `SESSION_NOT_FOUND` or `SESSION_NOT_PARTICIPANT` will both arrive as HTTP 400. **Do NOT use HTTP status codes alone to distinguish domain errors — always read `error.code`.**

| HTTP Status | Source | Scenario | UI Default Action |
|-------------|--------|----------|------------------|
| 400 | `DomainException` | Business rule violation (not found, wrong state, permission denied, etc.) | Read `error.code` from the response body; use the per-UC error table to react. |
| 401 | Spring Security | Token missing, expired, or invalid | Clear token from storage, navigate to Login screen. |
| 404 | `NoResourceFoundException` | URL route does not exist on the server | Show toast: "Page not found." Log to crash reporter. |
| 405 | `HttpRequestMethodNotSupportedException` | Wrong HTTP method used | Never happens in normal flow; log to crash reporter. |
| 422 | `MethodArgumentNotValidException` | Jakarta `@Valid` annotation failed | Split `error.message` on `", "` and map fragments to field-level inline errors. |
| 500 | Unhandled `Exception` | Unexpected server error | Show toast: "Something went wrong. Please try again." Log `error.code` + `error.message` to crash reporter. |
| Network timeout | — | No HTTP response received | Show toast: "Connection error. Check your internet." Offer retry. |

---

## Appendix B — Key Invariant ↔ UI Decision Map

| Invariant | Rule Summary | UI Enforcement |
|-----------|-------------|----------------|
| **I-1** | No overlapping time windows | Show `INTENT_OVERLAPPING` / `INTENT_OVERLAPPING_SESSION` as blocking dialog, not toast |
| **I-3** | An intent can only reach `CONSUMED` via the proposal acceptance flow (P-3). `CONSUMED` means the intent has been spent to create a WalkSession — it is permanently locked and cannot be cancelled, re-opened, or reused. It is **not** a user-facing error state; it is a silent terminal state. | Never show a "Cancel" or "Find Match" button for a `CONSUMED` intent. Do not surface the word "CONSUMED" to end users — these intents should simply disappear from the active intent list. If the API returns `INTENT_NOT_OPEN` for an intent the user tries to act on, refresh the list silently: the intent has likely been consumed by a concurrent proposal acceptance. |
| **I-4** | MATCHING intents show lock badge | Disable "Cancel Intent" button; show "View Proposal" instead |
| **I-6** | Terminal states are immutable | Hide all action buttons for CONSUMED, CANCELLED, EXPIRED intents and COMPLETED, NO_SHOW, CANCELLED, ABORTED sessions |
| **I-7** | Private intents need accepted friendship | Validate friend selection client-side before submit |
| **P-2** | Both users must accept proposal | Show "Waiting for partner..." state when only one has accepted |
| **P-3** | Session creation is atomic on double-accept | Navigate to session screen only when `status: "CONFIRMED"` and `session_id` is present in response |
| **P-4** | Proposal TTL is 5 minutes | Show live countdown; auto-refresh list when timer hits 0 |
| **S-2** | Session ACTIVE only when both activate | Show "Waiting for partner to arrive..." state after single activation |
| **S-3** | Activation window is `[scheduledStart − 10 min, scheduledStart + 15 min]` | Disable "I'm Here!" button outside this window; show a countdown to window open and a countdown to window close once inside |
| **S-5** | 5-minute minimum walk before complete | Disable "Complete Walk" button; show a countdown timer until it becomes enabled |
| **S-6** | Session auto-closes at 4 hours | Listen for FCM notification of auto-completion; navigate to history |
| **S-7** | Chat locked after terminal state | Disable chat input immediately upon session terminal state |
| **S-8** | Session time/location are immutable snapshots | Never offer "edit session time" after session creation |
| **X-3** | Exclude list updated on rejection | After passing a proposal, the same pair won't appear again — no UI action needed |
| **X-4** | Reputation updated on terminal session | Refresh user stats after session ends |
| **X-5** | Optimistic locking on all state changes | Handle `PROPOSAL_CONCURRENT_MODIFICATION` with retry toast; never silently discard |
