# WalkMate Backend — Implementation Plan
**Date:** 2026-04-13
**Based on:** `gap_analysis.md` + `docs/single-source-of-truth/use-cases/backend_use_cases.md` (2026-04-13)
**Architecture:** Spring Boot 3 · Hexagonal (Domain / Application / Infrastructure / Presentation) · JDBC (no JPA) · MongoDB for Chat · JWT Auth
**Execution model:** Fix bottom-up — Domain first, Application second, Infrastructure/Repository third, Presentation (Controller + DTO) last.

---

## DB Context (Read Before Planning)

The production DB already has a `friendship` table with the following schema:
```
friendship_id  UUID PK (gen_random_uuid)
requester_id   UUID FK → user_account   ← "the user who sent the request"
addressee_id   UUID FK → user_account   ← "the user who received the request"
status         friend_status enum (PENDING / ACCEPTED / DECLINED)
version        BIGINT NOT NULL DEFAULT 0  ← optimistic locking (X-5)
created_at     TIMESTAMPTZ
updated_at     TIMESTAMPTZ
```

`SocialJdbcRepository` (V104 migration) already uses this table to back `follow/unfollow` operations and `areAcceptedFriends()`. **No DB migration is required.** All domain and service work in Phase 5 and 6 builds on this existing table.

---

## 0. Pre-Work: One New Domain Entity

This entity is required by Phase 5 (Friends System) and must be created before service or controller work in that phase.

---

### DE-1 — `Friendship` (`domain/social/`)

**Why a new entity:** The DB `friendship` table tracks the full lifecycle (PENDING → ACCEPTED / DECLINED). No Java domain class currently maps to it — `FollowRelation` is a stale record type not used by the repository. A proper `Friendship` entity exposes lifecycle transitions and is used by both `FriendCommandService` and (indirectly) `WalkIntentCommandService` via `areAcceptedFriends()`.

**DB column ↔ Java field mapping:**

| DB Column | Java Field | Notes |
|---|---|---|
| `friendship_id` | `friendshipId` | String (UUID as String) |
| `requester_id` | `requesterId` | UUID |
| `addressee_id` | `addresseeId` | UUID |
| `status` | `status` | String: "PENDING" \| "ACCEPTED" \| "DECLINED" |
| `version` | `version` | long — for optimistic locking (X-5) |
| `created_at` | `createdAt` | Instant |
| `updated_at` | `updatedAt` | Instant (mutable) |

**Create:** `domain/social/Friendship.java`

```java
public class Friendship {
    private final String  friendshipId;
    private final UUID    requesterId;
    private final UUID    addresseeId;
    private       String  status;
    private final long    version;
    private final Instant createdAt;
    private       Instant updatedAt;

    // Factory — used when sending a new friend request
    public static Friendship create(UUID requesterId, UUID addresseeId) {
        return new Friendship(UUID.randomUUID().toString(), requesterId, addresseeId,
            "PENDING", 0L, Instant.now(), Instant.now());
    }

    // Transitions — each guards status == "PENDING" before mutating
    public void accept()  { guardPending(); this.status = "ACCEPTED"; this.updatedAt = Instant.now(); }
    public void decline() { guardPending(); this.status = "DECLINED"; this.updatedAt = Instant.now(); }

    public boolean isPending()  { return "PENDING".equals(status); }
    public boolean isAccepted() { return "ACCEPTED".equals(status); }

    private void guardPending() {
        if (!"PENDING".equals(status))
            throw new DomainException(FriendshipErrorCode.FRIEND_REQUEST_ALREADY_RESOLVED);
    }
    // getters for all fields
}
```

**Create:** `domain/social/FriendshipErrorCode.java` (implements `ErrorCode`)

Error codes required:
- `FRIEND_REQUEST_SELF_FORBIDDEN`
- `FRIEND_REQUEST_ALREADY_FRIENDS`
- `FRIEND_REQUEST_ALREADY_PENDING`
- `FRIEND_REQUEST_BLOCKED`
- `FRIEND_REQUEST_NOT_FOUND`
- `FRIEND_REQUEST_NOT_PARTICIPANT`
- `FRIEND_REQUEST_ALREADY_RESOLVED`
- `FRIEND_REMOVE_NOT_FRIENDS`
- `SOCIAL_USER_NOT_FOUND` (already exists in `SocialErrorCode` — reuse, do not duplicate)

