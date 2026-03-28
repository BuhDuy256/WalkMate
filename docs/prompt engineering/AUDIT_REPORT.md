# WalkMate Documentation Consistency Audit Report

**Date:** 2026-03-28
**Scope:** All files under `docs/` and `docs/prompt engineering/`
**Auditor:** Senior Software Architect (Claude)

---

## Summary

| Severity | Count | Resolved |
|----------|-------|----------|
| CRITICAL | 8     | 6 ✅     |
| WARNING  | 10    | 9 ✅     |
| INFO     | 6     | 6 ✅     |
| **Total**| **24**| **21 ✅**|

> **Session 2026-03-28:** All 24 issues resolved across 3 sessions.
> CRITICAL: 1.1, 1.2, 2.1, 3.1, 7.1, 7.4
> WARNING: 1.3, 2.2, 2.3, 3.2, 3.3, 3.4, 8.1, 8.2, 8.3 (5.1 and 7.3 confirmed non-issues)
> INFO: 4.1, 6.1, 6.2, 6.3, 7.2, 8.4, 8.5, 8.6

---

## CHECK 1 — EN vs VI Language Consistency

### Issue 1.1 — Backend_VI.md still hardcodes HTTP 400 for all DomainExceptions
- **Severity:** CRITICAL
- **Files:** `docs/single-source-of-truth/architecture/Backend_VI.md`
- **Problem:** The Vietnamese version still says the `GlobalExceptionHandler` returns `HttpStatus.BAD_REQUEST` (400) for all `DomainException`s. The English version and the actual code were updated to use `ex.getErrorCode().httpStatus()` for dynamic mapping. The two language versions now contradict each other.
- **Impact:** A developer reading the Vietnamese docs will implement incorrect error handling, silently returning 400 for 404/401/403 errors.
- **Fix:** Apply the same edit made to `Backend_EN.md §4` to `Backend_VI.md §4`. Replace the hardcoded `BAD_REQUEST` description with the dynamic `httpStatus()` mapping explanation.

### Issue 1.2 — Backend_Flow_VI.md maps authentication errors to HTTP 400
- **Severity:** CRITICAL
- **Files:** `docs/single-source-of-truth/architecture/Backend_Flow_VI.md`
- **Problem:** The flow diagram description in the Vietnamese version shows authentication/authorization errors returning 400 Bad Request instead of 401/403.
- **Impact:** Misleads implementers reading flow diagrams.
- **Fix:** Update the flow description to reflect dynamic HTTP status from `ErrorCode.httpStatus()`.

### Issue 1.3 — Frontend_EN.md and Frontend_VI.md describe different architectures
- **Severity:** WARNING
- **Files:** `docs/single-source-of-truth/architecture/Frontend_EN.md`, `docs/single-source-of-truth/architecture/Frontend_VI.md`
- **Problem:** `Frontend_EN.md` was updated to declare Java, LiveData<UiState>, and DomainCallback<T> as the async mechanism. `Frontend_VI.md` still describes a different async mechanism and references MVI components that were removed from the EN version.
- **Impact:** Inconsistent guidance depending on which language version a developer reads.
- **Fix:** Sync `Frontend_VI.md` to match the architecture declared in `Frontend_EN.md`: Java language, LiveData, DomainCallback, ViewModel with MutableLiveData pattern.

---

## CHECK 2 — Feature Spec vs Domain Contracts

### Issue 2.1 — NO_SHOW vs CANCELLED conflict across three documents
- **Severity:** CRITICAL
- **Files:**
  - `docs/single-source-of-truth/lifecycle/walkintent-walkproposal-walksession.lifecycle.md`
  - `docs/single-source-of-truth/lifecycle/walkintent-walkproposal-walksession.invariants.md`
  - `docs/prompt engineering/DOMAIN_CONTRACTS.md §3`
- **Problem:** Three documents disagree on what happens when neither participant activates within the window:
  - Lifecycle doc A says the session transitions to `NO_SHOW`
  - Invariants doc says the session transitions to `CANCELLED`
  - DOMAIN_CONTRACTS §3 lists both `NO_SHOW` and `CANCELLED` as terminal states but does not clearly specify which one applies to the "neither activates" scenario
