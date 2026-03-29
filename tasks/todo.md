# ChatRoom Aggregate Implementation Plan
<!-- Created: 2026-03-29 | Revised: 2026-03-30 -->

## Dependency Discovery

**Critical Finding:** WalkSession does not exist as Java code yet (only `match_proposal` DB
schema exists). ChatRoom is created atomically with WalkSession and closes when WalkSession
goes terminal — WalkSession must be implemented first as a prerequisite.

**Also:** The current `GlobalExceptionHandler` maps ALL `DomainException` to HTTP 400.
The ChatRoom spec requires 404, 409, 403, and 400 per error code. The `ErrorCode` interface
must be extended with a default HTTP status, and the handler updated before any new errors
can work correctly.

---

## Phase 0 — Foundation Fixes (prerequisite, minimal impact)

- [ ] P0.1 — Add `default int httpStatus() { return 400; }` to `ErrorCode` interface
  - Keeps Spring out of domain layer (plain int, not HttpStatus enum)
  - All existing enums default to 400 — zero behavior change for existing features
  - `GlobalExceptionHandler` reads `ex.getErrorCode().httpStatus()` instead of hard-coding 400

- [ ] P0.2 — Update `GlobalExceptionHandler.handleDomainException()` to use
  `HttpStatus.resolve(ex.getErrorCode().httpStatus())`
  - Single-line change; all existing error codes stay at 400

---

## Phase 1 — WalkSession Aggregate (prerequisite for ChatRoom)

Following §3 of DOMAIN_CONTRACTS.md exactly.

- [ ] 1.1 — `SessionStatus` enum
  (`PENDING`, `ACTIVE`, `COMPLETED`, `NO_SHOW`, `CANCELLED`, `ABORTED`)

- [ ] 1.2 — `WalkSession` domain entity
  - Fields: `id`, `proposalId`, `participantA`, `participantB`,
    `scheduledStart`, `scheduledEnd`, `status`, `createdAt`, `version`
  - Factory: `WalkSession.create(proposalId, participantA, participantB, start, end)`
  - `isTerminal()` — true for COMPLETED, NO_SHOW, CANCELLED, **ABORTED**
  - `isChatWritable()` — true only for PENDING and ACTIVE (§3.5)
  - State mutations needed for wiring tests:
    - `complete()` — guards against non-ACTIVE state, throws `SESSION_NOT_ACTIVE`
    - `cancel(requestingUserId, cancellationTime)` — guards PENDING only, throws `SESSION_NOT_PENDING`
    - `markNoShow()` — system job only, guards PENDING only, throws `SESSION_NOT_PENDING`
    - `abort(requestingUserId, abortReason)` — guards ACTIVE only, throws `SESSION_NOT_ACTIVE`;
      `abortReason` must be one of `INJURY | SAFETY | ENVIRONMENT | OTHER`,
      throws `SESSION_INVALID_ABORT_REASON` otherwise
    - Each method throws `SESSION_ALREADY_TERMINAL` if called on any terminal state
  - Rehydration constructor

- [ ] 1.3 — `WalkSessionErrorCode` enum (implements `ErrorCode`)
  - `SESSION_NOT_FOUND` → 404
  - `SESSION_ALREADY_TERMINAL` → 409
  - `SESSION_NOT_ACTIVE` → 409
  - `SESSION_NOT_PENDING` → 409
  - `SESSION_INVALID_ABORT_REASON` → 400
  - Override `httpStatus()` per entry

- [ ] 1.4 — `WalkSessionRepository` interface (domain layer)
  - `save(WalkSession)`
  - `findById(String) → Optional<WalkSession>`

- [ ] 1.5 — DB migration: `V5__create_walk_session.sql`
  > **Scope note:** This migration is intentionally minimal for the ChatRoom feature scope.
  > Remaining columns (`abort_reason`, `cancelled_by`, activation timestamps, `total_distance`,
  > `total_duration`, `source_intent_id_a/b`, etc.) will be added in V7+ when those
  > WalkSession methods are fully implemented. This avoids coupling ChatRoom delivery
  > to the full WalkSession feature scope.

  - CREATE TYPE `session_status` AS ENUM('PENDING','ACTIVE','COMPLETED','NO_SHOW','CANCELLED','ABORTED')
  - `walk_session` table:
    - `session_id UUID PK DEFAULT gen_random_uuid()`
    - `proposal_id UUID NOT NULL REFERENCES match_proposal(proposal_id)`
    - `participant_a UUID NOT NULL REFERENCES user_account(user_id)`
    - `participant_b UUID NOT NULL REFERENCES user_account(user_id)`
    - `scheduled_start TIMESTAMP NOT NULL`
    - `scheduled_end TIMESTAMP NOT NULL`
    - `status session_status NOT NULL DEFAULT 'PENDING'`
    - `created_at TIMESTAMP NOT NULL DEFAULT NOW()`
    - `version BIGINT NOT NULL DEFAULT 0`
    - CONSTRAINT: `scheduled_end > scheduled_start`
  - Indexes on `(participant_a, status)`, `(participant_b, status)`, `proposal_id`

