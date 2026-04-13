# WalkMate Backend — Execution Playbook
**Date:** 2026-04-13
**Based on:** `gap_analysis.md` + `implementation_plan.md` + `docs/single-source-of-truth/use-cases/backend_use_cases.md`
**Architecture:** Spring Boot 3 · Hexagonal · JDBC (no JPA) · Lombok · JWT · MongoDB (chat only)
**Strategy:** Bottom-up — Domain first, Application second, Infrastructure/Repository third, Presentation last. Each phase is atomic and independently verifiable.

---

## How to Use This Playbook

1. Execute phases **in order** — later phases depend on types created in earlier ones.
2. Each prompt is **copy-pasteable** into a new AI session. It is fully self-contained.
3. Every phase begins by running the MCP index command to ensure the codebase graph is current.
4. Every phase ends by writing a **phase report** to `docs/dev/backend/`.
5. The ACKG MCP tool (`mcp__ackg-walkmate__get_file_outline`, `mcp__ackg-walkmate__search_symbols`, `mcp__ackg-walkmate__find_usages`, `mcp__ackg-walkmate__get_definition`) **must be used** to read every file before modifying it.
6. **Never** violate the non-negotiable architecture rules repeated in each prompt.

---

## Non-Negotiable Architecture Rules

These apply to every phase without exception:

| Rule | Enforcement |
|---|---|
| Hexagonal layering | Domain has no Spring annotations. Application layer orchestrates. Infrastructure implements domain interfaces. Presentation maps DTOs. |
| No JPA | All persistence is via `NamedParameterJdbcTemplate`. No `@Entity`, no Hibernate. |
| Lombok | Use `@RequiredArgsConstructor`, `@Slf4j` where appropriate. No manual constructors unless Lombok cannot generate them. |
| Domain exceptions | Business errors are `DomainException(ErrorCode)`. Never throw plain `RuntimeException` for domain violations. |
| No speculative code | Only implement what the plan explicitly requires. No extra abstractions. |
| Transactional boundaries | Application service methods that mutate state are `@Transactional`. Read-only queries are `@Transactional(readOnly = true)`. |
| Response shape | All controller responses are `ResponseEntity<ApiResponse<T>>`. Never return raw objects. |

---

## MCP Index Command

**Run this command at the start of EVERY phase before touching any file:**

```
node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"
```

---

## Phase Map

| Phase | Name | Key Deliverable | Gaps Closed |
|---|---|---|---|
| 1 | Quick Response Fixes | Hotspot empty list, Register shape, Logout status | 2.1, 1.1, 1.2 |
| 2 | Session `isReviewed` | `WalkSessionResponse` field + SessionMapper | 5.1 |
| 3 | Create Intent — Inline Match (Public) | `CreateIntentResponse` DTO + inline match wiring | 3.1, 3.3 |
| 4 | Create Intent — Private Invite Flow | Atomic paired intent + proposal + auto-accept | 3.2 |
| 5 | Friends System — Domain & Repository | `Friendship` entity (maps existing DB table) + 7 new repo methods | 8.1 (partial) |
| 6 | Friends System — Application & Presentation | Services + Controller + fix `SocialQueryService.getFriends()` + hard-cut follow/unfollow | 8.1, 8.2, 8.3 |

---

## Phase 1: Quick Response Fixes

**Objective:** Fix three low-risk response shape issues with no domain model changes. Each fix is a one-file change.
**Gaps Closed:** 2.1 (Hotspot empty list), 1.1 (Register response), 1.2 (Logout 204→200)
**Depends On:** Nothing — this is the starting point.

---

### Prompt for Phase 1

```
## Phase 1: Quick Response Fixes

## MCP Index (run first)
node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Architecture Rules (Non-Negotiable)
- Spring Boot 3 / Hexagonal — domain has no Spring annotations
- No JPA — JDBC only
- All controller responses are ResponseEntity<ApiResponse<T>>
- DomainException(ErrorCode) for domain violations — never plain RuntimeException
- No speculative code — only fix what's listed below

## Reference Documents
Read these before starting:
- docs/single-source-of-truth/use-cases/backend_use_cases.md (UC-01, UC-10, UC-11, UC-14)
- docs/dev/backend/gap_analysis.md (gaps 1.1, 1.2, 2.1)
- docs/dev/backend/implementation_plan.md (sections 2.1, 3.4, 3.5)

## Fix 1 — HotspotQueryService empty list (Gap 2.1)
File: backend/src/main/java/com/walkmate/application/hotspot/HotspotQueryService.java

Use mcp__ackg-walkmate__get_file_outline on the file first.
Remove the guard block:
  if (hotspots.isEmpty()) { throw new DomainException(HotspotErrorCode.NO_HOTSPOT_AVAILABLE); }
The method should return the list as-is (empty list is valid).
Do NOT remove the HotspotErrorCode.NO_HOTSPOT_AVAILABLE entry — it may be used elsewhere.

## Fix 2 — Register response (Gap 1.1)
Steps:
1. Create presentation/dto/response/user/RegisterUserResponse.java:
   public record RegisterUserResponse(String email) {}

2. Modify presentation/dto/request/user/RegisterUserRequest.java:
   - Remove the deviceId field (spec UC-01 has no deviceId in register payload).
   
3. Modify application/user/RegisterUserCommand.java:
   - Remove deviceId field.
   
4. Modify application/user/UserCommandService.java — registerUser() method:
   - Use mcp__ackg-walkmate__get_definition to read the full method first.
   - Remove token issuance. The method should only create the user account.
   - Change return type to void (or User if needed for the email). No LoginResult.
   
5. Modify presentation/controller/user/UserController.java — registerUser() endpoint:
   - Change return type to ResponseEntity<ApiResponse<RegisterUserResponse>>.
   - Call userCommandService.registerUser(command) — no LoginResult.
   - Return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(new RegisterUserResponse(request.email()))).

IMPORTANT: After removing deviceId from RegisterUserCommand, check
mcp__ackg-walkmate__find_usages for RegisterUserCommand to ensure no other callers pass deviceId.

## Fix 3 — Logout 204 → 200 (Gap 1.2)
File: presentation/controller/user/UserController.java

Use mcp__ackg-walkmate__get_file_outline first.

For both logout() and logoutAll():
- Change return type from ResponseEntity<Void> to ResponseEntity<ApiResponse<Void>>.
- Replace ResponseEntity.noContent().build() with ResponseEntity.ok(ApiResponse.success(null)).

## Verification
After all three fixes:
1. Verify HotspotQueryService no longer throws on empty list — read the method.
2. Verify registerUser() returns RegisterUserResponse, not LoginUserResponse.
3. Verify both logout methods return 200 with ApiResponse<Void>.
4. Run: node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Phase Report
Write a brief report to docs/dev/backend/phase1_report.md confirming:
- Which files were modified
- That the three gaps are closed
- Any unexpected findings
```

