# Execution Playbook: Session, Gamification, Report & History Flows

**Date:** 2026-04-08
**Branch:** `implement/realtime`
**Plan source:** `docs/dev/gamification/implementation_plan.md`
**Gap source:** `docs/dev/gamification/gap_analyze.md`

---

## How to Use This Playbook

This document is a **context-switching guide**. Each section gives you everything needed to start a fresh AI session for one implementation phase without losing context from a previous session.

**Workflow per phase:**
1. Open a new AI session.
2. Attach every file listed under **Inputs**.
3. Copy-paste the prompt under **The Prompt** verbatim.
4. At the end of the session, verify the AI has produced the output report described under **Outputs**.
5. Save the output report — it becomes the primary input for the next phase.

**Dependency order (never skip ahead):**
```
Phase 0  →  Phase 1  →  Phase 4
Phase 0  →  Phase 2   (parallel with Phase 3)
Phase 0  →  Phase 3   (parallel with Phase 2)
Phase 1 must complete before Phase 4 begins.
```

---

## Phase 0 — Schema Migration

### Phase Name
`V107 — Add user_id to session_point_chunks; add unique constraint to session_report`

### Inputs

Attach the following files at the start of the AI session:

| File | Purpose |
|------|---------|
| `docs/dev/gamification/gap_analyze.md` | Authoritative gap definitions for G-1 and G-8 |
| `docs/dev/gamification/implementation_plan.md` | Phase 0 step-by-step specification |
| `backend/src/main/resources/db/migration/V1__init.sql` | Reference schema — live definitions of `session_point_chunks` and `session_report` |
| `backend/src/main/resources/db/migration/V106__add_intent_exclude_list.sql` | Confirm latest migration version before creating V107 |

---

### The Prompt

```
Role: You are an Expert Backend Engineer specializing in PostgreSQL and Flyway migrations for a Spring Boot application.

Context:
You are working on the WalkMate project on branch `implement/realtime`.
The gap analysis is in @docs/dev/gamification/gap_analyze.md.
The implementation plan is in @docs/dev/gamification/implementation_plan.md.

Your task is to implement Phase 0 of the implementation plan ONLY.

MANDATORY FIRST STEP — ACKG MCP Verification:
Before writing any code, use the ACKG MCP Server to perform the following checks:
1. Search for any symbol referencing `session_point_chunks` to confirm no Java code holds a compile-time reference to the old unique constraint name `session_point_chunks_unique`.
2. Search for any symbol referencing `session_report_unique` to confirm the constraint does not already exist.
3. Search for `nextChunkIndex` and `saveChunk` to identify every call site — list each file and line number. You will need this list to verify Phase 1 is not blocked.
4. Confirm the latest Flyway migration file is V106 by reading the migration directory listing.

Report your ACKG findings explicitly before proceeding.

Implementation:
Following STRICTLY the steps defined in Phase 0 of the implementation_plan.md, create the file:
  `backend/src/main/resources/db/migration/V107__tracking_report_schema.sql`

The migration must contain, in this exact order:
  Step 0.1 — Add `user_id uuid NOT NULL` to `session_point_chunks`, drop `session_point_chunks_unique`, add new `UNIQUE(session_id, user_id, chunk_index)`, replace the covering index.
  Step 0.2 — Add `UNIQUE(session_id, reporter_id)` to `session_report`.

Each SQL block must be preceded by a clear comment identifying which gap it closes (G-1 or G-8).

After writing the file, run:
  `./gradlew :backend:flywayMigrate`
and report the full output.

Output Report:
When complete, generate the file:
  `docs/dev/gamification/phase_outputs/phase0_report.md`

The report must contain exactly the sections described in the Outputs section of the playbook.
```

---

### Outputs

The AI must produce `docs/dev/gamification/phase_outputs/phase0_report.md` containing:

1. **ACKG Pre-flight Results** — the list of every call site found for `nextChunkIndex` and `saveChunk` (file path + line number). This is the authoritative reference for Phase 1.
2. **Files Created** — full relative path of `V107__tracking_report_schema.sql`.
3. **Migration Content Summary** — the two ALTER TABLE blocks with their gap labels, confirmed correct.
4. **`flywayMigrate` Output** — the full terminal output (must end with `BUILD SUCCESSFUL`).
5. **Schema Verification** — confirmation that `session_point_chunks` now has `user_id NOT NULL` and the new unique constraint, and that `session_report` has `session_report_unique`. Paste the relevant `\d` psql output or equivalent JDBC metadata query result.
6. **Open Issues** — any deviations from the plan or blockers encountered.

---

---

## Phase 1 — GPS Chunk Repository & Service Layer

### Phase Name
`Fix per-user chunk index, saveChunk ownership, and gamification distance calculation`

### Inputs

Attach the following files at the start of the AI session:

| File | Purpose |
|------|---------|
| `docs/dev/gamification/gap_analyze.md` | Gap definitions G-1 through G-5 |
| `docs/dev/gamification/implementation_plan.md` | Phase 1 step-by-step specification |
| `docs/dev/gamification/phase_outputs/phase0_report.md` | **Phase 0 output** — ACKG call-site list, confirmed migration result |
| `backend/src/main/java/com/walkmate/domain/tracking/TrackingChunkRepository.java` | Current interface |
| `backend/src/main/java/com/walkmate/infrastructure/repository/tracking/TrackingChunkJdbcRepository.java` | Current JDBC implementation |
| `backend/src/main/java/com/walkmate/application/tracking/TrackingCommandService.java` | Call site for chunk repo |
| `backend/src/main/java/com/walkmate/application/gamification/GamificationCommandService.java` | Distance calculation target |

---

### The Prompt

```
Role: You are an Expert Backend Engineer specializing in Java Spring Boot and domain-driven design.

Context:
You are working on the WalkMate project on branch `implement/realtime`.
The gap analysis is in @docs/dev/gamification/gap_analyze.md.
The implementation plan is in @docs/dev/gamification/implementation_plan.md.
Phase 0 has been completed; its results are in @docs/dev/gamification/phase_outputs/phase0_report.md.

Your task is to implement Phase 1 of the implementation plan ONLY.

MANDATORY FIRST STEP — ACKG MCP Verification:
Before writing any code, use the ACKG MCP Server to perform the following checks:
1. Call `get_file_outline` on `TrackingChunkRepository` — list all current method signatures.
2. Call `find_usages` on `nextChunkIndex` — confirm the full list of call sites matches what was recorded in phase0_report.md. If there is any discrepancy, STOP and report it.
3. Call `find_usages` on `saveChunk` — confirm the same.
4. Call `find_usages` on `calculateTotalDistanceKm` — identify its call site inside `GamificationCommandService` and confirm the current parameter type (String sessionId vs WalkSession).
5. Call `find_usages` on `findPolylinesBySessionId` — confirm it is called in `GamificationCommandService` and nowhere else that would break if the gamification call site is changed.

Report your ACKG findings explicitly before proceeding.

Implementation:
Following STRICTLY the steps defined in Phase 1 of the implementation_plan.md:

Step 1.1: Modify `TrackingChunkRepository` interface:
  - Add: `int nextChunkIndex(String sessionId, String userId)`
  - Add: `void saveChunk(String sessionId, String userId, int chunkIndex, String polyline, byte[] timestamps, int pointCount)`
  - Add: `List<String> findPolylinesBySessionAndUser(String sessionId, String userId)`
  - Add: `int countChunks(String sessionId, String userId)`
  - Remove: the old `nextChunkIndex(String sessionId)` and `saveChunk(String sessionId, int chunkIndex, ...)` signatures ONLY if ACKG confirms they have no other callers beyond `TrackingCommandService`.

Step 1.2: Modify `TrackingChunkJdbcRepository`:
  - Implement all four new methods using scoped SQL (WHERE session_id = :sid AND user_id = :userId).
  - Remove implementations of the old signatures (if removed from interface in Step 1.1).

Step 1.3: Modify `TrackingCommandService.syncRoutePoints`:
  - Pass `callerId` to both `nextChunkIndex(sessionId, callerId)` and `saveChunk(sessionId, callerId, ...)`.
  - No other logic changes.

Step 1.4: Modify `GamificationCommandService.calculateTotalDistanceKm`:
  - Change signature to accept `WalkSession session` instead of `String sessionId`.
  - Implement fallback-user selection: count chunks for user_id_a and user_id_b; pick the user with MORE chunks. If equal, default to user_id_a.
  - Use `findPolylinesBySessionAndUser(sessionId, canonicalUserId)` for distance aggregation.
  - Update the call site in `rewardBothParticipants` accordingly.

After all changes, run:
  `./gradlew :backend:compileJava`
and report the full output.

Output Report:
When complete, generate the file:
  `docs/dev/gamification/phase_outputs/phase1_report.md`

The report must contain exactly the sections described in the Outputs section of the playbook.
```

