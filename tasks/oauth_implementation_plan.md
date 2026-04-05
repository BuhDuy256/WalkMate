# OAuth Google Sign-In — Master Implementation Plan
**Project:** WalkMate Android  
**Date:** 2026-04-05  
**Status:** Phase 2 Complete — Awaiting Phase 3 Execution

---

## 1. Project Context

### Feature
Add "Sign in with Google" as a second authentication method alongside the existing email/password (LOCAL) flow.

### Current State (Gap Summary)
The entire auth system is password-based end-to-end:
- Backend issues an internal HS256 JWT after bcrypt password verification.
- `AuthProvider` enum only has `LOCAL`; no concept of OAuth exists anywhere.
- `User` domain entity's `register()` factory hard-requires a `passwordHash`.
- No `provider_subject` column exists to store Google's stable `sub` claim.
- Frontend `AuthActivity` has no Google Sign-In button or launcher.

### Account Linking Policy (A2 — Merge & Keep Both)
If a LOCAL user signs in with Google using the same email:
- Their account is merged: `provider_subject` is populated.
- `password_hash` is preserved — they can still log in with email/password.
- Future Google sign-ins resolve via `provider_subject` lookup first, email fallback second.

### Profile Auto-Creation Policy
New Google users get a `user_profile` row auto-created silently using:
- `full_name` from Firebase ID token `name` claim
- `avatar_url` from Firebase ID token `picture` claim
- `gender = null` (Google no longer provides gender in the token)

### Tech Stack
| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot, Spring Security (OAuth2 Resource Server), Nimbus JOSE (HS256 JWT), Spring JDBC, PostgreSQL (Supabase), Firebase Admin SDK (already present for FCM) |
| Frontend | Android Java, MVVM + LiveData, Retrofit, EncryptedSharedPreferences, Firebase Auth SDK (to be added) |
| Token Verification Strategy | Firebase path — Android uses `FirebaseAuth` → gets Firebase ID token → backend verifies via `FirebaseAuth.verifyIdToken()`. Zero new backend dependencies. |

---

## 2. The Action Plan