---

## Phase 2: Session `isReviewed`

**Objective:** Add `is_reviewed` boolean to `WalkSessionResponse` and populate it in `SessionMapper` so the client can show/hide the review prompt on history items.
**Gaps Closed:** 5.1
**Depends On:** Phase 1 (no hard dependency, but run after to keep phases ordered).

---

### Prompt for Phase 2

```
## Phase 2: Session isReviewed

## MCP Index (run first)
node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Architecture Rules (Non-Negotiable)
- No JPA — JDBC only
- All controller responses are ResponseEntity<ApiResponse<T>>
- The is_reviewed field is caller-specific: it is true when the calling user has already submitted a review for this session
- SessionMapper must NOT make DB calls directly — it is a pure mapper. DB queries belong in Application Services.

## Reference Documents
Read before starting:
- docs/single-source-of-truth/use-cases/backend_use_cases.md (UC-29, UC-31)
- docs/dev/backend/gap_analysis.md (gap 5.1)
- docs/dev/backend/implementation_plan.md (section 3.3)

## Step 1: Extend WalkSessionResponse
File: presentation/dto/response/session/WalkSessionResponse.java

Use mcp__ackg-walkmate__get_file_outline first.
Add the field at the end of the record:
  @JsonProperty("is_reviewed") boolean isReviewed

## Step 2: Extend SessionMapper
File: presentation/mapper/session/SessionMapper.java

Use mcp__ackg-walkmate__get_file_outline first.
The toResponse() signature must accept a boolean isReviewed parameter:
  public WalkSessionResponse toResponse(WalkSession session, boolean isReviewed)

Pass isReviewed into the new WalkSessionResponse constructor parameter.

## Step 3: Update all callers of SessionMapper.toResponse()
Use mcp__ackg-walkmate__find_usages to find every call to sessionMapper.toResponse().
For each caller:
  - Identify the session and the calling user (callerId).
  - For ACTIVE/PENDING sessions: pass false (review not yet possible).
  - For terminal sessions (history endpoint): call 
    walkReviewRepository.existsBySessionIdAndReviewerId(session.getSessionId(), callerId)
    and pass the boolean result.
  
Use mcp__ackg-walkmate__get_definition on WalkReviewRepository to confirm the
existsBySessionIdAndReviewerId method signature. If it does not exist, add it to the
WalkReviewRepository interface and implement it in WalkReviewJdbcRepository.

## Step 4: Verify WalkReviewRepository has existsBySessionIdAndReviewerId
File: domain/review/WalkReviewRepository.java

If the method is missing:
1. Add: boolean existsBySessionIdAndReviewerId(String sessionId, String reviewerId);
2. Implement in infrastructure/repository/review/WalkReviewJdbcRepository.java using a
   SELECT COUNT(*) > 0 query.

## Verification
Read WalkSessionResponse — confirm is_reviewed field is present.
Read SessionMapper — confirm the new overload.
Read each controller that uses SessionMapper — confirm isReviewed is passed correctly.
Run: node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Phase Report
Write a brief report to docs/dev/backend/phase2_report.md.
```

---

## Phase 3: Create Intent — Inline Match (Public Path)

**Objective:** Wire up the matching engine to run inline after a public intent is created. Return a `CreateIntentResponse` that carries both the intent and an optional proposal so the client can route correctly (stay on Intent tab vs. switch to Proposal tab).
**Gaps Closed:** 3.1, 3.3
**Depends On:** Nothing from Phase 1/2, but run after to keep phases ordered.

---

### Prompt for Phase 3