---

### Outputs

The AI must produce `docs/dev/gamification/phase_outputs/phase1_report.md` containing:

1. **ACKG Pre-flight Results** — confirmed call-site list for `nextChunkIndex`, `saveChunk`, `calculateTotalDistanceKm`, `findPolylinesBySessionId`. Note any discrepancy with the Phase 0 report.
2. **Files Modified** — full relative path of each changed file with a one-line summary of what changed.
3. **Interface Diff Summary** — the four new method signatures added to `TrackingChunkRepository`; the removed old signatures (or rationale for keeping them).
4. **JDBC Implementation Snippets** — the SQL used for `nextChunkIndex`, `saveChunk`, `findPolylinesBySessionAndUser`, and `countChunks` (confirmed correct against V107 schema).
5. **Gamification Distance Logic** — the final form of the fallback-user selector inside `calculateTotalDistanceKm` (code snippet).
6. **`compileJava` Output** — full terminal output (must end with `BUILD SUCCESSFUL`).
7. **Gaps Closed** — G-2, G-3, G-4, G-5 marked closed; G-1 confirmed closed by Phase 0.
8. **Open Issues / Deviations** — any deviation from the plan.

---

---

## Phase 2 — Badge Evaluation Service

### Phase Name
`Extract BadgeEvaluationService and wire it into ReviewCommandService`

### Inputs

Attach the following files at the start of the AI session:

| File | Purpose |
|------|---------|
| `docs/dev/gamification/gap_analyze.md` | Gap definitions G-6 and G-7 |
| `docs/dev/gamification/implementation_plan.md` | Phase 2 step-by-step specification |
| `docs/dev/gamification/phase_outputs/phase0_report.md` | Confirms schema baseline is stable |
| `backend/src/main/java/com/walkmate/application/gamification/GamificationCommandService.java` | Source of inline badge logic to be extracted |
| `backend/src/main/java/com/walkmate/application/review/ReviewCommandService.java` | Target for BadgeEvaluationService wiring |
| `backend/src/main/java/com/walkmate/domain/gamification/BadgePolicy.java` | Badge evaluation rules |
| `backend/src/main/java/com/walkmate/domain/gamification/UserBadgeRepository.java` | Repository interface used by the new service |

---

### The Prompt

