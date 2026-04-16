# ADR-002: FCM Dual-Channel Notification Dispatch

**Date:** 2026-04-07  
**Status:** Accepted  
**Deciders:** BuhDuy

---

## Context

WalkMate has two notification channels:
1. **In-app DB feed** — `NotificationRepository.save()` persists a row the mobile client polls via `GET /api/v1/notifications`.
2. **Real-time FCM push** — `FirebaseMessaging.send()` delivers a data-only message to the device immediately, even when backgrounded.

Before Phase 2B, these channels were disconnected. `NotificationPublisherImpl` only wrote to the DB. FCM was invoked manually and ad-hoc: a single explicit call to `pushNotificationProvider.sendMatchFound()` existed inside `MatchingCommandService.findOrCreateProposal()`. All other lifecycle events — `SESSION_CONFIRMED`, `SESSION_ACTIVE`, `REVIEW_REQUESTED` — reached the DB feed only. Users would not receive a push notification for these events until the next app poll.

This inconsistency also violated the single-responsibility principle: application services were responsible for knowing which events required a push, and which token to use — concerns that belong in the infrastructure layer.

---

## Decision

`NotificationPublisherImpl` is upgraded to dual-dispatch on every `publish()` call:

1. **Channel 1 (DB):** `notificationRepository.save(notification)` — persists to PostgreSQL for the in-app feed. Always attempted first.
2. **Channel 2 (FCM):** `userRepository.findById(userId)` to resolve the FCM token, then `pushNotificationProvider.sendPush(token, type, payload)` — fires the FCM data message. Best-effort.

A generic `sendPush(String fcmToken, NotificationType type, Map<String, Object> payload)` method is added to the `PushNotificationProvider` port and implemented in `FcmNotificationProvider`. All Firebase Admin SDK types remain confined to `FcmNotificationProvider`; nothing leaks upward into the application layer.

The manual `sendMatchFound()` call in `MatchingCommandService` is removed. The existing `notificationPublisher.publish()` call for `PROPOSAL_RECEIVED` now covers both channels automatically. `PushNotificationProvider` and `UserRepository` are removed from `MatchingCommandService`'s dependencies entirely.

---

## Channel Failure Isolation

Each channel is wrapped in its own `try-catch` block inside `NotificationPublisherImpl.publish()`:

- **If Channel 1 (DB) fails:** the exception is caught and logged at `ERROR`. Channel 2 (FCM) still runs. The business transaction that called `publish()` is not affected — notification persistence failure must never roll back a session creation or proposal acceptance.
- **If Channel 2 (FCM) fails:** the exception is caught and logged at `ERROR`. This covers both `FirebaseMessagingException` (thrown inside `sendPush()` and swallowed there) and any token-lookup exception (e.g., DB unreachable when fetching the user). Channel 1 is already complete and unaffected.
- **Both failures are independent.** A broken FCM connection does not block DB persistence. A missing user record (no FCM token) silently skips the push — not an error.

The `sendPush()` adapter in `FcmNotificationProvider` additionally wraps `FirebaseMessaging.send()` in a `try-catch(FirebaseMessagingException)`, logging the error code and token prefix. This means FCM failures are absorbed at two layers: inside the adapter and inside `NotificationPublisherImpl`.

---

## Alternatives Considered

### Option A: Keep FCM calls explicit at each call site (rejected)
Each service method that publishes a notification would also look up the FCM token and call `sendMatchFound()` or a type-specific push method.  
**Rejected** because this scales poorly: every new `NotificationType` requires finding and updating all call sites. It also leaks infrastructure concerns (token lookup, FCM dispatch) into application services, violating the port/adapter boundary.

### Option B: Event-driven FCM dispatch via Spring ApplicationEvent (deferred)
`NotificationPublisherImpl` publishes a `NotificationCreatedEvent`; a separate `FcmEventListener` handles FCM dispatch asynchronously.  
**Deferred** because it introduces asynchronous complexity (thread pool, ordering guarantees, observability) that is not warranted at the current scale. The synchronous dual-dispatch in `NotificationPublisherImpl` is simpler and sufficient. Can be revisited if FCM latency becomes a problem.

### Option C: Dedicated notification microservice (rejected for this phase)
A separate service consumes notification events and handles all dispatch logic.  
**Not applicable** — WalkMate is currently a monolith. The port/adapter structure already isolates the boundary; extracting the service is a future option without code changes.

---

## Consequences

### What becomes easier
- **Zero call-site changes for new `NotificationTypes`**: adding a new lifecycle event and calling `notificationPublisher.publish()` automatically sends both a DB notification and an FCM push. No other file needs to change.
- **Single place to add rate-limiting, token refresh, or batching**: all FCM dispatch flows through `NotificationPublisherImpl` → `PushNotificationProvider`.
- **Dependency simplification**: `MatchingCommandService` loses two dependencies (`PushNotificationProvider`, `UserRepository`), making it easier to test and reason about.

### What to watch for
- **FCM token staleness:** the token stored in `user_account.fcm_token` may be invalid (user re-installed the app, cleared data, or the token rotated). `FcmNotificationProvider.sendPush()` logs the `MessagingErrorCode` on failure. A background job to refresh stale tokens (e.g., using Firebase token management API) should be added in a future phase.
- **Payload key collisions:** `sendPush()` serialises all `Map<String, Object>` values as strings via `Object.toString()`. If a payload value is a complex object (e.g., a nested Map), the string representation will be unreadable to the Android client. Call sites must ensure all payload values are primitive-compatible (String, Number, Boolean, UUID-as-String).
- **No retry on FCM failure:** if the FCM write fails (e.g., network partition), the push is lost silently. The in-app DB notification still exists so the user sees the event on next poll. A retry queue or dead-letter store can be added if real-time delivery SLA tightens.
