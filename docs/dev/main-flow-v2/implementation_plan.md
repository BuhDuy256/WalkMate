# Implementation Plan: Close SSOT Gaps

**Date:** 2026-04-07  
**Based on:** `docs/dev/main-flow-v2/gap_analysis.md`  
**Execution order:** P0 first (state machine must be correct before anything else), then P1, P2, P3.

---

## Phase 0 · Fix the State Machine (P0 Blockers)
> All gaps here are interdependent. Do them in order within a single PR.

---

### Step 0.1 · Add `MATCHING` to `IntentStatus` enum (GAP-1)
**Files:** `domain/walkintent/IntentStatus.java`

```java
// Before
public enum IntentStatus { OPEN, CONSUMED, CANCELLED, EXPIRED }

// After
public enum IntentStatus { OPEN, MATCHING, CONSUMED, CANCELLED, EXPIRED }
```

**Checklist:**
- [x] Add `MATCHING` between `OPEN` and `CONSUMED`.
- [x] Verify no existing `switch` statement on `IntentStatus` is non-exhaustive — confirmed no switch statements; guards use if-checks only.
- [x] Update any pattern matching / switch expressions in `WalkIntent.java` guard methods (`cancel()`, `consume()`) to allow `MATCHING` as a valid source state.

---

### Step 0.2 · Add `MATCHING` lock/unlock methods to `WalkIntent` domain entity (GAP-1, GAP-3)
**Files:** `domain/walkintent/WalkIntent.java`

Add two new domain operations:

```java
/** Called when a MatchProposal is created for this intent. Increments version. */
public void lock() {
    // Guard: must be OPEN
    if (this.status != IntentStatus.OPEN) throw ...INTENT_NOT_OPEN;
    this.status = IntentStatus.MATCHING;
    this.version++;
}

/** Called when a MatchProposal is REJECTED or EXPIRED. Increments version. */
public void unlock() {
    // Guard: must be MATCHING
    if (this.status != IntentStatus.MATCHING) throw ...INTENT_NOT_MATCHING;
    this.status = IntentStatus.OPEN;
    this.version++;
}
```

Also update `consume()` to allow source state `MATCHING` (not just `OPEN`):
```java
public void consume() {
    if (this.status != IntentStatus.MATCHING) throw ...INTENT_NOT_MATCHING; 
    // was: OPEN check
    this.status = IntentStatus.CONSUMED;
    this.version++;
}
```

**Checklist:**
- [x] `lock()` guards on `OPEN`.
- [x] `unlock()` guards on `MATCHING`.
- [x] `consume()` guards on `MATCHING` (updated from negative CONSUMED/CANCELLED check to positive MATCHING check).
- [x] `cancel()` guards on `OPEN` **or** `MATCHING` — confirmed: it blocks only CANCELLED/CONSUMED, so OPEN and MATCHING are both valid sources.

---

### Step 0.3 · Lock both intents when proposal is created (GAP-2)
**Files:** `application/proposal/MatchingCommandService.java`

In `findOrCreateProposal()`, after creating the proposal, immediately persist both intents as `MATCHING`:

```java
// After proposal = matchProposalRepository.save(proposal):
intentA.lock();
intentB.lock();
walkIntentRepository.save(intentA);
walkIntentRepository.save(intentB);
```

This must happen within the **same `@Transactional` boundary** as the proposal save.

**Checklist:**
- [x] Load `intentA` and `intentB` objects (by ID) before creating proposal — `intent` loaded at step 1, `matched` via matchingStrategy result.
- [x] Call `lock()` on both.
- [x] Save both intents inside the transaction.
- [x] `findOpenCandidates()` SQL query excludes `MATCHING` intents — confirmed `status = 'OPEN'` filter already handles this.

---

### Step 0.4 · Unlock intents on proposal rejection/expiry (GAP-3)
**Files:** `application/proposal/MatchingCommandService.java`

**`passProposal()` (User passes — both intents return to OPEN):**
```java
// After proposal.reject():
intentA.unlock();
intentB.unlock();
walkIntentRepository.save(intentA);
walkIntentRepository.save(intentB);
```

**`cancelProposal()` (User hard-cancels — caller's intent CANCELLED, partner's intent → OPEN):**
```java
// Caller's intent: cancel as before
callerIntent.cancel();
// Partner's intent: unlock back to OPEN
partnerIntent.unlock();
walkIntentRepository.save(callerIntent);
walkIntentRepository.save(partnerIntent);
```

