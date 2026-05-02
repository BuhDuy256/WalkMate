# Implementation Plan (Core): Report → AI Matching

**Scope:** Database Migrations · Domain Layer · Channel 1 (Trust Penalty) · Channel 2 (Weight Training + Imbalance Mitigation)  
**Constraint:** No message brokers. Synchronous transactions + `@Async` for weight training only. School-project scale.

---

## Invariant Alignment

Before any code, map every new rule back to the SSOT:

| Invariant | Impact on this plan |
|---|---|
| **X-4 (Trust System, 2 phases)** | This plan introduces a **third trust channel** — report-based penalty — independent of session-outcome gamification (channel 1) and star-rating reviews (channel 2). All three channels go through `TrustScorePolicy.apply()` to preserve the `[0, 1000]` bound. |
| **S-5 (Independent Completion)** | A session is globally `COMPLETED` even when one participant is `NO_SHOW`. The new Reporter Eligibility Guard (Phase 2.1, Step 5) must inspect the reporter's **personal status** (`user_a_status` / `user_b_status`), not the global `status`. |
| **S-4 (No-Show Policy)** | Although the scheduler no longer auto-applies `NO_SHOW`, it remains a valid historical personal status. The guard must still reject a reporter whose personal status is `NO_SHOW`. |
| **X-5 (Optimistic Locking)** | Report submission writes only to `session_report` and `user_account`. Neither table carries a `version` column — no optimistic-lock handling needed here. |

---

## Phase 0 — Database Migrations

Two separate Flyway files. Keep them separate so each can be rolled back independently.

---

### Migration A — `V[N]__report_ai_matching_support.sql`

#### Step 0.1 — Extend `report_status` Enum

Add two new values to the existing PostgreSQL `report_status` enum type. The existing `OPEN` default is preserved.

| New Value | Meaning |
|---|---|
| `APPROVED` | Admin confirmed the report is valid. Trust penalty applied at submission stands permanently. |
| `REJECTED` | Admin dismissed the report as false. Trust penalty is reversed in the same transaction. |

PostgreSQL does not allow removing enum values, so additions are always safe with no downtime risk.

#### Step 0.2 — Add Columns to `session_report`

Four new columns on the existing table. All are nullable except `applied_trust_delta` which has a safe default.

| Column | SQL Type | Constraint | Purpose |
|---|---|---|---|
| `applied_trust_delta` | `INTEGER` | `NOT NULL DEFAULT 0` | The exact trust delta **actually written** to `user_account.trust_score` at submission time. Stored here because the No-Show Double-Penalty Guard (Phase 2.1) may reduce the effective delta to `0` even when `reason` implies a large penalty. This is the only safe source of truth for the reversal amount at rejection time. |
| `resolved_by` | `UUID` | `NULL`, FK → `user_account(user_id)` | The admin's `user_id` who acted. NULL while report is `OPEN`. |
| `resolved_at` | `TIMESTAMP WITHOUT TIME ZONE` | `NULL` | Timestamp of admin action. NULL while `OPEN`. |
| `resolution_note` | `TEXT` | `NULL` | Optional free-text comment from the admin. |

No other tables are touched by this migration. The existing `dispute_case` table remains unused — it is reserved for future multi-party dispute workflows.

---

### Migration B — `V[N+1]__user_account_add_role.sql`

#### Step 0.3 — Add `role` Column to `user_account`

Add one column to the existing `user_account` table:

```
role  VARCHAR  NOT NULL  DEFAULT 'USER'
```

Valid string values: `'USER'` (all existing and new accounts default to this), `'ADMIN'`.

Using a plain `VARCHAR` instead of a PostgreSQL enum keeps the migration simple and avoids the overhead of adding a new type to the DB. The application layer validates the value.

#### Step 0.4 — Seed Admin Account (Manual, One-Time)

After running the migration, promote one known account to admin with a targeted `UPDATE`:

```
UPDATE user_account SET role = 'ADMIN' WHERE email = '<designated_admin_email>';
```

This is **not** automated by Flyway. It is a one-time manual seed by the developer during environment setup. No automated seed script is needed for a school project.

---

## Phase 1 — Domain Layer

All changes in this phase are pure Java with zero framework dependencies. This phase lays the contract that every other layer depends on — complete it first before touching Application or Infrastructure.

---

### 1.1 — `TrustScorePolicy.java` (Modify)

**File:** `backend/src/main/java/com/walkmate/domain/review/TrustScorePolicy.java`

Add one new static method: `deltaForReason(String reason)`.

