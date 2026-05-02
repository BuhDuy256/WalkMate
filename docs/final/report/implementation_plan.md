# Implementation Plan: Report → AI Matching + Admin Dispute Resolution

**Based on:** `01_review_affects_ai_matching.md`, `02_report_uc_trigger.md`, `03_report_ai_matching_proposal.md`  
**Scope:** Backend only (frontend already sends all required data). No message brokers, no rate limiting.  
**Architecture:** DDD-lite + Layered, synchronous transactions + `@Async` for weight training (mirrors the existing Review architecture).

---

## Architecture Overview

```
[Phase 0] DB Migration
      ↓
[Phase 1] Domain Layer  ←  foundation for every phase above it
      ↓
[Phase 2] Channel 1 — Trust Penalty on Submit
      ↓
[Phase 3] Channel 2 — Reporter Weight Training on Submit
      ↓
[Phase 4] Weight Imbalance Mitigation (cap in normalize())
      ↓
[Phase 5] Admin Query — view reports
      ↓
[Phase 6] Admin Command — approve / reject (with trust revert)
      ↓
[Phase 7] Presentation Layer — Admin REST endpoints
      ↓
[Phase 8] Security — protect /admin/** routes
```

Each phase builds on the previous one. Phases 2–4 are independent of Phases 5–8 and can be developed in parallel by separate team members after Phase 1 is complete.

---

## Phase 0 — Database Migration

**One new Flyway file:** `V[next]__report_ai_matching_support.sql`

### 0.1 — Extend `report_status` Enum

Add two new enum values to the existing `report_status` PostgreSQL type:

| New Value | Meaning |
|---|---|
| `APPROVED` | Admin confirmed the report is valid. Auto-deducted trust penalty stands. |
| `REJECTED` | Admin determined the report is false. Trust penalty is reversed. |

Existing value `OPEN` (default) remains the initial state.

### 0.2 — Add Columns to `session_report`

Add four new nullable columns to the existing `session_report` table:

| Column | Type | Purpose |
|---|---|---|
| `applied_trust_delta` | `INTEGER NOT NULL DEFAULT 0` | The exact trust-score delta that was actually applied at submission time. Stored because the no-show guard may reduce the effective delta to 0 even when `reason` implies a penalty. Needed to compute the exact reversal amount on rejection. |
| `resolved_by` | `UUID` (nullable, FK → `user_account.user_id`) | The admin who approved or rejected the report. Null while `OPEN`. |
| `resolved_at` | `TIMESTAMP` (nullable) | When the admin acted. Null while `OPEN`. |
| `resolution_note` | `TEXT` (nullable) | Optional admin comment. |

**No other tables are affected by this migration.**  
`dispute_case` table already exists in the schema but is not used by this feature. It is reserved for future complex multi-party dispute workflows.

---

## Phase 1 — Domain Layer

All domain changes are pure Java with no framework dependencies. This phase establishes the contracts that every higher layer depends on.

### 1.1 — Modify `TrustScorePolicy.java`

**File:** `backend/src/main/java/com/walkmate/domain/review/TrustScorePolicy.java`  
**Action:** Modify (add one static method)

Add a new static method `deltaForReason(String reason)` that maps the four report reason strings to their corresponding negative penalty integers. The method must be deterministic and side-effect-free.

**Penalty map to implement:**

| `reason` value | Delta |
|---|---|
| `SAFETY_CONCERN` | −50 |
| `PARTNER_MISCONDUCT` | −30 |
| `OTHER` | −10 |
| `EMERGENCY` | 0 (no penalty, ambiguous cause) |
| Any unrecognised value | 0 (safe default) |

The existing `apply(int currentScore, int delta)` method (clamps to `[0, 1000]`) is reused unchanged. No new policy methods are needed.

### 1.2 — Modify `SessionReport.java`

**File:** `backend/src/main/java/com/walkmate/domain/report/SessionReport.java`  
**Action:** Modify (add fields + two domain methods)

**Add new fields** corresponding to the migration columns:
- `int appliedTrustDelta` — defaults to `0`, set at creation time once the actual applied delta is known.
- `String resolvedBy` — null until admin acts.
- `Instant resolvedAt` — null until admin acts.
- `String resolutionNote` — null until admin acts.
- `String status` — already present; will now carry `OPEN`, `APPROVED`, or `REJECTED`.

**Add two domain methods that encapsulate state transitions:**

