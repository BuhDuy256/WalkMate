# TEST_PROMPTS.md

WalkMate Backend | Step-by-Step Test Generation Guide for Vibe Coders

---

## How to use this document

Each section is a self-contained prompt you copy, fill in the `[BRACKETS]`, and send
to Claude alongside two attached files:

- `TESTING.md` — test conventions, forbidden patterns, templates
- `DOMAIN_CONTRACTS.md` — aggregate contracts, error codes, method guards

Never send a prompt without both files attached. They are the ground truth Claude
compares everything against. Without them, Claude invents its own conventions and
every test will be inconsistent.

Read the **"How to proceed"** block under each prompt before you run it. It tells you
exactly what to do when the output comes back, what to check, and what to do if
something is wrong.

---

## Situation A — New feature (no code exists yet)

Use prompts A1 → A2 → A3 → A4 in order. Do not skip ahead.
Run the tests after each prompt before moving to the next.

---

### A0 — Write the contract first (before any code)

This is not a Claude prompt. This is a mandatory rule.

Before generating any code or any test for a new feature, open `DOMAIN_CONTRACTS.md`
and write the contract for this aggregate or method. Include:

- States and valid transitions
- Invariants
- Error codes and when each is thrown
- Method contracts (guards, side effects, return values)

Only then proceed to A1.

If the contract is not written, stop. Do not generate code. Do not generate tests.
A test without a contract is just a guess.

---

### A1 — Generate domain entity test FIRST (before the entity exists)

```
You are writing tests for the WalkMate backend BEFORE the production code exists.

Attached files (read both carefully before writing anything):
- TESTING.md — follow every rule in sections 2, 3, 4, 5, 6, and 8
- DOMAIN_CONTRACTS.md — the contract below is extracted from this document

Domain contract for this aggregate:
[paste the relevant aggregate section from DOMAIN_CONTRACTS.md
 — include: states, valid transitions, invariants, error codes, method contracts]

Task:
Generate TWO files:

1. src/test/java/com/walkmate/domain/[domain-name]/[Domain]Fixture.java
   - One factory method per domain state
     (e.g. pendingSession(), activeSession(), cancelledSession(), completedSession())
   - Use a fixed Clock constant for time-based logic so tests are deterministic
   - Define these time constants if the entity uses activation windows or durations:
       CLOCK_BEFORE_WINDOW   — time before the valid window opens
       CLOCK_WITHIN_WINDOW   — time inside the valid window
       CLOCK_AFTER_WINDOW    — time after the valid window closes
       CLOCK_AFTER_MIN_DURATION — time past the minimum required duration
   - Never call System.currentTimeMillis() or LocalDateTime.now() directly

2. src/test/java/com/walkmate/domain/[domain-name]/[Domain]Test.java
   - For every method in the contract, write exactly 3 test methods:
       (a) happy path — assert the correct state transition or return value
       (b) invariant violation — assert DomainException with the EXACT ErrorCode
           from the contract. Must assert ex.getErrorCode() not just that an
           exception was thrown.
       (c) terminal state guard — assert DomainException when calling a mutating
           method on a terminal state
   - Use JUnit 5 only. Zero Spring. Zero Mockito.
   - Instantiate entities using the Fixture class, never inline construction.
   - Use naming convention: methodName_shouldDoX_whenConditionY

Hard rules — reject the output if any of these are violated:
- No @SpringBootTest anywhere in the file
- No Mockito.mock() on any domain entity — ever
- No try/catch blocks in any test method — use assertThrows()
- No test that only asserts "no exception was thrown" — always assert state change
- Every assertThrows must follow up with assertThat(ex.getErrorCode()).isEqualTo(...)
```

**How to proceed after A1:**

The generated tests will not compile yet — the entity class does not exist. That is
correct. Do not fix the compilation errors yet.

1. Proceed to generate the entity using VIBE_CODING_GUIDE.md Prompt 1.
2. After the entity is generated, compile and run the tests.
3. All tests must go GREEN.
4. If any test fails, do not touch any code. Go to **D1** to diagnose first.
5. Only proceed to A2 when every domain test is GREEN.

---

### A2 — Generate service test (after CommandService is generated)

Generate the CommandService first using VIBE_CODING_GUIDE.md Prompt 3.
Then run this prompt.

