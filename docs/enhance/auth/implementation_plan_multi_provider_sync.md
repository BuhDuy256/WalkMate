# Implementation Plan: Sync Multi-Provider Sign-In

**Feature:** Account Linking — Google Sign-In ↔ Email/Password  
**Branch:** `dev/enhance`  
**Date:** 2026-05-08  
**Scope:** Backend (Spring Boot, DDD-lite) + Frontend (Android Java, MVVM)

---

## 1. Problem Statement

Two isolated login methods currently exist:

| Scenario | Current behaviour | Expected behaviour |
|---|---|---|
| Register with email that is already Google-only | `400 USER_EMAIL_ALREADY_EXISTS` | Guide user to login with Google, then set a password in Profile |
| Google Sign-In with email that already has a password account | Already handled (A2 merge via `linkGoogleAccount`) | ✅ Works |
| Google-only user wants to add a password | No endpoint, no UI | `Profile → Security → Set Password` flow |
| Password user wants to change password | No endpoint, no UI | `Profile → Security → Change Password` flow |

The root policy to enforce:

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
| `User.resetPassword()` | ✅ Done | Blocks Google-only accounts (`provider == GOOGLE && passwordHash == null`) |
| Forgot Password (3-step OTP flow) | ✅ Done | Only for accounts with `passwordHash != null` |
| `UserErrorCode.USER_PROVIDER_CONFLICT` | ✅ Done | Raised when a different Google sub tries to link |

### 2.2 Gaps — Backend

| Gap | Location | Description |
|---|---|---|
| **G-B1** | `UserCommandService.registerUser()` | Throws blunt `USER_EMAIL_ALREADY_EXISTS` without checking whether the existing account is Google-only |
| **G-B2** | `UserCommandService` | No `setOrChangePassword()` use-case for authenticated users |
| **G-B3** | `UserController` | Missing `POST /api/v1/users/me/password` and `GET /api/v1/users/me/security` endpoints |
| **G-B4** | `UserQueryService` | No `getAccountSecurityInfo()` to expose `hasPassword` / `hasGoogle` flags |
| **G-B5** | `User` domain | No `setOrChangePassword()` domain method (distinct from `resetPassword()` which blocks Google-only) |

### 2.3 Gaps — Frontend

| Gap | Location | Description |
|---|---|---|
| **G-F1** | `RegisterViewModel` | Generic error toast; no special handling for `USER_EMAIL_GOOGLE_ONLY` |
| **G-F2** | `UserErrorMessageMapper` | No entry for `USER_EMAIL_GOOGLE_ONLY` |
| **G-F3** | Profile screen | No Security / Login Methods section |
| **G-F4** | `UserApiService` | Missing `getAccountSecurityInfo()` and `setOrChangePassword()` calls |
| **G-F5** | `UserRepository` / `UserRepositoryImpl` | Missing domain contracts for the new operations |

### 2.4 Data Model Assessment

The current `user_account` schema is **sufficient without migration**:

| State | `provider` col | `password_hash` | `provider_subject` |
|---|---|---|---|
| LOCAL only | `LOCAL` | not null | null |
| Google only | `GOOGLE` | null | not null |
| Linked — originally LOCAL | `LOCAL` | not null | not null |
| Linked — originally GOOGLE | `GOOGLE` | not null | not null |

There are two linked states because `provider` is the original registration method and is never changed. When a Google-only user sets a password via Profile → Security, their `provider` stays `GOOGLE` — do not update it. When a LOCAL user signs in with Google, `provider` stays `LOCAL` and `providerSubject` is set.

No schema migration is needed.

> **Decision:** Do NOT add a `LINKED` enum value — derive state from the two nullable columns.

> **Critical coding rule:** Never use `provider` to determine which login methods a user has. Always use:
> - `passwordHash != null` → user can log in with a password
> - `providerSubject != null` → user can log in with Google
>
> The `provider` field only reflects the *original registration method*, not the current set of linked providers.

