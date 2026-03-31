# Optimization & Decision Log — Phase 3

- **Decision 1:** Used `ALTER TYPE intent_status ADD VALUE IF NOT EXISTS 'CONSUMED'` rather than recreating the enum.
  PostgreSQL does not support removing values from an enum type without a full drop-and-recreate. Dropping the type requires dropping all columns that reference it — which cascades to dropping `walk_intent` and its foreign key from `match_proposal`. An additive `ALTER TYPE` is the only safe path on a live database. `MATCHED` stays in the PG type forever; it is `@Deprecated` in Java so it can never be written by new code.

- **Decision 2:** Overlap check uses `status IN ('OPEN', 'CONSUMED')`, not just `OPEN`.
  A user who has been matched (CONSUMED) for a given window should not be able to create a second intent for the same window — they already have a walk commitment. Checking only `OPEN` would allow double-booking between the moment a match is confirmed and the walk actually starts.

- **Decision 3:** `toInstant` uses `Math.round(hourFloat * 60)` to convert fractional hours to integer minutes.
  Direct casting (`(int)(17.5 * 60) = 1049` due to float precision vs. the expected `1050`) produces off-by-one errors. `Math.round` pins floats like `17.5`, `8.25`, `18.75` to the nearest minute correctly regardless of IEEE-754 representation.

- **Decision 4:** `cancelIntent` was changed from `cancelIntent(intentId)` to `cancelIntent(intentId, callerId)`.
  The controller is the only caller and always has `principal.userId()` from the verified JWT. Passing `callerId` into the service keeps the ownership check inside the transaction and eliminates any risk of a race between "load intent" and "verify owner" in separate layers.

- **Decision 5:** `GET /api/v1/intents` returns only `OPEN` intents, not all statuses.
  The endpoint name is `listActiveIntents`. Returning cancelled or expired intents would clutter the UI list and expose history that belongs in a separate audit/history endpoint. If history is needed it can be added as `GET /api/v1/intents?status=CANCELLED` in a later phase without breaking this contract.

- **Decision 6:** `CreateWalkIntentRequest` switched from `Instant timeWindowStart/End` to `date + time_start + time_end` floats.
  The frontend domain model uses fractional hours (`16.5` = 16:30) — the native way Vietnamese users express time slots. Accepting `Instant` on the server would force the frontend to reconstruct ISO-8601 strings and expose timezone handling to the client. With `date + float`, the server owns the timezone conversion in one place (`toInstant()` in the controller, `Asia/Ho_Chi_Minh`), and the client speaks its natural language.

- **Decision 7:** `findMatch()` is left as a mock stub in `WalkIntentRepositoryImpl` with an explicit comment.
  The matching infrastructure (strategy, JDBC query, scoring) is already built on the backend. However, the proposal lifecycle (create, accept, expire) is not yet wired. Wiring only half of the matching flow would leave the frontend in an inconsistent state. A clean Phase 4 boundary is better than a partial implementation that silently drops proposals.

- **Decision 8:** `WalkIntent.cancel()` guards against both `CONSUMED` and legacy `MATCHED` status.
  ```java
  if (this.status == IntentStatus.CONSUMED || this.status == IntentStatus.MATCHED) { ... }
  ```
  During the transition window, a database row could still carry `MATCHED` (if the V6 migration was skipped or the row predates it). The dual guard makes cancel idempotently correct regardless of which enum value is stored, with zero performance cost.

- **Decision 9:** `findOpenByUserId` orders results by `created_at DESC`.
  The most recently created intent is most likely the one the user is actively waiting on. Showing it first reduces the number of taps needed to find the active intent in the UI list.

- **Decision 10:** `hasOverlappingActiveIntent` is a dedicated boolean query rather than loading the list and checking in Java.
  A full `SELECT *` to check overlap would transfer unnecessary row data and risk TOCTOU if the caller checked the list before calling `save`. A focused `SELECT COUNT(*)` with DB-level predicate is atomic within the same transaction and returns in O(index scan) time regardless of how many intents the user has.
