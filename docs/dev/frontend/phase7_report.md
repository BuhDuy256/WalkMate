# Phase 7 — Implementation Report
**Date:** 2026-04-14
**Branch:** feature/phase-2-rework
**Build status:** `BUILD SUCCESSFUL` — zero compile errors

---

## Overview

Phase 7 expanded `AppEventBus` to carry all nine backend FCM event types and wired each
one to the correct navigation destination via two paths:

1. **Foreground path** — `AppEventBus` LiveData observer in `MainActivity`; navigation
   fires immediately while the app is active.
2. **Background path** — `WalkMateFcmService` posts a system-tray notification whose
   `PendingIntent` embeds the event type and relevant ID as Intent extras; `MainActivity`
   reads these on launch via `handleFcmIntent()`.

---

## TASK 7.1 — Expand AppEvent Type Constants

### AppEvent.java — Redesigned

Replaced the previous ad-hoc individual fields (`intentId`, `proposalId`, `sessionId`)
with a single `Map<String, String> payload` field. This avoids constructor churn as new
FCM keys are added on the backend.

**New `Type` enum values (9 total):**

| Type | FCM data `type` key |
|------|---------------------|
| `MATCH_FOUND` | `MATCH_FOUND` (existing) |
| `PROPOSAL_RECEIVED` | `PROPOSAL_RECEIVED` |
| `INVITE_SENT` | `INVITE_SENT` |
| `PROPOSAL_ACCEPTED` | `PROPOSAL_ACCEPTED` |
| `SESSION_CONFIRMED` | `SESSION_CONFIRMED` |
| `SESSION_ACTIVE` | `SESSION_ACTIVE` |
| `FRIEND_REQUEST_RECEIVED` | `FRIEND_REQUEST_RECEIVED` |
| `FRIEND_REQUEST_ACCEPTED` | `FRIEND_REQUEST_ACCEPTED` |
| `FRIEND_REQUEST_DECLINED` | `FRIEND_REQUEST_DECLINED` |

**New constants (shared between FCM service and MainActivity):**

| Constant | Value | Purpose |
|----------|-------|---------|
| `EXTRA_FCM_TYPE` | `"fcm_type"` | Intent extra key for background PendingIntent |
| `EXTRA_FCM_PROPOSAL_ID` | `"fcm_proposalId"` | Optional proposalId extra |
| `EXTRA_FCM_SESSION_ID` | `"fcm_sessionId"` | Optional sessionId extra |
| `KEY_PROPOSAL_ID` | `"proposalId"` | Payload map key |
| `KEY_SESSION_ID` | `"sessionId"` | Payload map key |
| `KEY_INTENT_ID` | `"intentId"` | Payload map key |

**Migration:** `ExploreViewModel` previously read `event.intentId` and `event.proposalId`
directly. Updated to `event.payload.get(AppEvent.KEY_INTENT_ID)` /
`event.payload.get(AppEvent.KEY_PROPOSAL_ID)`.

---

## TASK 7.2 — Update FCM Message Handler

### WalkMateFcmService.java — Full dispatch + background tray notification

`onMessageReceived()` now:

1. Copies the full `remoteMessage.getData()` map into a `HashMap<String, String>` as
   the AppEvent payload.
2. Calls `resolveEventType(type)` which maps the raw `"type"` string to the
   `AppEvent.Type` enum. Unknown types return `null` — silently ignored.
3. Posts `new AppEvent(eventType, payload)` on `AppEventBus` (foreground path).
4. Calls `showTrayNotification(eventType, payload)` (background path).

### Background system-tray notification

`showTrayNotification()`:
- Creates notification channel `"walkmate_push"` (idempotent — safe to call every time).
- Builds a `NotificationCompat` notification with `PRIORITY_HIGH` and `autoCancel=true`.
- Sets a `contentIntent` PendingIntent targeting `MainActivity` with:
  - `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP`
  - `AppEvent.EXTRA_FCM_TYPE = eventType.name()`
  - `AppEvent.EXTRA_FCM_PROPOSAL_ID` (if proposalId present in payload)
  - `AppEvent.EXTRA_FCM_SESSION_ID` (if sessionId present in payload)
