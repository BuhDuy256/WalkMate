# WalkMate – Docs Improvement Checklist

> **Purpose:** Work through this list top to bottom. Each task includes the problem, what needs to change, and exactly how to fix it.
> Tasks are ordered by impact — fix conflicts first, fill missing contracts second, polish last.

---

## 🔴 Priority 1 — Fix Conflicts (Do these first)

---

### [X] 1. Fix HTTP status code mapping for DomainException

**File to change:** `Backend_EN.md` + `DOMAIN_CONTRACTS.md`

**Problem:**
`Backend_EN.md §4` states that all `DomainException` instances map to HTTP 400. This is incorrect. Many error codes represent situations that have standard, distinct HTTP semantics:
- `USER_NOT_FOUND`, `SESSION_NOT_FOUND`, `PROPOSAL_NOT_FOUND`, `INTENT_NOT_FOUND` → should be **404**
- `INTENT_OWNER_MISMATCH`, `SESSION_USER_NOT_PARTICIPANT` → should be **403**
- `USER_INVALID_CREDENTIALS` → should be **401**
- Business rule violations (e.g. `SESSION_ALREADY_TERMINAL`, `INTENT_INVALID_TIME_RANGE`) → stay **400**

Forcing all to 400 breaks frontend error handling (cannot distinguish "not found" from "bad input") and violates REST conventions.

**How to fix:**
1. Add an `httpStatus()` method to the `ErrorCode` interface in `domain/shared/exception/ErrorCode.java`:
   ```java
   public interface ErrorCode {
       String name();
       int httpStatus(); // Each error code declares its own HTTP status
   }
   ```
2. Update every `*ErrorCode` enum to implement `httpStatus()` with the correct value per code.
3. Update `GlobalExceptionHandler` to read `exception.getErrorCode().httpStatus()` instead of hardcoding 400.
4. Update `Backend_EN.md §4` to reflect the new dynamic mapping rule.
5. Update `DOMAIN_CONTRACTS.md` — add an `HTTP` column to each error code table showing the correct status per code.

---

### [X] 2. Add tiered cancellation penalty rules to DOMAIN_CONTRACTS.md

**File to change:** `DOMAIN_CONTRACTS.md`

**Problem:**
`Features_List_EN.md §5.6` mentions "tiered penalty based on timing" when a session is cancelled from PENDING. However, `DOMAIN_CONTRACTS.md §3` only says `cancel()` sets status to CANCELLED — no penalty logic, no tiers, no thresholds are defined anywhere. This is a business rule. If it is not in the contracts, it will either be forgotten or implemented inconsistently across the domain entity, TrustScore aggregate, and tests.

**How to fix:**
Add a `§3.9 Cancellation Penalty Policy` subsection to `DOMAIN_CONTRACTS.md` under WalkSession. Define:
```
Penalty tiers (example — adjust thresholds to product decision):

Tier 0 – No penalty:
  cancel() called when (scheduledStart - cancellationTime) > 2 hours

Tier 1 – Light penalty:
  cancel() called when 30 min < (scheduledStart - cancellationTime) <= 2 hours

Tier 2 – Heavy penalty:
  cancel() called when (scheduledStart - cancellationTime) <= 30 minutes

Rules:
- The WalkSession.cancel() method must return or emit the penalty tier.
- TrustScore aggregate applies the deduction based on the tier.
- WalkSession does NOT apply TrustScore directly — it only declares the tier.
- Penalty tier must be included in the SessionCancelled domain event payload.
```
After writing this, also update `TrustScore §5.1` trigger table to reference the tiers.

---

### [X] 3. Decide: Java or Kotlin for the frontend, then update Frontend_EN.md

**File to change:** `Frontend_EN.md`