```
You are writing tests for the WalkMate backend.

Attached files (read both carefully before writing anything):
- TESTING.md — follow every rule in sections 2, 3, 4, 5, 6, and 8
- DOMAIN_CONTRACTS.md — error codes and method contracts are the source of truth

Production code to test:
[paste the CommandService class]
[paste each Command record used by this service]

Domain entity and repository (already tested — do not re-test domain logic here):
[paste the domain entity class]
[paste the domain repository interface]
[paste the domain error code enum]

Fixture class (use this to build entity states, never construct entities inline):
[paste the Fixture class from A1]

Task:
Generate src/test/java/com/walkmate/application/[domain-name]/[Domain]CommandServiceTest.java

For every public method in the CommandService, write exactly 3 test methods:
  (a) Happy path:
      - mock repo returns a valid entity in the correct starting state (use Fixture)
      - the command executes successfully
      - verify repo.save() is called exactly once with the modified entity
  (b) Entity not found:
      - mock repo.findById() returns Optional.empty()
      - assert DomainException is thrown
      - assert ex.getErrorCode() equals the NOT_FOUND code for this domain
      - verify repo.save() is NEVER called
  (c) Domain failure bubbles:
      - use Fixture to build an entity in a terminal or invalid state
      - mock repo to return it
      - assert DomainException propagates — the service must NOT catch it
      - verify repo.save() is NEVER called (use Mockito never())

Hard rules:
- Use @ExtendWith(MockitoExtension.class). No @SpringBootTest.
- Mock the repository interface with @Mock. Never mock the domain entity.
- Use BDDMockito style: given(...).willReturn(...) and then(...).should(...)
- The service test must NOT re-test domain logic already covered by the entity test
- Use naming convention: methodName_shouldDoX_whenConditionY
```

**How to proceed after A2:**

1. Compile and run. All 3 scenarios per method must be GREEN.
2. If the "domain failure bubbles" test fails with the service NOT throwing:
   the service is catching `DomainException` somewhere — architecture violation.
   Go to **D2**.
3. If `repo.save()` is called in the failure test:
   the service contains logic it should not. Go to **D2**.
4. Only proceed to A3 when all service tests are GREEN.

---

### A3 — Generate controller test (after Controller + DTO are generated)

Generate the Controller and DTOs first using VIBE_CODING_GUIDE.md Prompt 5.
Then run this prompt.

```
You are writing tests for the WalkMate backend.

Attached files (read both carefully before writing anything):
- TESTING.md — follow every rule in sections 2, 3, 4, 5, 6, and 8
- DOMAIN_CONTRACTS.md — for reference on error codes if needed

Production code to test:
[paste the Controller class]
[paste every Request DTO used by this controller]
[paste the ApiResponse wrapper class]

CommandService (already tested — mock it entirely here):
[paste the CommandService class — method signatures are enough]

Task:
Generate src/test/java/com/walkmate/presentation/controller/[domain-name]/[Domain]ControllerTest.java

For every endpoint in the controller, write exactly 2 test methods:
  (a) Happy path:
      - send a valid, fully populated request body
      - assert HTTP 200 (or 201 for creation endpoints)
      - assert jsonPath("$.success").value(true)
  (b) Validation failure:
      - send a request body with one required field missing or invalid
      - assert HTTP 422
      - assert jsonPath("$.success").value(false)

Hard rules:
- Use @WebMvcTest([Domain]Controller.class). No @SpringBootTest.
- Mock the CommandService with @MockBean. Never instantiate it directly.
- Do NOT test DomainException → HTTP mapping here.
  That belongs in GlobalExceptionHandlerTest only.
- Do NOT add try/catch blocks anywhere in the test.
- Use naming convention: methodName_shouldReturnX_whenConditionY
```

**How to proceed after A3:**

1. Compile and run. Both scenarios per endpoint must be GREEN.
2. If validation failure returns 500 instead of 422:
   `@Valid` is missing from the request parameter in the controller method.
   Fix the controller, not the test.
3. If happy path returns the wrong HTTP status (e.g. 200 vs 201):
   check `@ResponseStatus` on the controller method. The test is correct —
   fix the controller to match what the contract says the status should be.
4. Only proceed to A4 when all controller tests are GREEN.

---

### A4 — Architecture violation check (run before marking any slice done)

```
Review these classes for architecture violations.

Enforce every rule in this checklist:
1. Domain entity must contain all business logic. No if/else logic in the service.
2. Infrastructure must not import anything from Spring Web, javax.ws, or HTTP packages.
3. Application service must NOT catch DomainException — it must bubble up always.
4. Controller must have zero try/catch blocks.
5. Controller must have zero if/else business logic.
6. Application service method flow must be exactly:
   (1) load from repo → (2) call entity method → (3) save via repo. Nothing else.
7. Domain entity must be a pure Java class — zero Spring or framework imports.
8. Repository interface must live in domain/, not in application/ or infrastructure/.

Classes to review:
[paste every new production class generated in this slice]

Output format:
- For each violation: state the rule number, the exact line(s), and the correct pattern.
- For each clean class: write exactly "[ClassName] — CLEAN" and nothing else.
```

