# VIBE_CODING_GUIDE.md

WalkMate Backend | Step-by-Step Vibe Coding Workflow

This document tells you exactly what to prompt, in what order, for every feature.
Follow it sequentially. Never jump steps.

---

## The Golden Rule

**Documents lead. Code follows.**

Before writing any feature:
1. Check `DOMAIN_CONTRACTS.md` for the aggregate contract
2. Check `Backend_EN.md` for the layer rules
3. Check `TESTING.md` for the test convention

If the contract does not exist yet → write it in `DOMAIN_CONTRACTS.md` first, then generate code.

---

## Step-by-Step: How to Vibe Code One Feature

Use this flow for every single feature without exception.

```
Step 1 → Identify the aggregate
Step 2 → Generate the domain entity
Step 3 → Generate the domain entity tests
Step 4 → Generate the repository interface
Step 5 → Generate the command/query service
Step 6 → Generate the service tests
Step 7 → Generate the controller + DTO
Step 8 → Generate the controller tests
Step 9 → Generate the infrastructure (Jdbc repo)
Step 10 → Run all tests
```

Each step produces one file. Each file has a test before you proceed to the next layer.

---

## Prompt Templates

Copy these exactly. Fill in the `[BRACKETS]`. Do not rephrase — the structure matters.

---

### Prompt 1 — Generate Domain Entity

```
You are implementing the backend for WalkMate, a walking partner app.

Architecture rules (non-negotiable):
[paste the entire "Layer Responsibilities" and "Hard Constraints" section from Backend_EN.md]

Domain contract for this aggregate:
[paste the relevant aggregate section from DOMAIN_CONTRACTS.md — states, invariants, method contracts, error codes]

Task:
Generate the following files exactly:
1. `domain/[domain-name]/[Domain].java` — Rich Domain Entity
2. `domain/[domain-name]/[Domain]ErrorCode.java` — enum implementing ErrorCode interface
3. `domain/[domain-name]/[Domain]Repository.java` — repository interface only, no implementation

Rules:
- The entity must contain all business logic defined in the method contracts above
- The entity must throw DomainException with the exact error codes listed above
- The entity must be a pure Java class with zero Spring/framework imports
- The repository interface must only declare method signatures, no implementation
- Do not generate any other files
```

---

### Prompt 2 — Generate Domain Entity Tests

```
You are writing tests for the WalkMate backend.

Test conventions (non-negotiable):
[paste sections 2, 3, 4, 5, 6, and 8 from TESTING.md]

Domain contract being tested:
[paste the same aggregate section from DOMAIN_CONTRACTS.md that you used for Prompt 1]

Production code to test:
[paste the generated entity class from Prompt 1]

Task:
Generate `[Domain]Test.java` in the test mirror path.

Rules:
- Use JUnit 5 only. No Spring, no Mockito.
- Instantiate the entity with `new` or a Fixture class — never mock it.
- Every method in the entity that has a contract must have exactly 3 test methods:
  happy path, invariant violation (with exact ErrorCode assertion), terminal state guard.
- Use the naming convention: methodName_shouldDoX_whenConditionY
- Also generate `[Domain]Fixture.java` with all the object construction helpers needed by the tests.
```

**Before proceeding to Prompt 3: run the tests. All must pass. If any fail, use the "debug prompt" below.**

---

### Prompt 3 — Generate Repository Interface + Command Service

```
You are implementing the backend for WalkMate.

Architecture rules:
[paste "Layer Responsibilities" and "Hard Constraints" from Backend_EN.md]

Domain contract:
[paste the aggregate section from DOMAIN_CONTRACTS.md]

Existing domain files (already generated):
[paste domain entity class]
[paste domain repository interface]
[paste error code enum]

Task:
Generate `application/[domain-name]/[Domain]CommandService.java`

Rules:
- The service receives Command records as input (pure Java records, no annotations)
- Also generate each Command record as `application/[domain-name]/[Verb][Domain]Command.java`
- The service method flow must be exactly: (1) load from repo → (2) call entity method → (3) save via repo
- The service must not contain any business logic — all logic stays in the entity
- The service must not catch DomainException — let it bubble up
- Zero Spring DB annotations inside the service
```