- Uses `eventType.ordinal()` as both the PendingIntent `requestCode` and the
  notification ID so same-type pushes replace each other in the tray.
- Notification icon: `R.drawable.ic_bell` (existing drawable).

---

## TASK 7.3 — Expand MainActivity.observeAppEventBus()

### handleFcmIntent() — New method (background deep-link routing)

Called from both `onCreate()` (cold-start tray tap) and `onNewIntent()` (warm-start
tray tap when app is already running in background):

1. Reads `EXTRA_FCM_TYPE` from the Intent; returns immediately if absent.
2. Consumes all three FCM extras from the Intent to prevent re-delivery on rotation.
3. Parses `AppEvent.Type.valueOf(fcmTypeName)` — ignores unknown future types gracefully.
4. Delegates to `routeToDestination(type, proposalId, sessionId)`.

### routeToDestination() — Shared routing helper

Extracted from the former inline `switch` in `observeAppEventBus()` so both the
foreground observer and `handleFcmIntent()` share one routing table.

**Routing table:**

| Event(s) | Destination | `scrollToTab` | Extra Bundle args |
|----------|-------------|--------------|-------------------|
| `MATCH_FOUND`, `PROPOSAL_RECEIVED`, `INVITE_SENT`, `PROPOSAL_ACCEPTED` | `matchesFragment` | `TAB_PROPOSAL` (1) | `proposalId` if present |
| `SESSION_CONFIRMED`, `SESSION_ACTIVE` | `matchesFragment` | `TAB_SESSION` (2) | `sessionId` if present |
| `FRIEND_REQUEST_RECEIVED` | `friendsFragment` | `TAB_INCOMING` (1) | — |
| `FRIEND_REQUEST_ACCEPTED` | `friendsFragment` | `TAB_FRIENDS` (0) | — |
| `FRIEND_REQUEST_DECLINED` | `friendsFragment` | `TAB_OUTGOING` (2) | — |

All navigations use `setPopUpTo(R.id.homeFragment, false)` for a clean back stack.

### observeAppEventBus() update

Now reads `event.payload.get(KEY_PROPOSAL_ID)` / `event.payload.get(KEY_SESSION_ID)` and
passes them to `routeToDestination()`. `consumeEvent()` is called unconditionally after
every event type (previously it was only inside the old single-case handler).

---

## TASK 7.4 — Notification Tap Deep-Link Dispatch (GAP-16)

### NotificationFragment.java — navigateForNotification()

Full dispatch table matching Task 7.3 routing:

| `Notification.Type` | Destination | Tab |
|--------------------|-------------|-----|
| `PROPOSAL_RECEIVED`, `INVITE_SENT`, `PROPOSAL_ACCEPTED` | `matchesFragment` | Proposal |
| `SESSION_CONFIRMED`, `SESSION_ACTIVE` | `matchesFragment` | Session |
| `FRIEND_REQUEST_RECEIVED` | `friendsFragment` | Incoming |
| `FRIEND_REQUEST_ACCEPTED` | `friendsFragment` | Friends |
| `FRIEND_REQUEST_DECLINED` | `friendsFragment` | Outgoing |
| `REVIEW_REQUESTED` | — (no navigation) | — |

Uses `setPopUpTo(R.id.notificationFragment, true)` to pop the Notification Centre off
the back stack — Back from the destination returns to the screen preceding Notifications.

Tap always fires (read or unread). `viewModel.markRead()` is called only when the
notification is currently unread, avoiding redundant API calls.

### NotificationAdapter.java

`bind()` now always sets `itemView.setOnClickListener` regardless of read state (previously
no-op for already-read items). Added `PROPOSAL_ACCEPTED` label/body strings.

### Notification.java (frontend domain)

Added `PROPOSAL_ACCEPTED` to `Notification.Type` enum so the mapper can deserialise
the backend value without falling back to `PROPOSAL_RECEIVED`.

---

## Files Modified (10 total)