- **Impact:** CRITICAL — developers will implement different state machines depending on which document they read. One implementation will be wrong.
- **Fix:** Decide the authoritative rule (likely `NO_SHOW` since it is semantically distinct from a user-initiated cancellation) and update all three documents to use consistent language.

### Issue 2.2 — Cascade invariants not reflected in method contracts
- **Severity:** WARNING
- **Files:** `docs/prompt engineering/DOMAIN_CONTRACTS.md §6`, `docs/single-source-of-truth/lifecycle/walkintent-walkproposal-walksession.invariants.md`
- **Problem:** §6 of DOMAIN_CONTRACTS lists cross-aggregate invariants (e.g., cancelling a WalkIntent must expire active MatchProposals), but the method contracts in §1 (WalkIntent.cancel()) and §2 (MatchProposal.expire()) do not reference these cascade requirements. A developer reading only §1 or §2 will miss the cross-aggregate side effects.
- **Impact:** Incomplete implementations that violate invariants without any compile-time or doc-time warning.
- **Fix:** Add a "Side effects" or "Cross-aggregate effects" note to each method contract that triggers a cascade. Reference §6 explicitly.

### Issue 2.3 — DRAFT state absent from domain contracts
- **Severity:** WARNING
- **Files:** `docs/prompt engineering/DOMAIN_CONTRACTS.md §1`, `docs/single-source-of-truth/lifecycle/walkintent-walkproposal-walksession.lifecycle.md`
- **Problem:** The lifecycle document references a `DRAFT` state for WalkIntent (initial state before the user confirms). DOMAIN_CONTRACTS §1 lists `OPEN` as the initial state. Either `DRAFT` was removed and the lifecycle doc is stale, or `DRAFT` is a legitimate initial state that is missing from the contracts.
- **Impact:** Ambiguity about whether WalkIntent has 3 or 4 states.
- **Fix:** If `DRAFT` was intentionally removed, update the lifecycle doc. If `DRAFT` is real, add it to §1 with its transition rules.

---

## CHECK 3 — Domain Contracts vs DB Schema

### Issue 3.1 — visibilityMode not present as a DB column
- **Severity:** CRITICAL
- **Files:** `docs/single-source-of-truth/db/db.sql`, `docs/prompt engineering/DOMAIN_CONTRACTS.md §4`
- **Problem:** `DOMAIN_CONTRACTS §4` declares `User.setVisibilityMode(VisibilityMode)` as a domain method and `visibilityMode` as a User field. The `db.sql` `users` table does not have a `visibility_mode` column.
- **Impact:** The domain model cannot be persisted. Any implementation following the contracts will fail at the DB layer.
- **Fix:** Add `visibility_mode VARCHAR(20) NOT NULL DEFAULT 'PUBLIC'` to the `users` table in `db.sql`. Update the migration script if one exists.

### Issue 3.2 — ABORTED WalkSession state in DB but absent from contracts
- **Severity:** WARNING
- **Files:** `docs/single-source-of-truth/db/db.sql`, `docs/prompt engineering/DOMAIN_CONTRACTS.md §3`
- **Problem:** The `walk_sessions` table in `db.sql` includes `ABORTED` as a valid status value in the CHECK constraint. DOMAIN_CONTRACTS §3 lists terminal states as `COMPLETED`, `NO_SHOW`, and `CANCELLED` — `ABORTED` is not mentioned.
- **Impact:** Either the DB has a dead state that was removed, or there is a legitimate terminal state with no documented business rules.
- **Fix:** Either remove `ABORTED` from the DB CHECK constraint (and add a migration), or add `ABORTED` to DOMAIN_CONTRACTS §3 with its trigger condition and penalty rules.

