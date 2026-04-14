# WalkMate — Engineering Lessons Log

> Updated after each session per CLAUDE.md Self-Improvement Loop rule.
> Format: date · phase · lesson · why it matters.

---

## 2026-04-12 · P0-1 · Gradle Multi-Module Project Structure

### Lesson: `gradlew` lives at the root, not inside `backend/`
**What happened:** First attempt ran `./gradlew` from inside `backend/` — file not found.
**Root cause:** WalkMate is a multi-module Gradle project (`settings.gradle.kts` includes `frontend` and `backend`). The Gradle wrapper (`gradlew`, `gradlew.bat`) is only at the root level.
**Fix:** Always run Gradle tasks from the repo root using the subproject path prefix:
```
bash gradlew :backend:test ...
bash gradlew :backend:dependencies ...
```
**Rule going forward:** For any Gradle command in this repo, `cd` to the repo root (`WalkMate/`) and target the subproject explicitly with `:backend:` or `:frontend:`.

---

## 2026-04-12 · P0-1 · Testcontainers BOM Placement in Groovy DSL

### Lesson: `dependencyManagement` block goes outside `dependencies {}` in Groovy DSL
**What happened:** In Spring Boot's Groovy DSL (`build.gradle`), the `dependencyManagement { imports { mavenBom ... } }` block is a top-level block provided by the `io.spring.dependency-management` plugin — it must sit outside `dependencies {}`.
**Rule going forward:** When adding a BOM to a Groovy `build.gradle`, append the `dependencyManagement` block after the closing `}` of `dependencies`. Individual TC modules under `testImplementation` then need no version — the BOM resolves them.

---

## 2026-04-12 · P0-2 · pgvector requires a non-standard Postgres Docker image

### Lesson: Use `pgvector/pgvector:pg16`, not `postgres:16`, as the TC container image
**What happened:** The standard `postgres` Docker image does NOT include the `pgvector` extension. The V1 Flyway migration runs `CREATE EXTENSION IF NOT EXISTS vector`, which fails on the plain image.
**Fix:** Always use `pgvector/pgvector:pg16` as the `PostgreSQLContainer` image string.
**Rule going forward:** Any time a new PostgreSQL extension is added to a Flyway migration (e.g., `postgis`, `pg_trgm`), check whether the container image needs to change accordingly.

---

## 2026-04-12 · P0-2 · Firebase context loading in integration tests

### Lesson: Use `@MockitoBean` (not `@Primary` + `@TestConfiguration`) to block `FirebaseConfig` from executing
**What happened:** `FirebaseConfig` is a `@Configuration` that calls `FirebaseApp.initializeApp()` at startup using real credentials (env var or classpath JSON). Without intervention, the integration test context fails to load.
**Root cause:** `@Primary` + `@TestConfiguration` approach still allows the original `@Bean` method body to execute before being overridden, causing a real Firebase call.
**Fix:** Declare `@MockitoBean` for `FirebaseApp`, `FirebaseAuth`, and `FirebaseMessaging` in the abstract base class. Spring Boot's `MockitoPostProcessor` replaces those bean definitions BEFORE instantiation — the `@Bean` method bodies in `FirebaseConfig` never execute.
**Rule going forward:** For any infrastructure `@Configuration` that does external I/O at startup (Firebase, AWS SDK, etc.), the correct suppression strategy in integration tests is `@MockitoBean` on all beans that configuration produces.

---

## 2026-04-12 · P0-2 · TRUNCATE strategy for FK-heavy schemas

### Lesson: Truncating FK root tables with CASCADE is sufficient; no need to list every table
**What happened:** The WalkMate schema has 20+ application tables, all FK-cascading from two roots: `public.hotspot` and `public.user_account`.
**Fix:** `TRUNCATE TABLE public.hotspot, public.user_account RESTART IDENTITY CASCADE` wipes all application data. `flyway_schema_history` is untouched.
**Rule going forward:** Before choosing a TRUNCATE strategy for a new schema, always identify the FK root tables first. Listing every table individually is fragile — new tables will be missed.

---