- [ ] 1.6 — `WalkSessionJdbcRepository` (infrastructure layer)
  - `save()` — ON CONFLICT (session_id) DO UPDATE (status, version)
  - `findById()` — SELECT + row mapper

- [ ] 1.7a — **Prerequisite:** Add `PROPOSAL_NOT_CONFIRMED → 409` to `MatchProposalErrorCode`
  - This error code does not exist yet and must be added before `WalkSessionCommandService` can compile
  - Override `httpStatus()` to return 409
  - Add to HTTP Status Mapping Summary table

- [ ] 1.7b — `CreateWalkSessionCommand` record + `WalkSessionCommandService`
  - `createSession(command)`:
    1. Fetch `MatchProposal` by `proposalId` — throw `PROPOSAL_NOT_FOUND` if absent
    2. Assert `proposal.status == CONFIRMED` — throw `PROPOSAL_NOT_CONFIRMED` (409) if not
       > Enforces §6 rule 1a: WalkSession can only be created if MatchProposal is CONFIRMED.
    3. Assert both referenced WalkIntents are still `OPEN` — throw `INTENT_NOT_OPEN` if not (§6 rule 1b)
    4. Call `WalkSession.create(...)` factory
    5. Persist session via `walkSessionRepository.save()`
    6. Create `ChatRoom` atomically (call `chatRoomCommandService.createChatRoom()` in the same `@Transactional`)
  - `transitionToTerminal(String sessionId, SessionStatus target)`:
    - Fetches the session, calls the appropriate domain method based on `target`:
      - `COMPLETED` → `session.complete(completionTime)`
      - `CANCELLED` → `session.cancel(requestingUserId, cancellationTime)`
      - `NO_SHOW` → `session.markNoShow()`
      - `ABORTED` → `session.abort(requestingUserId, abortReason)`
    - Persists updated session
    - Calls `chatRoomCommandService.closeChatRoom(sessionId)` in the same `@Transactional`
    - **Covers all four terminal states: COMPLETED, CANCELLED, NO_SHOW, ABORTED**
      (no "as needed" — ABORTED is a terminal state per §3.1 and must close the chat room)

---

## Phase 2 — ChatRoom Aggregate

- [ ] 2.1 — `ChatRoomStatus` enum (`OPEN`, `CLOSED`)

- [ ] 2.2 — `ChatMessage` entity
  - Fields: `id`, `roomId`, `senderId`, `content`, `sentAt`
  - Rehydration constructor (created only inside `ChatRoom.sendMessage()`)

- [ ] 2.3 — `ChatRoom` aggregate root
  - Fields: `id`, `sessionId`, `participantA`, `participantB`, `status`, `createdAt`, `version`
  - Factory: `ChatRoom.create(sessionId, participantA, participantB)` → status OPEN
  - Rehydration constructor
  - `sendMessage(senderId, content) → ChatMessage`
    - Guard 1: status != OPEN → throw `CHAT_ROOM_CLOSED` (409)
    - Guard 2: senderId not in {participantA, participantB} → throw `CHAT_NOT_PARTICIPANT` (403)
    - Guard 3: content.trim().isEmpty() → throw `CHAT_MESSAGE_BLANK` (400)
    - Returns new `ChatMessage` (service persists it; aggregate does not touch repo)
  - `close()`
    - If already CLOSED → no-op (idempotent, per §9.4)
    - Sets status to CLOSED, increments version

- [ ] 2.4 — `ChatRoomErrorCode` enum (implements `ErrorCode`)
  - `CHAT_ROOM_NOT_FOUND` → 404
  - `CHAT_ROOM_CLOSED` → 409
  - `CHAT_NOT_PARTICIPANT` → 403
  - `CHAT_MESSAGE_BLANK` → 400
  - Override `httpStatus()` per entry

- [ ] 2.5 — `ChatRoomRepository` interface (domain layer)
  - `save(ChatRoom)`
  - `findBySessionId(String sessionId) → Optional<ChatRoom>`

