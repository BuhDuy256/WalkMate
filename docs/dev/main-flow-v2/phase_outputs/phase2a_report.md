# Phase 2A Completion Report

**Date:** 2026-04-07  
**Branch:** `implement/realtime`  
**Steps covered:** 2.1 (MongoDB infra), 2.2 (chat room init hook), 2.3 (chat room close hooks)

---

## Files Modified / Created

```
backend/build.gradle                                                              (modified)
backend/src/main/resources/application.properties                                 (modified)
backend/.env                                                                       (modified)
backend/src/main/java/com/walkmate/infrastructure/repository/chat/document/ChatRoomDocument.java   (new)
backend/src/main/java/com/walkmate/application/chat/ChatRoomRepository.java       (new)
backend/src/main/java/com/walkmate/infrastructure/repository/chat/MongoChatRoomRepository.java    (new)
backend/src/main/java/com/walkmate/application/proposal/MatchingCommandService.java (modified)
backend/src/main/java/com/walkmate/application/session/SessionCommandService.java  (modified)
docs/dev/main-flow-v2/implementation_plan.md                                       (checklist updated)
```

---

## Verification Results (Manual)

All five pre-implementation checks were performed against the live codebase.

### 1. `build.gradle` — MongoDB dependency
**Before:** `spring-boot-starter-data-mongodb` absent.  
**Verdict: GAP-17 confirmed — dependency not present. Proceed.**

### 2. `application.properties` — MongoDB URI
**Before:** No `spring.data.mongodb.*` keys present.  
**Verdict: GAP-17 confirmed — not configured. Proceed.**

### 3. `ChatRoomRepository` — port / adapter / document
**Search result:** Zero matches across the entire `backend/` tree.  
**Verdict: Confirmed absent. Proceed.**

### 4. `MatchingCommandService.acceptProposal()` — afterCommit insertion point
**File:** `application/proposal/MatchingCommandService.java`  
**Line:** `walkSessionRepository.save(session)` at line 233 (pre-edit).  
**afterCommit hook inserted immediately after that save, before the notification block.**

### 5. `SessionCommandService` — terminal transition methods
**File:** `application/session/SessionCommandService.java`  
- `completeSession()` — `sessionRepository.save(session)` at line 126  
- `cancelSession()`  — `sessionRepository.save(session)` at line 90  
- `abortSession()`   — `sessionRepository.save(session)` at line 105  
- `handleExpiredSessions()` — two paths: auto-cancel loop (line 170) and auto-complete loop (line 184)  
**afterCommit hooks inserted in all four locations.**

---

## MongoDB Integration State

| Check | Status |
|---|---|
| Dependency `spring-boot-starter-data-mongodb` in `build.gradle` | **yes** |
| `MONGODB_URI` / `MONGODB_DATABASE` in `application.properties` | **yes** |
| `MONGODB_URI` / `MONGODB_DATABASE` in `.env` (placeholder values) | **yes** |
| `ChatRoomRepository` port path | `application/chat/ChatRoomRepository.java` |
| `MongoChatRoomRepository` adapter path | `infrastructure/repository/chat/MongoChatRoomRepository.java` |
| `ChatRoomDocument` collection name | `chat_rooms` |
| afterCommit hook in `acceptProposal()` | **yes** |
| afterCommit hooks in `completeSession()` / `cancelSession()` / `abortSession()` / `handleExpiredSessions()` | **yes** (all four) |
| `MongoTransactionManager` registered | **no** |

---

## Deviations from Plan

### 1. `@Slf4j` added to `MatchingCommandService`
**Plan said:** Add `ChatRoomRepository` dependency and hook.  
**Actual:** `MatchingCommandService` lacked `@Slf4j` / `log` — the hook's error log requires it. Added `@Slf4j` annotation.  
**Reason:** Without `log`, the catch block inside afterCommit() would not compile.

### 2. `.env` uses placeholder MONGODB_URI value
**Plan said:** Add both variables to `.env` (local dev).  
**Actual:** Added `MONGODB_URI=mongodb+srv://<user>:<password>@<cluster>.mongodb.net/?retryWrites=true&w=majority` and `MONGODB_DATABASE=walkmate` as template placeholders.  
**Reason:** Real Atlas credentials are environment-specific and must not be committed to source control. The developer replaces the placeholders before running locally.

### 3. `handleExpiredSessions()` — hook registered inside `@Transactional` boundary
**Plan said:** "Also apply to the scheduler sweep in `handleExpiredSessions()` for sessions that auto-complete or auto-cancel."  
**Observation:** `handleExpiredSessions()` is itself `@Transactional`. The `TransactionSynchronizationManager` hook therefore fires after the method-level transaction commits, which is correct. Each session-level save and the hook fire together within the same transaction boundary — matching the architectural contract.

---

## Open Issues / Blockers

### 1. `.env` MONGODB_URI is a placeholder
The real Atlas connection string must be filled in before the application can start. Until then, Spring will throw a `BeanCreationException` on startup because `MONGODB_URI` resolves to a literal `<user>` string that Atlas will reject.

### 2. No test coverage for Phase 2A changes
No unit or integration tests were written for steps 2.1–2.3 (same pattern as prior phases). Phase 3 should include:
- `MongoChatRoomRepository.initRoom()` — verify upsert idempotency
- `MongoChatRoomRepository.closeRoom()` — verify status and closedAt set correctly
- `MatchingCommandService.acceptProposal()` — verify afterCommit hook fires; verify chat room is not initialized on PostgreSQL rollback
- `SessionCommandService` terminal transitions — verify afterCommit hook fires for each

### 3. No MongoTransactionManager — reconciliation path undefined
Per the architectural contract, if MongoDB write fails after PostgreSQL commits, the WalkSession is valid but has no chat room. A reconciliation job to detect and repair this gap is not yet implemented. This is a known accepted risk per ADR-001. (Not a code fix — accepted architectural trade-off.)
