# Backend Engineering Standards & Workflow (Spring Boot Edition)

This document defines the strict engineering standards, architectural patterns, and development workflows for our Spring Boot backend services. It serves as the single source of truth for all backend development, ensuring consistency, maintainability, and production readiness across the team.

**Tech Stack context:** Java 17+, Spring Boot 3.x, JPA/Hibernate, PostgreSQL, JWT Authentication, DDD-lite architecture.

---

## 1. Project Structure & Architecture

We strictly adhere to a **DDD-lite (Domain-Driven Design)** layered architecture to ensure a clear separation of concerns.

### 1.1 Folder Tree

> ⚠️ **AI / LLM INSTRUCTION: STRICT FOLDER RULES** ⚠️
> **Read this carefully:** The architecture is NOT "Package-by-Feature".
> The `domain/` directory is the **ONLY** place where features (like `/session/` or `/intent/`) are actively given sub-folders.
> The other layers (`application/`, `presentation/`, `infrastructure/`) MUST REMAIN FLAT. You must **NEVER** create paths like `application/session/` or `presentation/session/controller/`.
> Put controllers directly in `presentation/controller/`, and Services directly in `application/`.

> Terminology convention: In this guide, a "Use Case" is implemented as an **Application Service** class.
> Naming standard is `*Service` (e.g., `CompleteSessionService`).

```text
com.walkmate
├── application           # Service coordination & transaction boundaries
├── domain                # Core business logic, Entities, Value Objects, Domain Exceptions
│   ├── intent
│   ├── session
│   ├── user
│   └── valueobject
├── infrastructure        # External concerns, DB implementation, Configs, Security
│   ├── config
│   ├── exception         # Global handlers
│   ├── logging           # AOP logging, MDC
│   ├── repository        # JPA implementations / Spring Data interfaces
│   └── security          # JWT filters, auth providers
└── presentation          # HTTP endpoints, routing, DTOs, Mappers
    ├── controller
    ├── dto
    │   ├── request
    │   └── response
    └── mapper
```

### 1.2 Layer Diagram & Request Flow

```text
Client Request (HTTP)
       │
       ▼
[ Presentation Layer ] (Controllers, DTOs, Mappers)
  Translates JSON ↔ DTO. Routes to application service.
       │
       ▼
[ Application Layer ] (Services)
  Transaction boundary. Fetches entities, orchestrates flow, saves state.
       │
       ▼
[ Domain Layer ] (Entities, Value Objects)
  Holds business state, validates invariants, mutates state securely.
       │
       ▼
[ Infrastructure Layer ] (Repositories, External APIs)
  Persists state to database, calls third-party services.
```

### 1.3 Separation of Responsibilities

- **Presentation Layer**: _Dumb routing._ Only knows about HTTP, URLs, payloads, and mapping.
- **Application Layer**: _Workflow orchestrator._ Only knows about transaction boundaries and coordinating domain objects. No complex business logic.
- **Domain Layer**: _Heart of the system._ Knows ONLY about business logic and invariants. Zero knowledge of databases or HTTP.
- **Infrastructure Layer**: _The plumbing._ Knows how to write to PostgreSQL, talk to external APIs, and read config properties.

### 1.4 Data Flow Standard (The Contract)

To avoid confusing responsibilities, all requests MUST follow this strict flow without skipping steps:

1. **Request JSON** → Deserialized and validated into **Request DTO**
2. **Controller** → Extracts context, maps payload, calls **Application Service**
3. **Application Service** → Starts transaction, fetches entities, calls **Domain Method**
4. **Domain Entity** → Validates invariants, mutates its own internal state
5. **Application Service** → Passes mutated entity to **Repository** to persist
6. **Controller** → Calls **Mapper** to translate returned Entity into **Response DTO**
7. **ApiResponse Wrapper** → Wraps DTO and returns to **Client**

---

## 2. Folder-by-Folder Standards

### 2.1 Presentation Layer

#### Controllers (`presentation/controller`)

- **Purpose**: Expose REST APIs, validate HTTP requests, and return standard responses.
- **Rules**: Must be extremely thin. Never inject repositories. Never write business logic.
- **DO/DON'T**:
  - **DO**: Delegate work immediately to an Application Service.
  - **DON'T**: Write loops, condition checks (if/else), or database access in a controller.

```java
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {
  private final CreateSessionService createSessionService;
    private final SessionMapper mapper;

    @PostMapping
    public ApiResponse<SessionResponse> createSession(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CreateSessionRequest request) {

        Session session = createSessionService.execute(user.getId(), request);
        return ApiResponse.success(mapper.toResponse(session));
    }
}
```

#### DTOs (`presentation/dto`)