| Phase | Task Name | Brief Description | Priority |
|-------|-----------|-------------------|----------|
| **1 — Provider Setup** | Enable Google Sign-In in Firebase Console | Turn on Google as an auth provider under Authentication → Sign-in method | High |
| **1 — Provider Setup** | Register SHA-1 fingerprints in Firebase | Add debug + release SHA-1 keystores to Firebase project settings so Android client is trusted | High |
| **1 — Provider Setup** | Verify `google-services.json` is up to date | Re-download after enabling Google Sign-In; the `client_id` (OAuth client) must be present | High |
| **2 — Database ✅** | **Migration V4 — `V4__add_oauth_support.sql`** | **`ALTER TYPE auth_provider ADD VALUE 'GOOGLE'` + `provider_subject varchar` column + partial unique index on `provider_subject WHERE provider_subject IS NOT NULL`** | High |
| **3 — Backend: Domain** | **`AuthProvider.java` — add `GOOGLE` value** | **Gap #2: Enum currently only has `LOCAL`; `AuthProvider.valueOf()` will throw on any DB row with `GOOGLE`** | High |
| **3 — Backend: Domain** | **`User.java` — add `providerSubject` field + new factories** | **Gap #3/4: Add field, update rehydration constructor, add `registerWithGoogle(email, name, providerSubject)` factory, add `linkGoogleAccount(providerSubject)` method for A2 merge** | High |
| **3 — Backend: Domain** | **`UserRepository.java` — add `findByProviderSubject(String)`** | **Gap #4: Domain interface contract for Google `sub`-based lookup needed by A2 merge strategy** | High |
| **3 — Backend: Domain** | **`UserErrorCode.java` — add `USER_PROVIDER_CONFLICT`** | **New error for edge case: account found by email but `provider_subject` belongs to a different Google account** | Med |
| **3 — Backend: Infra** | **`UserJdbcRepository` — implement `findByProviderSubject()`** | **Gap #4: SQL `WHERE provider_subject = :sub`; reuses existing `mapRow()`** | High |
| **3 — Backend: Infra** | **`UserJdbcRepository.save()` and `mapRow()` — add `provider_subject`** | **Gap #4: The INSERT/UPDATE and SELECT queries must include the new column** | High |
| **3 — Backend: Application** | **`GoogleIdentity.java` — new record** | **Internal value object: `record GoogleIdentity(String sub, String email, String name, String pictureUrl)`** | High |
| **3 — Backend: Application** | **`GoogleTokenVerifier.java` — new interface** | **Gap #6: Declare in `application/user/`; single method `GoogleIdentity verify(String firebaseIdToken)`** | High |
| **3 — Backend: Application** | **`GoogleAuthCommand.java` — new record** | **Gap #5: Pure Java record `(String firebaseIdToken)` — the command handed to `UserCommandService`** | High |
| **3 — Backend: Application** | **`UserCommandService.loginOrRegisterWithGoogle()` — new method** | **Gap #5: Core find-or-create logic: verify token → look up by `providerSubject` → if not found, try email (A2 merge) → else create new Google user + auto-create profile** | High |
| **3 — Backend: Infra** | **`FirebaseTokenVerifier.java` — new impl** | **Gap #6: Implements `GoogleTokenVerifier`; calls `FirebaseAuth.getInstance(firebaseApp).verifyIdToken(idToken)`; wraps `FirebaseAuthException` into `DomainException(USER_INVALID_CREDENTIALS)`** | High |
| **3 — Backend: Presentation** | **`GoogleLoginRequest.java` — new DTO** | **Gap #8: Request DTO with single field `@NotBlank String idToken`** | High |
| **3 — Backend: Presentation** | **`UserController` — add `POST /api/v1/auth/google`** | **Gap #8: Accepts `GoogleLoginRequest`, delegates to `loginOrRegisterWithGoogle()`, returns same `LoginUserResponse` shape** | High |
| **3 — Backend: Config** | **`SecurityConfig` — permit `/api/v1/auth/google`** | **Gap #7: Add `.requestMatchers(HttpMethod.POST, "/api/v1/auth/google").permitAll()`** | High |
| **3 — Backend: Infra** | Auto-create `user_profile` for new Google users | Inside `loginOrRegisterWithGoogle()`: call `UserProfileCommandService` or insert directly; uses `name` + `pictureUrl` from `GoogleIdentity`; `gender = null` | High |
| **4 — Frontend: Build** | **Add `firebase-auth` dependency to `build.gradle`** | **Gap #9: `com.google.firebase:firebase-auth:23.x.x`** | High |
| **4 — Frontend: Data** | **`GoogleLoginRequestDto.java` — new DTO** | **Gap #11: Simple POJO `{ String idToken }` under `dto/request/user/`** | High |
| **4 — Frontend: Data** | **`AuthApiService` — add `loginWithGoogle()`** | **Gap #11: `@POST("api/v1/auth/google") Call<ApiResponse<LoginResponseDto>> loginWithGoogle(@Body GoogleLoginRequestDto)`** | High |
| **4 — Frontend: Domain** | **`UserRepository` (interface) — add `loginWithGoogle()`** | **Gap #10: `void loginWithGoogle(String firebaseIdToken, DomainCallback<String> callback)`** | High |
| **4 — Frontend: Data** | **`UserRepositoryImpl` — implement `loginWithGoogle()`** | **Gap #12: (1) `FirebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(googleIdToken, null))` → (2) get Firebase ID token → (3) `POST /api/v1/auth/google` → (4) `saveAccessToken()` → callback** | High |
| **4 — Frontend: ViewModel** | **`LoginViewModel` — add `loginWithGoogle(String googleIdToken)`** | **Gap #13: Sets loading state, delegates to `userRepository.loginWithGoogle()`, posts success/error UiState** | High |
| **4 — Frontend: UI** | **`AuthActivity` — add Google Sign-In button + `ActivityResultLauncher`** | **Gap #14: Add `WalkMateButton` (OUTLINED); register launcher for `GoogleSignIn` intent; on result extract `idToken` and call `loginViewModel.loginWithGoogle(idToken)`** | High |
| **4 — Frontend: UI** | Add Google button to `activity_auth.xml` layout | Place below the divider, above "Create Account" link; follow Google branding guidelines | Med |
| **4 — Frontend: UI** | Handle Google flow from `RegisterActivity` | Reuse same `loginWithGoogle` path — the backend find-or-create covers both new and existing users | Med |
| **5 — Testing & Security** | Backend: unit test `FirebaseTokenVerifier` | Mock `FirebaseAuth`; verify happy path, expired token, revoked token all produce correct result/exception | High |
| **5 — Testing & Security** | Backend: integration test `POST /api/v1/auth/google` | Cover: new Google user, existing LOCAL merge (A2), duplicate `provider_subject` idempotency | High |
| **5 — Testing & Security** | Frontend: manual E2E — new Google user | Sign in → profile auto-created → lands on `MainActivity` | High |
| **5 — Testing & Security** | Frontend: manual E2E — LOCAL account A2 merge | Register email/pass → sign in with same Google email → verify both login methods still work | High |
| **5 — Testing & Security** | Frontend: token expiry validation | Confirm `SessionManager.hasUsableAccessToken()` correctly evaluates the WalkMate JWT (not Firebase token) | Med |

---

## 3. Risk Management

### Risk 1 — Firebase Network Dependency at Token Verification
**Description:** `FirebaseAuth.verifyIdToken()` (Firebase Admin SDK) makes an outbound HTTPS call to Google's public JWK endpoint to validate the token's signature. If the backend environment has a restricted egress firewall, this call will silently time out, causing all Google logins to fail.

**Mitigation:**
- Add a startup connectivity check in `FirebaseTokenVerifier` or a health-check endpoint.
- Ensure the deployment environment whitelists outbound HTTPS to `*.googleapis.com`.
- Wrap `FirebaseAuthException` with a distinct error log so ops can diagnose network vs. token issues.

---

### Risk 2 — A2 Merge Race Condition
**Description:** If a LOCAL user double-taps "Sign in with Google" before the first request completes, two concurrent backend calls both pass the "email exists, no `provider_subject` yet" check. Both attempt to `UPDATE user_account SET provider_subject = ...`. The second write hits the `UNIQUE INDEX` on `provider_subject` and throws a PostgreSQL constraint violation, returning a 500 to the client.

**Mitigation:**
- Use `SELECT ... FOR UPDATE` on the user row inside the find-or-create transaction to serialize concurrent writes for the same user.
- Alternatively, catch the unique constraint violation in `UserCommandService` and treat it as an idempotent success — the correct `provider_subject` is already set.

---

## 4. Execution Rules

> **Do not generate code for all phases at once.**
> The user will explicitly prompt: **"Execute Phase X"**, at which point the AI will provide the code and instructions strictly for that phase only.
>
> - Complete all tasks within the requested phase before stopping.
> - Mark tasks complete in this document as each phase is finished.
> - If a blocker is discovered during execution, stop and flag it before proceeding.
> - Do not refactor or modify code outside the scope of the current phase.