**Problem:**
`Frontend_EN.md` uses `.java` file extensions throughout all examples and the folder structure, but the architecture it describes (UiState, ViewModel, UiEffect, UiEvent) is the Jetpack Compose / Android MVVM pattern that is Kotlin-native. Using Java for this pattern requires significant boilerplate and lacks coroutine support, StateFlow, data classes, and sealed classes — all of which the described architecture implicitly depends on. This ambiguity means any code generated from this doc will either be wrong-language or wrong-pattern.

**How to fix:**
Make a product decision, then apply it consistently across the entire `Frontend_EN.md`:

**If Kotlin (recommended for Android MVVM):**
- Replace all `.java` extensions with `.kt`
- Update `UiState` example in §4 to use a `data class` with `copy()` instead of a constructor-based Java class
- Replace `StateFlow<UiState>` pattern reference
- Update mapper examples to use Kotlin extension functions
- Note coroutines/Flow as the async mechanism instead of callbacks/RxJava

**If Java:**
- Keep `.java` extensions
- Explicitly state that UiState uses immutable POJOs with builders
- Remove any implicit Kotlin-specific references (sealed classes, data classes, extension functions)
- Specify RxJava or callbacks as the async mechanism

---

### [X] 4. Create tasks/ directory and initialize required files

**File to change:** Project root + `CLAUDE.md`

**Problem:**
`CLAUDE.md §1` and `§6` instruct the AI agent to write plans to `tasks/todo.md` and capture lessons to `tasks/lessons.md` after every correction. Neither file nor the `tasks/` directory exists in the project. Any AI agent following `CLAUDE.md` will either silently skip the task tracking step or create files in unexpected locations, breaking the self-improvement loop.

**How to fix:**
1. Create `tasks/` directory at the project root.
2. Create `tasks/todo.md` with this starter structure:
   ```markdown
   # Todo
   <!-- Claude writes implementation plans here before starting any task. -->
   <!-- Format: - [ ] Step description -->
   ```
3. Create `tasks/lessons.md` with this starter structure:
   ```markdown
   # Lessons
   <!-- Claude appends a new entry here after every user correction. -->
   <!-- Format: ## YYYY-MM-DD – [short pattern name] -->
   <!-- What went wrong, what the correct pattern is, rule to prevent recurrence. -->
   ```
4. Optionally add both files to `.gitignore` if you don't want them tracked, or commit them if you want a shared history of lessons.

---

## 🟡 Priority 2 — Fill Missing Contracts

---

### [ ] 5. Add MatchProposal method contracts to DOMAIN_CONTRACTS.md

**File to change:** `DOMAIN_CONTRACTS.md`

**Problem:**
`DOMAIN_CONTRACTS.md §2` defines states, transitions, invariants, and error codes for MatchProposal — but has no method contracts section (unlike WalkIntent §1.5 and WalkSession §3.8). The `accept()` and `reject()` methods are the most critical in the entire coordination flow (they trigger WalkSession creation) but their guards, parameters, and error codes are completely implicit. Code generated without this contract will be inconsistent and untestable.

**How to fix:**
Add `§2.5 Method Contracts` to DOMAIN_CONTRACTS.md. Minimum methods to define:

```
MatchProposal.acceptByUser(userId)
- Guards: status must be PENDING → throws PROPOSAL_ALREADY_TERMINAL or PROPOSAL_ALREADY_CONFIRMED
- Guards: userId must be either intentOwnerA or intentOwnerB → throws SESSION_USER_NOT_PARTICIPANT (or define PROPOSAL_USER_NOT_PARTICIPANT)
- Guards: user has not already accepted → record acceptance for that user
- If both users have now accepted → transition to CONFIRMED
- A CONFIRMED proposal must trigger WalkSession creation via Domain Service (not inside this method)

MatchProposal.rejectByUser(userId)
- Guards: status must be PENDING → throws PROPOSAL_ALREADY_TERMINAL
- Guards: userId must be a participant
- Transitions to REJECTED immediately (single rejection is enough)

MatchProposal.expire()
- Called only by system job (no userId check)
- Guards: status must be PENDING → throws PROPOSAL_ALREADY_TERMINAL
- Transitions to EXPIRED
```

