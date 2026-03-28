# TESTING.md

WalkMate Backend | Test Conventions & Vibe Coding Reference

---

## 1. Where Tests Live

Every production class has exactly one counterpart test class in the mirror path under `src/test/`:

```text
src/main/java/com/walkmate/
├── domain/walksession/WalkSession.java
├── application/walksession/WalkSessionCommandService.java
├── presentation/controller/walksession/WalkSessionController.java
└── infrastructure/repository/walksession/WalkSessionJdbcRepository.java

src/test/java/com/walkmate/
├── domain/walksession/WalkSessionTest.java
├── application/walksession/WalkSessionCommandServiceTest.java
├── presentation/controller/walksession/WalkSessionControllerTest.java
└── infrastructure/repository/walksession/WalkSessionJdbcRepositoryTest.java
```

Rule: one production class → one test class, same package, suffix `Test`.

---

## 2. Test Type Per Layer

| Layer             | Test Class Suffix                 | Framework                | Spring Context                         | DB        |
| ----------------- | --------------------------------- | ------------------------ | -------------------------------------- | --------- |
| `domain/`         | `<Domain>Test.java`               | JUnit 5 only             | ❌ Never                                | ❌ Never   |
| `application/`    | `<Domain>CommandServiceTest.java` | JUnit 5 + Mockito        | ❌ Never                                | ❌ Never   |
| `presentation/`   | `<Domain>ControllerTest.java`     | JUnit 5 + MockMvc        | ✅ `@WebMvcTest` slice only             | ❌ Never   |
| `infrastructure/` | `<Domain>JdbcRepositoryTest.java` | JUnit 5 + TestContainers | ✅ `@DataJdbcTest` or `@SpringBootTest` | ✅ Real DB |

Key rule: **never use `@SpringBootTest` on a domain or application test.** It boots the entire context for no reason and hides coupling problems.

---

## 3. What to Mock vs What to Keep Real

| Component                              | In domain test | In application test | In controller test  |
| -------------------------------------- | -------------- | ------------------- | ------------------- |
| Domain entity (e.g. `WalkSession`)     | ✅ Real object  | ✅ Real object       | N/A                 |
| Repository interface                   | N/A            | ✅ Mock with Mockito | ✅ Mock              |
| CommandService / QueryService          | N/A            | N/A                 | ✅ Mock with Mockito |
| Provider (e.g. `TokenProvider`)        | N/A            | ✅ Mock              | ✅ Mock              |
| Infrastructure impl (`JdbcRepository`) | ❌ Never        | ❌ Never             | ❌ Never             |

Golden rule: **domain entities are never mocked.** They are instantiated directly. Mocking an entity means you are not testing the business logic at all.

---

## 4. Mandatory Scenarios Per Layer

### 4.1 Domain Entity Test — 3 required scenarios per method

```
✅ Happy path: valid inputs, correct state transition or return value
✅ Invariant violation: input that should throw DomainException — assert the exact ErrorCode
✅ Terminal state guard: if the entity has terminal states (COMPLETED, CANCELLED, NO_SHOW),
   assert that calling a mutating method on a terminal state throws DomainException
```

### 4.2 Application CommandService Test — 3 required scenarios per command method

```
✅ Happy path: repo returns data, entity method succeeds, repo.save() is called once
✅ Entity not found: repo.findById() returns empty, assert DomainException with correct ErrorCode
✅ Domain logic failure: entity throws DomainException (e.g. invalid state), assert it bubbles up unchanged
```

### 4.3 Controller Test — 2 required scenarios per endpoint

```
✅ Happy path: valid request body → assert HTTP 200/201 and correct ApiResponse<T> shape
✅ Validation failure: missing/invalid field → assert HTTP 422 and ApiResponse error shape
   (DomainException mapping is covered by GlobalExceptionHandler — test that separately)
```

### 4.4 GlobalExceptionHandler Test — 2 required scenarios

```
✅ DomainException maps to HTTP 400 with correct errorCode field in ApiResponse
✅ MethodArgumentNotValidException maps to HTTP 422 with field error details
```

---

## 5. Forbidden Patterns

These patterns are banned. If a generated test contains any of them, reject it and re-prompt.

| Forbidden                                                   | Why                                | Fix                                                                              |
| ----------------------------------------------------------- | ---------------------------------- | -------------------------------------------------------------------------------- |
| `@SpringBootTest` on a domain or application test           | Loads full context, masks coupling | Use plain `new` for entities, `@ExtendWith(MockitoExtension.class)` for services |
| `Mockito.mock(WalkSession.class)` or any domain entity mock | You are testing nothing            | Instantiate with `new WalkSession(...)` or a builder                             |
| `try { ... } catch (DomainException e) {}` in test body     | Swallows the exception, hides bugs | Use `assertThrows(DomainException.class, () -> ...)`                             |
| Asserting only that no exception was thrown                 | Not a test                         | Always assert the actual return value or state change                            |
| `Thread.sleep()` for timing                                 | Flaky                              | Use a `Clock` interface injected into the entity                                 |
| Hardcoded `System.currentTimeMillis()` in production entity | Untestable                         | Inject `Clock` or `Instant` via constructor/method                               |
| Testing private methods directly                            | Breaks encapsulation               | Test through the public method that calls them                                   |
| One test class testing multiple production classes          | Unclear failure origin             | One test class per production class, always                                      |

---

## 6. Canonical Test Templates

Copy these as the starting point when prompting Claude. Never let Claude invent the structure from scratch.

### 6.1 Domain Entity Test Template

