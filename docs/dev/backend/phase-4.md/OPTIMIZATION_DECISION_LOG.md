# Phase 4 Optimization Decision Log

## OPT-4-001 — Pessimistic Locking over Optimistic Locking for P-3

**Decision:** Use `SELECT … FOR UPDATE` (pessimistic locking) for the session-creation critical section rather than optimistic locking with retry.

**Alternatives considered:**
- *Optimistic locking (version column + retry):* Would expose retryable `OptimisticLockingFailureException` to the caller, requiring the service to catch, re-read, and retry — adding latency and complexity in the P-3 hot path.
- *Pessimistic locking (chosen):* Blocks the second transaction at the DB level; the first transaction completes and the second re-reads the already-committed state, sees `CONSUMED`, and surfaces a clean `PROPOSAL_INTENT_NO_LONGER_OPEN` domain error with no retry loop.

**Why this is better here:** Contention on any single intent pair is inherently rare (O(1) concurrent accepts per proposal). The pessimistic lock hold time is short (read → consume → save → commit, all in-memory). The UX benefit — a deterministic error instead of a silent retry — outweighs the marginal throughput cost.

---

## OPT-4-002 — Lexicographic Deadlock Prevention

**Decision:** When locking two intent rows for the P-3 accept protocol, always lock the row with the lexicographically smaller `intentId` first.

**Problem:** If user A accepts proposal P (locking intentA then intentB) while user B concurrently accepts proposal Q (locking intentB then intentA), a classic AB/BA deadlock results.

**Solution:** Both transactions compute `min(intentIdA, intentIdB)` and lock that row first. Since both transactions agree on the ordering, neither can form a cycle.

**Tradeoff:** Requires a deterministic ordering contract between any two code paths that lock intent rows. Documented here so future developers do not introduce a path that locks intents in a different order.

---

## OPT-4-003 — Partial Unique Index for Duplicate Proposal Prevention

**Decision:** Use a PostgreSQL partial unique index to prevent duplicate PENDING proposals for the same intent pair:

```sql
CREATE UNIQUE INDEX idx_match_proposal_unique_pending_pair
    ON match_proposal (LEAST(intent_id_a::text, intent_id_b::text),
                       GREATEST(intent_id_a::text, intent_id_b::text))
    WHERE status = 'PENDING';
```

**Problem solved:** Two concurrent `findOrCreateProposal` calls for the same intent could both pass the "existing proposal?" check and both attempt an INSERT.

**Why partial index:** Once a proposal is CONFIRMED or REJECTED, the pair should be free to match again (e.g., if they re-open intents). A full unique index would block this. The `WHERE status='PENDING'` clause makes the constraint active only while a proposal is live.

**Application behaviour:** The duplicate INSERT gets a unique-constraint violation from PostgreSQL. `MatchingCommandService` catches `DataIntegrityViolationException`, re-queries for the now-existing proposal, and returns it — so the caller always gets a valid proposal with no visible error.

---

## OPT-4-004 — `DomainCallback<WalkProposal>` for `findMatch`

**Decision:** Change `WalkIntentRepository.findMatch` callback type from `DomainCallback<WalkIntent>` to `DomainCallback<WalkProposal>`.

**Reason:** The `GET /intents/{id}/match` endpoint no longer returns intent data — it runs the matching engine and returns the resulting proposal (or 204 if none). Keeping `DomainCallback<WalkIntent>` would require artificially projecting proposal data back into a `WalkIntent` shape, losing precision and breaking the domain model.

**Tradeoff:** Any existing call site using the old signature must be updated. At the time of this change, no ViewModel or Fragment called `findMatch` yet, so the migration cost was zero.

---

## OPT-4-005 — `onSuccess(null)` for Partial Accept

**Decision:** When `acceptProposal` returns a PENDING proposal (only one participant has accepted), `WalkProposalRepositoryImpl` calls `callback.onSuccess(null)` rather than `callback.onError(...)`.

**Reasoning:** A partial accept is not an error — it is a valid intermediate state. Routing it through `onError` would force every ViewModel to inspect the exception message to distinguish "real error" from "waiting state". Using `onSuccess(null)` lets the ViewModel use a simple null-check to branch into a "waiting for partner" UI state.

**Contract:** Callers of `acceptProposal` must handle `null` in the success path. This is documented in `WalkProposalRepositoryImpl` and in `HANDOFF_PHASE_4.md`.

---

## OPT-4-006 — Placeholder Values for Missing Profile Fields

**Decision:** `WalkProposalMapper.toDomain()` populates `matchedUserName`, `matchedUserAge`, `trustScore`, and `overlappingTags` with safe defaults (`userId`, `0`, `0`, empty list) rather than making a second API call.

**Reason:** No user-profile endpoint exists in Phase 4. Making a secondary `GET /users/{id}` call inside the mapper would introduce hidden I/O, complicate error handling, and violate single-responsibility. These fields are display-only; defaulting them allows the UI to compile and run while the profile API is planned for Phase 5.

**Risk:** If a UI component renders `matchedUserName` without a null/zero guard, it will display the raw userId string. Acceptable for a pre-release build; tracked as a known gap in `HANDOFF_PHASE_4.md`.
