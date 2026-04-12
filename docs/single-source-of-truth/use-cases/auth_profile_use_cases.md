# WalkMate — Auth & Profile Use Cases

> Part of: [Use Cases Index](README.md)

**Domain:** Authentication, Profile Management, and Device Registration
**Last Updated:** 2026-04-12

---

## Table of Contents

| UC# | Use Case | API Endpoint |
|-----|----------|--------------|
| UC-01 | [Register Account](#uc-01--register-account) | `POST /api/v1/auth/register` |
| UC-02 | [Login](#uc-02--login) | `POST /api/v1/auth/login` |
| UC-03 | [View My Profile](#uc-03--view-my-profile) | `GET /api/v1/profile/me` |
| UC-04 | [Edit My Profile](#uc-04--edit-my-profile) | `PUT /api/v1/profile/me` |
| UC-05 | [Upload Avatar](#uc-05--upload-avatar) | `POST /api/v1/profile/avatar` |
| UC-06 | [Register FCM Token](#uc-06--register-fcm-token) | `PATCH /api/v1/users/me/fcm-token` |

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