### 2.5 SecurityConfig — No Change Required

`SecurityConfig.java` ends with `anyRequest().authenticated()` as a catch-all. The new endpoints (`/api/v1/users/me/password`, `/api/v1/users/me/security`) fall under this rule automatically — they require a valid JWT without any explicit addition. Do not add a `permitAll()` rule for these paths.

---

## 3. Implementation Phases

### Phase 1 — Better Register Error (G-B1, G-F1, G-F2)

**Effort:** Small. No new endpoints.

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
USER_EMAIL_GOOGLE_ONLY("This email is registered via Google. Please sign in with Google, then set a password in your Profile.")
```

#### Frontend

Add `USER_EMAIL_GOOGLE_ONLY` mapping in `UserErrorMessageMapper`:
```java
case "USER_EMAIL_GOOGLE_ONLY":
    return new ErrorResult(R.string.error_email_google_only, ErrorTarget.GLOBAL);
```

Add string in `res/values/strings.xml`:
```xml
<string name="error_email_google_only">This email is already linked to a Google account. Sign in with Google, then set a password in your Profile.</string>
```

---

### Phase 2 — Set/Change Password Endpoint (G-B2 through G-B5, G-F4, G-F5)

**Effort:** Medium. New authenticated endpoints + new domain method.

#### Backend

**Step 1 — New domain method in `User.java`:**

```java
/**
 * Sets or changes the password for any authenticated user.
 * Unlike resetPassword(), this works for Google-only accounts (first-time set).
 * The caller (application service) is responsible for verifying currentPassword
 * when passwordHash is already set.
 */
public void setOrChangePassword(String newPasswordHash) {
    this.passwordHash = requireText(newPasswordHash, "Password hash is required");
}
```

**Step 2 — New Command record in `application/user/`:**

```java
// SetOrChangePasswordCommand.java
public record SetOrChangePasswordCommand(
    UUID userId,
    String currentPassword,  // null only when passwordHash == null (Google-only, first-time set)
    String newPassword
) {}
```

**Step 3 — New use-case method in `UserCommandService`:**

```java
@Transactional
public void setOrChangePassword(SetOrChangePasswordCommand command) {
    User user = userRepository.findById(command.userId().toString())
            .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

    User.validatePasswordStrength(command.newPassword());

    if (user.getPasswordHash() != null) {
        // User already has a password (LOCAL or Linked) — always require currentPassword.
        // This rule applies even when the user also has Google connected.
        // isBlank() guards against frontend sending an empty string instead of null.
        if (command.currentPassword() == null || command.currentPassword().isBlank()) {
            throw new DomainException(UserErrorCode.INVALID_USER_DATA, "Current password is required");
        }
        user.validateCredentials(command.currentPassword(), passwordEncoder::matches);
    }

    user.setOrChangePassword(passwordEncoder.encode(command.newPassword()));
    userRepository.save(user);
}
```

**Step 4 — New Request DTO in `presentation/dto/request/user/`:**

```java
// SetOrChangePasswordRequest.java
// confirmNewPassword is intentionally omitted — password confirmation
// is a UI concern and is validated in SecurityViewModel before API call.
public record SetOrChangePasswordRequest(
    @Size(max = 128) String currentPassword,
    @NotBlank @Size(min = 8, max = 128) String newPassword
) {}
```

**Step 5 — New endpoints in `UserController`:**

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

> Both endpoints extract `userId` exclusively from `@AuthenticationPrincipal`. No `userId` is accepted from the request body.

**Step 6 — Query in `UserQueryService`:**

```java
public AccountSecurityInfo getAccountSecurityInfo(UUID userId) {
    User user = userRepository.findById(userId.toString())
            .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));
    // Use nullable columns — NOT the provider field — to determine login methods
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
- On init: call UserRepository.getAccountSecurityInfo() → post initial UiState