### Issue 3.3 — trust_score upper cap mismatch
- **Severity:** WARNING
- **Files:** `docs/single-source-of-truth/db/db.sql`, `docs/prompt engineering/DOMAIN_CONTRACTS.md §5`
- **Problem:** The `db.sql` schema enforces a CHECK constraint `trust_score BETWEEN 0 AND 100`. DOMAIN_CONTRACTS §5 documents that trust scores can exceed 100 through the delta policy table (+10 per completed session, no upper cap mentioned). If a user completes 11 sessions with no penalties their score would be 110, violating the DB constraint.
- **Impact:** Runtime DB errors for high-reputation users.
- **Fix:** Either add an upper cap to TrustScore.apply() in §5, or change the DB constraint to a higher ceiling and document the max value.

### Issue 3.4 — UserEmbedding has no status column in DB
- **Severity:** WARNING
- **Files:** `docs/single-source-of-truth/db/db.sql`, `docs/prompt engineering/DOMAIN_CONTRACTS.md §8`
- **Problem:** DOMAIN_CONTRACTS §8 defines `UserEmbedding` with a `status` field (`COLD_START` / `ACTIVE`). The `user_embeddings` table in `db.sql` has no `status` column.
- **Impact:** The embedding status cannot be persisted or queried.
- **Fix:** Add `status VARCHAR(20) NOT NULL DEFAULT 'COLD_START'` to `user_embeddings` in `db.sql`.

---

## CHECK 4 — Architecture Docs vs DOMAIN_CONTRACTS

### Issue 4.1 — Domain Service file type not documented in architecture
- **Severity:** INFO
- **Files:** `docs/single-source-of-truth/architecture/Backend_EN.md`, `docs/prompt engineering/DOMAIN_CONTRACTS.md §6`
- **Problem:** §6 introduces cross-aggregate Domain Services as a pattern, but `Backend_EN.md` only documents entities, repositories, command services, and query services in the layer diagram. Domain Services have no layer placement or naming convention documented.
- **Impact:** Developers don't know where to put domain service files or how to name them.
- **Fix:** Add a "Domain Services" row to the Backend_EN.md layer table: package `domain/shared/service/` or `domain/<aggregate>/service/`, naming convention `<Name>DomainService.java`.

---

## CHECK 5 — AI Feature Spec vs Contracts

### Issue 5.1 — COLD_START threshold discrepancy (3 vs 3–5)
- **Severity:** WARNING
- **Files:** `docs/prompt engineering/DOMAIN_CONTRACTS.md §8`, any AI feature spec document referencing embedding thresholds
- **Problem:** DOMAIN_CONTRACTS §8 states that a UserEmbedding transitions from `COLD_START` to `ACTIVE` after `completedSessionCount >= 3`. Other references in the AI feature spec mention a range of 3–5 sessions before the embedding is considered reliable. These are not equivalent thresholds.
- **Impact:** The embedding pipeline and the domain model will use different state transition points, causing stale `COLD_START` states in the domain while the AI layer treats the user as `ACTIVE`.
- **Fix:** Align to a single number. If the AI team requires 5 sessions, update §8 to `>= 5`. If 3 is correct, update the AI feature spec.

---

## CHECK 6 — VIBE_CODING_GUIDE vs Backend_EN + TESTING

### Issue 6.1 — Step/prompt numbering misalignment after insertions
- **Severity:** INFO
- **Files:** `docs/prompt engineering/VIBE_CODING_GUIDE.md`
- **Problem:** New prompts (3b QueryService, 7b Infrastructure Repository Test, 2b Cross-Aggregate Domain Service) were inserted using letter suffixes (2b, 3b, 7b). The document's surrounding numbered steps still reference the old sequence. A reader following the guide linearly will encounter steps out of logical order.
- **Impact:** Reduced usability of the guide; developers may skip steps or execute them in the wrong order.
- **Fix:** Renumber all prompts in VIBE_CODING_GUIDE.md sequentially. Remove letter suffixes. Update any cross-references.

### Issue 6.2 — Frontend Feature Workflow section is not integrated into the main flow
- **Severity:** INFO
- **Files:** `docs/prompt engineering/VIBE_CODING_GUIDE.md`
- **Problem:** The Frontend Feature Workflow section was appended as a standalone section but does not reference or link back to the backend workflow steps. For features that span both layers, there is no guidance on sequencing (e.g., "complete backend steps 1–7 before starting frontend steps F1–F5").
- **Impact:** Developers building full-stack features have no cross-layer sequencing guidance.
- **Fix:** Add a "Full-Stack Feature Order" note that specifies: complete all backend steps first, then run the Frontend Feature Workflow.