```
Role: You are an Expert Backend Engineer specializing in Java Spring Boot and domain-driven design.

Context:
You are working on the WalkMate project on branch `implement/realtime`.
The gap analysis is in @docs/dev/gamification/gap_analyze.md.
The implementation plan is in @docs/dev/gamification/implementation_plan.md.
Phase 0 has been completed; its results are in @docs/dev/gamification/phase_outputs/phase0_report.md.
Phase 2 does NOT depend on Phase 1 — it can be executed in parallel.

Your task is to implement Phase 2 of the implementation plan ONLY.

MANDATORY FIRST STEP — ACKG MCP Verification:
Before writing any code, use the ACKG MCP Server to perform the following checks:
1. Call `get_file_outline` on `GamificationCommandService` — identify exactly which lines contain the inline badge logic (findBadgeNamesByUserId, BadgePolicy.evaluateEarned, badgeRepository.saveAll).
2. Call `get_file_outline` on `ReviewCommandService` — confirm the line where `reviewee.applyTrustScore(newScore)` and `userRepository.save(reviewee)` are called. This is the insertion point for the new call.
3. Call `find_usages` on `UserBadgeRepository` — confirm it is currently injected only into `GamificationCommandService`. If it appears in any other class, report it before proceeding.
4. Call `find_usages` on `BadgePolicy` — confirm it is currently called only from `GamificationCommandService.rewardUser`. If it appears elsewhere, report it.
5. Confirm that a class named `BadgeEvaluationService` does NOT yet exist anywhere in the codebase.

Report your ACKG findings explicitly before proceeding.

Implementation:
Following STRICTLY the steps defined in Phase 2 of the implementation_plan.md:

Step 2.1: Create `backend/src/main/java/com/walkmate/application/gamification/BadgeEvaluationService.java`:
  - Annotated `@Slf4j @Service @RequiredArgsConstructor`.
  - Single dependency: `UserBadgeRepository`.
  - Single public method: `evaluateAndAward(User user)`.
  - Internally creates `UserStats` from the user object, loads existing badge names, calls `BadgePolicy.evaluateEarned`, saves new badges, and logs the result.
  - Must be idempotent — relies on `ON CONFLICT DO NOTHING` in `UserBadgeRepository.saveAll`.

Step 2.2: Modify `GamificationCommandService`:
  - Inject `BadgeEvaluationService` (add to constructor via `@RequiredArgsConstructor`).
  - In `rewardUser`: replace the three-line inline badge block with a single call to `badgeEvaluationService.evaluateAndAward(user)`.
  - Remove the `UserBadgeRepository` field from `GamificationCommandService` IF AND ONLY IF ACKG confirmed it has no other usage in that class. Do not remove it if any other method still uses it directly.

Step 2.3: Modify `ReviewCommandService`:
  - Inject `BadgeEvaluationService`.
  - After the line `userRepository.save(reviewee)`, add: `badgeEvaluationService.evaluateAndAward(reviewee)`.
  - No other changes to `ReviewCommandService`.

After all changes, run:
  `./gradlew :backend:compileJava`
and report the full output.

Output Report:
When complete, generate the file:
  `docs/dev/gamification/phase_outputs/phase2_report.md`

The report must contain exactly the sections described in the Outputs section of the playbook.
```

---

### Outputs

The AI must produce `docs/dev/gamification/phase_outputs/phase2_report.md` containing:

1. **ACKG Pre-flight Results** — confirmed location of inline badge logic in `GamificationCommandService`, confirmed insertion point in `ReviewCommandService`, confirmed `BadgeEvaluationService` did not previously exist.
2. **Files Created** — `BadgeEvaluationService.java` with a summary of its structure.
3. **Files Modified** — `GamificationCommandService.java` (what was removed, what was added) and `ReviewCommandService.java` (where the call was inserted).
4. **Before/After Snippet for `rewardUser`** — the three-line inline block replaced by the single delegation call.
5. **Before/After Snippet for `submitReview`** — the insertion point showing the new `evaluateAndAward` call in context.
6. **`compileJava` Output** — full terminal output (must end with `BUILD SUCCESSFUL`).
7. **Idempotency Confirmation** — explicit note that double-calling `evaluateAndAward` for the same user is safe and why (DB constraint + `ON CONFLICT DO NOTHING`).
8. **Gaps Closed** — G-6 and G-7 marked closed.
9. **Open Issues / Deviations** — any deviation from the plan.

---

---

## Phase 3 — Report Flow

### Phase Name
`Add SessionReport domain, ReportCommandService, and ReportController`

### Inputs

Attach the following files at the start of the AI session:

| File | Purpose |
|------|---------|
| `docs/dev/gamification/gap_analyze.md` | Gap definitions G-8 through G-11 and validation rules |
| `docs/dev/gamification/implementation_plan.md` | Phase 3 step-by-step specification |
| `docs/dev/gamification/phase_outputs/phase0_report.md` | **Required** — confirms `session_report_unique` constraint is live |
| `backend/src/main/resources/db/migration/V1__init.sql` | Reference for `session_report` column names and types |
| `backend/src/main/java/com/walkmate/domain/review/WalkReview.java` | Pattern reference for domain class structure |
| `backend/src/main/java/com/walkmate/domain/review/ReviewErrorCode.java` | Pattern reference for error codes |
| `backend/src/main/java/com/walkmate/application/review/ReviewCommandService.java` | Pattern reference for application service structure |
| `backend/src/main/java/com/walkmate/application/session/SessionCommandService.java` | Reference for session status validation patterns |
| `backend/src/main/java/com/walkmate/presentation/controller/SessionController.java` | Pattern reference for controller structure and auth extraction |

---

### The Prompt

```
Role: You are an Expert Backend Engineer specializing in Java Spring Boot and domain-driven design.

Context:
You are working on the WalkMate project on branch `implement/realtime`.
The gap analysis is in @docs/dev/gamification/gap_analyze.md.
The implementation plan is in @docs/dev/gamification/implementation_plan.md.
Phase 0 has been completed; its results are in @docs/dev/gamification/phase_outputs/phase0_report.md.
Phase 3 does NOT depend on Phase 1 or Phase 2 — it can be executed in parallel with Phase 2.

Your task is to implement Phase 3 of the implementation plan ONLY.

MANDATORY FIRST STEP — ACKG MCP Verification:
Before writing any code, use the ACKG MCP Server to perform the following checks:
1. Search for any class named `SessionReport`, `ReportCommandService`, or `ReportController` to confirm they do not already exist.
2. Call `get_file_outline` on `WalkReview` — extract the static factory pattern (`WalkReview.create(...)`) to use as the structural template for `SessionReport.create(...)`.
3. Call `get_file_outline` on `SessionCommandService` — identify how session status checks and participant verification are implemented. Your `ReportCommandService` must follow the same guard pattern.
4. Call `find_usages` on `SessionStatus` — confirm the full set of enum values available (PENDING, ACTIVE, COMPLETED, NO_SHOW, ABORTED, CANCELLED) to ensure the switch statement in `submitReport` covers all cases.
5. Call `get_file_outline` on `SessionController` — identify how `callerId` is extracted from the security context (the exact method call to replicate in `ReportController`).

Report your ACKG findings explicitly before proceeding.

Implementation:
Following STRICTLY the steps defined in Phase 3 of the implementation_plan.md:

Step 3.1 — Domain layer:
  Create `domain/report/SessionReport.java`:
    - Immutable value object with fields: reportId (UUID, generated), sessionId, reporterId, reportedUserId, reason, evidenceUrl (nullable), createdAt.
    - Static factory: `SessionReport.create(sessionId, reporterId, reportedUserId, reason, evidenceUrl)`.
  Create `domain/report/SessionReportRepository.java`:
    - `void save(SessionReport report)`
    - `boolean existsBySessionAndReporter(String sessionId, String reporterId)`
  Create `domain/report/ReportErrorCode.java`:
    - `REPORT_SESSION_INVALID_STATUS`, `REPORT_WINDOW_EXPIRED`, `REPORT_ALREADY_SUBMITTED`, `REPORT_SELF_NOT_ALLOWED`
    - Follow the exact same interface/enum pattern as the existing error codes in this project.

Step 3.2 — Infrastructure layer:
  Create `infrastructure/repository/report/SessionReportJdbcRepository.java`:
    - Implements `SessionReportRepository`.
    - `save()`: INSERT INTO session_report using the column names from V1__init.sql.
    - `existsBySessionAndReporter()`: SELECT COUNT(*) > 0.

Step 3.3 — Application layer:
  Create `application/report/ReportCommandService.java`:
    - `submitReport(String sessionId, String reporterId, String reportedUserId, String reason, String evidenceUrl)`
    - Implement the validation rules from gap_analyze.md Section 3.1 EXACTLY:
      - PENDING → REPORT_SESSION_INVALID_STATUS
      - ACTIVE → allow unconditionally
      - COMPLETED → allow if now < endedAt + 72h, else REPORT_WINDOW_EXPIRED
      - NO_SHOW → allow unconditionally
      - ABORTED → allow if now < endedAt + 24h, else REPORT_WINDOW_EXPIRED
      - CANCELLED → REPORT_SESSION_INVALID_STATUS
    - Guard: reporter == reportedUser → REPORT_SELF_NOT_ALLOWED
    - Guard: duplicate report → REPORT_ALREADY_SUBMITTED

Step 3.4 — Presentation layer:
  Create `presentation/controller/ReportController.java`:
    - `POST /api/sessions/{sessionId}/report`
  Create `presentation/dto/request/report/SubmitReportRequest.java`:
    - Fields: `reportedUserId` (required), `reason` (required), `evidenceUrl` (optional)

After all changes, run:
  `./gradlew :backend:compileJava`
and report the full output.

Output Report:
When complete, generate the file:
  `docs/dev/gamification/phase_outputs/phase3_report.md`

The report must contain exactly the sections described in the Outputs section of the playbook.
```

