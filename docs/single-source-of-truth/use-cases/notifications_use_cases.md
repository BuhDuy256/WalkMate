# WalkMate — Notifications Use Cases

> Part of: [Use Cases Index](README.md)

**Domain:** In-App Notification Feed
**Last Updated:** 2026-04-13

---

## Table of Contents

| UC# | Use Case | API Endpoint |
|-----|----------|--------------|
| UC-39 | [View Notification Feed](#uc-39--view-notification-feed) | `GET /api/v1/notifications` |
| UC-40 | [Mark Notification as Read](#uc-40--mark-notification-as-read) | `POST /api/v1/notifications/{notificationId}/read` |

---

### UC-39 — View Notification Feed

**Use Case Name:** View Notification Feed

**Initial assumption:** User is authenticated and on the Notifications screen. Unread badge is shown on the tab icon.

**Normal:**
1. UI calls `GET /api/v1/notifications`.
2. Backend returns list of notifications (newest first), each with `type`, `payload`, `status` (`UNREAD`/`READ`), `createdAt`, `readAt`.
3. UI renders the list. `UNREAD` items are visually highlighted.
4. Key notification types and their navigation targets:

| Notification Type | Description | Action |
|---|---|---|
| `PROPOSAL_RECEIVED` | New match proposal exists | Navigate to Proposal tab (UC-19), then open Proposal Detail |
| `INVITE_SENT` | Your private invite was created and is waiting for your friend | Navigate to Proposal tab (UC-19), then open Proposal Detail |
| `PROPOSAL_ACCEPTED` | Partner accepted the proposal | Show status update; navigate to Proposal tab and open Proposal Detail |
| `FRIEND_REQUEST_RECEIVED` | Someone sent you a friend request | Navigate to Social requests view (UC-36) |
| `FRIEND_REQUEST_ACCEPTED` | Your friend request was accepted | Navigate to Friends list (UC-36) |
| `FRIEND_REQUEST_DECLINED` | Your friend request was declined | Navigate to Sent Requests (UC-36) |
| `SESSION_CONFIRMED` | Proposal confirmed; session created in `PENDING` | Navigate to Session Detail screen (UC-23) |
| `SESSION_ACTIVE` | Both users activated; walk is live | Navigate to Active Session screen (UC-23) |

5. Tapping a notification marks it as read (UC-40) and navigates to the relevant screen.

**What can go wrong:**

| Condition | UI Reaction |
|-----------|------------|
| Network failure | Show cached notifications. |

**Other activities:**
- FCM push notifications should deep-link directly to the relevant screen when the app is in the background.
- Unread count badge on the Notifications tab icon should update on refresh.

**System state on completion:** Notification feed is rendered. Unread items are identifiable.

---

### UC-40 — Mark Notification as Read

**Use Case Name:** Mark Notification as Read

**Initial assumption:** User taps a notification in the feed. Notification is currently `UNREAD`.

**Normal:**
1. User taps a notification item.
2. UI optimistically marks it as `READ` (visual change).
3. UI calls `POST /api/v1/notifications/{notificationId}/read`.
4. Backend returns `200 OK`. `readAt` is set. Notification status is `READ`.
5. UI navigates to the action target (proposal, session, etc.) based on notification `type`.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Notification not found | `NOTIFICATION_NOT_FOUND` | Revert to UNREAD state. Show toast: "Could not mark as read." |
| User does not own notification | `NOTIFICATION_NOT_OWNER` | Show toast: "Permission denied." |

**Other activities:** None.

**System state on completion:** Notification status is `READ`. `readAt` is set. Unread count badge decrements.
