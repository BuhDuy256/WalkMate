# How to Develop a Backend Feature (DDD-lite)

**Pattern:** State Machine First → Domain → Service → Controller  
**Transaction:** One per use case (Service method)  
**Date:** March 6, 2026

---

## Workflow (7 Steps)

### Step 1: Define State Transition

**Before writing any code, answer these questions:**

1. What state transition am I implementing? (e.g., PENDING → ACTIVE)
2. Who can trigger it? (user action vs system auto-trigger)
3. What are the guard conditions? (time window, participant check, etc.)
4. What data changes? (state field, timestamps, etc.)

**Example:** "Activate Session"

- Transition: PENDING → ACTIVE
- Trigger: Either participant calls `/sessions/{id}/activate`
- Guards: Within activation window (-15min to +30min), user is participant, status PENDING
- Data: `status = ACTIVE`, `actualStartTime = now`

---

### Step 2: Write Domain Method (WalkSession.java)

**Where:** `domain/session/WalkSession.java`

**Template:**

```java
/**
 * {FROM_STATE} → {TO_STATE}
 * Guards: {list all conditions}
 */
public void {actionName}(UUID userId, LocalDateTime now) {
    // Guard 1: Check current state
    if (status != SessionStatus.{FROM_STATE}) {
        throw new InvalidStateTransitionException(
            "Can only {action} from {FROM_STATE}, current: " + status
        );
    }
    
    // Guard 2: Check authorization
    if (!isParticipant(userId)) {
        throw new UnauthorizedActionException();
    }
    
    // Guard 3: Check business rules
    // ... (time window, duration, etc.)
    
    // State transition
    this.status = SessionStatus.{TO_STATE};
    this.{relevantField} = {value};
    this.updatedAt = now;
}
```

**Real Example:**

```java
public void activate(UUID userId, LocalDateTime now) {
    // Guard: state
    if (status != SessionStatus.PENDING) {
        throw new InvalidStateTransitionException(
            "Can only activate from PENDING, current: " + status
        );
    }
    
    // Guard: participant
    if (!isParticipant(userId)) {
        throw new UnauthorizedActionException();
    }
    
    // Guard: time window
    LocalDateTime windowStart = scheduledStartTime.minusMinutes(15);
    LocalDateTime windowEnd = scheduledStartTime.plusMinutes(30);
    
    if (now.isBefore(windowStart) || now.isAfter(windowEnd)) {
        throw new ActivationWindowExpiredException(
            "Window: " + windowStart + " to " + windowEnd
        );
    }
    
    // State transition
    this.status = SessionStatus.ACTIVE;
    this.actualStartTime = now;
    this.updatedAt = now;
}
```

**Test:**

```java
@Test
void activate_success() {
    WalkSession session = new WalkSession(...);
    session.setStatus(PENDING);
    session.setScheduledStartTime(LocalDateTime.now().plusMinutes(10));
    
    session.activate(participant1, LocalDateTime.now());
    
    assertEquals(ACTIVE, session.getStatus());
    assertNotNull(session.getActualStartTime());
}

@Test
void activate_failsIfNotParticipant() {
    WalkSession session = new WalkSession(...);
    
    assertThrows(UnauthorizedActionException.class, 
        () -> session.activate(randomUser, LocalDateTime.now())
    );
}
```

---

### Step 3: Create DTOs

**Request DTO (if needed):**

```java
// Usually not needed for simple actions - userId extracted from JWT
```

**Response DTO:**

```java
public record SessionResponse(
    UUID id,
    SessionStatus status,
    LocalDateTime scheduledStartTime,
    LocalDateTime actualStartTime,
    LocalDateTime actualEndTime,
    UUID participant1,
    UUID participant2
) {}
```

---

### Step 4: Application Service Method

**Where:** `application/SessionApplicationService.java`

**Template:**

```java
@Transactional
public {ResponseDTO} {actionName}({ParamsWithDomain} id, UUID userId) {
    // Load aggregate
    {AggregateRoot} entity = {repository}.findById(id)
        .orElseThrow(() -> new {NotFound}Exception(id));
    
    // Call domain method (business logic inside!)
    entity.{domainMethod}(userId, LocalDateTime.now());
    
    // Save
    {repository}.save(entity);
    
    // Map to DTO
    return {Mapper}.toResponse(entity);
}
```

**Real Example:**

```java
@Service
@Transactional
public class SessionApplicationService {
    
    private final SessionRepository sessionRepository;
    
    public SessionResponse activateSession(UUID sessionId, UUID userId) {
        // Load
        WalkSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
        
        // Domain logic
        session.activate(userId, LocalDateTime.now());
        
        // Save
        sessionRepository.save(session);
        
        // Map
        return SessionMapper.toResponse(session);
    }
}
```

**Test:**