---

### Outputs

The AI must produce `docs/dev/gamification/phase_outputs/phase3_report.md` containing:

1. **ACKG Pre-flight Results** — confirmed absence of `SessionReport`/`ReportCommandService`/`ReportController`; the `WalkReview.create(...)` pattern used as template; the callerId extraction pattern from `SessionController`; the full `SessionStatus` enum values confirmed.
2. **Files Created** — full relative paths of all 7 new files.
3. **Domain Class Structure** — the fields and factory method signature of `SessionReport.java`.
4. **Error Codes** — all four `ReportErrorCode` values listed with their HTTP status mapping (if the project uses a global error code → HTTP mapping).
5. **Validation Logic Snippet** — the status-switch block from `ReportCommandService.submitReport`, showing all six cases and their outcomes.
6. **`compileJava` Output** — full terminal output (must end with `BUILD SUCCESSFUL`).
7. **Gaps Closed** — G-9, G-10, G-11 marked closed; G-8 confirmed closed by Phase 0.
8. **Open Issues / Deviations** — any deviation from the plan. Specifically note if any time-window constant (72h, 24h) was parameterized or hardcoded and why.

---

---

## Phase 4 — History Flow (Dual GPS Path Rendering)

### Phase Name
`Add session history query and dual-path route endpoint`

### Inputs

Attach the following files at the start of the AI session:

| File | Purpose |
|------|---------|
| `docs/dev/gamification/gap_analyze.md` | Gap definitions G-12 through G-16 |
| `docs/dev/gamification/implementation_plan.md` | Phase 4 step-by-step specification |
| `docs/dev/gamification/phase_outputs/phase1_report.md` | **Required** — confirms per-user chunk methods exist and their exact signatures |
| `backend/src/main/java/com/walkmate/domain/tracking/TrackingChunkRepository.java` | Current interface (post-Phase-1) |
| `backend/src/main/java/com/walkmate/infrastructure/repository/tracking/TrackingChunkJdbcRepository.java` | Current implementation (post-Phase-1) |
| `backend/src/main/java/com/walkmate/domain/session/WalkSessionRepository.java` | Target interface for history method |
| `backend/src/main/java/com/walkmate/infrastructure/repository/session/WalkSessionJdbcRepository.java` | Target implementation for history query |
| `backend/src/main/java/com/walkmate/presentation/controller/SessionController.java` | Pattern reference for controller and auth extraction |

---

### The Prompt