**How to proceed after A4:**

1. Fix every violation. Touch only the violating class.
2. Re-run all tests after each fix to confirm nothing broke.
3. Re-run the architecture check on the fixed class.
4. When every class is CLEAN and every test is GREEN: the slice is done.

---

## Situation B — Existing code, no tests yet

Use this when you have features already implemented but no test files exist.
Run B1 → B2 → B3 for each existing aggregate, then use A2 + A3 for service
and controller tests.

---

### B1 — Extract contract from existing code

Run this for each existing domain entity before writing any test.

```
Read this existing domain entity class carefully.

[paste the entity class]
[paste the ErrorCode enum for this domain]

Extract and write a DOMAIN_CONTRACTS.md section for this aggregate.

Include exactly:
- All states (if the entity has a status/state field)
- All valid state transitions you can infer from the code
- All forbidden transitions (states that must throw DomainException)
- All invariants you can infer from guard conditions and validation checks
- All error codes with the exact condition that triggers each one
- All public method contracts in this format:
    ### MethodName(params)
    - Guards: [what it checks and what it throws on failure]
    - Side effects: [what state it changes]
    - Returns: [what it returns, if anything]

Critical rules:
- Document only what the code CURRENTLY DOES. Do not invent missing guards.
- Do not add error codes that do not exist in the enum.
- If the code has a bug or a missing guard, document the current behavior anyway.
  We will discover and fix bugs through tests, not by changing the contract.

Format the output to match this structure exactly:
[paste one complete aggregate section from DOMAIN_CONTRACTS.md as the format reference]
```

**How to proceed after B1:**

1. Read the extracted contract yourself before using it.
2. If a guard or error code looks obviously missing, make a note separately —
   but do not add it to the contract yet. The contract must reflect the current code.
3. Add the extracted section to your `DOMAIN_CONTRACTS.md` file.
4. Proceed to B2.

---

### B2 — Generate Fixture class for existing entity

```
You are generating a test Fixture class for an existing WalkMate domain entity.

Attached files:
- TESTING.md — read section 6.4 for Fixture conventions

Existing entity class:
[paste the entity class]

Existing error code enum:
[paste the error code enum]

Task:
Generate src/test/java/com/walkmate/domain/[domain-name]/[Domain]Fixture.java

Rules:
- Create one factory method for every possible state of this entity.
  Example: if the entity has PENDING, ACTIVE, COMPLETED, CANCELLED, NO_SHOW,
  create a factory method for each.
- If the entity uses time-based logic, define these fixed Clock constants:
    CLOCK_BEFORE_WINDOW       — instant before the valid window opens
    CLOCK_WITHIN_WINDOW       — instant inside the valid window
    CLOCK_AFTER_WINDOW        — instant after the valid window closes
    CLOCK_AFTER_MIN_DURATION  — instant past the minimum required duration
- Build entity states by calling the entity's real public methods in sequence.
  Do not set fields via reflection, setters, or any backdoor.
- Never call System.currentTimeMillis() or LocalDateTime.now() directly.
```

**How to proceed after B2:**

1. Verify every entity state has a factory method.
2. Compile. Fix any compilation errors from constructor or method signature
   differences before proceeding.
3. Proceed to B3.

---

### B3 — Generate tests for existing entity

```
You are writing tests for an EXISTING WalkMate domain entity.
The entity code already exists. These tests verify the code matches its contract.

Attached files (read both carefully before writing anything):
- TESTING.md — follow every rule in sections 2, 3, 4, 5, 6, and 8
- DOMAIN_CONTRACTS.md — the contract below was extracted from the existing code

Domain contract (extracted in B1):
[paste the extracted contract section for this aggregate]

Existing production code:
[paste the entity class]
[paste the error code enum]

Fixture class (from B2):
[paste the Fixture class]

Task:
Generate src/test/java/com/walkmate/domain/[domain-name]/[Domain]Test.java

For every public method that has a contract, write exactly 3 test methods:
  (a) happy path — assert the correct state transition or return value
  (b) invariant violation — assert DomainException with the EXACT ErrorCode
  (c) terminal state guard — assert DomainException on a terminal entity

Hard rules — identical to A1:
- No @SpringBootTest. No Mockito.mock() on entities. No try/catch in test body.
- Every assertThrows must assert ex.getErrorCode().
- Use naming convention: methodName_shouldDoX_whenConditionY
```

