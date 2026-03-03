# Walking Session Prototype - System Architecture

## 1. Architectural Principles

This system follows:

- Clean Architecture
- SOLID Principles
- Separation of Concerns
- Strategy Pattern for extensibility
- Dependency Inversion Principle (DIP)

The goal is to ensure:

- Clear responsibility boundaries
- Scalability from V1 to V2
- Maintainability without rewrite
- Replaceable matching policies

---

# 2. Backend Architecture (Python - FastAPI)

## 2.1 Layered Structure

    backend/
    │
    ├── app/
    │   ├── main.py
    │
    │   ├── api/
    │   │   ├── routes/
    │   │   │   ├── auth.py
    │   │   │   ├── intent.py
    │   │   │   ├── match.py
    │   │   │   └── session.py
    │
    │   ├── domain/
    │   │   ├── models/
    │   │   │   ├── intent.py
    │   │   │   ├── proposal.py
    │   │   │   └── session.py
    │   │   ├── state_machine.py
    │   │   └── invariants.py
    │
    │   ├── services/
    │   │   ├── matching_service.py
    │   │   ├── session_service.py
    │   │   └── reliability_service.py
    │
    │   ├── strategies/
    │   │   ├── matching_strategy.py
    │   │   ├── first_compatible.py
    │   │   └── weighted_score.py
    │
    │   ├── repositories/
    │   │   ├── intent_repo.py
    │   │   ├── proposal_repo.py
    │   │   └── session_repo.py
    │
    │   └── infrastructure/
    │       ├── database.py
    │       └── orm_models.py

---

## 2.2 Responsibility by Layer

### API Layer

- Accepts HTTP requests
- Returns responses
- No business logic

### Domain Layer

- Contains state machine
- Contains invariants
- Pure business rules
- No DB logic

### Service Layer

- Orchestrates workflows
- Calls matching strategy
- Calls repositories
- Calls reliability service

### Strategy Layer

- Matching algorithm policy
- Pluggable implementations
- FirstCompatibleStrategy (V1)
- WeightedScoreStrategy (V2)

### Repository Layer

- Database access only
- No business logic

---

## 2.3 Matching Strategy (DIP Applied)

### Interface

    interface MatchingStrategy {
        List<Intent> rank(Intent current, List<Intent> candidates);
    }

### V1 Implementation

- Filter compatible intents
- Sort by nearest distance
- Select first

### V2 Implementation

Score formula:

    score =
    w1 * (1 - normalizedDistance) +
    w2 * tagSimilarity +
    w3 * normalizedReliability

- Sort by score descending
- Select top candidate

MatchingService depends on abstraction, not concrete implementation.

---

## 2.4 State Machine Ownership

- WalkSession transitions are centralized
- All transitions go through one function
- No direct status update in controller
- Reliability updates triggered by state transition events

---

# 3. Frontend Architecture (Android - Java)

Architecture pattern: MVVM

## 3.1 Structure

    com.walkingapp
    │
    ├── ui/
    │   ├── intent/
    │   │   ├── IntentActivity.java
    │   │   ├── IntentViewModel.java
    │   │   └── IntentAdapter.java
    │   │
    │   ├── session/
    │   │   ├── SessionActivity.java
    │   │   └── SessionViewModel.java
    │
    ├── data/
    │   ├── api/
    │   │   ├── ApiClient.java
    │   │   └── ApiService.java
    │   │
    │   ├── repository/
    │   │   └── SessionRepository.java
    │
    ├── model/
    │   ├── Intent.java
    │   ├── Session.java
    │   └── Proposal.java

---

## 3.2 Responsibility Separation

### Activity

- Render UI
- Observe LiveData
- No business logic

### ViewModel

- Calls repository
- Handles loading state
- Maintains screen state

### Repository

- Calls backend API
- Abstracts data source

---

# 4. Evolution Path (V1 → V2)

## V1

- FirstCompatibleStrategy
- Simple reliability update
- Lazy expiration
- Deterministic behavior

## V2

- WeightedScoreStrategy
- Tier-based reliability
- Priority matching
- Optional background scheduler

Core state machine remains unchanged.

---

# 5. Scalability and Maintainability Criteria

System is scalable if:

- Matching strategy is replaceable
- State machine does not change when matching logic changes
- Reliability logic is modular
- Controllers contain no business rules
- Database constraints enforce invariants

System is maintainable if:

- Clear separation of concerns
- No circular dependencies
- Domain layer is framework-independent
- Adding scoring requires only new strategy class

---

# 6. Architectural Guarantees

- Exclusive pairing enforced at DB level
- One active session per user
- Deterministic state transitions
- Matching policy is pluggable
- Reliability system does not control state machine

---

This architecture ensures that V1 is simple, correct, and clean, while
allowing V2 to extend matching and governance logic without rewriting
the core engine.