---

### [ ] 6. Add User aggregate method contracts to DOMAIN_CONTRACTS.md

**File to change:** `DOMAIN_CONTRACTS.md`

**Problem:**
`DOMAIN_CONTRACTS.md §4` covers User states (PUBLIC/PRIVATE) and invariants but has no method contracts. Without them, code for user registration, login, profile update, and visibility toggling will be generated without a testable spec. The "logic stays in entity" hard constraint from `Backend_EN.md` cannot be enforced if there is no contract defining what that logic should be.

**How to fix:**
Add `§4.4 Method Contracts` to DOMAIN_CONTRACTS.md. Minimum methods to define:

```
User.register(email, phone, displayName, passwordHash)
- Validates: email format if provided → throws USER_INVALID_EMAIL_FORMAT (add to error codes)
- Validates: displayName not blank → throws USER_DISPLAY_NAME_BLANK
- Sets visibilityMode to PUBLIC by default

User.validateCredentials(rawPassword, passwordHasher)
- Compares rawPassword against stored hash via PasswordHasher interface
- Throws USER_INVALID_CREDENTIALS if mismatch
- Does NOT return the hash — only succeeds or throws

User.setVisibilityMode(mode)
- Guards: if mode == current visibilityMode → throws USER_ALREADY_PRIVATE or USER_ALREADY_PUBLIC
- Sets visibilityMode

User.updateProfile(displayName, tags, ...)
- Validates: displayName not blank → throws USER_DISPLAY_NAME_BLANK
- Updates allowed public profile fields only
```

---

### [ ] 7. Add TrustScore method contracts to DOMAIN_CONTRACTS.md

**File to change:** `DOMAIN_CONTRACTS.md`

**Problem:**
`DOMAIN_CONTRACTS.md §5` lists which events trigger TrustScore changes and states the floor-at-zero invariant, but has no method contracts. There is no spec for how the aggregate actually applies changes — no method names, no parameters, no floor enforcement logic. Code generated from this section will either put scoring logic in the application service (violating the Rich Domain rule) or implement it inconsistently across triggers.

**How to fix:**
Add `§5.4 Method Contracts` to DOMAIN_CONTRACTS.md:

```
TrustScore.applyPositive(reason)
- reason: enum (SESSION_COMPLETED | FIVE_STAR_REVIEW | FOLLOW_RECEIVED)
- Adds a defined delta per reason type (define deltas in a policy table)
- No upper cap (or define one if product decides)

TrustScore.applyNegative(reason)
- reason: enum (NO_SHOW | LATE_CANCELLATION_TIER1 | LATE_CANCELLATION_TIER2)
- Subtracts a defined delta per reason type (define deltas in a policy table)
- Enforces floor: if (score - delta) < 0 → set score to 0, never negative
- Throws TRUST_SCORE_BELOW_ZERO only if floor enforcement is not applied (i.e. treat as a guard, not a floor)

Policy table to add:
| Reason                  | Delta |
| ----------------------- | ----- |
| SESSION_COMPLETED       | +10   |
| FIVE_STAR_REVIEW        | +5    |
| NO_SHOW                 | -20   |
| LATE_CANCELLATION_TIER1 | -5    |
| LATE_CANCELLATION_TIER2 | -15   |
(Adjust values to product decision)
```

---

### [ ] 8. Add UserEmbedding section to DOMAIN_CONTRACTS.md

**File to change:** `DOMAIN_CONTRACTS.md`

**Problem:**
`AI_Feature_EN.md §IV Step 1` defines `UserEmbedding` as an aggregate with a vector structure, update triggers, and behavioral rules (e.g. embedding does not directly affect other aggregates). However, `DOMAIN_CONTRACTS.md` has no corresponding section. If a developer starts implementing this, there is no ground truth — the AI feature doc is a design doc, not a contract.

**How to fix:**
Add `§8. UserEmbedding Aggregate` to DOMAIN_CONTRACTS.md:

