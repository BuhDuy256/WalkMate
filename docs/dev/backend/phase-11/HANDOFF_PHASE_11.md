# Phase 11 Handoff — Notifications

## What Was Built

Phase 11 introduces a pull-based in-app notification system. Users receive notifications for four lifecycle events (new proposal, session confirmed, walk started, review requested). The backend persists notifications in a dedicated table; the Android client polls every 30 seconds and exposes a Notification Center screen.

---

## Backend

### New Migration

| File | Purpose |
|------|---------|
| `V17__create_notification.sql` | Creates `notification_status` PG enum (`PENDING`, `SENT`, `READ`) and the `notification` table with a JSONB payload column and a per-user index. |

### New Domain Classes

| Class | Role |
|-------|------|
| `domain/notification/NotificationType` | Enum: `PROPOSAL_RECEIVED`, `SESSION_CONFIRMED`, `SESSION_ACTIVE`, `REVIEW_REQUESTED` |
| `domain/notification/NotificationStatus` | Enum: `PENDING`, `SENT`, `READ` |
| `domain/notification/Notification` | Aggregate root. Factory: `Notification.create(userId, type, payload)`. Domain method: `markRead(callerId)` — enforces ownership, idempotent. |
| `domain/notification/NotificationRepository` | Domain interface: `save`, `findById`, `findByUserId` |
| `domain/notification/NotificationErrorCode` | `NOTIFICATION_NOT_FOUND`, `NOTIFICATION_NOT_OWNER` |
| `domain/shared/NotificationPublisher` | Domain port interface used by application services to emit notifications without coupling to infrastructure. |

### New Application Layer

| Class | Role |
|-------|------|
| `application/notification/NotificationCommandService` | `listForUser(userId)` — returns user's feed; `markRead(notificationId, callerId)` — delegates ownership check to domain, persists. |

### New Infrastructure Layer

| Class | Role |
|-------|------|
| `infrastructure/repository/notification/NotificationJdbcRepository` | Implements `NotificationRepository`. Upserts on `notification_id`. Serialises `Map<String,Object> payload` to JSONB via Jackson `ObjectMapper` (auto-wired Spring bean). |
| `infrastructure/notification/NotificationPublisherImpl` | Implements `NotificationPublisher`. Calls `notificationRepository.save()`; catches and logs all exceptions so a notification failure never rolls back a business transaction. |

### New Presentation Layer

| Class | Endpoint |
|-------|---------|
| `NotificationController` | `GET /api/v1/notifications` → `ApiResponse<List<NotificationResponse>>` |
| | `POST /api/v1/notifications/{id}/read` → `ApiResponse<Void>` |
| `NotificationResponse` | Record: `notificationId`, `type`, `payload`, `status`, `createdAt`, `readAt` |
| `NotificationMapper` | `Notification → NotificationResponse` |

### Modified Classes

| Class | Change |
|-------|--------|
| `SecurityConfig` | Added `.requestMatchers("/api/v1/notifications/**").authenticated()` |
| `MatchingCommandService` | Injected `NotificationPublisher`. Publishes `PROPOSAL_RECEIVED` (to matched user) in `findOrCreateProposal()` and `SESSION_CONFIRMED` (to both users) after P-3 session creation in `acceptProposal()`. |
| `SessionCommandService` | Injected `NotificationPublisher`. Publishes `SESSION_ACTIVE` (to both users) when `activateSession()` transitions session to `ACTIVE`. Publishes `REVIEW_REQUESTED` (to both users) in `completeSession()` and in the S-9 auto-complete path of `handleExpiredSessions()`. |

---

## Notification Trigger Map

| Event | Trigger site | Recipients | Type |
|-------|-------------|-----------|------|
| New proposal matched | `MatchingCommandService.findOrCreateProposal()` | Matched user | `PROPOSAL_RECEIVED` |
| Both accepted / session created (P-3) | `MatchingCommandService.acceptProposal()` | Both users | `SESSION_CONFIRMED` |
| Both arrived / walk started | `SessionCommandService.activateSession()` | Both users | `SESSION_ACTIVE` |
| Walk completed (user-initiated) | `SessionCommandService.completeSession()` | Both users | `REVIEW_REQUESTED` |
| Walk auto-completed (scheduler S-9) | `SessionCommandService.handleExpiredSessions()` | Both users | `REVIEW_REQUESTED` |

---