| File | Change |
|------|--------|
| `core/event/AppEvent.java` | Full redesign: `Map<String,String> payload`; 9 types; EXTRA/KEY constants |
| `service/WalkMateFcmService.java` | Full dispatch switch; background tray notification + PendingIntent |
| `ui/main/MainActivity.java` | `handleFcmIntent()`; `routeToDestination()` helper; `observeAppEventBus()` updated |
| `ui/explore/ExploreViewModel.java` | Migrated `event.intentId/proposalId` → `event.payload.get(KEY_*)` |
| `domain/notification/Notification.java` | Added `PROPOSAL_ACCEPTED` enum value |
| `ui/notification/NotificationFragment.java` | `navigateForNotification()` dispatch table; conditional markRead |
| `ui/notification/NotificationAdapter.java` | Always-on click; `PROPOSAL_ACCEPTED` label/body |
| `res/navigation/nav_graph.xml` | `scrollToTab` arg on `friendsFragment` (from Phase 7 initial pass) |
| `ui/social/friends/FriendsPagerAdapter.java` | `TAB_COUNT` made public (from Phase 7 initial pass) |
| `ui/social/friends/FriendsFragment.java` | Reads `scrollToTab` arg; scrolls ViewPager2 (from Phase 7 initial pass) |

## Files Created

None.

---

## FCM Event Types Now Handled (9 total)

| # | `type` string | AppEvent.Type | Foreground | Background tray | Notification tap |
|---|---------------|--------------|-----------|----------------|-----------------|
| 1 | `MATCH_FOUND` | `MATCH_FOUND` | ✓ Proposal tab | ✓ | n/a |
| 2 | `PROPOSAL_RECEIVED` | `PROPOSAL_RECEIVED` | ✓ Proposal tab | ✓ | ✓ Proposal tab |
| 3 | `INVITE_SENT` | `INVITE_SENT` | ✓ Proposal tab | ✓ | ✓ Proposal tab |
| 4 | `PROPOSAL_ACCEPTED` | `PROPOSAL_ACCEPTED` | ✓ Proposal tab | ✓ | ✓ Proposal tab |
| 5 | `SESSION_CONFIRMED` | `SESSION_CONFIRMED` | ✓ Session tab | ✓ | ✓ Session tab |
| 6 | `SESSION_ACTIVE` | `SESSION_ACTIVE` | ✓ Session tab | ✓ | ✓ Session tab |
| 7 | `FRIEND_REQUEST_RECEIVED` | `FRIEND_REQUEST_RECEIVED` | ✓ Incoming tab | ✓ | ✓ Incoming tab |
| 8 | `FRIEND_REQUEST_ACCEPTED` | `FRIEND_REQUEST_ACCEPTED` | ✓ Friends tab | ✓ | ✓ Friends tab |
| 9 | `FRIEND_REQUEST_DECLINED` | `FRIEND_REQUEST_DECLINED` | ✓ Outgoing tab | ✓ | ✓ Outgoing tab |

---

## Known Risks / Follow-ups for Phase 8

| Risk | Detail |
|------|--------|
| `REVIEW_REQUESTED` tap | No review screen exists yet. Notification is marked read but no navigation fires. A Phase 8 review screen would add the missing case to `navigateForNotification()`. |
| `friendsFragment` back stack on FCM | FCM navigation uses `popUpTo(homeFragment, false)` — pressing Back from Friends returns to Home, not Profile. Could be refined to `popUpTo(profileFragment, false)` if a Profile-centric back stack is preferred. |
| Background notification permission | `NotificationManagerCompat.from(this).notify()` requires `POST_NOTIFICATIONS` permission on API 33+. No runtime permission request is currently made. A Phase 8 onboarding step should request this permission. |
| `sessionId` forwarded but not consumed | `MatchesFragment` receives `sessionId` in its Bundle but does not currently read it. A future phase could use it to pre-select the matching session row in the Session tab. |
| Same-type notification collapse | Using `eventType.ordinal()` as notification ID means a `SESSION_CONFIRMED` push replaces the previous one. If the user has two pending sessions, only the most recent tray entry is shown. Acceptable for now. |

---

## Verification

- **Build:** `./gradlew :frontend:assembleDebug` → `BUILD SUCCESSFUL in 9s` (0 errors, 0 new warnings)