```
## Phase 3: Create Intent — Inline Match (Public Path)

## MCP Index (run first)
node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Architecture Rules (Non-Negotiable)
- The inline match attempt must NOT break the @Transactional boundary.
  WalkIntentCommandService.createIntent() is @Transactional. MatchingCommandService.findOrCreateProposal()
  is also @Transactional. Since both run within the same thread, Spring will propagate the transaction.
  Do NOT call findOrCreateProposal() via a separate TransactionTemplate here — let the outer transaction
  absorb it.
- If inline match throws a non-critical exception (e.g., no candidates), catch it silently and return
  CreateIntentResult(saved, null). Do not surface matching failures as intent creation failures.
- The new CreateIntentResponse is a Presentation DTO — it must NOT cross into domain or application layers.

## Reference Documents
Read before starting:
- docs/single-source-of-truth/use-cases/backend_use_cases.md (UC-15 Cases A1 and A2)
- docs/dev/backend/gap_analysis.md (gaps 3.1, 3.3)
- docs/dev/backend/implementation_plan.md (sections 2.5, 3.1, 3.2)

## Step 1: Create CreateIntentResult value record
Create: application/walkintent/CreateIntentResult.java

public record CreateIntentResult(WalkIntent intent, MatchProposal proposal) {
    // proposal is null when no match was found
}

## Step 2: Update WalkIntentCommandService.createIntent() — public path
File: application/walkintent/WalkIntentCommandService.java

Use mcp__ackg-walkmate__get_file_outline and mcp__ackg-walkmate__get_definition first.

After saving the intent (line currently: return walkIntentRepository.save(intent)), add the inline
match attempt for public (non-private) intents:

  WalkIntent saved = walkIntentRepository.save(intent);

  if (!command.isPrivate()) {
      try {
          Optional<MatchProposal> proposal = matchingCommandService.findOrCreateProposal(
                  saved.getIntentId(), UUID.fromString(command.userId()));
          return new CreateIntentResult(saved, proposal.orElse(null));
      } catch (DomainException e) {
          // Only swallow known domain-level "no candidate" signals.
          // Do NOT catch Exception broadly — infrastructure failures (DB, network)
          // must propagate and roll back the outer @Transactional boundary.
          log.warn("Inline match skipped for intent {}: {}", saved.getIntentId(), e.getMessage());
      }
  }
  return new CreateIntentResult(saved, null);

Change the method return type from WalkIntent to CreateIntentResult.

Add @Slf4j to the class if not already present.
Inject MatchingCommandService via constructor (it must be added to @RequiredArgsConstructor fields).

IMPORTANT: Use mcp__ackg-walkmate__find_usages on createIntent() to find ALL callers before changing
the return type. Update each caller accordingly.

## Step 3: Create CreateIntentResponse DTO
Create: presentation/dto/response/walkintent/CreateIntentResponse.java

public record CreateIntentResponse(
        @JsonProperty("intent")   WalkIntentResponse   intent,
        @JsonProperty("proposal") WalkProposalResponse proposal  // null = no match yet
) {}

## Step 4: Update WalkIntentController.createIntent()
File: presentation/controller/walkintent/WalkIntentController.java

Use mcp__ackg-walkmate__get_file_outline first.

1. Change return type: ResponseEntity<ApiResponse<CreateIntentResponse>>
2. Call walkIntentCommandService.createIntent(command) → now returns CreateIntentResult
3. Map:
   WalkIntentResponse intentResp = walkIntentMapper.toResponse(result.intent());
   WalkProposalResponse proposalResp = null;
   if (result.proposal() != null) {
       proposalResp = proposalMapper.toResponse(
           result.proposal(), principal.userId().toString(), null);
   }
4. Return:
   ResponseEntity.status(HttpStatus.CREATED)
       .body(ApiResponse.success(new CreateIntentResponse(intentResp, proposalResp)));

## Verification
Read WalkIntentCommandService — confirm inline match is called for public path.
Read WalkIntentController — confirm return type is CreateIntentResponse.
Read CreateIntentResult — confirm it exists.
Read CreateIntentResponse — confirm it has both intent and proposal fields.
Run: node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Phase Report
Write a brief report to docs/dev/backend/phase3_report.md.
```

---

## Phase 4: Create Intent — Private Invite Flow

**Objective:** Implement the atomic private invite transaction: create paired intents → create proposal → auto-accept sender → fire push notifications. This is the most complex single phase.
**Gaps Closed:** 3.2
**Depends On:** Phase 3 (CreateIntentResult type, WalkIntentCommandService structure).

---

### Prompt for Phase 4