**Proposal EXPIRED (scheduler):** When a proposal timeout fires, both intents must be unlocked. Add an `expireProposal(proposalId)` method or handle in the existing expired-session sweep.

**Checklist:**
- [x] `passProposal()` unlocks both intents.
- [x] `cancelProposal()` cancels caller's intent, unlocks partner's intent.
- [x] Scheduled expiry path also unlocks both intents — `sweepExpiredProposals()` added to `MatchingCommandService`, wired into `SessionScheduler`.

---

### Step 0.5 · Add `version` field to `MatchProposal` domain entity (GAP-4)
**Files:** `domain/proposal/MatchProposal.java`, `infrastructure/repository/proposal/MatchProposalJdbcRepository.java`

**Domain entity:**
```java
private long version;   // Add field

// In create() factory:
proposal.version = 0;

// In every mutating method (recordAcceptance, reject, confirm):
this.version++;
```

**JDBC repository — READ:** Add `version` to the `RowMapper`:
```java
p.setVersion(rs.getLong("version"));
```

**JDBC repository — WRITE (Optimistic Lock check):**
```sql
UPDATE match_proposal
SET accepted_by_a = :acceptedByA, accepted_by_b = :acceptedByB,
    status = CAST(:status AS proposal_status), confirmed_at = :confirmedAt,
    version = version + 1
WHERE proposal_id = :proposalId
  AND version = :expectedVersion   -- OCC guard
```
If `rowsUpdated == 0`, throw a concurrency conflict exception.

**Checklist:**
- [x] `version` field in domain entity.
- [x] `version` populated in `create()` factory (`version = 0`).
- [x] `version` incremented in `recordAcceptance()`, `reject()`, `confirm()` — also added `expire()` with version++.
- [x] `RowMapper` reads `version` from result set.
- [x] UPDATE query includes `AND version = :expectedVersion` (expectedVersion = proposal.getVersion() - 1).
- [x] Throws `PROPOSAL_CONCURRENT_MODIFICATION` when update returns 0 rows.

---

### Step 0.6 · Update P-3 confirmation to re-check `MATCHING` (GAP-7)
**Files:** `application/proposal/MatchingCommandService.java`

In the pessimistic-lock section of `acceptProposal()`:
```java
// Before (wrong):
if (!intentA.isOpen() || !intentB.isOpen()) throw PROPOSAL_INTENT_NO_LONGER_OPEN;

// After (correct):
if (intentA.getStatus() != IntentStatus.MATCHING 
 || intentB.getStatus() != IntentStatus.MATCHING)
    throw PROPOSAL_INTENT_NO_LONGER_OPEN;
```

**Checklist:**
- [x] Guard changed from `OPEN` to `MATCHING`.
- [x] `consume()` (Step 0.2) correctly transitions `MATCHING → CONSUMED`.

---

## Phase 1 · Restore Invariant Correctness (P1)

---

### Step 1.1 · Fix proposal TTL to 5 minutes (GAP-5)
**Files:** `application/proposal/MatchingCommandService.java`

```java
// Before
private static final int PROPOSAL_TTL_MINUTES = 30;

// After
private static final int PROPOSAL_TTL_MINUTES = 5;
```

**Checklist:**
- [x] Constant updated.
- [ ] Any related test fixtures that assume 30 min TTL are updated.

---

### Step 1.2 · Fix activation window constants (GAP-6)
**Files:** `domain/session/WalkSession.java`

```java
// Before
private static final Duration ACTIVATION_WINDOW_BEFORE = Duration.ofMinutes(15);
private static final Duration ACTIVATION_WINDOW_AFTER  = Duration.ofMinutes(30);

// After (per S-3: [start - 10min, start + 15min])
private static final Duration ACTIVATION_WINDOW_BEFORE = Duration.ofMinutes(10);
private static final Duration ACTIVATION_WINDOW_AFTER  = Duration.ofMinutes(15);
```

**Checklist:**
- [x] Both constants updated to match S-3.
- [x] Scheduler query in `WalkSessionJdbcRepository.findSessionsPastActivationWindow()` uses correct boundary (`scheduledStart + 15 min < now`). Update SQL if needed.

---

### Step 1.3 · Expose `complete` session endpoint (GAP-9)
**Files:** `presentation/controller/session/SessionController.java`

Add:
```java
/**
 * POST /api/v1/sessions/{sessionId}/complete
 * User-initiated session completion. Enforces S-5 (minimum 5-minute guard).
 */
@PostMapping("/{sessionId}/complete")
public ResponseEntity<ApiResponse<WalkSessionResponse>> completeSession(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable String sessionId) {
    WalkSession session = sessionCommandService.completeSession(sessionId, principal.userId());
    return ResponseEntity.ok(ApiResponse.success(sessionMapper.toResponse(session)));
}
```