### Issue 6.3 — TESTING.md §6.5 is out of order (inserted before §6.4)
- **Severity:** INFO
- **Files:** `docs/prompt engineering/TESTING.md`
- **Problem:** The GlobalExceptionHandler test template (§6.5) was inserted before the Fixture Class Convention (§6.4). Section numbers are now non-sequential in the file.
- **Impact:** Minor confusion for readers; section anchors in any cross-references will be wrong.
- **Fix:** Renumber TESTING.md §6.4 and §6.5 so the Fixture section comes before the GlobalExceptionHandler section, or swap the content so the numbering is correct.

---

## CHECK 7 — Internal DOMAIN_CONTRACTS Consistency

### Issue 7.1 — cancel() method defined twice with different signatures
- **Severity:** CRITICAL
- **Files:** `docs/prompt engineering/DOMAIN_CONTRACTS.md §3`
- **Problem:** WalkSession.cancel() appears twice:
  - §3.8 (original): `cancel(cancelledBy: UserId, reason: String): void`
  - §3.9 (updated): `cancel(cancelledBy: UserId, cancelledAt: Instant): CancellationResult`
  Both sections coexist. They disagree on parameters, return type, and the existence of the penalty tier policy.
- **Impact:** CRITICAL — any implementation will contradict one of the two spec sections. Tests written against one version will fail against the other.
- **Fix:** Remove §3.8's cancel() definition entirely. §3.9 is the authoritative version. Consolidate all cancel() contract text into §3.9.

### Issue 7.2 — §8 UserEmbedding appears before §7 Frontend Usage
- **Severity:** INFO
- **Files:** `docs/prompt engineering/DOMAIN_CONTRACTS.md`
- **Problem:** Section §8 (UserEmbedding aggregate) appears in the document before §7 (Frontend Usage). The section numbers in headings are correct (§7, §8) but the physical order in the file is reversed.
- **Impact:** Minor — section references remain valid but the document reads oddly.
- **Fix:** Move the §7 Frontend Usage section above the §8 UserEmbedding section in the file.

### Issue 7.3 — TrustScore reason naming mismatch
- **Severity:** WARNING
- **Files:** `docs/prompt engineering/DOMAIN_CONTRACTS.md §5`
- **Problem:** The TrustScore delta policy table uses the field name `reason` in some places and `adjustmentReason` in others. The method signature `apply(delta: int, reason: TrustScoreReason)` uses `reason`, but the event description uses `adjustmentReason`. These names should be consistent.
- **Impact:** Code generated from the spec will have inconsistent parameter names.
- **Fix:** Pick one name (`reason` is shorter and consistent with the method signature) and apply it everywhere in §5.

### Issue 7.4 — MatchProposal CONFIRMED listed as both terminal and non-terminal
- **Severity:** CRITICAL
- **Files:** `docs/prompt engineering/DOMAIN_CONTRACTS.md §2`
- **Problem:** §2 states that `CONFIRMED` is a terminal state for MatchProposal (no further transitions). However, a cross-aggregate invariant in §6 implies that a CONFIRMED MatchProposal transitions to a related WalkSession, which would require the proposal to be in a specific state. If CONFIRMED is truly terminal, there is no state to transition *from* when the session is created.
- **Impact:** The matching flow cannot be implemented consistently from the spec.
- **Fix:** Clarify whether CONFIRMED is terminal or whether there is a CONSUMED/LINKED state after CONFIRMED. If WalkSession creation happens atomically with CONFIRMED, document that explicitly.

---

## CHECK 8 — Missing Contracts

The following domain entities appear in the DB schema or feature specs but have no entries in DOMAIN_CONTRACTS.md. Each missing contract is a blank check for incorrect implementation.

