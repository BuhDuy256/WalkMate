# WalkMate — Appendices

> Part of: [Use Cases Index](README.md)

**Document Purpose:** Shared reference material applicable across all domains — global error handling contract and the invariant-to-UI decision map.
**Last Updated:** 2026-04-13

---

## Appendix A — Global Error Handling

All API responses follow the `ApiResponse<T>` envelope. This is the **only** error format returned by the backend — there is no `errors` array.

```json
// Success
{ "success": true, "data": { ... }, "error": null, "timestamp": "2026-04-08T10:00:00Z" }

// Failure
{ "success": false, "data": null, "error": { "code": "SOME_ERROR_CODE", "message": "Human-readable description." }, "timestamp": "2026-04-08T10:00:00Z" }
```

**Validation error format** (`VALIDATION_ERROR` / HTTP 422): `error.message` is a **single comma-separated string** of field violations, e.g. `"fullName: must not be blank, email: must be a valid email address"`. Parse this string by splitting on `", "` to render per-field inline errors.

### Critical: HTTP Status vs. Domain Error Code

> **Important for UI engineers:** `@GlobalExceptionHandler` maps **all** `DomainException`s to HTTP **400 Bad Request**, regardless of the semantic meaning of the error code. A `SESSION_NOT_FOUND` or `SESSION_NOT_PARTICIPANT` will both arrive as HTTP 400. **Do NOT use HTTP status codes alone to distinguish domain errors — always read `error.code`.**

| HTTP Status | Source | Scenario | UI Default Action |
|-------------|--------|----------|------------------|
| 400 | `DomainException` | Business rule violation (not found, wrong state, permission denied, etc.) | Read `error.code` from the response body; use the per-UC error table to react. |
| 401 | Spring Security | Token missing, expired, or invalid | Clear token from storage, navigate to Login screen. |
| 404 | `NoResourceFoundException` | URL route does not exist on the server | Show toast: "Page not found." Log to crash reporter. |
| 405 | `HttpRequestMethodNotSupportedException` | Wrong HTTP method used | Never happens in normal flow; log to crash reporter. |
| 422 | `MethodArgumentNotValidException` | Jakarta `@Valid` annotation failed | Split `error.message` on `", "` and map fragments to field-level inline errors. |
| 500 | Unhandled `Exception` | Unexpected server error | Show toast: "Something went wrong. Please try again." Log `error.code` + `error.message` to crash reporter. |
| Network timeout | — | No HTTP response received | Show toast: "Connection error. Check your internet." Offer retry. |

---

## Appendix B — Key Invariant ↔ UI Decision Map

| Invariant | Rule Summary | UI Enforcement |
|-----------|-------------|----------------|
| **I-1** | No overlapping time windows | Show `INTENT_OVERLAPPING` / `INTENT_OVERLAPPING_SESSION` as blocking dialog, not toast |
| **I-3** | An intent can only reach `CONSUMED` via the proposal acceptance flow (P-3). `CONSUMED` means the intent has been spent to create a WalkSession — it is permanently locked and cannot be cancelled, re-opened, or reused. It is **not** a user-facing error state; it is a silent terminal state. | Never show any action button for a `CONSUMED` intent. Do not surface the word "CONSUMED" to end users — these intents should disappear from active lists. If the API returns `INTENT_NOT_OPEN` for an intent the user tries to act on, refresh silently: the intent has likely been consumed by concurrent proposal acceptance. |
| **I-4** | MATCHING intents are soft-locked and move out of Intent tab | In Intent tab, render OPEN intents only. Route MATCHING handling to Proposal tab with lock/wait states |
| **I-6** | Terminal states are immutable | Hide all action buttons for CONSUMED, CANCELLED, EXPIRED intents and COMPLETED, NO_SHOW, CANCELLED, ABORTED sessions |
| **I-7** | Private intents need accepted friendship | Validate friend selection client-side before submit |
| **P-2** | Both users must accept proposal | Show "Waiting for partner..." state when only one has accepted |
| **P-3** | Session creation is atomic on double-accept | Navigate to session screen only when `status: "CONFIRMED"` and `session_id` is present in response |
| **P-4** | Proposal TTL is 5 minutes | Show live countdown; auto-refresh list when timer hits 0 |
| **S-2** | Session ACTIVE only when both activate | Show "Waiting for partner to arrive..." state after single activation |
| **S-3** | Activation window is `[scheduledStart − 10 min, scheduledStart + 15 min]` | Disable "I'm Here!" button outside this window; show a countdown to window open and a countdown to window close once inside |
| **S-5** | 5-minute minimum walk before complete | Disable "Complete Walk" button; show a countdown timer until it becomes enabled |
| **S-6** | Session auto-closes at 4 hours | Listen for FCM notification of auto-completion; navigate to history |
| **S-7** | Chat locked after terminal state | Disable chat input immediately upon session terminal state |
| **S-8** | Session time/location are immutable snapshots | Never offer "edit session time" after session creation |
| **X-3** | Exclude list updated on rejection | After passing a proposal, the same pair won't appear again — no UI action needed |
| **X-4** | Reputation updated on terminal session | Refresh user stats after session ends |
| **X-5** | Optimistic locking on all state changes | Handle `PROPOSAL_CONCURRENT_MODIFICATION` with retry toast; never silently discard |