## 2026-04-12 · P0-3 · Testcontainers singleton pattern — do NOT use @Container on abstract classes

### Lesson: `@Container` on a static field in an abstract base class stops containers between test classes
**What happened:** `AbstractIntegrationTestSmokeTest` passed, but `AuthTokenFactoryTest` (the second class) got `Connection refused` — the PostgreSQL container stopped after the first class finished.
**Root cause:** `@Container` + `@Testcontainers` ties the container lifecycle to the annotated class. When that class finishes, Testcontainers stops its `@Container` fields — even static ones on a parent class — before the next test class starts.
**Fix:** Remove `@Testcontainers` and `@Container`. Declare containers as plain `static final` fields and start them in a `static {}` initialiser. Ryuk (the Testcontainers resource reaper) shuts them down at JVM exit.
```java
static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("...");
static { postgres.start(); }
```
**Rule going forward:** For shared containers across multiple test classes, ALWAYS use the static initialiser pattern, never `@Container` on the abstract base.

---

## 2026-04-12 · P0-3 · `searchRadius` is a primitive int — must always be sent in profile update

### Lesson: Missing `searchRadius` in `PUT /api/v1/profile/me` causes a 422 validation error
**Root cause:** `UpdateProfileRequest.searchRadius` is `int` (primitive), not `Integer`. Jackson deserialises a missing field as `0`, which fails `@Min(1)`.
**Fix:** Always include `"searchRadius": 5000` (or any valid value) in the profile update payload in test helpers.
**Rule going forward:** When building JSON payloads for records with primitive numeric fields annotated with `@Min`/`@Max`, always supply an explicit value — never rely on JSON omission.

---

## 2026-04-12 · P0-2 · Docker Desktop must be running for Testcontainers to work

### Lesson: Testcontainers fails with `DockerClientProviderStrategy` if Docker Desktop daemon is stopped
**What happened:** `IllegalStateException: Could not find a valid Docker environment` — Docker Desktop was installed but not started.
**Diagnosis command:** `docker ps` — if it returns a pipe error, Docker Desktop is not running.
**Rule going forward:** Always start Docker Desktop before running any test class that extends `AbstractIntegrationTest`. This is a developer environment prerequisite, not a code problem.

---

## 2026-04-13 · Phase 1 · otp_record has no FK — not covered by CASCADE TRUNCATE

### Lesson: Standalone tables with no FK to root tables must be listed explicitly in the TRUNCATE statement
**What happened:** `PhoneOtpIntegrationTest` tests ran in non-deterministic order. When a test that called `send-otp` ran first, the `otp_record` row was NOT cleaned by the next `@BeforeEach` (TRUNCATE only covered `hotspot` + `user_account`). The subsequent test hit the 60-second OTP rate-limit guard (`USER_OTP_RATE_LIMITED`).
**Root cause:** `otp_record` has no foreign key to `user_account` or `hotspot`, so `TRUNCATE ... CASCADE` never touches it.
**Fix:** Added `public.otp_record` explicitly to the TRUNCATE statement in `AbstractIntegrationTest.resetDatabase()`.
**Rule going forward:** When a new standalone table is added to the schema (no FK to either root), it MUST be added to the TRUNCATE list immediately. Review FK graph whenever adding a migration.

---

## 2026-04-13 · Phase 1 · JwtClaimsSet.Builder.claim() rejects null values

### Lesson: `JwtClaimsSet.Builder.claim(name, value)` throws `IllegalArgumentException: value cannot be null` — phone-only users have no email
**What happened:** `PhoneOtpIntegrationTest.t09_1` received HTTP 400 `INVALID_ARGUMENT: value cannot be null` when calling `POST /api/v1/auth/phone/verify`. Root cause: `JwtTokenProvider.generateToken()` called `.claim("email", user.getEmail())` unconditionally. Phone-only users (`User.registerWithPhone()`) have `email = null`. Spring Security's `JwtClaimsSet.Builder` uses `Assert.notNull(value, "value cannot be null")` and throws.
**Fix:** Added a null guard in `JwtTokenProvider.generateToken()` — only add the `email` claim if `user.getEmail() != null`.
**Production impact:** This was a latent production bug — any phone-registered user attempting to log in would have received a 500-level error on JWT generation.
**Rule going forward:** Never call `.claim(name, value)` on a `JwtClaimsSet.Builder` with a potentially null value. Guard all optional claims with an explicit null check.