**How to proceed after B3:**

Run the tests immediately. There are three possible outcomes:

**All GREEN:** the existing code matches its contract exactly. Proceed to service
and controller tests using A2 and A3.

**Some RED — assertion mismatch:** the contract was extracted incorrectly. The test
asserts X but the code does Y. Check whether Y is what the code actually does. If
yes, the contract extraction was wrong — fix the contract to match the code. If no,
you found a real bug — use **D1** to diagnose.

**Some RED — wrong error code:** the code throws a different `DomainException` error
code than the contract says. This is a real bug in existing code. Use **D1** to
diagnose before fixing anything.

Do not change test assertions to make them pass. If a test fails, understand
the reason completely before touching any file.

After all B3 tests are GREEN, run A2 for the service test and A3 for the
controller test using the same production code.

---

## Situation C — A test is failing

Never modify code or tests before running the diagnosis prompt.
Diagnosis first. Fix second. Always.

---

### D1 — Diagnose a failing domain entity test

```
A domain entity test is failing. Diagnose only — do NOT suggest any fix yet.

Attached files:
- TESTING.md — the conventions used to write this test
- DOMAIN_CONTRACTS.md — the contract this test was written against

Failing test:
[paste the complete failing test method including the class declaration and imports]

Production code under test:
[paste the complete entity class]

Exact failure output:
[paste the complete error message and stack trace from your test runner]

Relevant contract section:
[paste the method contract from DOMAIN_CONTRACTS.md that this test covers]

Answer ONLY these four questions. No code. No fix suggestions yet.

1. Is the test asserting the wrong thing according to the contract?
   (the contract says X should happen but the test expects Y)
2. Is the production code violating the contract?
   (the contract says throw ERROR_A but the code throws ERROR_B or does not throw)
3. Is this a test setup problem?
   (wrong Fixture state, wrong Clock value, incorrect entity construction order)
4. Is this an undocumented behavior gap?
   (the code does something the contract never mentioned at all)

State which one applies and explain why in one paragraph. One diagnosis only.
```

**After D1 — use the matching fix prompt:**

If (1) — test is wrong:
```
Diagnosis confirmed: the test asserts the wrong thing.
Fix ONLY the test assertion to match this contract:
[paste the relevant contract section]
Do not change the production code. Do not touch any other test.
```

If (2) — code is wrong:
```
Diagnosis confirmed: the production code violates the contract.
Fix ONLY the production code to match this contract:
[paste the relevant contract section]
Do not change the test. Do not change any other class.
```

If (3) — test setup is wrong:
```
Diagnosis confirmed: the test setup is incorrect.
Fix ONLY the Fixture construction or Clock constant or mock configuration.
Do not change the production code. Do not change the assertion.
```

If (4) — undocumented behavior gap:
```
Diagnosis confirmed: this behavior is not in the contract.
Should this behavior exist according to the domain design in DOMAIN_CONTRACTS.md?
Answer yes or no and explain. Do not change any code yet.
```

---

### D2 — Diagnose a failing service test

```
A CommandService test is failing. Diagnose only — do NOT suggest any fix yet.

Attached files:
- TESTING.md — the conventions used to write this test
- DOMAIN_CONTRACTS.md — the contract this test was written against

Failing test:
[paste the complete failing test method]

Production code under test:
[paste the complete CommandService class]

Domain entity used in the test:
[paste the entity class]

Exact failure output:
[paste the complete error message and stack trace]

Answer ONLY these four questions. No code. No fix suggestions yet.

1. Is repo.save() being called when it should NOT be?
   (the service is catching DomainException instead of letting it bubble)
2. Is the wrong exception type or error code being thrown?
   (the service throws its own exception instead of propagating the entity's)
3. Is this a Mockito wiring problem?
   (wrong argument matchers, missing given() setup, incorrect @InjectMocks)
4. Does the service contain business logic it should not have?
   (if/else or validation inside the service that belongs in the entity)

State which one applies and explain why. One diagnosis only.
```

---

### D3 — Diagnose a failing controller test