**Checklist:**
- [x] Endpoint added.
- [x] `completeSession()` in service already enforces `SESSION_COMPLETE_TOO_EARLY` guard (S-5) — verify no changes needed.
- [x] `SessionMapper.toResponse()` correctly maps the terminal session fields.

---

### Step 1.4 · Add private intent fields across all layers (GAP-10)
This is a cross-cutting change spanning domain → application → presentation.

**1.4a — Domain entity `WalkIntent.java`:**
```java
private boolean isPrivate;
private String  invitedFriendId;   // UUID as String, nullable
private String  description;       // nullable
```
Update `create()` factory to accept and set these fields.

**1.4b — `CreateWalkIntentCommand.java`:**
```java
record CreateWalkIntentCommand(
    String hotspotId, String userId,
    Instant timeWindowStart, Instant timeWindowEnd,
    int ageMin, int ageMax,
    boolean isPrivate,           // new
    String  invitedFriendId,     // new, nullable
    String  description          // new, nullable
)
```

**1.4c — `CreateWalkIntentRequest.java` (DTO):**
```java
boolean isPrivate();
@Nullable String invitedFriendId();
@Nullable String description();
```
Add validation: if `isPrivate == true`, `invitedFriendId` must not be blank.

**1.4d — `WalkIntentController.java`:**
Pass new fields through to `CreateWalkIntentCommand`.

**1.4e — `WalkIntentCommandService.createIntent()`:**
- If `isPrivate == true`: validate that `invitedFriendId` resolves to an `ACCEPTED` friendship with the caller. Throw a domain error if not.
- Pass private fields through to `WalkIntent.create()`.

**1.4f — `WalkIntentJdbcRepository` (JDBC):**
- Include `is_private`, `invited_friend_id`, `description` in INSERT/UPDATE and RowMapper.

**1.4g — Matching engine `findOpenCandidates()` SQL:**
Add filter: `AND (is_private = false OR invited_friend_id = :callerId)`. This enforces I-7 at the DB level.

**1.4h — `WalkIntentResponse.java`:**
Expose `isPrivate`, `description` (omit `invitedFriendId` for privacy unless caller is the owner).

**Checklist:**
- [x] Domain entity updated.
- [x] Command updated.
- [x] Request DTO updated with validation.
- [x] Controller passes new fields.
- [x] Service validates friendship before creating private intent.
- [x] JDBC repository reads/writes new columns.
- [x] `findOpenCandidates()` SQL includes `is_private` filter.
- [x] Response DTO updated.

---

### Step 1.5 · Fix overlap check status set (GAP-12)
**Files:** `infrastructure/repository/walkintent/WalkIntentJdbcRepository.java`

The `hasOverlappingActiveIntent()` SQL query:
```sql
-- Before (wrong: includes CONSUMED, excludes MATCHING)
AND status IN ('OPEN', 'CONSUMED')

-- After (correct per I-1: OPEN + MATCHING block time; CONSUMED hands off to WalkSession)
AND status IN ('OPEN', 'MATCHING')
```

The `WalkSessionRepository.hasOverlappingActiveSession()` query is already correct (`PENDING`, `ACTIVE`) — verify and leave as-is.

**Checklist:**
- [x] SQL updated from `('OPEN', 'CONSUMED')` to `('OPEN', 'MATCHING')`.
- [x] Verify session overlap check still uses `('PENDING', 'ACTIVE')` — confirmed, no change needed.
- [ ] `WalkIntentCommandService.createIntent()` calls both checks (intent overlap + session overlap) — session overlap check is NOT called; pre-existing gap, not in Phase 1 scope.

---

## Phase 2 · Implement Missing Features (P2)

---

### Step 2.1 · Wire MongoDB Atlas infrastructure (GAP-17)
> **Prerequisite for Step 2.2.** Complete this step fully before touching any chat room logic.

#### 2.1a — Add dependency to `build.gradle`

```groovy
// MongoDB Atlas (Chat layer)
implementation 'org.springframework.boot:spring-boot-starter-data-mongodb'
```

**Do NOT add a `MongoTransactionManager` bean.** Spring `@Transactional` must continue to manage only the `DataSourceTransactionManager` (PostgreSQL). MongoDB operations are non-transactional at the Spring level; document-level atomicity is handled by MongoDB itself.

#### 2.1b — Add configuration to `application.properties`

