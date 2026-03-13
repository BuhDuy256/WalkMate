# WalkMate Required Invariants

This document serves as the absolute Single Source of Truth (SSOT) for the invariants governing the WalkMate system domains. These business rules maintain domain integrity and must be strictly enforced.

## 1. WalkIntent Invariants

*   **I-1 Overlapping OPEN Intents Forbidden:** A user cannot have multiple WalkIntents in the `OPEN` state with overlapping time windows. This must be enforced at the database level.
*   **I-2 Intent Eligibility for Proposal:** A MatchProposal requires exactly two existing WalkIntents that are in the `OPEN` state, have overlapping time windows, and have compatible locations.
*   **I-3 Intent Consumption is Exclusive:** A WalkIntent transitions from `OPEN` to `CONSUMED` exactly once. A `CONSUMED` intent cannot be reopened, participate in another proposal, or create another session.
*   **I-4 Intent Expiry Cascade:** When a WalkIntent transitions to `EXPIRED`, all related `MatchProposal` instances in the `PENDING` state automatically transition to `EXPIRED`.
*   **I-5 Terminal State Immutability:** The states `CONSUMED`, `CANCELLED`, and `EXPIRED` are terminal and immutable. No state mutation is permitted.

## 2. MatchProposal Invariants

*   **P-1 Proposal Creation Context:** At the moment of MatchProposal creation, both referenced WalkIntents must be in the `OPEN` state.
*   **P-2 Mutual Acceptance Requirement:** A MatchProposal transitions to `CONFIRMED` if and only if both participants have accepted the proposal and both referenced WalkIntents remain in the `OPEN` state.
*   **P-3 Atomic Session Creation:** The transition of a MatchProposal to `CONFIRMED` must execute within an atomic transaction that: locks both intents, verifies both are `OPEN`, verifies no overlapping sessions exist for either user, creates exactly one WalkSession, and transitions both intents to `CONSUMED`.
*   **P-4 Exclusive Session Creation:** A MatchProposal results in a maximum of one WalkSession.
*   **P-5 Proposal Invalidity Cascade:** If either referenced WalkIntent leaves the `OPEN` state (transitioning to `CONSUMED`, `CANCELLED`, or `EXPIRED`), the associated MatchProposal transitions to `REJECTED` or `EXPIRED`.
*   **P-6 Terminal State Immutability:** The states `CONFIRMED`, `REJECTED`, and `EXPIRED` are terminal and immutable.

## 3. WalkSession Invariants

*   **S-1 Session Creation Validity:** A WalkSession is exclusively created by the Domain Service from a `CONFIRMED` MatchProposal referencing two distinct users whose WalkIntents were `OPEN` at the time of creation.
*   **S-2 No Overlapping Sessions:** A user cannot have multiple WalkSessions in either the `PENDING` or `ACTIVE` states with overlapping time windows. This must be enforced at the database level.
*   **S-3 Mutual Activation Required:** A WalkSession transitions from `PENDING` to `ACTIVE` if and only if both participants successfully activate it within the valid activation window.
*   **S-4 Activation Window Enforcement:** Session activation is strictly limited to the defined window: `[confirmedStartTime - earlyGrace, confirmedStartTime + lateGrace]`.
*   **S-5 NO_SHOW Determination:** A WalkSession transitions to `NO_SHOW` when exactly one participant activates the session before the activation window closes.
*   **S-6 CANCELLED Determination:** A WalkSession transitions to `CANCELLED` when it is manually cancelled prior to the activation window (requiring a valid cancellation reason payload), or when neither participant activates the session before the activation window closes.
*   **S-7 COMPLETED Requires ACTIVE:** A WalkSession transitions to `COMPLETED` exclusively from the `ACTIVE` state. User-initiated completion is strictly forbidden unless the session has been active for the minimum physical duration threshold (e.g., 5 minutes). System-initiated completion occurs when the scheduled end time is reached.
*   **S-8 Terminal Session Immutability:** The states `COMPLETED`, `NO_SHOW`, and `CANCELLED` are terminal and immutable. Corrections must be handled via compensating events, not state mutations.
*   **S-9 Maximum Session Lifespan:** An `ACTIVE` WalkSession cannot exceed the system's maximum safety duration limit (e.g., 4 hours). Upon exceeding this limit, the system automatically transitions the session to `COMPLETED` to prevent indefinite sessions.

## 4. Cross-Aggregate Invariants

*   **X-1 Intent-Session Consistency:** If a WalkSession references a WalkIntent, that WalkIntent must be in the `CONSUMED` state. Every `CONSUMED` WalkIntent must map to exactly one WalkSession. Orphaned `CONSUMED` intents are forbidden.
*   **X-2 Session Time Window Integrity:** The WalkSession time window must exactly match the intersection of the two confirmed WalkIntents' time windows. It cannot be mutated post-creation.
*   **X-3 Distinct Participants:** A WalkSession must reference two distinct user IDs.
*   **X-4 Orphan Proposal Prevention:** A MatchProposal cannot remain in the `CONFIRMED` state without an associated WalkSession. If WalkSession creation fails, the MatchProposal confirmation must be aborted.