```
## Phase 4: Create Intent — Private Invite Flow

## MCP Index (run first)
node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Architecture Rules (Non-Negotiable)
- The ENTIRE private invite flow (5 steps) must execute in a SINGLE @Transactional boundary.
  If any step fails, ALL DB changes roll back. No partial state must be committed.
- The receiver intent must have is_private = true. The WalkIntentRepository.listActiveIntents()
  query (UC-16) must NOT return private intents. Verify this is already filtered; if not, add
  the filter.
- invariant I-7: Private intents must never appear in public OPEN matching results.
  Verify WalkIntentRepository's matching query excludes is_private = true.
- invariant P-1(b): Proposal is created atomically with both MATCHING intents.
- The auto-accept of the sender mimics the existing MatchingCommandService.acceptProposal() logic
  for side A. Do NOT duplicate that logic — call the service method directly.

## Reference Documents
Read before starting:
- docs/single-source-of-truth/use-cases/backend_use_cases.md (UC-15 Case B)
- docs/single-source-of-truth/lifecycle/invariants.md (I-2, I-7, P-1)
- docs/dev/backend/gap_analysis.md (gap 3.2)
- docs/dev/backend/implementation_plan.md (section 2.4)

## Step 1: Read and understand existing types
Use mcp__ackg-walkmate__get_file_outline on:
- application/walkintent/WalkIntentCommandService.java
- application/walkintent/CreateWalkIntentCommand.java
- application/proposal/MatchingCommandService.java
- domain/walkintent/WalkIntent.java
- domain/proposal/MatchProposal.java

## Step 2: Implement createPrivateInviteIntent() in WalkIntentCommandService

The createIntent() method already handles the public path after Phase 3. For the private branch, route
to a new private method BEFORE the inline-match attempt:

  if (command.isPrivate() && command.invitedFriendId() != null) {
      return createPrivateInviteIntent(command);
  }
  // ... public path continues

Implement createPrivateInviteIntent(CreateWalkIntentCommand command):

  @Transactional (propagates from the outer createIntent @Transactional)
  private CreateIntentResult createPrivateInviteIntent(CreateWalkIntentCommand command) {
      // 1. Create sender intent (status = MATCHING)
      WalkIntent senderIntent = WalkIntent.create(
          command.hotspotId(), command.userId(),
          command.timeWindowStart(), command.timeWindowEnd(),
          new MatchingConstraints(command.ageMin(), command.ageMax()),
          true, command.invitedFriendId(), command.description()
      );
      senderIntent.lockForMatching();   // transitions OPEN → MATCHING
      WalkIntent savedSender = walkIntentRepository.save(senderIntent);

      // 2. Check receiver overlap (I-1 applied to invited friend)
      String receiverId = command.invitedFriendId();
      if (walkIntentRepository.hasOverlappingActiveIntent(
              receiverId, command.timeWindowStart(), command.timeWindowEnd())) {
          throw new DomainException(WalkIntentErrorCode.INTENT_OVERLAPPING);
      }
      if (walkSessionRepository.hasOverlappingActiveSession(
              receiverId, command.timeWindowStart(), command.timeWindowEnd())) {
          throw new DomainException(WalkIntentErrorCode.INTENT_OVERLAPPING_SESSION);
      }

      // 3. Create system-generated receiver intent (MATCHING, private, never enters public OPEN)
      WalkIntent receiverIntent = WalkIntent.create(
          command.hotspotId(), receiverId,
          command.timeWindowStart(), command.timeWindowEnd(),
          new MatchingConstraints(command.ageMin(), command.ageMax()),
          true, command.userId(), null
      );
      receiverIntent.lockForMatching();
      WalkIntent savedReceiver = walkIntentRepository.save(receiverIntent);

      // 4. Create proposal
      MatchProposal proposal = MatchProposal.createPrivate(
          savedSender.getIntentId(), savedReceiver.getIntentId(),
          command.userId(), receiverId,
          command.timeWindowStart(), command.timeWindowEnd(),
          /* hotspot lat/lng */ hotspotRepository.findById(command.hotspotId()).get().getLat(),
          hotspotRepository.findById(command.hotspotId()).get().getLng()
      );
      MatchProposal savedProposal = matchProposalRepository.save(proposal);

      // 5. Auto-accept sender side
      matchingCommandService.acceptProposal(
          savedProposal.getProposalId(), UUID.fromString(command.userId()));

      // 6. Notifications
      notificationPublisher.publish(Notification.create(
          command.userId(), NotificationType.INVITE_SENT,
          Map.of("proposalId", savedProposal.getProposalId())));
      notificationPublisher.publish(Notification.create(
          receiverId, NotificationType.PROPOSAL_RECEIVED,
          Map.of("proposalId", savedProposal.getProposalId())));

      return new CreateIntentResult(savedSender, savedProposal);
  }

IMPORTANT: If MatchProposal.createPrivate() does not exist, check MatchProposal.create() signature
and create an appropriate factory method or check if the existing one can be reused.
Use mcp__ackg-walkmate__get_definition on MatchProposal to confirm.

## Step 3: Verify WalkIntentRepository excludes private intents from public queries
Use mcp__ackg-walkmate__find_usages on WalkIntentRepository to find:
- listActiveIntents() — used by GET /api/v1/intents (UC-16). Must NOT return private intents.
- The matching engine query (used in RuleBasedMatchingStrategy). Must NOT match against private intents.

Read infrastructure/repository/walkintent/WalkIntentJdbcRepository.java.
If the SQL for listActiveIntents() does not filter WHERE is_private = false, add that filter.
If the matching query does not exclude is_private = true intents, add the filter.

## Verification
Read WalkIntentCommandService — confirm private path routes to createPrivateInviteIntent().
Read createPrivateInviteIntent — confirm all 6 steps are present.
Read WalkIntentJdbcRepository — confirm listActiveIntents and match queries exclude private intents.
Run: node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Phase Report
Write a detailed report to docs/dev/backend/phase4_report.md including:
- Each step implemented
- Any MatchProposal factory method decisions made
- Confirmation of is_private filter in repository queries
```

