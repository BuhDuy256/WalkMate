# WalkMate — Gamification Use Cases

> Part of: [Use Cases Index](README.md)

**Domain:** Badges, Stats, and Leaderboard
**Last Updated:** 2026-04-12

---

## Table of Contents

| UC# | Use Case | API Endpoint |
|-----|----------|--------------|
| UC-34 | [View User Badges](#uc-34--view-user-badges) | `GET /api/v1/users/{userId}/badges` |
| UC-35 | [View User Stats](#uc-35--view-user-stats) | `GET /api/v1/users/{userId}/stats` |
| UC-36 | [View Leaderboard](#uc-36--view-leaderboard) | `GET /api/v1/leaderboard` |

---

### UC-34 — View User Badges

**Use Case Name:** View User Badges

**Initial assumption:** User is on any public profile page (own or another user's). Authentication not required.

**Normal:**
1. UI calls `GET /api/v1/users/{userId}/badges`.
2. Backend returns list of `{ badgeName, awardedAt }`.
3. UI renders badge chips/icons in the profile's "Achievements" section.
4. Each badge can have a tooltip explaining how it was earned.

**What can go wrong:**

| Condition | UI Reaction |
|-----------|------------|
| No badges | Show empty state: "No badges yet." |
| Network failure | Show cached badge list or hide section. |

**Other activities:** Badges are awarded server-side automatically when sessions complete. No explicit user action is needed.

**System state on completion:** Badges displayed. Read-only.

---

### UC-35 — View User Stats

**Use Case Name:** View User Stats

**Initial assumption:** User is on any public profile page. Authentication not required.

**Normal:**
1. UI calls `GET /api/v1/users/{userId}/stats`.
2. Backend returns `{ userId, totalPoints, totalDistanceKm, completedSessions, trustScore }`.
3. UI renders stats in the profile header or a stats section.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| User not found | `USER_NOT_FOUND` | Handle at the profile level (UC-26 already checks). |
| Network failure | — | Show cached or skeleton stats. |

**Other activities:** None.

**System state on completion:** Stats displayed. Read-only.

---

### UC-36 — View Leaderboard

**Use Case Name:** View Leaderboard

**Initial assumption:** User navigates to the Leaderboard tab. Authentication not required.

**Normal:**
1. UI calls `GET /api/v1/leaderboard`.
2. Backend returns top 50 users sorted by `totalPoints` descending, each entry including `rank`, `userId`, `totalPoints`, `totalDistanceKm`, `completedSessions`, `trustScore`.
3. UI renders a ranked list. Each row is tappable and navigates to UC-26 (public profile).
4. If the authenticated user is in the top 50, highlight their row.

**What can go wrong:**

| Condition | UI Reaction |
|-----------|------------|
| Network failure | Show cached leaderboard with "Last updated at..." label. |

**Other activities:** Refresh on screen focus or pull-to-refresh.

**System state on completion:** Leaderboard displayed. Read-only.