```
A controller test is failing. Diagnose only — do NOT suggest any fix yet.

Attached files:
- TESTING.md — the conventions used to write this test

Failing test:
[paste the complete failing test method]

Production code under test:
[paste the Controller class]
[paste the Request DTO(s)]

Exact failure output:
[paste the complete error message and stack trace]

Answer ONLY these four questions. No code. No fix suggestions yet.

1. Is the HTTP status wrong?
   (returning 200 when 201 expected, or 500 when 422 expected)
   If yes: is @ResponseStatus missing on the method, or is @Valid missing on the DTO?
2. Is the response body shape wrong?
   (ApiResponse fields missing, wrong JSON field names, unexpected null values)
3. Is this a @WebMvcTest wiring problem?
   (missing @MockBean, security filter blocking the request, wrong controller in annotation)
4. Does the controller do something it must not?
   (try/catch block present, business logic present, direct repository call)

State which one applies and explain why. One diagnosis only.
```

---

## Situation D — GlobalExceptionHandler test (write once, never per feature)

---

### E1 — Generate GlobalExceptionHandler test

Run this once after Phase 0. This test covers error mapping for every feature
forever. Never delete it, never duplicate it per feature.

```
You are writing a one-time test for the WalkMate GlobalExceptionHandler.
This test is written once and validates error mapping for the entire application.

Attached files:
- TESTING.md — follow section 4.4 exactly
- DOMAIN_CONTRACTS.md — for reference on DomainException and ErrorCode structure

Production code:
[paste GlobalExceptionHandler class]
[paste ApiResponse class]
[paste DomainException class]
[paste ErrorCode interface]

Task:
Generate src/test/java/com/walkmate/presentation/exception/GlobalExceptionHandlerTest.java

Write exactly 2 test methods:

Test 1 — DomainException maps to HTTP 400:
- Create a minimal @RestController stub as a static inner class inside the test file.
  The stub has one endpoint that throws a DomainException with a known hardcoded errorCode.
- Use @WebMvcTest to load only this stub + the GlobalExceptionHandler.
- Call the endpoint.
- Assert HTTP status 400.
- Assert jsonPath("$.success").value(false).
- Assert jsonPath("$.errorCode").value(the known error code string).

Test 2 — @Valid failure maps to HTTP 422:
- Create a second minimal @RestController stub as a static inner class.
  The stub has one endpoint with a @RequestBody DTO that has one @NotNull field.
- Send a request body with that field missing.
- Assert HTTP status 422.
- Assert jsonPath("$.success").value(false).

Hard rules:
- Use @WebMvcTest scoped to the stub controllers only. No @SpringBootTest.
- Both stub controllers are static inner classes inside the test file.
  Do not use any real WalkMate controller or service in this test.
- Do not test any DomainException → HTTP mapping anywhere else in the codebase.
  This file is the single place for that concern.
```

**How to proceed after E1:**

1. Run immediately. Both tests must be GREEN before writing any feature test.
2. If they fail, the bug is in `GlobalExceptionHandler` or `ApiResponse` — fix those,
   not the test.
3. Once GREEN, you never need to test exception-to-HTTP mapping per feature again.
   Every controller test inherits this guarantee automatically.

---

## Quick reference — which prompt for which situation

| Situation                                                       | Prompt           |
| --------------------------------------------------------------- | ---------------- |
| New feature, need domain test before entity exists              | A1               |
| New feature, need service test after service is generated       | A2               |
| New feature, need controller test after controller is generated | A3               |
| New feature, architecture check before marking slice done       | A4               |
| Existing code, no tests — extract contract first                | B1               |
| Existing code, no tests — build Fixture class                   | B2               |
| Existing code, no tests — generate domain entity tests          | B3               |
| Existing code, no tests — service + controller tests            | B4 (run A2 + A3) |
| Domain entity test is failing                                   | D1               |
| Service test is failing                                         | D2               |
| Controller test is failing                                      | D3               |
| GlobalExceptionHandler test (once only)                         | E1               |

---

## The mandatory order — every slice, every time

```
1. Contract written in DOMAIN_CONTRACTS.md
             ↓
2. A1: Domain Fixture + domain test (RED — entity does not exist yet)
             ↓
3. Generate entity code → domain tests go GREEN
             ↓
4. Generate service code
             ↓
5. A2: Service test → GREEN
             ↓
6. Generate controller + DTO code
             ↓
7. A3: Controller test → GREEN
             ↓
8. A4: Architecture check → all classes CLEAN
             ↓
         Slice is done
```

If a test is RED and you do not know why, run the matching D-prompt before touching
any code. Changing code without a diagnosis is the fastest way to break other tests
and bury real bugs.