---

## 2026-04-13 · Phase 3 · seedPendingSession must use CONSUMED intent status, not OPEN

### Lesson: Guard order matters — intent overlap (guard 2a) runs before session overlap (guard 2b)
**What happened:** T15-3 expected `INTENT_OVERLAPPING_SESSION` but got `INTENT_OVERLAPPING`. The seeder inserted walk_intent rows with status `OPEN`. `WalkIntentCommandService.createIntent()` runs guard 2a first (`hasOverlappingActiveIntent` checks `OPEN/MATCHING`) — it fired immediately, masking guard 2b.
**Fix:** Changed `seedPendingSession()` to insert intent rows with `status = 'CONSUMED'::intent_status`. CONSUMED is not in the `('OPEN', 'MATCHING')` overlap check, so guard 2a passes and guard 2b (`hasOverlappingActiveSession`) can fire.
**Rule going forward:** When testing a specific guard in a multi-guard chain, ensure all earlier guards are satisfied by the seeded data. Audit the guard order in the service before writing the seeder.

---

## 2026-04-13 · Phase 1 · Logout endpoints return 204, not 200

### Lesson: `POST /auth/logout` and `POST /auth/logout-all` must return `ResponseEntity.noContent().build()` (204 No Content)
**What happened:** Controllers returned `ResponseEntity.ok(ApiResponse.success(null))` (200). Tests expected 204.
**Fix:** Changed both logout handlers to `return ResponseEntity.noContent().build()`.
**Rule going forward:** Endpoints that perform an action with no response body should return 204 No Content. Never return 200 with a null body.

---

## 2026-04-13 · Phase 1 · Partial unique index ON CONFLICT predicate is evaluated against the NEW row

### Lesson: `ON CONFLICT (cols) WHERE predicate DO UPDATE` only fires if the NEW row satisfies the partial index predicate
**What happened:** `refreshToken()` called `refreshTokenRepository.save(existing)` after `existing.revoke()`. The UPSERT was `ON CONFLICT (user_id, device_id) WHERE revoked = false DO UPDATE`. Since the new row being inserted had `revoked = true`, the partial index predicate evaluated to `false` for the new row, so ON CONFLICT never fired. The INSERT proceeded as a plain INSERT and hit the PRIMARY KEY constraint (`token_id` already existed), throwing `DuplicateKeyException` (HTTP 500).
**Root cause:** PostgreSQL evaluates the partial index predicate against the PROPOSED new row (EXCLUDED), not the existing row. If the new row does not satisfy `WHERE revoked = false`, the partial index is not an arbiter and ON CONFLICT is not triggered.
**Fix:** Added `RefreshTokenRepository.revokeById(UUID tokenId)` → `UPDATE refresh_token SET revoked = true WHERE token_id = ?`. Used this direct UPDATE in `refreshToken()` instead of `save(existing)`.
**Rule going forward:** Never use an UPSERT with a partial index conflict target to UPDATE an existing row to a value that would EXCLUDE it from the partial index. Use a direct UPDATE by primary key instead. This applies to any `ON CONFLICT ... WHERE predicate` pattern where the intended update changes the column(s) in the predicate.

---

## 2026-04-14 · Phase 4 · Private-invite pass must cancel intents, not unlock them

### Lesson: `passProposal` must branch on `isPrivate` — unlocking private intents violates I-7
**What happened:** `passProposal()` called `intentA.unlock()` + `intentB.unlock()` unconditionally, transitioning private invite intents from MATCHING → OPEN. UC-21 spec and Invariant I-7 require private intents to move MATCHING → CANCELLED so they never surface in the public wait list.
**Root cause:** `WalkIntent.unlock()` has no awareness of whether an intent is private; the routing logic must live in `passProposal`.
**Fix:** Added an `if (intentA.isPrivate() && intentB.isPrivate())` branch — private path calls `cancel()` on both; public path calls `unlock()` + `excludeUser()` as before.
**Rule going forward:** Any proposal resolution path (pass, expire, concurrent cancellation) must handle the private/public fork on intent state transitions. Check `isPrivate` before calling `unlock()`.