```
States: COLD_START (< 3 completed sessions), ACTIVE (>= 3 completed sessions)

Invariants:
1. UserEmbedding never directly affects WalkIntent, MatchProposal, or WalkSession state
2. UserEmbedding is only written by projection/event handlers, never by user action
3. A COLD_START embedding must fall back to geo/time/purpose matching (no vector scoring)

Update triggers:
- WalkSessionCompleted → update all vector dimensions
- WalkReviewCreated → update reliability_score dimension
- FollowRelationCreated → update acceptance_pattern dimension
- PartnerNoShowReported → update reliability_score dimension

Method contracts:
UserEmbedding.updateFromSessionCompleted(sessionData)
UserEmbedding.updateFromReview(rating)
UserEmbedding.updateFromFollow()
UserEmbedding.updateFromNoShow()
UserEmbedding.isReadyForPersonalization() → returns true only if status == ACTIVE

Error codes:
EMBEDDING_NOT_FOUND — findByUserId returns empty
```

---

### [ ] 9. Add QueryService prompt template to VIBE_CODING_GUIDE.md

**File to change:** `VIBE_CODING_GUIDE.md`

**Problem:**
`VIBE_CODING_GUIDE.md` only has a prompt template for `CommandService` (write side). `Backend_EN.md §3.3` explicitly separates CQRS — QueryService is a distinct class with distinct rules (no mutations, may return read models instead of domain entities, different test scenarios). Without a template, QueryService code will be generated ad-hoc and may accidentally contain write logic or skip proper test structure.

**How to fix:**
Add a `Prompt 3b — Generate QueryService` template immediately after the existing Prompt 3. It should cover:
- QueryService receives query parameters (not Command records)
- Methods must be named with `get`, `find`, `list`, `search` prefixes
- Must not call `repository.save()` under any circumstance
- May return domain entities, read models, or plain value types — specify which per method
- Test template needs 2 scenarios per method: happy path (repo returns data) + not found (repo returns empty → throw DomainException)

---

### [ ] 10. Add infrastructure repository test prompt to VIBE_CODING_GUIDE.md

**File to change:** `VIBE_CODING_GUIDE.md`

**Problem:**
The 10-step workflow in `VIBE_CODING_GUIDE.md` includes Step 9 (generate JdbcRepository) but has no corresponding test prompt. `TESTING.md §2` specifies that infra tests use `@DataJdbcTest` or `@SpringBootTest` with TestContainers against a real DB — but there is no template or prompt to generate this. Developers skip infra tests because there is no scaffold to follow.

**How to fix:**
Add a `Prompt 7b — Generate Infrastructure Repository Test` after the existing Prompt 7. It should include:
- Use `@DataJdbcTest` (or `@SpringBootTest` if complex joins needed)
- Use TestContainers to spin up a real DB instance
- Required scenarios: save and findById round-trip, findById returns empty for unknown ID, any custom query methods return correct results
- Fixture SQL or test data setup via `@Sql` or a test data builder
- Template class structure following the mirror-path convention from `TESTING.md §1`

---

## 🟢 Priority 3 — Polish

---

### [ ] 11. Add frontend vibe coding workflow to VIBE_CODING_GUIDE.md

**File to change:** `VIBE_CODING_GUIDE.md`

**Problem:**
`VIBE_CODING_GUIDE.md` covers the backend only. The frontend architecture in `Frontend_EN.md` is well-defined but has no parallel prompt templates. Developers working on the Android side have no guided workflow for generating ViewModel, UiState, Screen, RepositoryImpl, or Mapper classes consistently.

**How to fix:**
Add a `Frontend Feature Workflow` section to `VIBE_CODING_GUIDE.md` with these steps mirroring the backend:
1. Generate domain model + repository interface + domain service
2. Generate domain service tests
3. Generate RepositoryImpl + remote DTO + mapper
4. Generate ViewModel + UiState/UiEvent/UiEffect
5. Generate Screen
6. Run all tests