The method is a pure lookup — no side effects, deterministic. It maps the four possible `reason` strings (matching the frontend's `AbortReason` enum values) to their corresponding negative integer penalties:

| `reason` | Delta | Rationale |
|---|---|---|
| `SAFETY_CONCERN` | `−50` | Highest severity — physical safety risk |
| `PARTNER_MISCONDUCT` | `−30` | Strong behavioral signal |
| `OTHER` | `−10` | Weak/unspecified signal |
| `EMERGENCY` | `0` | Ambiguous cause — reporter may have contributed |
| Any unrecognized string | `0` | Safe default — never fail on unknown input |

The existing `apply(int currentScore, int delta)` method (clamps result to `[0, 1000]`) is **not modified**. All channels (gamification, review, report) continue to call it for bounds enforcement.

---

### 1.2 — `SessionReport.java` (Modify)

**File:** `backend/src/main/java/com/walkmate/domain/report/SessionReport.java`

**Current state:** 7 fields (`reportId`, `sessionId`, `reporterId`, `reportedUserId`, `reason`, `evidenceUrl`, `createdAt`). No `status` field. No resolution metadata.

**Changes:**

**Add 5 new fields:**

| Field | Type | Initial value | Notes |
|---|---|---|---|
| `status` | `String` | `"OPEN"` set in `create()` | Carries `OPEN`, `APPROVED`, or `REJECTED` |
| `appliedTrustDelta` | `int` | `0` | Updated by `ReportCommandService` after computing the actual delta |
| `resolvedBy` | `String` | `null` | Admin `userId` |
| `resolvedAt` | `Instant` | `null` | |
| `resolutionNote` | `String` | `null` | |

**Update the `create()` factory method** to initialize `status = "OPEN"` and `appliedTrustDelta = 0` alongside the existing fields.

**Update the rehydration constructor** (used by the repository when loading from DB) to accept all 12 fields including the 5 new ones.

**Add one setter:** `setAppliedTrustDelta(int delta)` — called by `ReportCommandService` after the trust penalty is computed and applied, so the delta can be persisted to the DB.

**Add three domain methods (state machine):**

- **`isResolved()`** — returns `true` when `status` is `APPROVED` or `REJECTED`. Used as the re-resolution guard.

- **`approve(String adminUserId, String note)`:**
  1. Call `isResolved()` — if `true`, throw `DomainException(ReportErrorCode.REPORT_ALREADY_RESOLVED)`.
  2. Set `status = "APPROVED"`, `resolvedBy = adminUserId`, `resolvedAt = Instant.now()`, `resolutionNote = note`.
  3. The method does **not** touch `appliedTrustDelta` or any user data — that is Application-layer responsibility.

- **`reject(String adminUserId, String note)`:**
  1. Same re-resolution guard as `approve()`.
  2. Set `status = "REJECTED"`, populate the same resolution fields.
  3. Same boundary: no trust-score logic inside the domain object.

---

### 1.3 — `ReportErrorCode.java` (Modify)

**File:** `backend/src/main/java/com/walkmate/domain/report/ReportErrorCode.java`

**Current constants (keep unchanged):**
- `REPORT_SESSION_INVALID_STATUS`
- `REPORT_WINDOW_EXPIRED`
- `REPORT_ALREADY_SUBMITTED`
- `REPORT_SELF_NOT_ALLOWED`

**Add 4 new constants:**

| Constant | Message (human-readable) | Used in |
|---|---|---|
| `REPORT_REPORTER_NO_SHOW` | `"You cannot file a report when you did not attend the session."` | Phase 2.1, Step 5 — new trigger guard |
| `REPORT_NOT_FOUND` | `"Report not found."` | Admin query + command services |
| `REPORT_ALREADY_RESOLVED` | `"This report has already been resolved."` | Admin command service re-resolution guard |
| `REPORT_INVALID_RESOLUTION` | `"Resolution must be APPROVED or REJECTED."` | Admin command service input validation |

---

### 1.4 — `MatchingPreference.java` (Modify)

**File:** `backend/src/main/java/com/walkmate/domain/walkintent/MatchingPreference.java`

**Current `normalize()` behavior:** Standard single-pass — divides each weight by the total sum. Breaks on degenerate zero-sum case by resetting to equal thirds. No upper bound per weight.

**Changes:**

Add constant: `public static final double MAX_WEIGHT_CAP = 0.70`

**Rework `normalize()` into a two-pass algorithm:**

---

*Pass 1 — Standard Normalization (existing logic, unchanged):*

Sum all three weights. If total ≤ 0 (degenerate), reset all to `DEFAULT_WEIGHT` (1/3) and return early. Otherwise divide each weight by total. After Pass 1: `W_time + W_interest + W_behavior = 1.0`.

---

*Pass 2 — Cap Enforcement (new):*

Scan the three normalized weights. If **any** single weight exceeds `MAX_WEIGHT_CAP`:

1. Identify the capped weight. Set it to `MAX_WEIGHT_CAP`.
2. Compute `remainder = 1.0 - MAX_WEIGHT_CAP` (the budget available for the other two).
3. Compute `subSum = sum of the other two (uncapped) weights` using their *pre-clamp* normalized values.
4. If `subSum > 0`: scale each of the two remaining weights by `remainder / subSum` (proportional redistribution).
5. If `subSum == 0` (both are zero — degenerate): set each remaining weight to `remainder / 2.0` (equal split).

After Pass 2: `W_time + W_interest + W_behavior = 1.0` is still guaranteed.

---

**Why 0.70 (not 0.80):**

A cap of 0.70 means the two non-dominant factors always contribute a combined minimum of 30% to the final match score. At 0.80, a user filing just two severe reports would hit 0.63 (below cap) and then continued review-tag training could push them past 0.80. At 0.70 the ceiling is lower and safer for a school project where edge cases from accumulated training are harder to audit.

**Verification — cap only triggers under extreme repetition:**

| Scenario | W_behavior after normalize (before cap) | Cap fires? |
|---|---|---|
| Fresh user (default) | 0.333 | No |
| 1 severe report | ~0.375 | No |
| 3 severe reports + 3 BEHAVIOR review tags | ~0.58 | No |
| 6+ severe reports + many BEHAVIOR tags | Approaches 1.0 | Yes — clamped to 0.70 |

**No changes** to `adjustWeightInterest()`, `adjustWeightBehavior()`, `defaultFor()`, or `updateLastTrainedAt()`.

---

### 1.5 — `SessionReportRepository.java` (Modify)

**File:** `backend/src/main/java/com/walkmate/domain/report/SessionReportRepository.java`

Add 4 new method signatures (implementations are in Phase 2.2's infrastructure change):

- `Optional<SessionReport> findById(String reportId)` — single report lookup by primary key.
- `List<SessionReport> findAll()` — all reports, ordered `created_at DESC`. No pagination for school project scale.
- `List<SessionReport> findByStatus(String status)` — filter by `OPEN`, `APPROVED`, or `REJECTED`.
- `void update(SessionReport report)` — persists `status`, `resolved_by`, `resolved_at`, `resolution_note`, and `applied_trust_delta` back to the database row.

**Unchanged:** `save(SessionReport)` and `existsBySessionAndReporter(String, String)`.

---

## Phase 2 — Channel 1: Updated Trigger Validation + Trust Penalty

This phase makes two changes to `ReportCommandService`: adds the new Reporter Eligibility Guard, and wires the trust penalty + delta recording.

---

### 2.1 — `ReportCommandService.java` (Modify)

**File:** `backend/src/main/java/com/walkmate/application/report/ReportCommandService.java`

**New dependency to inject:** `UserRepository userRepository`

---

#### Updated Validation Sequence for `submitReport()`

The current code has 6 validation steps. One new step is inserted (Step 5). The full updated sequence:

| Step | Condition Checked | Exception Thrown |
|---|---|---|
| 1 | Session exists | `SESSION_NOT_FOUND` |
| 2 | `reporterId` is a participant (`userIdA` or `userIdB`) | `SESSION_NOT_PARTICIPANT` |
| 3 | `reporterId ≠ reportedUserId` | `REPORT_SELF_NOT_ALLOWED` |
| 4 | Session global `status == COMPLETED` | `REPORT_SESSION_INVALID_STATUS` |
| **5 (NEW)** | **Reporter's personal status is NOT `NO_SHOW`** | **`REPORT_REPORTER_NO_SHOW`** |
| 6 | Current time is within 72-hour window from `session.endedAt` | `REPORT_WINDOW_EXPIRED` |
| 7 | No prior report exists from this reporter for this session | `REPORT_ALREADY_SUBMITTED` |

---

#### Step 5 — Reporter Eligibility Guard (New)

**Placement:** Immediately after Step 4 (global status confirmed as `COMPLETED`), before the window check.

**Why after Step 4:** There is no point checking personal status until we have confirmed the session is globally COMPLETED (S-5 guarantees personal statuses are only meaningful in terminal sessions).

**Logic:**

Using the already-loaded `session` object (from Step 1, no additional DB query):

```
if reporterId == session.getUserIdA():
    reporterPersonalStatus = session.getUserAStatus()
else:
    reporterPersonalStatus = session.getUserBStatus()

if reporterPersonalStatus == NO_SHOW:
    throw DomainException(REPORT_REPORTER_NO_SHOW)
```

**The reported user's personal status is not checked.** The reported user may be `NO_SHOW`, `COMPLETED`, or any other terminal status. Only the reporter's eligibility matters for this guard.

**Alignment with S-5:** A session where userA=COMPLETED, userB=NO_SHOW is globally COMPLETED. UserA may report. UserB may not.

---

#### Trust Penalty Block (New)

Add immediately after `reportRepository.save(report)`, still inside the `@Transactional` boundary:

**Step A — Compute theoretical delta:**
Call `TrustScorePolicy.deltaForReason(reason)` → `theoreticalDelta` (a negative integer or 0).

**Step B — No-Show Double-Penalty Guard:**
The reported user may already have a −100 penalty from gamification if they are a `NO_SHOW`. Using the same approach as Step 5 (inspect session entity, no extra query):

```
if reportedUserId == session.getUserIdA():
    reportedUserPersonalStatus = session.getUserAStatus()
else:
    reportedUserPersonalStatus = session.getUserBStatus()

if reportedUserPersonalStatus == NO_SHOW:
    actualDelta = 0   // gamification already applied -100, do not stack
else:
    actualDelta = theoreticalDelta
```

**Step C — Apply penalty (only when `actualDelta ≠ 0`):**

1. Load reported user: `userRepository.findById(reportedUserId)`.
2. Compute new score: `TrustScorePolicy.apply(reportedUser.getTrustScore(), actualDelta)`.
3. `reportedUser.applyTrustScore(newScore)`.
4. `userRepository.save(reportedUser)`.

**Step D — Record the applied delta:**

```
report.setAppliedTrustDelta(actualDelta)
```

This call must happen **before** the `@Transactional` method returns so the delta is included in the same DB write as the rest of the report. If `actualDelta == 0`, the stored value remains `0`, which correctly signals to the admin's REJECT handler: "there is nothing to reverse."

---

#### Async Weight Training Call (New)

After the `@Transactional` method has returned, invoke the async training:

```
aiTrainingService.trainWeightsFromReport(UUID.fromString(reporterId), reason)
```

**Injection note:** `aiTrainingService` must be an injected field (not called on `this`). Spring's `@Async` proxy only activates on calls going through the Spring bean proxy, not on internal `this.*` calls.

---

### 2.2 — `SessionReportJdbcRepository.java` (Modify)

**File:** `backend/src/main/java/com/walkmate/infrastructure/repository/report/SessionReportJdbcRepository.java`

**Update `save()` SQL:**

Add `status` and `applied_trust_delta` to the column list and parameter list in the existing `INSERT INTO session_report (...)` statement. The four resolution columns (`resolved_by`, `resolved_at`, `resolution_note`) are `NULL` at creation time — they do not appear in the INSERT and their absence will satisfy the database defaults.

**Implement the 4 new interface methods from Phase 1.5:**

| Method | SQL | Notes |
|---|---|---|
| `findById(String reportId)` | `SELECT * FROM session_report WHERE report_id = ?` | Return `Optional.empty()` if no row found |
| `findAll()` | `SELECT * FROM session_report ORDER BY created_at DESC` | Used by admin list endpoint |
| `findByStatus(String status)` | `SELECT * FROM session_report WHERE status = ?::report_status ORDER BY created_at DESC` | Cast to enum type |
| `update(SessionReport report)` | `UPDATE session_report SET status=?, resolved_by=?, resolved_at=?, resolution_note=?, applied_trust_delta=? WHERE report_id=?` | Used by admin resolve command |

**Update the row-mapper** used by `findById`, `findAll`, and `findByStatus` to read the 5 new columns: `status`, `applied_trust_delta`, `resolved_by`, `resolved_at`, `resolution_note`. All four resolution columns must handle `NULL` gracefully.

---

## Phase 3 — Channel 2: Reporter Weight Training

### 3.1 — `AiTrainingService.java` (Modify)

**File:** `backend/src/main/java/com/walkmate/application/walkintent/AiTrainingService.java`

Add one new method: `trainWeightsFromReport(UUID reporterId, String reason)` annotated with `@Async`.

**Logic:**

1. Load reporter's `MatchingPreference` via `matchingPreferenceRepository.findByUserId(reporterId)`. If absent, create a default via `MatchingPreference.defaultFor(reporterId)`.

2. Apply base weight increment — any report is a behavior signal:
   `pref.adjustWeightBehavior(+0.10)`

3. Apply severity bonus — for high-severity reasons:
   If `reason` equals `"SAFETY_CONCERN"` or `"PARTNER_MISCONDUCT"`:
   `pref.adjustWeightBehavior(+0.05)` (additional)

   Maximum total increment per report: `+0.15`.

4. Call `pref.normalize()` — the two-pass cap algorithm (Phase 1.4) ensures `weightBehavior` cannot exceed `MAX_WEIGHT_CAP = 0.70` regardless of how many reports this user files.

5. `pref.updateLastTrainedAt(Instant.now())`

6. `matchingPreferenceRepository.save(pref)` (upsert by `user_id`)

7. Wrap the **entire method body** in `try { ... } catch (Exception e) { log.error(...) }`. Async failures must never surface as HTTP errors to the reporter.

**Why `weightBehavior` only (not `weightTimeOverlap` or `weightInterest`):**
A user filing a report is signaling that their partner's behavioral trustworthiness matters to them. This should make the `W_behavior × S_trust` component weigh more in their future match scoring. Time overlap and interest similarity are irrelevant to what the report communicates.

---

## Phase 4 — Weight Imbalance Mitigation

**No new files.** This phase is the activation of the `normalize()` rework from Phase 1.4.

### 4.1 — Consistency Verification

Confirm that both training paths call `pref.normalize()` after adjusting weights:

| Training path | Method | Calls `normalize()`? |
|---|---|---|
| Review tags | `AiTrainingService.trainWeightsFromReview()` | Yes (existing) |
| Report submission | `AiTrainingService.trainWeightsFromReport()` | Yes (Phase 3.1) |

Because `normalize()` is the single point where the cap is enforced, no changes to `AiTrainingService` are needed beyond Phase 3.1. The cap is automatically inherited by both paths.

---

## Complete File Inventory

### New Files

| File | Phase |
|---|---|
| `backend/src/main/resources/db/migration/V[N]__report_ai_matching_support.sql` | 0 |
| `backend/src/main/resources/db/migration/V[N+1]__user_account_add_role.sql` | 0 |

### Modified Files

| File | Phase | Summary |
|---|---|---|
| `backend/.../domain/review/TrustScorePolicy.java` | 1.1 | Add `deltaForReason(String)` |
| `backend/.../domain/report/SessionReport.java` | 1.2 | Add 5 fields, update constructors, add `isResolved()` / `approve()` / `reject()` / `setAppliedTrustDelta()` |
| `backend/.../domain/report/ReportErrorCode.java` | 1.3 | Add 4 new error codes |
| `backend/.../domain/walkintent/MatchingPreference.java` | 1.4 | Add `MAX_WEIGHT_CAP = 0.70`, rework `normalize()` with two-pass cap |
| `backend/.../domain/report/SessionReportRepository.java` | 1.5 | Add 4 new method signatures |
| `backend/.../application/report/ReportCommandService.java` | 2.1 | New Step 5 (reporter NO_SHOW guard), trust penalty block, async call |
| `backend/.../application/walkintent/AiTrainingService.java` | 3.1 | Add `trainWeightsFromReport()` |
| `backend/.../infrastructure/repository/report/SessionReportJdbcRepository.java` | 2.2 | Update INSERT SQL, implement 4 new query/update methods, update row-mapper |

---

## End-State Data Flow (Core)

```
POST /api/v1/sessions/{sessionId}/report
  └─ ReportCommandService.submitReport()  [@Transactional]
       ├─ Step 1: Load WalkSession
       ├─ Step 2: Assert reporter is participant
       ├─ Step 3: Assert reporter ≠ reportedUser
       ├─ Step 4: Assert session.status == COMPLETED
       ├─ Step 5 (NEW): Assert reporter's personal status != NO_SHOW
       ├─ Step 6: Assert within 72-hour window
       ├─ Step 7: Assert no duplicate report
       ├─ SessionReport.create() → reportRepository.save()
       ├─ (NEW) No-show guard → compute actualDelta
       ├─ (NEW) If actualDelta != 0 → UPDATE user_account.trust_score
       ├─ (NEW) report.setAppliedTrustDelta(actualDelta)
       └─ (NEW) aiTrainingService.trainWeightsFromReport()  [@Async]
                    ├─ weightBehavior += 0.10 (+ 0.05 if severe reason)
                    ├─ pref.normalize()  [two-pass, MAX_CAP = 0.70]
                    └─ UPSERT matching_preference_model
```