```properties
# ==============================
# MONGODB ATLAS (Chat)
# ==============================
spring.data.mongodb.uri=${MONGODB_URI}
spring.data.mongodb.database=${MONGODB_DATABASE:walkmate}
```

`MONGODB_URI` follows the Atlas connection string format:  
`mongodb+srv://<user>:<password>@<cluster>.mongodb.net/?retryWrites=true&w=majority`

Add both variables to the `.env` file (local dev) and to the deployment environment. The application will fail to start if `MONGODB_URI` is absent — this is intentional, matching the fail-fast pattern already used by `FirebaseConfig`.

#### 2.1c — Create `ChatRoom` document class (infrastructure layer only)

**File:** `infrastructure/repository/chat/document/ChatRoomDocument.java`

```java
@Document(collection = "chat_rooms")
@Getter
public class ChatRoomDocument {

    @Id
    private String sessionId;        // UUID string — PK is the WalkSession ID

    private String status;           // "OPEN" or "CLOSED" (convenience cache, NOT the S-7 gatekeeper)
    private Instant createdAt;
    private Instant closedAt;        // null while OPEN

    public static ChatRoomDocument open(String sessionId) {
        ChatRoomDocument doc = new ChatRoomDocument();
        doc.sessionId  = sessionId;
        doc.status     = "OPEN";
        doc.createdAt  = Instant.now();
        doc.closedAt   = null;
        return doc;
    }

    public void close() {
        this.status   = "CLOSED";
        this.closedAt = Instant.now();
    }
}
```

> **Architecture note:** The `@Document` annotation and all MongoDB-specific types are confined to the `infrastructure` layer. No MongoDB type leaks into the `domain` or `application` layers.

#### 2.1d — Define `ChatRoomRepository` port (application layer)

**File:** `application/chat/ChatRoomRepository.java`

```java
/**
 * Application-layer port for managing chat room lifecycle.
 * Implementations live in the infrastructure layer (MongoDB adapter).
 */
public interface ChatRoomRepository {

    /**
     * Initialises a new chat room for the given session. Idempotent — calling
     * again for an existing sessionId is a no-op (upsert semantics).
     */
    void initRoom(String sessionId);

    /**
     * Closes the chat room for the given session, enforcing the S-7 write-lock.
     * Idempotent — closing an already-closed room is a no-op.
     */
    void closeRoom(String sessionId);
}
```

#### 2.1e — Implement `MongoChatRoomRepository` adapter (infrastructure layer)

**File:** `infrastructure/repository/chat/MongoChatRoomRepository.java`

```java
@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoChatRoomRepository implements ChatRoomRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public void initRoom(String sessionId) {
        // Upsert: create only if not already present (handles retries safely)
        Query query  = Query.query(Criteria.where("_id").is(sessionId));
        Update update = new Update()
                .setOnInsert("sessionId", sessionId)
                .setOnInsert("status",    "OPEN")
                .setOnInsert("createdAt", Instant.now())
                .setOnInsert("closedAt",  null);

        mongoTemplate.upsert(query, update, ChatRoomDocument.class);
        log.debug("Chat room initialised: sessionId={}", sessionId);
    }

    @Override
    public void closeRoom(String sessionId) {
        Query  query  = Query.query(Criteria.where("_id").is(sessionId));
        Update update = new Update()
                .set("status",   "CLOSED")
                .set("closedAt", Instant.now());

        mongoTemplate.updateFirst(query, update, ChatRoomDocument.class);
        log.debug("Chat room closed: sessionId={}", sessionId);
    }
}
```

**Checklist:**
- [x] `spring-boot-starter-data-mongodb` added to `build.gradle`.
- [x] `MONGODB_URI` and `MONGODB_DATABASE` added to `application.properties`.
- [x] `MONGODB_URI` and `MONGODB_DATABASE` added to `.env` (local dev).
- [x] `ChatRoomDocument` created in `infrastructure/repository/chat/document/`.
- [x] `ChatRoomRepository` port created in `application/chat/`.
- [x] `MongoChatRoomRepository` adapter created in `infrastructure/repository/chat/`.
- [x] No `MongoTransactionManager` bean registered anywhere.
- [ ] Application starts successfully with MongoDB URI present; fails fast without it.

---

### Step 2.2 · Initialize MongoDB chat room on session creation (GAP-8)
> **Depends on Step 2.1.**

**Files:** `application/proposal/MatchingCommandService.java`

