# WalkMate Backend Architecture

**Stack:** Java 17+ | Spring Boot 3.x | JPA/Hibernate | PostgreSQL (Supabase)  
**Style:** DDD-lite (minimal layers, pragmatic)  
**Date:** March 6, 2026

---

## 1. Overview

### 1.1 Two-Phase Model

WalkMate operates in **two distinct phases**:

```
┌──────────────────────────┐         ┌────────────────────────┐
│  COORDINATION PHASE      │         │   LIFECYCLE PHASE      │
│  (Pre-Value)             │  ────>  │   (Value Realization)  │
│                          │         │                        │
│  - WalkIntent            │         │   - WalkSession        │
│  - MatchSuggestion       │         │   - State Transitions  │
│  - Mutual Confirmation   │         │   - Trust Enforcement  │
└──────────────────────────┘         └────────────────────────┘
        Intent Domain                    Session Domain
```

**Phase Boundary (Value Realization Point):**

- WalkSession is created **only after** mutual confirmation
- Before: reversible intent coordination
- After: irreversible lifecycle enforcement with accountability

### 1.2 Core Value Statement

**Value = Completed Walk Session** (not matching, not intent creation)

All architecture decisions protect this value realization point.

---

## 2. Package Structure

```
src/main/java/com/walkmate/

controller/
    SessionController.java
    IntentController.java

application/
    SessionService.java
    IntentService.java

domain/
    intent/
        WalkIntent.java
        IntentRepository.java

    session/
        WalkSession.java
        SessionStatus.java
        SessionRepository.java

    user/
        User.java

    valueobject/
        Location.java
        Distance.java

infrastructure/
    repository/
        JpaSessionRepository.java
        JpaIntentRepository.java
```

### Folder Responsibilities

