# ADR-001: MongoDB Atlas for Chat Room Lifecycle

**Date:** 2026-04-07  
**Status:** Accepted  
**Deciders:** BuhDuy

---

## Context

WalkMate requires a chat room scoped to each WalkSession (invariant P-3, step 3). The relational
SQL chat tables were removed in migration V101. The target store for chat rooms is MongoDB Atlas,
keyed by `session_id`. The primary database is PostgreSQL (Supabase), managed via Spring JDBC
and the `DataSourceTransactionManager`. A chat room must be created exactly once when a
`WalkSession` is confirmed, and must be write-locked when the session reaches any terminal state
(`COMPLETED`, `CANCELLED`, `ABORTED`, `NO_SHOW`).

The core tension: MongoDB and PostgreSQL cannot participate in a single distributed transaction
without a coordinator (e.g., 2PC or a `ChainedTransactionManager`), both of which introduce
unacceptable complexity or availability risk for this use case.

---

## Decision

MongoDB is treated as a **derived, eventually-consistent store**. PostgreSQL (Supabase) is the
sole Source of Truth for session state.

MongoDB writes (`initRoom`, `closeRoom`) are dispatched via
`TransactionSynchronizationManager.registerSynchronization()` `afterCommit()` hooks registered
inside the PostgreSQL `@Transactional` methods of `MatchingCommandService` and
`SessionCommandService`. This guarantees:

1. If the PostgreSQL transaction rolls back, the `afterCommit()` callback never fires — MongoDB
   is never written.
2. If the PostgreSQL transaction commits but the MongoDB write fails, the `WalkSession` row in
   PostgreSQL remains valid. The chat room document is absent, but the session itself is
   consistent. A future reconciliation job can detect and repair this gap.

No `MongoTransactionManager` bean is registered. Spring `@Transactional` continues to manage
only the `DataSourceTransactionManager`. MongoDB operations are non-transactional at the Spring
level; document-level atomicity is handled by MongoDB itself.

`MongoChatRoomRepository.initRoom()` uses upsert with `$setOnInsert` semantics, making it safe
for retries — calling it twice for the same `sessionId` is a no-op.

---

## Transaction Boundary

The `afterCommit()` hook is registered **inside** the `@Transactional` method boundary but fires
**outside** of it — after the JDBC transaction has been durably committed to PostgreSQL. This is
the only safe insertion point: registering before commit risks writing to MongoDB for a
transaction that subsequently rolls back; registering outside the transactional method loses the
ability to observe the committed `sessionId`.

**Failure consequence:** If the `afterCommit` MongoDB write fails (network partition, Atlas
unavailability), the `WalkSession` row exists in PostgreSQL and is fully valid. Users can still
see their session, activate it, and complete it. The only degraded capability is chat — the room
document is absent, so the mobile client cannot send messages. A reconciliation job (not yet
implemented) can query for `WalkSession` rows that have no corresponding `chat_rooms` document
and call `initRoom()` for each, healing the gap without any data loss.

---

## Alternatives Considered

### Option A: Write MongoDB inside the `@Transactional` boundary (rejected)
Registering a `MongoTransactionManager` and chaining it with `DataSourceTransactionManager` via
`ChainedTransactionManager` would allow both writes inside a single `@Transactional` block.
**Rejected** because `ChainedTransactionManager` does not provide true 2PC — if the MongoDB
commit succeeds but the PostgreSQL commit fails, MongoDB is left with a dangling document for a
session that does not exist. This is worse than the accepted failure mode (session exists, chat
absent), as it creates phantom chat rooms.

### Option B: Outbox pattern with a separate relay process (deferred)
Write a `chat_room_outbox` row inside the PostgreSQL transaction; a separate relay process reads
the outbox and writes to MongoDB. This provides stronger delivery guarantees but introduces a
relay service, additional infrastructure, and polling complexity. Deferred to a future phase
given the current scale requirements.

### Option C: Synchronous MongoDB write outside any transaction (rejected)
Calling `initRoom()` after the transactional method returns but within the same request thread
means a PostgreSQL rollback (e.g., from an optimistic lock conflict on the session save) would
still trigger the MongoDB write. **Rejected** because it violates the "only write MongoDB if
PostgreSQL committed" invariant.

---

## Consequences

### What becomes easier
- Chat room lifecycle is automatically synchronized with session lifecycle via service-layer hooks
  — no call-site discipline required across controllers.
- `initRoom()` idempotency means any retry of `acceptProposal()` (after a transient failure) is
  safe — the second call upserts to the same document without duplication.
- No MongoDB-specific types leak into the domain or application layers — the `ChatRoomRepository`
  port is a plain Java interface; swapping the backing store requires only a new adapter.

### New failure modes and mitigations
| Failure | Observable symptom | Mitigation |
|---|---|---|
| MongoDB write fails after PostgreSQL commit | `WalkSession` exists; no chat room document | Log ERROR; reconciliation job repairs gap |
| MongoDB unavailable at startup | `BeanCreationException` on `MongoAutoConfiguration` | Fail-fast is intentional — chat is a core feature |
| Duplicate `afterCommit` registration (retry storm) | `initRoom()` called twice for same sessionId | Upsert + `$setOnInsert` makes this a no-op |
| `closeRoom()` fails for terminal session | Chat room stays OPEN in MongoDB; S-7 write-lock not enforced | Log ERROR; reconciliation job closes dangling rooms |