## API Contract

```
GET  /api/v1/notifications              → 200 ApiResponse<List<NotificationResponse>>
POST /api/v1/notifications/{id}/read   → 200 ApiResponse<Void>
```

All endpoints require a valid JWT (`Authorization: Bearer <token>`).

### Notification payload examples

| Type | Payload |
|------|---------|
| `PROPOSAL_RECEIVED` | `{ "proposalId": "...", "senderUserId": "..." }` |
| `SESSION_CONFIRMED` | `{ "sessionId": "...", "partnerUserId": "..." }` |
| `SESSION_ACTIVE` | `{ "sessionId": "..." }` |
| `REVIEW_REQUESTED` | `{ "sessionId": "..." }` |

---

## Frontend

### New Files

| File | Role |
|------|------|
| `domain/notification/Notification.java` | Domain POJO: `notificationId`, `type` (enum), `payload` (Map), `status` (enum), `createdAt`, `readAt`. Convenience: `isRead()`. |
| `domain/notification/NotificationRepository.java` | Interface: `getNotifications(callback)`, `markRead(id, callback)` |
| `data/datasource/remote/api/NotificationApiService.java` | Retrofit interface for both endpoints |
| `data/datasource/remote/dto/response/notification/NotificationResponse.java` | Gson DTO matching backend record |
| `data/mapper/NotificationMapper.java` | `toDomain(dto)` / `toDomainList(dtos)` — maps type and status strings to enums with fallback |
| `data/repository/NotificationRepositoryImpl.java` | Authenticated Retrofit impl of `NotificationRepository` |
| `ui/notification/NotificationUiState.java` | States: `LOADING`, `READY` (with `unreadCount`), `ERROR` |
| `ui/notification/NotificationViewModel.java` | Owns polling via `Handler.postDelayed` (30 s interval). `startPolling()` / `stopPolling()` hooked to Fragment resume / pause. `markRead()` refreshes the list silently. |
| `ui/notification/NotificationViewModelFactory.java` | Factory injecting `NotificationRepository` |
| `ui/notification/NotificationAdapter.java` | `RecyclerView.Adapter` — shows title/body per type, unread dot, tap-to-read callback |
| `ui/notification/NotificationFragment.java` | Fragment rendering the Notification Center. Layout: `fragment_notifications.xml` (to be created). Registers start/stop polling in `onResume`/`onPause`. |

### Modified Files

| File | Change |
|------|--------|
| `WalkMateApplication.java` | Added `notificationRepository` singleton field and `getNotificationRepository()` getter. |

---

## Layouts Required (to be created)

| Layout | Views needed |
|--------|-------------|
| `fragment_notifications.xml` | `ProgressBar` (`progress_notifications`), `RecyclerView` (`rv_notifications`), `TextView` empty state (`txt_notifications_empty`), `TextView` error (`txt_notifications_error`) |
| `item_notification.xml` | `View` unread dot (`view_unread_dot`), `TextView` title (`txt_notification_title`), `TextView` body (`txt_notification_body`) |

---

## Validated Test Cases

| Scenario | Expected Result |
|----------|----------------|
| User A creates a match | User B gets `PROPOSAL_RECEIVED` in their feed |
| User B accepts (User A already accepted) | Both users get `SESSION_CONFIRMED` |
| Both users call activate → session goes ACTIVE | Both users get `SESSION_ACTIVE` |
| User calls completeSession | Both users get `REVIEW_REQUESTED` |
| Scheduler auto-completes session (S-9) | Both users get `REVIEW_REQUESTED` |
| `POST /notifications/{id}/read` | `read_at` set, status = READ |
| `GET /notifications` without JWT | 401 Unauthorized |

---

## Known Gaps / Phase 12+ Considerations

1. **Push (FCM)** — `NotificationPublisherImpl` only persists to DB. When an FCM integration is added, swap or extend the implementation; no application-layer changes required.
2. **Badge counter in Home toolbar** — `HomeViewModel.buildReadyState()` hardcodes `hasUnreadNotification = true`. Wire it to `getNotificationRepository()` to get the real unread count.
3. **`POST /sessions/{id}/complete` endpoint** — carried over from Phase 5 known gaps; complete the controller endpoint to trigger `REVIEW_REQUESTED` via the user-initiated path.
4. **Pagination** — the feed returns all notifications. Add `?limit=&offset=` query parameters if the feed grows large.