- [ ] 2.6 — `ChatMessageRepository` interface (domain layer)
  - `save(ChatMessage)`
  - `findByRoomId(String roomId) → List<ChatMessage>` (ORDER BY sent_at ASC)

---

## Phase 3 — Database Migration

- [ ] 3.1 — `V6__create_chat_room.sql`
  - CREATE TYPE `chat_status` AS ENUM ('OPEN', 'CLOSED')
    > Type name is `chat_status` (matches existing db.sql reference). Do NOT use `chat_room_status`.
  - `chat_room` table:
    - `room_id UUID PK DEFAULT gen_random_uuid()`
    - `session_id UUID NOT NULL UNIQUE REFERENCES walk_session(session_id)`
    - `participant_a UUID NOT NULL REFERENCES user_account(user_id)`
    - `participant_b UUID NOT NULL REFERENCES user_account(user_id)`
    - `status chat_status NOT NULL DEFAULT 'OPEN'::chat_status`
    - `created_at TIMESTAMP NOT NULL DEFAULT NOW()`
    - `version BIGINT NOT NULL DEFAULT 0`
    - INDEX on `session_id` (for findBySessionId)
  - `chat_message` table:
    - `message_id UUID PK DEFAULT gen_random_uuid()`
    - `room_id UUID NOT NULL REFERENCES chat_room(room_id) ON DELETE CASCADE`
    - `sender_id UUID NOT NULL REFERENCES user_account(user_id)`
    - `content TEXT NOT NULL CHECK (TRIM(content) <> '')`
      > Must use `TRIM()` — bare `<> ''` passes whitespace-only strings,
      > which would violate invariant §9.2.4 and mismatch the domain guard `content.trim().isEmpty()`
    - `sent_at TIMESTAMP NOT NULL DEFAULT NOW()`
    - INDEX on `(room_id, sent_at)` for ordered message queries

- [ ] 3.2 — `V7__enable_chat_rls.sql` — Enable RLS on chat tables in Supabase
  > **Why this is required:** Supabase Realtime broadcasts row-level changes to any
  > subscribed client. Without RLS, any authenticated user can subscribe to any room's
  > message feed — a critical data leak. This migration must run before the Android
  > Realtime integration goes live.

  ```sql
  -- Enable RLS
  ALTER TABLE chat_room ENABLE ROW LEVEL SECURITY;
  ALTER TABLE chat_message ENABLE ROW LEVEL SECURITY;

  -- chat_room: participants can read their own rooms
  CREATE POLICY chat_room_select_policy ON chat_room
    FOR SELECT
    USING (
      auth.uid() = participant_a OR auth.uid() = participant_b
    );

  -- chat_message SELECT: only participants of the owning room can read
  CREATE POLICY chat_message_select_policy ON chat_message
    FOR SELECT
    USING (
      EXISTS (
        SELECT 1 FROM chat_room cr
        WHERE cr.room_id = chat_message.room_id
          AND (auth.uid() = cr.participant_a OR auth.uid() = cr.participant_b)
      )
    );

  -- chat_message INSERT: only participants can insert, only into OPEN rooms,
  -- and sender_id must match the authenticated user (no spoofing)
  CREATE POLICY chat_message_insert_policy ON chat_message
    FOR INSERT
    WITH CHECK (
      auth.uid() = sender_id
      AND EXISTS (
        SELECT 1 FROM chat_room cr
        WHERE cr.room_id = chat_message.room_id
          AND cr.status = 'OPEN'
          AND (auth.uid() = cr.participant_a OR auth.uid() = cr.participant_b)
      )
    );
  ```

  > **Note:** The backend writes messages via a service account / connection string that
  > bypasses RLS (Postgres superuser role). RLS here governs Supabase Realtime subscriptions
  > and direct client access only — it does not block your Spring backend.

---

## Phase 4 — Infrastructure Repositories

- [ ] 4.1 — `ChatRoomJdbcRepository` (implements `ChatRoomRepository`)
  - `save()`: ON CONFLICT (room_id) DO UPDATE (status, version)
  - `findBySessionId()`: SELECT … WHERE session_id = :sessionId
  - Row mapper must include: `room_id`, `session_id`, `participant_a`, `participant_b`,
    `status`, `created_at`, `version`
    > participant columns are required — `ChatRoomQueryService` uses them for access checks

