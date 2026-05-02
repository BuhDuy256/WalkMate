# Implementation Plan (Admin API): Dispute Resolution + Role-Based Security

**Scope:** Admin Query Layer · Admin Command Layer (Trust Revert) · Controller Layer · Full Role-Based Security Chain  
**Depends on:** `implementation_plan_core.md` — all phases there must be completed first (domain layer, DB migrations for both `session_report` columns and the `user_account.role` column).

---

## Invariant Alignment

| Invariant | Impact on this plan |
|---|---|
| **X-4 (Trust System)** | Admin's REJECT action introduces a **trust reversal path** outside the existing two X-4 channels. The reversal uses the stored `applied_trust_delta` from `session_report` and routes through `TrustScorePolicy.apply()` to honor the `[0, 1000]` bound. It does not trigger gamification events or badge recalculations. |
| **X-5 (Optimistic Locking)** | The admin resolve action writes to `session_report` (a status update) and conditionally to `user_account` (trust score). Neither table has a `version` column, so no optimistic-lock conflict handling is required. |
| **S-1 / S-5 (Session Integrity)** | The admin flow is entirely post-session. The `WalkSession` is already in a terminal state (`COMPLETED`). The admin's decision does not alter any session state, preventing any violation of the session state machine. |

---

## Phase A — Admin Query Layer

### A.1 — Create `AdminReportQueryService.java`

**File:** `backend/src/main/java/com/walkmate/application/report/AdminReportQueryService.java`  
**Action:** Create new

A read-only service. No `@Transactional` needed (reads are non-mutating).

**Injected dependency:** `SessionReportRepository reportRepository`

**Methods:**

**`getAllReports()`**
- Calls `reportRepository.findAll()`.
- Returns all `SessionReport` objects ordered by `created_at DESC`.
- No filtering. Used by the admin Reports List screen's "All" tab.

**`getReportsByStatus(String status)`**
- Validate `status` is one of `"OPEN"`, `"APPROVED"`, `"REJECTED"`.
- If invalid, throw `DomainException(ReportErrorCode.REPORT_INVALID_RESOLUTION)` — reusing the same error code avoids adding an unnecessary new constant.
- Calls `reportRepository.findByStatus(status)`.

**`getReportById(String reportId)`**
- Calls `reportRepository.findById(reportId)`.
- If absent: throw `DomainException(ReportErrorCode.REPORT_NOT_FOUND)`.
- Returns the `SessionReport` domain object.

---

### A.2 — Create `AdminReportResponse.java` (Response DTO)

**File:** `backend/src/main/java/com/walkmate/presentation/dto/response/report/AdminReportResponse.java`  
**Action:** Create new

Implement as a Java `record`. This is the JSON shape returned to the admin UI for both list and detail views.

**Fields:**

| Field | Java type | Source |
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
| `resolvedAt` | `String` (nullable) | `SessionReport.resolvedAt.toString()` — null if unresolved |
| `resolutionNote` | `String` (nullable) | `SessionReport.resolutionNote` |

**No mapper class needed.** The controller uses a private `toAdminResponse(SessionReport)` helper method directly (see Phase C).

---

## Phase B — Admin Command Layer

### B.1 — Create `ResolveReportRequest.java` (Request DTO)

**File:** `backend/src/main/java/com/walkmate/presentation/dto/request/report/ResolveReportRequest.java`  
**Action:** Create new

Implement as a Java `record` with Jakarta validation annotations.

**Fields:**

| Field | Type | Validation | Notes |
|---|---|---|---|
| `resolution` | `String` | `@NotBlank` | Must be `"APPROVED"` or `"REJECTED"`. The enum check happens in the service, not here. |
| `resolutionNote` | `String` | None | Optional. May be null or blank. |

---

### B.2 — Create `AdminReportCommandService.java`

**File:** `backend/src/main/java/com/walkmate/application/report/AdminReportCommandService.java`  
**Action:** Create new

