# WalkMate — Backend API Specification

**Architecture:** DDD-lite + Layered (Rich Domain Model)
**Standard:** `Backend_VI.md`
**Base URL:** `http://<host>:8080/api/v1`
**Auth:** `Authorization: Bearer <accessToken>` (all endpoints except Auth)

---

## Part 0 — Pre-flight: Mismatch Report

> Analysis of conflicts between Invariant/Lifecycle docs, DB Schema, and Frontend domain models.
> **These must be resolved before implementing the affected endpoints.**

---

### C-1 · WalkIntent — Status Enum Terminology Mismatch

| Source | Values |
|---|---|
| **Lifecycle doc** | `DRAFT`, `OPEN`, `CONSUMED`, `CANCELLED`, `EXPIRED` |
| **DB** (`intent_status`) | Declared as USER-DEFINED — no inline enum shown; assumed to follow lifecycle doc |
| **Frontend `WalkIntent.java`** | `"OPEN"`, `"WAITLIST"`, `"MATCHED"`, `"EXPIRED"` |

**Conflicts:**
- `"WAITLIST"` is used in mock data (intent-002) but **does not exist** in the lifecycle spec.
- `"MATCHED"` is used in the frontend but the canonical term is **`CONSUMED`** (I-3, X-1).
- `"DRAFT"` and `"CANCELLED"` exist in the lifecycle but are absent from the frontend model.

**Resolution required:** The backend DB enum and API response must use lifecycle-canonical names (`OPEN`, `CONSUMED`, `CANCELLED`, `EXPIRED`). The frontend mapping layer must translate `CONSUMED → "MATCHED"` if the UI displays that label — the API contract itself uses `CONSUMED`.

---

### C-2 · WalkSession — Undocumented `ABORTED` State in DB

| Source | States |
|---|---|
| **Lifecycle doc** | `PENDING`, `ACTIVE`, `COMPLETED`, `NO_SHOW`, `CANCELLED` |
| **DB** (`session_status`) | All lifecycle states **plus `ABORTED`** |
| **DB `abort_reason`** | `INJURY`, `SAFETY`, `ENVIRONMENT`, `OTHER` |

**Conflict:** `ABORTED` is a 6th terminal state in the DB that is **not documented** in the lifecycle (S-7, S-8, S-9 do not mention it). The `abort_reason` column confirms it is intentional.

**Resolution required:** Add `ABORTED` to the lifecycle spec as a terminal state reachable from `ACTIVE` (user-initiated emergency abort). Add the `ABORTED` column value and `abort_reason` to all session API responses. Add `POST /api/v1/sessions/{sessionId}/abort`.

---

### C-3 · WalkSession — `PENDING_MEET` (Frontend) vs `PENDING` (Backend)

| Source | Status |
|---|---|
| **Lifecycle / DB** | `PENDING` |
| **Frontend `WalkSession.java`** | `PENDING_MEET` |

**Conflict:** The frontend's `Status` enum uses `PENDING_MEET` while the canonical state is `PENDING`. The frontend is also **missing `NO_SHOW`**.

**Resolution required:** The API response sends `"PENDING"`. The frontend mapper must translate `PENDING → PENDING_MEET`. `NO_SHOW` must be added to the frontend domain model.

---

### C-4 · WalkIntent — Float Hour vs Full Timestamp

| Source | Format |
|---|---|
| **DB** (`walk_intent`) | `time_window_start TIMESTAMP`, `time_window_end TIMESTAMP` |
| **Frontend** (`WalkIntentRepository`) | `timeStart float`, `timeEnd float` (e.g. `17.0f` = 17:00) |

**Conflict:** The frontend sends hour-of-day floats, the DB expects full timestamps.

**Resolution required:** `CreateWalkIntentRequest` must include an explicit `date` field (`LocalDate`) or the backend infers the date from the current server date. The recommended approach is to include a `date` field to be deterministic. The API stores and returns full ISO-8601 timestamps; the frontend UI layer converts floats to ISO strings before the request.

---

### C-5 · WalkSession — Missing Activation Endpoint

| Source | Detail |
|---|---|
| **DB** | `user1_activated_at`, `user2_activated_at` columns |
| **Lifecycle S-3, S-4** | `PENDING → ACTIVE` requires mutual activation within a grace window |
| **Frontend** | No `activateSession()` method in `WalkSessionRepository` |

**Conflict:** The DB and invariants clearly require a per-user activation call, but no frontend repository method or API service interface exists for it.

