Note: The two risks in parentheses reference the OAuth doc. I'm substituting the two risks I actually identified in this
Ac session — the refresh_tokens migration risk and the visibilityMode coupling risk — since those are the ones that apply to
   this plan.

  # Authentication & User Aggregate — Master Implementation Plan

  **Project:** WalkMate Backend
  **Date:** 2026-04-07
  **Branch:** `feature/oauth` (base for all auth work)
  **Status:** Planning Complete — Awaiting Phase 1 Execution

  ---

  ## 1. Project Context

  ### Feature Scope
  Complete and harden the Authentication & User Aggregate to align 100% with `DOMAIN_CONTRACTS.md §4`
  and `Features List_EN.md §1–2`. This covers:
  - Core DDD bug fixes (silent data loss, method side-effect violations)
  - RefreshToken infrastructure for real token rotation and multi-device logout
  - User visibility mode (PUBLIC/PRIVATE) for matching coordination
  - Phone+OTP as a full standalone auth method (parity with Google + email/password)

  ### Current Gap Summary (from Gap Analysis, 2026-04-07)

  | Area                                                                                                  | Status              |
  | ----------------------------------------------------------------------------------------------------- | ------------------- |
  | `POST /auth/register`, `POST /auth/login`, `POST /auth/google`                                        | ✅ Implemented       |
  | `fullName` collected at register but silently discarded — `UserProfile` never created for LOCAL users | ❌ Critical Bug      |
  |                                                                                                       |
  | `authenticate()` mutates `lastLoginAt` — violates pure-validation contract in §4.4                    | ❌ DDD Violation     |
  | `UserProfileController` directly injects `UserProfileRepository` (bypasses service layer)             | ❌ DDD Boundary Leak |
  | `RefreshToken` saved to DB but never returned to client and never validated — dead store              | ❌ Critical Gap      |
  | `POST /auth/refresh`, `POST /auth/logout`, `POST /auth/logout-all`                                    | ❌ Missing           |
  | `visibilityMode` (PUBLIC/PRIVATE) field on `User` + `PATCH /users/me/visibility`                      | ❌ Missing           |
  | Phone+OTP login/registration (`POST /auth/phone/send-otp`, `POST /auth/phone/verify`)                 | ❌ Missing           |
  | `AccountStatus` only has `ACTIVE` — no `SUSPENDED`/`BANNED`                                           | ❌ Incomplete        |
  | 6 `UserErrorCode` values missing from contract (see Phase 2)                                          | ❌ Missing           |

  ### Architectural Decisions Locked In

  | Question                   | Decision                                                                                    |
  | -------------------------- | ------------------------------------------------------------------------------------------- |
  | Q1 — `fullName` ownership  | **Option A:** Lives in `UserProfile` only. `User` is pure auth/identity. Created atomically |
  | at register.               |
  | Q2 — RefreshToken strategy | **Option B:** Multi-device. One token per `(userId, deviceId)`. Supports simultaneous       |
  | sessions.                  |
  | Q3 — Phone OTP scope       | **Option A:** Full standalone auth method. User can register/login with phone only.         |

  ### Tech Stack

  | Layer             | Technology                                                            |
  | ----------------- | --------------------------------------------------------------------- |
  | Backend Framework | Spring Boot, Spring Security (OAuth2 Resource Server)                 |
  | Token Signing     | Nimbus JOSE — HS256 JWT                                               |
  | Database          | PostgreSQL (Supabase), Spring JDBC (no JPA/Hibernate)                 |
  | Google Auth       | Firebase Admin SDK — `FirebaseAuth.verifyIdToken()`                   |
  | SMS Provider      | Interface-first (`SmsGateway`) — concrete impl TBD (Twilio / AWS SNS) |
  | Password Hashing  | BCrypt via Spring Security `PasswordEncoder`                          |

  ---

  ## 2. The Action Plan

  ### Phase 1 — Core Bug Fixes & DDD Alignment
  *Goal: Fix silent data loss and method contract violations before touching the DB.*

  | Phase                                                                                                    | Task                                                                   | Brief Description                               | Priority |
  | -------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- | ----------------------------------------------- | -------- |
  | 1                                                                                                        | **Fix `fullName` silent discard bug** ✅                               | Remove `fullName` from `User.register()`;       |
  | `UserCommandService.registerUser()` creates `UserProfile` with `fullName` atomically after saving `User` | High                                                                   |
  | 1                                                                                                        | **Split `authenticate()` → `validateCredentials()` + `recordLogin()`** ✅ | `validateCredentials()` becomes a pure       |
  | check (no side-effects); service calls `recordLogin()` explicitly afterward                              | High                                                                   |
  | 1                                                                                                        | **Remove `UserProfileRepository` from `UserProfileController`** ✅     | Move `findTagsByUserId()` call into             |
  | `UserQueryService`; controller calls service only                                                        | High                                                                   |
  | 1                                                                                                        | **Rename `USER_ALREADY_EXISTS` → `USER_EMAIL_ALREADY_EXISTS`** ✅      | Align error code name with `DOMAIN_CONTRACTS.md |
  | §4.3`; update all call sites                                                                             | Med                                                                    |

  ---

  ### Phase 2 — Database & Domain Updates
  *Goal: Extend DB schema and domain model with all missing fields, enums, and error codes.*

  | Phase                                                                                                | Task                                                             | Brief Description                                                               | Priority |
  | ---------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------- | -------- |
  | 2                                                                                                    | **Add `visibility_mode` column — DB migration**                  | `ALTER TABLE users ADD COLUMN visibility_mode VARCHAR(10) NOT NULL              |
  | DEFAULT 'PUBLIC'`                                                                                    | High                                                             |
  | 2                                                                                                    | **Add `visibilityMode` field + `setVisibilityMode()` to `User`** | Add `VisibilityMode { PUBLIC, PRIVATE }` enum;                                  |
  | field on entity; method with idempotency guards (`USER_ALREADY_PUBLIC` / `USER_ALREADY_PRIVATE`)     | High                                                             |
  | 2                                                                                                    | **Add `AccountStatus.SUSPENDED` / `BANNED`**                     | Extend enum; add status guard in `validateCredentials()` — throw                |
  | `USER_ACCOUNT_SUSPENDED` if not `ACTIVE`                                                             | High                                                             |
  | 2                                                                                                    | **Add missing `UserErrorCode` values**                           | `USER_PHONE_ALREADY_EXISTS`, `USER_DISPLAY_NAME_BLANK`,                         |
  | `USER_ALREADY_PRIVATE`, `USER_ALREADY_PUBLIC`, `USER_INVALID_EMAIL_FORMAT`, `USER_ACCOUNT_SUSPENDED` | Med                                                              |
  | 2                                                                                                    | **Redesign `RefreshToken` for multi-device**                     | Add fields: `deviceId: String`, `expiresAt: Instant`; `tokenId` =               |
  | per-device key                                                                                       | High                                                             |
  | 2                                                                                                    | **DB migration: `refresh_tokens` table**                         | Add `device_id VARCHAR`, `expires_at TIMESTAMPTZ`; unique index on              |
  | `(user_id, device_id)` — see Risk 1 for safe migration sequence                                      | High                                                             |
  | 2                                                                                                    | **Extend `RefreshTokenRepository`**                              | Add: `findByTokenValue(String)`, `deleteByUserIdAndDeviceId(UUID, String)`,     |
  | `deleteAllByUserId(UUID)`                                                                            | High                                                             |
  | 2                                                                                                    | **Propagate `deviceId` through login commands & `LoginResult`**  | `LoginUserCommand`, `GoogleAuthCommand` accept                                  |
  | `deviceId`; `LoginResult` now returns `refreshToken` + `refreshTokenExpiresIn`                       | High                                                             |
  | 2                                                                                                    | **Add `Phone` value object**                                     | Record with E.164 format validation; used in `User.registerWithPhone()` factory | Med      |
  |                                                                                                      |
  | 2                                                                                                    | **Add `OtpRecord` entity + repository**                          | Fields: `phone`, `codeHash`, `expiresAt`, `used: boolean`, `attemptCount:       |
  | int`; domain method `verify(rawCode, hasher)`                                                        | High                                                             |

  ---

  ### Phase 3 — Backend API Implementation
  *Goal: Wire all new domain capabilities into HTTP endpoints.*

  | Phase                                                  | Task                                                 | Brief Description                                                              | Priority |
  | ------------------------------------------------------ | ---------------------------------------------------- | ------------------------------------------------------------------------------ | -------- |
  | 3                                                      | **Update `POST /auth/register` request/response**    | Accept `deviceId`; ensure `UserProfile` created atomically;                    |
  | return refresh token in response                       | High                                                 |
  | 3                                                      | **Implement `POST /auth/refresh`**                   | Validate token → check expiry → rotate: delete old `(userId, deviceId)` token, |
  | issue new pair → return `LoginUserResponse`            | High                                                 |
  | 3                                                      | **Implement `POST /auth/logout`**                    | Accept `deviceId`; delete `RefreshToken` by `(userId, deviceId)`; return 204   |
  | High                                                   |
  | 3                                                      | **Implement `POST /auth/logout-all`**                | Delete all refresh tokens for `userId`; invalidates all devices; return 204    |
  | Med                                                    |
  | 3                                                      | **Implement `PATCH /users/me/visibility`**           | Body `{ "mode": "PUBLIC" \| "PRIVATE" }`; calls                                |
  | `user.setVisibilityMode()`; returns updated user state | High                                                 |
  | 3                                                      | **Implement `POST /auth/phone/send-otp`**            | Rate-limited by phone; generate OTP, hash + store in `OtpRecord`,              |
  | dispatch via `SmsGateway` interface                    | High                                                 |
  | 3                                                      | **Implement `POST /auth/phone/verify`**              | Validate OTP → find-or-create `User` by phone; issue `LoginResult` with        |
  | refresh token                                          | High                                                 |
  | 3                                                      | **Add email format validation to `User.register()`** | Validate against RFC 5322 pattern; throw                                       |
  | `USER_INVALID_EMAIL_FORMAT`                            | Med                                                  |
  | 3                                                      | **Add `displayName` blank guard**                    | Guard in `User.register()` / `registerWithPhone()`: throw                      |
  | `USER_DISPLAY_NAME_BLANK` if blank                     | Med                                                  |

  ---

  ### Phase 4 — Testing & Security
  *Goal: Prove correctness end-to-end and harden against abuse vectors.*

  | Phase                                        | Task                                        | Brief Description                                                               | Priority |
  | -------------------------------------------- | ------------------------------------------- | ------------------------------------------------------------------------------- | -------- |
  | 4                                            | **Unit tests: `User` aggregate**            | Cover `setVisibilityMode()` idempotency, `validateCredentials()` purity (no     |
  | lastLoginAt mutation), `registerWithPhone()` | High                                        |
  | 4                                            | **Unit tests: `OtpRecord` domain**          | Cover `verify()` — expired, already-used, wrong code, happy path, attempt count |
  | exceeded                                     | High                                        |
  | 4                                            | **Integration tests: token refresh flow**   | Register → login (device A) → refresh → verify old token is invalidated         |
  | → verify new token works                     | High                                        |
  | 4                                            | **Integration tests: logout flows**         | Single-device logout; logout-all; verify tokens are gone; subsequent refresh    |
  | returns 401                                  | High                                        |
  | 4                                            | **Integration tests: phone OTP full flow**  | Send OTP → verify → receive JWT; duplicate phone; expired OTP; wrong            |
  | code rejection                               | High                                        |
  | 4                                            | **Security: OTP brute-force protection**    | Verify attempt counter on `OtpRecord` locks after N failures; rate-limit        |
  | `send-otp` per phone per time window         | High                                        |
  | 4                                            | **Security: refresh token reuse detection** | If a rotated-away token is presented → revoke ALL tokens for that               |
  | `userId` (compromise signal)                 | High                                        |
  | 4                                            | **Update `GoogleAuthControllerTest`**       | Pass `deviceId` in request; assert refresh token is returned in response        |
  | Med                                          |

  ---

  ## 3. Risk Management

  ### Risk 1 — Two-step DB migration for `refresh_tokens` (Data Migration Risk)

  **Description:**
  Adding `device_id NOT NULL` and `expires_at NOT NULL` to an existing table that may already contain
  rows will cause the migration to fail outright if applied naively. Existing rows have no `device_id`
  value.

  **Safe Migration Sequence:**
  1. **Migration step 1:** Add `device_id VARCHAR NULL`, `expires_at TIMESTAMPTZ NULL` (nullable first).
  2. **Deploy Phase 3 code** — all new logins now write `device_id` and `expires_at`.
  3. **Migration step 2 (cleanup):** Truncate all existing tokens (they are safe to discard — the
     current `LoginResult` never returned refresh tokens to clients, so no client holds a valid one),
     then `ALTER COLUMN device_id SET NOT NULL`, `ALTER COLUMN expires_at SET NOT NULL`.

  > **Do NOT** attempt a single `ALTER TABLE ... ADD COLUMN device_id NOT NULL` if any rows exist.

  ---

  ### Risk 2 — `visibilityMode DEFAULT 'PUBLIC'` coupling to the matching engine

  **Description:**
  Setting `DEFAULT 'PUBLIC'` is the correct safe default — all existing users remain visible.
  However, when the matching engine (Phase D / `RuleBasedMatchingStrategy`) is implemented later,
  `PRIVATE` users must be filtered out of match candidates. There is a risk that this filter is
  forgotten because the column exists but the enforcement code hasn't been written yet, creating
  a window where `PRIVATE` users still appear in matches.

  **Mitigation:**
  As part of Phase 2 (when the column is added), place a `// TODO [DOMAIN_CONTRACTS §4.2 Invariant 3]:
  filter out PRIVATE users before returning candidates` comment directly inside
  `RuleBasedMatchingStrategy`. This makes the invariant visible to the next engineer implementing
  matching and prevents it from being overlooked.

  ---

  ## 4. Full API Surface (Phase 3 Deliverables)

  POST   /api/v1/auth/register              → email+password register (fixed)
  POST   /api/v1/auth/login                 → email+password login (deviceId added)
  POST   /api/v1/auth/google                → Google OAuth (deviceId added) ✅ exists
  POST   /api/v1/auth/refresh               → rotate refresh token (NEW)
  POST   /api/v1/auth/logout                → single-device logout (NEW)
  POST   /api/v1/auth/logout-all            → all-device logout (NEW)
  POST   /api/v1/auth/phone/send-otp        → send OTP to phone (NEW)
  POST   /api/v1/auth/phone/verify          → verify OTP → issue JWT (NEW)
  PATCH  /api/v1/users/me/visibility        → toggle PUBLIC/PRIVATE (NEW)

  ---

  ## 5. Execution Rules

  > **Do not generate code for all phases at once.**
  > The user will explicitly prompt: **"Execute Phase X"**, at which point the AI will provide
  > the code and instructions strictly for that phase only.
  >
  > **Before starting any phase, the AI must:**
  > 1. Restate the goal of that phase in 2–3 sentences to confirm alignment.
  > 2. List exactly which files will be created or modified.
  > 3. Wait for explicit approval if the scope differs from the plan.
  >
  > **During execution:**
  > - Complete all tasks within the requested phase before stopping.
  > - Mark tasks as `✅` in this document as each is finished.
  > - If a blocker is discovered, stop immediately and flag it — do not work around it silently.
  > - Do not refactor or modify code outside the scope of the current phase.
  > - Do not skip tasks and mark them complete without proof.

  ---