**Injected dependencies:** `SessionReportRepository reportRepository`, `UserRepository userRepository`

**Single method:** `resolveReport(String reportId, String adminUserId, String resolution, String note)` annotated `@Transactional`.

**Full Logic:**

**Step 1 — Load the report:**
`reportRepository.findById(reportId)` → if absent, throw `DomainException(REPORT_NOT_FOUND)`.

**Step 2 — Validate resolution string:**
If `resolution` is not `"APPROVED"` and not `"REJECTED"`, throw `DomainException(REPORT_INVALID_RESOLUTION)`.

**Step 3 — Guard re-resolution:**
Call `report.isResolved()`. If `true`, throw `DomainException(REPORT_ALREADY_RESOLVED)`.
This prevents an admin from acting twice on the same report, including a race condition if two admins open the same report simultaneously.

**Step 4 — Branch on resolution:**

---

**If `"APPROVED"`:**

1. Call `report.approve(adminUserId, note)`.
   - This transitions `status → APPROVED` and records resolution metadata on the domain object.
2. Call `reportRepository.update(report)` to persist the status change.
3. **No change to `user_account`.** The trust-score penalty applied at submission time stands permanently. No user lookup, no score update.
4. Return the updated `SessionReport`.

---

**If `"REJECTED"`:**

1. Call `report.reject(adminUserId, note)`.
   - This transitions `status → REJECTED` and records resolution metadata.
2. Call `reportRepository.update(report)` to persist the status change.
3. **Conditional trust-score reversal:**

   Read `report.getAppliedTrustDelta()`:

   - **If `appliedTrustDelta == 0`:** No penalty was applied at submission time (either the reason was `EMERGENCY`, or the No-Show Double-Penalty Guard zeroed the delta). Skip the reversal. No `user_account` update.

   - **If `appliedTrustDelta < 0`** (a negative penalty was applied):
     1. Load the reported user: `userRepository.findById(report.getReportedUserId())`. Throw a generic system error if not found (should never happen in normal operation).
     2. Compute the reversal delta: `-report.getAppliedTrustDelta()` — this is positive (e.g., stored delta was −30, reversal is +30).
     3. Compute new score: `TrustScorePolicy.apply(reportedUser.getTrustScore(), reversalDelta)`. The `apply()` method enforces `[0, 1000]` — in practice, reversal cannot exceed the original pre-deduction score but this guard is always applied as a safety net.
     4. `reportedUser.applyTrustScore(newScore)`.
     5. `userRepository.save(reportedUser)`.

4. Return the updated `SessionReport`.

---

**Why `applied_trust_delta` is the single source of truth for reversal:**

The no-show guard in `ReportCommandService` (core plan, Phase 2.1) may have zeroed `actualDelta` even when `reason = "SAFETY_CONCERN"`. If we recomputed the delta from `reason` at rejection time, we would credit back `−50` that was never subtracted. Storing the exact value in `session_report.applied_trust_delta` is the only way to guarantee a precise reversal.

---

**Weight Training is NOT reversed on rejection:**

The reporter's `matching_preference_model` is a personal behavioral signal — it reflects the reporter's own preferences, not a penalty against the reported user. Reversing it on rejection would silently alter the reporter's future matching behavior in a way they did not consent to and could not anticipate. The weight adjustment is left in place.

---

## Phase C — Controller Layer

### C.1 — Create `AdminReportController.java`

**File:** `backend/src/main/java/com/walkmate/presentation/controller/report/AdminReportController.java`  
**Action:** Create new

**Injected dependencies:** `AdminReportQueryService adminReportQueryService`, `AdminReportCommandService adminReportCommandService`

**Class-level annotations:** `@RestController`, `@RequiredArgsConstructor`, `@Tag(name = "Admin - Reports")`

**Base path:** `/api/v1/admin/reports`

---

#### Endpoint 1 — List Reports

**Method/Path:** `GET /api/v1/admin/reports`

**Query param:** `status` (optional, String) — if present, delegates to `getReportsByStatus(status)`; if absent, delegates to `getAllReports()`.

