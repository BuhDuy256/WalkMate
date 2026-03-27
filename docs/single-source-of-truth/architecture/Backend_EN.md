# Backend Architecture

DDD-lite + Layered | WalkMate Android Project

## Overview

Model: DDD-lite + Layered architecture (Aiming for **Rich Domain Model**).

Organization strategy:

- `domain/` - domain-oriented (organized by aggregate root)
- `application/` - feature-oriented (organized by use case)

Goals:

- Separate business logic from technical implementation (Dependency Inversion).
- Keep domain independent from frameworks, DB, and HTTP. Entities must protect their own state (Rich Domain).
- Enable easy horizontal scaling when adding new domains or features.
- Centralize Exception handling at the Presentation layer.

## 1. Standard Directory Structure

```text
src/main/java/com/walkmate/
├── application/
│   └── <domain-name>/
│       ├── <Domain>CommandService.java
│       ├── <Domain>QueryService.java
│       ├── <Verb><Domain>Command.java (E.g: LoginUserCommand)
│       └── <Name>Provider.java (Interface, E.g: TokenProvider)
├── domain/
│   ├── <domain-name>/
│   │   ├── <AggregateRoot>.java (Rich Domain Entity)
│   │   ├── <Domain>Repository.java
│   │   ├── <Domain>ErrorCode.java
│   │   └── <ValueObject>.java / <EnumOrPolicy>.java
│   └── shared/
│       └── exception/
│           ├── DomainException.java
│           └── ErrorCode.java
├── infrastructure/
│   ├── repository/
│   │   └── <domain-name>/
│   │       └── <Domain><Tech>Repository.java (E.g: UserJdbcRepository)
│   ├── security/
│   │   └── jwt/
│   │       └── JwtTokenProvider.java (Impl)
│   └── config/
└── presentation/
    ├── controller/
    │   └── <domain-name>/
    │       └── <Domain>Controller.java
    ├── dto/
    │   ├── request/
    │   │   └── <domain-name>/
    │   │       └── <Verb><Domain>Request.java
    │   └── response/
    │       ├── ApiResponse.java (Generic Response Wrapper)
    │       └── <domain-name>/
    │           └── <Domain>Response.java
    ├── mapper/
    └── exception/
        └── GlobalExceptionHandler.java
```

## 2. Layer Responsibilities

| Layer             | Focus            | Responsibility                                                                                                         |
| ----------------- | ---------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `application/`    | Feature-oriented | Use case orchestration, defining Boundaries & Transactions, calling Domain Interfaces. Receives Command objects. No core business logic. |
| `domain/`         | Domain-oriented  | **Rich Domain Model**: Entities encapsulate business logic, guard state, and throw `DomainException` upon invariant violations. Contains repository/provider contracts. |
| `infrastructure/` | Technical        | Implements DB/jwt/framework/security. Repository implements domain interfaces (using JDBC/jOOQ). Contains only technical details. |
| `presentation/`   | HTTP entry point | Controller, DTO to Command mapping, HTTP validation (`@Valid`), exception aggregation via `GlobalExceptionHandler`. |

## 3. Naming Conventions

The domain name serves as the primary axis for naming across all layers. Ensure structural consistency.

### 3.1 Class Naming per Layer

| Layer                        | Name Pattern                   | Example                      |
| ---------------------------- | ------------------------------ | ---------------------------- |
| `domain/` aggregate          | `<Domain>.java`                | `Intent.java`                |
| `domain/` repo interface     | `<Domain>Repository.java`      | `IntentRepository.java`      |
| `domain/` error codes        | `<Domain>ErrorCode.java`       | `IntentErrorCode.java`       |
| `application/` write         | `<Domain>CommandService.java`  | `IntentCommandService.java`  |
| `application/` internal cmd  | `<Verb><Domain>Command.java`   | `LoginUserCommand.java`      |
| `application/` / `domain/` interface  | `<Name>Provider.java` / `Matcher`   | `TokenProvider.java`         |
| `infrastructure/` repo impl  | `<Domain><Tech>Repository.java`| `IntentJdbcRepository.java`  |
| `infrastructure/` tech impl  | `<Tech><Name>Provider.java`    | `JwtTokenProvider.java`      |
| `presentation/` controller   | `<Domain>Controller.java`      | `IntentController.java`      |
| `presentation/` request DTO  | `<Verb><Domain>Request.java`   | `CreateIntentRequest.java`   |