The P-3 atomic block is currently `@Transactional` over PostgreSQL. The MongoDB write must happen **after** that transaction commits. Use a `TransactionSynchronizationManager.afterCommit()` hook registered inside the transactional method. This guarantees:
- If PostgreSQL rolls back → MongoDB is never written.
- If MongoDB write fails → WalkSession still exists; PostgreSQL is the source of truth.

```java
// In acceptProposal(), after walkSessionRepository.save(session):

// Register afterCommit hook — MongoDB write fires only once PostgreSQL commits.
final String sessionId = session.getSessionId();
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        try {
            chatRoomRepository.initRoom(sessionId);
        } catch (Exception e) {
            // Non-fatal: WalkSession is the source of truth.
            // A reconciliation job can re-initialize missing chat rooms.
            log.error("Chat room init failed after session creation: sessionId={} error={}",
                    sessionId, e.getMessage());
        }
    }
});
```

Inject `ChatRoomRepository` into `MatchingCommandService`:
```java
private final ChatRoomRepository chatRoomRepository;  // new dependency
```

**Checklist:**
- [x] `ChatRoomRepository` injected into `MatchingCommandService`.
- [x] `afterCommit()` hook registered after `walkSessionRepository.save(session)`.
- [x] MongoDB write is NOT inside the `@Transactional` boundary.
- [x] Exception from `initRoom()` is caught and logged; it never propagates to the caller.

---

### Step 2.3 · Lock chat rooms when session reaches a terminal state (S-7)
> **Depends on Step 2.1.** Addresses the write-lock side of GAP-8.

**Files:** `application/session/SessionCommandService.java`

Inject `ChatRoomRepository` and register `afterCommit()` hooks in the three terminal transitions:

```java
private final ChatRoomRepository chatRoomRepository;  // new dependency
```

**`completeSession()`** — after `sessionRepository.save(session)`:
```java
final String sessionId = session.getSessionId();
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        try { chatRoomRepository.closeRoom(sessionId); }
        catch (Exception e) { log.error("Chat room close failed: sessionId={}", sessionId, e); }
    }
});
```

Apply the same `afterCommit()` pattern to **`cancelSession()`** and **`abortSession()`**.

Also apply to the scheduler sweep in **`handleExpiredSessions()`** for sessions that auto-complete or auto-cancel.

**Checklist:**
- [x] `ChatRoomRepository` injected into `SessionCommandService`.
- [x] `afterCommit()` hook added to `completeSession()`.
- [x] `afterCommit()` hook added to `cancelSession()`.
- [x] `afterCommit()` hook added to `abortSession()`.
- [x] `afterCommit()` hook added to the scheduler's auto-complete and auto-cancel paths inside `handleExpiredSessions()`.
- [x] Exception from `closeRoom()` is always caught and logged; never propagates.

---

### Step 2.4 · Per-intent exclude list on proposal rejection (GAP-11)
**Files:** `domain/walkintent/WalkIntent.java`, `infrastructure/repository/walkintent/WalkIntentJdbcRepository.java`, `application/proposal/MatchingCommandService.java`

**DB:** Add a column to `walk_intent`:
```sql
ALTER TABLE public.walk_intent
ADD COLUMN IF NOT EXISTS excluded_user_ids uuid[] NOT NULL DEFAULT '{}';
```
Create a new migration file `V106__add_intent_exclude_list.sql`.

**Domain entity:** Add field `List<UUID> excludedUserIds` with method:
```java
public void excludeUser(UUID userId) {
    this.excludedUserIds.add(userId);
    this.version++;
}
```

**Service:** In `passProposal()`, after unlocking both intents:
```java
// Caller excludes the matched user from their intent
callerIntent.excludeUser(partnerUserId);
walkIntentRepository.save(callerIntent);
// (Partner's intent is NOT updated — their intent stays clean for re-matching)
```

**Matching engine:** In `findOpenCandidates()` SQL, add:
```sql
AND :callerId != ALL(wi.excluded_user_ids)
```
This filters out candidates who have excluded the caller.

**Checklist:**
- [x] New migration `V106` with `excluded_user_ids` column.
- [x] Domain entity field + `excludeUser()` method.
- [x] JDBC repository reads/writes `excluded_user_ids` array.
- [x] `passProposal()` calls `excludeUser()` on caller's intent.
- [x] `findOpenCandidates()` SQL filters on `excluded_user_ids`.

---

### Step 2.5 · Intent expiry scheduler with proposal cascade (GAP-13, GAP-14)
**Files:** New or extended scheduler in `application/walkintent/IntentScheduler.java` (create if absent)

Create a scheduled task running every 60 seconds:

```java
@Scheduled(fixedDelay = 60_000)
public void handleExpiredIntents() {
    Instant now = Instant.now();
    
    // GAP-14: Auto-expire intents 5 min before their start time
    // Condition: time_window_start <= now + 5min AND status IN ('OPEN', 'MATCHING')
    List<WalkIntent> aboutToStart = walkIntentRepository.findIntentsExpiringSoon(now, Duration.ofMinutes(5));
    for (WalkIntent intent : aboutToStart) {
        expireIntent(intent, now);
    }
    
    // GAP-13: Expire intents whose time window has fully passed
    // Condition: time_window_end <= now AND status IN ('OPEN', 'MATCHING')
    List<WalkIntent> pastEnd = walkIntentRepository.findOverdueOpenIntents(now);
    for (WalkIntent intent : pastEnd) {
        expireIntent(intent, now);
    }
}

private void expireIntent(WalkIntent intent, Instant now) {
    // I-6: terminal check
    intent.expire();   // new domain method: OPEN/MATCHING → EXPIRED
    walkIntentRepository.save(intent);
    
    // I-5: cascade to any PENDING proposal
    matchProposalRepository.findPendingByIntentId(intent.getId())
        .ifPresent(proposal -> {
            proposal.expire();  // new domain method: PENDING → EXPIRED
            matchProposalRepository.save(proposal);
            // Also unlock partner's intent
            String partnerIntentId = proposal.getIntentIdA().equals(intent.getId())
                ? proposal.getIntentIdB() : proposal.getIntentIdA();
            walkIntentRepository.findById(partnerIntentId)
                .ifPresent(partner -> {
                    partner.unlock();
                    walkIntentRepository.save(partner);
                });
        });
}
```

**New domain methods needed:**

`WalkIntent.expire()`:
```java
public void expire() {
    if (this.status != IntentStatus.OPEN && this.status != IntentStatus.MATCHING)
        throw INTENT_ALREADY_TERMINAL;
    this.status = IntentStatus.EXPIRED;
    this.version++;
}
```

`MatchProposal.expire()`:
```java
public void expire() {
    if (this.status != ProposalStatus.PENDING) throw PROPOSAL_ALREADY_TERMINAL;
    this.status = ProposalStatus.EXPIRED;
    this.version++;
}
```

**New repository queries:**
- `WalkIntentRepository.findIntentsExpiringSoon(now, buffer)` → intents where `time_window_start <= :now + :buffer AND status IN ('OPEN', 'MATCHING')`
- `WalkIntentRepository.findOverdueOpenIntents(now)` → intents where `time_window_end <= :now AND status IN ('OPEN', 'MATCHING')`

**Checklist:**
- [x] `IntentScheduler.java` created (or existing scheduler extended).
- [x] `WalkIntent.expire()` domain method added.
- [x] `MatchProposal.expire()` domain method added — already present from Phase 0.
- [x] Two new repository query methods in `WalkIntentRepository` + JDBC impl.
- [x] Cascade: proposal expired + partner intent unlocked atomically.
- [x] `@Scheduled` runs every 60 seconds.

---

### Step 2.6 · Upgrade FCM notification dispatch to dual-channel (GAP-18, GAP-19)
> Steps 2.6a and 2.6b must be done in order. 2.6c–2.6d can follow once both are done.

#### 2.6a — Add generic `sendPush()` to `PushNotificationProvider` port (GAP-19)

**File:** `application/notification/PushNotificationProvider.java`

Add the following method to the existing interface:

```java
/**
 * Sends a generic data-only push notification to a device.
 *
 * <p>This is the primary dispatch method used by {@code NotificationPublisherImpl}
 * for all lifecycle events. The existing {@code sendMatchFound()} remains for
 * backwards compatibility but new notification types route through here.</p>
 *
 * @param fcmToken FCM registration token of the target device
 * @param type     the notification type — mapped to the {@code "type"} data field
 * @param payload  arbitrary key/value data included in the FCM data payload
 */
void sendPush(String fcmToken, NotificationType type, Map<String, Object> payload);
```

#### 2.6b — Implement `sendPush()` in `FcmNotificationProvider` (GAP-19)

**File:** `infrastructure/notification/FcmNotificationProvider.java`

