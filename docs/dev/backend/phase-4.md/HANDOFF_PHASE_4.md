# Phase 4 Handoff — Matching Engine

## What Was Built

Phase 4 implements the full proposal lifecycle: matching, mutual acceptance, and session creation.

---

## Backend

### New Migrations

| File | Purpose |
|------|---------|
| `V7__add_proposal_status_confirmed.sql` | Adds `CONFIRMED` to `proposal_status` enum; migrates `ACCEPTED→CONFIRMED`; adds partial unique index on `(LEAST/GREATEST intent_id pair) WHERE status='PENDING'` to prevent duplicate proposals |
| `V8__create_walk_session.sql` | Creates `walk_session_status` enum and `walk_session` table |

### New Domain Classes

| Class | Role |
|-------|------|
| `domain/proposal/ProposalStatus` | `PENDING`, `CONFIRMED`, `REJECTED`, `EXPIRED`; `@Deprecated ACCEPTED` |
| `domain/proposal/ProposalErrorCode` | `PROPOSAL_NOT_FOUND`, `PROPOSAL_NOT_PARTICIPANT`, `PROPOSAL_ALREADY_TERMINAL`, `PROPOSAL_INTENT_NO_LONGER_OPEN` |
| `domain/proposal/MatchProposal` | Rich entity. `recordAcceptance(callerIntentId)` returns `true` when both have accepted. `confirm()`, `reject()`. |
| `domain/proposal/MatchProposalRepository` | `save`, `findById`, `findPendingByIntentId`, `findPendingForUser` |
| `domain/session/SessionStatus` | `PENDING`, `ACTIVE`, `COMPLETED`, `NO_SHOW`, `CANCELLED`, `ABORTED` |
| `domain/session/WalkSession` | Entity with `WalkSession.create(...)` factory |
| `domain/session/WalkSessionRepository` | `save`, `findByProposalId`, `findActiveForUser` |

### New Infrastructure

| Class | Role |
|-------|------|
| `infrastructure/repository/proposal/MatchProposalJdbcRepository` | `JdbcClient`-based; JOINs `walk_intent` to resolve `userIdA/B` |
| `infrastructure/repository/session/WalkSessionJdbcRepository` | `JdbcClient`-based |

### Modified Domain

- `WalkIntentRepository` — added `findByIdForUpdate(String id)` (`SELECT … FOR UPDATE`)
- `WalkIntentJdbcRepository` — implemented `findByIdForUpdate`

### New Application Service

**`MatchingCommandService`** — the critical service. Key methods:

#### `findOrCreateProposal(intentId, callerId)`
1. Verify intent is OPEN and owned by caller.
2. If an existing PENDING proposal exists for this intent, return it.
3. Run matching strategy to find a candidate intent.
4. Insert new proposal (partial unique index aborts duplicates silently).
5. Return the proposal, or `Optional.empty()` if no candidate found.

#### `acceptProposal(proposalId, callerId)` — P-3 concurrency-safe protocol
1. Load proposal, resolve `callerIntentId` via `resolveIntentIdForUser`.
2. `proposal.recordAcceptance(callerIntentId)` — if not both accepted yet, save and return.
3. If both accepted:
   - Lock intent rows in **lexicographic order** (prevents deadlock).
   - Re-verify both intents are still OPEN under lock.
   - `first.consume()` + `second.consume()` + save both.
   - `proposal.confirm(now)` + save.
   - `WalkSession.create(...)` + save.
4. Return confirmed proposal.

#### `passProposal(proposalId, callerId)`
- Sets proposal to REJECTED. Both intents remain OPEN.

#### `getPendingProposals(userId)`
- Used by `GET /api/v1/proposals`.

### New Presentation Layer

| Class | Endpoint |
|-------|---------|
| `WalkProposalResponse` | Response DTO (proposal_id, callers_intent_id, matched_intent_id, callers_user_id, matched_user_id, proposed_time_start/end, lat, lng, status, expires_at, session_id) |
| `ProposalMapper` | Routes caller vs matched fields based on authenticated user ID |
| `ProposalController` | `GET /api/v1/proposals`, `POST /proposals/{id}/accept`, `POST /proposals/{id}/pass` |

### Modified Presentation Layer

- `WalkIntentController.findMatch` now delegates to `MatchingCommandService.findOrCreateProposal()` and returns `WalkProposalResponse` (was `WalkIntentResponse`).
- `SecurityConfig` — added `.requestMatchers("/api/v1/proposals/**").authenticated()`.

---

## Frontend

### New Files

| File | Role |
|------|------|
| `data/datasource/remote/api/ProposalApiService.java` | Retrofit interface: `getProposals`, `acceptProposal`, `passProposal` |
| `data/datasource/remote/dto/response/proposal/WalkProposalResponse.java` | Gson DTO matching backend `WalkProposalResponse` |
| `data/mapper/WalkProposalMapper.java` | `toDomain(WalkProposalResponse)` → `WalkProposal`; `toSession(WalkProposalResponse)` → `WalkSession` |

### Modified Files

| File | Change |
|------|--------|
| `data/datasource/remote/api/WalkIntentApiService.java` | `findMatch` return type changed: `ApiResponse<WalkIntentResponse>` → `ApiResponse<WalkProposalResponse>` |
| `domain/walkintent/WalkIntentRepository.java` | `findMatch` callback changed: `DomainCallback<WalkIntent>` → `DomainCallback<WalkProposal>` |
| `data/repository/WalkIntentRepositoryImpl.java` | `findMatch` un-stubbed: wired to real Retrofit; 204 response → `onSuccess(null)` |
| `data/repository/WalkProposalRepositoryImpl.java` | Full rewrite — removed mock; wired `getProposals`, `acceptProposal`, `passProposal` to real Retrofit |

### `acceptProposal` Contract

The endpoint always returns `WalkProposalResponse`. The repository translates it:
- `sessionId != null` (status = CONFIRMED) → `onSuccess(WalkSession)` — session was created.
- `sessionId == null` (status = PENDING) → `onSuccess(null)` — waiting for the other participant; the UI layer should show a "waiting" state.

### Placeholder Fields

The following `WalkProposal` domain fields are not yet provided by the backend API and use defaults until a user-profile enrichment endpoint is added:
- `matchedUserName` → uses `matchedUserId` as placeholder
- `matchedUserAge` → `0`
- `trustScore` → `0`
- `overlappingTags` → empty list

---

## API Contract Summary

```
GET    /api/v1/intents/{intentId}/match   → 200 WalkProposalResponse | 204 No Content
GET    /api/v1/proposals                  → 200 List<WalkProposalResponse>
POST   /api/v1/proposals/{id}/accept      → 200 WalkProposalResponse (sessionId populated when CONFIRMED)
POST   /api/v1/proposals/{id}/pass        → 200 ApiResponse<Void>
```

All endpoints require a valid JWT (`Authorization: Bearer <token>`).

---

## Known Gaps / Phase 5 Considerations

1. **User profile enrichment** — `matchedUserName`, `matchedUserAge`, `trustScore`, `overlappingTags` require a `GET /api/v1/users/{id}` profile endpoint.
2. **Proposal expiry** — `expires_at` is stored but no scheduled job sweeps expired proposals yet.
3. **WebSocket / push notifications** — the matched user is not notified when a proposal is created. Phase 5 should add STOMP/WebSocket or FCM push.
4. **`findMatch` UI wiring** — `WalkIntentRepository.findMatch` is implemented but no ViewModel/Fragment calls it yet; that wiring belongs in the UI phase.
