# Implementation Plan: Sync Multi-Provider Sign-In

**Feature:** Account Linking — Google Sign-In ↔ Email/Password  
**Branch:** `dev/enhance`  
**Date:** 2026-05-08  
**Scope:** Backend (Spring Boot, DDD-lite) + Frontend (Android Java, MVVM)

---

## 1. Problem Statement

| Scenario | Current behaviour | Expected behaviour |
|---|---|---|
| Register with email that is already Google-only | `400 USER_EMAIL_ALREADY_EXISTS` | Show actionable message; user can use Forgot Password to set a password |
| Google Sign-In with email that already has a password account | Already handled (A2 merge) | ✅ Works |
| Google-only user wants to add a password (logged out) | Stuck in OTP loop with no email arriving | Forgot Password OTP flow — works for all account types |
| Google-only user wants to add a password (logged in) | No UI | `Profile → Security → Set Password` |
| Password user wants to change password | No UI | `Profile → Security → Change Password` |

The root policy:

```
One verified email = one WalkMate user.
One WalkMate user can have multiple login methods.
```

---

## 2. Current State Analysis

### 2.1 What Already Works

| Component | Status | Notes |
|---|---|---|
| `UserCommandService.loginOrRegisterWithGoogle()` | ✅ Done | `resolveByEmailOrCreate()` merges a LOCAL account when Google signs in with same email |
| `User.linkGoogleAccount()` | ✅ Done | Sets `providerSubject`; has conflict guard |
| `User.resetPassword()` | ⚠️ Will be deleted | Blocks Google-only accounts — replaced by `setOrChangePassword()` in Phase 0 |
| Forgot Password (3-step OTP flow) | ✅ Done | Currently LOCAL-only; Phase 0 opens it to all account types |
| `UserErrorCode.USER_PROVIDER_CONFLICT` | ✅ Done | Raised when a different Google sub tries to link |

### 2.2 Gaps — Backend

| Gap | Location | Description |
|---|---|---|
| **G-B1** | `UserCommandService.registerUser()` | Blunt `USER_EMAIL_ALREADY_EXISTS` with no guidance |
| **G-B2** | `UserCommandService.requestPasswordReset()` | Silently drops Google-only requests — frontend navigates to OTP screen; user waits for email that never comes |
| **G-B3** | `UserCommandService.confirmPasswordReset()` | Calls `user.resetPassword()` which explicitly blocks Google-only accounts |
| **G-B4** | `UserCommandService` | No `setOrChangePassword()` use-case for authenticated users |
| **G-B5** | `UserController` | Missing `POST /api/v1/users/me/password` and `GET /api/v1/users/me/security` |
| **G-B6** | `UserQueryService` | No `getAccountSecurityInfo()` to expose `hasPassword`/`hasGoogle` flags |
| **G-B7** | `User` domain | No `setOrChangePassword()` method; `resetPassword()` must be replaced |

### 2.3 Gaps — Frontend

| Gap | Location | Description |
|---|---|---|
| **G-F1** | `RegisterViewModel` | Generic error toast for email-already-exists |
| **G-F2** | `UserErrorMessageMapper` | No entry for `USER_EMAIL_GOOGLE_ONLY` |
| **G-F3** | Profile screen | No Security / Login Methods section |
| **G-F4** | `UserApiService` | Missing `getAccountSecurityInfo()` and `setOrChangePassword()` calls |
| **G-F5** | `UserRepository` / `UserRepositoryImpl` | Missing domain contracts for new operations |

### 2.4 Data Model Assessment

The current `user_account` schema is **sufficient without migration**:

| State | `provider` col | `password_hash` | `provider_subject` |
|---|---|---|---|
| LOCAL only | `LOCAL` | not null | null |
| Google only | `GOOGLE` | null | not null |
| Linked — originally LOCAL | `LOCAL` | not null | not null |
| Linked — originally GOOGLE | `GOOGLE` | not null | not null |

`provider` is the original registration method and is **never changed**. When a Google-only user sets a password, `provider` stays `GOOGLE`. When a LOCAL user links Google, `provider` stays `LOCAL`.

> **Critical coding rule:** Never use `provider` to determine login methods. Always use:
> - `passwordHash != null` → user can log in with password
> - `providerSubject != null` → user can log in with Google

### 2.5 SecurityConfig — No Change Required

`SecurityConfig.java` ends with `anyRequest().authenticated()`. The new endpoints (`/api/v1/users/me/password`, `/api/v1/users/me/security`) are covered automatically. Do not add `permitAll()` for these paths.