- **Purpose**: Define exactly what JSON comes in (Request) and what goes out (Response).
- **Rules**: Use Java Records or immutable POJOs (`@Value` / `@Data` without setters). Separate Request and Response completely.
- **DO/DON'T**:
  - **DO**: Use validation annotations (`@NotNull`, `@Size`) on Request DTOs.
  - **DON'T**: Reuse Domain Entities as DTOs. Never leak entities to the client.

#### Mappers (`presentation/mapper`)

- **Purpose**: Translate DTO ↔ Domain Entity.
- **Rules**: Use MapStruct or manual static mapping methods. Keep mapping logic out of controllers and services.

### 2.2 Application Layer

#### Services (`application`)

- **Purpose**: Defines system use cases and transaction boundaries.
- **Rules**: Classes should be named after actions/services (`CreateUserService`, `UpdateSessionService`).
- **DO/DON'T**:
  - **DO**: Annotate public methods with `@Transactional`.
  - **DON'T**: Put heavy domain logic here. The service should fetch the entity, call an entity method to perform the logic, and save it back.

```java
@Service
@RequiredArgsConstructor
public class CompleteSessionService {
    private final SessionRepository repository;

    @Transactional
    public Session execute(Long userId, Long sessionId) {
        Session session = repository.findByIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        session.complete(); // Business logic happens inside the entity!

        return repository.save(session);
    }
}
```

### 2.3 Domain Layer

#### Domain Entities (`domain/...`)

- **Purpose**: Represent business concepts, relationships, and state.
- **Rules**: Must hold their own invariants. State transitions happen via intention-revealing methods, not setters.
- **DO/DON'T**:
  - **DO**: Hide no-args constructors (use `protected` for JPA). Provide static factory methods or private constructors for creation.
  - **DON'T**: Expose public setters. DON'T use `@Data` on entities. Use `@Getter` cautiously.

### 2.4 Infrastructure Layer

#### Repository Interfaces (`infrastructure/repository`)

- **Purpose**: Abstract the persistence mechanism.
- **Repository Rules**:
  - **No business decision logic**: The repository strictly saves or loads state.
  - **No entity mutation**: Do not write custom `UPDATE` queries if the action can be resolved via an entity's method and a `.save()`.
  - **No transaction management**: `@Transactional` belongs on the Application Service.
  - **Only persistence abstraction**: Hide JPA/SQL details from the Domain layer.
  - **Custom queries must be documented**: Any complex `@Query` must explain _why_ it is needed.
  - **Use `Optional` for single fetch**: Standardize on `Optional<Entity>` to securely handle nullable returns.

#### Global Exceptions (`infrastructure/exception`)

- **Purpose**: Centralized `@RestControllerAdvice` to map Java exceptions to standard HTTP payload errors.

---

## 3. API Response Standards

All APIs MUST return a structured, unified envelope. Raw strings, generic Objects, or direct Entities are strictly forbidden.

### 3.1 Standard Wrapper (`ApiResponse<T>`)

```java
public record ApiResponse<T>(
    boolean success,
    T data,
    ErrorDetails error,
    String timestamp
) {
    public static <T> ApiResponse<T> success(T data) { ... }
    public static <T> ApiResponse<T> error(ErrorCode code, String message) { ... }
}
```

### 3.2 Explicit Return Type Standard

- **Application Service** MUST return a **Domain Entity**, a **Value Object**, or a standard Java collection of those. Services NEVER return DTOs or `ApiResponse`.
- **Controller** MUST return `ApiResponse<ResponseDTO>`.
- **NEVER** return generic `Object`.
- **NEVER** return `ResponseEntity<Object>` (unless responding with raw byte streams like file downloads).

### 3.3 ErrorCode Governance

We use a central `ErrorCode` enum. It MUST adhere to the following rules:

- **Be unique**: No two errors can share the same string code.
- **Be immutable**: Standard Java Enums only.
- **Be grouped by module prefix**: E.g., `AUTH_INVALID_TOKEN`, `SESSION_NOT_FOUND`, `USER_CONFLICT`.
- **Never expose internal details**: Do not leak internal Spring/Java exception class names, database constraints, or stack traces to the client.

### 3.4 Example JSON

**Success (2xx):**

```json
{
  "success": true,
  "data": {
    "id": 1,
    "title": "Morning Walk"
  },
  "error": null,
  "timestamp": "2026-03-10T09:00:00Z"
}
```