- `approve(String adminUserId, String note)` — transitions status from `OPEN` to `APPROVED`, sets `resolvedBy`, `resolvedAt`, `resolutionNote`. Must guard against re-resolving an already-resolved report (throw `REPORT_ALREADY_RESOLVED`).
- `reject(String adminUserId, String note)` — same as above but sets status to `REJECTED`. The actual trust-score reversal is **not** done inside the domain object; this method only closes the report and records metadata.

**Do not add** the trust reversal logic inside this entity — that is Application-layer orchestration (Phase 6).

### 1.3 — Modify `MatchingPreference.java`

**File:** `backend/src/main/java/com/walkmate/domain/walkintent/MatchingPreference.java`  
**Action:** Modify (rework `normalize()` and add a constant)

Add a new constant `MAX_WEIGHT_CAP = 0.70`.

**Rework `normalize()` using a two-pass Cap-and-Redistribute algorithm:**

*Pass 1 — Standard normalization:*  
Divide each weight by the total sum so that `W_time + W_interest + W_behavior = 1.0` (existing logic, kept as-is).

*Pass 2 — Cap enforcement:*  
After normalization, inspect each weight. If any weight exceeds `MAX_WEIGHT_CAP`:
1. Clamp that weight to `MAX_WEIGHT_CAP`.
2. Compute `remainder = 1.0 - MAX_WEIGHT_CAP` — the budget left for the other two.
3. Re-normalize the remaining two weights proportionally to fill the `remainder` budget.
4. If both remaining weights are zero (degenerate case), split the remainder evenly between them.

This two-pass approach guarantees `W_time + W_interest + W_behavior = 1.0` is preserved after capping.

**Why 0.70?** A cap of 0.70 means no single factor can contribute more than 70% of the final score. The other two factors retain at least 30% combined influence, preventing a user who has filed many misconduct reports from completely ignoring time-overlap or interest compatibility in their matches.

**Applies to:** both `trainWeightsFromReview()` (existing) and the new `trainWeightsFromReport()` (Phase 3), because both call `normalize()`.

### 1.4 — Modify `SessionReportRepository.java` Interface

**File:** `backend/src/main/java/com/walkmate/domain/report/SessionReportRepository.java`  
**Action:** Modify (add method signatures)