**Response:** `ResponseEntity<ApiResponse<List<AdminReportResponse>>>` HTTP 200.

**Controller logic:**
- If `status` query param is provided and non-blank: call `adminReportQueryService.getReportsByStatus(status)`.
- Otherwise: call `adminReportQueryService.getAllReports()`.
- Map the list of `SessionReport` objects to `List<AdminReportResponse>` via `toAdminResponse()`.
- Wrap in `ApiResponse.success(...)`.

---

#### Endpoint 2 — Get Single Report

**Method/Path:** `GET /api/v1/admin/reports/{reportId}`

**Response:** `ResponseEntity<ApiResponse<AdminReportResponse>>` HTTP 200.

**Controller logic:**
- Call `adminReportQueryService.getReportById(reportId)`.
- Map to `AdminReportResponse` via `toAdminResponse()`.
- `REPORT_NOT_FOUND` bubbles up to `GlobalExceptionHandler` → 400 response.

---

#### Endpoint 3 — Resolve Report (Approve / Reject)

**Method/Path:** `PATCH /api/v1/admin/reports/{reportId}/resolve`

**Request body:** `@Valid @RequestBody ResolveReportRequest request`

**Auth:** `@AuthenticationPrincipal UserPrincipal principal` — used to extract `adminUserId` for the audit trail (`resolvedBy` field).

**Response:** `ResponseEntity<ApiResponse<AdminReportResponse>>` HTTP 200.

**Controller logic:**
- Call `adminReportCommandService.resolveReport(reportId, principal.userId(), request.resolution(), request.resolutionNote())`.
- Map result to `AdminReportResponse` via `toAdminResponse()`.
- Exceptions (`REPORT_NOT_FOUND`, `REPORT_ALREADY_RESOLVED`, `REPORT_INVALID_RESOLUTION`) bubble to `GlobalExceptionHandler`.

---

#### Private Helper — `toAdminResponse(SessionReport report)`

A private method on the controller. Maps all 12 fields from the domain object to the response record. Handles nullable `resolvedAt` with a null-safe `.toString()` call.

---

**Controller rules (consistent with existing architecture):**
- No `try-catch` blocks anywhere in the controller.
- No business logic — only delegation to service layer and DTO mapping.
- All exceptions are handled by the existing `GlobalExceptionHandler`.

---

## Phase D — Role-Based Security Chain

This is the most interconnected phase. It spans four files and must be implemented in the order listed below, as each step depends on the previous one.

---

### D.1 — `JwtTokenProvider.java` (Modify)

**File:** `backend/src/main/java/com/walkmate/infrastructure/security/jwt/JwtTokenProvider.java`  
**Action:** Modify (add `role` claim to issued tokens)

**Current state:** When issuing a JWT, the provider encodes `userId` and `email` as claims.

**Change:** Add the `user_account.role` string as a new claim named `"role"` in the JWT payload.

**Where to get the role value:** The `JwtTokenProvider.generateToken(...)` method receives a user object or user details. The `User` domain entity (or the auth command that calls the provider) must pass the role value. This requires:

1. Ensure `User.java` (domain entity) exposes a `getRole()` method backed by the `role` field from `user_account`.
2. Pass the role value to `JwtTokenProvider.generateToken(...)` as an additional parameter or as part of the user object that is already passed.

**Claim name:** Use `"role"` (lowercase, no prefix). The prefix `"ROLE_"` is a Spring Security convention added only when constructing `GrantedAuthority` objects — do not store it in the JWT itself.

**No structural change to the token's signature or expiry logic.**

---

### D.2 — `UserPrincipal.java` (Modify)

**File:** `backend/src/main/java/com/walkmate/application/user/UserPrincipal.java`  
**Action:** Modify (add `role` field)

**Current state:** A `record` with two components: `userId` (String) and `email` (String).

**Change:** Add a third component: `role` (String).

Updated record signature:
```
public record UserPrincipal(String userId, String email, String role)
```