**Resolution required:** Add `POST /api/v1/sessions/{sessionId}/activate` to both the API spec and the frontend `WalkSessionRepository` interface.

---

### C-6 · WalkIntent DRAFT State — No Frontend Support

**Lifecycle** defines `DRAFT → OPEN` as the first transition (user submits intent).
**Frontend** calls `createIntent()` which produces an `OPEN` intent directly — there is no DRAFT step.

**Resolution:** The backend implementation of `POST /api/v1/intents` creates the intent directly in `OPEN` state, consistent with the current frontend flow. The `DRAFT` state remains reserved for a future "save draft" feature.

---

### C-7 · `refresh_token` Table — No Frontend Refresh Flow

The DB has a `refresh_token` table but the frontend only persists and uses an `accessToken` (no refresh call). The backend should implement token refresh but the frontend does not yet call it.

---

## Part 1 — Shared Contracts

### `ApiResponse<T>`

All endpoints return this wrapper. HTTP status is always `200` on transport success; business-level failures are signalled via `success: false`.

```java
// presentation/dto/response/ApiResponse.java
public record ApiResponse<T>(
    boolean success,
    T data,           // null on failure
    ApiError error    // null on success
) {
    public record ApiError(String code, String message) {}
}
```

**Success shape:**
```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

**Failure shape:**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "DOMAIN_ERROR_CODE",
    "message": "Human-readable description"
  }
}
```

**HTTP Status mapping** (via `GlobalExceptionHandler`):

| Scenario | HTTP |
|---|---|
| `DomainException` (business rule violation) | `400` |
| `@Valid` DTO validation failure | `422` |
| No authenticated principal / expired token | `401` |
| Insufficient permissions | `403` |
| Unhandled server error | `500` |

---

## Part 2 — Domain: Auth

### 2.1 Register

```
POST /api/v1/auth/register
```

**Request DTO** — `RegisterUserRequest.java`
```java
public record RegisterUserRequest(
    @NotBlank String fullName,
    @Email @NotBlank String email,
    @NotBlank @Size(min = 8) String password
) {}
```

**Success Response `200`**
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Business Logic & Invariants**
1. Validate email format (DB enforces regex `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$`).
2. Email must be unique (`user_account.email UNIQUE`).
3. Hash password with BCrypt before persisting.
4. Create `user_account` row (status = `ACTIVE`), then create `user_profile` row (full_name populated), `trust_score` row (initial score = 100), and `user_presence` row (status = `OFFLINE`).
5. Do NOT create `user_embedding` yet — that is populated asynchronously after profile completion.

**Potential Error Codes**

| Code | Trigger |
|---|---|
| `USER_EMAIL_ALREADY_EXISTS` | Duplicate email |
| `USER_INVALID_DATA` | `@Valid` failure (422) |

---

### 2.2 Login

```
POST /api/v1/auth/login
```

**Request DTO** — `LoginUserRequest.java`
```java
public record LoginUserRequest(
    @Email @NotBlank String email,
    @NotBlank String password
) {}
```

