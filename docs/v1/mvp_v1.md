# MVP (V1) Functional Chain — Walking Session Prototype

This document summarizes the **Functional Chain** for the V1 MVP (school project), expanded with the updated **Intent** definition:

- **Intent includes:** Flexible Time Window (±30m / ±1h), Location, Walk Type

---

## A) Problem

**V1 goal:** validate **session lifecycle correctness** (state consistency + deterministic behavior + DB integrity).  
Matching is **only a trigger**; lifecycle rules are the core.

**Out of scope (V1):** chat, notifications, AI scoring, radar UI, advanced filtering, background workers.

**Deliverable:** a working end-to-end flow where **two users complete a walk session** through the defined lifecycle.

---

## B) Core Workflow (Narrative Flow)

1. User creates an **Intent** (time window + location + walk type).
2. User triggers **Match**.
3. System finds a compatible Intent and creates a **Proposal**.
4. Both users **Confirm** the Proposal.
5. System creates a **Session** (CONFIRMED).
6. Users **Start** the Session (IN_PROGRESS).
7. Users **End** the Session (COMPLETED).

---

## C) State Machine (Formal Logic)

### Intent

- `OPEN` → `LOCKED` (when a Proposal is created that references it)
- `OPEN` → `CANCELLED`
- `LOCKED` → `CANCELLED` (optional V1; if allowed, must also cancel the linked Proposal/Session safely)

### Proposal

- `PROPOSED` → `CONFIRMED` (when both users confirm)
- `PROPOSED` → `CANCELLED` (by either user before full confirmation)
- `PROPOSED` → `EXPIRED` (lazy expiration based on `expires_at`)
- `CONFIRMED` → `CANCELLED` (optional V1; if allowed, must cancel/close Session correctly)

### Session

- `CONFIRMED` → `IN_PROGRESS`
- `IN_PROGRESS` → `COMPLETED`
- `CONFIRMED` → `CANCELLED` (optional V1)
- `IN_PROGRESS` → `CANCELLED` (optional V1)

> Rule of thumb: keep V1 minimal. Only include optional transitions if you can implement + test them reliably.

---

## D) Domain Invariants (Must NEVER be violated)

1. **One active intent per user** (at most one `OPEN`/`LOCKED` Intent per user).
2. **One active proposal per user** (at most one `PROPOSED`/`CONFIRMED` Proposal involving the user).
3. **One active session per user** (at most one `CONFIRMED`/`IN_PROGRESS` Session involving the user).
4. **Exclusive pairing**: one Intent cannot be locked by multiple Proposals.
5. **All state transitions go through the service layer** (no direct status writes from controllers/UI).
6. **Deterministic matching**: same inputs → same selection (given a stable ordering rule).

---

## E) DB Schema (Consistency Enforcement)

### Minimal tables

- `users`
- `intents`
- `proposals`
- `sessions`

### Intent (UPDATED)

Intent must store:

- **Flexible time window**: represent as an _effective interval_ `[window_start, window_end]`
- **Location**: lat/lng
- **Walk type**: exact match

**Recommended columns**

- `start_at` (preferred start time user selects)
- `flex_minutes` (allowed flexibility, e.g. 30 or 60)
- `window_start = start_at - flex_minutes`
- `window_end   = start_at + flex_minutes`

You can store either:

- **Option 1 (recommended for querying):** store `window_start`, `window_end` explicitly (plus `start_at`, `flex_minutes` for clarity)
- **Option 2:** store only `start_at`, `flex_minutes` and compute windows in query (more logic in code)

**Compatibility check (time overlap)**
Two intents A and B are time-compatible if:

- `A.window_start < B.window_end` AND `B.window_start < A.window_end`

**Other compatibility rules**

- `distance(A, B) <= min(A.radius_m, B.radius_m)`
- `walk_type` exact match
- candidates must be `OPEN` and belong to a different user

### Constraints (enforce invariants)

- Foreign keys + enum checks (`CHECK(status IN (...))`)
- Unique / partial unique indexes to enforce “one active per user”
- Transactional matching + unique constraints to prevent double booking

---

## F) Service Layer (Orchestration)

### MatchingService (V1)

**match(user_id):**

1. Load user’s **OPEN** Intent.
2. Query **OPEN** candidate intents that satisfy:
   - time overlap (windows)
   - distance <= min radius
   - same walk_type
3. Sort candidates by nearest distance (deterministic tie-breaker: e.g., `created_at`, then `id`).
4. In one DB transaction:
   - create `Proposal(PROPOSED, expires_at=now+TTL)`
   - lock both intents: `Intent.OPEN → Intent.LOCKED`

### SessionService (V1)

- `confirm(proposal_id, user_id)`:
  - record user confirmation
  - when both confirmed:
    - `Proposal.PROPOSED → CONFIRMED`
    - create `Session(CONFIRMED)`
- `start(session_id, user_id)` → `IN_PROGRESS`
- `complete(session_id, user_id)` → `COMPLETED`

### ExpirationPolicy (V1, lazy)

- On read/write of a `PROPOSED` proposal:
  - if `now > expires_at`: mark `EXPIRED` (and unlock intents if your design requires it)

---

## G) UI (Presentation of State)

V1 screens (minimal):

1. Create Intent (time + flexibility + location + walk type)
2. Match (trigger match + show result)
3. Proposal (confirm/cancel)
4. Session (start/end)

UI rule:

- UI renders based on `state` + `allowed_actions` returned by API
- UI does **not** implement business rules

---

## Notes on the Time Flexibility UX

You can support two presets:

- **±30 minutes**
- **±1 hour**

Implementation approach:

- user chooses `start_at` and `flex_minutes`
- backend stores and queries via `window_start/window_end` overlap

This keeps V1 simple and deterministic while still modeling “flexible time window” correctly.