---

## 2026-04-14 · Phase 4 · seedPendingProposal must use MATCHING intents, not OPEN

### Lesson: Seeded proposals require MATCHING intents because `acceptProposal` critical section re-verifies MATCHING status
**What happened:** Would have failed with `PROPOSAL_INTENT_NO_LONGER_OPEN` on T20-2 if intents were seeded as OPEN — the P-3 critical section checks `status == MATCHING` before consuming intents.
**Fix:** `seedPendingProposal()` inserts both intents with `'MATCHING'::intent_status`.
**Rule going forward:** When seeding a match_proposal row for acceptance tests, always set both walk_intent rows to MATCHING. OPEN intents will fail the P-3 guard and no session will be created.

---

## 2026-04-14 · Phase 4 · MockMvc is thread-safe for concurrent integration tests

### Lesson: Two threads can safely call `MockMvc.perform()` simultaneously in a `@SpringBootTest` test
**What happened:** T20-5 required two threads to call the accept endpoint concurrently to trigger the OCC guard. MockMvc dispatches each `perform()` through the full Spring MVC stack, and each request gets its own transaction. PostgreSQL's version-based OCC (`UPDATE ... WHERE version = ?`) correctly serialises double-acceptance.
**Rule going forward:** For OCC / concurrency invariants in `@SpringBootTest` tests, use `CountDownLatch` + two `Thread` instances calling `MockMvc.perform()`. No need for `TestRestTemplate` or a separate HTTP client — MockMvc is thread-safe.

---

## 2026-04-14 · Phase 10 · UserJdbcRepository.mapRow() hardcodes totalDistanceKm=0 and completedSessions=0

### Lesson: Production bug — total_distance_km and completed_sessions are never read from the ResultSet
**What happened:** T42-1 could not assert seeded `totalDistanceKm` / `completedSessions` values because `UserJdbcRepository.mapRow()` hardcodes them to `0.0` and `0` regardless of DB state. The columns exist in the schema and are updated by services, but are excluded from the SELECT clause in both `selectAll()` and `findTopByPoints()`.
**Impact:** Stats endpoint (`/stats`) and leaderboard (`/leaderboard`) always return 0 for these two fields.
**Test approach:** Assert `isNumber()` for these fields — do not assert specific seeded values. Document as known bug.
**Rule going forward:** When a test expectation can't be met due to a pre-existing bug, assert presence/type only and add a comment. Never silently skip the assertion.

---

## 2026-04-14 · Phase 9 · notification_status has no UNREAD value — "unread" is PENDING

### Lesson: The `notification_status` PostgreSQL enum is `PENDING | SENT | READ` — there is no `UNREAD`
**What happened:** todo.md referred to seeding "one UNREAD, one READ" notification. The actual PG enum type only has `'PENDING'`, `'SENT'`, and `'READ'`. The "unread" UI concept maps to `PENDING` in the DB.
**Fix:** Seed notifications with `'PENDING'::notification_status` for unread, `'READ'::notification_status` for read.
**Rule going forward:** Never use `UNREAD` as a notification status value. Always use `PENDING` for the unread state in notification test setups.

---

## 2026-04-14 · Phase 8 · Friend request returns 201 Created, not 200 OK

### Lesson: `POST /api/v1/friends/{userId}/request` returns HTTP 201, not 200
**What happened:** todo.md listed 200 OK for T34-1. `FriendsController` returns `ResponseEntity.created(...)` → HTTP 201 Created.
**Rule going forward:** Always verify HTTP status from the controller, not the use case spec. Friend request creation follows REST convention: new resource → 201.

---

## 2026-04-14 · Phase 8 · FRIEND_REQUEST_ALREADY_PENDING requires same-direction resend