---

## 3. Implementation Phases

---

### Phase 0 — Unify Forgot Password to Work for All Account Types (G-B2, G-B3, G-B7)

**Effort:** Small. No new files. Modifies two existing methods + deletes one domain method.

**Goal:** The Forgot Password OTP flow works identically for LOCAL accounts, Google-only accounts, and Linked accounts. A Google-only user who goes to Forgot Password gets an OTP, verifies it, and sets a password — their account becomes Linked.

#### Backend Step 1 — Replace `User.resetPassword()` with `User.setOrChangePassword()`

Add new domain method to `User.java`:

```java
/**
 * Sets or changes the password hash on this account.
 * Works for all account types including Google-only (first-time set).
 * The caller is responsible for verifying currentPassword when passwordHash is already set.
 */
public void setOrChangePassword(String newPasswordHash) {
    this.passwordHash = requireText(newPasswordHash, "Password hash is required");
}
```

Delete `resetPassword()` from `User.java` entirely — it blocks Google-only accounts and is replaced by `setOrChangePassword()`.

#### Backend Step 2 — Open `requestPasswordReset()` to all account types

**`UserCommandService.requestPasswordReset()`** — remove the Google-only block:

```java
// BEFORE
if (user.getPasswordHash() == null) return;  // silently drops Google-only

// AFTER — remove those two lines entirely.
// Send OTP to all accounts with a registered email, regardless of provider.
// Google-only accounts are setting a password for the first time via this flow.
```

The rest of the method (cooldown, OTP generation, email dispatch) is unchanged.

#### Backend Step 3 — Update `confirmPasswordReset()` to call the new method

**`UserCommandService.confirmPasswordReset()`** — one-line change:

```java
// BEFORE
user.resetPassword(passwordEncoder.encode(command.newPassword()));

// AFTER
user.setOrChangePassword(passwordEncoder.encode(command.newPassword()));
```

No other changes. The 3-step OTP flow (request → verify → confirm) is otherwise identical for all account types.

#### Frontend — No Changes for Phase 0

The existing `ForgotPasswordActivity`, `EmailInputFragment`, `OtpVerifyFragment`, and `NewPasswordFragment` require no changes. The flow already handles any HTTP 200 from the backend correctly.

---

### Phase 1 — Better Register Error (G-B1, G-F1, G-F2)

**Effort:** Small. No new endpoints.

**Goal:** When a user tries to register with an email that belongs to a Google-only account, show an actionable message pointing them to Forgot Password or Google Sign-In — not a generic "email already exists" error.

#### Backend

**`UserCommandService.registerUser()`** — replace the blunt guard:

```java
// BEFORE
userRepository.findByEmail(normalizedEmail)
    .ifPresent(existing -> {
        throw new DomainException(UserErrorCode.USER_EMAIL_ALREADY_EXISTS);
    });

// AFTER
userRepository.findByEmail(normalizedEmail).ifPresent(existing -> {
    // Check both conditions: passwordHash==null alone could be a corrupted row.
    // Only report Google-only when providerSubject is actually set.
    if (existing.getPasswordHash() == null && existing.getProviderSubject() != null) {
        throw new DomainException(UserErrorCode.USER_EMAIL_GOOGLE_ONLY);
    }
    throw new DomainException(UserErrorCode.USER_EMAIL_ALREADY_EXISTS);
});
```

Add to `UserErrorCode`:

```java
USER_EMAIL_GOOGLE_ONLY("This email is registered via Google. Sign in with Google, or use Forgot Password to set a password.")
```

> The message mentions Forgot Password because Phase 0 now makes it a valid path.

#### Frontend

Add `USER_EMAIL_GOOGLE_ONLY` mapping in `UserErrorMessageMapper`:

```java
case "USER_EMAIL_GOOGLE_ONLY":
    return new ErrorResult(R.string.error_email_google_only, ActionType.TOAST);
```

Add string in `res/values/strings.xml`:

```xml
<string name="error_email_google_only">This email is linked to a Google account. Sign in with Google, or use Forgot Password to set a password.</string>
```

---

### Phase 2 — Set/Change Password Endpoint for Authenticated Users (G-B4, G-B5, G-B6, G-F4, G-F5)

**Effort:** Medium. New authenticated endpoints.

**Goal:** A logged-in user can set a password (Google-only) or change their existing password (LOCAL or Linked) from Profile → Security without going through the Forgot Password OTP flow.