```java
@SpringBootTest
@Transactional
class SessionApplicationServiceTest {
    
    @Autowired
    private SessionApplicationService service;
    
    @Autowired
    private SessionRepository sessionRepository;
    
    @Test
    void activateSession_success() {
        // Given: session in DB
        WalkSession session = createPendingSession();
        sessionRepository.save(session);
        
        // When
        SessionResponse response = service.activateSession(
            session.getId(), 
            session.getParticipant1()
        );
        
        // Then
        assertEquals(SessionStatus.ACTIVE, response.status());
        
        // Verify in DB (transaction committed)
        WalkSession reloaded = sessionRepository.findById(session.getId()).get();
        assertEquals(SessionStatus.ACTIVE, reloaded.getStatus());
    }
}
```

---

### Step 5: Controller Endpoint

**Where:** `controller/SessionController.java`

**Template:**

```java
@PostMapping("/{id}/{action}")
public ResponseEntity<{ResponseDTO}> {actionName}(
    @PathVariable UUID id,
    @AuthenticationPrincipal Jwt jwt
) {
    UUID userId = UUID.fromString(jwt.getSubject());
    {ResponseDTO} response = {service}.{actionName}(id, userId);
    return ResponseEntity.ok(response);
}
```

**Real Example:**

```java
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {
    
    private final SessionApplicationService sessionService;
    
    @PostMapping("/{id}/activate")
    public ResponseEntity<SessionResponse> activateSession(
        @PathVariable UUID id,
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        SessionResponse response = sessionService.activateSession(id, userId);
        return ResponseEntity.ok(response);
    }
}
```

**Test:**

```java
@SpringBootTest
@AutoConfigureMockMvc
class SessionControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @WithMockJwt(subject = "user-123")  // Custom annotation for testing
    void activateSession_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/{id}/activate", sessionId)
                .contentType(APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.actualStartTime").exists());
    }
}
```

---

### Step 6: Exception Handling

**Add to GlobalExceptionHandler (if new exception):**

```java
@ExceptionHandler(ActivationWindowExpiredException.class)
public ResponseEntity<ErrorResponse> handleActivationWindowExpired(
    ActivationWindowExpiredException ex,
    WebRequest request
) {
    ErrorResponse error = ErrorResponse.builder()
        .timestamp(LocalDateTime.now())
        .status(HttpStatus.BAD_REQUEST.value())
        .error("OUTSIDE_ACTIVATION_WINDOW")
        .message(ex.getMessage())
        .path(getRequestPath(request))
        .build();
    
    return ResponseEntity.badRequest().body(error);
}
```

---

### Step 7: Integration Test (End-to-End)

```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ActivateSessionIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private SessionRepository sessionRepository;
    
    @Test
    void fullFlow_activateSession() throws Exception {
        // Given: session in DB
        WalkSession session = createPendingSession();
        session.setScheduledStartTime(LocalDateTime.now());  // Now is within window
        sessionRepository.save(session);
        
        String userId = session.getParticipant1().toString();
        
        // When: POST /api/v1/sessions/{id}/activate
        mockMvc.perform(post("/api/v1/sessions/{id}/activate", session.getId())
                .header("Authorization", "Bearer " + generateMockJwt(userId))
                .contentType(APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));
        
        // Then: DB updated
        WalkSession updated = sessionRepository.findById(session.getId()).get();
        assertEquals(SessionStatus.ACTIVE, updated.getStatus());
        assertNotNull(updated.getActualStartTime());
    }
}
```

---

## Common Anti-Patterns & Fixes

### ❌ Anti-Pattern 1: Business Logic in Service

```java
// BAD
@Transactional
public SessionResponse activateSession(UUID id, UUID userId) {
    WalkSession session = sessionRepository.findById(id).get();
    
    // Business logic in service (WRONG!)
    if (session.getStatus() != SessionStatus.PENDING) {
        throw new InvalidStateTransitionException();
    }
    if (!session.isParticipant(userId)) {
        throw new UnauthorizedActionException();
    }
    
    session.setStatus(SessionStatus.ACTIVE);
    session.setActualStartTime(LocalDateTime.now());
    sessionRepository.save(session);
    
    return SessionMapper.toResponse(session);
}
```

```java
// GOOD
@Transactional
public SessionResponse activateSession(UUID id, UUID userId) {
    WalkSession session = sessionRepository.findById(id).get();
    
    session.activate(userId, LocalDateTime.now());  // All logic in domain!
    sessionRepository.save(session);
    
    return SessionMapper.toResponse(session);
}
```

---

### ❌ Anti-Pattern 2: Using Setters Instead of Behavioral Methods

```java
// BAD
session.setStatus(SessionStatus.ACTIVE);
session.setActualStartTime(LocalDateTime.now());
```

```java
// GOOD
session.activate(userId, LocalDateTime.now());  // Encapsulated behavior
```

---

### ❌ Anti-Pattern 3: Returning Domain Entities from Controller