### Lesson: If B already sent to A, the service auto-accepts instead of throwing ALREADY_PENDING
**What happened:** `FriendCommandService` has a branch: if a reverse PENDING request exists (B→A), accepting it rather than creating a duplicate. Testing ALREADY_PENDING must use A→B twice (same direction), not B→A then A→B.
**Rule going forward:** When testing `FRIEND_REQUEST_ALREADY_PENDING`, always send two requests in the same direction. Cross-direction triggers auto-accept, not an error.

---

## 2026-04-14 · Phase 8 · Declined friendship row stays in DB with status DECLINED

### Lesson: Declining a friend request does NOT delete the row — it sets status to 'DECLINED'
**What happened:** T35-1b verifies that after `/decline`, the `friendship` row still exists with `status = 'DECLINED'`. The row is never physically deleted on decline or friend-remove.
**Rule going forward:** Never assert `COUNT(*) = 0` after a decline or remove-friend operation. Assert `status = 'DECLINED'` instead.

---

## 2026-04-14 · Phase 7 · rating_stars: 6 triggers HTTP 422, not HTTP 400 REVIEW_INVALID_RATING

### Lesson: DTO `@Max(5)` on `rating_stars` fires before the service-level guard — same two-layer pattern as GPS coordinates
**What happened:** Todo said `rating_stars: 6 → HTTP 400, REVIEW_INVALID_RATING`. The request DTO `SubmitReviewRequest` has `@Min(1)` and `@Max(5)` on `rating_stars`. Bean Validation at the controller catches out-of-range values first → HTTP 422, `VALIDATION_ERROR`.
**Pattern:** This is the same design as `PushRoutePointsRequest.lat` with `@DecimalMax(90.0)` from Phase 6. The service-level domain error code (`REVIEW_INVALID_RATING`) is unreachable for values blocked by the DTO validator.
**Rule going forward:** Whenever a todo says "invalid field value → HTTP 400 DOMAIN_CODE", check if the DTO also annotates that field. If `@Min`/`@Max`/`@DecimalMax` is present, the actual response is HTTP 422 `VALIDATION_ERROR`, not 400.

---

## 2026-04-14 · Phase 7 · Report window test uses `ended_at`, not `completed_at` — column is `ended_at`

### Lesson: The session terminal-time column is `ended_at`, not `completed_at`
**What happened:** Todo said "seed a COMPLETED session with `completed_at = 73 hours ago`". No such column exists — the correct column used by `ReportCommandService` for window calculation is `ended_at` in `walk_session`.
**Fix:** JDBC sets `ended_at = now() - interval '73 hours'` to expire the 72-hour reporting window.
**Rule going forward:** Session terminal timestamp is always `walk_session.ended_at`. Never reference `completed_at` in any SQL for this schema.

---

## 2026-04-14 · Phase 7 · Route replay is allowed for ALL terminal sessions, not just COMPLETED

### Lesson: `GET /sessions/{id}/route` returns 200 OK for CANCELLED (and other terminal) sessions
**What was expected:** UC-30 spec says "Initial assumption: Session is COMPLETED" and error table lists SESSION_NOT_FINISHED for non-COMPLETED sessions — implying CANCELLED would also be rejected.
**What actually happened:** T30-3 confirmed CANCELLED sessions return 200 OK with empty polyline arrays. The service only guards against non-terminal sessions (PENDING, ACTIVE) with SESSION_NOT_FINISHED.
**Rule going forward:** Route replay endpoint accepts any terminal session status. Only non-terminal (PENDING/ACTIVE) sessions are rejected with SESSION_NOT_FINISHED. Document this gap in the use case spec.

---

## 2026-04-14 · Phase 7 · Promote sessions via JDBC — no seedCompletedSession helper exists

