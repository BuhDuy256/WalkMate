# WalkMate — Full-Stack Implementation Plan

**Role:** Lead Full-Stack Engineer & Architect
**Covers:** Backend (Spring Boot / DDD-lite) + Frontend (Android Java / MVVM)
**Ground rules:**

| Rule | Enforcement |
|---|---|
| **Always Runnable** | Every phase ends with a passing build and a testable milestone. If backend is incomplete, frontend uses its existing mock `*RepositoryImpl`. No phase may introduce a crash. |
| **Contract-first** | Phase 0 resolves all 7 Part-0 mismatches before a single new feature is coded. |
| **Deferred scope is real scope** | Profile, Chat, Notifications, Follow/Block, Reviews, Gamification are designed and phased in — not left as TODO. |
| **Architecture laws** | Backend: `DDD-lite + Layered`, Rich Domain Model, one `GlobalExceptionHandler`, Technology Suffix. Frontend: `MVVM`, `ExecutorService`, Manual DI, DTO boundary via Mapper, no Hilt/Dagger/RxJava. |

---

## Phase Map (Overview)

```
Phase 0  → Contract Hardening           (no new features, just fixes)
Phase 1  → Backend Foundation + Auth    (register, login, JWT)
Phase 2  → Hotspot                      (static catalogue)
Phase 3  → WalkIntent Core              (create, list, cancel)
Phase 4  → Matching Engine              (findMatch, MatchProposal lifecycle)
Phase 5  → WalkSession Full Lifecycle   (activate, cancel, abort, auto-complete)
Phase 6  → GPS Tracking Sync            (batch route-point upload)
Phase 7  → User Profile CRUD            (deferred scope starts here)
Phase 8  → Social Graph                 (follow, block, user discovery)
Phase 9  → Post-Session                 (reviews, trust score recalculation)
Phase 10 → Chat                         (in-session messaging)
Phase 11 → Notifications                (push + in-app)
Phase 12 → Gamification                 (badges, points, leaderboard)
```

---

## Phase 0 — Contract Hardening

### Goal
Eliminate all 7 Part-0 mismatches so that every subsequent phase builds on a single, unambiguous contract. The app must build and run identically to before — zero new features, zero regressions.

---

### Backend Tasks

**0-B1 · Lifecycle Document Patch**
- Add `ABORTED` as a 6th terminal `WalkSession` state to `walkintent-walkproposal-walksession.lifecycle.md` (C-2 resolution).
- Document the `PENDING → ABORTED` transition: _"User-initiated emergency abort from `ACTIVE` state, requires an `abortReason` (INJURY / SAFETY / ENVIRONMENT / OTHER)."_
- Add `S-10` to the invariants doc: _"An `ACTIVE` session may be aborted by either participant at any time. The state is terminal and immutable. `abort_reason` is mandatory."_

**0-B2 · Canonical Enum Definitions** _(schema docs only — no code yet)_
Confirm the PostgreSQL enum values that the backend will use:
```sql
-- intent_status: OPEN, CONSUMED, CANCELLED, EXPIRED
--   (DRAFT reserved but not yet used at API surface)
-- proposal_status: PENDING, CONFIRMED, REJECTED, EXPIRED
-- session_status:  PENDING, ACTIVE, COMPLETED, NO_SHOW, CANCELLED, ABORTED
```
Document these explicitly as comments at the top of `db.sql`.

---

### Frontend Tasks

**0-F1 · Fix `WalkIntent` status strings (C-1)**

File: `domain/walkintent/WalkIntent.java`
- Replace the inline comment `"OPEN" | "WAITLIST" | "MATCHED" | "EXPIRED"` with the canonical set:
  `"OPEN" | "CONSUMED" | "CANCELLED" | "EXPIRED"`
- `"WAITLIST"` was an undocumented mock artifact — remove all references.
- `"MATCHED"` was informal shorthand for `"CONSUMED"` — update every `status.equals("MATCHED")` call in ViewModels/Adapters to `status.equals("CONSUMED")`.
- The UI label _"Đang chờ"_ can still display for `CONSUMED` — that is a **display** decision, not a contract value.

**0-F2 · Fix `WalkSession.Status` enum (C-3)**

File: `domain/walksession/WalkSession.java`
- Rename `PENDING_MEET → PENDING` in the `Status` enum.
- Add `NO_SHOW` and `ABORTED` to the enum.
- Updated enum:
  ```java
  public enum Status { PENDING, ACTIVE, COMPLETED, NO_SHOW, CANCELLED, ABORTED }
  ```
- Search the entire UI layer for any `== Status.PENDING_MEET` comparisons and update them to `== Status.PENDING`.
- Update any switch/if blocks in `WalkSessionAdapter`, `SessionFragment` etc. to handle `NO_SHOW` and `ABORTED` (show a "No-show" or "Aborted" chip — no crash).

**0-F3 · Declare `ApiResponse<T>` frontend DTO**

Create: `data/datasource/remote/dto/response/ApiResponse.java`
```java
package com.walkmate.data.datasource.remote.dto.response;

public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ApiError error;

    // Gson no-arg constructor
    public ApiResponse() {}

    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public ApiError getError() { return error; }

    public static class ApiError {
        private String code;
        private String message;
        public String getCode() { return code; }
        public String getMessage() { return message; }
    }
}
```
This DTO already exists implicitly in `TrackingRepositoryImpl` comments — formalise it now.

**0-F4 · Add `date` field understanding to `CreateWalkIntentRequest` (C-4)**

Create: `data/datasource/remote/dto/request/walkintent/CreateWalkIntentRequest.java`
```java
public class CreateWalkIntentRequest {
    private String hotspotId;
    private String date;        // "YYYY-MM-DD" — the calendar date of the walk
    private float timeStart;    // hour float, e.g. 17.0 = 17:00
    private float timeEnd;
    private int ageMin;
    private int ageMax;
    private List<String> tags;
    // constructor + getters
}
```
The mock `WalkIntentRepositoryImpl.createIntent()` does NOT need to change yet — this DTO is ready for Phase 3 when the real Retrofit call is wired.

**0-F5 · Add `activateSession()` to `WalkSessionRepository` interface (C-5)**

File: `domain/walksession/WalkSessionRepository.java`
```java
public interface WalkSessionRepository {
    void getActiveSessions(DomainCallback<List<WalkSession>> callback);
    void cancelSession(String sessionId, String reason, DomainCallback<Void> callback);
    void activateSession(String sessionId, DomainCallback<WalkSession> callback); // NEW
}
```

File: `data/repository/WalkSessionRepositoryImpl.java`
Add a mock implementation:
```java
@Override
public void activateSession(String sessionId, DomainCallback<WalkSession> callback) {
    executor.execute(() -> {
        sleep();
        // Mock: return the session with status ACTIVE
        callback.onSuccess(new WalkSession(
            sessionId, "proposal-mock", "Thu Hà", null,
            10.7769, 106.7009, "2026-03-29T14:00:00Z",
            WalkSession.Status.ACTIVE));
    });
}
```

**0-F6 · Update mock data in `WalkIntentRepositoryImpl`**

- Remove `intent-002` with status `"WAITLIST"` or change its status to `"OPEN"`.
- The `WalkProposalRepositoryImpl` mock already uses `WalkSession.Status.PENDING_MEET` — update to `WalkSession.Status.PENDING` after 0-F2.

---

### Testing / Validation
- `./gradlew build` (or Build → Make Project in Android Studio) — must succeed with zero errors.
- Run app on emulator: login screen appears, home screen loads mock hotspots, intent list loads, session card shows — identical to before Phase 0.
- Manually inspect any screen that previously displayed a "WAITLIST" or "MATCHED" status badge — confirm no crash and a correct label.
- Run `grep -r "PENDING_MEET"` across the codebase — result must be empty.
- Run `grep -r "WAITLIST"` — result must be empty.

---

## Phase 1 — Backend Foundation + Auth

### Goal
A running Spring Boot server with the DDD-lite skeleton, a working `POST /api/v1/auth/register` and `POST /api/v1/auth/login`, Swagger UI available, and the frontend wired to real auth (no more hardcoded `"mock-access-token-demo"`).

---

### Backend Tasks