**Success Response `200`**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
  },
  "error": null
}
```

**Response DTO** — `LoginUserResponse.java`
```java
public record LoginUserResponse(String accessToken, String userId) {}
```

**Business Logic & Invariants**
1. Look up `user_account` by email.
2. Verify BCrypt hash match.
3. Check `user_account.status == ACTIVE` (reject `BANNED`/`SUSPENDED` accounts).
4. Issue a signed JWT `accessToken` (short-lived, e.g. 15 min) via `TokenProvider`.
5. Persist a `refresh_token` row linked to the user.
6. Update `user_account.last_login_at`.

**Potential Error Codes**

| Code | Trigger |
|---|---|
| `USER_NOT_FOUND` | Email not registered |
| `USER_INVALID_CREDENTIALS` | Password mismatch |
| `USER_ACCOUNT_SUSPENDED` | Account status not `ACTIVE` |

---

## Part 3 — Domain: Hotspot

### 3.1 List Hotspots

```
GET /api/v1/hotspots
```
*(No auth required — or optional auth for personalised ordering)*

**Success Response `200`**
```json
{
  "success": true,
  "data": [
    {
      "id": "hs-tao-dan",
      "name": "Công viên Tao Đàn",
      "lat": 10.77413,
      "lng": 106.68863,
      "activeWalkerCount": 12
    },
    {
      "id": "hs-nguyen-hue",
      "name": "Phố đi bộ Nguyễn Huệ",
      "lat": 10.77256,
      "lng": 106.70262,
      "activeWalkerCount": 28
    },
    {
      "id": "hs-ho-con-rua",
      "name": "Hồ Con Rùa",
      "lat": 10.77352,
      "lng": 106.69327,
      "activeWalkerCount": 15
    },
    {
      "id": "hs-gia-dinh",
      "name": "Công viên Gia Định",
      "lat": 10.81348,
      "lng": 106.68372,
      "activeWalkerCount": 9
    },
    {
      "id": "hs-le-van-tam",
      "name": "Công viên Lê Văn Tám",
      "lat": 10.78670,
      "lng": 106.69680,
      "activeWalkerCount": 6
    }
  ],
  "error": null
}
```

**Response DTO** — `HotspotResponse.java`
```java
public record HotspotResponse(
    String id,
    String name,
    double lat,
    double lng,
    int activeWalkerCount
) {}
```

**Business Logic & Invariants**
1. Return all hotspots in the system (static dataset initially).
2. `activeWalkerCount` = count of `walk_session` rows in `ACTIVE` or `PENDING` state whose `source_intent_id_a`/`_b` reference an intent at this hotspot, or computed via a materialised counter updated by session lifecycle events.

**Potential Error Codes**

| Code | Trigger |
|---|---|
| `HOTSPOT_FETCH_FAILED` | DB read error |

---

### 3.2 Get Hotspot by ID

```
GET /api/v1/hotspots/{id}
```

**Path Param:** `id` — hotspot ID string

**Success Response `200`** — same shape as single `HotspotResponse` object in `data`.

**Potential Error Codes**

| Code | Trigger |
|---|---|
| `HOTSPOT_NOT_FOUND` | No hotspot with the given `id` |

---

## Part 4 — Domain: WalkIntent

> **Note on C-4:** The frontend sends `timeStart` / `timeEnd` as hour floats (e.g. `17.0 = 17:00`).
> The `CreateWalkIntentRequest` includes a `date` field. The backend constructs the full timestamp as `date + hour_float` in the server timezone (UTC+7 / Ho Chi Minh City).

### 4.1 Create WalkIntent

```
POST /api/v1/intents
```

**Request DTO** — `CreateWalkIntentRequest.java`
```java
public record CreateWalkIntentRequest(
    @NotBlank String hotspotId,
    @NotNull @FutureOrPresent LocalDate date,   // e.g. "2026-03-29"
    @NotNull @DecimalMin("0.0") @DecimalMax("23.99") float timeStart,  // hour float
    @NotNull @DecimalMin("0.01") @DecimalMax("24.0") float timeEnd,
    @Min(13) @Max(100) int ageMin,
    @Min(13) @Max(100) int ageMax,
    List<String> tags   // optional, may be empty
) {}
```

**Success Response `200`**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "hotspotId": "hs-tao-dan",
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "timeStart": 17.0,
    "timeEnd": 19.0,
    "ageMin": 22,
    "ageMax": 35,
    "status": "OPEN",
    "createdAt": "2026-03-29T12:00:00Z",
    "tags": ["Đi bộ chậm", "Nghe podcast"]
  },
  "error": null
}
```

**Response DTO** — `WalkIntentResponse.java`
```java
public record WalkIntentResponse(
    String id,
    String hotspotId,
    String userId,
    float timeStart,
    float timeEnd,
    int ageMin,
    int ageMax,
    String status,        // "OPEN" | "CONSUMED" | "CANCELLED" | "EXPIRED"
    String createdAt,     // ISO-8601
    List<String> tags
) {}
```

**Business Logic & Invariants**
1. Caller is the authenticated user — `userId` is extracted from the JWT, not from the request body.
2. Validate `timeStart < timeEnd`.
3. Validate `ageMin <= ageMax`.
4. **I-1 (DB-level):** Check no existing `walk_intent` row for this user in `OPEN` state with overlapping `time_window_start`/`time_window_end`. Reject if overlap found.
5. Resolve `hotspotId` to ensure the hotspot exists.
6. Construct `time_window_start = date + timeStart`, `time_window_end = date + timeEnd` in UTC+7.
7. Set `expires_at = time_window_end`.
8. Insert `walk_intent` with `status = OPEN` (skip DRAFT per C-6 resolution).
9. Store `tags` inside `matching_constraints jsonb` field.

**Potential Error Codes**

| Code | Trigger |
|---|---|
| `INTENT_OVERLAPPING_TIME_WINDOW` | Invariant I-1 violated |
| `INTENT_INVALID_TIME_RANGE` | `timeStart >= timeEnd` |
| `INTENT_INVALID_AGE_RANGE` | `ageMin > ageMax` |
| `HOTSPOT_NOT_FOUND` | Invalid `hotspotId` |
| `INTENT_CREATE_FAILED` | General persistence failure |