Include prompt templates for each step, specifying which sections of `Frontend_EN.md` to paste as context.

---

### [ ] 12. Add GlobalExceptionHandler test template to TESTING.md

**File to change:** `TESTING.md`

**Problem:**
`TESTING.md §4.4` requires two test scenarios for `GlobalExceptionHandler` but unlike sections §6.1–§6.3, there is no code template for it. The conventions document is inconsistent — three layers have templates, one does not. Developers generating exception handler tests from scratch will produce varying structures.

**How to fix:**
Add `§6.5 GlobalExceptionHandler Test Template` to TESTING.md:

```java
// presentation/exception/GlobalExceptionHandlerTest.java
@WebMvcTest(controllers = { /* any controller */ })
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;
    @MockBean SomeCommandService commandService;

    @Test
    void domainException_shouldReturn400_withErrorCodeInBody() throws Exception {
        given(commandService.someMethod(any()))
            .willThrow(new DomainException(SomeErrorCode.SOME_ERROR));

        mockMvc.perform(post("/api/v1/some-endpoint")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"field": "value"}"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("SOME_ERROR"));
    }

    @Test
    void validationException_shouldReturn422_withFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/some-endpoint")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.success").value(false));
    }
}
```

---

### [ ] 13. Add frontend usage section to DOMAIN_CONTRACTS.md §7

**File to change:** `DOMAIN_CONTRACTS.md`

**Problem:**
`DOMAIN_CONTRACTS.md §7` ("How to Use This Document in Vibe Coding") only explains backend usage — generating entity code and tests. The same contracts are the source of truth for the Android domain layer too, but there are no instructions for how to use them in a frontend context.

**How to fix:**
Add a `§7.4 When generating frontend domain code` subsection:

```
## When generating frontend domain service code
Paste the relevant aggregate section (states + invariants + error codes) into your prompt:
> "Implement IntentService.createIntent() on the Android domain layer according to
>  these contracts: [paste section 1.3 invariants + 1.5 method contracts]"

## When generating frontend mappers
Paste the domain model fields alongside the DTO structure:
> "Write IntentDtoToDomainMapper using these domain invariants as validation rules:
>  [paste relevant invariants]"

## When a frontend test fails
Same protocol as backend — compare against DOMAIN_CONTRACTS.md first.
The contract is the referee for both platforms.
```

---

### [ ] 14. Add cross-aggregate Domain Service prompt to VIBE_CODING_GUIDE.md

**File to change:** `VIBE_CODING_GUIDE.md`

**Problem:**
`DOMAIN_CONTRACTS.md §6` defines 4 critical cross-aggregate rules (e.g. WalkSession can only be created from a CONFIRMED MatchProposal with both intents still OPEN; no duplicate PENDING proposals per intent). These rules live in Domain Services, not in individual entities. `VIBE_CODING_GUIDE.md` has no prompt template for generating or testing Domain Services that span multiple aggregates. This is the most complex code in the system and the most likely to be generated incorrectly without guidance.

**How to fix:**
Add a `Prompt 2b — Generate Cross-Aggregate Domain Service` to VIBE_CODING_GUIDE.md:

```
You are implementing a Domain Service for WalkMate that enforces cross-aggregate invariants.

Cross-aggregate rules to enforce:
[paste the relevant rules from DOMAIN_CONTRACTS.md §6]

Aggregates involved:
[paste each relevant aggregate section]

Task:
Generate `domain/<domain-name>/<Name>DomainService.java`

Rules:
- This is a domain service, not an application service — it contains real business logic
- It receives domain entities as parameters, not IDs or Commands
- It calls entity methods and throws DomainException for invariant violations
- It does NOT call repositories directly — the application service loads entities first
- It does NOT handle persistence — that is the application service's responsibility
- Zero Spring/framework imports
```

Also add a corresponding test prompt requiring one test per cross-aggregate rule in §6.