**1-B1 · Spring Boot Project Skeleton**
- Confirm `build.gradle` (or `pom.xml`) dependencies: `spring-boot-starter-web`, `spring-boot-starter-jdbc` (or jOOQ), `spring-boot-starter-validation`, `springdoc-openapi-starter-webmvc-ui`, `jjwt-api/impl/jackson`, `postgresql`, `flyway-core`, `spring-security-crypto` (for BCrypt).
- Establish the canonical directory layout per `Backend_VI.md`:
  ```
  src/main/java/com/walkmate/
  ├── application/user/
  ├── domain/user/
  ├── domain/shared/exception/
  ├── infrastructure/repository/user/
  ├── infrastructure/security/jwt/
  ├── infrastructure/config/
  └── presentation/
      ├── controller/user/
      ├── dto/request/user/
      ├── dto/response/
      └── exception/
  ```

**1-B2 · Shared Contracts**

`presentation/dto/response/ApiResponse.java`:
```java
public record ApiResponse<T>(boolean success, T data, ApiError error) {
    public record ApiError(String code, String message) {}
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }
    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message));
    }
}
```

`domain/shared/exception/ErrorCode.java` (interface marker):
```java
public interface ErrorCode { String getCode(); String getDefaultMessage(); }
```

`domain/shared/exception/DomainException.java`:
```java
public class DomainException extends RuntimeException {
    private final ErrorCode errorCode;
    public DomainException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }
    public ErrorCode getErrorCode() { return errorCode; }
}
```

`presentation/exception/GlobalExceptionHandler.java`:
- `@ExceptionHandler(DomainException.class)` → `ResponseEntity` with HTTP 400 + `ApiResponse.fail(code, message)`
- `@ExceptionHandler(MethodArgumentNotValidException.class)` → HTTP 422
- `@ExceptionHandler(Exception.class)` → HTTP 500 + `INTERNAL_ERROR`

**1-B3 · User Domain**

`domain/user/UserAccount.java` (Rich Domain Entity):
```java
public class UserAccount {
    private final UUID userId;
    private String passwordHash;
    private AccountStatus status;
    // Rich behaviour:
    public void authenticate(String rawPassword, PasswordMatcher matcher) {
        if (!matcher.matches(rawPassword, this.passwordHash))
            throw new DomainException(UserErrorCode.USER_INVALID_CREDENTIALS);
        if (this.status != AccountStatus.ACTIVE)
            throw new DomainException(UserErrorCode.USER_ACCOUNT_SUSPENDED);
    }
    public static UserAccount register(String email, String rawPassword,
                                       String fullName, PasswordHasher hasher) { ... }
}
```

`domain/user/UserErrorCode.java`:
```java
public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND("User not found"),
    USER_INVALID_CREDENTIALS("Invalid email or password"),
    USER_EMAIL_ALREADY_EXISTS("Email is already registered"),
    USER_INVALID_DATA("Invalid user data"),
    USER_ACCOUNT_SUSPENDED("Account is suspended or banned");
    // ...
}
```

`domain/user/UserRepository.java` (interface in domain layer):
```java
public interface UserRepository {
    Optional<UserAccount> findByEmail(String email);
    void save(UserAccount user);
    boolean existsByEmail(String email);
}
```

`domain/user/PasswordHasher.java` + `domain/user/PasswordMatcher.java` (interfaces for DI):
```java
public interface PasswordHasher  { String hash(String raw); }
public interface PasswordMatcher { boolean matches(String raw, String hash); }
```

**1-B4 · Application Layer**

`application/user/UserCommandService.java`:
- `registerUser(RegisterUserCommand)` → validates uniqueness, creates `UserAccount`, saves to repo, creates `user_profile`, `trust_score`, `user_presence` rows in a transaction.
- `loginUser(LoginUserCommand)` → finds by email, calls `user.authenticate(...)`, issues JWT via `TokenProvider`, persists `refresh_token`, returns `LoginResult`.

`application/user/TokenProvider.java` (interface):
```java
public interface TokenProvider {
    String issueAccessToken(UUID userId);
    UUID extractUserId(String token);
}
```

**1-B5 · Infrastructure Layer**

`infrastructure/repository/user/UserJdbcRepository.java` — implements `domain/user/UserRepository` using Spring `JdbcTemplate` or jOOQ.

`infrastructure/security/jwt/JwtTokenProvider.java` — implements `TokenProvider` using `io.jsonwebtoken` (JJWT).

`infrastructure/security/BcryptPasswordHasher.java` + `BcryptPasswordMatcher.java` — wraps `BCryptPasswordEncoder`.

`infrastructure/config/BeanConfig.java` — wires DI: `@Bean UserRepository`, `@Bean TokenProvider`, `@Bean PasswordHasher`, etc.

**1-B6 · Presentation Layer**

`presentation/controller/user/AuthController.java`:
```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterUserRequest req) { ... }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginUserResponse>> login(@Valid @RequestBody LoginUserRequest req) { ... }
}
```

DTOs: `RegisterUserRequest`, `LoginUserRequest`, `LoginUserResponse` (as per API spec Part 2).

**1-B7 · Flyway Migration**
- Confirm `db.sql` is split into Flyway migrations under `resources/db/migration/`.
- `V1__initial_schema.sql` — creates all 26 tables.

**1-B8 · Swagger / OpenAPI**
- Add `springdoc-openapi` to `build.gradle`.
- Accessible at `http://localhost:8080/swagger-ui.html`.
- Annotate `AuthController` with `@Tag(name = "Auth")`.

---

### Frontend Tasks

**1-F1 · Wire Real Auth in `UserRepositoryImpl`**

Replace mock bodies with real Retrofit calls:
```java
// In login():
AuthApiService api = ApiClient.getAuthApiService();
Call<ApiResponse<LoginUserResponse>> call = api.login(new LoginUserRequest(email, password));
Response<ApiResponse<LoginUserResponse>> resp = call.execute();
if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
    String token = resp.body().getData().getAccessToken();
    saveAccessToken(token);
    callback.onSuccess(token);
} else {
    String code = resp.body() != null && resp.body().getError() != null
        ? resp.body().getError().getCode() : "USER_INVALID_CREDENTIALS";
    callback.onError(new Exception(code));
}
```

Create: `data/datasource/remote/dto/response/user/LoginUserResponse.java`
```java
public class LoginUserResponse {
    private String accessToken;
    private String userId;
    // getters
}
```

Create: `data/datasource/remote/dto/request/user/LoginUserRequest.java`
```java
public class LoginUserRequest {
    private String email;
    private String password;
    // constructor + getters
}
```

Create: `data/datasource/remote/dto/request/user/RegisterUserRequest.java`
```java
public class RegisterUserRequest {
    private String fullName;
    private String email;
    private String password;
}
```

**1-F2 · Create `UserMapper`**

`data/mapper/UserMapper.java`:
```java
public class UserMapper {
    public static String toAccessToken(LoginUserResponse dto) {
        return dto.getAccessToken();
    }
}
```

**1-F3 · Update `AuthApiService.java`** (already exists — verify it matches the contract)
- Confirm `RegisterResponseDto` is replaced with `ApiResponse<Void>`.
- Confirm `LoginResponseDto` is replaced with `ApiResponse<LoginUserResponse>`.

---

### Testing / Validation
1. Start PostgreSQL locally (or Docker: `docker run -e POSTGRES_DB=walkmate -p 5432:5432 postgres:16`).
2. Start Spring Boot: `./gradlew bootRun` — Flyway runs migrations, server starts on `:8080`.
3. Open `http://localhost:8080/swagger-ui.html` — both Auth endpoints visible.
4. `POST /api/v1/auth/register` with `{"fullName":"Test User","email":"test@walkmate.com","password":"password123"}` → `200 {"success":true}`.
5. `POST /api/v1/auth/login` with same credentials → `200 {"success":true,"data":{"accessToken":"eyJ...","userId":"uuid"}}`.
6. Run Android app → tap Login → observe `D/UserRepo: real login called` in Logcat (no more `MOCK_TOKEN` value) — token stored in `EncryptedSharedPreferences`.
7. Attempt login with wrong password → app shows error toast `USER_INVALID_CREDENTIALS`.
8. `POST /api/v1/auth/register` duplicate email → `400 {"success":false,"error":{"code":"USER_EMAIL_ALREADY_EXISTS",...}}`.

---

## Phase 2 — Hotspot

### Goal
The home screen map loads real hotspot data from the backend. The mock `HotspotRepositoryImpl` is replaced.

---

### Backend Tasks

**2-B1 · Hotspot Domain**

`domain/hotspot/Hotspot.java` (aggregate — simple value, no mutable state):
```java
public class Hotspot {
    private final String hotspotId;
    private final String name;
    private final double lat;
    private final double lng;
    private int activeWalkerCount; // derived, updated by session events
}
```

`domain/hotspot/HotspotErrorCode.java`:
```java
public enum HotspotErrorCode implements ErrorCode {
    HOTSPOT_NOT_FOUND("Hotspot not found"),
    HOTSPOT_FETCH_FAILED("Failed to fetch hotspots");
}
```