```java
@Override
public void sendPush(String fcmToken, NotificationType type, Map<String, Object> payload) {
    Message.Builder builder = Message.builder()
            .setToken(fcmToken)
            .putData("type", type.name());

    // Flatten payload into FCM data fields (values coerced to String)
    if (payload != null) {
        payload.forEach((k, v) -> builder.putData(k, v != null ? v.toString() : ""));
    }

    try {
        String messageId = firebaseMessaging.send(builder.build());
        log.debug("FCM push dispatched: type={} messageId={}", type, messageId);
    } catch (FirebaseMessagingException ex) {
        // Failures are always swallowed — a push failure must never roll back
        // the business transaction or block notification DB persistence.
        log.error("FCM push delivery failed: type={} token=[{}…] code={} message={}",
                type,
                fcmToken.substring(0, Math.min(12, fcmToken.length())),
                ex.getMessagingErrorCode(),
                ex.getMessage());
    }
}
```

#### 2.6c — Upgrade `NotificationPublisherImpl` to dual-dispatch (GAP-18)

**File:** `infrastructure/notification/NotificationPublisherImpl.java`

Inject `UserRepository` (to look up FCM token) and `PushNotificationProvider`, then dual-dispatch on every `publish()` call:

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisherImpl implements NotificationPublisher {

    private final NotificationRepository   notificationRepository;
    private final UserRepository           userRepository;          // new dependency
    private final PushNotificationProvider pushNotificationProvider; // new dependency

    @Override
    public void publish(Notification notification) {
        // Channel 1: persist to DB (in-app notification feed — always attempted)
        try {
            notificationRepository.save(notification);
            log.debug("Notification persisted: type={} userId={}",
                    notification.getType(), notification.getUserId());
        } catch (Exception ex) {
            log.error("Failed to persist notification type={} userId={}: {}",
                    notification.getType(), notification.getUserId(), ex.getMessage(), ex);
        }

        // Channel 2: FCM push (real-time device delivery — best-effort)
        // Token lookup is infra-layer only; no Firebase type leaks upward.
        try {
            userRepository.findById(notification.getUserId()).ifPresent(user -> {
                String token = user.getFcmToken();
                if (token != null && !token.isBlank()) {
                    pushNotificationProvider.sendPush(
                            token,
                            notification.getType(),
                            notification.getPayload()
                    );
                }
            });
        } catch (Exception ex) {
            // A failure to resolve the FCM token must never block DB persistence.
            log.error("FCM dispatch failed for notification type={} userId={}: {}",
                    notification.getType(), notification.getUserId(), ex.getMessage(), ex);
        }
    }
}
```

#### 2.6d — Retire the manual FCM call in `MatchingCommandService` (cleanup)

**File:** `application/proposal/MatchingCommandService.java`

The direct `pushNotificationProvider.sendMatchFound(...)` call inside `findOrCreateProposal()` is now redundant. The `notificationPublisher.publish(...)` call already dispatches both DB and FCM. Remove the manual FCM block:

```java
// REMOVE this entire block from findOrCreateProposal():
userRepository.findById(matched.getUserId()).ifPresent(matchedUser -> {
    String token = matchedUser.getFcmToken();
    if (token != null && !token.isBlank()) {
        pushNotificationProvider.sendMatchFound(
                token,
                matched.getId(),
                saved.getProposalId()
        );
    }
});
```

The `PROPOSAL_RECEIVED` notification's `publish()` call that already exists in `findOrCreateProposal()` will now automatically trigger both DB persistence and FCM push.

Also remove `PushNotificationProvider` from `MatchingCommandService`'s constructor dependencies once this cleanup is done, unless it is still used elsewhere.

**Checklist:**
- [x] `sendPush(String, NotificationType, Map)` added to `PushNotificationProvider` interface.
- [x] `sendPush()` implemented in `FcmNotificationProvider` with data-only payload and error swallowing.
- [x] `UserRepository` and `PushNotificationProvider` injected into `NotificationPublisherImpl`.
- [x] `NotificationPublisherImpl.publish()` dual-dispatches: DB persist first, then FCM.
- [x] FCM exception in `publish()` is caught independently — never blocks DB persistence.
- [x] Manual `pushNotificationProvider.sendMatchFound()` call removed from `MatchingCommandService`.
- [x] `PushNotificationProvider` removed from `MatchingCommandService` dependencies (if no longer used).
- [x] `SESSION_CONFIRMED`, `SESSION_ACTIVE`, and `REVIEW_REQUESTED` events are now automatically pushed via FCM with no call-site changes in `SessionCommandService`.

---

## Phase 3 · Polish (P3)

---

### Step 3.1 · Emit penalty event for NO_SHOW and ABORTED (GAP-15)
**Files:** `application/session/SessionCommandService.java`

In `handleExpiredSessions()`, when marking `NO_SHOW`:
```java
// Identify the non-showing user
String noShowUserId = (session.getUserAActivatedAt() == null) 
    ? session.getUserIdA() : session.getUserIdB();