```java
// domain/walksession/WalkSessionTest.java
class WalkSessionTest {

    // --- Happy path ---
    @Test
    void activate_shouldTransitionToActive_whenBothUsersActivateWithinWindow() {
        WalkSession session = WalkSessionFixture.pendingSession();
        session.activateByUser(USER_A_ID, NOW_WITHIN_WINDOW);
        session.activateByUser(USER_B_ID, NOW_WITHIN_WINDOW);
        assertThat(session.getStatus()).isEqualTo(WalkSessionStatus.ACTIVE);
    }

    // --- Invariant violation ---
    @Test
    void activate_shouldThrowDomainException_whenActivationWindowHasExpired() {
        WalkSession session = WalkSessionFixture.pendingSession();
        Instant expired = SCHEDULED_START.plusMinutes(31);
        DomainException ex = assertThrows(DomainException.class,
            () -> session.activateByUser(USER_A_ID, expired));
        assertThat(ex.getErrorCode()).isEqualTo(WalkSessionErrorCode.SESSION_ACTIVATION_WINDOW_EXPIRED);
    }

    // --- Terminal state guard ---
    @Test
    void activate_shouldThrowDomainException_whenSessionIsAlreadyCancelled() {
        WalkSession session = WalkSessionFixture.cancelledSession();
        DomainException ex = assertThrows(DomainException.class,
            () -> session.activateByUser(USER_A_ID, NOW_WITHIN_WINDOW));
        assertThat(ex.getErrorCode()).isEqualTo(WalkSessionErrorCode.SESSION_ALREADY_TERMINAL);
    }
}
```

### 6.2 Application CommandService Test Template

```java
// application/walksession/WalkSessionCommandServiceTest.java
@ExtendWith(MockitoExtension.class)
class WalkSessionCommandServiceTest {

    @Mock WalkSessionRepository sessionRepository;
    @Mock UserRepository userRepository;
    @InjectMocks WalkSessionCommandService commandService;

    // --- Happy path ---
    @Test
    void activateSession_shouldSaveSession_whenActivationIsValid() {
        WalkSession session = WalkSessionFixture.pendingSession();
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));

        commandService.activateSession(new ActivateSessionCommand(SESSION_ID, USER_A_ID, NOW));

        then(sessionRepository).should().save(session);
    }

    // --- Entity not found ---
    @Test
    void activateSession_shouldThrowDomainException_whenSessionNotFound() {
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.empty());
        DomainException ex = assertThrows(DomainException.class,
            () -> commandService.activateSession(new ActivateSessionCommand(SESSION_ID, USER_A_ID, NOW)));
        assertThat(ex.getErrorCode()).isEqualTo(WalkSessionErrorCode.SESSION_NOT_FOUND);
    }

    // --- Domain logic failure bubbles up ---
    @Test
    void activateSession_shouldPropagateDomainException_whenEntityRejectsActivation() {
        WalkSession session = WalkSessionFixture.cancelledSession();
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        assertThrows(DomainException.class,
            () -> commandService.activateSession(new ActivateSessionCommand(SESSION_ID, USER_A_ID, NOW)));
        then(sessionRepository).should(never()).save(any());
    }
}
```

### 6.3 Controller Test Template

```java
// presentation/controller/walksession/WalkSessionControllerTest.java
@WebMvcTest(WalkSessionController.class)
class WalkSessionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean WalkSessionCommandService commandService;

    // --- Happy path ---
    @Test
    void activateSession_shouldReturn200_whenRequestIsValid() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/{id}/activate", SESSION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "userId": "user-abc" }"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    // --- Validation failure ---
    @Test
    void activateSession_shouldReturn422_whenUserIdIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/{id}/activate", SESSION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.success").value(false));
    }
}
```

### 6.4 Fixture Class Convention

Every domain has a `<Domain>Fixture.java` in `src/test/java/.../domain/<domain-name>/`. This class provides pre-built valid and invalid domain objects so tests never duplicate construction logic.

```java
// src/test/java/com/walkmate/domain/walksession/WalkSessionFixture.java
public class WalkSessionFixture {
    public static WalkSession pendingSession() { ... }
    public static WalkSession activeSession() { ... }
    public static WalkSession cancelledSession() { ... }
    public static WalkSession completedSession() { ... }
}
```

Rule: if you find yourself calling `new WalkSession(...)` with a long argument list in more than one test, move it to the Fixture class.

---

## 7. The "Test or Code Wrong" Protocol

When a test fails, use this exact decision process before touching any code:

```
Step 1 — Read the failure message literally.
  Is it a compilation error? → The code or test has a structural problem (wrong method name, wrong type).
  Is it an assertion error?  → The behavior is wrong. Go to Step 2.
  Is it an unexpected exception? → Go to Step 3.

Step 2 — Assertion error.
  Compare the expected value against DOMAIN_CONTRACTS.md for this aggregate.
  If the contract says the behavior should be X and the test asserts X → code is wrong.
  If the contract says the behavior should be X and the test asserts Y → test is wrong.

Step 3 — Unexpected exception.
  Is it a DomainException with an error code not in DOMAIN_CONTRACTS.md? → Code added logic not in spec.
  Is it a NullPointerException inside the entity? → Missing guard in domain method.
  Is it a Spring/Mockito wiring error? → Test setup is wrong (forbidden pattern, bad mock).
```

When prompting Claude to debug, always provide: the test code, the production code, the exact error message, and the relevant section from `DOMAIN_CONTRACTS.md`. Never ask "why is this failing" without all four.

---

## 8. Test Naming Convention

```
methodName_shouldDoX_whenConditionY()
```

Examples:
- `activate_shouldTransitionToActive_whenBothUsersActivateWithinWindow`
- `activate_shouldThrowDomainException_whenActivationWindowHasExpired`
- `createIntent_shouldThrowDomainException_whenUserIsInPrivateMode`

This naming makes failure messages self-documenting. When a test fails, the name tells you exactly what broke without reading the body.