---

### Prompt 3b — Generate QueryService

```
You are implementing the backend for WalkMate.

Architecture rules:
[paste "Layer Responsibilities" and "Hard Constraints" from Backend_EN.md]

Domain contract:
[paste the aggregate section from DOMAIN_CONTRACTS.md]

Existing domain files (already generated):
[paste domain entity class]
[paste domain repository interface]
[paste error code enum]

Task:
Generate `application/[domain-name]/[Domain]QueryService.java`

Rules:
- QueryService receives plain query parameters — not Command records
- All method names must use `get`, `find`, `list`, or `search` prefixes
- QueryService must NEVER call `repository.save()` — read-only access only
- Methods may return domain entities, read models, or plain value types — declare which per method in the javadoc
- The service must not contain any business logic — orchestration only
- Must not catch DomainException — let it bubble up to GlobalExceptionHandler
- Zero Spring DB annotations inside the service
```

---

### Prompt 4 — Generate Command Service Tests

```
You are writing tests for the WalkMate backend.

Test conventions:
[paste sections 4.2, 5, 6.2, and 8 from TESTING.md]

Production code to test:
[paste the CommandService class]
[paste the Command record(s)]
[paste the domain entity class]
[paste the repository interface]

Task:
Generate `[Domain]CommandServiceTest.java` in the test mirror path.

Rules:
- Use @ExtendWith(MockitoExtension.class). No @SpringBootTest.
- Mock the repository interface with @Mock. Never mock the domain entity.
- Every service method must have exactly 3 tests:
  1. Happy path: repo returns data, entity method succeeds, repo.save() is called once
  2. Not found: repo.findById() returns Optional.empty(), assertThrows DomainException with SESSION_NOT_FOUND (or equivalent)
  3. Domain failure: set up a terminal/invalid entity state (use Fixture), assertThrows DomainException, verify repo.save() is NEVER called
- Use naming convention: methodName_shouldDoX_whenConditionY
```

**Before proceeding to Prompt 5: run the tests. All must pass.**

---

### Prompt 5 — Generate Controller + DTO

```
You are implementing the backend for WalkMate.

Architecture rules:
[paste "Layer Responsibilities", naming conventions, and exception flow from Backend_EN.md]

Existing application files:
[paste CommandService class]
[paste Command record(s)]

Task:
Generate the following files:
1. `presentation/controller/[domain-name]/[Domain]Controller.java`
2. `presentation/dto/request/[domain-name]/[Verb][Domain]Request.java` (one per endpoint)
3. `presentation/dto/response/[domain-name]/[Domain]Response.java` (if needed)

Rules:
- Controllers must be try-catch free. No exception handling whatsoever.
- Controllers map DTO → Command → call service → wrap result in ApiResponse<T> → return.
- Request DTOs carry @Valid annotations. Nothing else.
- Controllers must not contain any business logic.
- Do not generate GlobalExceptionHandler — it already exists.
```

---

### Prompt 6 — Generate Controller Tests

```
You are writing tests for the WalkMate backend.

Test conventions:
[paste sections 4.3, 5, 6.3, and 8 from TESTING.md]

Production code to test:
[paste the Controller class]
[paste the Request DTO(s)]

Task:
Generate `[Domain]ControllerTest.java`.

Rules:
- Use @WebMvcTest([Domain]Controller.class). No @SpringBootTest.
- Mock the CommandService with @MockBean.
- Every endpoint must have exactly 2 tests:
  1. Happy path: valid request body → assert HTTP 200 or 201 and ApiResponse shape
  2. Validation failure: missing required field → assert HTTP 422
- Do not test DomainException → HTTP mapping here. That belongs in GlobalExceptionHandlerTest.
```

---

### Prompt 7 — Generate Infrastructure (Repository Implementation)

