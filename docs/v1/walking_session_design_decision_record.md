# Walking Session Prototype - Design Decision Record

## 1. Problem Framing

This system is NOT:

- A real-time radar-based social walking app
- A Tinder-style browsing interface
- A production-scale distributed
  matching system

This system IS: \> A state-consistent pairing engine that validates
walking session lifecycle rules.

Primary objectives:

- Validate business rules
- Validate state transitions
- Validate DB integrity
- Prevent inconsistent session states

Matching is only a trigger. Session lifecycle correctness is the core
focus.

---

## 2. Constraints

### Scope Constraints

- No chat
- No notifications
- No AI scoring
- No multi-selection list
- No radar
- No advanced filtering

### Technical Constraints

- No background worker required
- Matching is synchronous
- Lazy expiration allowed
- Single active intent/proposal/session per user

### Complexity Constraints

- No distributed concurrency
- No multi-resource locking
- No ranking engine
- No event-driven infrastructure

---

## 3. Tradeoff Prioritization

### Highest Priority

1.  State consistency
2.  Deterministic behavior
3.  Database integrity
4.  Exclusive pairing

### Accepted Tradeoffs

- No fairness optimization
- No optimal matching quality
- No real-time UX refinement
- No network density optimization

Reason: Prototype validates the engine, not the market.

---

## 4. Architectural Boundaries

### Matching Strategy

Conditions for compatibility:

1. Other intent is OPEN
2. Different user
3. Time overlap: startA \< endB AND startB \< endA
4. Distance \<= min(radiusA, radiusB)
5. walkType exact match

Algorithm:

- Sort compatible intents by nearest distance
- Select first compatible
- Create proposal
- Lock both users

No ranking score. No multi-proposal. No suggestion list.

---

### State Machine Boundary

Entities:

Intent:

- OPEN
- LOCKED
- CANCELLED

Proposal:

- PROPOSED
- CONFIRMED
- EXPIRED
- CANCELLED

Session:

- CONFIRMED
- IN_PROGRESS
- COMPLETED
- CANCELLED

Rules: - Each user can have only one active intent - Only one active
proposal - Only one active session - All transitions go through service
layer

---

### Concurrency Boundary

- Matching executed inside DB transaction
- Unique constraint prevents double booking
- No background job
- No asynchronous matching worker

---

## 5. Long-Term Maintainability Criteria

System is maintainable if:

1.  State transitions are centralized in one domain function.
2.  Matching logic is isolated in a single service.
3.  Database enforces constraints (unique active per user, enum states,
    foreign keys).
4.  Clear separation of concerns:
    - Domain layer: rules and states
    - Service layer: orchestration
    - Repository layer: persistence
    - Controller layer: input/output
5.  Matching algorithm can be replaced without rewriting session
    lifecycle logic.

---

## Final Architecture Summary

Matching:

- First Compatible Strategy
- Flexible Time Window
- Distance check (\<= radius)
- Exact walkType match
- Sorted by nearest distance

Infrastructure:

- Synchronous matching
- Lazy expiration
- No worker

State Control:

- Exclusive pairing
- Double confirmation
- One active flow per user

---

This prototype prioritizes correctness, clarity, and architectural
discipline over feature richness.