> `User.setOrChangePassword()` is introduced in Phase 0 Step 1. Phase 2 builds the authenticated use-case on top of it.

#### Backend

**New Command record — `application/user/SetOrChangePasswordCommand.java`:**

```java
public record SetOrChangePasswordCommand(
    UUID userId,
    String currentPassword,  // null when passwordHash == null (first-time set)
    String newPassword
) {}
```

**New use-case in `UserCommandService`:**

```java
@Transactional
public void setOrChangePassword(SetOrChangePasswordCommand command) {
    User user = userRepository.findById(command.userId().toString())
            .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

    User.validatePasswordStrength(command.newPassword());

    if (user.getPasswordHash() != null) {
        // Already has a password — always require currentPassword,
        // even if the account also has Google connected.
        // isBlank() guards against frontend sending "" instead of null.
        if (command.currentPassword() == null || command.currentPassword().isBlank()) {
            throw new DomainException(UserErrorCode.INVALID_USER_DATA, "Current password is required");
        }
        user.validateCredentials(command.currentPassword(), passwordEncoder::matches);
    }

    user.setOrChangePassword(passwordEncoder.encode(command.newPassword()));
    userRepository.save(user);
}
```

**New Request DTO — `presentation/dto/request/user/SetOrChangePasswordRequest.java`:**

```java
// confirmNewPassword is intentionally omitted — validated in SecurityViewModel before API call.
public record SetOrChangePasswordRequest(
    @Size(max = 128) String currentPassword,
    @NotBlank @Size(min = 8, max = 128) String newPassword
) {}
```

**New endpoints in `UserController`:**

```java
// POST /api/v1/users/me/password
@PostMapping("/me/password")
public ResponseEntity<ApiResponse<Void>> setOrChangePassword(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody SetOrChangePasswordRequest request) {

    UUID userId = UUID.fromString(principal.userId());
    userCommandService.setOrChangePassword(
            new SetOrChangePasswordCommand(userId, request.currentPassword(), request.newPassword()));
    return ResponseEntity.ok(ApiResponse.success(null));
}

// GET /api/v1/users/me/security
@GetMapping("/me/security")
public ResponseEntity<ApiResponse<AccountSecurityInfoResponse>> getSecurityInfo(
        @AuthenticationPrincipal UserPrincipal principal) {

    UUID userId = UUID.fromString(principal.userId());
    AccountSecurityInfo info = userQueryService.getAccountSecurityInfo(userId);
    return ResponseEntity.ok(ApiResponse.success(
            new AccountSecurityInfoResponse(info.hasPassword(), info.hasGoogle())));
}
```

> `userId` comes exclusively from `@AuthenticationPrincipal`. Never accept it from the request body.

**New query in `UserQueryService`:**

```java
public AccountSecurityInfo getAccountSecurityInfo(UUID userId) {
    User user = userRepository.findById(userId.toString())
            .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));
    // Use nullable columns — NOT the provider field
    return new AccountSecurityInfo(
        user.getPasswordHash() != null,
        user.getProviderSubject() != null
    );
}
```

New records:

```java
// application/user/AccountSecurityInfo.java
public record AccountSecurityInfo(boolean hasPassword, boolean hasGoogle) {}

// presentation/dto/response/user/AccountSecurityInfoResponse.java
public record AccountSecurityInfoResponse(boolean hasPassword, boolean hasGoogle) {}
```

---

### Phase 3 — Profile Security UI (G-F3, G-F4, G-F5)

**Effort:** Medium. New sub-feature under Profile.

#### Architecture placement

```
ui/profile/
└── security/
    ├── SecurityFragment.java
    ├── SecurityViewModel.java
    ├── SecurityViewModelFactory.java
    └── SecurityUiState.java
```

#### `SecurityUiState.java`

```java
public class SecurityUiState {
    private final boolean isLoading;
    private final boolean hasPassword;
    private final boolean hasGoogle;
    private final boolean isSuccess;
    private final String error;
    // getters + static initial()
}
```

#### `SecurityViewModel.java` responsibilities

