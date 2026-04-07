# Gap Analysis: Codebase vs. SSOT Docs

**Date:** 2026-04-07  
**Branch:** `implement/realtime`  
**Sources compared:**
- `docs/single-source-of-truth/lifecycle/state-transitions.md`
- `docs/single-source-of-truth/lifecycle/invariants.md`
- DB migrations `V100` – `V105`
- Controllers: `WalkIntentController`, `ProposalController`, `SessionController`
- Services: `WalkIntentCommandService`, `MatchingCommandService`, `SessionCommandService`
- Domain: `WalkIntent`, `MatchProposal`, `WalkSession`
- Infrastructure: `FirebaseConfig`, `FcmNotificationProvider`, `NotificationPublisherImpl`

---

## Legend

| Priority | Meaning |
|---|---|
| **P0 – Blocking** | State machine is broken; system cannot work correctly |
| **P1 – High** | Invariants violated; correctness at risk |
| **P2 – Medium** | Features from SSOT missing; partial implementation |
| **P3 – Low** | Minor polish, secondary behavior |

---

## P0 – Blocking: State Machine Broken

### GAP-1 · `MATCHING` status absent from Java IntentStatus enum
- **SSOT:** State-transitions doc defines `OPEN → MATCHING → CONSUMED/OPEN/CANCELLED` as the core lifecycle.
- **DB:** V100 migration correctly added `ALTER TYPE public.intent_status ADD VALUE 'MATCHING'`.
- **Code:** `IntentStatus.java` still declares only `OPEN, CONSUMED, CANCELLED, EXPIRED`. The new enum value is orphaned in the DB.
- **Impact:** Every subsequent gap depends on this. Nothing MATCHING-related compiles/works until this is fixed.

### GAP-2 · Proposal creation does NOT lock intents to MATCHING (violates I-4, P-1)
- **SSOT I-4:** "Khi một Intent chuyển sang trạng thái `MATCHING`, nó phải được đánh dấu để loại khỏi kết quả của Matching Engine."
- **SSOT P-1:** "Ngay sau khi tạo, hệ thống phải chuyển cả hai Intent sang `MATCHING`."
- **Code:** `MatchingCommandService.findOrCreateProposal()` creates the proposal but never updates intent status. Both intents stay `OPEN` → can be matched again → duplicate proposals.
- **Affected file:** `application/proposal/MatchingCommandService.java`

### GAP-3 · Proposal rejection does NOT return intents to OPEN (violates state-transition: MATCHING → OPEN)
- **SSOT:** Transition `MATCHING → OPEN` is triggered by "MatchProposal is REJECTED or EXPIRED".
- **Code:** `passProposal()` sets proposal status = REJECTED and stops. `cancelProposal()` sets proposal to REJECTED and cancels *only* the caller's intent. Neither restores partner's intent from MATCHING → OPEN.
- **Affected file:** `application/proposal/MatchingCommandService.java`

### GAP-4 · MatchProposal domain entity has no `version` field (violates X-5)
- **SSOT X-5:** "Mọi hành động cập nhật trạng thái trên `WalkIntent`, `MatchProposal`, và `WalkSession` đều phải kiểm tra trường `version`."
- **DB:** V100 added `version bigint NOT NULL DEFAULT 0` to `match_proposal`.
- **Code:** `MatchProposal.java` has no `version` field. `MatchProposalJdbcRepository` does not read/write it. No optimistic locking on proposals.
- **Affected files:** `domain/proposal/MatchProposal.java`, `infrastructure/repository/proposal/MatchProposalJdbcRepository.java`

### GAP-7 · P-3 confirmation re-checks `OPEN`, must re-check `MATCHING` (violates P-2)
- **SSOT P-2:** "Lời mời chỉ được chuyển sang CONFIRMED khi và chỉ khi cả hai người dùng đã nhấn Chấp nhận VÀ cả hai Intent vẫn đang ở trạng thái `MATCHING`."
- **Code:** `acceptProposal()` pessimistic-lock path re-verifies `isOpen()` on both intents. After GAP-1/GAP-2 are fixed, intents will be `MATCHING`, so this guard will always fail with `PROPOSAL_INTENT_NO_LONGER_OPEN`.
- **Affected file:** `application/proposal/MatchingCommandService.java`

---

## P1 – High: Invariants Violated

### GAP-5 · Proposal TTL is 30 minutes; SSOT mandates ≤ 5 minutes (violates P-4)
- **SSOT P-4:** "Giữ nguyên tối đa 5 phút. Nếu quá hạn mà chưa đủ 2 bên chấp nhận, lời mời chuyển sang `EXPIRED`..."
- **Code:** `PROPOSAL_TTL_MINUTES = 30` in `MatchingCommandService`. Users are locked in `MATCHING` for up to 30 min instead of 5.

