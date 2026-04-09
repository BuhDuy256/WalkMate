● # 🔐 WalkMate Frontend — Auth Upgrade: Master Context & Implementation Plan

  > **File:** `tasks/auth_fe_implementation_plan.md`
  > **Created:** 2026-04-09
  > **Branch:** `feature/oauth`
  > **Overall Status:** 🟢 Complete

  ---

  ## 1. Project Context

  ### 1.1 Feature Overview

  The WalkMate backend has been fully upgraded for the Authentication & User Aggregate. The frontend
  must now be upgraded to seamlessly consume these new backend capabilities. This document is the
  single source of truth for that upgrade effort.

  **What the backend now supports (fully implemented & hardened):**

  | Capability | Detail |
  |---|---|
  | Multi-device Refresh Token | Real server-side token rotation per `deviceId` |
  | Phone + OTP Auth | Full standalone flow: `POST /auth/phone/send-otp` → `POST /auth/phone/verify` |
  | User Visibility Mode | `PUBLIC` / `PRIVATE` toggle via `PATCH /api/v1/users/me/visibility` |
  | Google OAuth | Google ID Token exchange, now requires `deviceId` |
  | Email/Password Login | Now requires `deviceId` in the request body |
  | Strict Domain Error Codes | 15 specific `UserErrorCode` values (e.g., `USER_ACCOUNT_SUSPENDED`) |
  | Logout per Device | `POST /auth/logout { deviceId }` — invalidates one device |
  | Logout All Devices | `POST /auth/logout-all` — invalidates all refresh tokens |

  ---

  ### 1.2 Current Frontend State (Pre-Upgrade Baseline)

  **Tech Stack:**

  | Concern | Implementation |
  |---|---|
  | Language | Java (Pure Android — no Kotlin) |
  | Architecture | MVVM + DDD-lite (Feature-oriented UI, Domain-oriented Domain/Data) |
  | State Management | `LiveData<UiState>` — immutable UiState pattern |
  | Async | `ExecutorService` (no RxJava, no Coroutines) |
  | API Client | Retrofit 2 + OkHttp 3 |
  | DI | Manual Service Locator via `WalkMateApplication` (no Hilt/Dagger) |
  | Token Storage | `SessionManager` → `EncryptedSharedPreferences` (AES256-GCM) |
  | Local DB | Room (GPS tracking; not relevant to this feature) |

  **What currently exists in Auth:**

  | Component | File | Current State |
  |---|---|---|
  | Auth Screen | `ui/auth/AuthActivity.java` | Email/Password + Google Sign-In only |
  | Register Screen | `ui/auth/register/RegisterActivity.java` | Email/Password only; navigates to Login on success |
  | Login ViewModel | `ui/auth/login/LoginViewModel.java` | Exists; no deviceId, no error mapping |
  | Register ViewModel | `ui/auth/login/RegisterViewModel.java` | Misplaced package; no deviceId; treats register as
  Void |
  | Session Storage | `data/datasource/remote/api/SessionManager.java` | Stores `accessToken` only; no refresh token, no
   deviceId |
  | Auth Interceptor | `data/datasource/remote/api/AuthInterceptor.java` | Attaches Bearer token; **no 401 handling** |
  | API Client | `data/datasource/remote/api/ApiClient.java` | Two clients (PUBLIC / authenticated); **no
  Authenticator** |
  | Auth API Service | `data/datasource/remote/api/AuthApiService.java` | 3 endpoints: `login`, `register`, `google`
  only |
  | User API Service | `data/datasource/remote/api/UserApiService.java` | 1 endpoint: `fcm-token` only |
  | Login Request DTO | `dto/request/user/LoginRequestDto.java` | `email`, `password` only — **missing `deviceId`** |
  | Register Request DTO | `dto/request/user/RegisterRequestDto.java` | `fullname`, `email`, `password` — **missing
  `deviceId`** |
  | Google Login DTO | `dto/request/user/GoogleLoginRequestDto.java` | `idToken` only — **missing `deviceId`** |
  | Login Response DTO | `dto/response/user/LoginResponseDto.java` | `accessToken`, `tokenType`, `expiresIn` — **missing
   `refreshToken`** |
  | Domain `User` Model | `domain/user/User.java` | `id`, `fullname`, `email` — **missing `accountStatus`,
  `visibilityMode`** |
  | Error Handling | `LoginViewModel`, `AuthActivity` | Raw error code strings surfaced directly to Toast |
  | Phone OTP Flow | — | **Does not exist** |
  | Visibility Toggle | — | **Does not exist** |
  | Logout (backend call) | — | **Does not exist** |
  | Token Refresh | — | **Does not exist** |

  **Structural Bug Identified:**
  `LoginResponseDto` and `LoginRequestDto` each exist as phantom duplicates in both
  `dto/request/user/` AND `dto/response/user/`. The copies in `dto/request/user/` are
  dead code and must be deleted.

  ---

  ### 1.3 Locked Architectural Decisions

  These decisions were made and confirmed by the project owner. They are non-negotiable
  during implementation.

  | # | Decision | Choice | Reason |
  |---|---|---|---|
  | Q1 | DeviceId generation strategy | `UUID.randomUUID().toString()` | Max privacy; lifecycle tied to app data; no
  Google Play policy conflicts |
  | Q2 | Phone OTP entry point | Separate "Continue with Phone Number" link below Google Sign-In | Clean UI; scalable
  for future providers (Apple, Facebook) |
  | Q3 | Logout All Devices placement | Settings screen (Security section) or Profile Danger Zone + confirmation dialog
  | Destructive action must never be accidentally triggered |
  | Q4 | Error message string location | `res/values/strings.xml` keyed by error code | Android best practice; MVVM
  separation of concerns; trivially localizable to EN/VI |
  | Q5 | Post-registration behavior | Auto-login → navigate directly to `MainActivity` | Backend returns full token pair
   on register; forcing re-login is unnecessary friction |

  ---

  ## 2. The Action Plan

  ### 2.1 Master Phase Table

  | Phase | Task Name | Brief Description | Priority |
  |---|---|---|---|
  | **1** | Fix `SessionManager` — Refresh Token | Add `saveRefreshToken()`, `getRefreshToken()`, update
  `clearSession()` | **High** |
  | **1** | Fix `SessionManager` — DeviceId | Add `getOrGenerateDeviceId()` using `UUID.randomUUID()`, persisted
  permanently | **High** |
  | **1** | Fix `LoginResponseDto` | Add `refreshToken`, `refreshTokenExpiresIn` fields | **High** |
  | **1** | Fix `LoginRequestDto` — `deviceId` | Add required `deviceId` field; all login calls currently return HTTP
  400 | **High** |
  | **1** | Fix `RegisterRequestDto` — `deviceId` | Add required `deviceId` field | **High** |
  | **1** | Fix `GoogleLoginRequestDto` — `deviceId` | Add required `deviceId` field | **High** |
  | **1** | Create `RefreshTokenRequestDto` | New DTO: `{ String refreshToken }` | **High** |
  | **1** | Create `LogoutRequestDto` | New DTO: `{ String deviceId }` | **High** |
  | **1** | Create `SendOtpRequestDto` | New DTO: `{ String phone }` | **High** |
  | **1** | Create `VerifyOtpRequestDto` | New DTO: `{ String phone, String code, String deviceId }` | **High** |
  | **1** | Create `SetVisibilityRequestDto` | New DTO: `{ String mode }` — values: `"PUBLIC"` \| `"PRIVATE"` | Med |
  | **1** | Delete phantom duplicate DTOs | Remove duplicate `LoginResponseDto` and `LoginRequestDto` from
  `dto/request/user/` | Med |
  | **1** | Expand `AuthApiService` | Add 5 missing endpoints: `refresh`, `logout`, `logoutAll`, `sendOtp`, `verifyOtp`
  | **High** |
  | **1** | Expand `UserApiService` | Add `setVisibility` endpoint: `PATCH /api/v1/users/me/visibility` | Med |
  | **2** | Create `TokenRefreshAuthenticator` | OkHttp `Authenticator`; infinite-loop guard; `ReentrantLock` for thread
   safety | **High** |
  | **2** | Create `AuthEventBus` | `LiveData`-based singleton to signal forced logout from the network layer to the UI
  | **High** |
  | **2** | Update `ApiClient` | Wire `TokenRefreshAuthenticator` into authenticated `OkHttpClient` | **High** |
  | **3** | Create `AccountStatus` enum | `ACTIVE`, `SUSPENDED`, `BANNED` in `domain/user/` | Med |
  | **3** | Create `VisibilityMode` enum | `PUBLIC`, `PRIVATE` in `domain/user/` | Med |
  | **3** | Update domain `User.java` | Add `accountStatus`, `visibilityMode` fields + update constructor and getters |
  Med |
  | **3** | Add error strings to `strings.xml` | Add all 15 `UserErrorCode` entries as keyed string resources | Med |
  | **3** | Create `UserErrorMessageMapper` | Maps `UserErrorCode` strings → `strings.xml` resource IDs + `ActionType` |
   **High** |
  | **3** | Update `UserRepository` interface | Add method signatures for all new operations; update `login`,
  `register`, `loginWithGoogle` | **High** |
  | **3** | Update `UserRepositoryImpl` — existing methods | Update `login()`, `register()`, `loginWithGoogle()` to send
   `deviceId` and save refresh token | **High** |
  | **3** | Update `UserRepositoryImpl` — new methods | Implement `logout()`, `logoutAll()`, `sendOtp()`, `verifyOtp()`,
   `setVisibility()` | **High** |
  | **3** | Auto-login on register | Change `register()` callback from `DomainCallback<Void>` to
  `DomainCallback<String>`; save tokens | **High** |
  | **4** | Force Logout Observer in `AuthActivity` | Observe `AuthEventBus`; on `FORCE_LOGOUT` clear session + relaunch
   `AuthActivity` | **High** |
  | **4** | Force Logout Observer in `MainActivity` | Same pattern; covers mid-session token expiry | **High** |
  | **4** | Update `LoginViewModel` — error mapping | Use `UserErrorMessageMapper`; handle `FORCE_LOGOUT` action type |
  **High** |
  | **4** | Update `LoginUiState` | Add `boolean forcedLogout` field | **High** |
  | **4** | Update `RegisterViewModel` — auto-login | On success, navigate to `MainActivity` instead of back to Login |
  **High** |
  | **4** | Add Phone OTP entry to `AuthActivity` | Add "Continue with Phone Number" link below Google Sign-In button |
  Med |
  | **4** | Create `PhoneOtpUiState` | Fields: `isLoading`, `otpSent`, `isSuccess`, `error`, `resendCooldownSeconds` |
  Med |
  | **4** | Create `PhoneOtpViewModel` | Orchestrates `sendOtp()` and `verifyOtp()`; manages resend countdown timer |
  Med |
  | **4** | Create `PhoneOtpViewModelFactory` | Manual DI factory | Low |
  | **4** | Create `PhoneInputFragment` | Phone number entry + "Send Code" action | Med |
  | **4** | Create `OtpInputView` custom view | 6-digit individual input boxes in `core/designsystem/view/`; auto-focus
  on digit entry | Med |
  | **4** | Create `OtpVerifyFragment` | 6-digit OTP input + countdown resend timer + error display | Med |
  | **4** | Add Visibility Toggle to Profile/Settings | `SwitchMaterial` bound to `visibilityMode`; calls
  `setVisibility()` on toggle | Med |
  | **4** | Add Logout All Devices to Settings | Button in Security section + `AlertDialog` confirmation + `logoutAll()`
   + navigate | Med |

  ---

  ### 2.2 Detailed Checklists Per Phase

  #### Phase 1 — Foundation & Data Layer
  > **Goal:** Fix all contract mismatches at the DTO and `SessionManager` level.
  > No UI changes. No network changes. Data layer only.

  **`SessionManager.java`**
  - [x] **Add `KEY_REFRESH_TOKEN = "refresh_token"` constant**
  - [x] **Add `saveRefreshToken(String token)` method**
  - [x] **Add `getRefreshToken()` method — returns null if absent**
  - [x] **Add `KEY_DEVICE_ID = "device_id"` constant**
  - [x] **Add `getOrGenerateDeviceId()` — reads stored ID; if null, generates `UUID.randomUUID().toString()`, saves,
  returns it**
  - [x] **Update `clearSession()` — remove `KEY_REFRESH_TOKEN` entry; do NOT remove `KEY_DEVICE_ID`**

  **Request DTOs**
  - [x] **Add `deviceId` field + constructor param to `LoginRequestDto.java`**
  - [x] **Add `deviceId` field + constructor param to `RegisterRequestDto.java`**
  - [x] **Add `deviceId` field + constructor param to `GoogleLoginRequestDto.java`**
  - [x] **Create `RefreshTokenRequestDto.java` in `dto/request/user/`**
  - [x] **Create `LogoutRequestDto.java` in `dto/request/user/`**
  - [x] **Create `SendOtpRequestDto.java` in `dto/request/user/`**
  - [x] **Create `VerifyOtpRequestDto.java` in `dto/request/user/`**
  - [x] Create `SetVisibilityRequestDto.java` in `dto/request/user/`

  **Response DTOs**
  - [x] **Add `refreshToken` (String) field + getter to `LoginResponseDto.java` in `dto/response/user/`**
  - [x] **Add `refreshTokenExpiresIn` (long) field + getter to `LoginResponseDto.java`**
  - [x] **Delete phantom `LoginResponseDto.java` from `dto/request/user/`**
  - [x] **Delete phantom `LoginRequestDto.java` from `dto/response/user/`**
  - [x] Create `SetVisibilityResponseDto.java` in `dto/response/user/`

  **API Services**
  - [x] **Add `refreshToken()` endpoint to `AuthApiService`**
  - [x] **Add `logout()` endpoint to `AuthApiService`**
  - [x] **Add `logoutAll()` endpoint to `AuthApiService`**
  - [x] **Add `sendOtp()` endpoint to `AuthApiService`**
  - [x] **Add `verifyOtp()` endpoint to `AuthApiService`**
  - [x] Add `setVisibility()` endpoint to `UserApiService`

  ---

  #### Phase 2 — Token Lifecycle & Network Layer
  > **Goal:** Implement silent token rotation that is thread-safe and loop-proof.
  > No Repository or UI changes.

  - [x] **Create `AuthEventBus.java` — singleton `MutableLiveData<AuthEvent>` with `postForceLogout()` and `observe()`
  methods**
  - [x] **Register `AuthEventBus` as singleton in `WalkMateApplication`**
  - [x] **Create `TokenRefreshAuthenticator.java` implementing `okhttp3.Authenticator`**
  - [x] **Add `private static final ReentrantLock REFRESH_LOCK = new ReentrantLock();`**
  - [x] **Implement infinite-loop guard: if URL contains `/auth/refresh` → call `clearSession()` +
  `AuthEventBus.postForceLogout()` → return `null`**
  - [x] **Implement stale-token check: if `sessionManager.getAccessToken()` differs from token at entry → rebuild
  request with current token, skip refresh**
  - [x] **Implement refresh call inside lock: `authApiService.refreshToken(...).execute()` synchronously**
  - [x] **On refresh success: `saveAccessToken()` + `saveRefreshToken()` → rebuild and return original request**
  - [x] **On refresh failure: `clearSession()` + `AuthEventBus.postForceLogout()` → return `null`**
  - [x] **Always release lock in `finally` block**
  - [x] **Update `ApiClient.buildAuthenticatedRetrofit()` to accept `AuthApiService` parameter and wire
  `TokenRefreshAuthenticator`**
  - [x] Update all callers of `buildAuthenticatedRetrofit()` in `RepositoryImpl` classes to pass `authApiService`

  ---

  #### Phase 3 — Domain & Repository Layer
  > **Goal:** Update domain model, add error mapping, implement all new repository operations.
  > No Fragment or Activity changes.

  - [x] Create `AccountStatus.java` enum in `domain/user/`: `ACTIVE`, `SUSPENDED`, `BANNED`
  - [x] Create `VisibilityMode.java` enum in `domain/user/`: `PUBLIC`, `PRIVATE`
  - [x] Add `accountStatus` and `visibilityMode` fields to `domain/user/User.java`
  - [x] **Add all 16 `UserErrorCode` entries as string resources in `res/values/strings.xml` keyed as
  `error_<CODE_NAME>`** (backend has 16, not 15)
  - [x] Add `error_generic` fallback string resource
  - [x] **Create `UserErrorMessageMapper.java` in `core/util/` with `enum ActionType { TOAST, FIELD_ERROR, FORCE_LOGOUT,
   SILENT }` and `static ErrorResult map(String errorCode)`**
  - [x] **Map `USER_ACCOUNT_SUSPENDED` → `ActionType.FORCE_LOGOUT`**
  - [x] **Map `USER_ALREADY_PRIVATE` / `USER_ALREADY_PUBLIC` → `ActionType.SILENT`**
  - [x] **Update `UserRepository.java` interface: add all new method signatures; update existing signatures to include
  `deviceId`**
  - [x] **Update `UserRepositoryImpl.login()`: include `deviceId`, save refresh token on success**
  - [x] **Update `UserRepositoryImpl.register()`: include `deviceId`, save both tokens, change callback to
  `DomainCallback<String>` returning access token**
  - [x] **Update `UserRepositoryImpl.loginWithGoogle()`: include `deviceId`, save both tokens**
  - [x] **Implement `UserRepositoryImpl.logout()`**
  - [x] **Implement `UserRepositoryImpl.logoutAll()`**
  - [x] **Implement `UserRepositoryImpl.sendOtp()`**
  - [x] **Implement `UserRepositoryImpl.verifyOtp()`**
  - [x] Implement `UserRepositoryImpl.setVisibility()`

  ---

  #### Phase 4 — UI/UX Integration
  > **Goal:** Wire all backend capabilities into the UI.
  > One sub-feature at a time.

  - [x] **Add `AuthEventBus` observer to `AuthActivity` → on `FORCE_LOGOUT`: `clearSession()` + relaunch with
  `FLAG_ACTIVITY_CLEAR_TASK`**
  - [x] **Add `AuthEventBus` observer to `MainActivity` → same forced logout behaviour**
  - [x] **Update `LoginUiState.java` — add `boolean forcedLogout` field**
  - [x] **Update `LoginViewModel` — use `UserErrorMessageMapper`; post `forcedLogout=true` on `FORCE_LOGOUT` action**
  - [x] **Update `RegisterViewModel` — on success, navigate to `MainActivity` (auto-login)**
  - [x] Add "Continue with Phone Number" entry point to `activity_auth.xml` and `AuthActivity`
  - [x] Create `PhoneOtpUiState.java`
  - [x] Create `PhoneOtpViewModel.java` with `sendOtp()`, `verifyOtp()`, resend countdown timer
  - [x] Create `PhoneOtpViewModelFactory.java`
  - [x] Create `PhoneInputFragment.java`
  - [x] **Create `OtpInputView.java` in `core/designsystem/view/` — 6-digit auto-focus input; register in `attrs.xml`
  and `Frontend_VI.md` catalogue**
  - [x] Create `OtpVerifyFragment.java` with `OtpInputView` + resend countdown
  - [x] Add `visibilityMode` to Profile UiState; add `setVisibility()` to Profile ViewModel
  - [x] Add `SwitchMaterial` visibility toggle to Profile/Settings layout
  - [x] Add "Log Out All Devices" button to Settings layout (Security section)
  - [x] **Wire Logout All: `AlertDialog` confirmation → `logoutAll()` → `clearSession()` → navigate to `AuthActivity`**

  ---

  ## 3. Risk Management

  ### Risk 1 — Infinite 401 Refresh Loop (Critical)

  **Threat:** OkHttp's `Authenticator` is invoked on every 401 response. If the
  `POST /auth/refresh` call itself returns 401 (expired or revoked refresh token),
  the `Authenticator` will be invoked again, creating an infinite recursive loop that
  hangs the app indefinitely and exhausts thread resources.

  **Mitigation (non-negotiable):**
  At the very top of `TokenRefreshAuthenticator.authenticate()`, before any other logic:
  ```java
  if (response.request().url().toString().contains("/auth/refresh")) {
      sessionManager.clearSession();
      AuthEventBus.getInstance().postForceLogout();
      return null; // Stops the retry chain permanently
  }

  ---
  Risk 2 — Refresh Token Race Condition (Critical)

  Threat: If 3 API calls fail with 401 simultaneously, all 3 OkHttp dispatcher
  threads invoke authenticate() concurrently. Each holds the same stale refresh
  token. The first rotation succeeds and the backend invalidates the old token.
  The second and third calls use a now-invalid token and receive an error, causing
  unexpected session termination.

  Mitigation (non-negotiable):
  Use ReentrantLock with a stale-token check:
  // Before acquiring the lock, snapshot the current token
  String tokenAtEntry = sessionManager.getAccessToken();
  REFRESH_LOCK.lock();
  try {
      // If the token changed while waiting, another thread already refreshed.
      // Just retry with the new token — do not call /auth/refresh again.
      if (!tokenAtEntry.equals(sessionManager.getAccessToken())) {
          return response.request().newBuilder()
              .header("Authorization", "Bearer " + sessionManager.getAccessToken())
              .build();
      }
      // Otherwise, this thread is responsible for the refresh.
      // ... perform refresh call ...
  } finally {
      REFRESH_LOCK.unlock(); // Always release, even on exception
  }

  ---
  Risk 3 — DeviceId Regeneration (High)

  Threat: If UUID.randomUUID() is called on every login instead of once at
  install, the backend accumulates orphaned RefreshToken rows per session. The
  per-device logout becomes meaningless. The user's token limit (if enforced server-side
  in the future) fills up with phantom device records.

  Mitigation:
  getOrGenerateDeviceId() in SessionManager must follow a strict read-first pattern:
  public String getOrGenerateDeviceId() {
      String existingId = prefs.getString(KEY_DEVICE_ID, null);
      if (existingId != null) return existingId; // Always reuse
      String newId = UUID.randomUUID().toString();
      prefs.edit().putString(KEY_DEVICE_ID, newId).commit(); // commit() not apply()
      return newId;
  }
  Note: KEY_DEVICE_ID must never be cleared in clearSession(). It must survive
  logout and only reset if the user manually clears the app's data.

  ---
  4. Execution Rules

  ╔══════════════════════════════════════════════════════════════════════════╗
  ║                  STRICT AI EXECUTION PROTOCOL                           ║
  ╠══════════════════════════════════════════════════════════════════════════╣
  ║                                                                          ║
  ║  RULE 1 — ONE PHASE AT A TIME.                                           ║
  ║  Do not generate code for all phases at once. The user will explicitly   ║
  ║  prompt: "Execute Phase X". At that point, the AI will:                  ║
  ║    (a) Briefly restate the goal of that phase to confirm alignment.      ║
  ║    (b) Provide code and instructions strictly for that phase only.       ║
  ║    (c) Not write any code belonging to a future phase.                   ║
  ║                                                                          ║
  ║  RULE 2 — READ BEFORE WRITING.                                           ║
  ║  Before modifying any existing file, read it in full using the Read      ║
  ║  tool. Never edit from memory or from a prior session's snapshot.        ║
  ║                                                                          ║
  ║  RULE 3 — VERIFY THE GATE BEFORE DECLARING DONE.                         ║
  ║  Each phase has a Verification Gate embedded in its checklist. Walk      ║
  ║  through it explicitly before stating the phase is complete.             ║
  ║                                                                          ║
  ║  RULE 4 — ONE HIGH-RISK FILE AT A TIME.                                  ║
  ║  SessionManager, TokenRefreshAuthenticator, and UserRepositoryImpl are   ║
  ║  high-risk files. Edit each independently in its own response turn.      ║
  ║  Do not batch multiple high-risk file edits in a single response.        ║
  ║                                                                          ║
  ║  RULE 5 — OBEY THE ARCHITECTURE SSOT.                                    ║
  ║  Before creating any new file, Fragment, or custom view, consult         ║
  ║  docs/single-source-of-truth/architecture/Frontend_VI.md.               ║
  ║  Naming conventions, package structure, and layer boundaries are         ║
  ║  non-negotiable.                                                          ║
  ║                                                                          ║
  ║  RULE 6 — NO SCOPE CREEP.                                                ║
  ║  Do not add error handling, helpers, abstractions, or features beyond    ║
  ║  what is explicitly listed in this plan. If a gap is discovered,         ║
  ║  surface it to the user before implementing any fix.                     ║
  ║                                                                          ║
  ║  RULE 7 — UPDATE THIS DOCUMENT.                                          ║
  ║  After completing each task, mark its checkbox [x].                      ║
  ║  After completing each phase, update its status in the tracker below.    ║
  ║                                                                          ║
  ╚══════════════════════════════════════════════════════════════════════════╝

  ---
  5. Progress Tracker

  ┌───────┬───────────────────────────┬────────────────┬──────────────┐
  │ Phase │           Name            │     Status     │ Done / Total │
  ├───────┼───────────────────────────┼────────────────┼──────────────┤
  │ 1     │ Foundation & Data Layer   │ 🟢 Complete    │ 20 / 20      │
  ├───────┼───────────────────────────┼────────────────┼──────────────┤
  │ 2     │ Token Lifecycle & Network │ 🟢 Complete    │ 12 / 12      │
  ├───────┼───────────────────────────┼────────────────┼──────────────┤
  │ 3     │ Domain & Repository       │ 🟢 Complete    │ 17 / 17      │
  ├───────┼───────────────────────────┼────────────────┼──────────────┤
  │ 4     │ UI/UX Integration         │ 🟢 Complete    │ 16 / 16      │
  ├───────┼───────────────────────────┼────────────────┼──────────────┤
  │ —     │ Overall                   │ 🟢 Complete    │ 65 / 65      │
  └───────┴───────────────────────────┴────────────────┴──────────────┘