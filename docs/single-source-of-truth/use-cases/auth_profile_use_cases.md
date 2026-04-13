# WalkMate — Auth & Profile Use Cases

> Part of: [Use Cases Index](README.md)

**Domain:** Authentication, Profile Management, and Device Registration
**Last Updated:** 2026-04-12 (UC-07 to UC-13 added)

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
| UC-07 | [Login with Google (OAuth)](#uc-07--login-with-google-oauth) | `POST /api/v1/auth/google` |
| UC-08 | [Phone Sign-In — Send OTP](#uc-08--phone-sign-in--send-otp) | `POST /api/v1/auth/phone/send-otp` |
| UC-09 | [Phone Sign-In — Verify OTP](#uc-09--phone-sign-in--verify-otp) | `POST /api/v1/auth/phone/verify` |
| UC-10 | [Logout (This Device)](#uc-10--logout-this-device) | `POST /api/v1/auth/logout` |
| UC-11 | [Logout All Devices](#uc-11--logout-all-devices) | `POST /api/v1/auth/logout-all` |
| UC-12 | [Silent Token Refresh](#uc-12--silent-token-refresh) | `POST /api/v1/auth/refresh` |
| UC-13 | [Set Profile Visibility](#uc-13--set-profile-visibility) | `PATCH /api/v1/users/me/visibility` |

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

### UC-07 — Login with Google (OAuth)

**Use Case Name:** Login with Google (OAuth)

**Initial assumption:** User is unauthenticated. Google Play Services is available on the device. Firebase is initialised (`FirebaseApp.initializeApp`).

**Normal:**
1. User taps "Continue with Google" on the Auth screen.
2. Google Sign-In SDK presents the account picker.
3. User selects an account → SDK returns a **Google ID Token**.
4. App passes the Google ID Token to Firebase Auth:
   ```
   FirebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(googleIdToken, null))
   ```
5. Firebase returns a **Firebase ID Token** (JWT issued by Firebase, not Google).
6. App calls `POST /api/v1/auth/google` on a background thread:
   ```json
   { "idToken": "<Firebase ID Token>", "deviceId": "<persistent device UUID>" }
   ```
7. Backend verifies the Firebase ID Token, creates or finds the user, and returns `200 OK`:
   ```json
   {
     "data": {
       "accessToken": "...",
       "refreshToken": "...",
       "tokenType": "Bearer",
       "expiresIn": 86400000,
       "refreshTokenExpiresIn": 2592000000
     }
   }
   ```
8. App stores `accessToken` and `refreshToken` in `EncryptedSharedPreferences` via `SessionManager`.
9. App registers FCM token (UC-06) in the background.
10. App navigates to the Home screen.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| User cancels account picker | — (client-side) | No-op; remain on Auth screen. |
| Firebase `signInWithCredential` fails | Firebase exception | Show toast: "Google sign-in failed. Please try again." |
| Firebase fails to return ID token | Firebase exception | Show toast: "Google sign-in failed. Please try again." |
| Backend rejects Firebase token | `GOOGLE_LOGIN_FAILED` | Show toast: "Google sign-in failed. Please try again." |
| Network failure | `IOException` | Show toast: "Check your connection and try again." |

**Other activities:** Immediately after login, trigger FCM token registration (UC-06) in the background.

**System state on completion:** `accessToken` and `refreshToken` persisted. User lands on Home screen. A WalkMate user record exists (created if first login). All subsequent requests include `Authorization: Bearer <token>` header.

---

### UC-08 — Phone Sign-In — Send OTP

**Use Case Name:** Phone Sign-In — Send OTP

**Initial assumption:** User is unauthenticated and on the Phone Number input screen.

**Normal:**
1. User enters a phone number in local Vietnamese format (e.g. `0702341568`).
2. UI normalises the number to E.164 format: strips leading `0`, prepends `+84` (e.g. `+84702341568`).
3. UI calls `POST /api/v1/auth/phone/send-otp`:
   ```json
   { "phone": "+84702341568" }
   ```
4. Backend sends a 6-digit OTP via SMS to the phone number and returns `200 OK`:
   ```json
   { "success": true, "data": null }
   ```
5. UI navigates to the OTP verification screen and starts a 60-second resend countdown timer.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Phone not in E.164 format | `INVALID_USER_DATA` | Show toast: "Please enter a valid phone number." |
| Network failure | `IOException` | Show toast: "Check your connection and try again." |
| SMS delivery failure (backend) | `SEND_OTP_FAILED` | Show toast mapped from `UserErrorMessageMapper`. |

**Other activities:** Resend button is disabled for 60 seconds after a successful send. Tapping resend calls this use case again with the same phone number.

**System state on completion:** A time-limited OTP is pending on the backend for the given phone number. UI shows the OTP entry screen.

---

### UC-09 — Phone Sign-In — Verify OTP

**Use Case Name:** Phone Sign-In — Verify OTP

**Initial assumption:** UC-08 completed successfully. User is on the OTP verification screen with a valid pending OTP.

**Normal:**
1. User enters the 6-digit OTP code received via SMS.
2. UI validates client-side that the code is exactly 6 digits.
3. UI calls `POST /api/v1/auth/phone/verify`:
   ```json
   { "phone": "+84702341568", "code": "123456", "deviceId": "<persistent device UUID>" }
   ```
4. Backend verifies the OTP, creates or finds the user, and returns `200 OK` with the same token shape as UC-02:
   ```json
   {
     "data": {
       "accessToken": "...",
       "refreshToken": "...",
       "tokenType": "Bearer",
       "expiresIn": 86400000,
       "refreshTokenExpiresIn": 2592000000
     }
   }
   ```
5. App stores `accessToken` and `refreshToken` in `SessionManager`.
6. App registers FCM token (UC-06) in the background.
7. App navigates to the Home screen.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Code is not 6 digits | — (client-side) | Disable submit until 6 digits entered. |
| Wrong or expired OTP | `USER_OTP_INVALID` | Show error mapped from `UserErrorMessageMapper`. |
| Phone/session mismatch | `USER_OTP_INVALID` | Show same error. |
| Network failure | `IOException` | Show toast: "Check your connection and try again." |

**Other activities:** After successful verification, trigger FCM token registration (UC-06).

**System state on completion:** `accessToken` and `refreshToken` persisted. A WalkMate user record exists for this phone number. User lands on Home screen.

---

### UC-10 — Logout (This Device)

**Use Case Name:** Logout (This Device)

**Initial assumption:** User is authenticated. `deviceId` is stored in `SessionManager`.

**Normal:**
1. User triggers logout (e.g. from a Settings screen, or a future explicit logout button).
2. App calls `POST /api/v1/auth/logout` on a background thread:
   ```json
   { "deviceId": "<persistent device UUID>" }
   ```
3. Backend invalidates the refresh token associated with this specific device.
4. **Regardless of backend response or network error**, app calls `sessionManager.clearSession()` — removes `accessToken` and `refreshToken` from `EncryptedSharedPreferences`. The `deviceId` is intentionally preserved.
5. App navigates to the Auth screen.

**What can go wrong:**

| Condition | Behaviour |
|-----------|-----------|
| Network failure / backend error | Session is cleared locally anyway. User is logged out from the device. No UI error shown. |
| Backend returns non-2xx | Treated as success from the user's perspective. Session cleared locally. |

**Other activities:** None.

**System state on completion:** Local session cleared. Refresh token revoked on the backend for this device. Other devices remain unaffected. `deviceId` is preserved for next login.

---

### UC-11 — Logout All Devices

**Use Case Name:** Logout All Devices

**Initial assumption:** User is authenticated and on the Profile screen.

**Normal:**
1. User taps "Log out from all devices" in the Security section of the Profile screen.
2. UI shows a confirmation dialog: "This will sign you out on all your devices. Continue?"
3. User confirms.
4. App calls `POST /api/v1/auth/logout-all` (no request body, `Authorization: Bearer <token>` header is attached by `AuthInterceptor`).
5. Backend invalidates **all** refresh tokens for this user across all devices.
6. Backend returns `200 OK`.
7. App calls `sessionManager.clearSession()`.
8. App posts `AuthEvent.FORCE_LOGOUT` on `AuthEventBus`.
9. `MainActivity` observes the event and relaunches `AuthActivity` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` — clearing the back stack.

**What can go wrong:**

| Condition | Behaviour |
|-----------|-----------|
| Network failure / backend error | Session is cleared locally anyway. `FORCE_LOGOUT` is still posted. User is signed out locally. |
| Backend returns non-2xx | Same as above — treated as local success. |

**Other activities:** All other active sessions (on other devices) will receive a 401 on their next API call, triggering UC-12 which will fail and force-logout those devices too.

**System state on completion:** All refresh tokens for this user are invalidated in the backend. Local session cleared. App restarts at Auth screen.

---

### UC-12 — Silent Token Refresh

**Use Case Name:** Silent Token Refresh

**Initial assumption:** User is authenticated. The access token has expired (or is about to expire). A `refreshToken` is stored in `SessionManager`. This use case is **system-initiated** — never triggered directly by the user.

**Normal:**
1. Any authenticated API call returns `401 Unauthorized`.
2. OkHttp's `TokenRefreshAuthenticator.authenticate()` intercepts the 401 response.
3. A `ReentrantLock` (static, shared across threads) is acquired to serialise concurrent refresh attempts.
4. **Stale-token check:** if the token already changed while waiting on the lock (another thread refreshed it), the original request is retried immediately with the new token — no refresh call is made.
5. App calls `POST /api/v1/auth/refresh` on the current thread (OkHttp background thread):
   ```json
   { "refreshToken": "<stored refresh token>" }
   ```
6. Backend returns `200 OK` with a new token pair:
   ```json
   {
     "data": {
       "accessToken": "...",
       "refreshToken": "...",
       "tokenType": "Bearer",
       "expiresIn": 86400000,
       "refreshTokenExpiresIn": 2592000000
     }
   }
   ```
7. `SessionManager.saveAccessToken()` and `saveRefreshToken()` are called with the new tokens.
8. Lock is released.
9. The original failed request is automatically retried by OkHttp with the new `Authorization: Bearer <new token>` header.

**What can go wrong:**

| Condition | Behaviour |
|-----------|-----------|
| Refresh token is `null` | Lock released. `sessionManager.clearSession()`. `AuthEventBus.postForceLogout()`. `MainActivity` navigates to Auth screen. |
| `/auth/refresh` itself returns `401` (refresh token revoked/expired) | Infinite-loop guard triggers. `sessionManager.clearSession()`. `AuthEventBus.postForceLogout()`. App restarts at Auth screen. |
| `/auth/refresh` returns non-2xx (other error) | Same as above — session cleared, force logout. |
| Network failure (`IOException`) | OkHttp propagates the exception. The original call fails. No force-logout — transient failure. |

**Other activities:** None visible to the user. The refresh is fully transparent.

**System state on completion:** New `accessToken` and `refreshToken` stored. Original API call succeeds. User session continues uninterrupted.

---

### UC-13 — Set Profile Visibility

**Use Case Name:** Set Profile Visibility

**Initial assumption:** User is authenticated and on the Profile screen. The visibility toggle (SwitchMaterial) reflects the current `visibilityMode` from the last profile load.

**Normal:**
1. User toggles the "Visible to others" switch on the Profile screen.
2. UI maps the switch state to a `VisibilityMode`: `ON → PUBLIC`, `OFF → PRIVATE`.
3. UI calls `PATCH /api/v1/users/me/visibility`:
   ```json
   { "visibility": "PUBLIC" }
   ```
   or
   ```json
   { "visibility": "PRIVATE" }
   ```
4. Backend updates the user's visibility status and returns `200 OK`.
5. `ProfileViewModel` calls `loadProfile()` to reload the full profile state, ensuring the switch reflects the persisted value.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Backend returns non-2xx | `SET_VISIBILITY_FAILED` | Show toast with friendly error message. Switch reverts to previous state on next profile reload. |
| Network failure | `IOException` | Show toast. Switch reverts on reload. |
| 401 (token expired) | — | `TokenRefreshAuthenticator` retries transparently (UC-12). |

**Other activities:** A successful visibility change affects whether this user appears in other users' hotspot discovery results (UC-14 in discovery_use_cases.md).

**System state on completion:** User's visibility preference updated in the backend. Profile screen reflects the new state after reload.