---

## 1. Repository Layer

---

### 1.1 Extend `SocialRepository` interface

**Modify:** `domain/social/SocialRepository.java`

Add the following method signatures. All use `Friendship` (DB column names: `requester_id`/`addressee_id`). Do NOT rename or remove any existing method.

```java
// Persist a Friendship row (INSERT on create, UPDATE on accept/decline)
void saveFriendship(Friendship friendship);

// Lookup by PK
Optional<Friendship> findFriendshipById(String friendshipId);

// Find the PENDING row between two users (either direction)
Optional<Friendship> findPendingFriendship(UUID requesterId, UUID addresseeId);

// List rows for the incoming-request and outgoing-request screens
List<Friendship> findIncomingPendingRequests(UUID addresseeId);
List<Friendship> findOutgoingPendingRequests(UUID requesterId);

// Accepted-friends list for the friend-picker (UC-36 / UC-15)
List<UUID> getAcceptedFriendIds(UUID userId);

// Remove an accepted friendship (set status = 'DECLINED')
void removeFriendship(UUID userId1, UUID userId2);

// areAcceptedFriends() already declared and correctly implemented — keep as-is
```

---

### 1.2 Implement new `SocialRepository` methods in `SocialJdbcRepository`

**Modify:** `infrastructure/repository/social/SocialJdbcRepository.java`

The `friendship` table already exists. Implement each new method using `JdbcClient` consistent with the existing patterns in that file:

| Method | SQL sketch |
|---|---|
| `saveFriendship` | `INSERT INTO friendship (...) ... ON CONFLICT (requester_id, addressee_id) DO UPDATE SET status=..., version=friendship.version+1, updated_at=now()` — conflict key is the user-pair, NOT `friendship_id`; prevents duplicate logical rows |
| `findFriendshipById` | `SELECT * FROM friendship WHERE friendship_id = :id` |
| `findPendingFriendship` | `SELECT * FROM friendship WHERE status = 'PENDING' AND ((requester_id = :a AND addressee_id = :b) OR (requester_id = :b AND addressee_id = :a))` — bidirectional; catches both A→B and B→A pending rows |
| `findIncomingPendingRequests` | `SELECT * FROM friendship WHERE addressee_id = :id AND status = 'PENDING' ORDER BY created_at DESC` |
| `findOutgoingPendingRequests` | `SELECT * FROM friendship WHERE requester_id = :id AND status = 'PENDING' ORDER BY created_at DESC` |
| `getAcceptedFriendIds` | `SELECT CASE WHEN requester_id = :id THEN addressee_id ELSE requester_id END FROM friendship WHERE (requester_id = :id OR addressee_id = :id) AND status = 'ACCEPTED'` |
| `removeFriendship` | `UPDATE friendship SET status = 'DECLINED', updated_at = now(), version = version + 1 WHERE (requester_id=:a AND addressee_id=:b OR requester_id=:b AND addressee_id=:a) AND status = 'ACCEPTED'` |

**Add `RowMapper<Friendship>`** as a private static final field that maps each column to the `Friendship` Java fields.

---

## 2. Application Layer

---

### 2.1 Fix `HotspotQueryService.getAllHotspots()` — remove empty-list exception

**Modify:** `application/hotspot/HotspotQueryService.java:21–26`

Delete the `if (hotspots.isEmpty()) throw` block. Return the empty list directly. UC-14 treats an empty hotspot list as a valid state.

---

### 2.2 Add `FriendCommandService` — friend request lifecycle

**Create:** `application/social/FriendCommandService.java`