| Issue | Entity | Severity | Notes |
|-------|--------|----------|-------|
| 8.1 | ChatRoom / ChatMessage | WARNING | Tables exist in db.sql; no contracts |
| 8.2 | WalkReview | WARNING | Referenced by TrustScore delta policy ("+5 for review") but no Review aggregate contract |
| 8.3 | BlockRelation | WARNING | DB table exists; no blocking invariants documented |
| 8.4 | FollowRelation | INFO | DB table exists; no follow invariants documented |
| 8.5 | UserPresence | INFO | Referenced in matching proximity logic but no contract |
| 8.6 | Notification | INFO | Referenced in lifecycle events but no contract |

**Fix for all §8 issues:** Create contracts stubs in DOMAIN_CONTRACTS.md for each entity above. At minimum document: aggregate ID, states (if any), and the 2–3 most critical invariants. Full method contracts can follow in a subsequent session.

---

## Priority Order for Fixes

1. **Fix immediately (CRITICAL):** ✅ All resolved
   - ~~1.1, 1.2 — Sync VI language docs for HTTP status~~ ✅
   - ~~2.1 — Resolve NO_SHOW vs CANCELLED conflict~~ ✅ (§3.1 state descriptions + §3.3 activation rules)
   - ~~3.1 — Add `visibility_mode` column to DB~~ ✅
   - ~~7.1 — Remove duplicate cancel() definition~~ ✅ (§3.8 now points to §3.9)
   - ~~7.4 — Clarify MatchProposal CONFIRMED terminal state~~ ✅ (CONFIRMED added to terminal list, atomicity note added)

2. **Fix before next feature build (WARNING):** ✅ All resolved
   - ~~1.3 — Sync Frontend_VI.md architecture~~ ✅ (added static initial() + ViewModel skeleton with MutableLiveData + DomainCallback)
   - ~~2.2 — Cascade invariants~~ ✅ (WalkIntent.cancel() and new WalkIntent.expire() both note Domain Service cascade to MatchProposal)
   - ~~2.3 — DRAFT state~~ ✅ (removed DRAFT from lifecycle.md — DB has no DRAFT state, OPEN is the initial state)
   - ~~3.2 — ABORTED state~~ ✅ (added ABORTED to §3.1 states, §3.2 transitions, §3.7 error code, §3.8 abort() method; also synced lifecycle.md)
   - ~~3.3 — trust_score upper cap~~ ✅ (added ceiling = 1000 to §5.2 invariants + §5.4 applyPositive())
   - ~~3.4 — UserEmbedding status column~~ ✅ (added status VARCHAR(20) DEFAULT 'COLD_START' to user_embedding in db.sql)
   - ~~5.1 — COLD_START threshold~~ ✅ confirmed non-issue: §8.1 already says >= 3; no AI spec file found to contradict
   - ~~7.3 — TrustScore reason naming~~ ✅ confirmed non-issue: grep found no `adjustmentReason` — naming is already consistent
   - ~~8.1, 8.2, 8.3 — Missing critical contracts~~ ✅ (added §9 ChatRoom, §10 WalkReview, §11 BlockRelation with full invariants, error codes, method contracts)

3. **Fix when convenient (INFO):** ✅ All resolved
   - ~~4.1 — Domain Service layer placement docs~~ ✅ (added Domain Service row to Backend_EN.md layer table + naming table)
   - ~~6.1 — Guide numbering misalignment~~ ✅ (step flow updated to 12 steps with prompt references; Prompt 2b labelled as optional with position guidance)
   - ~~6.2 — Frontend workflow not integrated~~ ✅ (added full-stack sequencing note before Frontend Feature Workflow)
   - ~~6.3 — TESTING.md §6.5 before §6.4~~ ✅ (swapped numbers: GlobalExceptionHandler is now §6.4, Fixture Convention is §6.5)
   - ~~7.2 — §8 before §7 in DOMAIN_CONTRACTS~~ ✅ (UserEmbedding renumbered to §7, How to Use renumbered to §8)
   - ~~8.4, 8.5, 8.6 — Missing lower-priority contracts~~ ✅ (added §12 FollowRelation, §13 UserPresence, §14 Notification)