The `role` value will be `"USER"` or `"ADMIN"` (the raw string from the JWT claim, without the `ROLE_` prefix).

This field is used in the admin controller's audit trail indirectly — the controller reads `principal.userId()` for the `resolvedBy` field. The `role` component is also available if any future controller needs to branch on it, but **the actual security enforcement is handled by Spring Security at the filter layer, not inside the controller**.

---

### D.3 — `UserPrincipalConverter.java` (Modify)

**File:** `backend/src/main/java/com/walkmate/infrastructure/security/jwt/UserPrincipalConverter.java`  
**Action:** Modify (extract role from JWT + build GrantedAuthority list)

**Current state:** Implements `Converter<Jwt, AbstractAuthenticationToken>`. Extracts `userId` and `email` from JWT claims, creates a `UserPrincipal`, and returns a `JwtAuthenticationToken` (likely with empty authorities).

**Changes:**

1. **Extract the `role` claim** from the JWT: `String role = jwt.getClaimAsString("role")`. If the claim is absent (legacy tokens without the role claim), default to `"USER"`.

2. **Build authorities:** Create `List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role))`. The `"ROLE_"` prefix is required by Spring Security's `hasRole("ADMIN")` matcher — it internally looks for `"ROLE_ADMIN"`.

3. **Update `UserPrincipal` construction** to pass the three-component constructor: `new UserPrincipal(userId, email, role)`.

4. **Return** a `JwtAuthenticationToken(jwt, authorities, userPrincipal)` — the authorities list is now populated instead of empty.

**Why this is the right place for authority construction:** `UserPrincipalConverter` is the boundary between the raw JWT and the Spring Security context. Placing authority logic here means the rest of the application (services, controllers) never needs to parse JWT claims directly.

---

### D.4 — `SecurityConfig.java` (Modify)

**File:** `backend/src/main/java/com/walkmate/infrastructure/config/SecurityConfig.java`  
**Action:** Modify (add one `requestMatchers` rule)

**Current state:** The `authorizeHttpRequests` chain has rules for public endpoints, authenticated endpoints, and a catch-all `.anyRequest().authenticated()`. There is no admin-specific rule.

**Change:** Add one rule **before** the catch-all `.anyRequest().authenticated()` line:

```
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
```

**Why placement order matters:** Spring Security evaluates `requestMatchers` rules in the order they appear. The admin rule must appear before `.anyRequest().authenticated()`, otherwise all requests matching `/api/v1/admin/**` would pass the general authenticated check first and never hit the role restriction.

**Effect:** Any request to `/api/v1/admin/**` that:
- Carries no JWT → returns `401 Unauthorized`
- Carries a valid JWT with `ROLE_USER` → returns `403 Forbidden`
- Carries a valid JWT with `ROLE_ADMIN` → passes to the controller

`GlobalExceptionHandler` is already configured to catch Spring Security exceptions and return `ApiResponse`-formatted errors, so no additional exception handling is needed.

---

### D.5 — `User.java` (Modify, if needed)

**File:** `backend/src/main/java/com/walkmate/domain/user/User.java`  
**Action:** Verify or modify

Check whether the `User` domain entity already loads and exposes `role` from the `user_account` table. If not:

1. Add field: `private String role`
2. Add getter: `getRole()`
3. Update the rehydration constructor (used by `UserJdbcRepository`) to accept and set `role`
4. Update `UserJdbcRepository`'s row-mapper to read `role` from the `user_account` result set

The `role` value is only needed for JWT generation at login time (Phase D.1). It does not need to appear in any other domain logic.

---

## Complete File Inventory (Admin API)

### New Files

| File | Phase |
|---|---|
| `backend/.../application/report/AdminReportQueryService.java` | A.1 |
| `backend/.../presentation/dto/response/report/AdminReportResponse.java` | A.2 |
| `backend/.../presentation/dto/request/report/ResolveReportRequest.java` | B.1 |
| `backend/.../application/report/AdminReportCommandService.java` | B.2 |
| `backend/.../presentation/controller/report/AdminReportController.java` | C.1 |