- [ ] 4.2 — `ChatMessageJdbcRepository` (implements `ChatMessageRepository`)
  - `save()`: plain INSERT (messages are immutable — no update path needed)
  - `findByRoomId()`: SELECT … WHERE room_id = :roomId ORDER BY sent_at ASC

---

## Phase 5 — Application Services

- [ ] 5.1 — `SendMessageCommand` record (`sessionId`, `senderId`, `content`)

- [ ] 5.2 — `ChatRoomCommandService` (`@Service @Transactional @RequiredArgsConstructor`)
  - `createChatRoom(String sessionId, String participantA, String participantB)`:
    1. Call `ChatRoom.create(sessionId, participantA, participantB)`
    2. `chatRoomRepository.save(room)`
    > Called only by `WalkSessionCommandService.createSession()` — never directly by controller
  - `sendMessage(SendMessageCommand)`:
    1. `chatRoomRepository.findBySessionId(sessionId)` → throw `CHAT_ROOM_NOT_FOUND` if empty
    2. `room.sendMessage(senderId, content)` → returns `ChatMessage`
    3. `chatMessageRepository.save(message)`
    4. Return saved `ChatMessage`
  - `closeChatRoom(String sessionId)` (`@Transactional`):
    1. `chatRoomRepository.findBySessionId(sessionId)` → if empty, no-op (room may not exist yet)
    2. `room.close()` (idempotent at domain level)
    3. `chatRoomRepository.save(room)`

- [ ] 5.3 — `ChatRoomQueryService` (`@Service @Transactional(readOnly=true) @RequiredArgsConstructor`)
  - `getMessages(String sessionId, String requesterId)`:
    1. `chatRoomRepository.findBySessionId(sessionId)` → throw `CHAT_ROOM_NOT_FOUND` if empty
    2. Check requesterId is participantA or participantB → throw `CHAT_NOT_PARTICIPANT` if not
    3. Return `chatMessageRepository.findByRoomId(room.getId())`
    > **No status check here — intentional product decision:**
    > All CLOSED rooms (COMPLETED, CANCELLED, NO_SHOW, ABORTED) remain readable forever.
    > This overrides the "Closed" entries in §3.5 of DOMAIN_CONTRACTS.md for read access.
    > DOMAIN_CONTRACTS.md §3.5 should be updated to reflect this.
    > Write access is still blocked by the domain guard in `ChatRoom.sendMessage()`.
    >
    > **Pagination deferred:** `getMessages()` currently returns all messages for a room
    > in a single response. For long sessions this may produce large payloads. Cursor-based
    > pagination should be added in a future iteration.

---

## Phase 6 — Cross-Aggregate Domain Service Wiring

- [ ] 6.1 — In `WalkSessionCommandService.transitionToTerminal()`:
  - After persisting the terminal WalkSession, call `chatRoomCommandService.closeChatRoom(sessionId)`
  - All in one `@Transactional` — atomic: session goes terminal AND chat room closes together
  - **Explicitly covers all four terminal states:**
    | Target State | Domain Method Called                                 |
    | ------------ | ---------------------------------------------------- |
    | `COMPLETED`  | `session.complete(completionTime)`                   |
    | `CANCELLED`  | `session.cancel(requestingUserId, cancellationTime)` |
    | `NO_SHOW`    | `session.markNoShow()`                               |
    | `ABORTED`    | `session.abort(requestingUserId, abortReason)`       |
  - After any of the four → `chatRoomCommandService.closeChatRoom(sessionId)`

---

## Phase 7 — Presentation Layer

- [ ] 7.1 — `SendMessageRequest` record
  - `@NotBlank(message="content must not be blank") String content`

- [ ] 7.2 — `ChatMessageResponse` record
  - `String id`, `@JsonProperty("sender_id") String senderId`,
    `String content`, `@JsonProperty("sent_at") String sentAt`

- [ ] 7.3 — `ChatRoomResponse` record
  - `@JsonProperty("room_id") String roomId`, `@JsonProperty("session_id") String sessionId`,
    `String status`, `List<ChatMessageResponse> messages`
  > Pagination deferred — full message list returned for now

- [ ] 7.4 — `ChatMapper` (entity → response DTO)

- [ ] 7.5 — `ChatController` (`@RestController @RequestMapping("/api/v1/sessions/{sessionId}/chat")`)
  - `POST /api/v1/sessions/{sessionId}/chat`
    - senderId from `@AuthenticationPrincipal UserPrincipal` (never from request body)
    - Returns 201 + `ApiResponse<ChatMessageResponse>`
  - `GET /api/v1/sessions/{sessionId}/chat`
    - requesterId from `@AuthenticationPrincipal UserPrincipal`
    - Returns 200 + `ApiResponse<ChatRoomResponse>`