Add the following method signatures without implementation (implementations are in Phase 5's infrastructure step):

- `Optional<SessionReport> findById(String reportId)` — fetch a single report by its UUID.
- `List<SessionReport> findAll()` — return all reports ordered by `created_at DESC`. (Acceptable for a school project; no pagination needed.)
- `List<SessionReport> findByStatus(String status)` — filter by `OPEN`, `APPROVED`, or `REJECTED`.
- `void update(SessionReport report)` — persist status + resolution fields back to the database.

The existing `save(SessionReport)` and `existsBySessionAndReporter(...)` signatures remain unchanged.

### 1.5 — Modify `ReportErrorCode.java`

**File:** `backend/src/main/java/com/walkmate/domain/report/ReportErrorCode.java`  
**Action:** Modify (add new constants)

Add the following new error-code constants:

| Constant | Used when |
|---|---|
| `REPORT_NOT_FOUND` | Admin tries to act on a report ID that doesn't exist. |
| `REPORT_ALREADY_RESOLVED` | Admin tries to approve/reject a report that is already `APPROVED` or `REJECTED`. |
| `REPORT_INVALID_RESOLUTION` | The resolution value sent by the admin is not `APPROVED` or `REJECTED`. |

---

## Phase 2 — Channel 1: Trust Penalty on Submission

This phase wires the penalty calculation into the existing `submitReport()` transaction.

### 2.1 — Modify `ReportCommandService.java`

**File:** `backend/src/main/java/com/walkmate/application/report/ReportCommandService.java`  
**Action:** Modify (extend existing `@Transactional` method)

**New dependency to inject:** `UserRepository userRepository` and `GamificationCommandService gamificationService` (or a simpler `WalkSessionRepository` to inspect session's outcome — whichever is already injected in the context).

**Logic to add immediately after `reportRepository.save(report)`, still inside the same `@Transactional` boundary:**

1. **Compute theoretical delta:**  
   Call `TrustScorePolicy.deltaForReason(request.reason())` to get the intended penalty.

2. **No-Show Double-Penalty Guard:**  
   Check whether the session's walk outcome already applied a `NO_SHOW` gamification penalty to the reported user. The cleanest way to check this without coupling to `GamificationCommandService` internals is to inspect the session entity: if `session.getUserXStatus() == NO_SHOW` for the reported user's slot (`userIdA` or `userIdB`), then the −100 has already been applied.  
   - If the no-show penalty already applies **and** the theoretical delta is less severe than −100: set `actualDelta = 0` (do not stack).  
   - If the no-show penalty already applies **but** the report reason (`SAFETY_CONCERN`, −50) is genuinely independent of attendance: still set `actualDelta = 0` for simplicity. (A school project does not need partial stacking logic.)  
   - If no no-show penalty: `actualDelta = theoreticalDelta`.

3. **Apply penalty (only when `actualDelta != 0`):**  
   - Load the reported `User` from `UserRepository`.  
   - Call `TrustScorePolicy.apply(user.getTrustScore(), actualDelta)`.  
   - Call `user.applyTrustScore(newScore)`.  
   - Call `userRepository.save(user)`.

4. **Record the applied delta on the report:**  
   Call `report.setAppliedTrustDelta(actualDelta)` so the value is persisted (see 2.2).

### 2.2 — Modify `SessionReportJdbcRepository.java`

**File:** `backend/src/main/java/com/walkmate/infrastructure/repository/report/SessionReportJdbcRepository.java`  
**Action:** Modify (update `save()` SQL)

Update the `INSERT INTO session_report` statement to also write the new `applied_trust_delta` column. The other three new columns (`resolved_by`, `resolved_at`, `resolution_note`) are `NULL` at creation and do not need to appear in the INSERT.

Also implement the four new methods declared in the repository interface (Phase 1.4):
- `findById()` — SELECT by `report_id`.
- `findAll()` — SELECT all, ORDER BY `created_at DESC`.
- `findByStatus()` — SELECT WHERE `status = ?`.
- `update()` — UPDATE `status`, `resolved_by`, `resolved_at`, `resolution_note`, and `applied_trust_delta` (if ever needed) WHERE `report_id = ?`.

---

## Phase 3 — Channel 2: Reporter Weight Training on Submission

### 3.1 — Modify `AiTrainingService.java`

**File:** `backend/src/main/java/com/walkmate/application/walkintent/AiTrainingService.java`  
**Action:** Modify (add one new `@Async` method)

Add a new method `trainWeightsFromReport(UUID reporterId, String reason)` annotated with `@Async`.

**Logic:**
1. Load the reporter's `MatchingPreference` from `matchingPreferenceRepository`, or create a default if none exists.
2. Apply a base increment to `weightBehavior` of `+0.10` for any report submission (2× the per-tag review increment, reflecting the stronger signal).
3. Apply an additional `+0.05` to `weightBehavior` if `reason` is `SAFETY_CONCERN` or `PARTNER_MISCONDUCT` (i.e., the maximum total increment for a severe report is `+0.15`).
4. Call `pref.normalize()` — this now includes the Phase 4 cap logic, so the weight will not exceed `MAX_WEIGHT_CAP = 0.70`.
5. Call `pref.updateLastTrainedAt(Instant.now())`.
6. Call `matchingPreferenceRepository.save(pref)` (upsert).
7. Wrap the body in a try/catch; log failures but do not rethrow — async failures must not surface as user-visible errors.

### 3.2 — Modify `ReportCommandService.java` (second edit)

**File:** `backend/src/main/java/com/walkmate/application/report/ReportCommandService.java`  
**Action:** Modify (add async call after the transaction)

Inject `AiTrainingService aiTrainingService`.

After the `@Transactional` method returns (i.e., the transaction has committed), add the call:

```
aiTrainingService.trainWeightsFromReport(reporterId, reason)
```

**Important placement note:** In Spring, calling `@Async` methods on a bean injected from outside the current class triggers the async proxy correctly. Do not call this method directly on `this` — use the injected instance. This is the same pattern already used by `ReviewCommandService` calling `AiTrainingService.trainWeightsFromReview()`.

---

## Phase 4 — Weight Imbalance Mitigation

This phase has no new files. It is the implementation of the `normalize()` rework described in Phase 1.3.

### 4.1 — Confirm Cap Constant Value

Add `public static final double MAX_WEIGHT_CAP = 0.70` to `MatchingPreference.java`.

**Rationale for 0.70 vs 0.80:**  
At 0.80, a user filing two severe reports would reach the cap after the second report (2 × 0.15 = 0.30 increment from default 0.333 → 0.633, within 0.80). But after further reviews with BEHAVIOR tags, they could still approach 0.80.  
At 0.70, the system guarantees S_time and S_tags always contribute a combined minimum of 30%, keeping the matching algorithm balanced even for heavily opinionated users.  
**Decision: use 0.70.**

### 4.2 — Verify `normalize()` Is Called Consistently

Confirm that both `AiTrainingService.trainWeightsFromReview()` (existing) and the new `trainWeightsFromReport()` (Phase 3.1) both call `pref.normalize()` after adjustments. The cap logic in `normalize()` will automatically apply in both cases — no changes to `AiTrainingService` are needed beyond Phase 3.

---

## Phase 5 — Admin Query Layer

### 5.1 — Create `AdminReportQueryService.java`

**File:** `backend/src/main/java/com/walkmate/application/report/AdminReportQueryService.java`  
**Action:** Create new

This is a pure read service with no writes.

**Methods to implement:**

- `List<SessionReport> getAllReports()` — calls `reportRepository.findAll()`. Returns all reports sorted by `created_at DESC`.
- `List<SessionReport> getReportsByStatus(String status)` — validates the status string against the three allowed values (`OPEN`, `APPROVED`, `REJECTED`), throws `REPORT_INVALID_RESOLUTION` if invalid, then calls `reportRepository.findByStatus(status)`.
- `SessionReport getReportById(String reportId)` — calls `reportRepository.findById(reportId)`, throws `REPORT_NOT_FOUND` if absent.

No pagination, no filtering beyond status — adequate for a school project.

### 5.2 — Create `AdminReportResponse.java` (Response DTO)

**File:** `backend/src/main/java/com/walkmate/presentation/dto/response/report/AdminReportResponse.java`  
**Action:** Create new

A `record` (or plain class with final fields) that exposes the following fields for the admin UI:

| Field | Type | Source |
|---|---|---|
| `reportId` | `String` | `SessionReport.reportId` |
| `sessionId` | `String` | `SessionReport.sessionId` |
| `reporterId` | `String` | `SessionReport.reporterId` |
| `reportedUserId` | `String` | `SessionReport.reportedUserId` |
| `reason` | `String` | `SessionReport.reason` |
| `evidenceUrl` | `String` (nullable) | `SessionReport.evidenceUrl` |
| `status` | `String` | `SessionReport.status` |
| `appliedTrustDelta` | `int` | `SessionReport.appliedTrustDelta` |
| `createdAt` | `String` | `SessionReport.createdAt.toString()` |
| `resolvedBy` | `String` (nullable) | `SessionReport.resolvedBy` |
| `resolvedAt` | `String` (nullable) | `SessionReport.resolvedAt.toString()` |
| `resolutionNote` | `String` (nullable) | `SessionReport.resolutionNote` |

---

## Phase 6 — Admin Command Layer (Approve / Reject)

This is the most complex phase. It handles the admin's resolution decision and the conditional trust-score reversal.

### 6.1 — Create `ResolveReportRequest.java` (Request DTO)

**File:** `backend/src/main/java/com/walkmate/presentation/dto/request/report/ResolveReportRequest.java`  
**Action:** Create new

A `record` with two fields:

- `resolution` (`String`, `@NotBlank`) — must be `"APPROVED"` or `"REJECTED"`.
- `resolutionNote` (`String`, nullable) — optional admin comment.

`@Valid` is applied at the controller layer.

### 6.2 — Create `AdminReportCommandService.java`

**File:** `backend/src/main/java/com/walkmate/application/report/AdminReportCommandService.java`  
**Action:** Create new

**Dependencies to inject:** `SessionReportRepository reportRepository`, `UserRepository userRepository`.

**Single method:** `resolveReport(String reportId, String adminUserId, String resolution, String note)`  
Annotated `@Transactional`.

**Logic:**

1. **Load the report** via `reportRepository.findById(reportId)`. Throw `REPORT_NOT_FOUND` if absent.

2. **Validate the resolution string.** If it is neither `"APPROVED"` nor `"REJECTED"`, throw `REPORT_INVALID_RESOLUTION`.

3. **Guard re-resolution.** Call `report.isResolved()` (a helper that returns true if status ≠ `OPEN`). If already resolved, throw `REPORT_ALREADY_RESOLVED`.

4. **Branch on resolution:**

   **If `APPROVED`:**
   - Call `report.approve(adminUserId, note)` — transitions status to `APPROVED`, records `resolvedBy`, `resolvedAt`, `resolutionNote`.
   - The trust-score penalty that was applied at submission time **remains unchanged**. No user table update.

   **If `REJECTED`:**
   - Call `report.reject(adminUserId, note)` — transitions status to `REJECTED`, records resolution metadata.
   - **Trust-score reversal:** Read `report.getAppliedTrustDelta()`.  
     - If `appliedTrustDelta == 0` (no penalty was applied, e.g., no-show guard fired): skip reversal.  
     - If `appliedTrustDelta < 0` (a penalty was applied): load the reported user via `userRepository.findById(report.getReportedUserId())`. Apply the inverse: `TrustScorePolicy.apply(user.getTrustScore(), -appliedTrustDelta)`. Note that `-appliedTrustDelta` will be positive (e.g., delta was −30, reversal is +30). Call `user.applyTrustScore(newScore)` and `userRepository.save(user)`.

5. **Persist the report** via `reportRepository.update(report)`.

6. **Return** the updated `SessionReport` object.

**Key design decision — why store `applied_trust_delta` in the DB:**  
The no-show guard in Phase 2 may have reduced the effective penalty to `0` even when the reason is `SAFETY_CONCERN`. If we only stored the `reason` and recomputed the delta at rejection time, we would incorrectly credit back a penalty that was never applied. Storing the actual applied delta is the only safe reversal mechanism.

---

## Phase 7 — Presentation Layer: Admin REST Endpoints

### 7.1 — Create `AdminReportController.java`

**File:** `backend/src/main/java/com/walkmate/presentation/controller/report/AdminReportController.java`  
**Action:** Create new

**Base path:** `/api/v1/admin/reports`

**Endpoints:**

| Method | Path | Description | Response |
|---|---|---|---|
| `GET` | `/api/v1/admin/reports` | List all reports. Optional query param `?status=OPEN` to filter. | `ApiResponse<List<AdminReportResponse>>` |
| `GET` | `/api/v1/admin/reports/{reportId}` | Get a single report by ID. | `ApiResponse<AdminReportResponse>` |
| `PATCH` | `/api/v1/admin/reports/{reportId}/resolve` | Approve or reject a report. | `ApiResponse<AdminReportResponse>` |

**Controller rules (following existing architecture):**
- No `try-catch` blocks. All exceptions bubble to `GlobalExceptionHandler`.
- `@AuthenticationPrincipal UserPrincipal principal` is used to capture `adminUserId` for audit purposes on the resolve endpoint.
- `@Valid @RequestBody ResolveReportRequest request` on the PATCH endpoint.
- Map `SessionReport` domain objects to `AdminReportResponse` via a private `toAdminResponse()` helper method.

---

## Phase 8 — Security Configuration

### 8.1 — Protect Admin Routes

**File:** `backend/src/main/java/com/walkmate/infrastructure/config/SecurityConfig.java` (or equivalent Spring Security configuration class)  
**Action:** Modify (add one `requestMatchers` rule)

Add a rule that restricts all `/api/v1/admin/**` paths to requests authenticated with the `ADMIN` role. Place this rule **before** the general authenticated-user rule in the `SecurityFilterChain` so it takes precedence.

The existing `UserPrincipal` already carries the user's authorities. Ensure that admin accounts have `ROLE_ADMIN` in their authorities list. If the `user_account` table does not currently store a `role` column, this may require a separate small migration to add `role VARCHAR NOT NULL DEFAULT 'USER'` and a seed UPDATE to set one account to `'ADMIN'`. Document this dependency but implement it as a separate concern — it is outside the scope of the Report feature.

---

## Full File Inventory

### Files to Create (new)

| File | Phase |
|---|---|
| `V[next]__report_ai_matching_support.sql` | 0 |
| `backend/.../application/report/AdminReportQueryService.java` | 5 |
| `backend/.../application/report/AdminReportCommandService.java` | 6 |
| `backend/.../presentation/dto/request/report/ResolveReportRequest.java` | 6 |
| `backend/.../presentation/dto/response/report/AdminReportResponse.java` | 5 |
| `backend/.../presentation/controller/report/AdminReportController.java` | 7 |

### Files to Modify (existing)

| File | Phase | Change Summary |
|---|---|---|
| `backend/.../domain/review/TrustScorePolicy.java` | 1.1 | Add `deltaForReason(String reason)` static method |
| `backend/.../domain/report/SessionReport.java` | 1.2 | Add 4 new fields; add `approve()`, `reject()`, `isResolved()` domain methods |
| `backend/.../domain/walkintent/MatchingPreference.java` | 1.3 / 4 | Add `MAX_WEIGHT_CAP = 0.70`; rework `normalize()` with cap-and-redistribute |
| `backend/.../domain/report/SessionReportRepository.java` | 1.4 | Add `findById`, `findAll`, `findByStatus`, `update` signatures |
| `backend/.../domain/report/ReportErrorCode.java` | 1.5 | Add `REPORT_NOT_FOUND`, `REPORT_ALREADY_RESOLVED`, `REPORT_INVALID_RESOLUTION` |
| `backend/.../application/report/ReportCommandService.java` | 2.1 / 3.2 | Add trust-penalty block + no-show guard; inject + call `AiTrainingService` after transaction |
| `backend/.../application/walkintent/AiTrainingService.java` | 3.1 | Add `trainWeightsFromReport(UUID reporterId, String reason)` async method |
| `backend/.../infrastructure/repository/report/SessionReportJdbcRepository.java` | 2.2 / 5 | Update INSERT; implement `findById`, `findAll`, `findByStatus`, `update` |
| `backend/.../infrastructure/config/SecurityConfig.java` | 8.1 | Add `ADMIN` role guard for `/api/v1/admin/**` |

---

## Data-Flow Summary (Final State)

```
POST /api/v1/sessions/{sessionId}/report
  └─ ReportCommandService.submitReport()  [@Transactional]
       ├─ [existing] 6 validation guards
       ├─ [existing] SessionReport.create() → reportRepository.save()
       ├─ [Phase 2] No-show guard check
       ├─ [Phase 2] TrustScorePolicy.deltaForReason(reason) → apply → UPDATE user_account.trust_score
       ├─ [Phase 2] report.setAppliedTrustDelta(actualDelta)
       └─ [Phase 3] aiTrainingService.trainWeightsFromReport(reporterId, reason)  [async]
                 ├─ weightBehavior += 0.10 (+ 0.05 if severe)
                 ├─ normalize()  ← now includes Phase 4 MAX_WEIGHT_CAP = 0.70
                 └─ UPSERT matching_preference_model

PATCH /api/v1/admin/reports/{reportId}/resolve  [ADMIN only]
  └─ AdminReportCommandService.resolveReport()  [@Transactional]
       ├─ Load SessionReport (throws REPORT_NOT_FOUND)
       ├─ Validate resolution string
       ├─ Guard re-resolution (throws REPORT_ALREADY_RESOLVED)
       ├─ If APPROVED: report.approve()  →  reportRepository.update()
       │    └─ Trust penalty stands. No user_account change.
       └─ If REJECTED: report.reject()  →  reportRepository.update()
            └─ If appliedTrustDelta != 0:
                 └─ TrustScorePolicy.apply(current, -appliedTrustDelta)
                      └─ UPDATE user_account.trust_score  (reversal)
```

---

## Decision Log

| Decision | Rationale |
|---|---|
| Store `applied_trust_delta` in DB (not recompute at rejection time) | The no-show guard may have zeroed the actual delta even when the reason implies a penalty. Recomputing from `reason` alone would produce incorrect reversals. |
| Cap at `0.70`, not `0.80` | 0.70 guarantees the two non-dominant factors always contribute at least 30% combined. 0.80 leaves too much room for imbalance (single factor = 80% of score). |
| Two-pass Cap-and-Redistribute, not simple clamp | Simple clamping would break the invariant `W_time + W_interest + W_behavior = 1.0`. The redistribute step restores it. |
| Admin routes require `ROLE_ADMIN`, not a separate JWT | Consistent with the existing `UserPrincipal` authority model. No new auth mechanism needed. |
| `dispute_case` table not used | The schema exists for future complex workflows. Enhancing `session_report` directly keeps this feature self-contained and avoids adding an unnecessary join across tables. |
| Async weight training, synchronous trust penalty | Mirrors Review architecture. Trust penalty is a financial-grade write that must be atomic with the report save. Weight training can tolerate async failure because it is a personalization hint, not a hard constraint. |
| No weight training reversal on admin rejection | The reporter's `matching_preference_model` is a personal signal, not a penalty on another user. Reversing it on report rejection would silently alter the reporter's matching behavior in a way they did not consent to. |