**controller/**  
HTTP endpoints, request validation, response formatting.  
**Không** chứa business logic.

**application/**  
Orchestrate use cases.  
Transaction boundary (`@Transactional`).  
Load domain → call domain method → save.

**domain/**  
Business rules, state transitions, invariants.  
Entity có thể dùng JPA annotations (pragmatic trade-off).  
Repository là **interface** (implementation trong infrastructure).

**infrastructure/**  
JPA repository implementations, database access.

---

## 3. Request Flow Examples

### 3.1 Activate Session (PENDING → ACTIVE)

**HTTP Request:**

```http
POST /api/v1/sessions/{sessionId}/activate
Authorization: Bearer <jwt>
Content-Type: application/json
```

**Flow:**

```java
// 1. Controller: Parse request, extract userId from JWT
@PostMapping("/{id}/activate")
public ResponseEntity<SessionResponse> activate(
    @PathVariable UUID id,
    @AuthenticationPrincipal UserPrincipal principal
) {
    return ok(sessionService.activateSession(id, principal.userId()));
}

// 2. Service: Load aggregate, delegate to domain, save
@Transactional
public SessionResponse activateSession(UUID id, UUID userId) {
    WalkSession session = sessionRepo.findById(id)
        .orElseThrow(() -> new SessionNotFoundException(id));

    session.activate(userId, LocalDateTime.now()); // Business rules inside!
    sessionRepo.save(session);

    return SessionMapper.toResponse(session);
}

// 3. Domain: Enforce business rules, transition state
public void activate(UUID userId, LocalDateTime now) {
    if (status != PENDING) throw new InvalidStateTransitionException();
    if (!isParticipant(userId)) throw new UnauthorizedActionException();

    LocalDateTime windowStart = scheduledStartTime.minusMinutes(15);
    LocalDateTime windowEnd = scheduledStartTime.plusMinutes(30);
    if (now.isBefore(windowStart) || now.isAfter(windowEnd)) {
        throw new ActivationWindowExpiredException();
    }

    this.status = ACTIVE;
    this.actualStartTime = now;
}
```

**Key Points:**

- Controller: thin HTTP adapter
- Service: transaction boundary, load/save pattern
- Domain: **all** business logic (guards, state transition)

---

### 3.2 Complete Session (ACTIVE → COMPLETED)

**Flow:**

```java
// Controller
@PostMapping("/{id}/complete")
public ResponseEntity<SessionResponse> complete(
    @PathVariable UUID id,
    @AuthenticationPrincipal UserPrincipal principal
) {
    return ok(sessionService.completeSession(id, principal.userId()));
}

// Service
@Transactional
public SessionResponse completeSession(UUID id, UUID userId) {
    WalkSession session = sessionRepo.findById(id)
        .orElseThrow(() -> new SessionNotFoundException(id));

    session.complete(userId, LocalDateTime.now());
    sessionRepo.save(session);

    return SessionMapper.toResponse(session);
}

// Domain (WalkSession.java)
public void complete(UUID userId, LocalDateTime now) {
    if (status != ACTIVE) {
        throw new InvalidStateTransitionException("Must be ACTIVE to complete");
    }
    if (!isParticipant(userId)) {
        throw new UnauthorizedActionException();
    }

    Duration duration = Duration.between(actualStartTime, now);
    if (duration.toMinutes() < 5) {
        throw new MinimumDurationNotMetException();
    }
    if (duration.toHours() > 4) {
        throw new SafetyLimitExceededException();
    }

    this.status = COMPLETED;
    this.actualEndTime = now;
}
```

---

### 3.3 Create Session (Mutual Confirmation → Session Creation)

**Coordination Phase (Intent Domain):**

Before session exists, users create/confirm WalkIntents. When both confirm → trigger session creation.

**Flow:**

```java
// IntentService
@Transactional
public SessionResponse createSessionFromIntent(UUID intentId, UUID userId) {
    WalkIntent intent = intentRepo.findById(intentId)
        .orElseThrow(() -> new IntentNotFoundException(intentId));

    // Domain guard: both users must have confirmed
    if (!intent.isMutuallyConfirmed()) {
        throw new MutualConfirmationRequiredException();
    }

    // Cross-aggregate check: prevent duplicate active sessions
    if (sessionRepo.findActiveSessionByUser(intent.getCreator()).isPresent()) {
        throw new UserAlreadyHasActiveSessionException(intent.getCreator());
    }
    if (sessionRepo.findActiveSessionByUser(intent.getPartner()).isPresent()) {
        throw new UserAlreadyHasActiveSessionException(intent.getPartner());
    }

    // Create session (domain factory method)
    WalkSession session = WalkSession.fromIntent(
        intent.getCreator(),
        intent.getPartner(),
        intent.getScheduledTime(),
        intent.getDuration()
    );

    sessionRepo.save(session);

    // Mark intent as used
    intent.markAsUsed(session.getId());
    intentRepo.save(intent);

    return SessionMapper.toResponse(session);
}
```

**Domain Factory (WalkSession.java):**

```java
public static WalkSession fromIntent(
    UUID participant1,
    UUID participant2,
    LocalDateTime startTime,
    Duration duration
) {
    WalkSession session = new WalkSession();
    session.id = UUID.randomUUID();
    session.participant1 = participant1;
    session.participant2 = participant2;
    session.scheduledStartTime = startTime;
    session.scheduledEndTime = startTime.plus(duration);
    session.status = SessionStatus.PENDING; // Initial state
    session.createdAt = LocalDateTime.now();

    return session;
}
```

---

## 4. Transaction & Concurrency

### 4.1 Optimistic Locking

Every WalkSession has a `@Version` field:

```java
@Entity
public class WalkSession {
    @Version
    private Long version; // Auto-incremented by JPA on UPDATE
}
```

**Race Condition Scenario:**

2 users try to activate the same session simultaneously:

```
User A: Load session (version=1) → activate()
User B: Load session (version=1) → activate()

User A: Save → version becomes 2 ✅
User B: Save → OptimisticLockException ❌ (expected version=1, actual=2)
```

**Handling:**

```java
@ExceptionHandler(OptimisticLockException.class)
public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockException ex) {
    return ResponseEntity.status(409).body(
        ErrorResponse.of("CONCURRENT_MODIFICATION", "Session was modified by another user")
    );
}
```

Frontend should retry the request (server will re-load latest version).

---

### 4.2 Idempotency

State transitions are naturally idempotent (guard clauses prevent double-apply):

```java
public void activate(UUID userId, LocalDateTime now) {
    if (status != PENDING) {
        throw new InvalidStateTransitionException("Current state: " + status);
    }
    // If already ACTIVE, this throws immediately (safe!)
}
```

---

### 4.3 Transaction Boundary Rules

**✅ DO:**

- One transaction per use case (Service method)
- Load → domain method → save pattern
- Keep transactions short (<200ms typical)
- Use `@Transactional(readOnly = true)` for queries

**❌ DON'T:**

- Call external APIs inside transaction
- Run long computations inside transaction
- Open multiple transactions per request

---

## 5. Authentication (JWT từ Supabase)

**Spring Security config:**

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt());
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${supabase.url}") String url) {
        return NimbusJwtDecoder.withJwkSetUri(url + "/.well-known/jwks.json").build();
    }
}
```

**Extract user trong Controller:**

```java
@PostMapping("/{id}/activate")
public ResponseEntity<SessionResponse> activate(
    @PathVariable UUID id,
    @AuthenticationPrincipal Jwt jwt
) {
    UUID userId = UUID.fromString(jwt.getSubject());
    return ok(sessionService.activateSession(id, userId));
}
```

---

## 6. What We Intentionally Do NOT Do

To keep things simple and pragmatic, we **avoid**:

| Pattern/Practice           | Why We Skip It                                           |
| -------------------------- | -------------------------------------------------------- |
| Event Sourcing             | Overkill for simple CRUD + state machine                 |
| CQRS                       | Read model = write model (no complex projections needed) |
| Separate Domain/JPA models | Pragmatic trade-off: use JPA annotations in domain       |
| Domain Services            | Behavior lives in aggregates (WalkSession methods)       |
| Specification Pattern      | Simple queries in repositories are enough                |
| DDD Aggregates (strict)    | We use them pragmatically, not dogmatically              |

**Philosophy:** Use DDD concepts where they add value (state machine, transaction boundaries, domain methods). Skip the ceremony where it doesn't.

---

## 7. Summary: Core Principles

### ✅ DO These

1. **Business logic in domain layer** — All guards, state transitions, invariants live in `WalkSession.activate()`, `.complete()`, etc.
2. **Transaction per use case** — One `@Transactional` service method per API endpoint
3. **Optimistic locking** — Always use `@Version` for entities with state machines
4. **State machine First** — Design state transitions before writing code
5. **Guard clauses everywhere** — Fail fast with domain exceptions
6. **DTO boundaries** — Domain never leaks to controller
7. **Two-phase model** — Coordination (Intent) → Lifecycle (Session) separation

### ❌ DON'T Do These

1. **Business logic in service** — Service only orchestrates, doesn't decide
2. **Setters in domain** — Use behavioral methods (`activate()`, not `setStatus(ACTIVE)`)
3. **External API calls in transaction** — Keep transactions short
4. **Skip validation** — Both request DTO validation + domain guard clauses
5. **Over-engineer** — No event sourcing, CQRS, saga unless truly needed

### Test Strategy

| Layer       | Test Type         | What to Test                        | Tool         |
| ----------- | ----------------- | ----------------------------------- | ------------ |
| Domain      | Unit              | State transitions, guard clauses    | JUnit 5      |
| Application | Integration       | Use case orchestration, transaction | Spring Test  |
| Controller  | Integration (API) | HTTP request/response, auth         | MockMvc      |
| Repository  | Integration       | Query logic, custom queries         | @DataJpaTest |

**Focus on domain tests** — If domain logic is correct, rest is glue code.

---

**END OF DOCUMENT**