```java
@Service @RequiredArgsConstructor @Transactional
public class FriendCommandService {

    // sendFriendRequest(UUID callerId, UUID targetId) → Friendship
    //   Guards (in order):
    //     1. callerId == targetId → FRIEND_REQUEST_SELF_FORBIDDEN
    //     2. isBlocked(either direction) → FRIEND_REQUEST_BLOCKED
    //     3. areAcceptedFriends → FRIEND_REQUEST_ALREADY_FRIENDS
    //     4. findPendingFriendship(callerId, targetId):
    //          — if existing.requesterId == targetId (B already sent to A): auto-accept that row
    //            by calling acceptFriendRequest(callerId, existing.friendshipId) and returning it.
    //            Do NOT create a new row in this branch.
    //          — if existing.requesterId == callerId (A already sent to B): throw FRIEND_REQUEST_ALREADY_PENDING
    //   Action (only if no existing pending row): Friendship.create(callerId, targetId),
    //           socialRepository.saveFriendship(), publish FRIEND_REQUEST_RECEIVED to targetId

    // acceptFriendRequest(UUID callerId, String friendshipId) → Friendship
    //   Guards: findFriendshipById(friendshipId) → not found → FRIEND_REQUEST_NOT_FOUND
    //           friendship.getAddresseeId() != callerId → FRIEND_REQUEST_NOT_PARTICIPANT
    //           !friendship.isPending() → FRIEND_REQUEST_ALREADY_RESOLVED
    //   Action: friendship.accept(), socialRepository.saveFriendship(friendship)
    //           Publish FRIEND_REQUEST_ACCEPTED notification to friendship.getRequesterId()

    // declineFriendRequest(UUID callerId, String friendshipId) → void
    //   Guards: same pattern as accept; calls friendship.decline()
    //   No notification needed on decline.

    // removeFriend(UUID callerId, UUID targetId) → void
    //   Guard: !socialRepository.areAcceptedFriends(callerId, targetId) → FRIEND_REMOVE_NOT_FRIENDS
    //   Action: socialRepository.removeFriendship(callerId, targetId)
}
```

**Note on naming:** The `friendshipId` parameter used for accept/decline corresponds to the DB `friendship_id` PK column. The API path variable `{requestId}` in `POST /api/v1/friends/requests/{requestId}/accept` maps to this value.

---

### 2.3 Add `FriendQueryService` — read-only friend queries

**Create:** `application/social/FriendQueryService.java`

```java
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class FriendQueryService {

    // getFriends(UUID callerId) → List<UUID>
    //   Calls socialRepository.getAcceptedFriendIds(callerId)

    // getIncomingRequests(UUID callerId) → List<Friendship>
    //   Calls socialRepository.findIncomingPendingRequests(callerId)

    // getOutgoingRequests(UUID callerId) → List<Friendship>
    //   Calls socialRepository.findOutgoingPendingRequests(callerId)
}
```

### 2.4 Fix `SocialQueryService.getFriends()` — stale comment + wrong data source

**Modify:** `application/social/SocialQueryService.java:37–38`

The comment "There is no dedicated Friendship table yet" is incorrect — the table exists. Fix the method body:

```java
// Before (wrong):
return socialRepository.getFolloweeIds(callerId);

// After (correct):
return socialRepository.getAcceptedFriendIds(callerId);
```

---

### 2.4 Enhance `WalkIntentCommandService.createIntent()` — private invite flow

**Modify:** `application/walkintent/WalkIntentCommandService.java`

Split the private path (when `command.isPrivate() == true`) into a dedicated private-invite transaction:

```
// After validating friendship (existing code):
if (command.isPrivate() && command.invitedFriendId() != null) {
    return createPrivateInviteIntent(command);   // NEW: atomic private invite transaction
}
// Public path continues as before...
return walkIntentRepository.save(senderIntent);
```

**New private method `createPrivateInviteIntent(command)`** — all steps in one `@Transactional` boundary:
1. Create and persist sender intent (status = `MATCHING`).
2. Validate invited friend has no overlap (call `walkIntentRepository.hasOverlappingActiveIntent` and `walkSessionRepository.hasOverlappingActiveSession` for `invitedFriendId`). Throw `INTENT_OVERLAPPING` / `INTENT_OVERLAPPING_SESSION` if conflict.
3. Create and persist receiver intent (`is_private = true`, status = `MATCHING`, `userId = invitedFriendId`). This intent must never appear in public `OPEN` query results — the `WalkIntentRepository.listActiveIntents()` query already filters by `is_private = false` or must be updated to do so.
4. Create `MatchProposal` via `matchProposalRepository.save(MatchProposal.create(...))`.
5. Auto-accept sender: call `matchingCommandService.acceptProposal(proposal.getProposalId(), UUID.fromString(command.userId()))`.
6. Publish notifications: `INVITE_SENT` to sender, `PROPOSAL_RECEIVED` to receiver.
7. Return sender intent (the controller will also attach the proposal to the response — see §3.1).