```
On init:
  → call UserRepository.getAccountSecurityInfo() → post initial UiState

setOrChangePassword(currentPass, newPass, confirmNewPass):
  1. Validate confirmNewPass == newPass (UI concern, not sent to backend)
  2. Call UserRepository.setOrChangePassword(currentPass, newPass)
  3. On success:
     - Call getAccountSecurityInfo() again to refresh hasPassword flag
     - Post UiState with updated hasPassword=true, isSuccess=true
  4. On error:
     - USER_INVALID_CREDENTIALS → post message "Current password is incorrect."
       (do NOT delegate to shared UserErrorMessageMapper — its string says
       "Invalid email or password" which is login-context language)
     - USER_PASSWORD_TOO_WEAK / INVALID_USER_DATA → delegate to mapper
```

#### `SecurityFragment.java` UI logic

```
Observe SecurityUiState:
  if hasPassword == false → show "Password: Not Set" + [Set Password] button
  if hasPassword == true  → show "Password: Set" + [Change Password] button
  if hasGoogle == true    → show "Google: Connected"
  if hasGoogle == false   → show "Google: Not Connected" (no action needed yet)
```

#### `UserApiService.java` — new calls

Belongs in `UserApiService` (authenticated, `/api/v1/users/me/...`), **not** `AuthApiService`.

```java
@GET("api/v1/users/me/security")
Call<ApiResponse<AccountSecurityInfoResponseDto>> getAccountSecurityInfo();

@POST("api/v1/users/me/password")
Call<ApiResponse<Void>> setOrChangePassword(@Body SetOrChangePasswordRequestDto body);
```

Authorization headers are injected by the existing `AuthInterceptor`.

#### `UserRepository.java` domain interface — additions

```java
void getAccountSecurityInfo(DomainCallback<AccountSecurityInfo> callback);
void setOrChangePassword(String currentPassword, String newPassword, DomainCallback<Void> callback);
```

#### Set/Change Password inline form

```
hasPassword == false (Set Password):
  - New Password field
  - Confirm New Password field
  - [Save] → ViewModel validates confirm, then calls API

hasPassword == true (Change Password):
  - Current Password field
  - New Password field
  - Confirm New Password field
  - [Save] → ViewModel validates confirm, then calls API
```

---

## 4. Full Change Inventory

### Backend files

| File | Change type | Description |
|---|---|---|
| `domain/user/User.java` | Edit | Add `setOrChangePassword()`; **delete** `resetPassword()` |
| `domain/user/UserErrorCode.java` | Edit | Add `USER_EMAIL_GOOGLE_ONLY` |
| `application/user/UserCommandService.java` | Edit | Remove Google-only block in `requestPasswordReset()`; update `confirmPasswordReset()` to call `setOrChangePassword()`; update `registerUser()` guard; add `setOrChangePassword()` use-case |
| `application/user/SetOrChangePasswordCommand.java` | New | Command record |
| `application/user/AccountSecurityInfo.java` | New | Query result record |
| `application/user/UserQueryService.java` | Edit | Add `getAccountSecurityInfo()` |
| `presentation/dto/request/user/SetOrChangePasswordRequest.java` | New | Request DTO |
| `presentation/dto/response/user/AccountSecurityInfoResponse.java` | New | Response DTO |
| `presentation/controller/user/UserController.java` | Edit | Add `POST /me/password` and `GET /me/security` |
| `infrastructure/config/SecurityConfig.java` | **No change** | `anyRequest().authenticated()` already covers new endpoints |

### Frontend files

| File | Change type | Description |
|---|---|---|
| `core/util/UserErrorMessageMapper.java` | Edit | Add `USER_EMAIL_GOOGLE_ONLY` case |
| `res/values/strings.xml` | Edit | Add `error_email_google_only` string |
| `data/datasource/remote/api/UserApiService.java` | Edit | Add `getAccountSecurityInfo()` and `setOrChangePassword()` |
| `data/datasource/remote/dto/request/user/SetOrChangePasswordRequestDto.java` | New | Request DTO |
| `data/datasource/remote/dto/response/user/AccountSecurityInfoResponseDto.java` | New | Response DTO |
| `domain/user/UserRepository.java` | Edit | Add `getAccountSecurityInfo()` and `setOrChangePassword()` |
| `data/repository/UserRepositoryImpl.java` | Edit | Implement new domain contracts |
| `ui/profile/security/SecurityUiState.java` | New | UiState |
| `ui/profile/security/SecurityViewModel.java` | New | ViewModel |
| `ui/profile/security/SecurityViewModelFactory.java` | New | Factory |
| `ui/profile/security/SecurityFragment.java` | New | Fragment |
| Profile screen layout | Edit | Add Security section entry → navigates to SecurityFragment |

### No database migration required