### Modified Files

| File | Phase | Summary |
|---|---|---|
| `backend/.../infrastructure/security/jwt/JwtTokenProvider.java` | D.1 | Add `"role"` claim to issued JWT |
| `backend/.../application/user/UserPrincipal.java` | D.2 | Add `role` component to record |
| `backend/.../infrastructure/security/jwt/UserPrincipalConverter.java` | D.3 | Extract role claim, build `GrantedAuthority`, pass to token |
| `backend/.../infrastructure/config/SecurityConfig.java` | D.4 | Add `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")` |
| `backend/.../domain/user/User.java` | D.5 | Add `role` field + getter (if not already present) |
| `backend/.../infrastructure/repository/user/UserJdbcRepository.java` | D.5 | Update row-mapper to read `role` from `user_account` |

---

## End-State Data Flow (Admin API)

```
── JWT Issuance (at login) ──────────────────────────────────────────
AuthCommandService.login()
  └─ JwtTokenProvider.generateToken(userId, email, role)
       └─ JWT payload: { "sub": userId, "email": email, "role": "ADMIN" }

── Request Authentication (every request) ────────────────────────────
Incoming HTTP request with Bearer token
  └─ JwtDecoder verifies signature
       └─ UserPrincipalConverter.convert(jwt)
            ├─ Extract: userId, email, role = jwt.claim("role")
            ├─ Build:   List.of(new SimpleGrantedAuthority("ROLE_" + role))
            └─ Return:  JwtAuthenticationToken(jwt, authorities, UserPrincipal(userId, email, role))

── Admin Route Guard ─────────────────────────────────────────────────
SecurityConfig: .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
  ├─ ROLE_USER token  →  403 Forbidden
  └─ ROLE_ADMIN token →  passes to controller

── Admin Query Flow ─────────────────────────────────────────────────
GET /api/v1/admin/reports[?status=OPEN]
  └─ AdminReportController
       └─ AdminReportQueryService.getAllReports() | getReportsByStatus()
            └─ SessionReportJdbcRepository.findAll() | findByStatus()
                 └─ ApiResponse<List<AdminReportResponse>>

── Admin Resolve Flow ────────────────────────────────────────────────
PATCH /api/v1/admin/reports/{reportId}/resolve
  └─ AdminReportController (extracts adminUserId from @AuthenticationPrincipal)
       └─ AdminReportCommandService.resolveReport()  [@Transactional]
            ├─ Load SessionReport (REPORT_NOT_FOUND guard)
            ├─ Validate resolution string (REPORT_INVALID_RESOLUTION guard)
            ├─ Guard re-resolution (REPORT_ALREADY_RESOLVED guard)
            │
            ├─ If APPROVED:
            │    ├─ report.approve(adminUserId, note)
            │    └─ reportRepository.update(report)
            │         └─ Trust penalty stands. No user_account change.
            │
            └─ If REJECTED:
                 ├─ report.reject(adminUserId, note)
                 ├─ reportRepository.update(report)
                 └─ If applied_trust_delta != 0:
                      ├─ Load reported user
                      ├─ TrustScorePolicy.apply(current, -appliedTrustDelta)
                      └─ UPDATE user_account.trust_score  (reversal, bounds [0, 1000])
```

---

## Implementation Order (Recommended)

Because Phase D (security chain) involves four connected files that must be consistent with each other, implement them in strict order: D.5 → D.1 → D.2 → D.3 → D.4.

Testing milestone: After Phase D is complete, verify that:
1. A `USER` account cannot access any `GET /api/v1/admin/reports` endpoint (expect 403).
2. An `ADMIN` account can access `GET /api/v1/admin/reports` (expect 200).
3. The `@AuthenticationPrincipal UserPrincipal` in `AdminReportController` correctly returns the admin's `userId` for the `resolvedBy` audit field.