```
Role: You are an Expert Backend Engineer specializing in Java Spring Boot and domain-driven design.

Context:
You are working on the WalkMate project on branch `implement/realtime`.
The gap analysis is in @docs/dev/gamification/gap_analyze.md.
The implementation plan is in @docs/dev/gamification/implementation_plan.md.
Phase 1 has been completed; its results are in @docs/dev/gamification/phase_outputs/phase1_report.md.
Phase 4 depends on Phase 1 — do NOT start Phase 4 unless phase1_report.md confirms BUILD SUCCESSFUL.

Your task is to implement Phase 4 of the implementation plan ONLY.

MANDATORY FIRST STEP — ACKG MCP Verification:
Before writing any code, use the ACKG MCP Server to perform the following checks:
1. Call `get_file_outline` on `TrackingChunkRepository` — confirm that `findPolylinesBySessionAndUser(String sessionId, String userId)` and `countChunks(String sessionId, String userId)` exist with exactly these signatures (they were added in Phase 1). Cross-reference with phase1_report.md. If there is a discrepancy, STOP and report it.
2. Call `get_file_outline` on `WalkSessionJdbcRepository` — list all current `find*` methods to confirm `findCompletedByUserId` does NOT yet exist.
3. Call `get_file_outline` on `WalkSessionRepository` (domain interface) — confirm the same.
4. Call `find_usages` on `SessionStatus` — confirm the terminal statuses (COMPLETED, NO_SHOW, ABORTED, CANCELLED) to use in the `findCompletedByUserId` SQL `IN` clause.
5. Call `get_file_outline` on `WalkSession` domain class — confirm the field names `totalDistanceKm` and `totalDurationSeconds` (or their exact Java names) so the response DTO maps them correctly.
6. Search for any existing class named `TrackingQueryService`, `SessionHistoryQueryService`, or `SessionHistoryController` to confirm they do not exist.

Report your ACKG findings explicitly before proceeding.

Implementation:
Following STRICTLY the steps defined in Phase 4 of the implementation_plan.md:

Step 4.1 — Session history repository (G-13):
  Add to `WalkSessionRepository` interface:
    `List<WalkSession> findCompletedByUserId(String userId)`
  Implement in `WalkSessionJdbcRepository`:
    - SQL: SELECT all mapped columns FROM walk_session WHERE (user_id_a = :userId OR user_id_b = :userId) AND status IN ('COMPLETED','NO_SHOW','ABORTED','CANCELLED') ORDER BY COALESCE(ended_at, created_at) DESC LIMIT 50
    - Reuse the existing `mapRow` helper for result mapping.

Step 4.2 — Create `TrackingQueryService` (G-14):
  File: `application/tracking/TrackingQueryService.java`
  Method: `getSessionRoute(String sessionId, String callerId) → SessionRouteResponse`
  Logic:
    - Load session; throw SESSION_NOT_FOUND if absent.
    - Verify callerId is a participant; throw SESSION_NOT_PARTICIPANT if not.
    - Reject PENDING and ACTIVE sessions with a new error code SESSION_NOT_FINISHED.
    - Fetch pathA = findPolylinesBySessionAndUser(sessionId, session.getUserIdA())
    - Fetch pathB = findPolylinesBySessionAndUser(sessionId, session.getUserIdB())
    - Read total_distance_km and total_duration_seconds directly from the session domain object — NO re-aggregation of polylines.
    - Return SessionRouteResponse.

Step 4.3 — Create `SessionHistoryQueryService` (G-15):
  File: `application/session/SessionHistoryQueryService.java`
  Method: `getSessionHistory(String callerId) → List<SessionSummaryResponse>`
  Logic:
    - Call `findCompletedByUserId(callerId)`.
    - Map each WalkSession to SessionSummaryResponse. Include: sessionId, status, partnerId (the user that is NOT callerId), scheduledStart, scheduledEnd, totalDistanceKm, durationMinutes.
    - Partner display name is NOT required in this phase — return partnerId only.

Step 4.4 — Create DTOs and Controller (G-16):
  Create `presentation/dto/response/session/SessionRouteResponse.java`:
    - Fields: sessionId, userAPolylines (List<String>), userBPolylines (List<String>), totalDistanceKm (double), durationMinutes (int)
  Create `presentation/dto/response/session/SessionSummaryResponse.java`:
    - Fields: sessionId, status, partnerId, scheduledStart, scheduledEnd, totalDistanceKm (double), durationMinutes (int)
  Create `presentation/controller/SessionHistoryController.java`:
    - GET /api/sessions/history → List<SessionSummaryResponse>
    - GET /api/sessions/{sessionId}/route → SessionRouteResponse

Add `SESSION_NOT_FINISHED` to the appropriate error code class (follow ACKG findings for which class to use).

After all changes, run:
  `./gradlew :backend:compileJava`
and report the full output.

Output Report:
When complete, generate the file:
  `docs/dev/gamification/phase_outputs/phase4_report.md`

The report must contain exactly the sections described in the Outputs section of the playbook.
```