**Error (4xx/5xx):**

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "SESSION_409_CONFLICT",
    "message": "Session state has been modified by another transaction."
  },
  "timestamp": "2026-03-10T09:05:00Z"
}
```

---

## 4. Development Workflow & File Layout

### 4.1 Step-by-Step Development Workflow

When adding a new feature (e.g., updating a user's location):

1. **Define DTOs**: Create `UpdateLocationRequest` and `LocationResponse` in `presentation/dto`.
2. **Update Domain**: Add an `updateLocation(lat, lng)` method to the `User` or `Session` entity that enforces business rules.
3. **Add Repository Method**: If a custom query is needed, add it to `UserRepository`.
4. **Implement Service**: Create `UpdateLocationService` in `application`. Fetch entity -> call domain method -> save repository. Add `@Transactional`.
5. **Add Controller**: Create a new endpoint mapped to the Controller. Validate request, extract user ID from token, call Service, Map to Response.
6. **Add Mapper**: Update MapStruct/manual mapper to turn the entity into `LocationResponse`.
7. **Write Tests**: Write Domain Unit Tests for invariants, Service Integration Tests for database edge cases, and Controller API tests.

### 4.2 File Layout for New Feature (Example: "Session")

This layout is strictly enforced. Do not group files randomly.

> ⚠️ **AI / LLM INSTRUCTION: DO NOT NEST BY FEATURE** ⚠️
> Look at the example below. Notice that `CreateSessionService.java` goes _directly_ into `application/`. It does NOT go into `application/session/`. Do NOT hallucinate extra feature folders outside of the `domain/` layer.

```text
presentation/
    controller/SessionController.java
    dto/request/CreateSessionRequest.java
    dto/response/SessionResponse.java
    mapper/SessionMapper.java

application/
  CreateSessionService.java
  CompleteSessionService.java

domain/session/
    Session.java
    SessionStatus.java
    SessionRepository.java

infrastructure/repository/
    JpaSessionRepository.java
```

---

## 5. Common Patterns

For team consistency, use the established patterns for common flows:

- **Create Aggregate**: Application Service instantiates entity via a static factory method `Entity.create(...)` then calls `repository.save()`.
- **State Transition (Update)**: Application Service uses `repository.findById()` -> calls an intention-revealing method on the entity: `entity.complete()` -> calls `repository.save()`.
- **Read-only Query**: For endpoints purely fetching data, it is acceptable for a Service or specific Query Implementation to bypass the Domain Entity and map database results directly to a Read DTO for performance optimization.
- **Pagination Query**: Controller accepts Spring's `Pageable`. Service returns standard `Page<Entity>`. Controller/Mapper translates this to `Page<ResponseDTO>`, wrapped in `ApiResponse`.
- **Soft Delete**: Apply Hibernate's `@SQLDelete` and `@Where(clause = "deleted = false")` on the entity instead of hard-deleting records.

---

## 6. Golden Rules & Anti-Patterns

### 6.1 Golden Rules (Critical Constraints)

| Rule                        | Description                                                            | Do                                 | Don't                                         |
| :-------------------------- | :--------------------------------------------------------------------- | :--------------------------------- | :-------------------------------------------- |
| **Separation of Concerns**  | Layers must not bypass neighbors.                                      | Controller -> Service -> Domain    | Controller -> Repository                      |
| **No Logic in Controller**  | Routing and Mapping only.                                              | Call `service.execute(dto)`        | `if (dto.getAge() > 18) { ... }`              |
| **No Anemic Domains**       | Put logic in Entities.                                                 | `user.changePassword(newHash)`     | `user.setPassword(newHash)`                   |
| **Transaction Boundary**    | One TX per use-case via Application Service.                           | `@Transactional` on Service.       | `@Transactional` on Controller.               |
| **No External Calls in TX** | Keep TX short. Don't block DB threads expecting external HTTP replies. | Call API, _then_ start DB TX.      | Call third-party API inside `@Transactional`. |
| **DTO Separation**          | Never leak Domain to Client.                                           | Return `ApiResponse<UserResponse>` | Return `ApiResponse<User>`                    |

### 6.2 Anti-Patterns (Strictly Forbidden)

Any Pull Request containing these will be automatically rejected by reviewers:

- ❌ **Injecting a Repository in a Controller**: Strictly forbidden. Always go through the Application layer.
- ❌ **Exposing an Entity in the API**: Never return a raw Domain Entity. Always use a Response DTO.
- ❌ **Using `@Data` on an Entity**: `@Data` generates setters and poorly performing `equals()`/`hashCode()` for JPA entities.
- ❌ **Using public setters**: State mutations must be intention-revealing verbs (`verify()`, `complete()`), not `setStatus()`.
- ❌ **Catching Exception and swallowing it**: Empty catch blocks are forbidden. Ensure errors are logged or rethrown appropriately.
- ❌ **Starting transactions in Controller**: `@Transactional` goes on the Application Service.
- ❌ **Calling external APIs inside `@Transactional`**: This binds a database connection thread to an external HTTP timeout, causing pool exhaustion under load.

---

## 7. Naming Conventions

- **Classes (Entities, Services, Controllers)**: `PascalCase`. (e.g., `UserProfile`, `CreateSessionService`).
- **Methods**: `camelCase`. Verbs targeting actions. (e.g., `calculateScore()`, `findById()`).
- **Request DTOs**: Suffix with `Request`. (e.g., `UpdateEmailRequest`).
- **Response DTOs**: Suffix with `Response`. (e.g., `SessionDetailResponse`).
- **Exceptions**: Suffix with `Exception`. (e.g., `InvalidTransitionException`, `ResourceNotFoundException`).
- **REST Endpoints**: `kebab-case`, plural nouns. (e.g., `/api/v1/user-profiles`, `/api/v1/sessions`).
- **Packages**: all `lowercase`, singular. (e.g., `com.walkmate.domain.user`).

---

## 8. Concurrency & Transaction Standards

### Optimistic Locking

- All entities subject to concurrent updates MUST use a `@Version` field.
- **Handling 409 Conflicts**: If an `ObjectOptimisticLockingFailureException` is thrown, the Global Exception Handler MUST catch it and return a standard `409 Conflict` response with an appropriate error code. Do not handle locking manually in the service layer unless explicit retry logic is required.

### Boundaries and Idempotency

- Services represent exactly ONE transaction boundary. Do not nest transactions (`@Transactional(propagation = Propagation.REQUIRES_NEW)`) without extreme justification.
- Modifying REST endpoints (`POST`, `PUT`, `PATCH`) must be designed with idempotency in mind where applicable.

---

## 9. Security Standards

- **JWT Extraction**: Controllers must NEVER extract JWT headers directly. Use Spring Security filters.
- **@AuthenticationPrincipal**: Use this annotation in the controller method signature to get the authenticated user context securely.
- **Trust the Token**: Never trust a user ID or role sent in the Request Body or URL path if it dictates authorization context. Always use the implicit identity provided via the security context.

```java
// ✅ DO THIS
@PostMapping("/me/sessions")
public ApiResponse<Void> create(@AuthenticationPrincipal CustomUserDetails user) { ... }