`domain/hotspot/HotspotRepository.java` (interface):
```java
public interface HotspotRepository {
    List<Hotspot> findAll();
    Optional<Hotspot> findById(String id);
}
```

**2-B2 · Application Layer**

`application/hotspot/HotspotQueryService.java`:
- `getAllHotspots()` — queries repo, computes `activeWalkerCount` by joining `walk_session`.
- `getHotspotById(String id)` — throws `DomainException(HOTSPOT_NOT_FOUND)` if absent.

**2-B3 · Infrastructure**

`infrastructure/repository/hotspot/HotspotJdbcRepository.java`:
```sql
SELECT h.hotspot_id, h.name, h.lat, h.lng,
       COUNT(DISTINCT ws.session_id) AS active_walker_count
FROM hotspot h
LEFT JOIN walk_intent wi ON wi.location = h.hotspot_id
    AND wi.status IN ('OPEN','CONSUMED')
LEFT JOIN walk_session ws ON (ws.source_intent_id_a = wi.intent_id
    OR ws.source_intent_id_b = wi.intent_id)
    AND ws.status IN ('PENDING','ACTIVE')
GROUP BY h.hotspot_id
```
*(Note: if `hotspot` table doesn't exist in the current schema, add a `V2__add_hotspot_table.sql` Flyway migration and seed the 5 HCMC hotspots.)*

**2-B4 · Presentation**

`presentation/controller/hotspot/HotspotController.java`:
- `GET /api/v1/hotspots` → `ApiResponse<List<HotspotResponse>>`
- `GET /api/v1/hotspots/{id}` → `ApiResponse<HotspotResponse>`
- No auth required (public endpoint — add `@SecurityScheme` note to Swagger).

`presentation/dto/response/hotspot/HotspotResponse.java`:
```java
public record HotspotResponse(String id, String name, double lat, double lng, int activeWalkerCount) {}
```

---

### Frontend Tasks

**2-F1 · Create `HotspotResponse` DTO**

`data/datasource/remote/dto/response/hotspot/HotspotResponse.java`:
```java
public class HotspotResponse {
    private String id;
    private String name;
    private double lat;
    private double lng;
    private int activeWalkerCount;
    // getters
}
```

**2-F2 · Create `HotspotMapper`**

`data/mapper/HotspotMapper.java`:
```java
public class HotspotMapper {
    public static Hotspot toDomain(HotspotResponse dto) {
        return new Hotspot(dto.getId(), dto.getName(), dto.getLat(), dto.getLng(), dto.getActiveWalkerCount());
    }
    public static List<Hotspot> toDomainList(List<HotspotResponse> dtos) {
        List<Hotspot> result = new ArrayList<>();
        for (HotspotResponse dto : dtos) result.add(toDomain(dto));
        return result;
    }
}
```

**2-F3 · Wire Real `HotspotRepositoryImpl`**

Replace mock bodies with Retrofit:
```java
@Override
public void getHotspots(DomainCallback<List<Hotspot>> callback) {
    executor.execute(() -> {
        try {
            Retrofit retrofit = ApiClient.buildAuthenticatedRetrofit(sessionManager);
            HotspotApiService api = retrofit.create(HotspotApiService.class);
            Response<ApiResponse<List<HotspotResponse>>> resp = api.getHotspots().execute();
            if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                callback.onSuccess(HotspotMapper.toDomainList(resp.body().getData()));
            } else {
                callback.onError(new Exception(HotspotErrorCode.FETCH_FAILED));
            }
        } catch (Exception e) {
            callback.onError(e);
        }
    });
}
```

Inject `SessionManager` into `HotspotRepositoryImpl` via constructor (update `WalkMateApplication` Service Locator).

---

### Testing / Validation
1. Seed the `hotspot` table via `V2__seed_hotspots.sql`.
2. `GET http://localhost:8080/api/v1/hotspots` → 5 hotspots with real `activeWalkerCount: 0` (no sessions yet).
3. `GET http://localhost:8080/api/v1/hotspots/hs-tao-dan` → single hotspot JSON.
4. `GET http://localhost:8080/api/v1/hotspots/nonexistent` → `400 HOTSPOT_NOT_FOUND`.
5. Run Android app → home screen map pins load from real API (verify via Logcat: no `[MOCK]` tag).

---

## Phase 3 — WalkIntent Core (Create / List / Cancel)

### Goal
A logged-in user can create a WalkIntent, see their active intents list, and cancel one — all against the real backend. The `findMatch` method remains mocked (wired in Phase 4).

---

### Backend Tasks

**3-B1 · WalkIntent Domain**

`domain/intent/Intent.java` (Rich Domain Entity):
```java
public class Intent {
    private UUID intentId;
    private UUID userId;
    private String hotspotId;
    private Timestamp timeWindowStart;
    private Timestamp timeWindowEnd;
    private IntentStatus status;
    private long version; // optimistic locking

    // Rich behaviour:
    public void cancel() {
        if (this.status != IntentStatus.OPEN)
            throw new DomainException(IntentErrorCode.INTENT_ALREADY_TERMINAL);
        this.status = IntentStatus.CANCELLED;
    }

    public void consume() {
        if (this.status != IntentStatus.OPEN)
            throw new DomainException(IntentErrorCode.INTENT_ALREADY_TERMINAL);
        this.status = IntentStatus.CONSUMED;
    }

    public boolean overlapsWith(Timestamp start, Timestamp end) {
        return this.timeWindowStart.before(end) && this.timeWindowEnd.after(start);
    }
}
```

`domain/intent/IntentStatus.java`:
```java
public enum IntentStatus { OPEN, CONSUMED, CANCELLED, EXPIRED }
```

`domain/intent/IntentErrorCode.java`:
```java
public enum IntentErrorCode implements ErrorCode {
    INTENT_NOT_FOUND("Intent not found"),
    INTENT_NOT_OPEN("Intent is not in OPEN state"),
    INTENT_ALREADY_TERMINAL("Intent is in a terminal state"),
    INTENT_OVERLAPPING_TIME_WINDOW("An OPEN intent already exists in this time window"),
    INTENT_INVALID_TIME_RANGE("timeStart must be before timeEnd"),
    INTENT_INVALID_AGE_RANGE("ageMin must be ≤ ageMax"),
    INTENT_CREATE_FAILED("Failed to create intent"),
    INTENT_CANCEL_FAILED("Failed to cancel intent"),
    INTENT_FETCH_FAILED("Failed to fetch intents"),
    INTENT_MATCH_NOT_FOUND("No compatible intent found");
}
```

`domain/intent/IntentRepository.java`:
```java
public interface IntentRepository {
    Intent save(Intent intent);
    Optional<Intent> findById(UUID intentId);
    List<Intent> findOpenByUserId(UUID userId);
    boolean hasOverlappingOpenIntent(UUID userId, Timestamp start, Timestamp end);
    void updateStatus(UUID intentId, IntentStatus status, long expectedVersion);
}
```

**3-B2 · Application Layer**

`application/intent/IntentCommandService.java`:
- `createIntent(CreateIntentCommand)`:
  1. Check `hotspotId` valid.
  2. Build timestamps from `date + timeStart/timeEnd` in `ZoneId.of("Asia/Ho_Chi_Minh")`.
  3. Validate `start < end`, `ageMin <= ageMax`.
  4. Call `repo.hasOverlappingOpenIntent()` → throw `INTENT_OVERLAPPING_TIME_WINDOW` if true (I-1).
  5. Save intent with status `OPEN`.
  6. Return saved intent.
- `cancelIntent(UUID intentId, UUID callerId)`:
  1. Load intent, verify ownership.
  2. Call `intent.cancel()` (Rich Domain handles terminal state guard).
  3. Cascade: update all `PENDING` proposals referencing this intent to `EXPIRED` (I-4).
  4. Save.

`application/intent/IntentQueryService.java`:
- `listActiveIntents(UUID userId)` → `repo.findOpenByUserId(userId)`.
- `getIntentById(UUID intentId)` → throws `INTENT_NOT_FOUND` if absent.

**3-B3 · Infrastructure**

`infrastructure/repository/intent/IntentJdbcRepository.java`:
- Implements all `IntentRepository` methods using `JdbcTemplate`.
- `hasOverlappingOpenIntent` uses DB-level range overlap query:
  ```sql
  SELECT COUNT(*) FROM walk_intent
  WHERE user_id = ? AND status = 'OPEN'
    AND time_window_start < ? AND time_window_end > ?
  ```

**3-B4 · Presentation**

`presentation/controller/intent/IntentController.java`:
- `POST /api/v1/intents` (auth required) → `CreateWalkIntentRequest` → `ApiResponse<WalkIntentResponse>`
- `GET /api/v1/intents` (auth required) → `ApiResponse<List<WalkIntentResponse>>`
- `DELETE /api/v1/intents/{intentId}` (auth required) → `ApiResponse<Void>`

Add a `JwtAuthFilter` (Spring `OncePerRequestFilter`) to extract `userId` from the Bearer token and place it in `SecurityContext` / a request attribute. Controllers read caller identity from here.

`presentation/dto/response/intent/WalkIntentResponse.java`:
```java
public record WalkIntentResponse(
    String id, String hotspotId, String userId,
    float timeStart, float timeEnd,
    int ageMin, int ageMax,
    String status, String createdAt, List<String> tags
) {}
```

---

### Frontend Tasks

**3-F1 · Create Intent DTOs**

`data/datasource/remote/dto/request/walkintent/CreateWalkIntentRequest.java` (already declared in Phase 0-F4 — confirm it exists).

`data/datasource/remote/dto/response/walkintent/WalkIntentResponse.java`:
```java
public class WalkIntentResponse {
    private String id;
    private String hotspotId;
    private String userId;
    private float timeStart;
    private float timeEnd;
    private int ageMin;
    private int ageMax;
    private String status;
    private String createdAt;
    private List<String> tags;
    // getters
}
```

**3-F2 · Create `WalkIntentMapper`**

`data/mapper/WalkIntentMapper.java`:
```java
public class WalkIntentMapper {
    public static WalkIntent toDomain(WalkIntentResponse dto) {
        return new WalkIntent(dto.getId(), dto.getHotspotId(), dto.getUserId(),
            dto.getTimeStart(), dto.getTimeEnd(), dto.getAgeMin(), dto.getAgeMax(),
            dto.getStatus(), dto.getCreatedAt(), dto.getTags());
    }
    public static List<WalkIntent> toDomainList(List<WalkIntentResponse> dtos) { ... }
}
```

**3-F3 · Wire `WalkIntentApiService.java`**

File already exists. Verify the 4 method signatures match the spec.

**3-F4 · Wire 3 of 4 methods in `WalkIntentRepositoryImpl`**

Replace `createIntent()`, `listActiveIntents()`, `cancelIntent()` with real Retrofit calls. Leave `findMatch()` as mock (see Phase 4).

The `createIntent()` call must assemble a `CreateWalkIntentRequest` including a `date` field (derive from the `timeStart` float + a date parameter added to the ViewModel call chain).

**3-F5 · Update `WalkIntentRepository` interface**

```java
void createIntent(String hotspotId, String date, float timeStart, float timeEnd,
                  int ageMin, int ageMax, List<String> tags,
                  DomainCallback<WalkIntent> callback);
```
Update the ViewModel call site to pass the `date` (use `LocalDate.now().toString()` for now).

---

### Testing / Validation
1. `POST /api/v1/intents` (with valid JWT) → 200, intent created with `status: "OPEN"`.
2. `GET /api/v1/intents` → returns the created intent.
3. `DELETE /api/v1/intents/{id}` → 200; subsequent `GET` returns empty list.
4. `POST /api/v1/intents` again with same user and same time window → `400 INTENT_OVERLAPPING_TIME_WINDOW`.
5. Android: create intent from UI → list screen refreshes with real data → cancel one → it disappears.
6. `findMatch` on the app still returns the mock error "No match found yet" — that is expected and correct.

---

## Phase 4 — Matching Engine (findMatch + MatchProposal)

### Goal
Two users with overlapping intents can be matched. The P-3 atomic session-creation transaction is live. MatchProposal accept/pass flows work end-to-end.

---

### Backend Tasks

**4-B1 · Matching Domain**

`domain/proposal/MatchProposal.java` (Rich Domain Entity):
```java
public class MatchProposal {
    private UUID proposalId;
    private UUID intentIdA;
    private UUID intentIdB;
    private boolean acceptedByA;
    private boolean acceptedByB;
    private ProposalStatus status;
    private Timestamp expiresAt;

    public boolean recordAcceptance(UUID callerIntentId) {
        if (this.status != ProposalStatus.PENDING)
            throw new DomainException(ProposalErrorCode.PROPOSAL_ALREADY_TERMINAL);
        if (callerIntentId.equals(intentIdA)) this.acceptedByA = true;
        else if (callerIntentId.equals(intentIdB)) this.acceptedByB = true;
        else throw new DomainException(ProposalErrorCode.PROPOSAL_NOT_FOUND);
        return this.acceptedByA && this.acceptedByB; // true = both accepted
    }

    public void reject() {
        if (this.status != ProposalStatus.PENDING)
            throw new DomainException(ProposalErrorCode.PROPOSAL_ALREADY_TERMINAL);
        this.status = ProposalStatus.REJECTED;
    }

    public void confirm(Timestamp confirmedAt) {
        this.status = ProposalStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
    }
}
```

`domain/proposal/ProposalRepository.java`:
```java
public interface ProposalRepository {
    MatchProposal save(MatchProposal proposal);
    Optional<MatchProposal> findById(UUID proposalId);
    List<MatchProposal> findPendingByUserId(UUID userId);
    void expireProposalsForIntent(UUID intentId); // I-4 cascade
}
```

**4-B2 · MatchDomainService (P-3 Atomic Transaction)**

`domain/proposal/MatchDomainService.java` (domain service, not application service):
```java
public class MatchDomainService {
    /**
     * Executes the P-3 atomic session creation protocol.
     * Must be called inside a transaction boundary managed by IntentCommandService.
     */
    public WalkSession confirmProposalAndCreateSession(
            MatchProposal proposal,
            Intent intentA,
            Intent intentB,
            Timestamp now) {
        // P-2: verify both still OPEN
        if (intentA.getStatus() != IntentStatus.OPEN || intentB.getStatus() != IntentStatus.OPEN)
            throw new DomainException(ProposalErrorCode.PROPOSAL_INTENT_NO_LONGER_OPEN);
        // P-3e: consume both intents
        intentA.consume();
        intentB.consume();
        // P-3f: confirm proposal
        proposal.confirm(now);
        // Create session
        Timestamp sessionStart = maxOf(intentA.getTimeWindowStart(), intentB.getTimeWindowStart());
        Timestamp sessionEnd   = minOf(intentA.getTimeWindowEnd(),   intentB.getTimeWindowEnd());
        return WalkSession.create(intentA.getUserId(), intentB.getUserId(),
                                  sessionStart, sessionEnd,
                                  proposal.getProposalId(),
                                  proposal.getProposedLocationLat(),
                                  proposal.getProposedLocationLng());
    }
}
```

**4-B3 · Application Layer**

`application/proposal/ProposalCommandService.java`:
- `acceptProposal(UUID proposalId, UUID callerId)`:
  1. Load proposal. Call `proposal.recordAcceptance(callerIntentId)`.
  2. If not both accepted: save updated proposal and return proposal status (no session yet).
  3. If both accepted: begin `@Transactional` block with `SELECT FOR UPDATE` on both intents.
  4. Re-fetch both intents under lock. Call `MatchDomainService.confirmProposalAndCreateSession(...)`.
  5. S-2 check: verify no overlapping PENDING/ACTIVE session for either user.
  6. Save session, save both intents, save proposal — all in one transaction (P-3, X-4).
  7. Return the created `WalkSession`.
- `passProposal(UUID proposalId, UUID callerId)`:
  1. Load proposal. Call `proposal.reject()`.
  2. Save. Intents remain `OPEN` (P-5).

`application/intent/IntentCommandService.findMatch(UUID intentId, UUID callerId)`:
- Verify intent is `OPEN` and belongs to caller (I-2).
- Query for candidate intents: `OPEN`, overlapping time window, same hotspot, different user, no block relation.
- Score candidates using `TrustScore` and tag overlap (simple cosine similarity for now).
- If no candidate: throw `INTENT_MATCH_NOT_FOUND`.
- Create `MatchProposal` (status=`PENDING`) linking the two intents. Set `expires_at = now + 30 minutes`.
- Return the proposal or the caller's intent (frontend updates local state).

**4-B4 · Infrastructure**

`infrastructure/repository/proposal/ProposalJdbcRepository.java`.

**4-B5 · Presentation**

`presentation/controller/proposal/ProposalController.java`:
- `GET /api/v1/proposals` → `ApiResponse<List<WalkProposalResponse>>`
- `POST /api/v1/proposals/{proposalId}/accept` → `ApiResponse<WalkSessionResponse>`
- `POST /api/v1/proposals/{proposalId}/pass` → `ApiResponse<Void>`

`presentation/dto/response/proposal/WalkProposalResponse.java` — matches spec Part 5.

---

### Frontend Tasks

**4-F1 · Create Proposal DTOs + Mapper**

`data/datasource/remote/dto/response/proposal/WalkProposalResponse.java`.

`data/mapper/WalkProposalMapper.java`:
```java
public class WalkProposalMapper {
    public static WalkProposal toDomain(WalkProposalResponse dto) {
        return new WalkProposal(
            dto.getProposalId(), dto.getIntentId(),
            dto.getMatchedUserId(), dto.getMatchedUserName(), dto.getMatchedUserAge(),
            dto.getTrustScore(), dto.getOverlappingTags(),
            dto.getOverlappingTimeStart(), dto.getOverlappingTimeEnd(),
            WalkProposal.Status.valueOf(dto.getStatus()));
    }
}
```

**4-F2 · Create `ProposalApiService`**

`data/datasource/remote/api/ProposalApiService.java`:
```java
public interface ProposalApiService {
    @GET("api/v1/proposals")
    Call<ApiResponse<List<WalkProposalResponse>>> getProposals();

    @POST("api/v1/proposals/{proposalId}/accept")
    Call<ApiResponse<WalkSessionResponse>> acceptProposal(@Path("proposalId") String id);

    @POST("api/v1/proposals/{proposalId}/pass")
    Call<ApiResponse<Void>> passProposal(@Path("proposalId") String id);
}
```

**4-F3 · Wire All Methods in `WalkProposalRepositoryImpl`**

Replace all 3 mock methods. `acceptProposal()` now maps the `WalkSessionResponse` response through `WalkSessionMapper` (create this mapper in 4-F4).

**4-F4 · Wire `findMatch()` in `WalkIntentRepositoryImpl`**

Replace the mock error with a real call to `GET /api/v1/intents/{intentId}/match`.

**4-F5 · Create `WalkSessionMapper`**

`data/mapper/WalkSessionMapper.java`:
```java
public class WalkSessionMapper {
    public static WalkSession toDomain(WalkSessionResponse dto) {
        return new WalkSession(
            dto.getSessionId(), dto.getProposalId(),
            dto.getPartnerName(), dto.getPartnerAvatar(),
            dto.getMeetingPointLat(), dto.getMeetingPointLng(),
            dto.getScheduledTime(),
            WalkSession.Status.valueOf(dto.getStatus()));
    }
}
```

---

### Testing / Validation
1. Register two users (User A, User B). Each creates an intent for the same hotspot with overlapping time window.
2. User A calls `GET /api/v1/intents/{id}/match` → `200` with a proposal created.
3. `GET /api/v1/proposals` for User A → 1 pending proposal with User B's details.
4. User A calls `POST .../accept` → partial acceptance persisted (both users not yet accepted) — returns proposal, no session yet.
5. User B calls `POST .../accept` → P-3 executes: both intents `CONSUMED`, proposal `CONFIRMED`, session `PENDING` created.
6. `GET /api/v1/sessions/active` for both users → each sees the new session.
7. Android end-to-end: intent screen → find match button → proposal card appears → accept → session card appears on home screen.
8. Concurrency test: simultaneously accept from both users via Postman — only one session created (P-3 prevents double-creation).

---

## Phase 5 — WalkSession Full Lifecycle

### Goal
The full session state machine (`PENDING → ACTIVE → COMPLETED / CANCELLED / NO_SHOW / ABORTED`) is implemented end-to-end. A scheduled job handles auto-completion (S-7/S-9) and no-show determination (S-5/S-6).

---

### Backend Tasks

**5-B1 · WalkSession Domain**

`domain/session/WalkSession.java` (Rich Domain Entity):
```java
public class WalkSession {
    private UUID sessionId;
    private UUID user1Id;
    private UUID user2Id;
    private Timestamp user1ActivatedAt;
    private Timestamp user2ActivatedAt;
    private SessionStatus status;
    private long version;

    public void recordActivation(UUID userId, Timestamp now,
                                  Timestamp activationWindowOpen,
                                  Timestamp activationWindowClose) {
        if (this.status != SessionStatus.PENDING)
            throw new DomainException(SessionErrorCode.SESSION_NOT_PENDING);
        if (now.before(activationWindowOpen) || now.after(activationWindowClose))
            throw new DomainException(SessionErrorCode.SESSION_ACTIVATION_WINDOW_CLOSED);
        if (userId.equals(user1Id)) this.user1ActivatedAt = now;
        else if (userId.equals(user2Id)) this.user2ActivatedAt = now;
        else throw new DomainException(SessionErrorCode.SESSION_NOT_FOUND);
        if (this.user1ActivatedAt != null && this.user2ActivatedAt != null)
            this.status = SessionStatus.ACTIVE; // S-3
    }

    public void cancel(String reason, UUID cancelledBy) {
        if (this.status != SessionStatus.PENDING)
            throw new DomainException(SessionErrorCode.SESSION_CANCEL_NOT_PENDING);
        this.status = SessionStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledBy = cancelledBy;
    }

    public void abort(AbortReason reason, Timestamp now) {
        if (this.status != SessionStatus.ACTIVE)
            throw new DomainException(SessionErrorCode.SESSION_NOT_ACTIVE);
        this.status = SessionStatus.ABORTED;
        this.abortReason = reason;
        this.actualEndTime = now;
    }

    public void complete(Timestamp now) {
        if (this.status != SessionStatus.ACTIVE)
            throw new DomainException(SessionErrorCode.SESSION_NOT_ACTIVE);
        this.status = SessionStatus.COMPLETED;
        this.actualEndTime = now;
    }

    public void markNoShow() {
        if (this.status != SessionStatus.PENDING)
            throw new DomainException(SessionErrorCode.SESSION_NOT_PENDING);
        this.status = SessionStatus.NO_SHOW;
    }
}
```

`domain/session/SessionErrorCode.java` — full set per spec Part 8.

`domain/session/SessionRepository.java`:
```java
public interface SessionRepository {
    WalkSession save(WalkSession session);
    Optional<WalkSession> findById(UUID sessionId);
    List<WalkSession> findActiveByUserId(UUID userId);
    boolean hasOverlappingActiveSession(UUID userId, Timestamp start, Timestamp end); // S-2
    List<WalkSession> findSessionsPastActivationWindow(Timestamp now); // for scheduler
    List<WalkSession> findSessionsPastEndTime(Timestamp now);          // for scheduler
}
```

**5-B2 · Application Layer**

`application/session/SessionCommandService.java`:
- `activateSession(UUID sessionId, UUID callerId)` — implements invariants S-3, S-4.
- `cancelSession(UUID sessionId, UUID callerId, String reason)` — S-6.
- `abortSession(UUID sessionId, UUID callerId, AbortReason reason)` — C-2 resolution.
- `completeSession(UUID sessionId, UUID callerId)` — S-7 user-initiated (minimum 5-minute guard).
- `handleExpiredSessions()` — called by scheduler:
  - Sessions past activation window with 0 activations → `CANCELLED` (S-6).
  - Sessions past activation window with 1 activation → `NO_SHOW` (S-5).
  - Active sessions past `scheduled_end_time + maxLifespan` → `COMPLETED` (S-9).

`application/session/SessionScheduler.java` (Spring `@Scheduled`):
```java
@Scheduled(fixedDelay = 60_000) // every 60 seconds
public void runSessionLifecycleSweep() {
    sessionCommandService.handleExpiredSessions();
}
```

**5-B3 · Presentation**

`presentation/controller/session/SessionController.java`:
- `GET /api/v1/sessions/active`
- `POST /api/v1/sessions/{id}/activate`
- `POST /api/v1/sessions/{id}/cancel`
- `POST /api/v1/sessions/{id}/abort`

`presentation/dto/response/session/WalkSessionResponse.java` — matches spec Part 6.

`StateChangeLog`: every status transition writes to `session_state_change_log` (via a helper in `SessionCommandService`).

---

### Frontend Tasks

**5-F1 · Wire Full `WalkSessionRepositoryImpl`**

Replace all 3 mock methods + `activateSession()` (added in Phase 0-F5) with real Retrofit calls.

`data/datasource/remote/api/SessionApiService.java`:
```java
public interface SessionApiService {
    @GET("api/v1/sessions/active")
    Call<ApiResponse<List<WalkSessionResponse>>> getActiveSessions();

    @POST("api/v1/sessions/{sessionId}/activate")
    Call<ApiResponse<WalkSessionResponse>> activateSession(@Path("sessionId") String id);

    @POST("api/v1/sessions/{sessionId}/cancel")
    Call<ApiResponse<Void>> cancelSession(@Path("sessionId") String id, @Body CancelWalkSessionRequest body);

    @POST("api/v1/sessions/{sessionId}/abort")
    Call<ApiResponse<Void>> abortSession(@Path("sessionId") String id, @Body AbortWalkSessionRequest body);
}
```

**5-F2 · Create Session Request DTOs**

`data/datasource/remote/dto/request/walksession/CancelWalkSessionRequest.java` and `AbortWalkSessionRequest.java`.

**5-F3 · Update Session Screen UI**

- Add `NO_SHOW` and `ABORTED` status handling in `WalkSessionAdapter` or the session Fragment (render appropriate UI chips without crashing).
- Wire `activateSession()` to the "Arrive" button in the session detail screen.

---

### Testing / Validation
1. Create a session via the Phase 4 flow. Confirm it is `PENDING`.
2. `POST /api/v1/sessions/{id}/activate` from User 1 → still `PENDING` (only 1 activated).
3. `POST /api/v1/sessions/{id}/activate` from User 2 → session transitions to `ACTIVE`.
4. `POST /api/v1/sessions/{id}/abort` → `ABORTED`.
5. Wait for scheduler sweep: create a session, let activation window expire → check DB that status becomes `CANCELLED` or `NO_SHOW`.
6. Android: tap "Arrive" button → session status changes to ACTIVE on both users' screens.
7. `session_state_change_log` table shows the full audit trail of every transition.

---

## Phase 6 — GPS Tracking Sync

### Goal
GPS route points recorded locally in Room are successfully synced to the backend `session_point_chunks` table. The mock in `TrackingRepositoryImpl.pushRoutePoints()` is replaced.

---

### Backend Tasks

**6-B1 · Tracking Application + Infrastructure**

`application/tracking/TrackingCommandService.java`:
- `syncRoutePoints(UUID sessionId, UUID callerId, List<RoutePointPayload> points)`:
  1. Verify session is `ACTIVE` and caller is a participant.
  2. Validate all points: lat/lng bounds, timestamp <= now.
  3. Encode the batch as a Google Encoded Polyline string.
  4. Determine `chunk_index` = `MAX(chunk_index) + 1` for this session.
  5. Insert `session_point_chunks` row.
  6. Return `PushRoutePointsResponse` with `acknowledgedIds` matching the request `localId` values.

`infrastructure/repository/tracking/TrackingJdbcRepository.java`:
```java
public interface TrackingRepository {
    int nextChunkIndex(UUID sessionId);
    void saveChunk(UUID sessionId, int chunkIndex, String polyline,
                   byte[] timestamps, int pointCount);
}
```

`PolylineEncoder.java` (utility in `infrastructure/util/`):
- Converts `List<LatLng>` to Google Encoded Polyline string.

**6-B2 · Presentation**

`presentation/controller/tracking/TrackingController.java`:
- `POST /api/v1/tracking/sync` (auth required, session must be ACTIVE).

DTOs: `PushRoutePointsRequest` (with `RoutePointPayload` inner record), `PushRoutePointsResponse`.

---

### Frontend Tasks

**6-F1 · Replace Mock `pushRoutePoints()`**

In `TrackingRepositoryImpl`, uncomment the real Retrofit call block and delete the mock body:
```java
@Override
public void pushRoutePoints(String sessionId, List<RoutePoint> points, DomainCallback<Void> callback) {
    executor.execute(() -> {
        try {
            List<PushRoutePointsRequest.RoutePointPayload> payloads = RoutePointMapper.toPayloadList(points);
            PushRoutePointsRequest request = new PushRoutePointsRequest(sessionId, payloads);
            Retrofit retrofit = ApiClient.buildAuthenticatedRetrofit(sessionManager);
            RoutePointSyncApiService api = retrofit.create(RoutePointSyncApiService.class);
            Response<ApiResponse<PushRoutePointsResponse>> response = api.pushRoutePoints(request).execute();
            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                callback.onSuccess(null);
            } else {
                callback.onError(new Exception(TrackingErrorCode.SYNC_FAILED));
            }
        } catch (Exception e) {
            callback.onError(e);
        }
    });
}
```

**6-F2 · Create Tracking DTOs**

`data/datasource/remote/dto/request/tracking/PushRoutePointsRequest.java`.
`data/datasource/remote/dto/response/tracking/PushRoutePointsResponse.java`.

`RoutePointMapper.toPayloadList()` — maps `RoutePointEntity` to `RoutePointPayload`.

**6-F3 · Inject `SessionManager` into `TrackingRepositoryImpl`**

Update `WalkMateApplication` to pass `sessionManager` when constructing `TrackingRepositoryImpl`.

---

### Testing / Validation
1. Start a walk session in `ACTIVE` state.
2. Walk around the emulator (simulate location via the emulator's extended controls).
3. After 50 GPS points: Room DB auto-triggers `pushRoutePoints()`.
4. Check `session_point_chunks` table: 1 row inserted with `point_count = 50`.
5. Logcat: `[MOCK SYNC]` log line no longer appears; network call log appears instead.
6. Force sync failure (shut down server mid-walk): points remain in Room as `is_synced = false`; app doesn't crash.

---

## Phase 7 — User Profile CRUD

### Goal
Users can view and edit their profile (avatar, bio, date of birth, gender, tags, search radius). Profile data is served from the backend.

---

### Backend Tasks

**7-B1 · Profile Domain**

`domain/user/UserProfile.java` (value object / entity):
```java
public class UserProfile {
    private UUID userId;
    private String fullName;
    private Gender gender;
    private LocalDate dateOfBirth; // must be > 13 years ago (DB constraint)
    private String avatarUrl;
    private String bio;
    private int searchRadius; // metres, 0 < r <= 50000

    public void update(String fullName, Gender gender, LocalDate dob,
                       String bio, int searchRadius) {
        // Rich domain validates minimum age:
        if (dob != null && Period.between(dob, LocalDate.now()).getYears() < 13)
            throw new DomainException(UserErrorCode.USER_INVALID_DATA);
        // apply updates...
    }
}
```

**7-B2 · Profile Tags**

`domain/user/ProfileTag.java` (value type stored in `profile_tag` table).

**7-B3 · Application**

`application/user/UserQueryService.java`:
- `getProfile(UUID userId)`.
- `getMyProfile(UUID callerId)`.

`application/user/UserCommandService.java` (extend existing):
- `updateProfile(UpdateProfileCommand)`.
- `updateAvatar(UUID userId, String avatarUrl)`.

**7-B4 · Infrastructure**

File upload: `POST /api/v1/profile/avatar` accepts `multipart/form-data`. Store to local file system or S3-compatible storage (MinIO for local dev). Return a URL.

**7-B5 · Presentation**

New endpoints:
- `GET /api/v1/profile/me` → `ApiResponse<UserProfileResponse>`
- `PUT /api/v1/profile/me` → `ApiResponse<UserProfileResponse>`
- `POST /api/v1/profile/avatar` → `ApiResponse<AvatarUploadResponse>`
- `GET /api/v1/users/{userId}` → `ApiResponse<UserProfileResponse>` (public profile)

`UserProfileResponse`:
```java
public record UserProfileResponse(
    String userId, String fullName, String gender,
    String dateOfBirth, String avatarUrl, String bio,
    int searchRadius, int trustScore, List<String> tags
) {}
```

---

### Frontend Tasks

**7-F1 · Profile Screen**

The profile screen already exists (from the earlier UI work). Wire it to real data:
- `GET /api/v1/profile/me` on screen load.
- `PUT /api/v1/profile/me` on Save button.
- Avatar picker + `POST /api/v1/profile/avatar` on image selection.

**7-F2 · `UserProfileRepository` + DTOs + Mapper**

New repository interface: `domain/user/UserProfileRepository.java`.
New impl: `data/repository/UserProfileRepositoryImpl.java`.
DTO: `data/datasource/remote/dto/response/user/UserProfileResponse.java`.
Mapper: `data/mapper/UserProfileMapper.java`.

**7-F3 · `AvatarInitialView` Integration**

The existing `AvatarInitialView` custom view already handles the photo → initials fallback. Pass the real `avatarUrl` from the profile response.

---

### Testing / Validation
1. `GET /api/v1/profile/me` → returns profile for logged-in user.
2. `PUT /api/v1/profile/me` with `dateOfBirth` < 13 years ago → `422 USER_INVALID_DATA`.
3. Upload avatar image → file saved, URL returned, profile screen shows new avatar.
4. Android: open profile → edit bio → save → re-open → bio persists.

---

## Phase 8 — Social Graph (Follow / Block)

### Goal
Users can follow/unfollow other walkers and block/unblock them. Block relations prevent matching (already referenced in Phase 4's findMatch query).

---

### Backend Tasks

**8-B1 · Social Domain**

`domain/social/FollowRelation.java` and `BlockRelation.java` (simple value objects).

`domain/social/SocialErrorCode.java`:
```java
public enum SocialErrorCode implements ErrorCode {
    FOLLOW_ALREADY_FOLLOWING("Already following this user"),
    FOLLOW_SELF_FOLLOW_FORBIDDEN("Cannot follow yourself"),
    BLOCK_ALREADY_BLOCKED("User is already blocked"),
    BLOCK_SELF_BLOCK_FORBIDDEN("Cannot block yourself"),
    SOCIAL_USER_NOT_FOUND("User not found");
}
```

`domain/social/SocialRepository.java`:
```java
public interface SocialRepository {
    void follow(UUID followerId, UUID followeeId);
    void unfollow(UUID followerId, UUID followeeId);
    void block(UUID blockerId, UUID blockedId);
    void unblock(UUID blockerId, UUID blockedId);
    boolean isFollowing(UUID followerId, UUID followeeId);
    boolean isBlocked(UUID blockerId, UUID blockedId);
    List<UUID> getFollowerIds(UUID userId);
    List<UUID> getFolloweeIds(UUID userId);
}
```

**8-B2 · Application**

`application/social/SocialCommandService.java` — follow/unfollow/block/unblock.
`application/social/SocialQueryService.java` — follower lists, isFollowing, isBlocked.

**8-B3 · Update Matching (findMatch integration)**

In `IntentCommandService.findMatch()`, add a filter:
```java
// exclude users that the caller has blocked OR who have blocked the caller
candidates = candidates.stream()
    .filter(c -> !socialRepo.isBlocked(callerId, c.getUserId()))
    .filter(c -> !socialRepo.isBlocked(c.getUserId(), callerId))
    .collect(toList());
```

**8-B4 · Presentation**

- `POST /api/v1/users/{userId}/follow`
- `DELETE /api/v1/users/{userId}/follow`
- `POST /api/v1/users/{userId}/block`
- `DELETE /api/v1/users/{userId}/block`
- `GET /api/v1/users/{userId}/followers`
- `GET /api/v1/users/{userId}/following`

---

### Frontend Tasks

**8-F1 · User Profile Screen — Social Actions**

On another user's profile screen: "Follow" / "Unfollow" button, "Block" option in overflow menu.

**8-F2 · `SocialRepository` + DTOs + Mapper**

`domain/social/SocialRepository.java` (interface).
`data/repository/SocialRepositoryImpl.java` (Retrofit calls).

**8-F3 · Follower/Following Lists Screen**

New screen: `ui/social/` with `FollowersFragment` and `FollowingFragment`.

---

### Testing / Validation
1. User A follows User B → `follow_relation` row created.
2. User A follows User B again → `400 FOLLOW_ALREADY_FOLLOWING`.
3. User A blocks User B → block row created.
4. User A creates intent overlapping with User B's intent → `findMatch` returns no match for User A (block excludes User B).
5. Android: profile screen of another user shows correct follow/block state.

---

## Phase 9 — Post-Session (Reviews + Trust Score)

### Goal
After a session is `COMPLETED`, both participants can review each other. Trust scores are recalculated after each review.

---

### Backend Tasks

**9-B1 · Review Domain**

`domain/review/WalkReview.java`:
```java
public class WalkReview {
    private UUID reviewId;
    private UUID sessionId;
    private UUID reviewerId;
    private UUID revieweeId;
    private int ratingStars; // 1-5
    private String comment;

    public static WalkReview create(UUID sessionId, UUID reviewerId, UUID revieweeId,
                                    int stars, String comment, SessionStatus sessionStatus) {
        if (sessionStatus != SessionStatus.COMPLETED)
            throw new DomainException(ReviewErrorCode.REVIEW_SESSION_NOT_COMPLETED);
        if (stars < 1 || stars > 5)
            throw new DomainException(ReviewErrorCode.REVIEW_INVALID_RATING);
        return new WalkReview(UUID.randomUUID(), sessionId, reviewerId, revieweeId, stars, comment);
    }
}
```

`domain/review/ReviewErrorCode.java`.

**9-B2 · Trust Score Recalculation**

`domain/trust/TrustScorePolicy.java` (domain policy):
```java
public class TrustScorePolicy {
    public int recalculate(TrustScore current, SessionOutcome outcome) {
        return switch (outcome) {
            case COMPLETED -> Math.min(current.getScore() + 5, 1000);
            case NO_SHOW   -> Math.max(current.getScore() - 20, 0);
            case ABORTED   -> Math.max(current.getScore() - 10, 0);
            case CANCELLED -> Math.max(current.getScore() - 5, 0);
        };
    }
}
```

`application/review/ReviewCommandService.java`:
- `submitReview(SubmitReviewCommand)`:
  1. Verify session is `COMPLETED`.
  2. Verify reviewer is a participant.
  3. Verify reviewer hasn't already submitted a review for this session (unique constraint on `session_id + reviewer_id`).
  4. Create `WalkReview` via domain factory.
  5. Save review + review tags.
  6. Recalculate `trust_score` for reviewee via `TrustScorePolicy`.
  7. Update `trust_score` table (also update `completed_sessions` counter).

**9-B3 · Presentation**

- `POST /api/v1/sessions/{sessionId}/review` → `ApiResponse<Void>`
- `GET /api/v1/users/{userId}/reviews` → `ApiResponse<List<ReviewResponse>>`

---

### Frontend Tasks

**9-F1 · Post-Session Review Screen**

New screen: `ui/session/review/` with `ReviewFragment`. Shown automatically after a session is completed.

- Star rating widget (1–5).
- `TagChipGroup` with selectable review tags (e.g. "Punctual", "Friendly").
- Comment text input.
- Submit button → `POST /api/v1/sessions/{id}/review`.

**9-F2 · Review Repository + DTOs**

`domain/review/ReviewRepository.java`.
`data/repository/ReviewRepositoryImpl.java`.
DTOs: `SubmitReviewRequest`, `ReviewResponse`.

---

### Testing / Validation
1. Complete a session → review screen auto-launches.
2. Submit a 5-star review → `walk_review` row inserted, trust_score of reviewee increases.
3. Submit review twice for same session → `400 REVIEW_ALREADY_SUBMITTED`.
4. Submit review for a `PENDING` session → `400 REVIEW_SESSION_NOT_COMPLETED`.
5. View another user's profile → reviews list shown with average star rating.

---

## Phase 10 — Chat

### Goal
Two users with a shared `ACTIVE` or `PENDING` session can chat via the session's `chat_room`. Initial implementation uses long-polling REST; a WebSocket upgrade is deferred.

---

### Backend Tasks

**10-B1 · Chat Domain**

`domain/chat/ChatRoom.java` — wraps `chat_room` row.
`domain/chat/ChatMessage.java`:
```java
public class ChatMessage {
    public static ChatMessage send(UUID chatRoomId, UUID senderId, String content,
                                   ChatRoomStatus roomStatus) {
        if (roomStatus == ChatRoomStatus.CLOSED)
            throw new DomainException(ChatErrorCode.CHAT_ROOM_CLOSED);
        if (content == null || content.isBlank())
            throw new DomainException(ChatErrorCode.CHAT_MESSAGE_EMPTY);
        return new ChatMessage(UUID.randomUUID(), chatRoomId, senderId, content, Instant.now());
    }
}
```

`domain/chat/ChatErrorCode.java`.

**10-B2 · Chat Room Lifecycle**

`chat_room` row is created atomically when the `walk_session` is created (Phase 4, P-3 transaction). Status = `OPEN`.

When session reaches a terminal state (`COMPLETED`, `CANCELLED`, `NO_SHOW`, `ABORTED`): `chat_room.status → CLOSED` and `close_at = now`. This is handled in `SessionCommandService` terminal transitions.

**10-B3 · Application**

`application/chat/ChatCommandService.java`:
- `sendMessage(UUID sessionId, UUID senderId, String content)`.
- `markMessagesRead(UUID sessionId, UUID userId)`.

`application/chat/ChatQueryService.java`:
- `getMessages(UUID sessionId, UUID callerId, UUID afterMessageId)` — pagination by cursor.

**10-B4 · Presentation**

- `POST /api/v1/sessions/{sessionId}/chat` → `ApiResponse<ChatMessageResponse>`
- `GET /api/v1/sessions/{sessionId}/chat?after={messageId}` → `ApiResponse<List<ChatMessageResponse>>`

```java
public record ChatMessageResponse(
    String messageId, String senderId, String senderName,
    String content, String createdAt, String readAt
) {}
```

**10-B5 · Long-Polling (Initial)**

Frontend polls `GET .../chat?after={lastId}` every 3 seconds (on a background `ScheduledExecutorService`). This avoids WebSocket complexity while keeping chat functional.

---

### Frontend Tasks

**10-F1 · Chat Screen**

New screen: `ui/session/chat/` with `ChatFragment`.
- `RecyclerView` with sent/received message bubbles.
- `WalkMateInputField` for message composition.
- Send button triggers `POST .../chat`.
- `ScheduledExecutorService` polls for new messages every 3 seconds while screen is visible.

**10-F2 · Chat Repository + DTOs + Mapper**

`domain/chat/ChatMessage.java` (frontend domain model, simpler than backend).
`domain/chat/ChatRepository.java` (interface).
`data/repository/ChatRepositoryImpl.java`.
`data/mapper/ChatMapper.java`.

---

### Testing / Validation
1. Open chat screen in session detail.
2. Send a message → it appears in the chat immediately.
3. Second user opens the same chat → message appears within 3 seconds (polling interval).
4. Mark messages as read → `read_at` timestamp set on the message row.
5. Session completes → chat room closes → sending a message returns `400 CHAT_ROOM_CLOSED`.
6. Offline: message send fails gracefully with a toast error (no crash).

---

## Phase 11 — Notifications

### Goal
Users receive notifications for key lifecycle events: new proposal, proposal accepted, session reminder, review request.

---

### Backend Tasks

**11-B1 · Notification Domain**

`domain/notification/Notification.java`:
```java
public class Notification {
    private UUID notificationId;
    private UUID userId;
    private String type;        // PROPOSAL_RECEIVED, SESSION_CONFIRMED, etc.
    private Map<String, Object> payload; // jsonb
    private NotificationStatus status;  // PENDING, SENT, READ
}
```

**11-B2 · Notification Service Integration**

Add a `NotificationPublisher` interface (in domain/shared or application layer):
```java
public interface NotificationPublisher {
    void publish(Notification notification);
}
```

Call `notificationPublisher.publish(...)` at the end of:
- `acceptProposal()` when proposal created → notify the matched user.
- P-3 completion → notify both users that session is confirmed.
- `activateSession()` when both users activate → notify both.
- Session completes → notify both users to leave a review.

**11-B3 · Pull API (Phase 11 — Push via FCM deferred to later)**

For now, implement a pull-based notification feed:
- `GET /api/v1/notifications` → `ApiResponse<List<NotificationResponse>>`
- `POST /api/v1/notifications/{id}/read` → `ApiResponse<Void>`

`NotificationJdbcRepository` persists and queries `notification` table.

**11-B4 · Push (FCM — optional stretch goal)**

Register device FCM tokens at `POST /api/v1/devices/token`. The `NotificationPublisher` implementation sends FCM messages when the user is `OFFLINE` in `user_presence`.

---

### Frontend Tasks

**11-F1 · Notification Center Screen**

New screen: `ui/notification/` with `NotificationFragment` (accessible from home screen bell icon).
- `RecyclerView` of notification cards.
- Poll `GET /api/v1/notifications` every 30 seconds when app is in foreground.
- Badge counter on bell icon shows unread count.

**11-F2 · Notification Repository + DTOs**

`domain/notification/Notification.java` (frontend model).
`domain/notification/NotificationRepository.java`.
`data/repository/NotificationRepositoryImpl.java`.

---

### Testing / Validation
1. User A creates a match → User B receives a `PROPOSAL_RECEIVED` notification in their list.
2. User B accepts → User A receives `SESSION_CONFIRMED`.
3. Session completes → both users receive `REVIEW_REQUESTED`.
4. Mark notification as read → `read_at` updated, badge count decrements.

---

## Phase 12 — Gamification

### Goal
Users earn badges for completing milestones (5 sessions, 10 km walked, etc.). Points accumulate per session. A leaderboard exists.

---

### Backend Tasks

**12-B1 · Badge System**

`domain/gamification/BadgePolicy.java` (domain policy):
```java
public class BadgePolicy {
    public List<Badge> evaluateEarned(TrustScore score, UserStats stats) {
        List<Badge> earned = new ArrayList<>();
        if (stats.completedSessions() >= 5) earned.add(Badge.FIRST_FIVE);
        if (stats.totalDistanceKm() >= 10)  earned.add(Badge.TEN_KM_WALKER);
        // ...
        return earned;
    }
}
```

**12-B2 · Session Points**

`session_point_chunks` already accumulates GPS data. After session `COMPLETED`:
1. Calculate `total_distance` from encoded polyline.
2. Calculate `total_duration` from `actual_start_time` to `actual_end_time`.
3. Award points = `distance_km * 10 + duration_minutes * 2`.
4. Evaluate badges via `BadgePolicy`.
5. Insert any new `user_badge` rows.

**12-B3 · Presentation**

- `GET /api/v1/users/{userId}/badges` → `ApiResponse<List<BadgeResponse>>`
- `GET /api/v1/users/{userId}/stats` → `ApiResponse<UserStatsResponse>`
- `GET /api/v1/leaderboard` → `ApiResponse<List<LeaderboardEntryResponse>>`

---

### Frontend Tasks

**12-F1 · Badges / Stats Screen**

On the profile screen, add a badges section below the stats row. Tap a badge to see its description. Use `WalkMateStatColumn` for the stat row (steps, distance, sessions).

**12-F2 · Post-Session Summary Screen**

After session `COMPLETED` and before the review screen: a summary screen showing distance, duration, points earned, and any new badges unlocked.

New screen: `ui/session/summary/`.

**12-F3 · Leaderboard Screen**

New screen: `ui/leaderboard/` — top 50 users by trust score with their completed session count.

---

### Testing / Validation
1. Complete 5 sessions → `FIRST_FIVE` badge appears in profile.
2. `GET /api/v1/leaderboard` → top users ordered by trust score.
3. Post-session summary screen shows correct distance and points earned.
4. Badges screen renders correctly for users with 0 badges (empty state) and multiple badges.

---

## Appendix A — Phase Dependencies

```
Phase 0  (required by all)
Phase 1  (required by 2–12)
Phase 2  (required by 3)
Phase 3  (required by 4)
Phase 4  (required by 5, 10)
Phase 5  (required by 6, 9, 10, 11, 12)
Phase 6  (required by 12)
Phase 7  (independent after Phase 1)
Phase 8  (required by Phase 4 block filter — can be done in parallel with Phase 4)
Phase 9  (required by 12)
Phase 10 (independent after Phase 5)
Phase 11 (independent after Phase 5)
Phase 12 (required by Phase 5, 6, 9)
```

---

## Appendix B — Runnable State Checklist (per Phase)

At the end of every phase, verify:
- [ ] `./gradlew build` passes (Android)
- [ ] `./gradlew bootRun` starts without error (Spring Boot)
- [ ] App installs and launches without crash on emulator
- [ ] The specific validation steps listed in the phase all pass
- [ ] No `[MOCK]` log tags appear for features that have been wired to the real API in this phase
- [ ] Mock repos still exist and are still used for features not yet implemented

---

## Appendix C — Service Locator Updates (per Phase)

`WalkMateApplication.java` must be updated at the end of each phase to provide the real repository instead of the mock:

| Phase | Change |
|---|---|
| Phase 1 | `UserRepositoryImpl` receives real `SessionManager` |
| Phase 2 | `HotspotRepositoryImpl` receives real `SessionManager` |
| Phase 3 | `WalkIntentRepositoryImpl` receives real `SessionManager` |
| Phase 4 | `WalkProposalRepositoryImpl` receives real `SessionManager` |
| Phase 5 | `WalkSessionRepositoryImpl` receives real `SessionManager` |
| Phase 6 | `TrackingRepositoryImpl` receives real `SessionManager` |
| Phase 7 | `UserProfileRepositoryImpl` added as new Singleton |
| Phase 8 | `SocialRepositoryImpl` added as new Singleton |
| Phase 9 | `ReviewRepositoryImpl` added as new Singleton |
| Phase 10 | `ChatRepositoryImpl` added as new Singleton |
| Phase 11 | `NotificationRepositoryImpl` added as new Singleton |
| Phase 12 | `GameRepositoryImpl` added as new Singleton |