The existing `password_hash` (nullable) and `provider_subject` (nullable) columns fully express all account states.

---

## 5. API Contract Summary

### POST `/api/v1/users/me/password` — Set or Change Password

**Auth:** Bearer JWT required

**Request:**
```json
{
  "currentPassword": "OldPass123",
  "newPassword": "NewPass456!"
}
```

- `currentPassword`: required only when `hasPassword == true`
- Confirm-password match validated client-side in `SecurityViewModel`

**Responses:**
```
200 OK                        → password set/changed
400 USER_INVALID_CREDENTIALS  → wrong currentPassword
400 USER_PASSWORD_TOO_WEAK    → newPassword too weak
400 INVALID_USER_DATA         → currentPassword missing when required
```

### GET `/api/v1/users/me/security` — Account Security Info

**Auth:** Bearer JWT required

**Response:**
```json
{
  "success": true,
  "data": {
    "hasPassword": false,
    "hasGoogle": true
  }
}
```

---

## 6. Implementation Order

```
Step 1:  User.setOrChangePassword() — new domain method; delete resetPassword()   (~15 min)
Step 2:  UserCommandService — remove Google-only block from requestPasswordReset() (~10 min)
Step 3:  UserCommandService.confirmPasswordReset() — call setOrChangePassword()    (~5 min)
Step 4:  UserCommandService.registerUser() — update guard + USER_EMAIL_GOOGLE_ONLY (~15 min)
Step 5:  UserErrorCode — add USER_EMAIL_GOOGLE_ONLY                                (~5 min)
Step 6:  UserErrorMessageMapper + strings.xml — frontend error message             (~10 min)
Step 7:  SetOrChangePasswordCommand + UserCommandService.setOrChangePassword()     (~25 min)
Step 8:  AccountSecurityInfo + UserQueryService.getAccountSecurityInfo()           (~20 min)
Step 9:  Request/Response DTOs + UserController new endpoints                      (~20 min)
Step 10: Frontend DTOs + UserApiService additions                                  (~20 min)
Step 11: UserRepository interface + UserRepositoryImpl                             (~20 min)
Step 12: SecurityFragment + ViewModel + UiState                                    (~45 min)
Step 13: Wire SecurityFragment into Profile screen                                 (~15 min)
```

Realistic effort (including XML layouts, navigation wiring, manual QA):

```
Backend  (Steps 1–9):  ~1 session (~3 hours)
Frontend (Steps 10–13): ~1 session (~3–4 hours)
Polish + QA:            ~1–2 hours
```

---

## 7. Complete User Flow Summary

```
Google-only user, logged out, wants a password:
  → Forgot Password → enter email → receive OTP → verify → set new password
  → Account becomes Linked (provider=GOOGLE, passwordHash!=null, providerSubject!=null)
  → Can now log in with either Google or email+password

Google-only user, logged in, wants a password:
  → Profile → Security → Set Password → enter new password
  → Same result: account becomes Linked

LOCAL user, logged in, wants to change password:
  → Profile → Security → Change Password → enter current + new password

Google-only user tries to Register with same email:
  → USER_EMAIL_GOOGLE_ONLY: "Sign in with Google, or use Forgot Password to set a password."
```

---

## 8. Edge Cases

| Case | Handling |
|---|---|
| Google-only user uses Forgot Password | ✅ Phase 0: OTP sent normally; `confirmPasswordReset()` calls `setOrChangePassword()` — no block |
| Google-only user calls `POST /me/password` (set, no currentPassword) | ✅ Service skips `validateCredentials` when `passwordHash == null` |
| Linked user calls `POST /me/password` (change, has both Google + password) | ✅ `passwordHash != null` → requires `currentPassword`; Google connection is irrelevant |
| User calls `POST /me/password` with empty string for `currentPassword` | `isBlank()` guard catches it → `INVALID_USER_DATA` instead of wrong-password error |
| Google Sign-In with email that has both password and Google already linked | `findByProviderSubject` finds user immediately; `recordLogin()` called; no double-link |
| `confirmNewPassword` mismatch in Profile → Security | Caught in `SecurityViewModel` before API call; never reaches backend |
| `passwordHash == null && providerSubject == null` (corrupted row) registers | Falls through to `USER_EMAIL_ALREADY_EXISTS`, not `USER_EMAIL_GOOGLE_ONLY` |
| After set password success in SecurityFragment | ViewModel reloads `getAccountSecurityInfo()` → `hasPassword` flips to `true`; UI updates |
