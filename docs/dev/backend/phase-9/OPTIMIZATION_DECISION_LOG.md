# Optimization Decision Log — Phase 9

## Decision 1: Single `@Transactional` Boundary for Review + Trust Score Update

**Context:** Submitting a review and updating the reviewee's trust score are two separate write operations (INSERT into `walk_review`, UPDATE `user_account.trust_score`). Either both must succeed or neither must.

**Decision:** Both operations execute inside a single `@Transactional` method in `ReviewCommandService.submitReview()`.

**Rationale:**
- Spring's `@Transactional` on a public service method creates a single database transaction. Both the `walk_review` INSERT and the `user_account` UPDATE are issued to the same connection; if either fails (e.g., a constraint violation or an optimistic lock failure), the entire transaction rolls back.
- Alternative considered: two separate transactions (save review first, then update score). This creates a window where a review exists but the score is not yet updated. If the score update fails, the review is orphaned and the user could re-submit, hitting the `REVIEW_ALREADY_SUBMITTED` guard and never getting their score updated — a silent data loss bug.
- Another alternative: an outbox/saga pattern. This is correct at distributed-systems scale but is vastly over-engineered for a single-PostgreSQL deployment.

**Result:** Atomic commit of both rows. No partial state is ever visible to any query.

---

## Decision 2: `TrustScorePolicy` as a Pure Static Domain Class

**Context:** The score bounding logic (`MIN_SCORE = 0`, `MAX_SCORE = 1000`) could live in the entity, the service, or a Spring `@Component`.

**Decision:** `TrustScorePolicy` is a `final` utility class with one static method and two public constants. It has no Spring annotations and no dependencies.

**Rationale:**
- The policy is pure business logic — it belongs in the domain layer, not the application layer.
- Making it a Spring `@Component` would add injection boilerplate with no benefit.
- A pure static method is trivially unit-testable with zero mocking.
- The `User.applyTrustScore(int boundedScore)` contract enforces that only pre-bounded values enter the entity, so any caller that skips `TrustScorePolicy` would be compelled to produce a correctly bounded value themselves.

---

## Decision 3: `User.applyTrustScore(int boundedScore)` Instead of `adjustTrustScore(int delta)`

**Context:** The entity could expose `adjustTrustScore(int delta)` (applies delta internally and clamps) or `applyTrustScore(int boundedScore)` (accepts pre-computed value).

**Decision:** `applyTrustScore(int boundedScore)` — the entity accepts an already-bounded value.

**Rationale:**
- Embedding the clamping in the entity would duplicate the `MIN/MAX` constants between `User` and `TrustScorePolicy`, creating a synchronisation hazard.
- The service is responsible for reading `TrustScorePolicy.apply()` and then calling `user.applyTrustScore(newScore)`. This keeps the entity as a simple state container and the policy as the single source of truth for the formula.
- Future callers (e.g., a scheduler that penalises no-shows) use the same `TrustScorePolicy.apply()` path, ensuring consistent bounding everywhere.

---

## Decision 4: `REVIEW_ALREADY_SUBMITTED` Checked in Application AND Enforced by DB Constraint

**Context:** The unique constraint on `(session_id, reviewer_id)` in `walk_review` prevents duplicate rows at the database level. The application service also checks `existsBySessionAndReviewer` before inserting.

**Decision:** Both layers enforce uniqueness.

**Rationale:**
- **Application check first:** Produces a clean, domain-typed `DomainException` (→ 400 `REVIEW_ALREADY_SUBMITTED`) instead of a raw PostgreSQL `unique_violation` (→ 500 from the generic exception handler). This gives the client a meaningful error code.
- **DB constraint as last resort:** If two concurrent requests somehow pass the application check simultaneously (unlikely but theoretically possible at high concurrency), the DB constraint aborts the second INSERT and rolls the transaction back — no duplicate review is ever committed.
- **No performance concern:** The `existsBySessionAndReviewer` query is a single-row COUNT on an indexed pair; it adds negligible overhead.

---

## Decision 5: `GET /api/v1/users/{userId}/reviews` is Public

**Context:** Reviews for a user could be restricted to authenticated callers only, or made public for profile browsing.

**Decision:** This endpoint is `permitAll()` in `SecurityConfig`.

**Rationale:** A user's reviews are their public reputation data (analogous to eBay/Airbnb seller reviews). Anonymous visitors viewing a walker's profile page should be able to see their reviews without logging in. Sensitive fields (reviewer_id) are already just UUIDs with no personal data. This aligns with the Phase 2 decision to make hotspot catalogue public.