---

## Phase 5: Friends System — Domain & Repository

**Objective:** Create the `Friendship` domain entity (maps to the existing `friendship` DB table), extend `SocialRepository` with friend-request management methods, and implement them in `SocialJdbcRepository`. No DB migration needed — the table already exists.
**Gaps Closed:** 8.1 (partial — infrastructure only)
**Depends On:** Nothing from previous phases.

---

### Prompt for Phase 5

```
## Phase 5: Friends System — Domain & Repository

## MCP Index (run first)
node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## DB Context (Critical — read before writing any code)
The production DB already has:
  friendship (
    friendship_id  UUID PK,
    requester_id   UUID FK → user_account,   ← the user who sent the request
    addressee_id   UUID FK → user_account,   ← the user who received the request
    status         friend_status enum (PENDING / ACCEPTED / DECLINED),
    version        BIGINT NOT NULL DEFAULT 0,   ← optimistic locking
    created_at     TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ
  )

SocialJdbcRepository (migration V104) already uses this table:
  follow(A,B) → INSERT friendship(requester_id=A, addressee_id=B, status=PENDING)
  unfollow(A,B) → DELETE friendship WHERE requester_id=A AND addressee_id=B
  areAcceptedFriends → SELECT WHERE status='ACCEPTED' (both directions)

NO DB migration is required. Use these exact column names in all SQL.

## Architecture Rules (Non-Negotiable)
- Friendship is a domain entity in domain/social/ — NO Spring annotations.
- All persistence uses JdbcClient (Spring 6) — same client already used in SocialJdbcRepository.
- SocialRepository is a domain interface — only add method signatures. Implementations go in SocialJdbcRepository.
- The version field in Friendship must be included in UPDATE statements for optimistic locking (X-5).
- Do NOT create any DB migration file — table exists.

## Reference Documents
Read before starting:
- docs/single-source-of-truth/use-cases/backend_use_cases.md (UC-34, UC-35, UC-36)
- docs/dev/backend/gap_analysis.md (gap 8.1 — DB audit section)
- docs/dev/backend/implementation_plan.md (sections DE-1, 1.1, 1.2)

## Step 1: Read existing social layer
Use mcp__ackg-walkmate__get_file_outline on:
- domain/social/SocialRepository.java
- domain/social/SocialErrorCode.java
- infrastructure/repository/social/SocialJdbcRepository.java

Then read the full content of SocialJdbcRepository using mcp__ackg-walkmate__get_definition.
Note the existing JdbcClient usage pattern — you must follow the same pattern.

## Step 2: Create Friendship domain entity
Create: domain/social/Friendship.java

DB column → Java field mapping (must be exact):
  friendship_id  → friendshipId  (String)
  requester_id   → requesterId   (UUID)
  addressee_id   → addresseeId   (UUID)
  status         → status        (String: "PENDING" | "ACCEPTED" | "DECLINED")
  version        → version       (long)
  created_at     → createdAt     (Instant)
  updated_at     → updatedAt     (Instant, mutable)

Static factory (for new requests):
  public static Friendship create(UUID requesterId, UUID addresseeId) {
      return new Friendship(UUID.randomUUID().toString(), requesterId, addresseeId,
          "PENDING", 0L, Instant.now(), Instant.now());
  }

Transition methods — each must guard status == "PENDING":
  public void accept()  — status = "ACCEPTED", updatedAt = Instant.now()
  public void decline() — status = "DECLINED", updatedAt = Instant.now()

Helpers: isPending(), isAccepted()
Getters for all fields.

Guard implementation (throws DomainException on wrong state — create FriendshipErrorCode first):
  private void guardPending() {
      if (!"PENDING".equals(status))
          throw new DomainException(FriendshipErrorCode.FRIEND_REQUEST_ALREADY_RESOLVED);
  }

## Step 3: Create FriendshipErrorCode
Create: domain/social/FriendshipErrorCode.java (implements ErrorCode)

Error codes (verify SOCIAL_USER_NOT_FOUND is in SocialErrorCode and reuse — do not duplicate):
  FRIEND_REQUEST_SELF_FORBIDDEN
  FRIEND_REQUEST_ALREADY_FRIENDS
  FRIEND_REQUEST_ALREADY_PENDING
  FRIEND_REQUEST_BLOCKED
  FRIEND_REQUEST_NOT_FOUND
  FRIEND_REQUEST_NOT_PARTICIPANT
  FRIEND_REQUEST_ALREADY_RESOLVED
  FRIEND_REMOVE_NOT_FRIENDS

## Step 4: Extend SocialRepository interface
Modify: domain/social/SocialRepository.java

Add these signatures (interface only — no implementation here):
  void saveFriendship(Friendship friendship);
  Optional<Friendship> findFriendshipById(String friendshipId);
  Optional<Friendship> findPendingFriendship(UUID requesterId, UUID addresseeId);
  List<Friendship> findIncomingPendingRequests(UUID addresseeId);
  List<Friendship> findOutgoingPendingRequests(UUID requesterId);
  List<UUID> getAcceptedFriendIds(UUID userId);
  void removeFriendship(UUID userId1, UUID userId2);

Keep ALL existing methods unchanged. areAcceptedFriends() is already declared and implemented.

## Step 5: Implement new methods in SocialJdbcRepository
Modify: infrastructure/repository/social/SocialJdbcRepository.java

Add a private static final RowMapper<Friendship> FRIENDSHIP_MAPPER that maps each DB row:
  rs.getString("friendship_id") → friendshipId
  UUID.fromString(rs.getString("requester_id")) → requesterId
  UUID.fromString(rs.getString("addressee_id")) → addresseeId
  rs.getString("status") → status
  rs.getLong("version") → version
  rs.getTimestamp("created_at").toInstant() → createdAt
  rs.getTimestamp("updated_at").toInstant() → updatedAt

Implement each new method using JdbcClient (match existing style — use jdbcClient.sql(...).param(...)):

saveFriendship:
  INSERT INTO friendship (friendship_id, requester_id, addressee_id, status, version, created_at, updated_at)
  VALUES (:friendshipId, :requesterId, :addresseeId, CAST(:status AS friend_status), :version, :createdAt, :updatedAt)
  ON CONFLICT (requester_id, addressee_id) DO UPDATE SET
    status = EXCLUDED.status,
    version = friendship.version + 1,
    updated_at = EXCLUDED.updated_at
  -- Conflict key is the USER PAIR (requester_id, addressee_id), NOT friendship_id.
  -- This prevents duplicate logical rows when the same user re-sends a request.
  -- Requires a UNIQUE constraint on (requester_id, addressee_id) in the DB.
  -- If the constraint does not exist yet, note it in the phase report as a prerequisite.

findFriendshipById:
  SELECT * FROM friendship WHERE friendship_id = :id

findPendingFriendship:
  SELECT * FROM friendship
  WHERE status = 'PENDING'
    AND ((requester_id = :requesterId AND addressee_id = :addresseeId)
      OR (requester_id = :addresseeId AND addressee_id = :requesterId))
  -- Bidirectional: catches A→B AND B→A pending rows in one query.
  -- Service layer inspects result.requesterId to distinguish direction.

findIncomingPendingRequests:
  SELECT * FROM friendship WHERE addressee_id = :addresseeId AND status = 'PENDING' ORDER BY created_at DESC

findOutgoingPendingRequests:
  SELECT * FROM friendship WHERE requester_id = :requesterId AND status = 'PENDING' ORDER BY created_at DESC

getAcceptedFriendIds:
  SELECT CASE WHEN requester_id = :userId THEN addressee_id ELSE requester_id END AS friend_id
  FROM friendship
  WHERE (requester_id = :userId OR addressee_id = :userId) AND status = 'ACCEPTED'

removeFriendship:
  UPDATE friendship SET status = CAST('DECLINED' AS friend_status), updated_at = now(), version = version + 1
  WHERE ((requester_id = :a AND addressee_id = :b) OR (requester_id = :b AND addressee_id = :a))
    AND status = 'ACCEPTED'

IMPORTANT: The status column is a PostgreSQL enum (friend_status). Always CAST string literals:
  CAST('PENDING' AS friend_status), CAST('ACCEPTED' AS friend_status), etc.
  Look at existing SQL in SocialJdbcRepository (line ~33) for the pattern already in use.

## Verification
Read Friendship — confirm entity fields match DB columns exactly.
Read FriendshipErrorCode — confirm all 8 error codes.
Read SocialRepository — confirm 7 new method signatures are present.
Read SocialJdbcRepository — confirm all 7 new methods are implemented with correct SQL.
Run: node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Phase Report
Write docs/dev/backend/phase5_report.md including:
- Each step completed
- The RowMapper field name and pattern used
- Any CAST patterns needed for friend_status enum
- Confirmation that NO migration file was created
```