// ❌ NEVER DO THIS
@PostMapping("/sessions")
public ApiResponse<Void> create(@RequestBody CreateRequest req) {
    // relying on req.getUserId() allows privilege escalation
}
```

---

## 10. Logging Standards

- **AOP Logging**: Use Aspect-Oriented Programming (AOP) (`LayerLoggingAspect.java`) to automatically log entries and exits for Controller and Service layers.
- **No `System.out.println()`**: Under absolutely no circumstances is this allowed. Use SLF4J/Logback (`@Slf4j`).
- **MDC (Mapped Diagnostic Context)**: Use the existing `MdcFilter` to inject a trace/correlation ID into all logs for a single request flow.
- **Sensitive Data**: Never log passwords, raw JWT tokens, or PII (Personally Identifiable Information).

---

## 11. Testing Strategy

We strictly follow the test pyramid.

- **Domain Layer (Unit Tests)**: Pure JUnit 5/Mockito. Testing state transitions, validation, and domain invariants. Must execute in milliseconds. No Spring context allowed.
- **Application Layer (Integration Tests)**: Tests use cases from start to finish. Mocks external dependencies. Tests transaction rollback and optimistic locking behavior.
- **Repository (@DataJpaTest)**: Tests custom JPA queries to ensure syntax and result mappings are correct.
- **Presentation Layer (API/MockMvc Tests)**: Tests routing, HTTP 400 validations, mapper functionality, and JSON serialization.

---

## 12. PR Review Checklist

Before approving a backend Pull Request, the reviewer MUST verify:

- [ ] Is all business logic encapsulated inside the Domain Entity (No anemic objects)?
- [ ] Is the transaction boundary clearly defined on the Application Service?
- [ ] Are Domain Entities strictly separated from Presentation DTOs?
- [ ] Are explicit Maps/Mappers used to translate between DTOs and Entities?
- [ ] Are custom exceptions mapped correctly in the Global Exception Handler?
- [ ] Is the Controller free of explicit Repository dependencies?
- [ ] Is user identity extracted strictly from the Security Context (Token) and not the request payload?
- [ ] Are sufficient tests included covering Domain state rules and API responses?

---

## 13. Quick Reference Section

**Standard Service Method Signature**

```java
@Transactional
public ReturnPayload execute(Long authenticatedUserId, InputPayload payload) { ... }
```

**Standard Controller Endpoint**

```java
@PostMapping("/{id}")
public ApiResponse<ResDTO> handle(
    @PathVariable Long id,
    @AuthenticationPrincipal AuthCtx auth,
    @Valid @RequestBody ReqDTO req) { ... }
```

**Entity Exception Throwing Standard**

```java
if (invalidCondition) {
    throw new DomainRuleException(ErrorCode.INVALID_STATE, "State transition is invalid.");
}
```