- [ ] 7.6 — Add `/api/v1/sessions/**` to authenticated endpoints in `SecurityConfig`

---

## Phase 8 — Tests

### Unit Tests (pure domain — no Spring context, no DB)

- [ ] T1 — `ChatRoomTest` (JUnit 5)
  - `sendMessage_throwsChatRoomClosed_whenStatusIsClosed`
  - `sendMessage_throwsChatNotParticipant_whenSenderIsUnknown`
  - `sendMessage_throwsChatMessageBlank_whenContentIsEmpty`
  - `sendMessage_throwsChatMessageBlank_whenContentIsWhitespaceOnly`
  - `sendMessage_success_returnsMessageWithCorrectFields`
  - `close_setsStatusToClosed`
  - `close_isIdempotent_whenCalledTwice` (no exception on second call)

### Integration Tests (@SpringBootTest with real DB)

- [ ] T2 — `ChatRoomCommandServiceIntegrationTest`
  - `sendMessage_persistsMessageToDb`
  - `sendMessage_throwsChatRoomNotFound_whenNoRoomExistsForSession`
  - `sendMessage_throwsChatRoomClosed_whenRoomIsClosed`
  - `sendMessage_throwsChatNotParticipant_whenSenderNotInSession`
  - `sendMessage_throwsChatMessageBlank_whenContentIsBlank`

- [ ] T3 — `ChatRoomQueryServiceIntegrationTest`
  - `getMessages_returnsMessagesOrderedBySentAt`
  - `getMessages_throwsChatRoomNotFound_whenNoRoomExistsForSession`
  - `getMessages_throwsChatNotParticipant_whenRequesterIsNotParticipant`
  - `getMessages_succeeds_whenRoomIsClosedDueToCompletedSession` ← confirms read-always policy
  - `getMessages_succeeds_whenRoomIsClosedDueToCancelledSession` ← confirms read-always policy

- [ ] T4 — `WalkSessionChatRoomWiringIntegrationTest`
  - `chatRoom_closesClosed_whenWalkSessionCompleted`
  - `chatRoom_closesClosed_whenWalkSessionCancelled`
  - `chatRoom_closesClosed_whenWalkSessionNoShow`
  - `chatRoom_closesClosed_whenWalkSessionAborted`  ← was missing, now explicit
  - `chatRoom_remainsOpen_whenWalkSessionStillActive`

---

## Phase 9 — Android Realtime Integration (Supabase CDC)

> **Architecture:** The Spring backend handles all writes via REST (POST /chat sends a message,
> which is persisted via JDBC). Supabase Realtime then broadcasts the INSERT event on
> `chat_message` to any subscribed Android client. No WebSocket code is needed on the backend.

- [ ] 9.1 — Supabase Realtime client setup
  - Add `io.github.jan-tennert.supabase:realtime-kt` dependency to Android `build.gradle`
  - Configure Supabase client with anon key + project URL (same client used for auth)
  - The user's Supabase JWT (issued at login) must be stored and reused for Realtime auth —
    no second authentication flow needed

- [ ] 9.2 — ChatRoomViewModel subscription lifecycle
  - **On screen enter (room OPEN):**
    - Subscribe to `chat_message` table filtered by `room_id = :currentRoomId`
    - Also subscribe to `walk_session` table filtered by `session_id = :currentSessionId`
      for status change events (needed to detect terminal transitions)
  - **On new `chat_message` INSERT event:**
    - Append the new `ChatMessage` to the UiState message list
  - **On `walk_session` UPDATE event where `status` is terminal**
    (COMPLETED, CANCELLED, NO_SHOW, ABORTED):
    - Update UiState to reflect session is terminal (disable send input)
    - Unsubscribe from both Realtime channels
    > This replaces the vague "push or polling" — Supabase Realtime on `walk_session`
    > is the authoritative signal, consistent with already using Supabase Realtime.
  - **On screen exit (any reason):**
    - Always unsubscribe from both channels to prevent memory/connection leaks

- [ ] 9.3 — RLS token threading
  - Android must pass the authenticated user's Supabase JWT when opening Realtime channels
  - The RLS policies in V7 will reject subscriptions where `auth.uid()` does not match
    a participant — this is the security enforcement layer for Realtime
  - Verify the login flow persists the Supabase access token for reuse by the Realtime client

---

## HTTP Status Mapping Summary