---

### 4.2 List Active WalkIntents (Current User)

```
GET /api/v1/intents
```

Returns the authenticated user's own intents in non-terminal states (`OPEN`).

**Success Response `200`**
```json
{
  "success": true,
  "data": [
    {
      "id": "intent-001",
      "hotspotId": "Công viên Tao Đàn",
      "userId": "mock-user-1",
      "timeStart": 17.0,
      "timeEnd": 19.0,
      "ageMin": 22,
      "ageMax": 35,
      "status": "OPEN",
      "createdAt": "2026-03-29T10:00:00Z",
      "tags": ["Đi bộ chậm", "Nghe podcast"]
    },
    {
      "id": "intent-003",
      "hotspotId": "Công viên Gia Định",
      "userId": "mock-user-1",
      "timeStart": 18.5,
      "timeEnd": 20.0,
      "ageMin": 20,
      "ageMax": 30,
      "status": "OPEN",
      "createdAt": "2026-03-29T11:00:00Z",
      "tags": ["Thiền", "Đi bộ chậm"]
    }
  ],
  "error": null
}
```

**Business Logic & Invariants**
1. Filter `walk_intent` by `user_id = caller` AND `status = OPEN`.
2. Exclude terminal states (`CONSUMED`, `CANCELLED`, `EXPIRED`).

**Potential Error Codes**

| Code | Trigger |
|---|---|
| `INTENT_FETCH_FAILED` | DB read error |

---

### 4.3 Find Match for WalkIntent

```
GET /api/v1/intents/{intentId}/match
```

**Path Param:** `intentId`

Triggers the AI/matching engine to find a compatible `WalkIntent` from another user and creates a `MatchProposal` if a match is found.

