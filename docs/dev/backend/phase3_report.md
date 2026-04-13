# Phase 3 Report — Create Intent Inline Match (Public Path)

**Date:** 2026-04-13
**Branch:** `merge/oauth`
**Gaps closed:** 3.1, 3.3

---

## What Was Done

### Step 1 — `CreateIntentResult` (new)
**File:** `application/walkintent/CreateIntentResult.java`

Plain value record that carries both the saved `WalkIntent` and an optional `MatchProposal` out of `WalkIntentCommandService.createIntent()`. `proposal` is `null` when no match was found (Case A2).

### Step 2 — `WalkIntentCommandService.createIntent()` updated
**File:** `application/walkintent/WalkIntentCommandService.java`

- Return type changed from `WalkIntent` → `CreateIntentResult`.
- `MatchingCommandService` injected via `@RequiredArgsConstructor` (no circular dependency — `MatchingCommandService` depends on `WalkIntentRepository`, not on this service).
- Intent creation is committed first via `TransactionTemplate`.
- After commit, public path attempts inline matching (`findOrCreateProposal`) as best-effort.
- If no candidate exists, matching returns `Optional.empty()` and the response carries `proposal = null`.
- If matching throws any exception, it is logged and the API still returns success with `proposal = null` (intent remains created).

### Step 3 — `CreateIntentResponse` (new)
**File:** `presentation/dto/response/walkintent/CreateIntentResponse.java`

Presentation-layer DTO only. Carries `intent` (`WalkIntentResponse`) and nullable `proposal` (`WalkProposalResponse`). Jackson serialises `proposal` as `null` in the JSON when no match was found — the client uses this to decide whether to route to the Proposal tab.

### Step 4 — `WalkIntentController.createIntent()` updated
**File:** `presentation/controller/walkintent/WalkIntentController.java`

- Return type changed to `ResponseEntity<ApiResponse<CreateIntentResponse>>`.
- `CreateIntentResult` destructured: `intent` mapped via `walkIntentMapper.toResponse()`, `proposal` mapped via `proposalMapper.toResponse()` (or `null`).
- `principal.userId()` (already `String`) passed directly — no unnecessary `UUID.fromString()` round-trip.
- Only caller of `createIntent()` was this controller; no other callers required updating.
- Missing `MatchProposal` import in `triggerMatch()` was restored to keep backend compilation green.

---

## Architecture Compliance

| Rule | Status |
|---|---|
| Intent creation remains transactional and committed before best-effort matching | ✅ |
| Matching errors do not break create intent success path | ✅ |
| `CreateIntentResponse` is Presentation-only DTO | ✅ |
| No circular dependency introduced | ✅ |

---

## Gaps Closed

| Gap | Description | Status |
|---|---|---|
| 3.1 | UC-15 inline matching never triggered on create (public path) | ✅ CLOSED |
| 3.3 | UC-15 create response cannot embed proposal data (no union DTO) | ✅ CLOSED |