```
You are implementing the backend for WalkMate.

Architecture rules:
[paste "Infrastructure layer" row from layer table, and "Technology Suffix" rule from Backend_EN.md]

Domain repository interface to implement:
[paste repository interface]

Domain entity:
[paste entity class]

Task:
Generate `infrastructure/repository/[domain-name]/[Domain]JdbcRepository.java`

Rules:
- This class implements the domain repository interface.
- Use JDBC or jOOQ only. No JPA/Hibernate.
- No business logic. No DomainException. No HTTP/web imports.
- Map DB rows → domain entity using a private mapper method inside this class.
- Class name must end with the technology suffix: `[Domain]JdbcRepository`
```

---

### Prompt 7b — Generate Infrastructure Repository Test

```
You are writing integration tests for the WalkMate backend.

Test conventions:
[paste sections 1, 2, and 8 from TESTING.md]

Repository interface to test:
[paste the domain repository interface]

Infrastructure implementation under test:
[paste the JdbcRepository implementation class]

Task:
Generate `[Domain]JdbcRepositoryTest.java` in the test mirror path.

Rules:
- Use @DataJdbcTest (or @SpringBootTest if the query spans multiple tables or joins)
- Use TestContainers to spin up a real database instance matching the production DB engine
- Required test scenarios per repository method:
  1. save() + findById() round-trip: saved entity is retrievable with identical field values
  2. findById() with an unknown ID returns Optional.empty()
  3. Any custom query method (e.g. findByStatus, findAllByUserId) asserts correct filtering and ordering
- Set up test data via @Sql fixture scripts or a dedicated test data builder — do NOT write raw INSERT statements inline in test methods
- Mirror-path convention: test class lives in the same package path as the implementation class
```

---

## The Debug Prompt (use when a test fails)

Never touch any code before running this prompt:

```
A test is failing in the WalkMate backend. Help me diagnose it — do NOT fix anything yet.

Test code:
[paste the full test method]

Production code under test:
[paste the full production class]

Exact error output:
[paste the full stack trace or assertion error]

Domain contract:
[paste the relevant method contract from DOMAIN_CONTRACTS.md]

Answer only these questions:
1. Is the test asserting the wrong thing according to the contract? (test is wrong)
2. Is the production code violating the contract? (code is wrong)
3. Is this a test setup problem (wrong mock, missing fixture, bad wiring)?
4. Is this a genuine undocumented edge case not covered by the contract?

Do not suggest a fix yet. Diagnose only.
```

After you get the diagnosis, use this follow-up only if needed:

```
Diagnosis confirmed: [paste the diagnosis answer].
Now fix only the [test / production code] to align with this contract:
[paste the relevant contract section]
Do not change anything else.
```

---

## The Architecture Violation Check Prompt

Run this after generating any production class to catch violations before writing tests:

```
Review this class for architecture violations against the WalkMate backend rules.

Class to review:
[paste the class]

Architecture rules to enforce:
[paste the full "Hard Constraints" table from Backend_EN.md]

Report any violation found. For each violation, state:
- Which constraint is violated
- The exact line(s) in the code
- The correct pattern to use instead

If no violations found, say "CLEAN" and nothing else.
```

---

### Prompt 2b — Generate Cross-Aggregate Domain Service

```
You are implementing a Domain Service for WalkMate that enforces cross-aggregate invariants.

Cross-aggregate rules to enforce:
[paste the relevant rules from DOMAIN_CONTRACTS.md §6]

Aggregates involved:
[paste each relevant aggregate section — states, invariants, error codes, method contracts]

Task:
Generate `domain/[domain-name]/[Name]DomainService.java`

Rules:
- This is a domain service, not an application service — it contains real business logic
- It receives fully loaded domain entities as parameters, NOT IDs or Command records
- The application service is responsible for loading entities from repositories before calling this
- It calls entity methods and throws DomainException for any cross-aggregate invariant violations
- It does NOT call repositories directly — no repository dependency injection
- It does NOT handle persistence — that is always the application service's responsibility
- Zero Spring/framework imports — pure Java only
```