---

## Phase 6: Friends System — Application & Presentation

**Objective:** Build `FriendCommandService`, `FriendQueryService`, `FriendsController`, and fix `SocialQueryService.getFriends()`. After this phase, all UC-34–38 endpoints are operational.
**Gaps Closed:** 8.1 (complete), 8.2
**Depends On:** Phase 5 (Friendship entity + repository methods must exist).

---

### Prompt for Phase 6

```
## Phase 6: Friends System — Application & Presentation

## MCP Index (run first)
node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## DB Context (Critical)
The friendship table uses:
  friendship_id (PK), requester_id, addressee_id, status (friend_status enum), version (optimistic lock)
Phase 5 created: Friendship domain entity, FriendshipErrorCode, and 7 new SocialRepository methods.
Verify Phase 5 is complete by reading docs/dev/backend/phase5_report.md before proceeding.

## Architecture Rules (Non-Negotiable)
- FriendCommandService and FriendQueryService are @Service in application/social/
- FriendsController is @RestController in presentation/controller/social/
- All authenticated endpoints use @AuthenticationPrincipal UserPrincipal principal
- Notifications are published via NotificationPublisher (domain interface) — never call
  FcmNotificationProvider directly
- All responses are ResponseEntity<ApiResponse<T>> — no naked objects

## Reference Documents
Read before starting:
- docs/single-source-of-truth/use-cases/backend_use_cases.md (UC-34, UC-35, UC-36, UC-37, UC-38)
- docs/dev/backend/implementation_plan.md (sections 2.2, 2.3, 2.4, 3.6)
- docs/dev/backend/phase5_report.md

## Step 1: Read existing code
Use mcp__ackg-walkmate__get_file_outline on:
- application/social/SocialCommandService.java
- application/social/SocialQueryService.java
- presentation/controller/social/SocialController.java
- domain/notification/NotificationType.java

## Step 2: Add NotificationTypes if missing
Modify domain/notification/NotificationType.java.
Add FRIEND_REQUEST_RECEIVED and FRIEND_REQUEST_ACCEPTED if not present.

## Step 3: Create FriendCommandService
Create: application/social/FriendCommandService.java

@Service @RequiredArgsConstructor @Transactional
Methods (use Friendship and FriendshipErrorCode from Phase 5):

sendFriendRequest(UUID callerId, UUID targetId) → Friendship
  Guards (in order):
    1. callerId.equals(targetId) → throw FRIEND_REQUEST_SELF_FORBIDDEN
    2. socialRepository.isBlocked(callerId, targetId) || socialRepository.isBlocked(targetId, callerId)
           → throw FRIEND_REQUEST_BLOCKED
    3. socialRepository.areAcceptedFriends(callerId, targetId) → throw FRIEND_REQUEST_ALREADY_FRIENDS
    4. Optional<Friendship> existing = socialRepository.findPendingFriendship(callerId, targetId):
         if existing.isPresent():
           Friendship ex = existing.get();
           if ex.getRequesterId().equals(targetId):
             // B already sent a request to A — auto-accept it instead of creating a new row
             return acceptFriendRequest(callerId, ex.getFriendshipId());
           else:
             // A already sent a request to B — it's still waiting
             throw FRIEND_REQUEST_ALREADY_PENDING
  Action (only if no existing pending row):
    Friendship fs = Friendship.create(callerId, targetId);
    socialRepository.saveFriendship(fs);
    notificationPublisher.publish(Notification.create(targetId.toString(),
        NotificationType.FRIEND_REQUEST_RECEIVED,
        Map.of("friendshipId", fs.getFriendshipId(), "requesterId", callerId.toString())));
  Return: fs

acceptFriendRequest(UUID callerId, String friendshipId) → Friendship
  Guards:
    findFriendshipById(friendshipId) absent → throw FRIEND_REQUEST_NOT_FOUND
    !friendship.getAddresseeId().equals(callerId) → throw FRIEND_REQUEST_NOT_PARTICIPANT
    !friendship.isPending() → throw FRIEND_REQUEST_ALREADY_RESOLVED (guarded in entity)
  Action:
    friendship.accept();
    socialRepository.saveFriendship(friendship);
    notificationPublisher.publish(Notification.create(friendship.getRequesterId().toString(),
        NotificationType.FRIEND_REQUEST_ACCEPTED,
        Map.of("friendshipId", friendshipId)));
  Return: friendship

declineFriendRequest(UUID callerId, String friendshipId) → void
  Same guards as accept.
  Action: friendship.decline(); socialRepository.saveFriendship(friendship);
  No notification.

removeFriend(UUID callerId, UUID targetId) → void
  Guard: !socialRepository.areAcceptedFriends(callerId, targetId) → throw FRIEND_REMOVE_NOT_FRIENDS
  Action: socialRepository.removeFriendship(callerId, targetId);

## Step 4: Create FriendQueryService
Create: application/social/FriendQueryService.java

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
Methods:
  getFriends(UUID callerId) → List<UUID>
    return socialRepository.getAcceptedFriendIds(callerId);

  getIncomingRequests(UUID callerId) → List<Friendship>
    return socialRepository.findIncomingPendingRequests(callerId);

  getOutgoingRequests(UUID callerId) → List<Friendship>
    return socialRepository.findOutgoingPendingRequests(callerId);

## Step 5: Fix SocialQueryService.getFriends()
Modify: application/social/SocialQueryService.java

Inject FriendQueryService (add to @RequiredArgsConstructor fields).
Change getFriends():
  // Before (stale — returns followees via getFolloweeIds):
  return socialRepository.getFolloweeIds(callerId);
  // After (correct — returns accepted friends):
  return friendQueryService.getFriends(callerId);

Remove the stale comment "There is no dedicated Friendship table yet" — it is incorrect.

## Step 6: Create FriendshipResponse DTO
Create: presentation/dto/response/social/FriendshipResponse.java

public record FriendshipResponse(
    @JsonProperty("friendship_id")  String friendshipId,   // maps to friendship.friendship_id
    @JsonProperty("requester_id")   String requesterId,    // maps to friendship.requester_id
    @JsonProperty("addressee_id")   String addresseeId,    // maps to friendship.addressee_id
    String status,
    @JsonProperty("created_at")     String createdAt
) {}

NOTE: JSON fields use requester_id/addressee_id — matching DB column names exactly.
The spec calls them "sender"/"receiver" in prose; the DTO comment should clarify the mapping.

## Step 7: Create FriendsController
Create: presentation/controller/social/FriendsController.java

@Tag(name = "Friends", description = "Friend request and friendship management (UC-34–38)")
@RestController @RequestMapping("/api/v1/friends") @RequiredArgsConstructor

Inject: FriendCommandService, FriendQueryService, UserQueryService

POST /request → mapped to /{userId}/request
  Calls friendCommandService.sendFriendRequest(callerId, UUID.fromString(userId))
  Returns: ResponseEntity.status(201).body(ApiResponse.success(toFriendshipResponse(fs)))

POST /requests/{requestId}/accept
  Calls friendCommandService.acceptFriendRequest(callerId, requestId)
  Returns: ResponseEntity.ok(ApiResponse.success(toFriendshipResponse(fs)))

POST /requests/{requestId}/decline
  Calls friendCommandService.declineFriendRequest(callerId, requestId)
  Returns: ResponseEntity.ok(ApiResponse.success(null))

GET / (root)
  Calls friendQueryService.getFriends(callerId) → List<UUID>
  Resolves each UUID: userQueryService.getProfile(uid) → UserSummaryResponse
  Returns: ResponseEntity.ok(ApiResponse.success(list))

GET /requests/incoming
  Calls friendQueryService.getIncomingRequests(callerId)
  Returns: ResponseEntity.ok(ApiResponse.success(list mapped to FriendshipResponse))

GET /requests/outgoing
  Calls friendQueryService.getOutgoingRequests(callerId)
  Returns: ResponseEntity.ok(ApiResponse.success(list mapped to FriendshipResponse))

DELETE /{userId}
  Calls friendCommandService.removeFriend(callerId, UUID.fromString(userId))
  Returns: ResponseEntity.ok(ApiResponse.success(null))

Private helper:
  private FriendshipResponse toFriendshipResponse(Friendship fs) {
      return new FriendshipResponse(fs.getFriendshipId(), fs.getRequesterId().toString(),
          fs.getAddresseeId().toString(), fs.getStatus(), fs.getCreatedAt().toString());
  }

## Step 8: Hard-cut follow/unfollow endpoints from SocialController (Gap 8.3)
File: presentation/controller/social/SocialController.java

Use mcp__ackg-walkmate__get_file_outline to read the full method list.

DELETE the following four endpoint methods entirely:
  - POST /follow  (the follow() method)
  - DELETE /follow  (the unfollow() method)
  - GET /followers  (the getFollowers() method)
  - GET /following  (the getFollowing() method)

KEEP the following (still in spec as UC-37/38):
  - POST /block  (the block() method)
  - DELETE /block  (the unblock() method)

After removing the four methods:
- Remove any imports that are now unused (e.g., follow/unfollow-specific DTOs, if any).
- Use mcp__ackg-walkmate__find_usages on follow(), unfollow(), getFollowers(), getFollowing()
  to confirm no other code calls these controller methods before deleting.
- Do NOT remove the underlying SocialCommandService.follow/unfollow methods — they are still
  called internally by the friends system (saveFriendship in SocialJdbcRepository rewrites
  follow semantics). Only the HTTP endpoint handlers are removed.

RATIONALE: POST /api/v1/friends/{userId}/request (added in Step 7) replaces the /follow endpoint
semantically. Keeping both live simultaneously would create two competing paths for creating a
PENDING friendship row.

## Verification
Read FriendCommandService — confirm 4 methods with guards using FriendshipErrorCode.
Read FriendQueryService — confirm 3 methods.
Read SocialQueryService.getFriends() — confirm it calls friendQueryService, not getFolloweeIds.
Read FriendsController — confirm all 7 endpoints, correct return types.
Read SocialController — confirm follow/unfollow/getFollowers/getFollowing endpoints are ABSENT;
  block/unblock endpoints are PRESENT.
Run: node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"

## Phase Report
Write docs/dev/backend/phase6_report.md including:
- All 7 endpoints and their response shapes
- Notification types used
- Confirmation SocialQueryService.getFriends() is fixed
- Confirmation follow/unfollow/getFollowers/getFollowing endpoints removed from SocialController
- Any edge cases encountered with friend_status enum casting
```