---

### Outputs

The AI must produce `docs/dev/gamification/phase_outputs/phase4_report.md` containing:

1. **ACKG Pre-flight Results** — confirmation that `findPolylinesBySessionAndUser` and `countChunks` exist with exact signatures matching phase1_report.md; confirmation that history methods did not previously exist; confirmed terminal status values used in SQL.
2. **Files Modified** — `WalkSessionRepository.java` and `WalkSessionJdbcRepository.java` with a summary of what was added.
3. **Files Created** — full relative paths of all new files (`TrackingQueryService`, `SessionHistoryQueryService`, `SessionHistoryController`, `SessionRouteResponse`, `SessionSummaryResponse`).
4. **History SQL** — the exact SQL used in `findCompletedByUserId`, including the `IN` clause statuses, `ORDER BY`, and `LIMIT`.
5. **Route Endpoint Logic** — the session-status guard in `getSessionRoute` that rejects PENDING and ACTIVE, and confirmation that distance is read from the session row (not re-aggregated).
6. **`compileJava` Output** — full terminal output (must end with `BUILD SUCCESSFUL`).
7. **Gaps Closed** — G-12 through G-16 marked closed. Recap of all 16 gaps across all phases: closed vs. any remaining open.
8. **Open Issues / Deviations** — any deviation from the plan. Specifically note the `SESSION_NOT_FINISHED` error code — which class it was added to and why.

---

---

## Master Gap Closure Tracking

Use this table to track status across all sessions:

| Gap | Description | Phase | Status |
|-----|-------------|-------|--------|
| G-1 | Add `user_id` to `session_point_chunks`, change unique constraint | 0 | ⬜ |
| G-2 | Update `TrackingChunkRepository` to per-user signatures | 1 | ⬜ |
| G-3 | `TrackingCommandService` passes `callerId` to chunk repo | 1 | ⬜ |
| G-4 | Add `countChunks(sessionId, userId)` to chunk repository | 1 | ⬜ |
| G-5 | Gamification distance uses fallback-user canonical path | 1 | ⬜ |
| G-6 | Extract `BadgeEvaluationService` from `GamificationCommandService` | 2 | ⬜ |
| G-7 | Wire `BadgeEvaluationService` into `ReviewCommandService` | 2 | ⬜ |
| G-8 | Add `UNIQUE(session_id, reporter_id)` to `session_report` | 0 | ⬜ |
| G-9 | Create `SessionReport` domain + `SessionReportRepository` | 3 | ⬜ |
| G-10 | Create `ReportCommandService` with window-validation logic | 3 | ⬜ |
| G-11 | Create `ReportController` — POST `/api/sessions/{id}/report` | 3 | ⬜ |
| G-12 | Add `findPolylinesBySessionAndUser` to chunk repository | 1 | ⬜ |
| G-13 | Add `findCompletedByUserId` to session repository | 4 | ⬜ |
| G-14 | Create `TrackingQueryService.getSessionRoute` | 4 | ⬜ |
| G-15 | Create `SessionHistoryQueryService.getSessionHistory` | 4 | ⬜ |
| G-16 | Create `SessionHistoryController` with history + route endpoints | 4 | ⬜ |

Update each `⬜` to `✅` as phase reports confirm each gap is closed.