**Success Response `200`**
```json
{
  "success": true,
  "data": {
    "id": "intent-001",
    "hotspotId": "hs-tao-dan",
    "userId": "mock-user-1",
    "timeStart": 17.0,
    "timeEnd": 19.0,
    "ageMin": 22,
    "ageMax": 35,
    "status": "OPEN",
    "createdAt": "2026-03-29T10:00:00Z",
    "tags": ["Đi bộ chậm", "Nghe podcast"]
  },
  "error": null
}
```
*(Returns the caller's own intent updated with matching status, or the match proposal details — to be confirmed by the matching engine output contract.)*

**Business Logic & Invariants**
1. Verify `intentId` belongs to the authenticated caller.
2. Verify intent is in `OPEN` state (I-2 / I-3).
3. Query for candidate intents: `OPEN`, overlapping time window, compatible location (within hotspot radius), no block relation between users (X-3).
4. Score candidates via AI matching (user embedding similarity, interest tags, trust score).
5. If match found: create a `MatchProposal` (status = `PENDING`) linking the two intents, set `expires_at`.
6. Return the caller's own intent (frontend uses this to update local state).

**Potential Error Codes**

| Code | Trigger |
|---|---|
| `INTENT_NOT_FOUND` | `intentId` not found or doesn't belong to caller |
| `INTENT_NOT_OPEN` | Intent is not in `OPEN` state |
| `INTENT_MATCH_NOT_FOUND` | No compatible intent found after scoring |

---

### 4.4 Cancel WalkIntent

```
DELETE /api/v1/intents/{intentId}
```

**Path Param:** `intentId`

**Success Response `200`**
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Business Logic & Invariants**
1. Verify `intentId` belongs to the authenticated caller.
2. Verify intent is in `OPEN` state — I-5 forbids mutation of `CONSUMED`, `CANCELLED`, `EXPIRED`.
3. Transition `walk_intent.status → CANCELLED`.
4. **I-4 / P-5 cascade:** All `match_proposal` rows in `PENDING` state that reference this intent must transition to `EXPIRED` (or `REJECTED`) atomically within the same transaction.

**Potential Error Codes**

| Code | Trigger |
|---|---|
| `INTENT_NOT_FOUND` | Intent not found or not owned by caller |
| `INTENT_ALREADY_TERMINAL` | Intent in `CONSUMED`, `CANCELLED`, or `EXPIRED` |
| `INTENT_CANCEL_FAILED` | General persistence failure |

---

## Part 5 — Domain: MatchProposal

> No API service stub exists in the frontend yet. These endpoints are derived from `WalkProposalRepositoryImpl` and the lifecycle/invariants.

### 5.1 Get Proposals for Current User

```
GET /api/v1/proposals
```

Returns all `PENDING` proposals where the caller's intent is one of the two referenced intents.

**Success Response `200`**
```json
{
  "success": true,
  "data": [
    {
      "proposalId": "proposal-001",
      "intentId": "intent-001",
      "matchedUserId": "user-42",
      "matchedUserName": "Linh Nguyễn",
      "matchedUserAge": 26,
      "trustScore": 92,
      "overlappingTags": ["Đi bộ chậm", "Nghe podcast"],
      "overlappingTimeStart": 17.0,
      "overlappingTimeEnd": 18.5,
      "status": "PENDING",
      "expiresAt": "2026-03-29T18:30:00Z"
    },
    {
      "proposalId": "proposal-002",
      "intentId": "intent-002",
      "matchedUserId": "user-55",
      "matchedUserName": "Minh Tuấn",
      "matchedUserAge": 30,
      "trustScore": 85,
      "overlappingTags": ["Chạy bộ"],
      "overlappingTimeStart": 8.5,
      "overlappingTimeEnd": 9.5,
      "status": "PENDING",
      "expiresAt": "2026-03-29T09:30:00Z"
    }
  ],
  "error": null
}
```

**Response DTO** — `WalkProposalResponse.java`
```java
public record WalkProposalResponse(
    String proposalId,
    String intentId,
    String matchedUserId,
    String matchedUserName,
    int matchedUserAge,
    int trustScore,
    List<String> overlappingTags,
    float overlappingTimeStart,
    float overlappingTimeEnd,
    String status,      // "PENDING" | "CONFIRMED" | "REJECTED" | "EXPIRED"
    String expiresAt    // ISO-8601
) {}
```

**Business Logic & Invariants**
1. Return proposals where caller owns one of the two referenced intents.
2. Filter by `status = PENDING` (terminal proposals are historical, not actionable).
3. Compute `overlappingTags` as intersection of the two intents' tag lists.
4. Compute `overlappingTimeStart`/`End` as the time window intersection.
5. `matchedUserId`, `matchedUserName`, `matchedUserAge`, `trustScore` are fetched from `user_profile` and `trust_score` of the *other* user.

**Potential Error Codes**

| Code | Trigger |
|---|---|
| `PROPOSAL_FETCH_FAILED` | DB read error |

---

### 5.2 Accept Proposal

```
POST /api/v1/proposals/{proposalId}/accept
```

**Path Param:** `proposalId`

**Success Response `200`** — Returns the newly created `WalkSession`

```json
{
  "success": true,
  "data": {
    "sessionId": "550e8400-e29b-41d4-a716-446655440001",
    "proposalId": "proposal-001",
    "partnerName": "Linh Nguyễn",
    "partnerAvatar": null,
    "meetingPointLat": 10.7769,
    "meetingPointLng": 106.7009,
    "scheduledTime": "2026-03-29T17:00:00Z",
    "status": "PENDING"
  },
  "error": null
}
```

**Response DTO** — `WalkSessionResponse.java` (see Part 6)

**Business Logic & Invariants** — This is the most critical endpoint, governed by **P-2 and P-3**.
1. Verify `proposalId` belongs to one of the caller's `OPEN` intents.
2. Record the caller's acceptance (`accepted_by_a` or `accepted_by_b` = true).
3. Check if **both** participants have now accepted:
   - If **not**: persist acceptance flag only. Return the proposal (no session yet).
   - If **yes**: proceed with P-3 Atomic Session Creation (steps 4–7).
4. **P-3 ATOMIC TRANSACTION:**
   a. Lock both `walk_intent` rows (SELECT FOR UPDATE).
   b. Verify both are still in `OPEN` state (P-2). If not, reject and mark proposal `REJECTED`/`EXPIRED`.
   c. **S-2:** Verify neither user has an overlapping session in `PENDING` or `ACTIVE` state.
   d. Create one `walk_session` row with `status = PENDING`.
   e. Transition both `walk_intent` rows to `CONSUMED` (I-3).
   f. Transition `match_proposal` to `CONFIRMED` (confirmed_at = now).
5. **X-4:** If WalkSession creation fails, abort the transaction — `MatchProposal` must NOT remain `CONFIRMED` without a session.
6. Return the new `WalkSession` wrapped in `ApiResponse`.

**Potential Error Codes**

| Code | Trigger |
|---|---|
| `PROPOSAL_NOT_FOUND` | `proposalId` not found or caller not a participant |
| `PROPOSAL_ALREADY_TERMINAL` | Proposal is `CONFIRMED`, `REJECTED`, or `EXPIRED` |
| `PROPOSAL_INTENT_NO_LONGER_OPEN` | One/both intents transitioned away from `OPEN` during concurrency window |
| `PROPOSAL_OVERLAPPING_SESSION` | S-2 violation — user already has an active/pending session in this time window |
| `PROPOSAL_ACCEPT_FAILED` | Atomic transaction failure |

---

### 5.3 Pass (Reject) Proposal

```
POST /api/v1/proposals/{proposalId}/pass
```

**Path Param:** `proposalId`

**Success Response `200`**
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Business Logic & Invariants**
1. Verify `proposalId` is `PENDING` and caller is a participant.
2. Transition `match_proposal.status → REJECTED` (P-6 terminal).
3. **P-5 cascade:** The referenced `WalkIntent`(s) remain in `OPEN` state (rejecting a proposal does not cancel the intent — the user is still available for new proposals).

**Potential Error Codes**

| Code | Trigger |
|---|---|
| `PROPOSAL_NOT_FOUND` | `proposalId` not found or caller not a participant |
| `PROPOSAL_ALREADY_TERMINAL` | Cannot reject a non-`PENDING` proposal |
| `PROPOSAL_PASS_FAILED` | General persistence failure |

---

## Part 6 — Domain: WalkSession

### 6.1 Get Active Sessions

```
GET /api/v1/sessions/active
```

Returns the caller's sessions in non-terminal states (`PENDING`, `ACTIVE`).

**Success Response `200`**
```json
{
  "success": true,
  "data": [
    {
      "sessionId": "session-001",
      "proposalId": "proposal-003",
      "partnerName": "Thu Hà",
      "partnerAvatar": null,
      "meetingPointLat": 10.7769,
      "meetingPointLng": 106.7009,
      "scheduledTime": "2026-03-29T14:00:00Z",
      "status": "PENDING"
    }
  ],
  "error": null
}
```

**Response DTO** — `WalkSessionResponse.java`
```java
public record WalkSessionResponse(
    String sessionId,
    String proposalId,      // source match_proposal ID
    String partnerName,
    String partnerAvatar,   // URL or null
    double meetingPointLat,
    double meetingPointLng,
    String scheduledTime,   // ISO-8601 (= scheduled_start_time)
    String status           // "PENDING" | "ACTIVE" | "COMPLETED" | "NO_SHOW" | "CANCELLED" | "ABORTED"
) {}
```

**Business Logic & Invariants**
1. Query `walk_session` where (`user1_id = caller` OR `user2_id = caller`) AND `status IN ('PENDING', 'ACTIVE')`.
2. `partnerName`/`partnerAvatar` resolved from the *other* user's `user_profile`.
3. `meetingPointLat`/`Lng` resolved from the source `match_proposal.proposed_location_lat/lng`.
4. `proposalId` is derived by joining `match_proposal` via `source_intent_id_a`/`_b`.

**Potential Error Codes**

| Code | Trigger |
|---|---|
| `SESSION_FETCH_FAILED` | DB read error |

---

### 6.2 Activate Session

```
POST /api/v1/sessions/{sessionId}/activate
```

*(Derived from DB invariants S-3, S-4, S-5 — no frontend stub yet per C-5)*

**Path Param:** `sessionId`

**Success Response `200`**
```json
{
  "success": true,
  "data": {
    "sessionId": "session-001",
    "proposalId": "proposal-003",
    "partnerName": "Thu Hà",
    "partnerAvatar": null,
    "meetingPointLat": 10.7769,
    "meetingPointLng": 106.7009,
    "scheduledTime": "2026-03-29T14:00:00Z",
    "status": "ACTIVE"
  },
  "error": null
}
```

**Business Logic & Invariants**
1. Verify session is in `PENDING` state.
2. Verify current time is within the activation window: `[scheduled_start_time - earlyGrace, scheduled_start_time + lateGrace]` (S-4).
3. Record the caller's activation timestamp (`user1_activated_at` or `user2_activated_at`).
4. If **both** users have now activated → transition `status = ACTIVE`, set `actual_start_time = now` (S-3).
5. If only one user has activated → persist timestamp only, return session still in `PENDING`.
6. Write a `session_state_change_log` row for `PENDING → ACTIVE` transition.

**Potential Error Codes**

| Code | Trigger |
|---|---|
| `SESSION_NOT_FOUND` | Session not found or caller not a participant |
| `SESSION_NOT_PENDING` | Session is not in `PENDING` state |
| `SESSION_ACTIVATION_WINDOW_CLOSED` | Current time is outside the valid activation window (S-4) |

---

### 6.3 Cancel Session

```
POST /api/v1/sessions/{sessionId}/cancel
```

**Request DTO** — `CancelWalkSessionRequest.java`
```java
public record CancelWalkSessionRequest(
    @NotBlank String reason   // human-readable cancellation reason
) {}
```

*(Note: `cancelSession(sessionId, reason, callback)` in the repository confirms `reason` is required)*

**Success Response `200`**
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Business Logic & Invariants**
1. Verify session belongs to caller.
2. Verify session is in `PENDING` state — S-6 states cancellation is only valid **prior to** the activation window. `ACTIVE` sessions cannot be cancelled (use abort instead).
3. Transition `walk_session.status → CANCELLED`.
4. Persist `cancellation_reason` and `cancelled_by` (S-6).
5. Write `session_state_change_log` row.
6. S-8 terminal immutability — no further state changes possible.

**Potential Error Codes**

| Code | Trigger |
|---|---|
| `SESSION_NOT_FOUND` | Session not found or caller not a participant |
| `SESSION_CANCEL_NOT_PENDING` | Session is not in `PENDING` state (S-6) |
| `SESSION_CANCEL_FAILED` | General persistence failure |

---

### 6.4 Abort Active Session

```
POST /api/v1/sessions/{sessionId}/abort
```

*(Required by DB `ABORTED` state and `abort_reason` column — C-2 resolution)*

**Request DTO** — `AbortWalkSessionRequest.java`
```java
public record AbortWalkSessionRequest(
    @NotNull AbortReason reason
) {
    public enum AbortReason { INJURY, SAFETY, ENVIRONMENT, OTHER }
}
```

**Success Response `200`**
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Business Logic & Invariants**
1. Verify session is in `ACTIVE` state.
2. Verify caller is a participant.
3. Transition `walk_session.status → ABORTED`, persist `abort_reason`, set `actual_end_time = now`.
4. Write `session_state_change_log` row.
5. Terminal state — S-8 applies to `ABORTED` as well (per C-2 resolution).

**Potential Error Codes**

| Code | Trigger |
|---|---|
| `SESSION_NOT_FOUND` | Session not found or caller not a participant |
| `SESSION_NOT_ACTIVE` | Session is not in `ACTIVE` state |
| `SESSION_ABORT_FAILED` | General persistence failure |

---

## Part 7 — Domain: Tracking

### 7.1 Push Route Points (Batch Sync)

```
POST /api/v1/tracking/sync
```

**Request DTO** — `PushRoutePointsRequest.java`
```java
public record PushRoutePointsRequest(
    @NotBlank String sessionId,
    @NotEmpty List<RoutePointPayload> points
) {
    public record RoutePointPayload(
        long localId,       // Room row ID — used for client-side markPointsSynced()
        double lat,
        double lng,
        long timestamp,     // Unix epoch milliseconds
        float accuracy      // horizontal accuracy in metres
    ) {}
}
```

**Success Response `200`**
```json
{
  "success": true,
  "data": {
    "syncedCount": 50,
    "acknowledgedIds": [1001, 1002, 1003]
  },
  "error": null
}
```

**Response DTO** — `PushRoutePointsResponse.java`
```java
public record PushRoutePointsResponse(
    int syncedCount,
    List<Long> acknowledgedIds  // localId values from the request — client marks these synced
) {}
```

**Business Logic & Invariants**
1. Verify `sessionId` belongs to a session in `ACTIVE` state where caller is a participant.
2. Validate each point: `lat` in `[-90, 90]`, `lng` in `[-180, 180]`, `timestamp <= now`.
3. Encode points as a Google Encoded Polyline string.
4. Determine `chunk_index` (auto-increment per session).
5. Persist as a `session_point_chunks` row (`polyline`, `timestamps` as bytea, `elevations` as bytea, `point_count`).
6. Return `acknowledgedIds` (the `localId` values from the request) so the client can call `markPointsSynced()` locally.

**Potential Error Codes**

| Code | Trigger |
|---|---|
| `TRACKING_SESSION_NOT_FOUND` | `sessionId` not found or caller not a participant |
| `TRACKING_SESSION_NOT_ACTIVE` | Session is not in `ACTIVE` state |
| `TRACKING_INVALID_POINT` | A point has invalid coordinates or future timestamp |
| `TRACKING_SYNC_FAILED` | General persistence failure |

---

## Part 8 — Error Code Registry

Full canonical list of `DomainErrorCode` constants across all domains:

```java
// UserErrorCode.java
USER_NOT_FOUND
USER_INVALID_CREDENTIALS
USER_EMAIL_ALREADY_EXISTS
USER_INVALID_DATA
USER_ACCOUNT_SUSPENDED

// HotspotErrorCode.java
HOTSPOT_NOT_FOUND
HOTSPOT_FETCH_FAILED

// IntentErrorCode.java
INTENT_NOT_FOUND
INTENT_NOT_OPEN
INTENT_ALREADY_TERMINAL
INTENT_OVERLAPPING_TIME_WINDOW
INTENT_INVALID_TIME_RANGE
INTENT_INVALID_AGE_RANGE
INTENT_MATCH_NOT_FOUND
INTENT_CREATE_FAILED
INTENT_CANCEL_FAILED
INTENT_FETCH_FAILED

// ProposalErrorCode.java
PROPOSAL_NOT_FOUND
PROPOSAL_ALREADY_TERMINAL
PROPOSAL_INTENT_NO_LONGER_OPEN
PROPOSAL_OVERLAPPING_SESSION
PROPOSAL_ACCEPT_FAILED
PROPOSAL_PASS_FAILED
PROPOSAL_FETCH_FAILED

// SessionErrorCode.java
SESSION_NOT_FOUND
SESSION_NOT_PENDING
SESSION_NOT_ACTIVE
SESSION_FETCH_FAILED
SESSION_ACTIVATION_WINDOW_CLOSED
SESSION_CANCEL_NOT_PENDING
SESSION_CANCEL_FAILED
SESSION_ABORT_FAILED

// TrackingErrorCode.java
TRACKING_SESSION_NOT_FOUND
TRACKING_SESSION_NOT_ACTIVE
TRACKING_INVALID_POINT
TRACKING_SYNC_FAILED
TRACKING_SAVE_POINT_FAILED
TRACKING_FETCH_POINTS_FAILED
```

---

## Part 9 — Endpoint Summary

| # | Method | Path | Auth | Domain |
|---|---|---|---|---|
| 1 | `POST` | `/api/v1/auth/register` | No | Auth |
| 2 | `POST` | `/api/v1/auth/login` | No | Auth |
| 3 | `GET` | `/api/v1/hotspots` | Optional | Hotspot |
| 4 | `GET` | `/api/v1/hotspots/{id}` | Optional | Hotspot |
| 5 | `POST` | `/api/v1/intents` | Yes | WalkIntent |
| 6 | `GET` | `/api/v1/intents` | Yes | WalkIntent |
| 7 | `GET` | `/api/v1/intents/{intentId}/match` | Yes | WalkIntent |
| 8 | `DELETE` | `/api/v1/intents/{intentId}` | Yes | WalkIntent |
| 9 | `GET` | `/api/v1/proposals` | Yes | MatchProposal |
| 10 | `POST` | `/api/v1/proposals/{proposalId}/accept` | Yes | MatchProposal |
| 11 | `POST` | `/api/v1/proposals/{proposalId}/pass` | Yes | MatchProposal |
| 12 | `GET` | `/api/v1/sessions/active` | Yes | WalkSession |
| 13 | `POST` | `/api/v1/sessions/{sessionId}/activate` | Yes | WalkSession |
| 14 | `POST` | `/api/v1/sessions/{sessionId}/cancel` | Yes | WalkSession |
| 15 | `POST` | `/api/v1/sessions/{sessionId}/abort` | Yes | WalkSession |
| 16 | `POST` | `/api/v1/tracking/sync` | Yes | Tracking |

---

## Part 10 — Decisions & Deferred Scope

| Item | Decision |
|---|---|
| Refresh token endpoint | Deferred — DB table exists, frontend not wired yet |
| User profile CRUD | Deferred — no frontend repository found |
| Chat (chat_room / chat_message) | Deferred — no frontend API stub |
| Notification | Deferred — no frontend API stub |
| Follow / Block relations | Deferred — no frontend repository |
| Walk Review (post-session rating) | Deferred — no frontend stub |
| Dispute / Report | Deferred — admin/moderation scope |
| AI matching internals | Internal — `GET /intents/{id}/match` is the public surface |
| User Presence / Quick Mode | Deferred — `user_presence` table exists, no frontend stub |
| Gamification (badges, points) | Deferred — `badge`, `user_badge`, `session_point_chunks` exist in DB |