---

### 2.5 Inline matching in `WalkIntentCommandService.createIntent()` — public path

**Modify:** `application/walkintent/WalkIntentCommandService.java`

After saving the public intent, immediately attempt inline match:

```java
WalkIntent saved = walkIntentRepository.save(intent);
// Inline match attempt (public path only)
if (!command.isPrivate()) {
    try {
        Optional<MatchProposal> proposal = matchingCommandService.findOrCreateProposal(
                saved.getIntentId(), UUID.fromString(command.userId()));
        return new CreateIntentResult(saved, proposal.orElse(null));
    } catch (DomainException e) {
        // Only swallow known domain-level "no candidate" signals.
        // Infrastructure exceptions (DB, network) must NOT be swallowed —
        // they propagate and roll back the outer @Transactional boundary.
        log.warn("Inline match skipped for intent {}: {}", saved.getIntentId(), e.getMessage());
    }
}
return new CreateIntentResult(saved, null);
```

**Create:** `application/walkintent/CreateIntentResult.java` — a plain value record:
```java
public record CreateIntentResult(WalkIntent intent, MatchProposal proposal) {}
```

---

## 3. Presentation Layer

---

### 3.1 New response DTO: `CreateIntentResponse`

**Create:** `presentation/dto/response/walkintent/CreateIntentResponse.java`

```java
public record CreateIntentResponse(
        @JsonProperty("intent")   WalkIntentResponse  intent,
        @JsonProperty("proposal") WalkProposalResponse proposal  // null when no match found
) {}
```

---

### 3.2 Update `WalkIntentController.createIntent()` return type and wiring

**Modify:** `presentation/controller/walkintent/WalkIntentController.java:53–76`

Change return type from `ResponseEntity<ApiResponse<WalkIntentResponse>>` to `ResponseEntity<ApiResponse<CreateIntentResponse>>`.

New flow:
```java
// Delegate full create (public OR private) to service, which returns CreateIntentResult
CreateIntentResult result = walkIntentCommandService.createIntent(command);

WalkIntentResponse intentResponse = walkIntentMapper.toResponse(result.intent());
WalkProposalResponse proposalResponse = result.proposal() != null
        ? proposalMapper.toResponse(result.proposal(),
                principal.userId().toString(),
                null  // sessionId null at creation time
          )
        : null;

return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(new CreateIntentResponse(intentResponse, proposalResponse)));
```

---

### 3.3 Add `is_reviewed` field to `WalkSessionResponse`

**Modify:** `presentation/dto/response/session/WalkSessionResponse.java`

Add:
```java
@JsonProperty("is_reviewed")
boolean isReviewed          // true if the calling user has already reviewed this session
```

**Modify:** `presentation/mapper/session/SessionMapper.java` — `toResponse()` must accept a second parameter `boolean isReviewed` (or a pre-loaded flag). The caller (`SessionController` / `SessionHistoryController`) passes this by querying `walkReviewRepository.existsBySessionIdAndReviewerId(session.getSessionId(), callerId)`. For active sessions (PENDING/ACTIVE), this is always `false`.

---

### 3.4 Fix UC-01 Register response — return email-only confirmation

**Modify:** `presentation/controller/user/UserController.java:45–53`

**Create:** `presentation/dto/response/user/RegisterUserResponse.java`
```java
public record RegisterUserResponse(String email) {}
```

Change `registerUser()` to:
```java
@PostMapping("/register")
public ResponseEntity<ApiResponse<RegisterUserResponse>> registerUser(...) {
    userCommandService.registerUser(new RegisterUserCommand(
            request.fullname(), request.email(), request.password()));
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(new RegisterUserResponse(request.email())));
}
```

**Modify:** `RegisterUserRequest.java` — remove `deviceId` field (not in spec).
**Modify:** `RegisterUserCommand.java` — remove `deviceId` field.
**Modify:** `UserCommandService.registerUser()` — remove token issuance; only create the user account and return `void` (or the user entity).

---

### 3.5 Fix UC-10/11 Logout return type

**Modify:** `presentation/controller/user/UserController.java:88–106`

Change both `logout()` and `logoutAll()` from `ResponseEntity<Void>` with `noContent()` to `ResponseEntity<ApiResponse<Void>>` with `ApiResponse.success(null)` and HTTP 200.