---

## Final Verification Checklist

After all 6 phases are complete, verify the following against the spec:

| Check | Endpoint | Expected |
|---|---|---|
| 1.1 fixed | `POST /api/v1/auth/register` | 201 with `{ "data": { "email": "..." } }` |
| 1.2 fixed | `POST /api/v1/auth/logout` | 200 with `{ "data": null }` |
| 2.1 fixed | `GET /api/v1/hotspots` | 200 `[]` when no hotspots, not 500 |
| 3.1/3.3 fixed | `POST /api/v1/intents` | Returns `{ "intent": {...}, "proposal": null\|{...} }` |
| 3.2 fixed | Private invite via `POST /api/v1/intents` | Both intents in MATCHING, proposal in PENDING, sender auto-accepted |
| 5.1 fixed | `GET /api/v1/sessions/history` | Each session has `"is_reviewed": true\|false` |
| 8.1/8.2 fixed | `GET /api/v1/friends` | Returns accepted friends only |
| 8.1 fixed | `POST /api/v1/friends/{id}/request` | 201, persisted Friendship (status=PENDING, requester_id set) |
| 8.1 fixed | `POST /api/v1/friends/requests/{id}/accept` | 200, Friendship status=ACCEPTED |
| 8.3 fixed | `POST /follow`, `DELETE /follow`, `GET /followers`, `GET /following` | 404 Not Found — endpoints no longer exist |

Run the MCP index one final time:
```
node "C:\Users\Duy\Desktop\ackg-engine\dist\index.js" index --path "C:\Users\Duy\Desktop\WalkMate"
```