eventPublisher.publish(new SessionNoShowEvent(session.getSessionId(), noShowUserId));
```

In `abortSession()`, publish:
```java
eventPublisher.publish(new SessionAbortedEvent(session.getSessionId(), callerId));
```

The gamification/reputation service should subscribe to these events and apply penalty to `trust_score`.

**Checklist:**
- [x] `SessionNoShowEvent` created with `sessionId` + `penalizedUserId`.
- [x] `SessionAbortedEvent` created with `sessionId` + `abortingUserId`.
- [x] Events published from `handleExpiredSessions()` and `abortSession()`.
- [x] Gamification service handles events to deduct trust score.

---

### Step 3.2 · Include `MATCHING` intents in `listActiveIntents` (GAP-16)
**Files:** `application/walkintent/WalkIntentQueryService.java`, `infrastructure/repository/walkintent/WalkIntentJdbcRepository.java`

```sql
-- Before
WHERE user_id = :userId AND status = 'OPEN'

-- After
WHERE user_id = :userId AND status IN ('OPEN', 'MATCHING')
```

**Checklist:**
- [x] SQL updated.
- [x] `WalkIntentResponse` already exposes `status` field (so MATCHING will appear automatically).

---

## Execution Order & Dependencies

```
Phase 0 (must be sequential, in order):
  0.1 → 0.2 → 0.3 → 0.4 → 0.5 → 0.6

Phase 1 (can parallelize 1.1, 1.2, 1.3 as independent):
  0.x done → 1.1, 1.2, 1.3 (parallel) → 1.4, 1.5 (parallel, depend on 0.x only)

Phase 2 (after Phase 1 complete):
  2.1 (MongoDB infra — must come first)
    → 2.2 (chat room init, depends on 2.1)
    → 2.3 (chat room close, depends on 2.1)
  2.4 (exclude list — independent of MongoDB)
  2.5 (intent expiry scheduler — independent of MongoDB)
  2.6a → 2.6b → 2.6c → 2.6d (FCM dual-channel — sequential within sub-steps)
  Note: 2.4, 2.5, and 2.6 are independent of each other and of 2.1–2.3.

Phase 3 (after Phase 2):
  3.1, 3.2 (can parallelize)
```

---

## Files Touched Summary

| File | Steps |
|---|---|
| `domain/walkintent/IntentStatus.java` | 0.1 |
| `domain/walkintent/WalkIntent.java` | 0.2, 1.4a, 2.4, 2.5 |
| `domain/proposal/MatchProposal.java` | 0.5, 2.5 |
| `domain/session/WalkSession.java` | 1.2 |
| `application/proposal/MatchingCommandService.java` | 0.3, 0.4, 0.6, 1.1, 2.2, 2.4, 2.6d |
| `application/walkintent/WalkIntentCommandService.java` | 1.4e |
| `application/walkintent/WalkIntentQueryService.java` | 3.2 |
| `application/session/SessionCommandService.java` | 1.3, 2.3, 3.1 |
| `application/walkintent/IntentScheduler.java` | 2.5 (new file) |
| `application/chat/ChatRoomRepository.java` | 2.1d (new file) |
| `application/notification/PushNotificationProvider.java` | 2.6a |
| `presentation/controller/session/SessionController.java` | 1.3 |
| `presentation/dto/request/walkintent/CreateWalkIntentRequest.java` | 1.4c |
| `presentation/dto/response/walkintent/WalkIntentResponse.java` | 1.4h |
| `application/walkintent/CreateWalkIntentCommand.java` | 1.4b |
| `infrastructure/repository/walkintent/WalkIntentJdbcRepository.java` | 1.4f, 1.4g, 1.5, 2.4, 2.5, 3.2 |
| `infrastructure/repository/proposal/MatchProposalJdbcRepository.java` | 0.5 |
| `infrastructure/repository/chat/document/ChatRoomDocument.java` | 2.1c (new file) |
| `infrastructure/repository/chat/MongoChatRoomRepository.java` | 2.1e (new file) |
| `infrastructure/notification/FcmNotificationProvider.java` | 2.6b |
| `infrastructure/notification/NotificationPublisherImpl.java` | 2.6c |
| `domain/walkintent/WalkIntentRepository.java` | 2.4, 2.5 |
| `build.gradle` | 2.1a |
| `application.properties` | 2.1b |
| DB migration `V106__add_intent_exclude_list.sql` (new) | 2.4 |