Also generate tests for the cross-aggregate Domain Service:

```
You are writing tests for a WalkMate cross-aggregate Domain Service.

Test conventions:
[paste sections 3 and 5 from TESTING.md]

Cross-aggregate rules under test:
[paste the rules from DOMAIN_CONTRACTS.md §6 that this service enforces]

Production code to test:
[paste the Domain Service class]
[paste the relevant Fixture classes]

Task:
Generate `[Name]DomainServiceTest.java` in the test mirror path.

Rules:
- Use JUnit 5 only. No Spring, no Mockito.
- Pass real entity instances (use Fixture classes) — never mock domain entities.
- Write exactly one test per cross-aggregate rule from §6:
  - Happy path: all preconditions satisfied, service passes without exception
  - Violation: one precondition fails, assertThrows DomainException with exact ErrorCode
```

---

## Feature Implementation Checklist

Use this as a checklist before marking any feature as done:

```
□ Domain entity generated and passes all 3 scenarios per method
□ Repository interface generated (interface only, no implementation)
□ CommandService generated — thin orchestration only, no logic
□ CommandService tests pass — all 3 scenarios per method
□ Controller generated — no try-catch, no logic
□ Controller tests pass — happy path + validation failure
□ GlobalExceptionHandler covers the new error codes
□ Infrastructure repository generated with technology suffix
□ Architecture violation check run on all new classes — returned CLEAN
□ DOMAIN_CONTRACTS.md updated if any new error codes or invariants were added
```

---

## How to Handle a New Feature Not in DOMAIN_CONTRACTS.md

1. Stop. Do not generate code.
2. Write the contract in `DOMAIN_CONTRACTS.md` first:
   - Add the states (if aggregate is new)
   - Add all valid transitions
   - Add all invariants
   - Add all error codes
   - Add all method contracts
3. Commit or save `DOMAIN_CONTRACTS.md`.
4. Then start from Prompt 1 using the new contract as input.

This ensures the contract always exists before the code, so every test has a ground truth to verify against.

---

---

## Frontend Feature Workflow (Android / Java)

Use this flow for every Android feature. Language is Java. State via `LiveData<UiState>`. Async via `DomainCallback<T>`.

```
Step 1 → Generate domain model + repository interface + error codes
Step 2 → Generate domain service
Step 3 → Generate RepositoryImpl + remote DTO + mapper
Step 4 → Generate ViewModel + UiState
Step 5 → Generate Fragment / Activity (Screen)
Step 6 → Run all tests
```

---

### Frontend Prompt 1 — Generate Domain Model + Repository Interface

```
You are implementing the Android (Java) frontend for WalkMate.

Architecture rules:
[paste the "Layer Responsibilities" and "Naming Conventions §3.2" sections from Frontend_EN.md]

Domain contract:
[paste the relevant aggregate section from DOMAIN_CONTRACTS.md — states, invariants, error codes]

Task:
Generate the following files:
1. `domain/[domain-name]/[Domain].java` — immutable domain model (no Android imports)
2. `domain/[domain-name]/[Domain]Repository.java` — interface only, no implementation
3. `domain/[domain-name]/[Domain]ErrorCode.java` — string constants class

Rules:
- Zero Android or framework imports in domain/ files
- The domain model must be a plain immutable Java class with a constructor and getters only
- The repository interface declares method signatures using DomainCallback<T> for async results
- Do not generate any other files
```

---

### Frontend Prompt 2 — Generate Domain Service

```
You are implementing the Android (Java) frontend for WalkMate.

Architecture rules:
[paste "Layer Responsibilities §domain/" from Frontend_EN.md]

Domain contract:
[paste the relevant aggregate section from DOMAIN_CONTRACTS.md]

Existing domain files:
[paste domain model class]
[paste repository interface]
[paste error code class]

Task:
Generate `domain/[domain-name]/[Domain]Service.java`

Rules:
- Domain service coordinates calls to the repository and applies business validation rules
- Receives and returns domain model objects, never DTOs or Android types
- Calls repository methods via DomainCallback<T>
- Zero Android framework imports
```