- setOrChangePassword(currentPass, newPass, confirmNewPass):
    1. Validate confirmNewPass == newPass here (UI concern, not sent to backend)
    2. Call UserRepository.setOrChangePassword(currentPass, newPass)
    3. On success:
       - Call getAccountSecurityInfo() again to refresh hasPassword flag
       - Post UiState with hasPassword=true, clear password fields
    4. On error:
       - For USER_INVALID_CREDENTIALS: post error with message
         "Current password is incorrect." (do NOT delegate to shared
         UserErrorMessageMapper — its message is "Invalid email or password"
         which is login-context language and confusing here)
       - For USER_PASSWORD_TOO_WEAK: delegate to mapper (message is appropriate)
       - For INVALID_USER_DATA: delegate to mapper
```

#### `SecurityFragment.java` UI logic

```
Observe SecurityUiState:
  - if hasPassword == false: show "Password: Not Set" + [Set Password] button
  - if hasPassword == true:  show "Password: Set" + [Change Password] button
  - if hasGoogle == true:    show "Google: Connected"
  - if hasGoogle == false:   show "Google: Not Connected" (no action needed yet)
```

#### `UserApiService.java` — new calls

These belong in `UserApiService` (authenticated, `/api/v1/users/me/...`), **not** `AuthApiService` (public, `/api/v1/auth/...`).

```java
// GET /api/v1/users/me/security
@GET("api/v1/users/me/security")
Call<ApiResponse<AccountSecurityInfoResponseDto>> getAccountSecurityInfo();

// POST /api/v1/users/me/password
@POST("api/v1/users/me/password")
Call<ApiResponse<Void>> setOrChangePassword(@Body SetOrChangePasswordRequestDto body);
```

Authorization headers are injected by the existing `AuthInterceptor` — no manual `@Header` needed.

#### `UserRepository.java` (domain interface) — additions

```java
void getAccountSecurityInfo(DomainCallback<AccountSecurityInfo> callback);
void setOrChangePassword(String currentPassword, String newPassword, DomainCallback<Void> callback);
```

#### Set/Change Password inline form

`SecurityFragment` shows an inline form (or `BottomSheetDialogFragment`):

```
If hasPassword == false (Set Password):
  - New Password field
  - Confirm New Password field
  - [Save] → ViewModel validates confirm match, then calls API

If hasPassword == true (Change Password):
  - Current Password field
  - New Password field
  - Confirm New Password field
  - [Save] → ViewModel validates confirm match, then calls API