| Error Code                     | HTTP | Source           |
| ------------------------------ | ---- | ---------------- |
| `CHAT_ROOM_NOT_FOUND`          | 404  | Phase 0 + 2.4    |
| `CHAT_ROOM_CLOSED`             | 409  | Phase 0 + 2.4    |
| `CHAT_NOT_PARTICIPANT`         | 403  | Phase 0 + 2.4    |
| `CHAT_MESSAGE_BLANK`           | 400  | 2.4 (default)    |
| `SESSION_NOT_FOUND`            | 404  | Phase 0 + 1.3    |
| `SESSION_ALREADY_TERMINAL`     | 409  | Phase 0 + 1.3    |
| `SESSION_NOT_ACTIVE`           | 409  | Phase 0 + 1.3    |
| `SESSION_NOT_PENDING`          | 409  | Phase 0 + 1.3    |
| `SESSION_INVALID_ABORT_REASON` | 400  | 1.3 (default)    |
| `PROPOSAL_NOT_CONFIRMED`       | 409  | Phase 1.7a (new) |

---

## New Files

```
domain/chatroom/
  ChatRoomStatus.java, ChatRoom.java, ChatMessage.java
  ChatRoomErrorCode.java, ChatRoomRepository.java, ChatMessageRepository.java

domain/walksession/
  SessionStatus.java, WalkSession.java
  WalkSessionErrorCode.java, WalkSessionRepository.java

application/chatroom/
  SendMessageCommand.java
  ChatRoomCommandService.java, ChatRoomQueryService.java

application/walksession/
  CreateWalkSessionCommand.java, WalkSessionCommandService.java

infrastructure/repository/chatroom/
  ChatRoomJdbcRepository.java, ChatMessageJdbcRepository.java

infrastructure/repository/walksession/
  WalkSessionJdbcRepository.java

presentation/controller/chatroom/ChatController.java
presentation/dto/request/chatroom/SendMessageRequest.java
presentation/dto/response/chatroom/ChatMessageResponse.java, ChatRoomResponse.java
presentation/mapper/chatroom/ChatMapper.java

db/migration/V5__create_walk_session.sql
db/migration/V6__create_chat_room.sql
db/migration/V7__enable_chat_rls.sql
```

## Modified Files

```
domain/shared/exception/ErrorCode.java             — add default httpStatus()
presentation/exception/GlobalExceptionHandler.java — use error code httpStatus()
infrastructure/config/SecurityConfig.java          — add /api/v1/sessions/** as authenticated
domain/matchproposal/MatchProposalErrorCode.java   — add PROPOSAL_NOT_CONFIRMED → 409 (Phase 1.7a)
DOMAIN_CONTRACTS.md §3.5                           — update read access table to reflect
                                                     read-always policy for CLOSED rooms
```

---

## Changelog (2026-03-30 revision)

| #   | Issue Fixed                                                              | Location                              |
| --- | ------------------------------------------------------------------------ | ------------------------------------- |
| 1   | ABORTED made explicit in all terminal state coverage                     | Phase 1.2, 1.3, 6.1, T4               |
| 2   | V5 scope note added — intentionally minimal, deferred columns documented | Phase 1.5                             |
| 3   | `abort()` method added to WalkSession entity contract                    | Phase 1.2                             |
| 4   | MatchProposal CONFIRMED guard added to `createSession()`                 | Phase 1.7                             |
| 5   | `CHECK (content <> '')` corrected to `CHECK (TRIM(content) <> '')`       | Phase 3.1                             |
| 6   | RLS INSERT policy corrected with explicit JOIN to chat_room              | Phase 3.2 → V7                        |
| 7   | Android terminal signal source specified (Realtime on walk_session)      | Phase 9.2                             |
| 8   | V7 RLS migration file added to New Files list                            | New Files                             |
| 9   | T4 ABORTED wiring test added                                             | Phase 8 T4                            |
| 10  | Pagination deferred note added to getMessages and ChatRoomResponse       | Phase 5.3, 7.3                        |
| 11  | Read-always policy documented explicitly in getMessages                  | Phase 5.3                             |
| 12  | `chat_status` ENUM name fixed (was `chat_room_status`)                   | Phase 3.1                             |
| 13  | participant columns called out explicitly in row mapper                  | Phase 4.1                             |
| 14  | `createChatRoom()` added to ChatRoomCommandService                       | Phase 5.2                             |
| 16  | `PROPOSAL_NOT_CONFIRMED` split into explicit prerequisite task 1.7a      | Phase 1.7, HTTP table, Modified Files |