### 3.2 Request DTO vs Application Command

- **DTO (`presentation`)**: `LoginUserRequest` contains `@Valid` annotations, tightly coupled to Spring Web.
- **Command (`application`)**: `LoginUserCommand` is a pure Java `record` used to group parameters. Highly decoupled and intrinsically safe.

### 3.3 Service Method Naming - CQRS Segregation

Methods in `CommandService` must handle writes. Methods in `QueryService` must handle reads.

### 3.4 Error Codes

All error code constants must have a domain prefix adhering to `UPPER_SNAKE_CASE` (e.g `USER_NOT_FOUND`).

## 4. Standard Exception Flow

```text
Controller (Initiates @PostMapping request)
-> <Domain>CommandService (Application throws DomainException targeting data bounds: USER_NOT_FOUND)
-> Domain Model (Rich Domain throws DomainException for internal invariants: INVALID_USER_DATA, USER_INVALID_CREDENTIALS)
```
All emitted `DomainException` instances **bubble up** to `presentation/exception/GlobalExceptionHandler` and map automatically into a standard `ApiResponse<T>` wrapper. The HTTP status is determined dynamically by `ErrorCode.httpStatus()` — each error code declares its own correct HTTP status (404 for not-found, 403 for forbidden, 401 for unauthenticated, 409 for state conflicts, 400 for business rule violations).

## 5. Core Principles

1. **Rich Domain Model**: Domain is the logic epicenter. Entities autonomously guarantee their state and throw `DomainException`. The Application Service must not drain logic away from Entities (Avoid Anemic Domain Model).
2. **Dependency Inversion**: Password hashing algorithms, JWT generation... must pass through an Interface declared at the Application/Domain. The Infrastructure layer exclusively implements these Interfaces.
3. **One Global Exception Handler**: The Presentation layer absolutely must use a `GlobalExceptionHandler` to catch all `DomainException`, `@Valid` 422s, and 500 fallbacks. JSON Responses must be uniformly serialized into `ApiResponse<T>`.
4. **DTO Boundaries**: Web DTOs die upon hitting the Controller. Upon entering the Application layer, data must be converted into standard parameters or unannotated Pure Java Commands (`record`).
5. **Technology Suffix**: Infrastructure classes must reflect their operational technology via suffix (e.g., `UserJdbcRepository` for JDBC, `JwtTokenProvider` for JWT).

## 6. Core Architecture Hard Constraints

These are absolute **Hard Constraints** shaping the foundation of the architecture. **Zero tolerance for violations:**

| Constraint Criterion | Status | Disciplinary Requirement |
| -------------------- | ---------- | --------------- |
| **Logic inside Domain Entity?** | ✅ MANDATORY (Rich Model) | A pure getter/setter Entity generates a **Critical Anemic Domain Violation!** Business logic (including validation/authentication) must be pushed into the Entity to protect system states. |
| **Infrastructure knows about Web?** | ❌ STRICTLY FORBIDDEN | `infrastructure` cares solely about persistent DB/technologies and implements Application/Domain Contracts. Importations of HTTP/Web annotations are strictly forbidden. |
| **Application houses DB Logic?** | ❌ STRICTLY FORBIDDEN | The `application` orchestrator remains thin. Its sole duty: (1) Unload Data from Repo -> (2) Ship data to Entity for logic validation -> (3) Instruct Repo to commit. Complex logic statements here are strictly forbidden. |
| **Controller throws / catches Exception?** | ❌ STRICTLY FORBIDDEN | Controllers remain "try-catch free". Exceptions must seamlessly bubble upwards into the `GlobalExceptionHandler` for automated HTTP mapped responses. |