```

---

## 4. Full Change Inventory

### Backend files

| File | Change type | Description |
|---|---|---|
| `domain/user/UserErrorCode.java` | Edit | Add `USER_EMAIL_GOOGLE_ONLY` |
| `domain/user/User.java` | Edit | Add `setOrChangePassword()` method |
| `application/user/SetOrChangePasswordCommand.java` | New | Command record |
| `application/user/AccountSecurityInfo.java` | New | Query result record |
| `application/user/UserCommandService.java` | Edit | Add `setOrChangePassword()` use-case; update `registerUser()` guard |
| `application/user/UserQueryService.java` | Edit | Add `getAccountSecurityInfo()` |
| `presentation/dto/request/user/SetOrChangePasswordRequest.java` | New | Request DTO (no `confirmNewPassword` field) |
| `presentation/dto/response/user/AccountSecurityInfoResponse.java` | New | Response DTO |
| `presentation/controller/user/UserController.java` | Edit | Add `POST /me/password` and `GET /me/security` |
| `infrastructure/config/SecurityConfig.java` | **No change** | `anyRequest().authenticated()` already covers new endpoints |

### Frontend files

| File | Change type | Description |
|---|---|---|
| `core/util/UserErrorMessageMapper.java` | Edit | Add `USER_EMAIL_GOOGLE_ONLY` case |
| `res/values/strings.xml` | Edit | Add `error_email_google_only` string |
| `data/datasource/remote/api/UserApiService.java` | Edit | Add `getAccountSecurityInfo()` and `setOrChangePassword()` calls |
| `data/datasource/remote/dto/request/user/SetOrChangePasswordRequestDto.java` | New | Request DTO |
| `data/datasource/remote/dto/response/user/AccountSecurityInfoResponseDto.java` | New | Response DTO |
| `domain/user/UserRepository.java` | Edit | Add `getAccountSecurityInfo()` and `setOrChangePassword()` |
| `data/repository/UserRepositoryImpl.java` | Edit | Implement new domain contracts |
| `ui/profile/security/SecurityUiState.java` | New | UiState |
| `ui/profile/security/SecurityViewModel.java` | New | ViewModel |
| `ui/profile/security/SecurityViewModelFactory.java` | New | Factory |
| `ui/profile/security/SecurityFragment.java` | New | Fragment |
| Profile screen layout | Edit | Add Security section entry point that navigates to SecurityFragment |

### No database migration required

The existing `password_hash` (nullable) and `provider_subject` (nullable) columns fully express all account states.

---

## 5. API Contract Summary

### POST `/api/v1/users/me/password` — Set or Change Password

**Auth:** Bearer JWT required (via `AuthInterceptor`)

**Request:**
```json
{
  "currentPassword": "OldPass123",
  "newPassword": "NewPass456!"
}
```

- `currentPassword`: required only when `hasPassword == true`; ignored (and should not be sent) when `hasPassword == false`
- Confirm password match is validated client-side in `SecurityViewModel` before this call is made

**Responses:**
```
200 OK                           → password updated
400 USER_INVALID_CREDENTIALS     → wrong currentPassword
400 USER_PASSWORD_TOO_WEAK       → newPassword does not meet strength requirements
400 INVALID_USER_DATA            → currentPassword missing when required
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
Step 1:  UserErrorCode — add USER_EMAIL_GOOGLE_ONLY                    (~10 min)
Step 2:  UserCommandService.registerUser() — update guard              (~15 min)
Step 3:  UserErrorMessageMapper + strings.xml — frontend error message (~15 min)
Step 4:  User.setOrChangePassword() — domain method                    (~10 min)
Step 5:  SetOrChangePasswordCommand + UserCommandService.setOrChangePassword() (~30 min)
Step 6:  AccountSecurityInfo + UserQueryService.getAccountSecurityInfo()       (~20 min)
Step 7:  Request/Response DTOs + UserController new endpoints          (~20 min)
Step 8:  Frontend DTOs + UserApiService additions                      (~20 min)
Step 9:  UserRepository interface + UserRepositoryImpl                 (~20 min)
Step 10: SecurityFragment + ViewModel + UiState                        (~45 min)
Step 11: Wire SecurityFragment into Profile screen                     (~15 min)
```

Realistic effort for school project (including XML layouts, navigation wiring, manual QA):

```
Backend:           ~1 session (~3–4 hours)
Frontend Security: ~1 session (~3–4 hours)
Polish + QA:       ~1–2 hours
```

---

## 7. Edge Cases

| Case | Handling |
|---|---|
| Google-only user calls `POST /me/password` without `currentPassword` | ✅ Service skips the `validateCredentials` check when `passwordHash == null` |
| Linked user (has both Google + password) calls `POST /me/password` | ✅ Service requires `currentPassword` because `passwordHash != null` — Google connection is irrelevant |
| User with password calls `POST /me/password` without `currentPassword` | Service throws `INVALID_USER_DATA` |
| Google Sign-In with email that has both password and Google already linked | `findByProviderSubject` finds the user immediately; `recordLogin()` called; no double-link |
| Forgot Password called for Google-only account | Already handled — `requestPasswordReset()` silently returns when `passwordHash == null` |
| `confirmNewPassword` mismatch | Caught in `SecurityViewModel` before the API call; never reaches the backend |