### GAP-6 · Activation window constants mismatch (violates S-3)
- **SSOT S-3:** "Việc kích hoạt chỉ có hiệu lực trong khoảng: `[Giờ bắt đầu - 10 phút, Giờ bắt đầu + 15 phút]`."
- **Code:** `WalkSession.java` constants are `ACTIVATION_WINDOW_BEFORE = 15 min`, `ACTIVATION_WINDOW_AFTER = 30 min`. Both values are wrong.

### GAP-9 · No `POST /sessions/{id}/complete` endpoint (session cannot be user-completed)
- **SSOT S-5:** Session ACTIVE → COMPLETED requires the walk to reach its goal.
- **Code:** `SessionCommandService.completeSession()` exists with the 5-minute guard but is never exposed. `SessionController` only has activate, cancel, abort. Users have no way to end a walk.

### GAP-10 · `is_private`, `invited_friend_id`, `description` absent from code (violates I-7)
- **DB:** V100 adds all three columns to `walk_intent`.
- **SSOT I-7:** "Nếu `is_private = true`, Intent này tuyệt đối không được xuất hiện trong kết quả tìm kiếm công khai. Nó chỉ có thể được kết nối thông qua `invited_friend_id`."
- **Code:**
  - `WalkIntent.java` → missing fields
  - `CreateWalkIntentCommand.java` → missing fields
  - `CreateWalkIntentRequest.java` → missing fields
  - Matching engine (`findOpenCandidates()`) has no `is_private` filter
  - No validation that `invited_friend_id` maps to an ACCEPTED friendship