---

### 3.6 New `FriendsController` — UC-34–38 Friends API

**Create:** `presentation/controller/social/FriendsController.java`

Endpoints to implement:

```java
POST  /api/v1/friends/{userId}/request                   → sendFriendRequest()    → 201 + FriendshipResponse
POST  /api/v1/friends/requests/{requestId}/accept        → acceptFriendRequest()  → 200 + FriendshipResponse
POST  /api/v1/friends/requests/{requestId}/decline       → declineFriendRequest() → 200 + ApiResponse<Void>
GET   /api/v1/friends                                    → getFriends()           → 200 + List<UserSummaryResponse>
GET   /api/v1/friends/requests/incoming                  → getIncomingRequests()  → 200 + List<FriendshipResponse>
GET   /api/v1/friends/requests/outgoing                  → getOutgoingRequests()  → 200 + List<FriendshipResponse>
DELETE /api/v1/friends/{userId}                          → removeFriend()         → 200 + ApiResponse<Void>
```

Each endpoint maps to the corresponding `FriendCommandService` / `FriendQueryService` method. List endpoints resolve UUIDs to `UserSummaryResponse` via `UserQueryService.getProfile()`.

**Create:** `presentation/dto/response/social/FriendshipResponse.java`
```java
public record FriendshipResponse(
        @JsonProperty("friendship_id")  String friendshipId,  // maps to friendship.friendship_id
        @JsonProperty("requester_id")   String requesterId,   // maps to friendship.requester_id
        @JsonProperty("addressee_id")   String addresseeId,   // maps to friendship.addressee_id
        String status,
        @JsonProperty("created_at")     String createdAt
) {}
```

Note: JSON fields use `requester_id`/`addressee_id` matching the DB column names. The spec refers to these abstractly as "sender" and "receiver" — the controller Javadoc should clarify the mapping.

---

### 3.7 Fix `GET /api/v1/users/me/friends` to return accepted friends

**Modify:** `presentation/controller/social/SocialController.java:92–101`

The `queryService.getFriends()` call now delegates to `socialRepository.getAcceptedFriendIds()` (fixed in §2.4), so no controller change is needed. The fix is entirely in `SocialQueryService`.

---

## Implementation Order

```
Phase 1 — Quick Response Fixes (no domain change)
  - 2.1 Fix HotspotQueryService empty list
  - 3.4 Fix Register response (UC-01)
  - 3.5 Fix Logout 204 → 200 (UC-10/11)

Phase 2 — Session isReviewed
  - 3.3 Add is_reviewed to WalkSessionResponse + SessionMapper

Phase 3 — Create Intent Enhancement (public inline match)
  - 2.5 Inline matching in WalkIntentCommandService
  - 3.1 New CreateIntentResponse DTO
  - 3.2 Update WalkIntentController return type + wiring

Phase 4 — Private Invite Flow
  - 2.4 createPrivateInviteIntent atomic transaction in WalkIntentCommandService
  - (Uses existing MatchingCommandService.acceptProposal)

Phase 5 — Friends System — Domain & Repository
  NOTE: friendship table already exists in DB — NO migration required.
  - 0   Create Friendship domain entity (fields: friendshipId, requesterId, addresseeId, status, version)
        + FriendshipErrorCode enum
  - 1.1 Extend SocialRepository interface with 7 new method signatures
  - 1.2 Implement new methods in SocialJdbcRepository using existing friendship table

Phase 6 — Friends System — Application & Presentation
  - 2.2 FriendCommandService (sendFriendRequest with bidirectional pending check + auto-accept,
        acceptFriendRequest, declineFriendRequest, removeFriend)
  - 2.3 FriendQueryService (getFriends, getIncomingRequests, getOutgoingRequests)
  - 2.4 Fix SocialQueryService.getFriends() to call getAcceptedFriendIds()
  - 3.6 FriendsController (7 endpoints; POST /request → 201 Created per UC-34)
  - Create FriendshipResponse DTO
  - Gap 8.3: Hard-cut follow/unfollow from SocialController
    Remove: POST /follow, DELETE /follow, GET /followers, GET /following endpoints
    Keep: POST /block, DELETE /block (still in spec as UC-37/38)
    Deprecation comment not required — hard delete per user decision.
```
