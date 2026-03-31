# WalkMate Handoff — End of Phase 1

## What Was Delivered

### Backend
- **DDD-lite skeleton** confirmed complete: `domain / application / infrastructure / presentation` layers fully in place across all three domains (`user`, `walkintent`, `hotspot`).
- **Auth flow** functional end-to-end:
  - `POST /api/v1/auth/register` → HTTP 201 `{"success":true,"data":{"email":"..."}}`
  - `POST /api/v1/auth/login` → HTTP 200 `{"success":true,"data":{"accessToken":"eyJ...","tokenType":"Bearer","expiresIn":900}}`
- **JWT**: Spring Security OAuth2 Resource Server, HS256, access token TTL 900 s, refresh token TTL 30 days (stored in `refresh_token` table).
- **Password hashing**: BCryptPasswordEncoder (via `PasswordEncoder` bean in `SecurityConfig`).
- **Flyway migrations** now bootstrap a clean database from zero:
  - `V1__create_hotspot.sql`
  - `V1_1__create_user_account.sql` ← new: `auth_provider` / `account_status` PG enums + `user_account` table
  - `V1_2__create_refresh_token.sql` ← new: `refresh_token` table
  - `V2__create_walk_intent.sql`
  - `V3__create_match_proposal.sql`
  - `V4__seed_hotspot.sql`
- **Swagger UI** live at `http://localhost:8080/swagger-ui.html` — Auth tag with both endpoints visible and testable.

### Frontend
- `UserRepositoryImpl` **de-mocked**: login and register now execute real Retrofit calls against the backend. The hardcoded `"mock-access-token-demo"` token is gone.
- Backend error codes (`USER_INVALID_CREDENTIALS`, `USER_ALREADY_EXISTS`, etc.) are extracted from `ApiResponse.error.code` and propagated to the UI layer as `Exception(code)`.

---

## Validated Test Cases
| Scenario | Result |
|---|---|
| `POST /register` valid payload | 201 — user row in `user_account` |
| `POST /register` duplicate email | 400 — `USER_ALREADY_EXISTS` |
| `POST /login` correct credentials | 200 — valid JWT in response |
| `POST /login` wrong password | 400 — `USER_INVALID_CREDENTIALS` |
| Android login tap | Real token stored in `EncryptedSharedPreferences`, no mock value |
| Android login wrong password | Error toast with `USER_INVALID_CREDENTIALS` |
| Swagger UI | Both auth endpoints visible and executable |

---

## Canonical Error Codes (Auth Domain)
| Code | Trigger |
|---|---|
| `USER_NOT_FOUND` | Email does not exist (internal use) |
| `USER_ALREADY_EXISTS` | Duplicate email on register |
| `USER_INVALID_CREDENTIALS` | Wrong password on login |
| `INVALID_USER_DATA` | Blank required fields in domain |
| `VALIDATION_ERROR` | Bean validation failure (HTTP 422) |
| `INTERNAL_ERROR` | Uncaught exception (HTTP 500) |

---

## Ready for Phase 2
- Backend foundation is stable and fully runnable. Next phase can build authenticated features on top.
- All protected routes already require a valid JWT (`anyRequest().authenticated()` in `SecurityConfig`).
- `AuthInterceptor` on Android automatically attaches `Bearer` token to authenticated Retrofit calls.
- `WalkIntent` and `Hotspot` domains are implemented but **not yet covered by integration tests** — treat as provisional until Phase 2 validation.
