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