### GAP-12 · Overlap check uses wrong status set (violates I-1)
- **SSOT I-1:** Overlap is blocked by intents in `OPEN` or `MATCHING` (plus sessions in `PENDING` or `ACTIVE`).
- **Code:** `hasOverlappingActiveIntent()` currently checks `OPEN` and `CONSUMED`. `CONSUMED` should NOT block (hand-off is complete). `MATCHING` is not checked (yet doesn't exist in enum anyway).
- After GAP-1 fix, the SQL query must be updated to `status IN ('OPEN', 'MATCHING')`.

---

## P2 – Medium: Missing Features

### GAP-8 · P-3 atomic transaction missing MongoDB chat room creation (violates P-3)
- **SSOT P-3 step 3:** "Tạo Chat Room ảo trên MongoDB (Sử dụng `session_id` làm khóa)."
- **Code:** The atomic confirmation creates WalkSession and consumes intents but never initializes a MongoDB chat room keyed on `session_id`. SQL chat tables were removed (V101). MongoDB is the target. No chat initialization code exists anywhere.
- **Architecture constraint (agreed):** The MongoDB `initRoom()` call must NOT be inside the `@Transactional` JDBC boundary. It must be dispatched via a `TransactionSynchronizationManager.afterCommit()` hook so that MongoDB is written only after the PostgreSQL transaction has durably committed. If the MongoDB write fails, the WalkSession remains valid and a reconciliation path handles the gap.
- **Blocked by:** GAP-17 (MongoDB infrastructure must be wired first).

### GAP-11 · Per-intent exclude list not maintained on rejection (violates X-3)
- **SSOT X-3:** "Nếu User A từ chối lời mời từ User B, hệ thống phải cập nhật danh sách loại trừ của Intent đó để Matching Engine không ghép cặp hai người này lại trong cùng một yêu cầu."
- **Code:** Only the global block list (via `SocialRepository.getBlockedAndBlockerIds()`) is used. There is no per-intent exclude list. After a pass, the same pair can be re-matched immediately.

### GAP-13 · Intent expiry does NOT cascade to related proposals (violates I-5)
- **SSOT I-5:** "Khi một `WalkIntent` chuyển sang `EXPIRED`, tất cả các `MatchProposal` liên quan đang ở trạng thái `PENDING` phải tự động chuyển sang `EXPIRED`."
- **Code:** The scheduler (`SessionScheduler` / `handleExpiredSessions`) handles session timeouts but there is no equivalent scheduler that marks stale intents `EXPIRED` and cascades to proposals.

### GAP-14 · No auto-expire intent at T−5 min before scheduled start (SSOT Note #2)
- **SSOT Note #2:** "Nếu còn 5 phút nữa là đến giờ đi dạo mà Intent vẫn chưa chuyển sang `CONSUMED`, hãy tự động chuyển nó sang `EXPIRED`."
- **Code:** No scheduler job exists for this. Intents can remain `OPEN`/`MATCHING` past their start time.

### GAP-17 · No MongoDB Atlas dependency, configuration, or infrastructure (blocks GAP-8)
- **SSOT P-3 step 3:** Chat room must be stored in MongoDB keyed by `session_id`.
- **Code:** `build.gradle` has no `spring-boot-starter-data-mongodb` dependency. `application.properties` has no `MONGODB_URI` configuration. No `ChatRoom` document class, no `ChatRoomRepository` port, no `MongoChatRoomRepository` adapter exists anywhere in the codebase.
- **Impact:** GAP-8 cannot be addressed until the entire MongoDB infrastructure layer is wired. This is a prerequisite step.

### GAP-18 · `NotificationPublisherImpl` is single-channel — FCM not dispatched for lifecycle events
- **SSOT:** All lifecycle events (SESSION_CONFIRMED, SESSION_ACTIVE, REVIEW_REQUESTED) must reach the user's device in real-time.
- **Code:** `NotificationPublisherImpl.publish()` only calls `notificationRepository.save()`. It never calls the FCM channel. FCM is only invoked manually and directly inside `MatchingCommandService.findOrCreateProposal()` for the `MATCH_FOUND` event. SESSION_CONFIRMED, SESSION_ACTIVE, and REVIEW_REQUESTED are delivered to the in-app DB feed only — never as push notifications.
- **Architecture constraint (agreed):** `NotificationPublisherImpl` must be upgraded to dual-dispatch: (1) persist to DB, (2) look up the user's FCM token and call `PushNotificationProvider.sendPush()`. This centralizes all notification dispatch behind the existing `NotificationPublisher` port with zero call-site changes in application services.

### GAP-19 · `PushNotificationProvider` has no generic push method (blocks GAP-18)
- **Code:** `PushNotificationProvider` declares only `sendMatchFound(String fcmToken, String intentId, String proposalId)`. There is no generic `sendPush(String fcmToken, NotificationType type, Map<String, Object> payload)` method. Without this, `NotificationPublisherImpl` cannot delegate FCM dispatch for arbitrary notification types without importing vendor-specific types, which would violate the port/adapter boundary.
- **Impact:** GAP-18 cannot be implemented until this generic method exists on the port and its adapter.

---

## P3 – Low: Polish / Secondary Behavior

### GAP-15 · NO_SHOW and ABORTED do not emit reputation penalty events (violates S-4, X-4)
- **SSOT S-4:** "Người không kích hoạt sẽ bị hệ thống ghi nhận điểm xấu (Penalty)."
- **SSOT X-4:** Session outcomes must immediately update user reputation.
- **Code:** `SessionCommandService` publishes `SessionCompletedEvent` for COMPLETED sessions (gamification pipeline). No penalty event is published for NO_SHOW or ABORTED. Non-showing user is not identified or penalized.

### GAP-16 · `listActiveIntents` only returns `OPEN`; should include `MATCHING`
- **SSOT:** A user's "active" intents are `OPEN` or `MATCHING`.
- **Code:** `WalkIntentQueryService.listActiveIntents()` queries only `status = 'OPEN'`. After GAP-1 fix, users will not see their MATCHING intents in the list.

---

## Summary Table

| Gap | SSOT Ref | Priority | Area |
|-----|----------|----------|------|
| GAP-1: MATCHING missing from IntentStatus enum | state-transitions | **P0** | Domain |
| GAP-2: Proposal creation doesn't lock intents to MATCHING | I-4, P-1 | **P0** | Service |
| GAP-3: Proposal rejection doesn't restore intents to OPEN | state-transitions | **P0** | Service |
| GAP-4: MatchProposal has no version field | X-5 | **P0** | Domain |
| GAP-7: P-3 checks OPEN instead of MATCHING | P-2 | **P0** | Service |
| GAP-5: Proposal TTL = 30 min vs. 5 min | P-4 | **P1** | Service |
| GAP-6: Activation window 15/30 min vs. 10/15 min | S-3 | **P1** | Domain |
| GAP-9: No complete session endpoint | S-5 | **P1** | Controller |
| GAP-10: is_private/invited_friend_id/description not in code | I-7 | **P1** | Domain/Infra |
| GAP-12: Overlap check uses OPEN+CONSUMED instead of OPEN+MATCHING | I-1 | **P1** | Repository |
| GAP-8: No MongoDB chat room on session creation | P-3 | **P2** | Service |
| GAP-11: No per-intent exclude list on rejection | X-3 | **P2** | Repository |
| GAP-13: Intent expiry doesn't cascade to proposals | I-5 | **P2** | Scheduler |
| GAP-14: No auto-expire intent at T−5 min | Note #2 | **P2** | Scheduler |
| GAP-17: No MongoDB Atlas dependency, config, or infrastructure | P-3 | **P2** | Infrastructure |
| GAP-18: NotificationPublisherImpl is single-channel (DB only) | S-7, lifecycle | **P2** | Infrastructure |
| GAP-19: PushNotificationProvider has no generic sendPush() method | GAP-18 blocker | **P2** | Application Port |
| GAP-15: NO_SHOW/ABORTED emit no penalty event | S-4, X-4 | **P3** | Service |
| GAP-16: listActiveIntents excludes MATCHING | state-transitions | **P3** | Query |