---

### Frontend Prompt 3 — Generate RepositoryImpl + DTO + Mapper

```
You are implementing the Android (Java) frontend for WalkMate.

Architecture rules:
[paste "Layer Responsibilities §data/" and "Naming Conventions §3.3 and §3.4" from Frontend_EN.md]

Domain repository interface to implement:
[paste repository interface]

Domain model:
[paste domain model class]

Task:
Generate the following files:
1. `data/datasource/remote/dto/[Domain]Dto.java` — matches the backend API response shape
2. `data/mapper/[Domain]DtoToDomainMapper.java` — maps DTO → domain model
3. `data/repository/[Domain]RepositoryImpl.java` — implements the domain repository interface

Rules:
- RepositoryImpl is the only class that knows about the DTO and the API service
- Mapping must go through the Mapper class — never inline inside RepositoryImpl
- Domain model must never reach the data layer; DTO must never reach the domain or UI layer
- Use DomainCallback<T> to deliver results to callers
```

---

### Frontend Prompt 4 — Generate ViewModel + UiState

```
You are implementing the Android (Java) frontend for WalkMate.

Architecture rules:
[paste "Standard UiState Contract §4" and "Standard Request Flow §5" from Frontend_EN.md]

Domain service to use:
[paste domain service class]

Task:
Generate the following files:
1. `ui/[feature-name]/[Feature]UiState.java` — immutable POJO with static initial() factory
2. `ui/[feature-name]/[Feature]ViewModel.java` — extends ViewModel

Rules:
- UiState fields are all final, set only via constructor. Provide static initial() factory.
- ViewModel holds MutableLiveData<[Feature]UiState> and exposes LiveData<[Feature]UiState>
- Every user action is a public void method on ViewModel
- ViewModel calls domain service methods with DomainCallback<T> for async results
- Use postValue() when updating state from a callback (background thread)
- ViewModel must not contain business logic — delegate everything to the domain service
- No Android UI imports in ViewModel (no View, Fragment, Context)
```

---

### Frontend Prompt 5 — Generate Fragment / Activity (Screen)

```
You are implementing the Android (Java) frontend for WalkMate.

Architecture rules:
[paste "Layer Responsibilities §ui/" from Frontend_EN.md]

ViewModel and UiState:
[paste ViewModel class]
[paste UiState class]

Task:
Generate `ui/[feature-name]/[Feature]Fragment.java` (or Activity)

Rules:
- Fragment observes LiveData<UiState> via getViewLifecycleOwner()
- All user interactions call methods on ViewModel — no direct domain or data calls
- Fragment renders state; it does not compute state
- No business logic in the Fragment
- Use ViewBinding for view access
```

---

### Frontend Feature Checklist

```
□ Domain model generated (immutable, no Android imports)
□ Repository interface generated (DomainCallback<T> signatures)
□ Domain service generated (zero Android imports)
□ RepositoryImpl generated, mapper in separate class
□ UiState generated with initial() factory and all-final fields
□ ViewModel generated with MutableLiveData + DomainCallback pattern
□ Fragment/Activity observes LiveData and delegates all actions to ViewModel
□ No DTO leaks into domain or ui/ layers
□ No business logic in Fragment or ViewModel
```

---

## File Placement Summary

```
Project docs (add to your repo root or /docs folder):
├── TESTING.md              ← test conventions, forbidden patterns, templates
├── DOMAIN_CONTRACTS.md     ← aggregate state machines, invariants, error codes
└── VIBE_CODING_GUIDE.md    ← this file — prompt templates and workflow

Source layout (reminder):
src/
├── main/java/com/walkmate/
│   ├── domain/
│   ├── application/
│   ├── infrastructure/
│   └── presentation/
└── test/java/com/walkmate/
    ├── domain/             ← entity tests + Fixture classes
    ├── application/        ← service tests
    ├── infrastructure/     ← repo tests (optional, only for complex queries)
    └── presentation/       ← controller tests + GlobalExceptionHandlerTest
```