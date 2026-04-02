# Optimization Decision Log — Phase 11

## Decision 1: Domain port (`NotificationPublisher`) instead of direct repository calls in services

**Problem:** Application services (`MatchingCommandService`, `SessionCommandService`) need to emit notifications. The naive approach is to inject `NotificationRepository` directly.

**Choice:** Introduce a `NotificationPublisher` interface in `domain/shared/` and inject that instead.

**Rationale:**
- Application services are business-logic code. If they depend on `NotificationRepository` (an infrastructure concern), the dependency arrow points in the wrong direction.
- `NotificationPublisher` is a pure domain/application-layer port. The infrastructure adapter (`NotificationPublisherImpl`) wires the concrete delivery mechanism (currently: DB persist; later: FCM push, email, etc.).
- When FCM is added in a future phase, only `NotificationPublisherImpl` changes — zero changes to `MatchingCommandService` or `SessionCommandService`.

---

## Decision 2: Notification failure must never roll back a business transaction

**Problem:** `NotificationPublisherImpl.publish()` is called inside `@Transactional` business methods. If the JDBC INSERT for the notification throws, Spring will roll back the entire transaction (session creation, session completion, etc.).

**Choice:** `NotificationPublisherImpl.publish()` wraps the `save()` call in a `try/catch(Exception)` and logs the failure without re-throwing.

**Rationale:**
- A walk session being created or completed is irreversible business state. A failed notification is recoverable (the user can refresh, the next poll will succeed, or a retry can be added later).
- The alternative (REQUIRES_NEW propagation like Gamification uses) would open a separate transaction for each notification. With two notifications per event this is 2 extra round-trips. The try/catch approach is simpler, cheaper, and sufficient for a pull-based feed where stale state resolves on the next poll.
- The catch-and-log design is documented in the interface Javadoc so future implementors know the contract.

---

## Decision 3: Notification triggers placed at the call site, not as Spring events

**Problem:** Gamification uses `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` so it runs in a completely independent transaction after the session is committed. Should notifications follow the same pattern?

**Choice:** Notifications are triggered inline (same transaction), not via Spring events.

**Rationale:**
- Gamification must run AFTER_COMMIT because a gamification failure must never affect the session record and because it opens a REQUIRES_NEW transaction. Notifications have neither requirement — they are best-effort, failure-swallowed, and share the business transaction safely.
- Using AFTER_COMMIT for notifications would mean: if the outer transaction rolls back (e.g., DB constraint on session creation), the AFTER_COMMIT listener would not fire — correct. But the inline approach also achieves this: the notification save is part of the outer transaction, so a rollback rolls back the notification too — also correct.
- The inline approach requires fewer moving parts (no event class, no listener, no `@EnableAsync` needed) and is easier to trace in logs.

---

## Decision 4: Polling instead of WebSockets for real-time delivery

**Problem:** Notifications should feel responsive. WebSockets or Server-Sent Events would deliver notifications in real-time.

**Choice:** 30-second poll from the Android ViewModel.

**Rationale:**
- Phase 11 explicitly defers FCM push to a later phase. WebSockets require additional server infrastructure (e.g., Spring WebFlux or a dedicated broker) that is not in scope.
- WalkMate's notification events are low-frequency (1-4 per walk lifecycle). A 30-second lag is acceptable for: "you have a new proposal", "session confirmed", etc.
- The polling architecture is forward-compatible: when FCM is added, the `NotificationPublisherImpl` sends a push, and the poll becomes a fallback for missed messages.
- The ViewModel's `startPolling()`/`stopPolling()` tied to `onResume`/`onPause` ensures no background CPU or network is consumed when the screen is off-screen.

---

## Decision 5: JSONB payload column instead of typed columns per notification type

**Problem:** Each notification type carries different metadata (proposalId, sessionId, partnerUserId, etc.). Options: (a) separate typed columns per type, (b) a JSONB payload column.

**Choice:** Single `payload JSONB` column.

**Rationale:**
- There are currently four notification types with different payloads. Adding typed columns (`proposal_id UUID`, `session_id UUID`, `partner_user_id UUID`) would leave most columns NULL for any given row and couple the schema tightly to the current set of types.
- JSONB allows new notification types to be added (e.g., a future BADGE_EARNED or FOLLOW_RECEIVED) without a schema migration.
- The payload is deserialized into `Map<String, Object>` in the domain and on Android. Client code inspects the relevant key by notification type — a clean, type-safe pattern at the application layer without schema churn.

---

## Decision 6: WalkSession lifecycle integration — non-invasive injection

**Problem:** Adding `NotificationPublisher` to `MatchingCommandService` and `SessionCommandService` modifies two battle-tested services.

**Choice:** Inject via Lombok `@RequiredArgsConstructor` (adds one field) and call `publish()` at exactly the right points in the existing control flow, after the primary persistence is done.

**Rationale:**
- The notification calls are placed *after* the business state is saved (`sessionRepository.save`, `matchProposalRepository.save`, etc.). This means: if the notification save fails (caught), the business state is already safely persisted.
- No existing method signatures, return types, or transaction boundaries were changed.
- The `SESSION_ACTIVE` notification is gated on `session.getStatus() == SessionStatus.ACTIVE` — the precise transition check that was already implied by `recordActivation()`'s double-activation logic, expressed explicitly here rather than duplicated.

---

## Decision 7: Unread count computed in `NotificationUiState.ready()`, not fetched from a separate endpoint

**Problem:** The Home screen badge and the Notification Center both need an unread count. A dedicated `GET /api/v1/notifications/unread-count` endpoint would be the cleanest API design but adds another network call.

**Choice:** The unread count is computed client-side from the fetched list inside `NotificationUiState.ready()`.

**Rationale:**
- The full list is fetched anyway on every 30-second poll. Counting locally is O(n) with no extra round-trip.
- For a typical user the list is small (tens of items). Pagination is deferred to a later phase.
- The Home screen badge can be wired by fetching from the same `NotificationRepository` singleton and reading `unreadCount` from the state. This is noted as a gap in HANDOFF_PHASE_11 for the next session.