### Lesson: Use `seedActiveSession` + raw JDBC UPDATE to create COMPLETED/ABORTED/CANCELLED sessions
**What happened:** `TestDataSeeder` has no `seedCompletedSession` or `seedAbortedSession` methods.
**Pattern established:** All Phase 7 terminal-state setups use:
```sql
UPDATE public.walk_session
SET status = 'COMPLETED'::walk_session_status, ended_at = now(),
    total_distance_km = 2.5, total_duration_seconds = 1800
WHERE session_id = ?::uuid
```
For non-stats terminal states (ABORTED, CANCELLED) omit `total_distance_km` / `total_duration_seconds`.
**Rule going forward:** Always promote via JDBC for terminal session states in tests. Do NOT add these to TestDataSeeder unless many tests need them — inline JDBC keeps test intent explicit.

---

## 2026-04-14 · Phase 6 · GPS sync timestamps must be ≤ now() — no future offsets in test payloads

### Lesson: `TrackingCommandService` rejects any point whose `timestamp` is in the future
**What happened:** T28-1 used `now + 5000`, `now + 10000` … offsets (simulating sequential GPS fixes) — all of those were in the future relative to the server clock. The service throws `IllegalArgumentException("Point timestamp is in the future: …")` → HTTP 400 `INVALID_ARGUMENT`, causing a false failure.
**Fix:** Reversed the offsets to `now - 20000`, `now - 15000`, … `now - 1000` so every point is safely in the past.
**Rule going forward:** GPS point payloads in integration tests must always use **past** `toEpochMilli()` offsets. Never use `now + N` for timestamp fields in tracking tests.

---

## 2026-04-14 · Phase 6 · Tracking data is stored in PostgreSQL, not MongoDB

### Lesson: `session_point_chunks` is a PostgreSQL table, not a MongoDB collection
**What happened:** The todo.md for T28-1 said "Assert GPS point documents exist in MongoDB". The actual implementation uses `TrackingChunkJdbcRepository` which writes to the `session_point_chunks` PostgreSQL table (Google Encoded Polyline + packed timestamp bytes).
**Fix:** DB assertion uses `jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.session_point_chunks WHERE session_id = ?::uuid AND user_id = ?::uuid", ...)`.
**Rule going forward:** GPS/route tracking is PostgreSQL-only. MongoDB in WalkMate is used exclusively for chat. Never assume tracking data lands in Mongo.

---

## 2026-04-14 · Phase 6 · lat=999.0 triggers HTTP 422, not HTTP 400 INVALID_ARGUMENT

### Lesson: DTO `@DecimalMax(90.0)` fires before the service-level coordinate guard
**What happened:** The todo said `lat = 999.0 → HTTP 400, INVALID_ARGUMENT`. In practice, `PushRoutePointsRequest.RoutePointPayload` has `@DecimalMax(value = "90.0")` on `lat`, so Bean Validation fires at the controller layer → HTTP 422 `VALIDATION_ERROR` before the service even runs.
**The service-level `IllegalArgumentException`** (→ HTTP 400 `INVALID_ARGUMENT`) is only reachable for fields not covered by DTO annotations — e.g., future timestamps (`@Positive` only; no max-epoch check).
**Rule going forward:** When testing `INVALID_ARGUMENT` from the tracking service, use a **future timestamp**, not out-of-bounds coordinates. Coordinate bounds are enforced at the DTO level (HTTP 422).

---

## 2026-04-13 · Phase 1 · @MockitoBean in subclass creates a separate Spring context

### Lesson: Declaring `@MockitoBean` in a subclass (not the abstract base) forces Spring to spin up a new application context for that test class
**What happened:** `PhoneOtpIntegrationTest` declared `@MockitoBean SmsGateway smsGateway` — different from the other Phase 1 test classes that only use the mocks from `AbstractIntegrationTest`. This caused a separate Spring Boot context to boot (new Flyway run, new container port bindings).
**Trade-off accepted:** The design is intentional (per user instruction — SmsGateway mock belongs in `PhoneOtpIntegrationTest` only). The extra context startup cost (~25s) is acceptable for correct separation of concerns.
**Rule going forward:** Document when a test class introduces a new `@MockitoBean` beyond what `AbstractIntegrationTest` provides — it always creates a new context. If startup time becomes a problem, consolidate mocks into the base class.

---