```java
// BAD
@GetMapping("/{id}")
public ResponseEntity<WalkSession> getSession(@PathVariable UUID id) {
    return ok(sessionRepository.findById(id).get());  // Domain leaks!
}
```

```java
// GOOD
@GetMapping("/{id}")
public ResponseEntity<SessionResponse> getSession(@PathVariable UUID id) {
    SessionResponse dto = sessionService.getSession(id);
    return ok(dto);  // DTO boundary preserved
}
```

---

### ❌ Anti-Pattern 4: External API Calls Inside Transaction

```java
// BAD
@Transactional
public void completeSession(UUID id, UUID userId) {
    WalkSession session = sessionRepository.findById(id).get();
    session.complete(userId, LocalDateTime.now());
    sessionRepository.save(session);
    
    // External API call (increases transaction time!)
    notificationService.sendPushNotification(...);  // 500ms API call
}
```

```java
// GOOD (Option 1: After transaction)
@Transactional
public void completeSession(UUID id, UUID userId) {
    WalkSession session = sessionRepository.findById(id).get();
    session.complete(userId, LocalDateTime.now());
    sessionRepository.save(session);
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onSessionCompleted(SessionCompletedEvent event) {
    notificationService.sendPushNotification(...);  // After commit
}
```

---

## Repository Query Patterns

### Pattern 1: Single Entity Lookup

```java
public interface SessionRepository extends JpaRepository<WalkSession, UUID> {
    // Built-in: findById(UUID id)
}
```

### Pattern 2: Find by Single Criteria

```java
List<WalkSession> findByParticipant1(UUID userId);
```

### Pattern 3: Find by Multiple Criteria (AND)

```java
List<WalkSession> findByParticipant1AndStatus(UUID userId, SessionStatus status);
```

### Pattern 4: Find with OR Condition (Use @Query)

```java
@Query("SELECT s FROM WalkSession s WHERE " +
       "(s.participant1 = :userId OR s.participant2 = :userId) " +
       "AND s.status IN ('PENDING', 'ACTIVE')")
Optional<WalkSession> findActiveSessionByUser(@Param("userId") UUID userId);
```

### Pattern 5: Find with  Comparison Operators

```java
@Query("SELECT s FROM WalkSession s WHERE " +
       "s.status = 'PENDING' AND s.scheduledStartTime < :cutoff")
List<WalkSession> findExpiredPendingSessions(@Param("cutoff") LocalDateTime cutoff);
```

---

## Test Strategy

| Test Type      | What to Test                       | Tool              | Location                 |
| -------------- | ---------------------------------- | ----------------- | ------------------------ |
| Unit           | Domain method logic, guard clauses | JUnit 5           | `domain/` test folder    |
| Integration    | Service orchestration, transaction | Spring Test       | `application/` test      |
| API            | HTTP request/response, auth        | MockMvc           | `controller/` test       |
| Repository     | Custom queries                     | @DataJpaTest      | `infrastructure/` test   |
| End-to-End     | Full request flow (DB → API)       | MockMvc + DB      | `integration/` test      |

**Focus:** Spend 80% of effort on domain unit tests. If domain logic is correct, rest is glue code.

---

## Checklist Before Committing

- [ ] Domain method contains all business logic (no logic in service)
- [ ] Guard clauses throw domain exceptions
- [ ] Service method is `@Transactional`
- [ ] Service only does: load → domain method → save
- [ ] Controller uses DTO (domain never leaks)
- [ ] JWT userId extracted correctly
- [ ] Domain exception mapped to HTTP status in GlobalExceptionHandler
- [ ] Unit tests for domain method (happy path + all guards)
- [ ] Integration test for service (with real transaction)
- [ ] API test for controller (with MockMvc)

---

## Quick Reference Commands

### Run Tests

```bash
# Unit tests only (fast)
./gradlew test --tests '*Domain*'

# Integration tests (slower, needs DB)
./gradlew test --tests '*Integration*'

# All tests
./gradlew test
```

### Database Migration

```bash
# Create new migration
# File: src/main/resources/db/migration/V{number}__{description}.sql

# Example: V3__add_actual_times_to_session.sql
ALTER TABLE walk_sessions 
ADD COLUMN actual_start_time TIMESTAMP,
ADD COLUMN actual_end_time TIMESTAMP;
```

### Run Locally

```bash
# Start with local profile
./gradlew bootRun --args='--spring.profiles.active=local'
```

---

## Summary

**7-Step Workflow:**

1. Define state transition (from/to, guards, who)
2. Write domain method with guards
3. Create DTOs (request/response)
4. Write service method (load → domain → save)
5. Write controller endpoint (extract JWT userId)
6. Add exception handling
7. Write integration test

**Core Rules:**

- Business logic → Domain
- Transaction boundary → Service
- DTO boundary → Controller never returns domain
- One transaction per use case
- Test domain methods thoroughly

---

**END OF DOCUMENT**
